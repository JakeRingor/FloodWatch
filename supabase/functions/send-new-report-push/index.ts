import { createClient } from "npm:@supabase/supabase-js@2";

type FloodReport = {
  id: string;
  user_id: string;
  address: string | null;
  flood_level: string | null;
};

type FloodAlert = {
  id: string;
  title: string;
  message: string;
  severity: string | null;
  is_active: boolean | null;
};

type WebhookPayload = {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  schema: string;
  record: FloodReport | FloodAlert;
};

const encoder = new TextEncoder();

function base64Url(value: Uint8Array | string): string {
  const bytes = typeof value === "string" ? encoder.encode(value) : value;
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function pemToBytes(pem: string): Uint8Array {
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
}

async function getGoogleAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsignedJwt = `${header}.${claims}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsignedJwt));
  const assertion = `${unsignedJwt}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth failed: ${response.status} ${await response.text()}`);
  return (await response.json()).access_token;
}

Deno.serve(async (request) => {
  try {
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const webhookSecret = Deno.env.get("WEBHOOK_SECRET");
    const hasServiceRole = Boolean(
      serviceRoleKey && request.headers.get("authorization") === `Bearer ${serviceRoleKey}`
    );
    const hasWebhookSecret = Boolean(
      webhookSecret && request.headers.get("x-webhook-secret") === webhookSecret
    );
    if (!hasServiceRole && !hasWebhookSecret) {
      return new Response("Unauthorized", { status: 401 });
    }

    const payload = await request.json() as WebhookPayload;
    const supportedTable = payload.table === "flood_reports" || payload.table === "flood_alerts";
    if (payload.type !== "INSERT" || !supportedTable || !payload.record?.id) {
      return Response.json({ skipped: true });
    }
    if (payload.table === "flood_alerts" && (payload.record as FloodAlert).is_active === false) {
      return Response.json({ skipped: true, reason: "inactive alert" });
    }

    const serviceAccountBase64 = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_BASE64") ?? "";
    const serviceAccountJson = serviceAccountBase64
      ? new TextDecoder().decode(Uint8Array.from(atob(serviceAccountBase64), (c) => c.charCodeAt(0)))
      : "{}";
    const serviceAccount = JSON.parse(serviceAccountJson);
    if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
      throw new Error("FIREBASE_SERVICE_ACCOUNT is not configured");
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      serviceRoleKey,
    );
    let deviceQuery = supabase
      .from("device_push_tokens")
      .select("token")
      .eq("enabled", true);
    if (payload.table === "flood_reports") {
      deviceQuery = deviceQuery.neq("user_id", (payload.record as FloodReport).user_id);
    }
    const { data: devices, error } = await deviceQuery;
    if (error) throw error;
    if (!devices?.length) return Response.json({ sent: 0 });

    const accessToken = await getGoogleAccessToken(serviceAccount);
    const isAdminAlert = payload.table === "flood_alerts";
    const report = payload.record as FloodReport;
    const alert = payload.record as FloodAlert;
    const level = report.flood_level?.toUpperCase();
    const location = report.address?.trim() || "your community";
    const severity = alert.severity?.toUpperCase() || "ALERT";
    const title = isAdminAlert
      ? `${severity}: ${alert.title}`
      : level ? `New ${level} flood report` : "New flood report";
    const body = isAdminAlert
      ? alert.message
      : `A new flood report was submitted at ${location}.`;
    const channelId = isAdminAlert ? "admin_flood_alerts" : "new_flood_reports";

    const results = await Promise.all(devices.map(async ({ token }) => {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              notification: { title, body },
              data: {
                ...(isAdminAlert
                  ? { alert_id: payload.record.id, type: "admin_alert" }
                  : { report_id: payload.record.id, type: "new_report" }),
                title,
                body,
                open_alerts: "true",
              },
              android: {
                priority: "high",
                notification: {
                  channel_id: channelId,
                  sound: "default",
                },
              },
            },
          }),
        },
      );
      const responseBody = await response.text();
      return { token, ok: response.ok, status: response.status, responseBody };
    }));

    const staleTokens = results
      .filter(({ ok, responseBody }) => !ok &&
        (responseBody.includes("UNREGISTERED") || responseBody.includes("INVALID_ARGUMENT")))
      .map(({ token }) => token);
    if (staleTokens.length) {
      await supabase.from("device_push_tokens").delete().in("token", staleTokens);
    }

    const failures = results.filter((result) => !result.ok);
    console.log(JSON.stringify({ table: payload.table, recordId: payload.record.id, sent: results.length - failures.length, failed: failures.length }));
    return Response.json({ sent: results.length - failures.length, failed: failures.length });
  } catch (error) {
    console.error(error);
    return Response.json({ error: error instanceof Error ? error.message : "Unknown error" }, { status: 500 });
  }
});

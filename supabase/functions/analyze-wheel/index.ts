// Keep the Roboflow key on the server, never in the APK or source control.
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json" },
});

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply(405, { error: "POST required" });
  const authorization = req.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return reply(401, { error: "Sign in required" });
  try {
    // Validate the user even when platform JWT verification is disabled for newer keys.
    const auth = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, {
      headers: { Authorization: authorization, apikey: Deno.env.get("SUPABASE_ANON_KEY")! },
      signal: AbortSignal.timeout(10000),
    });
    if (!auth.ok) return reply(401, { error: "Invalid session" });
    const key = Deno.env.get("ROBOFLOW_API_KEY");
    if (!key) return reply(503, { error: "Wheel analysis is not configured" });
    // Bound the streamed request before JSON decoding (including chunked requests).
    const reader = req.body?.getReader();
    if (!reader) return reply(400, { error: "Image required" });
    let size = 0; const chunks: Uint8Array[] = [];
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 4_000_000) { await reader.cancel(); return reply(413, { error: "Image too large" }); }
      chunks.push(value);
    }
    const raw = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { raw.set(chunk, offset); offset += chunk.length; }
    let input;
    try { input = JSON.parse(new TextDecoder().decode(raw)); }
    catch { return reply(400, { error: "Invalid JSON" }); }
    if (typeof input.image !== "string" || !/^[A-Za-z0-9+/]+={0,2}$/.test(input.image)) {
      return reply(400, { error: "Base64 image required" });
    }
    const upstream = await fetch("https://serverless.roboflow.com/infer/object_detection", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        api_key: key,
        model_id: Deno.env.get("ROBOFLOW_WHEEL_MODEL") ?? "flood-watch-wheelie/5",
        image: { type: "base64", value: input.image }, confidence: 0.4,
      }),
      signal: AbortSignal.timeout(35000),
    });
    if (!upstream.ok) return reply(upstream.status === 429 ? 429 : 502, { error: "Wheel inference unavailable" });
    const result = await upstream.json();
    if (!Array.isArray(result.predictions) || !result.image) return reply(502, { error: "Unexpected inference response" });
    return reply(200, { predictions: result.predictions, image: result.image });
  } catch {
    // Do not log the request image, tokens, API key, or upstream error body.
    return reply(502, { error: "Wheel analysis unavailable" });
  }
});

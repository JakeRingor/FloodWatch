# Push notification deployment

The Android and Supabase code is implemented. Complete these one-time cloud steps:

1. In Firebase Console, create/select a project, add Android app package
   `com.example.floodwatch`, and download `google-services.json` to `app/`.
2. In Firebase project settings, create a service-account private key. Do not
   commit the JSON file.
3. Apply and deploy:

   ```powershell
   npx supabase db push
   npx supabase functions deploy send-new-report-push
   # Base64 avoids newline/quote corruption in the service-account JSON.
   npx supabase secrets set FIREBASE_SERVICE_ACCOUNT_BASE64="<base64 service-account JSON>"
   ```

4. Supabase Dashboard > Database > Webhooks > Create webhook:
   - Table: `public.flood_reports`
   - Event: `INSERT`
   - Type: Supabase Edge Function
   - Function: `send-new-report-push`
   - Method: `POST`
   - Click **Add auth header with service key**

   Create a second `INSERT` webhook with the same function and authentication
   for the `public.flood_alerts` table. This delivers official admin alerts to
   every enabled device.

5. Rebuild/install the Android app, sign in, and grant notification permission.
   Open it once so its FCM token is stored in `device_push_tokens`.

The reporter is excluded from the recipient query. Turning off Flood Alerts in
Profile disables that device token. Logging out removes it.

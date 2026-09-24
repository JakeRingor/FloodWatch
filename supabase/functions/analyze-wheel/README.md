# Wheel analysis setup

The Android report screen calls `analyze-wheel` through the existing Supabase project.
It sends a JPEG only after the user taps Analyze Wheel Online and confirms.
The existing local flood classifier remains independent. Reports retain user-selected depth.

## Dashboard setup

1. In the Supabase project used by the app, open Edge Functions and create a function named `analyze-wheel` using the editor.
2. Paste `index.ts` from this folder into the editor and deploy it.
3. In Edge Functions secrets, set `ROBOFLOW_API_KEY` to a fresh private key authorized for the wheel project. Do not paste it into Android code. The notebook contains a hardcoded key; rotate that exposed key.
4. Optionally set `ROBOFLOW_WHEEL_MODEL` to the exact deployment model ID from Roboflow. The default is `flood-watch-wheelie/5`, matching the notebook's project/version. Change this secret when upgrading models.
5. Install the debug APK, sign in, capture a photo, and tap Analyze Wheel Online. Check that one wheel box and an experimental result appear. Verify an empty image, no wheel, and lost connectivity produce unavailable states.

The function validates the bearer session against Supabase Auth on every call. If the gateway rejects newer JWT signing keys before the function runs, disable the platform's legacy JWT verification for this function; do not remove the in-function Auth check. Standard Supabase URL/anon-key environment variables must be available.

Live deployment and Roboflow inference have not been verified in this workspace. Hosted inference consumes Roboflow credits and needs internet access. Review usage before distributing widely. This implementation has authenticated access and payload bounds, but no per-user quota yet.

## Adaptation and limitations

Source: groupmate's `combined v2.ipynb`, final cell `eqEZxwLZnUSY`.
The notebook uses hosted flood classifier `floodwatch/4` and wheel detector version 5 (its printed v2 label is stale). The final cell uses bounding-box height, not the earlier Hough-circle variant.

The Android implementation keeps its existing flood classifier. It does not copy the notebook bug that treats top-class confidence >=10% as flood regardless of class. Wheel inference runs through a server-side proxy without packaging any API key in the APK.

Local pixel processing adapts the final brightness/saturation/row-gradient heuristic. It operates before drawing overlays, rejects flat/tiny crops and edge-clipped wheels, and filters wheel class/confidence before choosing the strongest prediction. A minimum visual-contrast requirement is experimental, not a calibrated water classifier. A curb, spoke, tire edge or shadow may still win. The search is restricted to 45–95% of box height and can miss water outside that range.

The preview explicitly assumes a 60 cm outside tire diameter. Box height may not describe a fully submerged tire; perspective and tire-size variation add error. No geometry or depth has been validated against measurements. Estimates are not saved as measured report depths or used to select road-passability categories. The yellow line remains an unverified candidate even when the wheel detector is confident.

# FloodWatch User's Guide — Five-Chapter Outline

## Purpose and scope

An English end-user guide based on the current Android layouts, Activities, Fragments, strings, manifest, architecture document, and separate admin web client. The visible roles are residents using Android and authorized administrators using a browser. Unconfirmed matters remain in [NEEDS_VERIFICATION.md](NEEDS_VERIFICATION.md).

The five-chapter progression is inspired by the broad task-oriented organization of the [Apple MacBook Air Getting Started Guide](https://support.apple.com/guide/macbook-air/welcome-apd16d505394/mac); no Apple features are implied.

## Length and writing rules

- **Main-text target: 17,350 words**, excluding title page, table of contents, screenshots, and appendices. At 300–350 words per page, approximately 50–58 content pages before final layout.
- Keep all topics below, but do not pad. If verified, useful, code-based English content runs short, note the shortfall in the relevant section file.
- Write clear English for end users; preserve exact visible UI labels and messages.
- Introduce each procedure with a bold task heading: **To [do the task]:** followed by numbered steps. Use a, b sub-steps only when needed.
- Use **Important:** callouts for critical warnings and brief notes for context.
- Add **Expected result** after major workflows.
- Cross-reference other content by **section title**, never by chapter number.
- Do not invent functionality or present FloodWatch as an official emergency service. Redact personal data in screenshots.

## Word budget and planned files

| Chapter | Section | Title | Target words | Planned file | Progress |
|---:|---:|---|---:|---|---|
| 1 | 1.1 | About FloodWatch and This Guide | 650 | `ch1-s1.md` | Written: 927 words in existing `chapter-01.md` |
| 1 | 1.2 | Getting Started: Access, Permissions, and Navigation | 1,050 | `ch1-s2.md` | Planned |
| 1 | 1.3 | Resident User Guide | 395 | `ch1-s3.md` | Planned |
| 1 | 1.4 | Account, Sign-up, Login, and Password Recovery | 1,550 | `ch1-s4.md` | Planned |
| 2 | 2.1 | Dashboard, Map, Weather, and Risk Information | 2,355 | `ch2-s1.md` | Written: 2,355 words |
| 2 | 2.2 | Flood Alerts and Verified Reports | 850 | `ch2-s2.md` | Planned |
| 2 | 2.3 | Creating and Submitting a Flood Report | 2,100 | `ch2-s3.md` | Planned |
| 2 | 2.4 | My Reports: Status, Filters, and Withdrawal | 1,250 | `ch2-s4.md` | Planned |
| 2 | 2.5 | Report-status Notifications and Badge | 850 | `ch2-s5.md` | Planned |
| 2 | 2.6 | Profile, Preferences, Emergency Contact, and Home Zone | 1,600 | `ch2-s6.md` | Planned |
| 3 | 3.1 | Troubleshooting and Frequently Asked Questions | 2,100 | `ch3-s1.md` | Planned |
| 4 | 4.1 | Safety, Privacy, and Responsible Use | 800 | `ch4-s1.md` | Planned |
| 5 | 5.1 | Administrator Guide | 1,800 | `ch5-s1.md` | Planned |
| **1 total** | | **Getting Started** | **3,645** | | |
| **2 total** | | **Using FloodWatch** | **9,005** | | |
| **3 total** | | **Troubleshooting and Frequently Asked Questions** | **2,100** | | |
| **4 total** | | **Safety, Privacy, and Responsible Use** | **800** | | |
| **5 total** | | **Administrator Guide** | **1,800** | | |
| **Grand total** | | | **17,350** | | |

**Accounting:** The completed Dashboard section is 2,355 words, 555 above its old 1,800-word plan. The old 950-word Resident User Guide is reduced by 555 to a short 395-word overview. All other targets stay unchanged. After section-title cross-references were updated, the existing `chapter-01.md` is 927 words against a 650-word plan; its actual count is recorded but does not change the planned total.

**Existing-file fit:** `docs/guide/chapter-01.md` supplies Section 1.1, **About FloodWatch and This Guide**. Do not rewrite or rename it now. In the assembled guide, place it beneath **Getting Started**; `ch1-s1.md` is the standardized future section filename.

---

# Detailed outline

# Chapter 1 — Getting Started

## 1.1 About FloodWatch and This Guide
**Target: 650 words** · Planned file: `docs/guide/ch1-s1.md` · Existing: `docs/guide/chapter-01.md` (927 words)

### What FloodWatch is
- Community flood-reporting and awareness app for **Sta. Ana, Taytay, Rizal**.
- Visible functions: dashboard map, verified-report markers, weather, active alerts, reporting, report history/status, and profile preferences.
- Resident reports require administrator review before becoming `VERIFIED`.

### Intended readers
- Residents using the Android app.
- Authorized administrators using the web panel.
- Shared concerns: safety, privacy, status interpretation, and troubleshooting.

### Limits
- Not an official warning, emergency-dispatch, hotline, rescue, chat, or route-navigation service.
- No resident report editing after submission.
- The visible risk card uses the newest verified report from the previous 24 hours; do not describe it as an official prediction.

### Guide conventions
- Define Note, Warning, Privacy, Expected result, and cross-reference conventions.
- Explain that Android permission dialogs can vary by OS/device.

## 1.2 Getting Started: Access, Permissions, and Navigation
**Target: 1,050 words** · Planned file: `docs/guide/ch1-s2.md`

### Requirements
- Android device, internet, and working email.
- Camera for report evidence and location services/GPS for report location/elevation.
- Browser and separately authorized account for admin use.
- Refer unconfirmed platform requirements to `NEEDS_VERIFICATION.md`.

### First launch
- Login is the launcher screen.
- Identify Email Address, Password, **Forgot?**, **Keep me signed in on this device**, **Login to Dashboard**, and **Sign Up**.
- A valid persisted session can open Home automatically when email is verified.

### Permissions
- Internet/network: authentication, database, images, maps, weather, alerts, sync.
- Fine location: position, coordinates/address, map marker, elevation fallback.
- Camera: in-app flood photo.
- Notifications on Android 13+: report-status updates.
- Explain allow, deny, and later enabling access in App settings.

### Navigation
- Bottom tabs: **Dashboard**, **Report**, **Alerts**, **My Reports**, **Profile**.
- Red Alerts badge for status events; tapping a system notification opens Alerts.

### Common states
- Progress indicators, disabled buttons, Toasts, Snackbars, dialogs, and empty states.
- Fixed app wording versus dynamic server errors.

### Session persistence
- Meaning of **Keep me signed in on this device**.
- Auto-entry is skipped after logout, password reset, or email-verification deep link.
- **Important:** Do not keep a session on a shared device.

## 1.3 Resident User Guide
**Target: 395 words** · Planned file: `docs/guide/ch1-s3.md`

### Resident capabilities and task map
- Keep this a short orientation, not a duplicate of detailed procedures.
- Account/verification → **Account, Sign-up, Login, and Password Recovery**.
- Dashboard/map/weather/risk → **Dashboard, Map, Weather, and Risk Information**.
- Active alerts/verified reports → **Flood Alerts and Verified Reports**.
- Location/photo report → **Creating and Submitting a Flood Report**.
- Own history, filters, withdrawal → **My Reports: Status, Filters, and Withdrawal**.
- Status alerts → **Report-status Notifications and Badge**.
- Profile/photo/contact/Home Zone/preferences → **Profile, Preferences, Emergency Contact, and Home Zone**.
- Responsible use → **Safety, Privacy, and Responsible Use**.
- Failures → **Troubleshooting and Frequently Asked Questions**.

### First-time and routine orientation
- Brief checklist: sign up, review permissions, inspect Dashboard/Alerts, complete Profile, read safety guidance.
- Genuine, current, relevant, permitted reports only; official directions override app labels.
- **Expected result:** Residents can find the complete procedure for their task without repeated instructions.

## 1.4 Account, Sign-up, Login, and Password Recovery
**Target: 1,550 words** · Planned file: `docs/guide/ch1-s4.md`

### Login
- Numbered procedure covering email, password, persistence choice, and **Login to Dashboard**.
- Loading: **Signing in…**; success opens Dashboard.
- Empty: **Empty fields are not allowed!**
- Failure: **Login failed: [server message]**
- Unverified dialog: **Email Not Verified** and **Please check your inbox and click the verification link before logging in.**
- **Expected result:** Dashboard opens for a verified account.

### Sign-up form
- Full Name, Phone Number, Email Address, Password, Confirm Password, legal checkbox, **Create Account**.
- Full name, email, and password are client-required; confirmation must match. Phone is submitted but is not in the initial required check.
- Clickable **Terms of Service** and **Privacy Policy**.

### Sign-up validation
- **Please fill in all required fields!**
- **Enter a valid email address.**
- **Password must be at least 12 characters with uppercase, number, and special character (!@#$%^&*).**
- **Passwords do not match!**
- **Please accept the Terms of Service.**
- Server error: **Error: [server message]**

### Account creation and email verification
- Numbered procedure; loading text **Creating account…**.
- **Verify Your Email** dialog, recipient, and **Go to Login**.
- Deep-link dialog: **Email Verified!** / **Your email has been verified. You can now log in to your FloodWatch account.**
- **Expected result:** The verified user can log in.

### Legal documents
- Toolbar title, scrolling body, back arrow, **I Understand**.
- Terms topics: purpose, emergency disclaimer, account, content, prohibited conduct, providers, suspension, changes, contact.
- Privacy topics: collected data, location/report data, use, providers, sharing, retention, security, rights, children, updates, contact.
- Embedded version `1.0`, last updated September 20, 2026.

### Forgot Password
- Numbered procedure using email and **Send Reset Link**.
- **Please enter your email address.** / **Enter a valid email address.**
- Loading: **Sending…**
- Success: **Check your inbox!**, recipient/spam reminder, **Back to Login**.
- **Too many requests. Please wait a few minutes and try again.**
- **Invalid email address. Please check and try again.**
- **Failed to send reset link. Please try again.**
- **Expected result:** Success card confirms that a reset email was requested.

### Reset Password
- **Verifying reset link…**, New Password, Confirm Password, **Update Password**.
- **Please fill in both fields.**; same strong-password rule; **Passwords do not match.**
- Loading: **Updating...**
- Success: **Password Updated!**, automatic redirect, **Back to Login**.
- **The reset link has expired. Please request a new one.**
- **That password is not accepted. Please choose a stronger password.**
- **Failed to update password. Please check your connection and try again.**
- **The reset link is invalid or expired. Please request a new one.**
- **Expected result:** Recovery session clears and Login opens, with email prefilled when available.

---

# Chapter 2 — Using FloodWatch

## 2.1 Dashboard, Map, Weather, and Risk Information
**Target: 2,355 words** · Planned file: `docs/guide/ch2-s1.md` · **Already written:** `docs/guide/ch2-s1.md` (2,355 words)

### Screen tour
- FloodWatch header; **Monitoring • Sta. Ana, Taytay**; weather; hybrid map and traffic; location/zoom controls; Weather Map, alert, risk, rainfall, wind, active alerts, evacuation status.

### Verified markers
- Fixed service-area start position.
- Only `VERIFIED` reports from the previous 24 hours.
- Marker: **✓ Verified Flood Report** plus level, passability, reported time.
- Location control moves to device location but does not change service-area queries.

### GPS elevation
- Numbered location-control procedure.
- **Reading GPS elevation...**, result with optional accuracy, **GPS permission required**, **GPS elevation unavailable**.
- **Expected result:** Altitude updates and map moves when position is available.

### Weather
- Temperature °C, condition, humidity, last-hour rainfall mm, wind km/h; sun/rain/cloud icon.
- Failure: **Weather unavailable**, **--°C**, **--% Humidity**, and **--**.
- **Note:** Third-party weather can be delayed or unavailable.

### Weather layers
- **🌧 Rain**, **🌡 Temp**, **☁ Cloud** replace the current overlay and update **Last updated**.
- **Rain layer is active. No rainfall is currently detected in this area.**
- Layer loads after tap, not automatically.
- **Expected result:** Selected overlay appears and its button is highlighted.

### Latest alert
- Newest active alert supplies title/message/relative time and **Details**.
- **Updated just now**, minute/hour/day variants.
- **No active alerts** / **All clear. No flood incidents reported.**
- Failure: **Alerts unavailable** / **Check your connection and try again.**

### Evacuation label
- Advisory → **MONITOR**; Watch → **STANDBY**; Warning → **READY**; Critical → **EVACUATE**; none → **NORMAL**.
- Error: count **--**, status **UNKNOWN**.
- **Important:** Follow official instructions.

### Risk card
- Newest verified report in 24 hours wins, not the oldest/highest.
- LOW → Low Threat/PASSABLE/25%; MODERATE → Moderate Threat/CAUTION/50%; HIGH → High Threat/AVOID AREA/75%; CRITICAL → Critical Threat/NOT PASSABLE/100%; none → No Verified Risk/STABLE/0%.
- Detail: **Risk is based on the latest verified report from the last 24 hours.**
- Failure: **Data unavailable**, **UNKNOWN**, zero progress.

### Refresh
- On load/resume and about every 15 minutes while active.
- **Last updated** is client refresh time, not proof every source is new.
- No manual Dashboard refresh button.

## 2.2 Flood Alerts and Verified Reports
**Target: 850 words** · Planned file: `docs/guide/ch2-s2.md`

### Layout
- **📢 Flood Alerts** and **📋 Verified Flood Reports**; scrolling/card structure.

### Active alerts
- Active records only; severity, title, full message; Advisory/Watch/Warning/Critical.
- **No active alerts.**
- **Error loading alerts: [message]**

### Verified reports
- Newest first; severity, status, time, level, passability, address, description, optional image.
- Fallbacks: N/A, Unknown, **No address**, **No description**.
- **No verified reports yet.**
- **Error loading reports: [message]**

### Interpretation
- Alert = active administrator broadcast; verified report = reviewed location/time observation.
- Dashboard shows only latest alert; Alerts lists all active alerts.
- **Important:** Check timestamps and official advisories.

## 2.3 Creating and Submitting a Flood Report
**Target: 2,100 words** · Planned file: `docs/guide/ch2-s3.md`

### Safety
- Do not enter floodwater or hazards for a photo; submit genuine/current information and permitted content.
- Workflow: depth → location → photo → review → submit → `pending`.

### Screen tour
- **Report Flood Incident**, lite map, **Auto-detect My Location**, **CURRENT ADDRESS**, depth choices, guidance, **Open Camera**, **Submit Flood Report**.
- Despite an internal “Gallery” ID, no report-photo gallery picker is implemented.

### Depth mapping
- 0–5 cm → LOW/1/Passable.
- 6–15 cm → LOW/1/Motorcycles not advised.
- 16–30 cm → MODERATE/2/Small cars not advised.
- 31–50 cm → HIGH/3/Only large vehicles.
- 50+ cm → CRITICAL/4/Not passable.
- Default is 0–5 cm; app computes level/severity/guidance.

### Location
- Numbered procedure. Loading **Detecting…**; success marker **Your Location**, address or `Lat: …, Lng: …`, **Re-detect Location**.
- Denial: **Location permission denied**.
- Null/failure only resets to **Auto Detect Location**.
- **Expected result:** Address/coordinates and detected-location state are available.

### Elevation
- MSL when supported, otherwise GPS, otherwise unavailable; terrain service can replace it.
- Labels: terrain, MSL, GPS approx., optional ± accuracy, **Elevation: unavailable**.
- Terrain failure silently retains device fallback.

### Camera Capture
- Requires detected location: **Please tap Auto Detect Location before opening the camera**.
- **Camera permission denied**, **Unable to open camera**, **Photo capture failed**.
- Portrait preview, close control, progress, **CAPTURE**, temporary JPEG.

### Photo Preview
- EXIF rotation/downsampling; overlay shows service area, address, coordinates, elevation/source, timestamp.
- **Is this photo clear? You can retake it if needed.**
- **Use Photo**, **Retake**; non-cancelable outside; **Could not load photo**.
- **Expected result:** Use Photo retains the capture.

### Confirmation
- Missing inputs: **Please capture a photo and detect location first**.
- **Confirm Flood Report** shows depth, level, guidance, elevation; **Submit** / **Cancel**.
- No user description field; description is generated.

### Submission
- Numbered procedure; progress visible and button disabled.
- JPEG 90% with overlay; stores user, URL, address, coordinates, level, passability, severity, generated description, status `pending`.
- **✅ Report submitted successfully!**
- State resets to **Detecting location...** and auto-detect.
- **Expected result:** Pending report appears in My Reports after refresh.

### Failure and retry
- **Submission Error** / **Detail: [technical/server message]**.
- Failed database insert triggers attempted upload cleanup.
- Check connection, avoid repeated taps, redetect/recapture if needed, retry once.
- **Important:** Repeated submission can create duplicates.

### Realtime report-map markers
- Insert events add markers titled with address or **New Flood Report**.
- Do not describe these as verified; Dashboard markers are verified-only.

## 2.4 My Reports: Status, Filters, and Withdrawal
**Target: 1,250 words** · Planned file: `docs/guide/ch2-s4.md`

### Screen and pagination
- Title/subtitle, chips, **Refresh**, progress, cards, **Load more**; newest first, 20 per page.
- Profile cards can open all or verified-only (**Verified Reports**).

### Filters
- **All**, **Pending**, **Verified**, **Dismissed**, **Needs attention** (`INVALID_IMAGE` + `INVALID_INFORMATION`), **Withdrawn**.
- Verified green; dismissed/invalid/withdrawn red; others amber.

### Empty/error states
- **You have not submitted any reports yet.**
- **You do not have any verified reports yet.**
- **You do not have any pending reports.**
- **You do not have any dismissed reports.**
- **You do not have reports that need attention.**
- **You do not have any withdrawn reports.**
- **Please sign in again to view your reports**.
- **Unable to load your reports. Please try again.** / **Reports could not be loaded**.

### Refresh and Load more
- Refresh clears/reloads; Load more appears after a full 20-item page; controls disabled while loading.
- **Expected result:** Newest matching reports appear.

### Cards
- Severity, uppercase status, time, **Level: … | Passable: …**, address, description, image.
- **Withdraw report** only for a current Pending owned report.

### Withdrawal
- Numbered procedure; **Withdraw report?** explains review stops, history remains, and no repeat/edit.
- **Cancel** / **Withdraw**.
- **Report withdrawn.**
- **This report is no longer pending and cannot be withdrawn.**
- **Only pending reports can be withdrawn.**
- **Unable to withdraw the report. Please try again.**
- **Expected result:** Report appears under Withdrawn after reload.
- No resident delete or restore for Withdrawn.

### Needs attention
- Explain Invalid Image and Invalid Information; no in-place correction/edit.
- Do not invent a remedy; reference `NEEDS_VERIFICATION.md`.

## 2.5 Report-status Notifications and Badge
**Target: 850 words** · Planned file: `docs/guide/ch2-s5.md`

### Trigger and channel
- Realtime update to current user's report; statuses PENDING, VERIFIED, DISMISSED, INVALID_IMAGE, INVALID_INFORMATION.
- Local deduplication by report ID/status.
- Android channel **Report status updates**, high priority; tap opens Alerts.
- Android 13+ permission; red Alerts badge clears when Alerts opens.

### Exact notifications
- **Flood report verified** — **Your report at [address] was verified by the admin.**
- **Flood report dismissed** — **Your report at [address] was dismissed by the admin.**
- **Report needs a valid image** — **Your report at [address] was not approved because of its image.**
- **Report information not verified** — **Your report at [address] was not approved because its information could not be verified.**
- **Flood report under review** — **Your report at [address] was returned to pending review.**
- Address fallback: **your submitted location**.

### Missing notifications
- Check Profile switch, Android permission/settings, connectivity, and Realtime availability.
- Alerts/My Reports remain available; refresh My Reports for authoritative status.
- Reference preference consistency in `NEEDS_VERIFICATION.md`.

## 2.6 Profile, Preferences, Emergency Contact, and Home Zone
**Target: 1,600 words** · Planned file: `docs/guide/ch2-s6.md`

### Overview and statistics
- Photo, name/email, submitted/verified counts, Emergency Settings, Notification Preferences, Edit Profile, Change Password, Log Out.
- Fallback **FloodWatch User**; failed counts `--`.
- **REPORTS SUBMITTED** opens all reports; **VERIFIED SUBMISSIONS** opens verified only.
- **Unable to load report statistics** with **Retry**.

### Edit Profile
- Numbered procedure for Full name, Phone number, Address.
- **Name is required**; blank phone/address allowed; no format validation.
- **Profile updated**; **Could not update profile: [message]**.
- **Unable to refresh profile** with **Retry**.
- **Expected result:** Displayed name/Home Zone updates.

### Profile photo
- Tap photo/edit icon; JPEG/PNG/WebP, maximum 5 MB.
- **Please select a JPEG, PNG, or WebP image**.
- **Profile photo must be 5 MB or smaller**.
- **Upload failed: Unable to read selected image** / **Upload failed: [message]**.
- **Profile photo updated**.
- **Expected result:** New circular photo appears.

### Emergency Contact
- Account-scoped local setting; default **Family or Friends**.
- **Call [name]**, **Send SMS**, **Edit contact**.
- **Add a phone number first**; fields Contact name/Phone number; blank name uses default; no strict number validation.
- **Emergency contact saved**.
- Dialer/messaging app performs the external action; FloodWatch does not place/send it.
- **Expected result:** Saved contact appears in Profile.

### Home Zone
- Saved address or **Sta. Ana, Taytay, Rizal**; tap opens external map/browser fallback.
- Describe as address shortcut, not geofence/subscription.

### Report Status Alerts
- On by default; Android 13+ may request permission.
- **Notifications are blocked. You can enable them in App settings.** with **Settings**.
- Turning off saves a local preference; reference **Report-status Notifications and Badge** and verification file.

### Change Password
- **Send a password reset link to [email]?**
- **No email is linked to this account**.
- **Reset link sent to [email]** / **Unable to send reset link**.
- Continue in **Account, Sign-up, Login, and Password Recovery**.

### Logout and expired session
- Numbered procedure; **Log out?**, **Are you sure you want to log out of FloodWatch?**, **Cancel**, **Log out**, **Signing out…**.
- Local session clears even on remote network error.
- **Expected result:** Login opens without auto-restoration.
- Missing user redirects; actions may show **Please sign in again**.

---

# Chapter 3 — Troubleshooting and Frequently Asked Questions

## 3.1 Troubleshooting and Frequently Asked Questions
**Target: 2,100 words** · Planned file: `docs/guide/ch3-s1.md`

### Diagnostic checklist
1. Confirm internet.
2. Confirm verified email and valid session.
3. Check Location, Camera, Notifications permissions.
4. Reopen the tab or use **Refresh**.
5. Do not repeat actions while loading.
6. Record exact message, screen, time, and redacted details.

### Account/recovery
- Cover empty login, auth failure, unverified email, spam/junk, shared-device auto-login, expired session.
- Invalid/expired reset link: request new and open newest.
- Review password rule and rate limit; Login redirect after reset is expected.

### Dashboard/map/weather
- Slow map: check connection/services and revisit.
- No markers: verified and under 24 hours only.
- No-rain Toast means zero/missing last-hour reading.
- Weather/alert/data unavailable: reconnect and compare official sources.
- GPS elevation unavailable: enable precise location/services; seek better reception only if safe.

### Location/camera/report
- Permission denied: enable in App settings.
- Silent detection reset: GPS/provider returned no location; wait/retry.
- Coordinates instead of address: geocoder did not return street address.
- Camera open/capture/load failures: close competing apps, check storage, retake.
- Missing photo/location: complete both and choose **Use Photo**.
- Submission Error: retain detail, reconnect, avoid duplicates, retry once.

### My Reports/Profile
- Empty filter: All/Refresh; load error: reconnect/sign in.
- Withdrawal requires current Pending status.
- Needs attention has no edit action.
- Missing notification: **Report-status Notifications and Badge** plus manual refresh.
- Profile/statistics: **Retry**; photos must be accepted type and ≤5 MB.
- Emergency actions require device handlers; fix Home Zone through Address; notification denial through **Settings**.

### Admin troubleshooting
- **Please fill in all fields.**
- **This account is not authorized for the admin panel.**
- **Error loading reports** / **Error: [message]**.
- Empty lists can be valid; if Realtime stalls, reload, confirm session/connection, verify visible state before repeating.

### FAQ
- Do reports immediately appear on Dashboard? No: verified and within 24 hours only.
- Can an existing gallery photo be used? No current gallery picker.
- Can residents edit/delete? No edit; only withdraw eligible Pending, which remains in history.
- Why is 6–15 cm LOW? Current mapping, with different vehicle guidance.
- Needs attention? Invalid Image + Invalid Information.
- Alert versus verified report? Admin broadcast versus reviewed observation.
- Emergency Contact a rescue request? No; opens dialer/SMS.
- Home Zone? External-map address shortcut, not visible geofence.
- Refresh frequency? Load/resume and about 15 minutes while active.
- Elevation difference? GPS/MSL versus terrain references.
- Offline? Core functions require network; no visible offline queue.
- Admin access? Only specifically authorized account; details restricted.

---

# Chapter 4 — Safety, Privacy, and Responsible Use

## 4.1 Safety, Privacy, and Responsible Use
**Target: 800 words** · Planned file: `docs/guide/ch4-s1.md`

### Data
- Name, email, optional phone, profile photo, address, emergency contact, identifiers, notification preference.
- Report coordinates, address, elevation, condition, time, photo; verified report data may be displayed publicly in-app.

### Photo metadata
- Report overlay contains location/address/time/elevation.
- Check for faces, plates, house numbers, private data, permission, and clarity before **Use Photo**.
- Distinguish profile photos from report photos.

### Providers
- Supabase, Google Maps, OpenWeather, Open-Meteo/Copernicus DEM; availability/accuracy outside direct app control.

### Safe interpretation
- One verified point does not make a route safe.
- **PASSABLE**, **STABLE**, **NORMAL** do not override barriers/officials/current observation.
- Official instructions outrank evacuation labels.

### Security
- Unique password, protected email, no credential sharing, no persistence on shared devices, sign out after use.
- Official support/privacy contacts remain in `NEEDS_VERIFICATION.md`.

---

# Chapter 5 — Administrator Guide

## 5.1 Administrator Guide
**Target: 1,800 words** · Planned file: `docs/guide/ch5-s1.md`

> The admin panel is a separate static browser client, not an Android screen.

### Login/access
- Email, Password, **Sign In →**; Enter in password also signs in.
- **Please fill in all fields.**; server auth error shown directly.
- **This account is not authorized for the admin panel.** then sign-out.
- Authorized stored session opens dashboard; non-admin stored session signs out.
- Never publish the hard-coded admin email or credentials.

### Dashboard
- Admin email/**Sign Out**; Pending, Verified, Active Alerts, Report History counts.
- Pending/Verified/History tabs and counts; Post Flood Alert; Active Alerts; Realtime reloads.

### Review Pending
- Card: status, severity, date, optional photo, level, address, description.
- Image lightbox closes by control, overlay, or Escape.
- **✅ Verify / Categorize** modal:
  - **Verified** — Report is accurate and confirmed.
  - **Dismissed** — Report is not confirmed or no longer actionable.
  - **Invalid Image** — Photo is missing, fake, or irrelevant.
  - **Invalid Information** — Details are inaccurate or unverifiable.
- **Cancel** changes nothing; errors **Error: [message]**.
- Include an explicitly editorial checklist for time/address/image/depth consistency.
- **Expected result:** Item moves to matching group and counts refresh.

### Verified and History
- Verified: **↩️ Unverify** → PENDING; **Updated!**.
- History combines dismissed, invalid image, invalid information, withdrawn, newest first.
- Non-withdrawn: **↩️ Restore** or **🗑️ Delete** with **Delete this report permanently?**.
- Withdrawn has no actions.
- **🗑️ Report deleted!** / **Error: [message]**.
- **Important:** Permanent deletion is not recoverable.

### Post Flood Alert
- Title/Message required; **🟢 Advisory** default, **🟡 Watch**, **🔴 Warning**, **🟣 Critical**.
- **Please fill in title and message**; **Posting…**; **📢 Alert posted!**; **Error: [message]**.
- Cross-reference evacuation mapping in **Dashboard, Map, Weather, and Risk Information**.
- **Expected result:** Alert appears in Active Alerts and resident active-alert screens.

### Active Alerts
- Newest first; title, severity, 50-character preview, **Deactivate**.
- **Alert deactivated**; inactive alert disappears from resident active queries.
- No edit/reactivate/history UI.

### Empty states and sign-out
- **No pending reports**, **No verified reports**, **No history reports**, **No active alerts**.
- **Sign Out** removes channels, signs out, reloads.
- **Expected result:** Admin Login returns.
- **Important:** Sign out on shared workstations.

### Limitations
- See `NEEDS_VERIFICATION.md` for URL, browsers, provisioning, audit, retention, and publication access.
- Do not claim bulk review, search, extra sorting, alert editing, or user management.

---

# Essential screenshot plan (28 total)

Each numbered item is one screenshot. Use release-like builds and synthetic/redacted data; there are no uncounted insets. The section column follows the new five-chapter reading order.

| # | Section | Screen | Required content |
|---:|---|---|---|
| 1 | 1.4 Account, Sign-up, Login, and Password Recovery | Login | Full form and actions |
| 2 | 1.4 | Email Not Verified | Full dialog |
| 3 | 1.4 | Sign-up | All fields, legal checkbox, Create Account |
| 4 | 1.4 | Sign-up validation | Strong-password or representative Toast |
| 5 | 1.4 | Legal document | One full Terms or Privacy screen; no inset |
| 6 | 1.4 | Forgot Password | Form |
| 7 | 1.4 | Forgot success | Check your inbox card |
| 8 | 1.4 | Reset Password | Verified form |
| 9 | 2.1 Dashboard, Map, Weather, and Risk Information | Dashboard | Full overview |
| 10 | 2.1 | Dashboard marker | Open verified-marker details |
| 11 | 2.1 | Weather layers | Selected overlay and timestamp |
| 12 | 2.1 | Alert details | Dialog |
| 13 | 2.1 | Risk details | 24-hour dialog |
| 14 | 2.2 Flood Alerts and Verified Reports | Alerts | Active alert and verified card |
| 15 | 2.3 Creating and Submitting a Flood Report | Report | Full form |
| 16 | 2.3 | Detected location | Marker/address/Re-detect |
| 17 | 2.3 | Camera Capture | Safe preview and CAPTURE |
| 18 | 2.3 | Photo Preview | Overlay and actions |
| 19 | 2.3 | Confirm Flood Report | Summary and actions |
| 20 | 2.4 My Reports: Status, Filters, and Withdrawal | My Reports filters | Six chips and cards |
| 21 | 2.4 | Report card | All displayed fields |
| 22 | 2.4 | Withdraw report | Confirmation |
| 23 | 2.5 Report-status Notifications and Badge | Android notification | Redacted status notification/badge |
| 24 | 2.6 Profile, Preferences, Emergency Contact, and Home Zone | Profile | Full overview |
| 25 | 2.6 | Edit Profile | Dialog |
| 26 | 2.6 | Emergency Contact | Three actions |
| 27 | 5.1 Administrator Guide | Admin dashboard | Counts, tabs, report, alert form; no inset |
| 28 | 5.1 | Admin categorization | Separate four-outcome modal |

### Screenshot rules
- Redact emails, phones, addresses, coordinates, IDs, faces, and plates.
- Keep consistent device/browser dimensions.
- Capture complete Toast/Snackbar/dialog text.
- Do not imply a sample is a real current emergency.
- Maximum remains **28**; replace rather than add.
- The five existing placeholders in `docs/guide/ch2-s1.md` correspond to screenshots 9–13 and require no change.

---

# Appendices (excluded from main target)

## Appendix A — Glossary
- Alert severities; report statuses; flood depth/level/severity/passability; GPS/MSL/terrain; Realtime, badge, service area, Home Zone.

## Appendix B — Reference tables
- Report statuses; depth-level-severity-passability; alert-to-evacuation mapping; risk mapping.

## Appendix C — Error and validation index
- Exact message, screen, likely cause, troubleshooting reference; identify dynamic placeholders.

## Appendix D — Permission checklist
- Network, precise location, camera, Android 13+ notifications; generic Android steps only.

## Appendix E — Privacy/data checklist
- Entered data, report metadata, local preferences, providers, screenshot redaction; reference in-app legal screens.

## Appendix F — Quick task cards
- Resident and administrator core workflows.

## Appendix G — Support worksheet
- App/build, device/OS, screen, time, exact error, network, permissions, redacted screenshot, verified contact.

## Appendix H — Document control
- Guide/app/legal versions, date, owner, approver, revision history, unresolved verification references.

---

# Screen and feature coverage

| Surface | New section coverage |
|---|---|
| Login, Sign-up, Legal, Forgot/Reset Password | **Account, Sign-up, Login, and Password Recovery**; **Troubleshooting and Frequently Asked Questions** |
| Requirements, permissions, initial navigation, sessions | **Getting Started: Access, Permissions, and Navigation** |
| Resident orientation and responsibilities | **Resident User Guide**; **Safety, Privacy, and Responsible Use** |
| HomeActivity navigation, badge, notifications | **Getting Started: Access, Permissions, and Navigation**; **Report-status Notifications and Badge** |
| Dashboard/map/weather/alerts/risk | **Dashboard, Map, Weather, and Risk Information**; **Troubleshooting and Frequently Asked Questions** |
| Report and Camera Capture | **Creating and Submitting a Flood Report**; **Safety, Privacy, and Responsible Use**; **Troubleshooting and Frequently Asked Questions** |
| Alerts | **Flood Alerts and Verified Reports**; **Report-status Notifications and Badge** |
| My Reports / report cards | **My Reports: Status, Filters, and Withdrawal** |
| Profile | **Profile, Preferences, Emergency Contact, and Home Zone**; **Report-status Notifications and Badge** |
| Permissions, deep links, FileProvider | **Getting Started: Access, Permissions, and Navigation**; **Account, Sign-up, Login, and Password Recovery**; [NEEDS_VERIFICATION.md](NEEDS_VERIFICATION.md) |
| Admin web login/dashboard/reports/alerts/lightbox | **Administrator Guide**; **Troubleshooting and Frequently Asked Questions** |

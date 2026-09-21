## 2.1 Dashboard, Map, Weather, and Risk Information

The Dashboard brings together the main flood-awareness information for **Sta. Ana, Taytay**. It shows the service area, current weather values, a map, recent verified flood reports, the latest active flood alert, a risk summary, rainfall, wind speed, the number of active alerts, and an evacuation-status label.

The Dashboard uses several online services. The map, weather, alerts, and report information may finish loading at different times. A value that is still loading, unavailable, or shown as `--` should not be treated as a confirmed safe condition.

> **Important:** FloodWatch supports community awareness. It does not replace official government warnings, emergency responders, road closures, or evacuation instructions. Always follow authorities and the conditions you can safely observe.

### Dashboard overview

At the top of the screen, the app shows **FloodWatch** and the service-area status **Monitoring • Sta. Ana, Taytay**. The weather summary displays the current condition, temperature in degrees Celsius, and humidity. Below it, the hybrid map combines road information with satellite imagery. Traffic is enabled after the map's base imagery finishes loading.

The lower part of the Dashboard contains:

- **Weather Map**, with **🌧 Rain**, **🌡 Temp**, and **☁ Cloud** layer controls.
- **Last updated**, which records when the app refreshed the Dashboard.
- An **ACTIVE NOW** alert card with a title, message, relative update time, and **Details**.
- A risk card with a threat label, area, road-status label, progress bar, and **Details**.
- **RAINFALL (1H)** in millimeters.
- **WIND SPEED** in kilometers per hour.
- **ACTIVE ALERTS**, displayed as a two-digit count when data is available.
- **EVAC. STATUS**, which summarizes the highest severity among active alerts.

[Screenshot: Dashboard]

**To open and review the Dashboard:**

1. Sign in to FloodWatch with a verified account.
2. Tap **Dashboard** in the bottom navigation if another screen is open.
3. Wait for the weather values, map, alert card, and risk card to finish loading.
4. Check **Last updated** before interpreting the information.
5. Read each card together. Do not use one value by itself to decide that an area is safe.

**Expected result:** The Dashboard displays **Monitoring • Sta. Ana, Taytay**, weather values, the hybrid map, alert information, risk information, and the four summary metrics.

### Understanding the map

The map initially opens over the configured service area. It uses a hybrid map view. The **+** and **−** controls zoom in and out. A separate location control, identified by its location symbol, can move the map to the device's current position when location permission and a usable position are available.

The Dashboard does not show every submitted report. A map marker is added only when a report:

1. Has been categorized as `VERIFIED` by an administrator; and
2. Was submitted within the previous 24 hours.

The age of the report is based on its submission time. Once it falls outside the 24-hour window, it no longer qualifies for the Dashboard's active verified-report markers.

Each qualifying report uses a red marker titled **✓ Verified Flood Report**. Its information contains the reported flood level, passability guidance, and report time. When data is missing, the marker can show `N/A` for the level or `Unknown` for passability.

[Screenshot: Dashboard marker]

**To inspect a verified flood-report marker:**

1. Open **Dashboard**.
2. Wait for the map and report data to load.
3. Use **+** or **−** if you need to change the zoom level.
4. Tap a red verified-report marker.
5. Read **✓ Verified Flood Report** and the details below it, including `Level`, `Passable`, and `Reported`.
6. Compare the report time with **Last updated** and any active alert before making a decision.

**Expected result:** The selected marker displays its verified-report title and the available level, passability, and reported-time details.

> **Important:** A marker describes one submitted location at one time. It does not prove that the surrounding road, an entire route, or the current conditions are safe.

If no marker appears, it does not necessarily mean there is no flooding. There may be no verified report from the previous 24 hours, report data may still be loading, or data may be unavailable. See **Troubleshooting and Frequently Asked Questions** for map and connection checks.

### Checking your location and GPS elevation

The map's location control requests precise location access when needed. When access is available, FloodWatch tries to read the device's current high-accuracy location and GPS altitude. It also enables the map's blue location indicator and moves the map toward the detected position.

This GPS elevation belongs to the device's detected location. It is separate from the fixed Sta. Ana, Taytay location used for Dashboard weather and service-area data.

**To check your GPS elevation:**

1. Open **Dashboard** and wait for the map to appear.
2. Tap the location control on the map.
3. If Android asks for location access, allow precise location access if you want to use this function.
4. Wait while the elevation label shows **Reading GPS elevation...**.
5. Read the result displayed as `GPS elevation: [value] m`. When the device provides vertical accuracy, the app also shows an approximate `±` value.

**Expected result:** The elevation label shows the GPS altitude, the blue location indicator is enabled when possible, and the map moves to the detected device position.

If permission is not granted, the label shows **GPS permission required**. If the device returns a position without altitude, or the altitude request fails, it shows **GPS elevation unavailable**.

> **Important:** GPS altitude can be approximate. Do not use it as the only basis for judging flood depth, building safety, or evacuation decisions.

### Reading current weather information

The weather summary is requested for the configured service-area coordinates. It includes:

- Temperature, rounded to a whole number and shown in °C.
- A weather condition such as Clear, Rain, or Cloud when supplied by the weather service.
- Relative humidity as a percentage.
- Rainfall during the latest one-hour period, shown with one decimal place in millimeters.
- Wind speed, converted to kilometers per hour and shown as a whole number.

The weather icon changes according to the returned condition. The app uses a sun icon for high temperature or clear/default conditions, a rain icon for a condition containing Rain, and a cloud icon for a condition containing Cloud.

**To review the weather readings:**

1. Open **Dashboard**.
2. Read the condition, temperature, and humidity at the top of the screen.
3. Scroll to **RAINFALL (1H)** and note the value in `mm`.
4. Read **WIND SPEED** and note the value in `km/h`.
5. Check **Last updated** to understand when the app most recently refreshed.

**Expected result:** The Dashboard shows a condition, temperature, humidity, one-hour rainfall, and wind speed when the weather service responds successfully.

If weather data cannot be loaded, the condition changes to **Weather unavailable**. Temperature becomes **--°C**, humidity becomes **--% Humidity**, and rainfall and wind speed show **--**. These are unavailable-data indicators, not zero values.

### Using Weather Map layers

The hybrid map can display one OpenWeather tile layer at a time. The layer is not automatically placed on the map during its initial load. Select a layer with one of the three buttons:

- **🌧 Rain** for precipitation tiles.
- **🌡 Temp** for temperature tiles.
- **☁ Cloud** for cloud tiles.

Selecting another button removes the current weather overlay and adds the new one. The selected button changes color, and **Last updated** is refreshed. The active overlay's tile cache is also refreshed during the Dashboard's scheduled data refresh.

[Screenshot: Weather layers]

**To display a weather layer:**

1. Open **Dashboard** and wait for the hybrid map to load.
2. Find the **Weather Map** card.
3. Tap **🌧 Rain**, **🌡 Temp**, or **☁ Cloud**.
4. Wait for the selected tiles to appear over the map.
5. Use **+** or **−** to inspect the displayed area at a different zoom level.
6. Check **Last updated** after switching layers.

**Expected result:** One selected weather overlay appears over the hybrid map, and the selected layer button is highlighted.

When **🌧 Rain** is selected and the latest one-hour rainfall reading is zero, the app shows **Rain layer is active. No rainfall is currently detected in this area.** This message confirms that the rain layer was selected; it does not guarantee that every nearby location is dry.

> **Important:** Weather tiles and numeric weather readings come from an external provider and may load at different times. A tile's appearance should be read together with the current values, report times, and official weather information.

### Reading the latest active alert

The **ACTIVE NOW** card displays the newest alert among records currently marked active by an administrator. It shows the alert title, message, and a relative time. Depending on age, the time can appear as **Updated just now**, `Updated [number] min ago`, `Updated [number] hr ago`, or `Updated [number] day/days ago`.

Only the newest active alert is summarized in this Dashboard card. The **Flood Alerts and Verified Reports** section of the guide explains the Alerts screen, which lists all currently active alerts.

[Screenshot: Alert details]

**To read the latest alert in full:**

1. Open **Dashboard**.
2. Locate the **ACTIVE NOW** card.
3. Read its title, message, and update time.
4. Tap **Details**.
5. Read the dialog title and full message.
6. Tap **OK** to close the dialog.

**Expected result:** A dialog displays the same current alert title and message shown on the card.

When there are no active alerts, the card shows **No active alerts** and **All clear. No flood incidents reported.** When alert data cannot be loaded, it shows **Alerts unavailable** and **Check your connection and try again.**

The no-active-alert message only describes the app records returned at that time. It is not an official all-clear for every place or hazard.

### Understanding ACTIVE ALERTS and EVAC. STATUS

**ACTIVE ALERTS** shows the number of currently active administrator alerts. Successful counts use two digits, such as `00` or `01`.

**EVAC. STATUS** is derived from the highest severity among those active alerts:

| Highest active alert severity | EVAC. STATUS |
|---|---|
| Advisory | **MONITOR** |
| Watch | **STANDBY** |
| Warning | **READY** |
| Critical | **EVACUATE** |
| No active alert | **NORMAL** |

If active-alert data cannot be loaded, the count changes to **--** and the evacuation status becomes **UNKNOWN**.

**To check the alert summary:**

1. Read **ACTIVE ALERTS** for the current count.
2. Read **EVAC. STATUS** beside it.
3. Review the **ACTIVE NOW** card for the newest alert.
4. Open the Alerts screen when you need to read every active alert, as described in **Flood Alerts and Verified Reports**.
5. Follow official evacuation instructions even if the app shows a less urgent label.

**Expected result:** The count and status summarize the active alert records currently available to the app.

> **Important:** **MONITOR**, **STANDBY**, **READY**, **EVACUATE**, **NORMAL**, and **UNKNOWN** are app summaries. They do not replace an evacuation order or other direction from authorities.

### Understanding the flood-risk card

The risk card is based on the newest qualifying verified flood report from the previous 24 hours. It does not average all reports and does not select an older report merely because that older report was more severe.

The app maps report severity to the following display:

| Report severity | Threat label | Area status | Progress |
|---|---|---|---:|
| LOW / 1 | **Low Threat** | **PASSABLE** | 25% |
| MODERATE or MEDIUM / 2 | **Moderate Threat** | **CAUTION** | 50% |
| HIGH / 3 | **High Threat** | **AVOID AREA** | 75% |
| CRITICAL / 4 | **Critical Threat** | **NOT PASSABLE** | 100% |
| No qualifying verified report | **No Verified Risk** | **STABLE** | 0% |

The card also identifies the monitored area as **Sta. Ana, Taytay**. Its progress bar is a visual representation of the mapped severity above; it is not a water-depth measurement.

[Screenshot: Risk details]

**To check the flood risk:**

1. Open **Dashboard** and wait for report data to load.
2. Find the risk card.
3. Read the threat label, area, and area-status label.
4. Review the progress bar only as a severity indicator.
5. Tap **Details**.
6. Read `Area status: [status]` and **Risk is based on the latest verified report from the last 24 hours.**
7. Tap **OK** to close the dialog.
8. Compare the result with the marker's reported time, active alerts, and official information.

**Expected result:** The dialog repeats the current area status and explains the 24-hour verified-report basis for the risk.

If report data cannot be loaded, the card shows **Data unavailable**, status **UNKNOWN**, and an empty progress bar. If the card shows **No Verified Risk** and **STABLE**, it means no qualifying verified report was used. It does not prove that flooding is absent.

> **Important:** Never interpret **PASSABLE**, **STABLE**, or a low progress value as permission to enter floodwater. Conditions can change after a report was submitted, and the card represents only the latest qualifying verified report.

### Refresh timing and unavailable information

FloodWatch refreshes Dashboard data when the map first becomes ready, when the screen resumes, and about every 15 minutes while the Dashboard remains active. The refresh requests report data, active alerts, and weather values, and refreshes the selected weather layer's cached tiles.

There is no manual Dashboard refresh button. Leaving and returning to the Dashboard causes it to refresh when it resumes.

**To check whether the Dashboard has refreshed:**

1. Find **Last updated** in the **Weather Map** card.
2. Read the displayed time and date.
3. If the Dashboard has been open for a while, allow the automatic refresh interval to run, or leave and return to **Dashboard**.
4. Recheck the time before using the displayed information.

**Expected result:** **Last updated** displays the time and date of the most recent Dashboard refresh.

The timestamp is the time the app refreshed its view. It does not guarantee that every weather record, alert, or community report was created at that moment. If values remain unavailable, use **Troubleshooting and Frequently Asked Questions**. For the complete list of active alerts and verified reports, use **Flood Alerts and Verified Reports**. To provide a new community observation safely, use **Creating and Submitting a Flood Report**.


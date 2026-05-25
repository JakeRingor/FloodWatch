# FloodWatch Implementation Plan (CnS Requirements)

This plan outlines the steps to implement the features requested in the "CnS" list while maintaining the current UI design and existing app functionality.

## 1. Real-Time Data Optimization
*   **Goal:** Update data every 15 minutes to balance real-time monitoring and device performance.
*   **Changes:**
    *   Modify `refreshInterval` in `HomeFragment.kt` from 3 minutes to 15 minutes (`900,000ms`).
*   **Impact:** Lower battery consumption and reduced API usage without changing the UI.

## 2. Prediction Basis (Above Sea Level & Satellite)
*   **Goal:** Provide visual context for elevation-based flood prediction.
*   **Changes:**
    *   Update `googleMap.mapType` in `HomeFragment.kt` to `GoogleMap.MAP_TYPE_HYBRID`. This combines satellite imagery with road labels and terrain contours.
    *   Enable `isTrafficEnabled` to help users see vehicle movement in relation to reported floods.
*   **Impact:** Users can visually assess low-lying areas (pagbabasihan) using the terrain/satellite hybrid view.

## 3. Vehicle Passability & Parameters
*   **Goal:** Provide specific details on which vehicles can pass and the exact flood parameters.
*   **Changes:**
    *   **Data Model (`FloodWatchModels.kt`):** Ensure `FloodReport` includes:
        *   `water_level`: Specific parameter (e.g., "30cm", "Knee High").
        *   `passability`: Vehicle status (e.g., "Not passable for light vehicles").
        *   `timestamp`: Accurate report time.
    *   **Logic (`HomeFragment.kt`):** Update the Map Marker snippet to display this data:
        ```kotlin
        .snippet("Level: ${report.water_level} | Passability: ${report.passability}")
        ```
*   **Impact:** Adds critical info to the existing map markers without adding new UI elements or buttons.

## 4. Database & Safety
*   **Goal:** Ensure data integrity in Supabase.
*   **Changes:**
    *   Verify `flood_reports` table schema in Supabase matches the updated Kotlin models.
*   **Safety Note:** No XML layouts will be modified. All changes are restricted to Kotlin logic and existing Map components.

---
**Status:** Ready for Implementation.

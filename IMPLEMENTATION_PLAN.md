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
        *   `flood_level`: Specific parameter (e.g., "30cm", "Knee High").
        *   `passability`: Vehicle status (e.g., "Not passable for light vehicles").
        *   `timestamp`: Accurate report time.
    *   **Logic (`HomeFragment.kt` & `FloodReportAdapter.kt`):** Update UI logic to display these parameters in map markers and lists.
*   **Impact:** Adds critical info to the existing UI without adding new elements.

## 4. Image Overlay (Timestamp & Location)
*   **Goal:** Embed the reporting time and location address directly into the captured image for verification.
*   **Changes:**
    *   Implement `addOverlayToBitmap` in `ReportFragment.kt` using `Canvas` and `Paint`.
    *   Overlay current date/time and geocoded address onto the bottom-left of the image before uploading to Supabase.
*   **Impact:** Enhances the credibility of visual proof provided by citizens.

## 5. Database & Safety
*   **Goal:** Ensure data integrity in Supabase.
*   **Changes:**
    *   Verify `flood_reports` table schema in Supabase has the `passability` (TEXT) column.
*   **Safety Note:** No XML layouts were modified. All changes are restricted to Kotlin logic and existing Map/List components.

---
**Status:** Implementation Complete.

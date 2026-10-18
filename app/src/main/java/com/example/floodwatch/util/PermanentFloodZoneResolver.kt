package com.example.floodwatch.util

import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.data.PermanentFloodZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class PermanentZoneDisplayState { LOW_HAZARD, MEDIUM_HAZARD, HIGH_HAZARD, DEEP_VERIFIED }

object PermanentFloodZoneResolver {
    fun lowestElevationZoneId(zones: List<PermanentFloodZone>): String? = zones
        .filter { it.elevationMeters?.isFinite() == true }
        .minByOrNull { it.elevationMeters!! }
        ?.id

    fun displayState(
        zone: PermanentFloodZone,
        activeVerifiedReports: List<FloodReport>
    ): PermanentZoneDisplayState {
        if (activeVerifiedReports.any { isDeepVerifiedInside(zone, it) }) {
            return PermanentZoneDisplayState.DEEP_VERIFIED
        }
        return when (zone.noahHazardLevel.uppercase()) {
            "HIGH" -> PermanentZoneDisplayState.HIGH_HAZARD
            "MEDIUM" -> PermanentZoneDisplayState.MEDIUM_HAZARD
            else -> PermanentZoneDisplayState.LOW_HAZARD
        }
    }

    fun isDeepVerifiedInside(zone: PermanentFloodZone, report: FloodReport): Boolean {
        if (report.latitude == 0.0 && report.longitude == 0.0) return false
        val severity = report.severity ?: when (report.floodLevel?.uppercase()) {
            "CRITICAL" -> 4
            "HIGH" -> 3
            "MODERATE", "MEDIUM" -> 2
            "LOW" -> 1
            else -> 0
        }
        val deep = (report.wheelEstimatedDepthCm ?: 0.0) >= 31.0 || severity >= 3
        return deep && distanceMeters(
            zone.latitude,
            zone.longitude,
            report.latitude,
            report.longitude
        ) <= zone.radiusMeters
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

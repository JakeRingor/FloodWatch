package com.example.floodwatch.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HomeZoneUtils {
    const val DEFAULT_RADIUS_METERS = 2_000
    const val MIN_RADIUS_METERS = 250
    const val MAX_RADIUS_METERS = 10_000

    fun normalizeRadius(meters: Int): Int =
        meters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)

    fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    fun isWithinRadius(
        homeLatitude: Double,
        homeLongitude: Double,
        eventLatitude: Double,
        eventLongitude: Double,
        radiusMeters: Int
    ): Boolean = distanceMeters(
        homeLatitude,
        homeLongitude,
        eventLatitude,
        eventLongitude
    ) <= normalizeRadius(radiusMeters)

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}

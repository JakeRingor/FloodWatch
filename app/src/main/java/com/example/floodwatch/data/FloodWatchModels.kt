package com.example.floodwatch.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

// ── APP LOCATION CONSTANTS ────────────────────────────────────────────────────

object AppLocation {
    const val BARANGAY     = "Sta. Ana"
    const val MUNICIPALITY = "Taytay"
    const val PROVINCE     = "Rizal"
    const val LAT          = 14.5653
    const val LNG          = 121.1268
    const val DEFAULT_ZOOM = 16f
}

// ── ENUMS ─────────────────────────────────────────────────────────────────────


@Serializable
enum class AlertSeverity { ADVISORY, WATCH, WARNING, CRITICAL }

// ── USER PROFILE ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserProfile(
    val id: String,

    @SerialName("full_name")
    val fullName: String? = null,

    @SerialName("phone_number")
    val phoneNumber: String? = null,

    val address: String? = null,

    @SerialName("profile_image_url")
    val profileImageUrl: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("home_zone_label")
    val homeZoneLabel: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("home_latitude")
    val homeLatitude: Double? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("home_longitude")
    val homeLongitude: Double? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("home_radius_meters")
    val homeRadiusMeters: Int? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("home_zone_enabled")
    val homeZoneEnabled: Boolean? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)

// ── FLOOD REPORT ──────────────────────────────────────────────────────────────

@Serializable
data class FloodReport(
    val id: String? = null,

    @SerialName("user_id")
    val userId: String = "",

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("original_image_url")
    val originalImageUrl: String? = null,

    val address: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    @SerialName("flood_level")
    val floodLevel: String? = null,

    @SerialName("passability")
    val passability: String? = null, // New field for CnS requirement

    val description: String? = null,
    val severity: Int? = null,

    @SerialName("flood_depth_range")
    val floodDepthRange: String? = null,

    @SerialName("elevation_meters")
    val elevationMeters: Double? = null,

    @SerialName("elevation_accuracy_meters")
    val elevationAccuracyMeters: Double? = null,

    @SerialName("elevation_source")
    val elevationSource: String? = null,

    @SerialName("wheel_count")
    val wheelCount: Int? = null,

    @SerialName("wheel_confidence")
    val wheelConfidence: Double? = null,

    @SerialName("wheel_submerged_percent")
    val wheelSubmergedPercent: Double? = null,

    @SerialName("wheel_estimated_depth_cm")
    val wheelEstimatedDepthCm: Double? = null,

    val status: String = "pending",

    @SerialName("deleted_at")
    val deletedAt: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)

// ── FLOOD ALERT ───────────────────────────────────────────────────────────────

@Serializable
data class FloodAlert(
    val id: String? = null,
    val title: String,
    val message: String,
    val severity: AlertSeverity = AlertSeverity.ADVISORY,

    @SerialName("is_active")
    val isActive: Boolean = true,

    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class PermanentFloodZone(
    val id: String,
    val slug: String,
    val name: String,

    @SerialName("nearest_street")
    val nearestStreet: String,

    val latitude: Double,
    val longitude: Double,

    @SerialName("radius_meters")
    val radiusMeters: Int,

    @SerialName("noah_hazard_level")
    val noahHazardLevel: String,

    @SerialName("elevation_meters")
    val elevationMeters: Double? = null,

    @SerialName("elevation_source")
    val elevationSource: String? = null
)

// ── WEATHER MODELS (OpenWeather API) ──────────────────────────────────────────

@Serializable
data class WeatherResponse(
    val main: MainData,
    val weather: List<WeatherDescription>,
    val wind: WindData,
    val rain: RainData? = null
)

@Serializable
data class MainData(
    val temp: Double,
    val humidity: Int,
    val pressure: Int
)

@Serializable
data class WeatherDescription(
    val main: String,
    val description: String
)

@Serializable
data class WindData(
    val speed: Double
)

@Serializable
data class RainData(
    @SerialName("1h")
    val oneHour: Double? = 0.0
)

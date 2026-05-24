package com.example.floodwatch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── APP LOCATION CONSTANTS ────────────────────────────────────────────────────

object AppLocation {
    const val BARANGAY     = "Kingsville Executive Village"
    const val MUNICIPALITY = "Cainta"
    const val PROVINCE     = "Rizal"
    const val LAT          = 14.5760  // ⚠️ I-verify sa Google Maps
    const val LNG          = 121.1058 // ⚠️ I-verify sa Google Maps
    const val DEFAULT_ZOOM = 16f
}

// ── ENUMS ─────────────────────────────────────────────────────────────────────


@Serializable
enum class AlertSeverity { ADVISORY, WATCH, WARNING, CRITICAL }

// ── USER PROFILE ──────────────────────────────────────────────────────────────

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

    val address: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    @SerialName("flood_level")
    val floodLevel: String? = null,   // ← String na, hindi enum

    val description: String? = null,
    val severity: Int? = null,

    val status: String = "pending",   // ← String na, hindi enum

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
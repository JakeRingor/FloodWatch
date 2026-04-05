package com.example.floodwatch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── USER & DATABASE MODELS ──

@Serializable
data class UserProfile(
    val id: String,
    val full_name: String? = null,
    val phone_number: String? = null,
    val address: String? = null
)



@Serializable
data class FloodReport(
    val id: String? = null,
    val user_id: String,
    val image_url: String? = null,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

// ── WEATHER MODELS (OpenWeather API) ──

@Serializable
data class WeatherResponse(
    val main: MainData,
    val weather: List<WeatherDescription>,
    val wind: WindData,
    val rain: RainData? = null // Nullable dahil hindi naman laging umuulan
)

@Serializable
data class MainData(
    val temp: Double,
    val humidity: Int,
    val pressure: Int
)

@Serializable
data class WeatherDescription(
    val main: String, // Halimbawa: "Rain", "Clouds", "Clear"
    val description: String
)

@Serializable
data class WindData(
    val speed: Double
)

@Serializable
data class RainData(
    // Ginagamitan natin ng SerialName dahil bawal magsimula sa numero ang variable name sa Kotlin
    @SerialName("1h")
    val oneHour: Double? = 0.0
)
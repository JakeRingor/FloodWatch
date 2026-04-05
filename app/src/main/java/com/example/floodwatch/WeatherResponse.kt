package com.example.floodwatch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



@Serializable
data class Weather(
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class Main(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)

@Serializable
data class Wind(
    val speed: Double
)

// <--- DAGDAG ITONG CLASS NA ITO PARA SA RAINFALL ACCURACY
@Serializable
data class Rain(
    @SerialName("1h") // Ginagamit ito dahil "1h" ang tawag ng API sa field
    val oneHour: Double = 0.0
)
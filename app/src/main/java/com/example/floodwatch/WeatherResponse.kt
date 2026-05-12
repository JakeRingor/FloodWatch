package com.example.floodwatch
import com.google.gson.annotations.SerializedName

data class Weather(
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)


data class Wind(
    val speed: Double
)

// <--- DAGDAG ITONG CLASS NA ITO PARA SA RAINFALL ACCURACY

data class Rain(
    @SerializedName("1h")
    val oneHour: Double = 0.0
)


package com.example.floodwatch

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,      // Gamit ang Coordinates para sa Kingsville PH
        @Query("lon") lon: Double,      // Mas accurate kaysa sa city name lang
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}
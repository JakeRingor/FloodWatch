package com.example.floodwatch

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("weather")
    suspend fun getWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}
package com.example.floodwatch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.floodwatch.OpenWeatherApi

object RetrofitInstance {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }
}
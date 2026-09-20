package com.example.floodwatch

import retrofit2.http.GET
import retrofit2.http.Query

interface ElevationApi {
    @GET("v1/elevation")
    suspend fun getElevation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): ElevationResponse
}

data class ElevationResponse(
    val elevation: List<Double> = emptyList()
)

package com.example.floodwatch

import android.app.Application

class FloodWatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // ✅ Dito tinatawag ang init — ngayon na gagana ang SupabaseClient.client
        SupabaseClient.init(this)
    }
}
package com.example.floodwatch

import android.app.Application
import android.util.Log
import com.google.android.gms.maps.MapsInitializer

class FloodWatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // ✅ Dito tinatawag ang init — ngayon na gagana ang SupabaseClient.client
        // Warm up the Maps SDK while the user is still on the login screen. This
        // shortens the cold start after the app's data and tile cache are cleared.
        MapsInitializer.initialize(
            applicationContext,
            MapsInitializer.Renderer.LATEST
        ) { renderer ->
            Log.d("FloodWatchMaps", "Maps renderer initialized: $renderer")
        }
        SupabaseClient.init(this)
    }
}

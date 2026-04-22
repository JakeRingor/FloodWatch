package com.example.floodwatch

import android.content.Context
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {

    private lateinit var _client: io.github.jan.supabase.SupabaseClient
    val client get() = _client

    fun init(context: Context) {
        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                // ✅ Dito na naka-save ang session kahit isara ang app
                sessionManager = SharedPreferencesSessionManager(context.applicationContext)
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}
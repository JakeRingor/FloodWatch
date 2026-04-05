package com.example.floodwatch

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime // <--- DAPAT MERON ITONG IMPORT

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Storage)

        // ITO ANG KAILANGAN PARA GUMANA YUNG .realtime SA IBANG FILES
        install(Realtime)
    }
}
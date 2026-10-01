package com.example.floodwatch

import android.content.Context
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {

    const val AUTH_SCHEME = "com.example.floodwatch"
    const val VERIFY_EMAIL_HOST = "verify-email"
    const val RESET_PASSWORD_HOST = "reset-password"
    const val VERIFY_EMAIL_REDIRECT =
        "https://floodwatch-admin-staana.netlify.app/email-verified.html"
    const val RESET_PASSWORD_REDIRECT = "$AUTH_SCHEME://$RESET_PASSWORD_HOST"

    private lateinit var _client: io.github.jan.supabase.SupabaseClient
    private lateinit var sessionManager: SharedPreferencesSessionManager
    val client get() = _client

    fun init(context: Context) {
        sessionManager = SharedPreferencesSessionManager(context.applicationContext)
        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                scheme = AUTH_SCHEME
                host = VERIFY_EMAIL_HOST
                // ✅ Dito na naka-save ang session kahit isara ang app
                sessionManager = this@SupabaseClient.sessionManager
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    fun setPersistentSessionsEnabled(enabled: Boolean) {
        sessionManager.setPersistentSessionsEnabled(enabled)
    }

    fun isPersistentSessionsEnabled(): Boolean =
        sessionManager.isPersistentSessionsEnabled()
}

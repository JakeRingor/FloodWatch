package com.example.floodwatch

import android.content.Context
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesSessionManager(context: Context) : SessionManager {

    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        if (!isPersistentSessionsEnabled()) {
            prefs.edit().remove(KEY_SESSION).apply()
            return
        }
        prefs.edit()
            .putString(KEY_SESSION, json.encodeToString(session))
            .apply()
    }

    override suspend fun loadSession(): UserSession? {
        if (!isPersistentSessionsEnabled()) return null
        val sessionString = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            json.decodeFromString<UserSession>(sessionString)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun setPersistentSessionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSIST_SESSION, enabled).apply()
        if (!enabled) prefs.edit().remove(KEY_SESSION).apply()
    }

    fun isPersistentSessionsEnabled(): Boolean {
        // Preserve the behavior of existing installations until the user
        // explicitly changes the checkbox on the login screen.
        return prefs.getBoolean(KEY_PERSIST_SESSION, true)
    }

    private companion object {
        const val KEY_SESSION = "session"
        const val KEY_PERSIST_SESSION = "persist_session"
    }
}

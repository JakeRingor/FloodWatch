package com.example.floodwatch.data

import android.content.Context
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesSessionManager(context: Context) : SessionManager {

    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        if (!isPersistentSessionsEnabled()) {
            prefs.edit { remove(KEY_SESSION) }
            return
        }
        val encryptedSession = SecureSessionCipher.encrypt(json.encodeToString(session))
        prefs.edit { putString(KEY_SESSION, encryptedSession) }
    }

    override suspend fun loadSession(): UserSession? {
        if (!isPersistentSessionsEnabled()) return null
        val storedSession = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val sessionJson = if (SecureSessionCipher.isEncrypted(storedSession)) {
                SecureSessionCipher.decrypt(storedSession)
            } else {
                // One-time migration for sessions saved before Keystore encryption.
                storedSession
            }
            json.decodeFromString<UserSession>(sessionJson).also {
                if (!SecureSessionCipher.isEncrypted(storedSession)) {
                    prefs.edit { putString(KEY_SESSION, SecureSessionCipher.encrypt(sessionJson)) }
                }
            }
        } catch (_: Exception) {
            // A restored, corrupt, or invalidated session must not fail repeatedly.
            prefs.edit { remove(KEY_SESSION) }
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit { remove(KEY_SESSION) }
    }

    fun setPersistentSessionsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PERSIST_SESSION, enabled) }
        if (!enabled) prefs.edit { remove(KEY_SESSION) }
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

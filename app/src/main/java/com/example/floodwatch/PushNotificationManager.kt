package com.example.floodwatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

@Serializable
private data class DevicePushToken(
    val token: String,
    @SerialName("user_id") val userId: String,
    val platform: String = "android",
    val enabled: Boolean = true
)

object PushNotificationManager {
    const val NEW_REPORT_CHANNEL_ID = "new_flood_reports"
    const val ADMIN_ALERT_CHANNEL_ID = "admin_flood_alerts"
    const val REPORT_STATUS_CHANNEL_ID = "report_status_updates"

    private const val PROFILE_PREFS = "profile_preferences"
    private const val PREF_FLOOD_ALERTS = "notify_flood_alerts"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ADMIN_ALERT_CHANNEL_ID,
                "Official flood alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent flood alerts posted by FloodWatch administrators"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NEW_REPORT_CHANNEL_ID,
                "New flood reports",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when another user submits a flood report"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                REPORT_STATUS_CHANNEL_ID,
                "Report status updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the status of your flood report changes"
            }
        )
    }

    fun syncCurrentDevice(context: Context) {
        val enabled = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_FLOOD_ALERTS, true)
        getTokenAndRegister(context.applicationContext, enabled)
    }

    fun setCurrentDeviceEnabled(context: Context, enabled: Boolean) {
        getTokenAndRegister(context.applicationContext, enabled)
    }

    fun registerRefreshedToken(context: Context, token: String) {
        val enabled = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_FLOOD_ALERTS, true)
        registerToken(token, enabled)
    }

    private fun getTokenAndRegister(context: Context, enabled: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            registerToken(token, enabled)
        }
    }

    private fun registerToken(token: String, enabled: Boolean) {
        applicationScope.launch {
            runCatching {
                SupabaseClient.client.auth.awaitInitialization()
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@runCatching
                SupabaseClient.client.from("device_push_tokens").upsert(
                    DevicePushToken(token = token, userId = userId, enabled = enabled)
                )
            }
        }
    }

    suspend fun unregisterCurrentDevice(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val token = currentToken() ?: return
        try {
            SupabaseClient.client.from("device_push_tokens").delete {
                filter { eq("token", token) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Logout must still succeed if token cleanup is temporarily offline.
        }
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (continuation.isActive) {
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    fun showIncomingReportNotification(
        context: Context,
        title: String,
        body: String,
        reportId: String?,
        channelId: String = NEW_REPORT_CHANNEL_ID
    ) {
        val enabled = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_FLOOD_ALERTS, true)
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        createNotificationChannels(context)
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(HomeActivity.EXTRA_OPEN_ALERTS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reportId?.hashCode() ?: body.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_verified_card)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context)
            .notify(reportId?.hashCode() ?: body.hashCode(), notification)
    }
}

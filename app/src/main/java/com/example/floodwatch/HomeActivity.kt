package com.example.floodwatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.floodwatch.databinding.ActivityHomeBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var reportStatusChannel: RealtimeChannel? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            val session = SupabaseClient.client.auth.currentSessionOrNull()
            if (session == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        val alertsBadge = binding.bottomNavigation.getOrCreateBadge(R.id.navigation_alerts)
        alertsBadge.isVisible = false
        alertsBadge.backgroundColor = Color.RED

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (binding.bottomNavigation.selectedItemId == item.itemId) {
                return@setOnItemSelectedListener false
            }

            val selectedFragment: Fragment = when (item.itemId) {
                R.id.navigation_home -> HomeFragment()
                R.id.navigation_report -> ReportFragment()
                R.id.navigation_alerts -> {
                    alertsBadge.isVisible = false
                    AlertsFragment()
                }
                R.id.navigation_my_reports -> MyReportsFragment()
                R.id.navigation_profile -> ProfileFragment()
                else -> HomeFragment()
            }

            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }

        if (savedInstanceState == null) openInitialDestination(intent)

        createReportStatusNotificationChannel()
        requestNotificationPermissionIfNeeded()
        listenForReportApprovals()
    }

    fun openMyReports() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_my_reports
    }

    private fun openInitialDestination(sourceIntent: Intent) {
        if (sourceIntent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AlertsFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.navigation_alerts
            binding.bottomNavigation.getBadge(R.id.navigation_alerts)?.isVisible = false
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.navigation_home
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AlertsFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.navigation_alerts
            binding.bottomNavigation.getBadge(R.id.navigation_alerts)?.isVisible = false
        }
    }

    private fun listenForReportApprovals() {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            ?: return
        lifecycleScope.launch {
            try {
                val channel = SupabaseClient.client.channel("report-approval-$userId")
                reportStatusChannel = channel
                val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "flood_reports"
                }
                channel.subscribe()
                updates.collect { action ->
                    val report = action.record
                    val ownerId = report["user_id"]?.jsonPrimitive?.contentOrNull
                    val status = report["status"]?.jsonPrimitive?.contentOrNull
                    val reportId = report["id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                    val normalizedStatus = status?.uppercase()
                    if (ownerId == userId && normalizedStatus in NOTIFIABLE_REPORT_STATUSES) {
                        val address = report["address"]?.jsonPrimitive?.contentOrNull
                            ?: "your submitted location"
                        notifyReportStatusChanged(reportId, address, normalizedStatus!!)
                    }
                }
            } catch (_: Exception) {
                // The regular Alerts screen remains available if Realtime is temporarily offline.
            }
        }
    }

    private fun notifyReportStatusChanged(reportId: String, address: String, status: String) {
        if (!getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE)
                .getBoolean(PREF_FLOOD_ALERTS, true)
        ) return

        val notificationPrefs = getSharedPreferences(NOTIFICATION_HISTORY_PREFS, MODE_PRIVATE)
        val notifiedIds = notificationPrefs.getStringSet(KEY_NOTIFIED_REPORT_EVENTS, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        val eventId = "$reportId:$status"
        if (!notifiedIds.add(eventId)) return
        notificationPrefs.edit().putStringSet(KEY_NOTIFIED_REPORT_EVENTS, notifiedIds).apply()

        binding.bottomNavigation.getOrCreateBadge(R.id.navigation_alerts).apply {
            isVisible = true
            backgroundColor = Color.RED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val openAlertsIntent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ALERTS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            reportId.hashCode(),
            openAlertsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, message) = when (status) {
            "VERIFIED" -> "Flood report verified" to
                "Your report at $address was verified by the admin."
            "DISMISSED" -> "Flood report dismissed" to
                "Your report at $address was dismissed by the admin."
            "INVALID_IMAGE" -> "Report needs a valid image" to
                "Your report at $address was not approved because of its image."
            "INVALID_INFORMATION" -> "Report information not verified" to
                "Your report at $address was not approved because its information could not be verified."
            else -> "Flood report under review" to
                "Your report at $address was returned to pending review."
        }
        val notification = NotificationCompat.Builder(this, REPORT_STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_verified_card)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(eventId.hashCode(), notification)
    }

    private fun createReportStatusNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REPORT_STATUS_CHANNEL_ID,
                "Report status updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your submitted flood report is verified"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        val alertsEnabled = getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE)
            .getBoolean(PREF_FLOOD_ALERTS, true)
        if (alertsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        private const val REPORT_STATUS_CHANNEL_ID = "report_status_updates"
        private const val EXTRA_OPEN_ALERTS = "open_alerts"
        private const val PROFILE_PREFS = "profile_preferences"
        private const val PREF_FLOOD_ALERTS = "notify_flood_alerts"
        private const val NOTIFICATION_HISTORY_PREFS = "notification_history"
        private const val KEY_NOTIFIED_REPORT_EVENTS = "report_status_events"
        private val NOTIFIABLE_REPORT_STATUSES = setOf(
            "PENDING",
            "VERIFIED",
            "DISMISSED",
            "INVALID_IMAGE",
            "INVALID_INFORMATION"
        )
    }
}

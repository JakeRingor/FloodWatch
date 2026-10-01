package com.example.floodwatch

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FloodWatchMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushNotificationManager.registerRefreshedToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Notification payloads are displayed automatically by Android while the
        // app is backgrounded. Foreground messages arrive here and need UI code.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "New flood report"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "A new flood report was submitted near your community."
        PushNotificationManager.showIncomingReportNotification(
            context = applicationContext,
            title = title,
            body = body,
            reportId = message.data["alert_id"] ?: message.data["report_id"],
            channelId = if (message.data["type"] == "admin_alert") {
                PushNotificationManager.ADMIN_ALERT_CHANNEL_ID
            } else {
                PushNotificationManager.NEW_REPORT_CHANNEL_ID
            }
        )
    }
}

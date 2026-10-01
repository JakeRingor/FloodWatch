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
        val messageType = message.data["type"]
        val title = message.notification?.title
            ?: message.data["title"]
            ?: if (messageType == "report_status") "Flood report verified" else "New flood report"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: if (messageType == "report_status") {
                "Your flood report was verified by the admin."
            } else {
                "A new flood report was submitted near your community."
            }
        PushNotificationManager.showIncomingReportNotification(
            context = applicationContext,
            title = title,
            body = body,
            reportId = message.data["alert_id"] ?: message.data["report_id"],
            channelId = when (messageType) {
                "admin_alert" -> PushNotificationManager.ADMIN_ALERT_CHANNEL_ID
                "report_status" -> PushNotificationManager.REPORT_STATUS_CHANNEL_ID
                else -> PushNotificationManager.NEW_REPORT_CHANNEL_ID
            }
        )
    }
}

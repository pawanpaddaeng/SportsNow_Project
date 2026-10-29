package com.example.cpen321application.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.cpen321application.MainActivity
import com.example.cpen321application.core.unexpectedErrorHandler
import com.example.cpen321application.user.NotificationRegistration
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// channel for game notifications (join requests, game changes, ...), users can mute it in settings
const val GAME_UPDATES_CHANNEL_ID = "game_updates"

// creates the notification channel, safe to call more than once
fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        GAME_UPDATES_CHANNEL_ID,
        "Game updates",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Join requests and changes to your games" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/*
    Receives push notifications from Firebase. Android only shows notifications
    by itself while the app is in the background, so this shows them while the
    app is open. message.data (e.g. type, gameId) is passed to GameEvents so open
    screens about that game can reload.
    Also sends the device's token to the server when Firebase replaces it
 */
class PushNotificationService : FirebaseMessagingService() {

    // not tied to the service's lifetime, which can end before the request finishes.
    // An unexpected error is only logged, the app may not be open to show anything
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + unexpectedErrorHandler())

    // Firebase issued a new token for this device (e.g. app data cleared, token rotated)
    override fun onNewToken(token: String) {
        scope.launch {
            NotificationRegistration.registerNewToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // let open screens about this game reload, e.g. Join Requests on a new request
        GameEvents.publish(message.data)

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        // Android 13+ only shows notifications once the user allowed them
        val allowed = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        createNotificationChannel(this)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, GAME_UPDATES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}

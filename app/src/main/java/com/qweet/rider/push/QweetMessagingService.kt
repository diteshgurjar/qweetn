package com.qweet.rider.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qweet.rider.MainActivity
import com.qweet.rider.R
import com.qweet.rider.RiderApp
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.DeviceTokenRequest
import com.qweet.rider.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Every push shown here originates from QWEET's own server (see backend
 * includes/fcm.php -> notifyUser()) — Firebase is only the delivery pipe to
 * the phone, same as it is for every Android app that has notifications.
 */
class QweetMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Called whenever Firebase issues a new/rotated token — must re-sync with our server. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val tokenStore = TokenStore(applicationContext)
        // Only registerable once logged in (endpoint requires a bearer token);
        // if this fires before login, ApiClient just won't have a token yet and
        // registerDeviceToken() below no-ops on 401 — MainActivity re-sends it
        // right after a successful login anyway.
        if (tokenStore.getToken() != null) {
            scope.launch { registerDeviceToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "QWEET"
        val body = message.notification?.body ?: message.data["message"] ?: ""

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, RiderApp.ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_online_dot)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // A random-ish id (instead of one fixed id) so multiple alerts (e.g. two
        // order offers arriving close together) stack instead of overwriting.
        NotificationManagerCompat.from(this).notify(Random.nextInt(10_000, 99_999), notification)
    }
}

/**
 * Sends the current FCM token to our backend so notifyUser() can push to this
 * device. Call after login, and Firebase calls onNewToken() will call it again
 * whenever the token rotates. Best-effort — a failure here just means this
 * device won't get pushes until the next successful call; it never blocks
 * login or breaks the app.
 */
suspend fun registerDeviceToken(token: String) {
    runCatching {
        ApiClient.service.registerDeviceToken(DeviceTokenRequest(fcm_token = token, platform = "android"))
    }
}

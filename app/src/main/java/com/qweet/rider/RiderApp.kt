package com.qweet.rider

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.TokenStore

class RiderApp : Application() {

    lateinit var tokenStore: TokenStore
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        ApiClient.init(tokenStore)
        createLocationChannel()
        createAlertsChannel()
    }

    private fun createLocationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Rider online status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while you're online and sharing your location for deliveries."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** High-importance channel (sound + heads-up) for push alerts from our server: new orders, withdrawal results, admin messages. */
    private fun createAlertsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Order & account alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New delivery offers, withdrawal updates, and messages from QWEET."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val LOCATION_CHANNEL_ID = "rider_location_channel"
        const val ALERTS_CHANNEL_ID = "rider_alerts_channel"
    }
}

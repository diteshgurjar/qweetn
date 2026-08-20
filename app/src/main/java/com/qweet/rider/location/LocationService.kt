package com.qweet.rider.location

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.qweet.rider.MainActivity
import com.qweet.rider.R
import com.qweet.rider.RiderApp
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.UpdateLocationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs as a foreground service (type=location) the entire time the rider is
 * online, so Android/OEM battery managers don't kill location updates the
 * moment the app is backgrounded — this is the "works while minimized /
 * doesn't get killed" requirement. Posts lat/lng to update-location.php
 * every ~20 seconds, matching the API's documented 15-30s cadence.
 */
class LocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var fusedClient: FusedLocationProviderClient

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            serviceScope.launch {
                runCatching {
                    ApiClient.service.updateLocation(UpdateLocationRequest(loc.latitude, loc.longitude))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    @Suppress("MissingPermission") // Permission is checked by the caller before starting the service.
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RiderApp.LOCATION_CHANNEL_ID)
            .setContentTitle("You're online")
            .setContentText("Sharing your location so new orders can find you.")
            .setSmallIcon(R.drawable.ic_online_dot)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 20_000L
        private const val MIN_UPDATE_INTERVAL_MS = 15_000L
    }
}

private fun CoroutineScope.cancel() {
    (coroutineContext[Job])?.cancel()
}

package com.qweet.rider

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.qweet.rider.data.TokenStore
import com.qweet.rider.location.LocationService
import com.qweet.rider.ui.AppEntry
import com.qweet.rider.ui.QweetRiderTheme

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: TokenStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Handled reactively — the online toggle checks permission state again before starting the service. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = (application as RiderApp).tokenStore
        requestLocationPermissions()

        setContent {
            QweetRiderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppEntry(
                        tokenStore = tokenStore,
                        onGoOnline = { startLocationService() },
                        onGoOffline = { stopLocationService() }
                    )
                }
            }
        }
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
        // Background location must be requested separately on Android 10+, after
        // foreground location is granted — request it on a second pass.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocationService() {
        stopService(Intent(this, LocationService::class.java))
    }
}

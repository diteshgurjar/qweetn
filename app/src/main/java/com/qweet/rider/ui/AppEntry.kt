package com.qweet.rider.ui

import android.content.Context
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qweet.rider.BuildConfig
import com.qweet.rider.data.AcceptedOrderStore
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.ChallengeSolver
import com.qweet.rider.data.OrderActionRequest
import com.qweet.rider.data.OrderOfferDto
import com.qweet.rider.data.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class MainTab { DASHBOARD, ORDERS, WALLET, PROFILE }

/**
 * Root of the app. Runs the InfinityFree/iFastNet anti-bot challenge solve ONCE per cold
 * start, before deciding whether to show Login or Dashboard.
 *
 * Previously this only ran inside LoginScreen's button click, which meant a returning rider
 * (token already saved) skipped straight to DashboardScreen and never solved the challenge —
 * if the earlier cookie had expired, every dashboard/orders/toggle-online call would silently
 * hit the anti-bot HTML page instead of the real API and fail with no explanation. Now every
 * cold start (logged in or not) solves it first.
 */
@Composable
fun AppEntry(tokenStore: TokenStore, onGoOnline: () -> Unit, onGoOffline: () -> Unit) {
    var preparing by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(tokenStore.getToken() != null) }
    val entryScope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = Modifier.size(0.dp),
        factory = { ctx -> WebView(ctx).also { webViewRef = it } }
    )

    LaunchedEffect(Unit) {
        val wv = webViewRef
        if (wv != null) {
            // Short-circuits almost instantly if already solved earlier this session, or if
            // this host doesn't need the challenge at all.
            ChallengeSolver.ensureSolved(wv, ChallengeSolver.baseHostFor(BuildConfig.API_BASE_URL))
        }
        preparing = false
    }

    if (preparing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (loggedIn) {
        MainTabs(
            onGoOnline = onGoOnline,
            onGoOffline = onGoOffline,
            onLogout = {
                onGoOffline()
                // Best-effort: tell the server to stop pushing to this device before we
                // drop the bearer token it'd need to make that call.
                entryScope.launch {
                    runCatching {
                        val fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                        com.qweet.rider.data.ApiClient.service.unregisterDeviceToken(
                            com.qweet.rider.data.DeviceTokenRequest(fcm_token = fcmToken)
                        )
                    }
                    tokenStore.clear()
                    loggedIn = false
                }
            }
        )
    } else {
        LoginScreen(tokenStore = tokenStore, onLoginSuccess = { loggedIn = true })
    }
}

/** Dashboard / Wallet / Profile behind a bottom nav bar, shown once the rider is logged in. */
@Composable
private fun MainTabs(onGoOnline: () -> Unit, onGoOffline: () -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(MainTab.DASHBOARD) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val orderStore = remember { AcceptedOrderStore(context) }

    // Global new-order popup state — lives here (above the tabs) so it can pop up over
    // whichever tab the rider is currently looking at, not just the Dashboard tab.
    var newOffer by remember { mutableStateOf<OrderOfferDto?>(null) }
    var offerActionInFlight by remember { mutableStateOf(false) }
    // Deliveries the popup has already shown+resolved (accepted, declined, or timed out) —
    // skips re-showing the same offer on the next poll tick.
    var seenOfferIds by remember { mutableStateOf(setOf<Int>()) }
    // Deliveries accepted via the global popup — passed down to DashboardScreen so its own
    // "Incoming Order" card doesn't ask the rider to accept/decline the same delivery again.
    var acknowledgedDeliveryIds by remember { mutableStateOf(setOf<Int>()) }

    fun alertRiderNewOrder() {
        vibrate(context)
        playBeep()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (newOffer != null) continue // one at a time, same as the web popup
            val result = runCatching { ApiClient.service.orderOffer() }
            val offer = result.getOrNull()?.body()?.offer
            if (offer != null && offer.delivery_id !in seenOfferIds && offer.seconds_left > 0) {
                newOffer = offer
                alertRiderNewOrder()
            }
        }
    }

    fun resolveDecline(deliveryId: Int, reason: String) {
        offerActionInFlight = true
        scope.launch {
            runCatching {
                ApiClient.service.orderAction(
                    OrderActionRequest(delivery_id = deliveryId, action = "decline", reason = reason)
                )
            }
            seenOfferIds = seenOfferIds + deliveryId
            newOffer = null
            offerActionInFlight = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == MainTab.DASHBOARD,
                        onClick = { tab = MainTab.DASHBOARD },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.ORDERS,
                        onClick = { tab = MainTab.ORDERS },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = "Orders") },
                        label = { Text("Orders") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.WALLET,
                        onClick = { tab = MainTab.WALLET },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                        label = { Text("Wallet") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.PROFILE,
                        onClick = { tab = MainTab.PROFILE },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        onGoOnline = onGoOnline,
                        onGoOffline = onGoOffline,
                        acknowledgedDeliveryIds = acknowledgedDeliveryIds
                    )
                    MainTab.ORDERS -> OrdersScreen(onContinueWorkingOrder = { tab = MainTab.DASHBOARD })
                    MainTab.WALLET -> WalletScreen(onManageBankAccount = { tab = MainTab.PROFILE })
                    MainTab.PROFILE -> ProfileScreen(onLogout = onLogout)
                }
            }
        }

        newOffer?.let { offer ->
            NewOrderPopup(
                offer = offer,
                actionInFlight = offerActionInFlight,
                onAccept = {
                    // Already assigned to this rider server-side — accepting is just
                    // dismissing the popup and jumping to the Dashboard's active-delivery view.
                    // Persisted (not just kept in this composable's memory) so the acceptance
                    // survives a tab switch, back-press, or app restart — see AcceptedOrderStore.
                    orderStore.add(offer.delivery_id)
                    seenOfferIds = seenOfferIds + offer.delivery_id
                    acknowledgedDeliveryIds = acknowledgedDeliveryIds + offer.delivery_id
                    newOffer = null
                    tab = MainTab.DASHBOARD
                },
                onDecline = { resolveDecline(offer.delivery_id, "Declined by rider") },
                onTimeout = { resolveDecline(offer.delivery_id, "No response from rider (auto-declined)") }
            )
        }
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 200, 100, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

private fun playBeep() {
    runCatching {
        val tone = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }
}

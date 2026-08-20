package com.qweet.rider.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.qweet.rider.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onGoOnline: () -> Unit,
    onGoOffline: () -> Unit,
    acknowledgedDeliveryIds: Set<Int> = emptySet()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val orderStore = remember { AcceptedOrderStore(context) }
    var showNotifications by remember { mutableStateOf(false) }

    var online by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf<DashboardData?>(null) }
    var deliveries by remember { mutableStateOf<List<DeliveryDto>>(emptyList()) }
    var declinedIds by remember { mutableStateOf(setOf<Int>()) }
    var actionInFlight by remember { mutableStateOf(false) }
    var loadingInitial by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    // Deliveries accepted from THIS screen's own inline card (as opposed to the global
    // new-order popup, whose accepted ids arrive via acknowledgedDeliveryIds) — merged below
    // so accepting either way immediately drops into the full-screen delivery flow.
    var localAcknowledgedIds by remember { mutableStateOf(setOf<Int>()) }
    // Same acceptances, but read back from disk (see AcceptedOrderStore) so an accept made
    // earlier — even in a previous app session — still resumes the flow instead of showing
    // the Accept/Decline card again for an order the rider already committed to.
    var persistedAcknowledgedIds by remember { mutableStateOf(orderStore.getAll()) }

    // Initial load: find out current online state + any already-assigned delivery.
    LaunchedEffect(retryTick) {
        loadingInitial = true
        val dashResult = runCatching { ApiClient.service.dashboard() }
        val dash = dashResult.getOrNull()?.body()
        if (dash?.success == true) {
            errorText = null
            dashboard = dash.data
            online = dash.data?.is_online ?: false
            if (online) onGoOnline()
        } else {
            errorText = dash?.error ?: describeFailure(dashResult)
        }

        val ordResult = runCatching { ApiClient.service.orders() }
        val ord = ordResult.getOrNull()?.body()
        if (ord?.success == true) {
            deliveries = ord.data.orEmpty()
            // Verify the persisted "already accepted" flags against what the server actually
            // has assigned right now, dropping anything stale.
            orderStore.retainOnly(deliveries.map { it.delivery_id }.toSet())
            persistedAcknowledgedIds = orderStore.getAll()
            if (dash?.success == true) errorText = null
        } else if (dash?.success != true) {
            errorText = ord?.error ?: describeFailure(ordResult)
        }
        loadingInitial = false
    }

    // Poll for orders + refresh dashboard stats every 10s while online.
    LaunchedEffect(online) {
        while (online) {
            delay(10_000)
            val ordResult = runCatching { ApiClient.service.orders() }
            val ord = ordResult.getOrNull()?.body()
            if (ord?.success == true) {
                deliveries = ord.data.orEmpty()
                orderStore.retainOnly(deliveries.map { it.delivery_id }.toSet())
                persistedAcknowledgedIds = orderStore.getAll()
                errorText = null
            } else {
                errorText = ord?.error ?: describeFailure(ordResult)
            }
            val dashResult = runCatching { ApiClient.service.dashboard() }
            val dash = dashResult.getOrNull()?.body()
            if (dash?.success == true) dashboard = dash.data
        }
    }

    val activeDelivery = deliveries.firstOrNull { it.delivery_id !in declinedIds }
    val effectiveAcknowledged = acknowledgedDeliveryIds + localAcknowledgedIds + persistedAcknowledgedIds
    // Once accepted (picked_up already implies acceptance), the rider drops into the
    // full-screen order-details -> navigate -> arrived -> deliver flow instead of the
    // normal dashboard content.
    val inFlowDelivery = activeDelivery?.takeIf {
        it.status == "picked_up" || (it.status == "assigned" && it.delivery_id in effectiveAcknowledged)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Qweet Rider", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE_NUMBER")))
                    }) {
                        Icon(Icons.Default.SupportAgent, contentDescription = "Support")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SOS_PHONE_NUMBER")))
                    }) {
                        Icon(Icons.Default.Sos, contentDescription = "SOS", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { showNotifications = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (inFlowDelivery != null) {
            DeliveryFlowScreen(
                delivery = inFlowDelivery,
                actionInFlight = actionInFlight,
                modifier = Modifier.padding(padding).fillMaxSize(),
                onMarkPickedUp = {
                    actionInFlight = true
                    scope.launch {
                        val result = runCatching {
                            ApiClient.service.orderAction(
                                OrderActionRequest(delivery_id = inFlowDelivery.delivery_id, action = "advance", new_status = "picked_up")
                            )
                        }
                        val resp = result.getOrNull()?.body()
                        if (resp?.success == true) {
                            errorText = null
                            deliveries = deliveries.map {
                                if (it.delivery_id == inFlowDelivery.delivery_id) it.copy(status = "picked_up") else it
                            }
                        } else {
                            errorText = resp?.error ?: describeFailure(result)
                        }
                        actionInFlight = false
                    }
                },
                onMarkDelivered = {
                    actionInFlight = true
                    scope.launch {
                        val result = runCatching {
                            ApiClient.service.orderAction(
                                OrderActionRequest(delivery_id = inFlowDelivery.delivery_id, action = "advance", new_status = "delivered")
                            )
                        }
                        val resp = result.getOrNull()?.body()
                        if (resp?.success == true) {
                            errorText = null
                            deliveries = deliveries.filter { it.delivery_id != inFlowDelivery.delivery_id }
                            localAcknowledgedIds = localAcknowledgedIds - inFlowDelivery.delivery_id
                        } else {
                            errorText = resp?.error ?: describeFailure(result)
                        }
                        actionInFlight = false
                    }
                }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            errorText?.let { msg ->
                ErrorBanner(message = msg, onRetry = { retryTick++ })
            }

            if (loadingInitial && dashboard == null && errorText == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            GreetingHeader(riderName = dashboard?.name, online = online, onToggle = { turnOn ->
                scope.launch {
                    val result = runCatching { ApiClient.service.toggleOnline(ToggleOnlineRequest(turnOn)) }
                    val resp = result.getOrNull()?.body()
                    if (resp?.success == true) {
                        errorText = null
                        online = resp.is_online ?: turnOn
                        if (online) onGoOnline() else onGoOffline()
                    } else {
                        errorText = resp?.error ?: describeFailure(result)
                    }
                }
            })

            StatusBanner(online = online, hasOrder = activeDelivery != null)

            LiveMapCard(online = online)

            StatsGrid(dashboard = dashboard)

            if (activeDelivery == null) {
                EmptyStateCard(online)
            } else {
                // If we get here, activeDelivery.status == "assigned" and it hasn't been
                // acknowledged yet (inFlowDelivery above already handled every other case
                // by returning early into the full-screen flow).
                IncomingOrderCard(
                    delivery = activeDelivery,
                    actionInFlight = actionInFlight,
                    onAccept = {
                        // Already assigned server-side — accepting just drops the rider
                        // into the full-screen order-details -> navigate -> deliver flow.
                        // Persisted so this survives a tab switch, back-press, or app restart.
                        orderStore.add(activeDelivery.delivery_id)
                        persistedAcknowledgedIds = orderStore.getAll()
                        localAcknowledgedIds = localAcknowledgedIds + activeDelivery.delivery_id
                    },
                    onDecline = { reason ->
                        actionInFlight = true
                        scope.launch {
                            val result = runCatching {
                                ApiClient.service.orderAction(
                                    OrderActionRequest(
                                        delivery_id = activeDelivery.delivery_id,
                                        action = "decline",
                                        reason = reason
                                    )
                                )
                            }
                            val resp = result.getOrNull()?.body()
                            if (resp?.success == true) {
                                errorText = null
                                declinedIds = declinedIds + activeDelivery.delivery_id
                                deliveries = deliveries.filter { it.delivery_id != activeDelivery.delivery_id }
                            } else {
                                errorText = resp?.error ?: describeFailure(result)
                            }
                            actionInFlight = false
                        }
                    }
                )
            }
        }
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            confirmButton = {
                TextButton(onClick = { showNotifications = false }) { Text("OK") }
            },
            title = { Text("Notifications") },
            text = { Text("You're all caught up — no new notifications right now.") }
        )
    }
}

private const val SUPPORT_PHONE_NUMBER = "18001234567"
private const val SOS_PHONE_NUMBER = "112"

private fun greetingForHour(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

@Composable
private fun GreetingHeader(riderName: String?, online: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(greetingForHour() + ",", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                riderName ?: "Rider",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = online,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
private fun StatusBanner(online: Boolean, hasOrder: Boolean) {
    val dotColor = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bannerBg = if (online) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bannerBg)
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (online) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val scale by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 2.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulseScale"
                )
                val alpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(12.dp * scale)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = alpha))
                )
            }
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dotColor))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (online) "You're live on duty" else "You're currently offline",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (!online) "Go online to receive orders"
                else if (hasOrder) "Order in progress" else "Searching for orders…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun LiveMapCard(online: Boolean) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf(false) }
    var mapReloadTick by remember { mutableStateOf(0) }

    // BUG FIX: this used to only re-check permission once via LaunchedEffect(Unit), which
    // never fires again after the first composition — so if the system permission dialog
    // was still pending when this card first drew (the common case: MainActivity asks for
    // permission asynchronously right as the UI is being built), hasPermission stayed false
    // forever and the map got stuck on "Location permission needed" even after the rider
    // tapped Allow, until they happened to switch tabs away and back. Re-checking on every
    // ON_RESUME (app foregrounded, or returning from the system Settings/permission dialog)
    // makes the map recover on its own as soon as permission is actually granted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc -> if (loc != null) location = loc }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                location = loc
                if (mapReady) {
                    webViewRef?.evaluateJavascript("updateRiderLocation(${loc.latitude}, ${loc.longitude});", null)
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()
        fusedClient.requestLocationUpdates(request, callback, context.mainLooper)

        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    LaunchedEffect(online, mapReady) {
        if (mapReady) webViewRef?.evaluateJavascript("setOnlineState(${online});", null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        when {
            !hasPermission -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Location permission needed to show the map.\nEnable it from Settings.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            location == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            mapLoadError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Map couldn't load. Check your connection.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { mapLoadError = false; mapReady = false; mapReloadTick++ }) { Text("Retry") }
                }
            }
            else -> {
                val loc = location!!
                if (!mapReady) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                key(mapReloadTick) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        // Wrapped in a FrameLayout with explicit MATCH_PARENT layout params —
                        // a bare WebView handed straight to AndroidView can get measured at
                        // 0x0 the first pass inside a clipped/rounded-corner container, which
                        // renders as a permanently blank white box even once content is ready.
                        android.widget.FrameLayout(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val webView = WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Fixes a known Compose bug where a hardware-accelerated
                                // WebView nested inside a clipped/rounded composable renders
                                // fully white — software layer composites correctly instead.
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        mapReady = true
                                        mapLoadError = false
                                        evaluateJavascript("setOnlineState(${online});", null)
                                    }
                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame != false) mapLoadError = true
                                    }
                                }
                                loadDataWithBaseURL(
                                    "https://unpkg.com/",
                                    riderMapHtml(loc.latitude, loc.longitude),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                                webViewRef = this
                            }
                            addView(webView)
                        }
                    }
                )
                }

                FilledIconButton(
                    onClick = {
                        location?.let { l ->
                            webViewRef?.evaluateJavascript("recenter(${l.latitude}, ${l.longitude});", null)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(40.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter on my location", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * Real OpenStreetMap (no API key needed, unlike Google Maps) centered on the rider's live
 * GPS position, with a marker that moves as updateRiderLocation() is called from Kotlin.
 */
private fun riderMapHtml(lat: Double, lng: Double): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
  html, body, #map { height: 100%; margin: 0; padding: 0; transition: filter 0.3s ease; }
  #map.offline { filter: grayscale(1) opacity(0.75); }
</style>
</head><body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
  var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([$lat, $lng], 16);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);

  function dotIcon(color) {
    return L.divIcon({
      className: '',
      html: '<div style="width:20px;height:20px;border-radius:50%;background:' + color +
            ';border:3px solid white;box-shadow:0 0 8px rgba(0,0,0,0.35);"></div>',
      iconSize: [20, 20],
      iconAnchor: [10, 10]
    });
  }

  var marker = L.marker([$lat, $lng], { icon: dotIcon('#A83300') }).addTo(map);

  function updateRiderLocation(lat, lng) {
    marker.setLatLng([lat, lng]);
    map.panTo([lat, lng]);
  }

  function recenter(lat, lng) {
    map.setView([lat, lng], 16);
  }

  function setOnlineState(isOnline) {
    var el = document.getElementById('map');
    if (isOnline) {
      el.classList.remove('offline');
      marker.setIcon(dotIcon('#A83300'));
    } else {
      el.classList.add('offline');
      marker.setIcon(dotIcon('#5C4037'));
    }
  }
</script>
</body></html>
""".trimIndent()

@Composable
private fun StatsGrid(dashboard: DashboardData?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocationOn,
            iconTint = MaterialTheme.colorScheme.primary,
            label = "Today's Earn",
            value = dashboard?.let { "${it.currency_symbol}${"%.0f".format(it.today_earnings)}" } ?: "—"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.DirectionsBike,
            iconTint = MaterialTheme.colorScheme.secondary,
            label = "Deliveries",
            value = dashboard?.completed_deliveries?.toString() ?: "—"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Star,
            iconTint = MaterialTheme.colorScheme.tertiary,
            label = "Rating",
            value = dashboard?.let { "%.1f".format(it.rating_avg) } ?: "—"
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyStateCard(online: Boolean) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                if (online) "No orders yet. We'll alert you the moment one comes in."
                else "Go online to start receiving delivery requests.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncomingOrderCard(
    delivery: DeliveryDto,
    actionInFlight: Boolean,
    onAccept: () -> Unit,
    onDecline: (String) -> Unit
) {
    var accepted by remember(delivery.delivery_id) { mutableStateOf(false) }
    var showDeclineReason by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("New Delivery Request", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(delivery.pickup.name ?: "Pickup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            delivery.pickup.address?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Order ${delivery.order_number}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${delivery.currency_symbol}${"%.2f".format(delivery.est_earning)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!accepted) {
                if (showDeclineReason) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for declining") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDeclineReason = false }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                        Button(
                            onClick = { onDecline(reason) },
                            enabled = reason.isNotBlank() && !actionInFlight,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) { Text("Confirm Decline") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDeclineReason = true }, modifier = Modifier.weight(1f)) {
                            Text("Decline")
                        }
                        Button(
                            onClick = { accepted = true; onAccept() },
                            modifier = Modifier.weight(2f)
                        ) { Text("Accept Order") }
                    }
                }
            } else {
                AssistChip(onClick = {}, label = { Text("Accepted — head to pickup") })
            }
        }
    }
}


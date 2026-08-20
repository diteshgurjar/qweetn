package com.qweet.rider.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.qweet.rider.data.DeliveryDto
import com.qweet.rider.data.GeoUtils
import com.qweet.rider.data.RiderRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FlowStage { DETAILS, NAVIGATING }

/**
 * Full-screen flow shown in place of the normal dashboard once a delivery has been
 * accepted: order details -> slide to go -> live navigation to the restaurant -> arrived ->
 * live navigation to the customer -> delivered. Bottom nav (Home/Wallet/Profile) stays
 * visible because this is placed inside DashboardScreen's Scaffold body, not on top of it.
 */
@Composable
fun DeliveryFlowScreen(
    delivery: DeliveryDto,
    actionInFlight: Boolean,
    onMarkPickedUp: () -> Unit,
    onMarkDelivered: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember(delivery.delivery_id) {
        mutableStateOf(if (delivery.status == "assigned") FlowStage.DETAILS else FlowStage.NAVIGATING)
    }

    when (stage) {
        FlowStage.DETAILS -> OrderDetailsScreen(
            delivery = delivery,
            modifier = modifier,
            onSlideGo = { stage = FlowStage.NAVIGATING }
        )
        FlowStage.NAVIGATING -> NavigationScreen(
            delivery = delivery,
            actionInFlight = actionInFlight,
            modifier = modifier,
            onMarkPickedUp = onMarkPickedUp,
            onMarkDelivered = onMarkDelivered
        )
    }
}

// ---------------------------------------------------------------------------------------
// Stage 1: Order details, full screen, right after accepting.
// ---------------------------------------------------------------------------------------

@Composable
private fun OrderDetailsScreen(delivery: DeliveryDto, onSlideGo: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Order Accepted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Order #${delivery.order_number}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You'll earn", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${delivery.currency_symbol}${"%.2f".format(delivery.est_earning)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (delivery.payment_method == "cod")
                                "Collect ${delivery.currency_symbol}${"%.2f".format(delivery.total_amount)} (COD)"
                            else "Prepaid — no cash to collect"
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("PICKUP FROM")
        InfoCard {
            Text(delivery.pickup.name ?: "Restaurant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            delivery.pickup.address?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("ORDER ITEMS" + if (delivery.items.isNotEmpty()) " (${delivery.items.sumOf { it.quantity }})" else "")
        InfoCard {
            if (delivery.items.isEmpty()) {
                Text("Item details not available for this order.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                delivery.items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.quantity}x ${item.item_name}", modifier = Modifier.weight(1f))
                        Text(
                            "${delivery.currency_symbol}${"%.2f".format(item.subtotal)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (idx != delivery.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order Total", fontWeight = FontWeight.Bold)
                Text("${delivery.currency_symbol}${"%.2f".format(delivery.total_amount)}", fontWeight = FontWeight.Bold)
            }
        }

        delivery.delivery_instructions?.takeIf { it.isNotBlank() }?.let { instructions ->
            Spacer(Modifier.height(20.dp))
            SectionLabel("DELIVERY INSTRUCTIONS")
            InfoCard {
                Row {
                    Icon(Icons.Default.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(instructions, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SlideToConfirmButton(
            text = "Slide to Go",
            containerColor = MaterialTheme.colorScheme.primary,
            onConfirmed = onSlideGo
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------------------
// Stage 2: Live navigation — reused for both the pickup leg and the drop-off leg. Which
// leg is active is driven entirely by delivery.status (assigned -> pickup, picked_up -> drop-off).
// ---------------------------------------------------------------------------------------

@SuppressLint("MissingPermission")
@Composable
private fun NavigationScreen(
    delivery: DeliveryDto,
    actionInFlight: Boolean,
    onMarkPickedUp: () -> Unit,
    onMarkDelivered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val headingToDropoff = delivery.status == "picked_up"

    val destLat = if (headingToDropoff) delivery.dropoff.lat else delivery.pickup.lat
    val destLng = if (headingToDropoff) delivery.dropoff.lng else delivery.pickup.lng
    val destName = if (headingToDropoff) (delivery.dropoff.customer_name ?: delivery.dropoff.label ?: "Customer") else (delivery.pickup.name ?: "Restaurant")
    val destAddress = if (headingToDropoff) {
        listOfNotNull(delivery.dropoff.address_line1, delivery.dropoff.address_line2, delivery.dropoff.city)
            .joinToString(", ").ifBlank { null }
    } else delivery.pickup.address
    val destPhone = if (headingToDropoff) delivery.dropoff.phone else delivery.pickup.phone

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
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

    var riderLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf(false) }
    var mapReloadTick by remember { mutableStateOf(0) }
    var route by remember(delivery.delivery_id, delivery.status) { mutableStateOf<RiderRoute?>(null) }
    // Bumped to force a fresh location fetch attempt when the rider taps "Retry".
    var locationRetryTick by remember { mutableStateOf(0) }
    // How long we've been waiting for a first fix, used to show a helpful message + retry
    // instead of spinning forever when GPS is slow to lock (indoors, cold start, etc.)
    var waitedSeconds by remember(delivery.delivery_id, delivery.status, locationRetryTick) { mutableStateOf(0) }

    DisposableEffect(hasPermission, locationRetryTick) {
        if (!hasPermission) return@DisposableEffect onDispose {}
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationSource = CancellationTokenSource()

        // lastLocation is only a cached fix (can be null after a reboot/fresh install or if
        // stale) — request a fresh active fix in parallel so we don't just sit waiting on a
        // cache that may never arrive.
        fusedClient.lastLocation.addOnSuccessListener { loc -> if (loc != null) riderLocation = loc }
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
            .addOnSuccessListener { loc -> if (loc != null) riderLocation = loc }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                riderLocation = loc
                if (mapReady) webViewRef?.evaluateJavascript("updateRiderLocation(${loc.latitude}, ${loc.longitude});", null)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .build()
        fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
        onDispose {
            fusedClient.removeLocationUpdates(callback)
            cancellationSource.cancel()
        }
    }

    // Tick every second while we have no fix yet, so we can show a "still waiting" message
    // with a retry option instead of an indefinite spinner.
    LaunchedEffect(riderLocation, locationRetryTick, delivery.delivery_id, delivery.status) {
        if (riderLocation != null) return@LaunchedEffect
        while (riderLocation == null) {
            delay(1_000)
            waitedSeconds++
        }
    }

    // Real road route + ETA from OSRM, refreshed periodically as the rider moves.
    LaunchedEffect(delivery.delivery_id, delivery.status) {
        while (true) {
            val loc = riderLocation
            if (loc != null && destLat != null && destLng != null) {
                val r = GeoUtils.fetchRoute(loc.latitude, loc.longitude, destLat, destLng)
                if (r != null) {
                    route = r
                    if (mapReady) {
                        val coordsJs = r.geometry.joinToString(",", prefix = "[", postfix = "]") { "[${it.first},${it.second}]" }
                        webViewRef?.evaluateJavascript("drawRoute($coordsJs);", null)
                    }
                }
                delay(12_000)
            } else {
                delay(2_000)
            }
        }
    }

    val distanceMeters = remember(riderLocation, destLat, destLng) {
        val loc = riderLocation
        if (loc != null && destLat != null && destLng != null) GeoUtils.haversineMeters(loc.latitude, loc.longitude, destLat, destLng) else null
    }
    val arrived = distanceMeters != null && distanceMeters <= 200.0

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !hasPermission -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Location permission needed to navigate.\nEnable it from Settings.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            riderLocation == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (waitedSeconds < 8) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Still waiting for a GPS fix.\nMake sure Location is turned on and you have a clear view of the sky.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { waitedSeconds = 0; locationRetryTick++ }) { Text("Retry") }
                    }
                }
            }
            destLat == null || destLng == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (headingToDropoff) "Customer location not available." else "Restaurant location not available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
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
                val loc = riderLocation!!
                if (!mapReady) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                key(mapReloadTick) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        // Wrapped in a FrameLayout with explicit MATCH_PARENT layout params,
                        // and forced to a software layer — fixes a known Compose issue where a
                        // hardware-accelerated WebView renders as a permanently blank white
                        // screen instead of the map.
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
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        mapReady = true
                                        mapLoadError = false
                                        route?.let { r ->
                                            val coordsJs = r.geometry.joinToString(",", prefix = "[", postfix = "]") { "[${it.first},${it.second}]" }
                                            evaluateJavascript("drawRoute($coordsJs);", null)
                                        }
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
                                    navigationMapHtml(loc.latitude, loc.longitude, destLat, destLng, destName),
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
            }
        }

        // Bottom sheet: status, place, ETA, order summary, instructions, actions, slide button.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill(if (headingToDropoff) "Heading to Customer" else "Heading to Pickup")
                    Spacer(Modifier.height(6.dp))
                    Text(destName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    destAddress?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    val km = (route?.distanceMeters ?: distanceMeters)?.let { "%.1f km".format(it / 1000.0) } ?: "—"
                    val mins = route?.let { "${(it.durationSeconds / 60.0).roundToInt()} min" }
                    Text(km, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(mins ?: "calculating…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            OrderSummaryRow(delivery)

            if (headingToDropoff) {
                delivery.delivery_instructions?.takeIf { it.isNotBlank() }?.let { instructions ->
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Delivery Instructions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(instructions, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("google.navigation:q=$destLat,$destLng(${Uri.encode(destName)})")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                        runCatching { context.startActivity(intent) }.onFailure {
                            val fallback = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng")
                            context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
                        }
                    },
                    enabled = destLat != null && destLng != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
                if (!destPhone.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$destPhone"))) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (headingToDropoff) "Call Customer" else "Call Restaurant")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = arrived, enter = fadeIn(), exit = fadeOut()) {
                SlideToConfirmButton(
                    text = if (headingToDropoff) "Slide to Deliver" else "Slide — Arrived at Restaurant",
                    containerColor = MaterialTheme.colorScheme.secondary,
                    loading = actionInFlight,
                    onConfirmed = { if (headingToDropoff) onMarkDelivered() else onMarkPickedUp() }
                )
            }
            if (!arrived) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        distanceMeters?.let {
                            "Get within 200m to ${if (headingToDropoff) "confirm delivery" else "mark arrival"} (${it.roundToInt()}m away)"
                        } ?: "Getting your location…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryRow(delivery: DeliveryDto) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Order #${delivery.order_number}", style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${delivery.items.sumOf { it.quantity }.takeIf { it > 0 } ?: delivery.items.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (delivery.items.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(6.dp))
            delivery.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.quantity}x ${item.item_name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${delivery.currency_symbol}${"%.2f".format(item.subtotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Leaflet/OSM map: rider's live dot + a destination pin, with the OSRM road route drawn between them. */
private fun navigationMapHtml(riderLat: Double, riderLng: Double, destLat: Double, destLng: Double, destLabel: String): String {
    val safeLabel = destLabel.replace("\\", "\\\\").replace("\"", "\\\"")
    return """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>html,body,#map{height:100%;margin:0;padding:0;}</style>
</head><body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
  var map = L.map('map', { zoomControl: false, attributionControl: false });
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);

  function dotIcon(color) {
    return L.divIcon({
      className: '',
      html: '<div style="width:20px;height:20px;border-radius:50%;background:' + color +
            ';border:3px solid white;box-shadow:0 0 8px rgba(0,0,0,0.35);"></div>',
      iconSize: [20, 20], iconAnchor: [10, 10]
    });
  }
  function pinIcon(color) {
    return L.divIcon({
      className: '',
      html: '<div style="width:18px;height:18px;border-radius:50% 50% 50% 0;background:' + color +
            ';transform:rotate(-45deg);border:2px solid white;box-shadow:0 0 6px rgba(0,0,0,0.4);"></div>',
      iconSize: [18, 18], iconAnchor: [9, 18]
    });
  }

  var riderMarker = L.marker([$riderLat, $riderLng], { icon: dotIcon('#A83300') }).addTo(map);
  var destMarker = L.marker([$destLat, $destLng], { icon: pinIcon('#006e1c') }).addTo(map).bindPopup("$safeLabel");
  var routeLine = null;

  map.fitBounds(L.latLngBounds([[$riderLat, $riderLng], [$destLat, $destLng]]), { padding: [70, 70] });

  function updateRiderLocation(lat, lng) {
    riderMarker.setLatLng([lat, lng]);
  }
  function drawRoute(coords) {
    if (routeLine) map.removeLayer(routeLine);
    routeLine = L.polyline(coords, { color: '#A83300', weight: 5, opacity: 0.85 }).addTo(map);
  }
</script>
</body></html>
""".trimIndent()
}

// ---------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * A left-to-right slide-to-confirm button: drag the knob to the end to trigger [onConfirmed].
 * Snaps back if released before the ~80% threshold.
 */
@Composable
fun SlideToConfirmButton(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ArrowForward,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackWidthPx by remember { mutableStateOf(0f) }
    val knobSizeDp = 56.dp
    val knobSizePx = with(density) { knobSizeDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var confirmed by remember(text) { mutableStateOf(false) }
    val maxOffset = (trackWidthPx - knobSizePx).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .clip(RoundedCornerShape(32.dp))
            .background(if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(knobSizeDp - 8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .then(
                    if (enabled && !loading) {
                        Modifier.pointerInput(maxOffset) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetX.value >= maxOffset * 0.8f) {
                                            offsetX.animateTo(maxOffset, tween(150))
                                            if (!confirmed) {
                                                confirmed = true
                                                onConfirmed()
                                            }
                                        } else {
                                            offsetX.animateTo(0f, tween(200))
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxOffset))
                                    }
                                }
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = containerColor)
            } else {
                Icon(icon, contentDescription = null, tint = containerColor)
            }
        }
    }
}


package com.qweet.rider.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Orders" tab, next to Home. Two jobs:
 *  1. Surface the rider's current working order (if any) with a one-tap way back into it, so
 *     an order left behind by an accidental back-press or tab switch is never really lost —
 *     just a tap away. This comes straight from orders.php, the same server-verified source
 *     DashboardScreen itself uses, so it always reflects exactly what's truly assigned right now.
 *  2. Show past delivered/cancelled orders (history.php), paginated.
 */
private enum class OrderStatusFilter(val label: String) {
    ALL("All"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onContinueWorkingOrder: () -> Unit) {
    val scope = rememberCoroutineScope()

    var activeDeliveries by remember { mutableStateOf<List<DeliveryDto>>(emptyList()) }
    var historyItems by remember { mutableStateOf<List<HistoryItemDto>>(emptyList()) }
    var dashboard by remember { mutableStateOf<DashboardData?>(null) }
    var page by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    var statusFilter by remember { mutableStateOf(OrderStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(retryTick) {
        loading = true
        val ordResult = runCatching { ApiClient.service.orders() }
        val ord = ordResult.getOrNull()?.body()
        if (ord?.success == true) {
            activeDeliveries = ord.data.orEmpty()
        }

        val dashResult = runCatching { ApiClient.service.dashboard() }
        val dash = dashResult.getOrNull()?.body()
        if (dash?.success == true) dashboard = dash.data

        val histResult = runCatching { ApiClient.service.history(page = 1) }
        val hist = histResult.getOrNull()?.body()
        if (hist?.success == true) {
            errorText = null
            historyItems = hist.data.orEmpty()
            page = 1
            totalPages = hist.pagination?.total_pages ?: 1
        } else if (ord?.success != true) {
            errorText = hist?.error ?: describeFailure(histResult)
        }
        loading = false
    }

    val filteredItems = remember(historyItems, statusFilter, searchQuery) {
        historyItems.filter { item ->
            val statusOk = when (statusFilter) {
                OrderStatusFilter.ALL -> true
                OrderStatusFilter.DELIVERED -> item.status == "delivered"
                OrderStatusFilter.CANCELLED -> item.status == "cancelled"
            }
            val searchOk = searchQuery.isBlank() || item.order_number.contains(searchQuery, ignoreCase = true)
            statusOk && searchOk
        }
    }

    val groupedItems = remember(filteredItems) {
        filteredItems.groupBy { dateBucketLabel(it.date) }
    }

    val loadedDeliveredEarnings = remember(historyItems) {
        historyItems.filter { it.status == "delivered" }.sumOf { it.earning_amount ?: 0.0 }
    }
    val loadedDeliveredCount = remember(historyItems) { historyItems.count { it.status == "delivered" } }
    val loadedCancelledCount = remember(historyItems) { historyItems.count { it.status == "cancelled" } }

    fun loadMore() {
        if (loadingMore || page >= totalPages) return
        loadingMore = true
        scope.launch {
            val nextPage = page + 1
            val histResult = runCatching { ApiClient.service.history(page = nextPage) }
            val hist = histResult.getOrNull()?.body()
            if (hist?.success == true) {
                historyItems = historyItems + hist.data.orEmpty()
                page = nextPage
                totalPages = hist.pagination?.total_pages ?: totalPages
            }
            loadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorText?.let { msg -> ErrorBanner(message = msg, onRetry = { retryTick++ }) }

            if (loading && activeDeliveries.isEmpty() && historyItems.isEmpty() && errorText == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            EarningsSummaryCard(
                currencySymbol = dashboard?.currency_symbol ?: historyItems.firstOrNull()?.currency_symbol ?: "",
                todayEarnings = dashboard?.today_earnings,
                pendingPayout = dashboard?.pending_payout,
                loadedDeliveredCount = loadedDeliveredCount,
                loadedCancelledCount = loadedCancelledCount,
                loadedDeliveredEarnings = loadedDeliveredEarnings
            )

            if (activeDeliveries.isNotEmpty()) {
                Text("Working Order", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                activeDeliveries.forEach { delivery ->
                    WorkingOrderCard(delivery = delivery, onContinue = onContinueWorkingOrder)
                }
            }

            Text("Past Orders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by order number") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OrderStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { statusFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!loading && filteredItems.isEmpty() && errorText == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (historyItems.isEmpty()) "No past orders yet." else "No orders match this filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            groupedItems.forEach { (bucket, items) ->
                Text(
                    bucket,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                items.forEach { item -> HistoryOrderCard(item) }
            }

            if (page < totalPages) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = { loadMore() }) { Text("Load more") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Buckets a MySQL-style "yyyy-MM-dd HH:mm:ss" (or date-only) string into Today / Yesterday / a readable date. */
private fun dateBucketLabel(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Earlier"
    val parsed = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")
        .firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr) }.getOrNull()
        } ?: return "Earlier"

    val target = Calendar.getInstance().apply { time = parsed }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(target, today) -> "Today"
        sameDay(target, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
    }
}

private fun formatTime(dateStr: String?): String? {
    if (dateStr.isNullOrBlank()) return null
    val parsed = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
        .firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr) }.getOrNull()
        } ?: return null
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed)
}

/** Top-of-list stats strip: today's earnings, pending payout, and a summary of what's loaded below. */
@Composable
private fun EarningsSummaryCard(
    currencySymbol: String,
    todayEarnings: Double?,
    pendingPayout: Double?,
    loadedDeliveredCount: Int,
    loadedCancelledCount: Int,
    loadedDeliveredEarnings: Double
) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Earnings summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    label = "Today",
                    value = todayEarnings?.let { "$currencySymbol${"%.0f".format(it)}" } ?: "—"
                )
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    label = "Pending payout",
                    value = pendingPayout?.let { "$currencySymbol${"%.0f".format(it)}" } ?: "—"
                )
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    label = "Loaded earned",
                    value = "$currencySymbol${"%.0f".format(loadedDeliveredEarnings)}"
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(label = "$loadedDeliveredCount delivered", color = Color(0xFF43A047))
                StatusBadge(label = "$loadedCancelledCount cancelled", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SummaryStat(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkingOrderCard(delivery: DeliveryDto, onContinue: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Order #${delivery.order_number}", fontWeight = FontWeight.Bold)
                Text(
                    if (delivery.status == "picked_up") "Heading to customer" else "Heading to pickup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onContinue) { Text("Continue") }
        }
    }
}

@Composable
private fun HistoryOrderCard(item: HistoryItemDto) {
    val delivered = item.status == "delivered"
    var expanded by remember(item.delivery_id) { mutableStateOf(false) }
    val statusColor = if (delivered) Color(0xFF43A047) else MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (delivered) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = statusColor
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Order #${item.order_number}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(
                        label = if (delivered) "Delivered" else "Cancelled",
                        color = statusColor
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (delivered && item.earning_amount != null) {
                    Text(
                        "+${item.currency_symbol}${"%.2f".format(item.earning_amount)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))

                    if (!item.cancel_reason.isNullOrBlank()) {
                        DetailRow(icon = Icons.Default.Cancel, label = "Reason", value = item.cancel_reason)
                        Spacer(Modifier.height(8.dp))
                    }
                    formatTime(item.date)?.let { time ->
                        DetailRow(icon = Icons.Default.Receipt, label = "Time", value = time)
                        Spacer(Modifier.height(8.dp))
                    }
                    item.payment_method?.let { method ->
                        DetailRow(icon = Icons.Default.Payments, label = "Payment method", value = method.replaceFirstChar { it.uppercase() })
                        Spacer(Modifier.height(8.dp))
                    }
                    DetailRow(icon = Icons.Default.DirectionsBike, label = "Order value", value = "${item.currency_symbol}${"%.2f".format(item.total_amount)}")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

package com.qweet.rider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.DailyEarningsPoint
import com.qweet.rider.data.EarningsData
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Detailed earnings breakdown — today / this week / this month / all-time,
 * split by delivery pay vs bonus vs tips, plus a 7-day trend chart. Reuses
 * the same EarningsData the Wallet screen already fetches (earnings.php),
 * so opening this doesn't cost an extra network call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(data: EarningsData, onBack: () -> Unit) {
    val currency = data.currency_symbol
    val totals = data.totals

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Earnings Analytics", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AllTimeCard(currency, totals.total_earned, totals.total_deliveries)

            Text("This Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PeriodCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Today,
                    label = "Today",
                    amount = "$currency${"%.2f".format(totals.today_total)}",
                    subLabel = "${totals.today_deliveries} deliveries"
                )
                PeriodCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CalendarViewWeek,
                    label = "This Week",
                    amount = "$currency${"%.2f".format(totals.this_week)}",
                    subLabel = "${totals.week_deliveries} deliveries"
                )
            }
            PeriodCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.DateRange,
                label = "This Month",
                amount = "$currency${"%.2f".format(totals.this_month)}",
                subLabel = "${totals.month_deliveries} deliveries",
                wide = true
            )

            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            WeeklyTrendChart(currency, data.daily_series)

            Text("Earnings Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            EarningsBreakdownCard(currency, totals.delivery_total, totals.bonus_total, totals.tips_total, totals.total_earned)

            Text("Payout Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Settled",
                    amount = "$currency${"%.2f".format(totals.settled_total)}",
                    color = QweetSecondary
                )
                StatusMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Pending Settlement",
                    amount = "$currency${"%.2f".format(totals.pending_total)}",
                    color = QweetPrimary
                )
            }

            if (totals.total_deliveries > 0) {
                val avg = totals.delivery_total / totals.total_deliveries
                Text(
                    "Average delivery payout: $currency${"%.2f".format(avg)} across ${totals.total_deliveries} deliveries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AllTimeCard(currency: String, totalEarned: Double, totalDeliveries: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(QweetPrimary, Color(0xFF832600))),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "TOTAL LIFETIME EARNINGS",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "$currency${"%.2f".format(totalEarned)}",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "$totalDeliveries deliveries completed",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PeriodCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    subLabel: String,
    wide: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = QweetSurfaceContainerLowest),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = QweetPrimary, modifier = Modifier.size(20.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = QweetPrimary)
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, tint = QweetPrimary, modifier = Modifier.size(16.dp))
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = QweetPrimary)
                Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusMiniCard(modifier: Modifier = Modifier, label: String, amount: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun EarningsBreakdownCard(currency: String, delivery: Double, bonus: Double, tips: Double, total: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = QweetSurfaceContainerLowest), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Proportion bar
            val safeTotal = if (total > 0) total else 1.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (delivery > 0) Box(Modifier.weight((delivery / safeTotal).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(QweetPrimary))
                if (bonus > 0) Box(Modifier.weight((bonus / safeTotal).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(QweetSecondary))
                if (tips > 0) Box(Modifier.weight((tips / safeTotal).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFFB58A00)))
                if (delivery <= 0 && bonus <= 0 && tips <= 0) Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            BreakdownRow(Icons.Filled.LocalShipping, "Delivery Earnings", "$currency${"%.2f".format(delivery)}", QweetPrimary)
            BreakdownRow(Icons.Filled.Bolt, "Bonuses", "$currency${"%.2f".format(bonus)}", QweetSecondary)
            BreakdownRow(Icons.Filled.Favorite, "Tips from Customers", "$currency${"%.2f".format(tips)}", Color(0xFFB58A00))
        }
    }
}

@Composable
private fun BreakdownRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, amount: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(amount, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun WeeklyTrendChart(currency: String, series: List<DailyEarningsPoint>) {
    Card(colors = CardDefaults.cardColors(containerColor = QweetSurfaceContainerLowest), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (series.isEmpty()) {
                Text(
                    "No earnings activity in the last 7 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val maxAmount = (series.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(1.0)
            val barColor = QweetPrimary
            val dayFormatter = remember(series) { SimpleDateFormat("EEE", Locale.getDefault()) }
            val parser = remember(series) { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val barCount = series.size
                val spacing = 12.dp.toPx()
                val barWidth = (size.width - spacing * (barCount - 1)) / barCount
                series.forEachIndexed { index, point ->
                    val barHeightRatio = (point.amount / maxAmount).toFloat().coerceIn(0f, 1f)
                    val barHeight = size.height * barHeightRatio
                    val x = index * (barWidth + spacing)
                    val topLeft = Offset(x, size.height - barHeight)
                    drawRoundRect(
                        color = if (point.amount > 0) barColor else barColor.copy(alpha = 0.15f),
                        topLeft = topLeft,
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(4f)),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
                // Baseline
                drawLine(
                    color = Color.LightGray,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                series.forEach { point ->
                    val label = runCatching {
                        val parsed = parser.parse(point.date)
                        if (parsed != null) dayFormatter.format(parsed) else point.date.takeLast(2)
                    }.getOrDefault(point.date.takeLast(2))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(0.dp).weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            val weekTotal = series.sumOf { it.amount }
            Text(
                "7-day total: $currency${"%.2f".format(weekTotal)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

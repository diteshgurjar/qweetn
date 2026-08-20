package com.qweet.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.CreateWithdrawalRequest
import com.qweet.rider.data.EarningsData
import com.qweet.rider.data.EarningsTotals
import com.qweet.rider.data.WalletHistoryItem
import com.qweet.rider.data.WithdrawalDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(onManageBankAccount: () -> Unit = {}) {
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    var earnings by remember { mutableStateOf<EarningsData?>(null) }

    var showWithdrawSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.earnings() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            earnings = body.data
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    // Full-screen detailed breakdown — reuses the already-fetched earnings
    // data, so it opens instantly with no extra network round trip.
    if (showAnalytics && earnings != null) {
        AnalyticsScreen(data = earnings!!, onBack = { showAnalytics = false })
        return
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Wallet", fontWeight = FontWeight.Bold) })
    }) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && earnings == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorText != null && earnings == null -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        ErrorBanner(message = errorText!!, onRetry = { retryTick++ })
                    }
                }
                earnings != null -> {
                    WalletContent(
                        data = earnings!!,
                        onWithdrawClick = { showWithdrawSheet = true },
                        onAddBankAccount = onManageBankAccount,
                        onWithdrawalHistory = { showHistorySheet = true },
                        onViewAnalytics = { showAnalytics = true }
                    )
                }
            }
        }
    }

    if (showWithdrawSheet && earnings != null) {
        WithdrawFundsSheet(
            data = earnings!!,
            onDismiss = { showWithdrawSheet = false },
            onManageBankAccount = {
                showWithdrawSheet = false
                onManageBankAccount()
            },
            onWithdrawalSubmitted = {
                // Refresh balance/history so the new pending withdrawal shows up immediately.
                retryTick++
            }
        )
    }

    if (showHistorySheet) {
        WithdrawalHistorySheet(
            currencySymbol = earnings?.currency_symbol ?: "",
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun WalletContent(
    data: EarningsData,
    onWithdrawClick: () -> Unit,
    onAddBankAccount: () -> Unit,
    onWithdrawalHistory: () -> Unit,
    onViewAnalytics: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        BalanceCard(data, onWithdrawClick)

        if (data.totals.pending_withdrawal > 0) {
            PendingWithdrawalCard(data.currency_symbol, data.totals.pending_withdrawal)
        }

        MonthlyPerformance(data.currency_symbol, data.totals)

        OutlinedButton(
            onClick = onViewAnalytics,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("View Detailed Earnings Analytics", fontWeight = FontWeight.Medium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            WalletActionButton(
                icon = Icons.Filled.AccountBalance,
                label = "Add Bank Account",
                modifier = Modifier.weight(1f),
                onClick = onAddBankAccount
            )
            WalletActionButton(
                icon = Icons.Filled.History,
                label = "Withdrawal History",
                modifier = Modifier.weight(1f),
                onClick = onWithdrawalHistory
            )
        }

        Text("Wallet History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (data.wallet_history.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No activity yet — completed deliveries, bonuses and withdrawals will show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.wallet_history.forEach { item ->
                    WalletHistoryRow(item, data.currency_symbol)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(data: EarningsData, onWithdrawClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(QweetPrimary, Color(0xFF832600))),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TODAY'S EARNING",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "${data.currency_symbol}${"%.2f".format(data.totals.today_total)}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        "CURRENT BALANCE",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "${data.currency_symbol}${"%.2f".format(data.totals.available_balance)}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Button(
                onClick = onWithdrawClick,
                enabled = data.totals.available_balance > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = QweetPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Withdraw Funds", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LAST PAYOUT", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    val payout = data.last_payout
                    Text(
                        if (payout != null) "${data.currency_symbol}${"%.2f".format(payout.amount)} on ${formatDate(payout.date)}" else "No payouts yet",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PendingWithdrawalCard(currencySymbol: String, amount: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = QweetPrimaryContainer.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).background(QweetPrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = QweetPrimary)
                }
                Column {
                    Text("PENDING WITHDRAWAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "$currencySymbol${"%.2f".format(amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            AssistChip(onClick = {}, label = { Text("Processing") })
        }
    }
}

@Composable
private fun MonthlyPerformance(currencySymbol: String, totals: EarningsTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Monthly Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "This Month",
                value = "$currencySymbol${"%.2f".format(totals.this_month)}",
                icon = Icons.Filled.DateRange,
                valueColor = QweetPrimary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Total Bonus",
                value = "$currencySymbol${"%.2f".format(totals.bonus_total)}",
                icon = Icons.Filled.CardGiftcard,
                valueColor = QweetSecondary
            )
        }
        StatCard(
            modifier = Modifier.fillMaxWidth(),
            label = "Total Tips by Customer",
            value = "$currencySymbol${"%.2f".format(totals.tips_total)}",
            icon = Icons.Filled.Favorite,
            valueColor = QweetOnSurface,
            wide = true
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            }
        }
    }
}

@Composable
private fun WalletActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(72.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WalletHistoryRow(item: WalletHistoryItem, currencySymbol: String) {
    val isCredit = item.amount >= 0
    val (icon, iconTint) = when (item.kind) {
        "bonus" -> Icons.Filled.Bolt to QweetSecondary
        "tip" -> Icons.Filled.Favorite to QweetSecondary
        "withdrawal" -> Icons.Filled.ArrowDownward to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.LocalShipping to QweetPrimary
    }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).background(iconTint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
                Column {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatDate(item.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(item.status)
                    }
                }
            }
            Text(
                (if (isCredit) "+" else "-") + "$currencySymbol${"%.2f".format(kotlin.math.abs(item.amount))}",
                fontWeight = FontWeight.Bold,
                color = if (isCredit) QweetPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (icon, label, color) = when (status) {
        "settled", "completed" -> Triple(Icons.Filled.CheckCircle, "Success", QweetSecondary)
        "processing" -> Triple(Icons.Filled.Schedule, "Processing", MaterialTheme.colorScheme.onSurfaceVariant)
        "rejected" -> Triple(Icons.Filled.Close, "Rejected", MaterialTheme.colorScheme.error)
        else -> Triple(Icons.Filled.Schedule, "Pending", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// Hardcoded for now, per product — swap for a real backend PIN-verify call
// (api/v1/rider/verify-pin.php or similar) once that endpoint exists. Every
// rider shares this PIN until then.
private const val DEFAULT_WITHDRAWAL_PIN = "782365"
private const val WITHDRAWAL_PIN_LENGTH = 6

private enum class WithdrawStep { AMOUNT, PIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawFundsSheet(
    data: EarningsData,
    onDismiss: () -> Unit,
    onManageBankAccount: () -> Unit,
    onWithdrawalSubmitted: () -> Unit
) {
    var step by remember { mutableStateOf(WithdrawStep.AMOUNT) }
    var amountText by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val amount = amountText.toDoubleOrNull() ?: 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (step) {
            WithdrawStep.AMOUNT -> Column(
                modifier = Modifier.padding(20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Withdraw Funds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Available balance: ${data.currency_symbol}${"%.2f".format(data.totals.available_balance)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) amountText = input },
                    label = { Text("Amount") },
                    prefix = { Text(data.currency_symbol) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Payouts go to the bank/UPI details on your profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onManageBankAccount) { Text("Manage bank / UPI details") }

                Button(
                    onClick = {
                        pinInput = ""
                        pinError = false
                        submitError = null
                        step = WithdrawStep.PIN
                    },
                    enabled = amount > 0 && amount <= data.totals.available_balance,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Request Withdrawal", fontWeight = FontWeight.Bold)
                }
            }

            WithdrawStep.PIN -> WithdrawPinContent(
                amountLabel = "${data.currency_symbol}${"%.2f".format(amount)}",
                pinInput = pinInput,
                pinError = pinError,
                submitting = submitting,
                submitError = submitError,
                success = success,
                onBack = { step = WithdrawStep.AMOUNT },
                onDigit = { d ->
                    if (!submitting && !success && pinInput.length < WITHDRAWAL_PIN_LENGTH) {
                        pinInput += d
                        pinError = false
                        submitError = null
                    }
                },
                onBackspace = {
                    if (!submitting && !success && pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                },
                onRetry = {
                    pinInput = ""
                    pinError = false
                    submitError = null
                }
            )
        }
    }

    // Once 6 digits are entered, verify locally against the default PIN and
    // submit the withdrawal request if it matches. Wrong PIN clears the
    // entry and shows a Retry option instead of silently failing.
    LaunchedEffect(pinInput, step) {
        if (step == WithdrawStep.PIN && pinInput.length == WITHDRAWAL_PIN_LENGTH && !submitting && !success) {
            if (pinInput != DEFAULT_WITHDRAWAL_PIN) {
                pinError = true
                pinInput = ""
                return@LaunchedEffect
            }

            submitting = true
            submitError = null
            val result = runCatching { ApiClient.service.createWithdrawal(CreateWithdrawalRequest(amount)) }
            val body = result.getOrNull()?.body()
            submitting = false

            if (body?.success == true) {
                success = true
                onWithdrawalSubmitted()
                kotlinx.coroutines.delay(1200)
                onDismiss()
            } else {
                submitError = body?.error ?: describeFailure(result)
                pinInput = ""
            }
        }
    }
}

@Composable
private fun WithdrawPinContent(
    amountLabel: String,
    pinInput: String,
    pinError: Boolean,
    submitting: Boolean,
    submitError: String?,
    success: Boolean,
    onBack: () -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp).padding(bottom = 24.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !submitting) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Security PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Withdrawing $amountLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            success -> {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = QweetSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Text("Withdrawal Requested", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your request has been sent to the Admin for approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    "Enter your $WITHDRAWAL_PIN_LENGTH-digit PIN to authorize this withdrawal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(WITHDRAWAL_PIN_LENGTH) { i ->
                        val filled = i < pinInput.length
                        val dotColor = when {
                            pinError -> MaterialTheme.colorScheme.error
                            filled -> QweetPrimary
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(dotColor, CircleShape)
                                .border(
                                    width = if (filled || pinError) 0.dp else 2.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (pinError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                if (submitError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(submitError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        keys.chunked(3).forEach { rowKeys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowKeys.forEach { key ->
                                    when (key) {
                                        "" -> Spacer(Modifier.size(56.dp))
                                        "⌫" -> IconButton(onClick = onBackspace, modifier = Modifier.size(56.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                        }
                                        else -> OutlinedButton(
                                            onClick = { onDigit(key) },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Text(key, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawalHistorySheet(currencySymbol: String, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<WithdrawalDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        val result = runCatching { ApiClient.service.withdrawals() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            items = body.data
            errorText = null
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Withdrawal History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorText != null -> Text(errorText!!, color = MaterialTheme.colorScheme.error)
                items.isEmpty() -> Text(
                    "No withdrawal requests yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { w ->
                        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("$currencySymbol${"%.2f".format(w.amount)}", fontWeight = FontWeight.Bold)
                                    Text(
                                        formatDate(w.requested_at) + " · " + w.payout_method.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(w.status)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    // Backend sends "YYYY-MM-DD HH:MM:SS" — show just the date portion for a compact row.
    return raw.substringBefore(' ')
}

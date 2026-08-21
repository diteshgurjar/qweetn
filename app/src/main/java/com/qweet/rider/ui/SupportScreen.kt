package com.qweet.rider.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.CreateTicketRequest
import com.qweet.rider.data.SupportTicketDto
import kotlinx.coroutines.launch

/**
 * Rider's "Help & Support" — a list of tickets they've raised, each opening
 * into a live chat thread (SupportChatScreen) with QWEET's admin team. Same
 * support_tickets/support_messages backend the customer app already uses,
 * just scoped to raiser_role='rider'. Admin replies arrive as real push
 * notifications too (notifyUser() -> FCM), not just in this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var tickets by remember { mutableStateOf<List<SupportTicketDto>>(emptyList()) }
    var retryTick by remember { mutableStateOf(0) }
    var openTicketId by remember { mutableStateOf<Int?>(null) }
    var showNewTicketSheet by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            val result = runCatching { ApiClient.service.supportTickets() }
            val body = result.getOrNull()?.body()
            if (body?.success == true) {
                errorText = null
                tickets = body.tickets.orEmpty()
            } else {
                errorText = body?.error ?: describeFailure(result)
            }
            loading = false
        }
    }

    LaunchedEffect(retryTick) { reload() }

    // Opened a ticket -> hand off to the chat thread. Returning refreshes the list
    // (status/unread-count may have changed while the rider was in the thread).
    openTicketId?.let { id ->
        SupportChatScreen(
            ticketId = id,
            onBack = {
                openTicketId = null
                retryTick++
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTicketSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Request") }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && tickets.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                errorText != null && tickets.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        ErrorBanner(message = errorText!!, onRetry = { retryTick++ })
                    }
                }
                tickets.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("No support requests yet", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Need help with a delivery, payout, or your account? We're here for you.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tickets) { t ->
                            SupportTicketRow(ticket = t, onClick = { openTicketId = t.id })
                        }
                        item { Spacer(Modifier.height(72.dp)) } // room above the FAB
                    }
                }
            }
        }
    }

    if (showNewTicketSheet) {
        NewSupportTicketSheet(
            onDismiss = { showNewTicketSheet = false },
            onCreated = { ticketId ->
                showNewTicketSheet = false
                retryTick++
                openTicketId = ticketId
            }
        )
    }
}

@Composable
private fun SupportTicketRow(ticket: SupportTicketDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🎫 ${ticket.ticket_no}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(ticket.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TicketStatusChip(status = ticket.status)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ticket.order_number?.let { "Order #$it" } ?: categoryLabel(ticket.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ticket.unread_count > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("${ticket.unread_count} new")
                    }
                }
            }
        }
    }
}

@Composable
fun TicketStatusChip(status: String) {
    val (label, color) = when (status) {
        "open" -> "Open" to MaterialTheme.colorScheme.tertiary
        "in_progress" -> "In Progress" to MaterialTheme.colorScheme.primary
        "resolved" -> "Resolved" to MaterialTheme.colorScheme.secondary
        "closed" -> "Closed" to MaterialTheme.colorScheme.outline
        else -> status to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(disabledLabelColor = color, disabledContainerColor = color.copy(alpha = 0.12f))
    )
}

fun categoryLabel(category: String): String = when (category) {
    "order_issue" -> "Order Issue"
    "payment" -> "Payment"
    "delivery" -> "Delivery"
    "account" -> "Account"
    "other" -> "Other"
    else -> "General"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSupportTicketSheet(onDismiss: () -> Unit, onCreated: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val categories = listOf("general" to "General", "payment" to "Payment / Payout", "delivery" to "Delivery", "account" to "My Account", "other" to "Other")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Raise a New Request", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
                OutlinedTextField(
                    value = categories.first { it.first == category }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("What is this about?") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    categories.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { category = value; categoryMenuOpen = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !submitting && subject.isNotBlank() && message.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    submitting = true
                    errorText = null
                    scope.launch {
                        val result = runCatching {
                            ApiClient.service.createSupportTicket(CreateTicketRequest(subject = subject.trim(), message = message.trim(), category = category))
                        }
                        val body = result.getOrNull()?.body()
                        if (body?.success == true && body.ticket_id != null) {
                            onCreated(body.ticket_id)
                        } else {
                            errorText = body?.error ?: describeFailure(result)
                        }
                        submitting = false
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Submit Request")
                }
            }
        }
    }
}

package com.qweet.rider.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.SupportMessageDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live chat thread for one support ticket. Polls for new messages every 4s
 * while open (same interval philosophy as DashboardScreen's order polling) —
 * cheap on shared PHP hosting and plenty responsive for a support chat.
 * Admin's replies also arrive as a push notification (see includes/fcm.php)
 * even if the rider isn't in this screen at the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(ticketId: Int, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<SupportMessageDto>>(emptyList()) }
    var status by remember { mutableStateOf("open") }
    var resolutionReason by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    suspend fun poll(afterId: Int) {
        val result = runCatching { ApiClient.service.supportMessages(ticketId = ticketId, afterId = afterId) }
        val body = result.getOrNull()?.body()
        if (body?.success == true) {
            errorText = null
            status = body.status ?: status
            resolutionReason = body.resolution_reason
            if (!body.messages.isNullOrEmpty()) {
                messages = messages + body.messages
            }
        } else if (messages.isEmpty()) {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    LaunchedEffect(ticketId) {
        while (true) {
            poll(afterId = messages.lastOrNull()?.id ?: 0)
            delay(4_000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val isClosed = status == "closed"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Support Chat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(statusLabel(status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (isClosed) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                        "This ticket is closed. Raise a new request if you still need help.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message…") },
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4
                        )
                        FilledIconButton(
                            enabled = draft.isNotBlank() && !sending,
                            onClick = {
                                val text = draft.trim()
                                draft = ""
                                sending = true
                                scope.launch {
                                    val result = runCatching { ApiClient.service.sendSupportMessage(ticketId = ticketId, message = text) }
                                    val body = result.getOrNull()?.body()
                                    if (body?.success == true && body.message != null) {
                                        messages = messages + body.message
                                        status = body.status ?: status
                                    } else {
                                        errorText = body?.error ?: describeFailure(result)
                                        draft = text // give it back so the rider doesn't lose what they typed
                                    }
                                    sending = false
                                }
                            }
                        ) {
                            if (sending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                errorText != null && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        ErrorBanner(message = errorText!!, onRetry = { scope.launch { poll(0) } })
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { m -> ChatBubble(m) }
                        if (resolutionReason != null && isClosed) {
                            item {
                                Text(
                                    "Reason: $resolutionReason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: SupportMessageDto) {
    if (m.sender_type == "system") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(m.message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }

    val isMe = m.sender_type == "rider"
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text("QWEET Support", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = bubbleColor
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (m.message.isNotBlank()) {
                        Text(m.message, color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                    m.attachment_url?.let {
                        if (m.message.isNotBlank()) Spacer(Modifier.height(4.dp))
                        Text("📎 ${m.attachment_name ?: "Attachment"}", color = textColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(m.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "open" -> "Open"
    "in_progress" -> "In Progress"
    "resolved" -> "Resolved"
    "closed" -> "Closed"
    else -> status
}

package com.qweet.rider.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Something went wrong",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * Turns a failed Retrofit call into a readable message instead of leaving the screen blank
 * with no explanation. Most importantly: catches the case where the hosting provider's
 * anti-bot challenge page (HTML) came back instead of real JSON, which previously just
 * crashed the JSON parser silently inside runCatching.
 */
fun <T> describeFailure(result: Result<retrofit2.Response<T>>): String {
    val ex = result.exceptionOrNull()
    if (ex != null) {
        return when (ex) {
            is java.net.UnknownHostException ->
                "Couldn't reach the server. Check your internet connection."
            is java.net.SocketTimeoutException ->
                "The server took too long to respond. Try again."
            is com.google.gson.JsonSyntaxException, is com.google.gson.JsonParseException ->
                "The server sent back an unexpected page instead of data — this usually means " +
                    "the hosting provider's anti-bot check needs to be re-solved. Try logging out and back in."
            else -> "${ex.javaClass.simpleName}: ${ex.message ?: "unknown error"}"
        }
    }
    val resp = result.getOrNull()
    if (resp != null) {
        // Prefer the server's own message when it sent one — it's usually far more specific
        // than a generic "unauthorized" (e.g. "Missing Authorization Bearer token." vs
        // "Invalid or expired session." are different bugs to chase).
        val serverError = runCatching {
            val body = resp.errorBody()?.string()
            if (body.isNullOrBlank()) null
            else com.google.gson.JsonParser.parseString(body).asJsonObject.get("error")?.asString
        }.getOrNull()

        if (resp.code() == 401 || resp.code() == 403) {
            return (serverError ?: "Session expired or unauthorized") + " (HTTP ${resp.code()})."
        }
        if (!resp.isSuccessful) {
            return "Server error (HTTP ${resp.code()})" + if (!serverError.isNullOrBlank()) ": $serverError" else ""
        }
    }
    return "Request failed for an unknown reason."
}

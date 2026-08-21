package com.qweet.rider.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qweet.rider.data.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(retryTick) {
        loading = true
        val result = runCatching { ApiClient.service.privacyPolicy() }
        val body = result.getOrNull()?.body()
        if (body?.success == true && body.data != null) {
            errorText = null
            content = body.data.content
        } else {
            errorText = body?.error ?: describeFailure(result)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                errorText != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    ErrorBanner(message = errorText!!, onRetry = { retryTick++ })
                }
                else -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val styled = """
                                <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>body{font-family:sans-serif;color:#191C1E;padding:16px;line-height:1.5;}
                                h2,h3{color:#A83300;}</style></head><body>$content</body></html>
                            """.trimIndent()
                            loadDataWithBaseURL(null, styled, "text/html", "utf-8", null)
                        }
                    },
                    update = { webView ->
                        val styled = """
                            <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>body{font-family:sans-serif;color:#191C1E;padding:16px;line-height:1.5;}
                            h2,h3{color:#A83300;}</style></head><body>$content</body></html>
                        """.trimIndent()
                        webView.loadDataWithBaseURL(null, styled, "text/html", "utf-8", null)
                    }
                )
            }
        }
    }
}

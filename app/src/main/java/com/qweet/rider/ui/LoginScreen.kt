package com.qweet.rider.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qweet.rider.BuildConfig
import com.qweet.rider.data.ApiClient
import com.qweet.rider.data.ChallengeSolver
import com.qweet.rider.data.LoginRequest
import com.qweet.rider.data.TokenStore
import com.qweet.rider.push.registerDeviceToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

@Composable
fun LoginScreen(tokenStore: TokenStore, onLoginSuccess: () -> Unit) {
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Hidden WebView used only to solve InfinityFree's JS anti-bot challenge (see
    // ChallengeSolver) before the first API call. Zero-size so it never renders anything.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = Modifier.size(0.dp),
        factory = { ctx -> WebView(ctx).also { webViewRef = it } }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Qweet Rider",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sign in to start accepting deliveries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = identity,
                onValueChange = { identity = it },
                label = { Text("Email, phone, or username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            errorText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    errorText = null
                    loading = true
                    scope.launch {
                        try {
                            val wv = webViewRef
                            if (wv != null) {
                                // Best-effort: solve InfinityFree's JS challenge first so the
                                // login request doesn't hit the anti-bot doorway page. If this
                                // host doesn't need it, hasCookie() short-circuits instantly.
                                ChallengeSolver.ensureSolved(wv, ChallengeSolver.baseHostFor(BuildConfig.API_BASE_URL))
                            }
                            val response = ApiClient.service.login(
                                LoginRequest(identity = identity.trim(), password = password, device_name = android.os.Build.MODEL)
                            )
                            val body = response.body()
                            if (response.isSuccessful && body?.success == true && body.token != null) {
                                tokenStore.saveToken(body.token)
                                // Best-effort: hand this device's FCM token to the server so it
                                // can push new-order/withdrawal/admin alerts to it. A failure
                                // here (e.g. Firebase not reachable yet) never blocks login.
                                runCatching {
                                    val fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                                    registerDeviceToken(fcmToken)
                                }
                                onLoginSuccess()
                            } else {
                                errorText = body?.errors?.joinToString() ?: body?.error ?: "Login failed. Please try again."
                            }
                        } catch (e: UnknownHostException) {
                            // DNS lookup failed — wrong domain in API_BASE_URL, or no internet.
                            errorText = "Couldn't resolve the server address (${BuildConfig.API_BASE_URL}). Check the domain is correct and that the phone has internet."
                        } catch (e: SocketTimeoutException) {
                            // Reached the network but server took too long — often a hosting/CDN issue.
                            errorText = "Server took too long to respond (timeout). The site may be slow or blocking the app's request."
                        } catch (e: SSLHandshakeException) {
                            errorText = "Secure connection (SSL/HTTPS) failed. The site's certificate may be invalid or expired."
                        } catch (e: SSLException) {
                            errorText = "Secure connection (SSL) error: ${e.message ?: "unknown SSL failure"}."
                        } catch (e: ConnectException) {
                            // Could not even open a socket to the host — server down, wrong port, or firewall.
                            errorText = "Couldn't connect to the server. It may be down or blocking this connection."
                        } catch (e: com.google.gson.JsonSyntaxException) {
                            // Reached the server, but the body wasn't valid JSON — usually the
                            // hosting provider's anti-bot HTML page instead of the real API reply.
                            val diag = diagnosePostRaw(identity.trim(), password)
                            errorText = "Server didn't return valid data (${e.javaClass.simpleName}).\n\nDiagnostic — $diag"
                        } catch (e: java.io.IOException) {
                            val diag = diagnosePostRaw(identity.trim(), password)
                            errorText = "Network error (${e.javaClass.simpleName}: ${e.message ?: "no details"}).\n\nDiagnostic — $diag"
                        } catch (e: Exception) {
                            errorText = "Unexpected error: ${e.javaClass.simpleName} — ${e.message ?: "no details"}."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && identity.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Log In")
                }
            }
        }
    }
}

// Bypasses Retrofit/Gson entirely and makes a plain OkHttp POST, so we can see the *exact*
// HTTP status code, content-type, and raw body the server sent back — even if that body is
// empty or not JSON at all. This is what actually reveals whether the hosting provider (e.g.
// InfinityFree) is silently blocking/challenging the app's POST instead of a real network fault.
private suspend fun diagnosePostRaw(identity: String, password: String): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val client = ApiClient.rawHttpClient
            val json = "{\"identity\":\"${identity.replace("\"", "\\\"")}\"," +
                "\"password\":\"${password.replace("\"", "\\\"")}\"}"
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(BuildConfig.API_BASE_URL + "login.php")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val trimmed = text.trim().lowercase()
                val looksLikeHtml = trimmed.startsWith("<") || trimmed.contains("<html") || trimmed.contains("<!doctype")
                val bodyPreview = when {
                    text.isBlank() -> "(empty body — 0 bytes)"
                    looksLikeHtml -> "HTML page, not JSON — first 200 chars: ${text.take(200)}"
                    else -> text.take(300)
                }
                "HTTP ${resp.code} ${resp.message} | Content-Type: ${resp.header("Content-Type") ?: "(none)"} | Body: $bodyPreview"
            }
        } catch (e: Exception) {
            "Diagnostic request itself failed: ${e.javaClass.simpleName} — ${e.message ?: "no details"}"
        }
    }

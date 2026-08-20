package com.qweet.rider.data

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

// Some free hosts (InfinityFree/iFastNet) put a JavaScript "anti-bot" doorway page in front of
// every request that doesn't look like it came from a real browser. It runs a small script,
// then sets a cookie and reloads the real page. A plain HTTP client (Retrofit/OkHttp) can't run
// that JS, so it gets stuck seeing the challenge page forever.
//
// The fix: load the site once in a hidden WebView (which *can* run JS), let the challenge solve
// itself exactly like it would in a normal browser, then read the cookie it sets out of
// Android's CookieManager. WebViewCookieJar then attaches that same cookie to every
// Retrofit/OkHttp request, so the "real" server thinks every future request is from that browser.
object ChallengeSolver {

    @Volatile private var solvedForBaseUrl: String? = null

    /** Shared with LoginScreen/AppEntry so both compute the same "scheme://host/" string. */
    fun baseHostFor(apiBaseUrl: String): String =
        runCatching {
            val u = java.net.URL(apiBaseUrl)
            "${u.protocol}://${u.host}/"
        }.getOrDefault(apiBaseUrl)

    // Returns true if we now have a cookie for this host (challenge likely solved or wasn't
    // needed in the first place). Safe to call every time before login — it short-circuits
    // instantly if we already solved it earlier in this app session.
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun ensureSolved(webView: WebView, baseUrl: String): Boolean {
        if (solvedForBaseUrl == baseUrl && hasCookie(baseUrl)) return true

        // Already have a cookie for this host from a previous run (CookieManager persists to
        // disk) — no need to reload the WebView at all.
        if (hasCookie(baseUrl)) {
            solvedForBaseUrl = baseUrl
            return true
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        val deferred = CompletableDeferred<Unit>()
        var finishedCount = 0

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                finishedCount++
                // The challenge page typically: loads -> runs JS -> sets cookie -> reloads
                // itself -> loads again as the real page. Waiting for a second finish (or a
                // cookie appearing) covers that redirect without guessing exact timings.
                if (hasCookie(baseUrl) || finishedCount >= 2) {
                    if (!deferred.isCompleted) deferred.complete(Unit)
                }
            }
        }

        webView.loadUrl(baseUrl)

        // Give the challenge up to 20 seconds to solve itself (generous — these scripts
        // normally finish in 1-4 seconds on a real device).
        withTimeoutOrNull(20_000) {
            deferred.await()
            // Small extra grace period in case the cookie is set just after onPageFinished fires.
            if (!hasCookie(baseUrl)) delay(1500)
        }

        val solved = hasCookie(baseUrl)
        if (solved) solvedForBaseUrl = baseUrl
        return solved
    }

    private fun hasCookie(baseUrl: String): Boolean {
        val cookie = CookieManager.getInstance().getCookie(baseUrl)
        return !cookie.isNullOrBlank()
    }
}

package com.qweet.rider.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

// Makes OkHttp share cookies with Android's WebView cookie store. This is what lets the
// cookie that ChallengeSolver obtains (by solving the hosting provider's JS anti-bot page in a
// hidden WebView) get attached to every normal Retrofit API call automatically.
class WebViewCookieJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        for (cookie in cookies) {
            manager.setCookie(url.toString(), cookie.toString())
        }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(";")
            .mapNotNull { part -> Cookie.parse(url, part.trim()) }
    }
}

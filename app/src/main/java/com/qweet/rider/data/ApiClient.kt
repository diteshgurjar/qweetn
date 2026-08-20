package com.qweet.rider.data

import com.qweet.rider.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var tokenStore: TokenStore? = null

    fun init(store: TokenStore) {
        tokenStore = store
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = tokenStore?.getToken()
        val requestBuilder = original.newBuilder()
            // Some free hosts (InfinityFree/iFastNet) serve a JS "anti-bot" challenge page
            // instead of the real API response to requests that don't look like a browser.
            // A normal browser User-Agent sometimes avoids that challenge.
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val service: RiderApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RiderApiService::class.java)
    }

    // Exposed for diagnostic (non-Retrofit) requests — lets LoginScreen make a plain POST and
    // inspect the raw HTTP status/body when the normal Retrofit+Gson call fails, since Gson
    // parsing failures hide the actual response the server sent (e.g. an HTML challenge page).
    val rawHttpClient: OkHttpClient get() = okHttpClient
}

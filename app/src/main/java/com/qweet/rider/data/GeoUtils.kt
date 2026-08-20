package com.qweet.rider.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A real road route between two points, as returned by OSRM. */
data class RiderRoute(
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** lat, lng pairs describing the road path, for drawing on the map. */
    val geometry: List<Pair<Double, Double>>
)

object GeoUtils {

    // Plain client, no auth/User-Agent overrides from ApiClient — OSRM's public demo
    // server doesn't need them and shouldn't see the rider's Qweet bearer token.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Straight-line distance in meters — used for the "arrived within 200m" geofence check. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Real road route + ETA from OSRM's public routing server (no API key needed) — used
     * for the "6 mins away" style distance/ETA and to draw an actual road path on the map,
     * matching the OSRM approach already used for the school bus tracker.
     */
    suspend fun fetchRoute(originLat: Double, originLng: Double, destLat: Double, destLng: Double): RiderRoute? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "$originLng,$originLat;$destLng,$destLat?overview=full&geometries=geojson"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    if (json.optString("code") != "Ok") return@use null
                    val routes = json.optJSONArray("routes") ?: return@use null
                    if (routes.length() == 0) return@use null
                    val route = routes.getJSONObject(0)
                    val coordsArr = route.getJSONObject("geometry").getJSONArray("coordinates")
                    val geometry = (0 until coordsArr.length()).map { i ->
                        val pt = coordsArr.getJSONArray(i)
                        pt.getDouble(1) to pt.getDouble(0) // OSRM gives [lng, lat] -> flip to lat, lng
                    }
                    RiderRoute(
                        distanceMeters = route.getDouble("distance"),
                        durationSeconds = route.getDouble("duration"),
                        geometry = geometry
                    )
                }
            }.getOrNull()
        }
}

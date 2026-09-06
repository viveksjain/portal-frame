package com.portalhacks.frame

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Tiny dependency-free current-weather lookup for the clock overlay. No API key:
 * the city (set in Settings → Clock & night) is resolved via Open-Meteo geocoding
 * and the reading comes from Open-Meteo. Both endpoints are HTTPS, so no
 * cleartext-traffic manifest change is needed.
 * Best-effort — returns null on any failure (including no city set) and the clock
 * simply shows no weather.
 */
internal object Weather {

    private const val TAG = "PortalFrame"

    /** A current reading: a weather emoji plus a rounded temperature in degrees. */
    class Now(
        @JvmField val emoji: String,
        @JvmField val temp: Int,
        @JvmField val moon: Boolean, // clear/mainly-clear at night → draw a blue crescent
    ) {
        /** e.g. "☀️ 22°" */
        fun label(): String = "$emoji $temp°"
    }

    @JvmStatic
    fun fetch(city: String, fahrenheit: Boolean): Now? {
        if (city.isBlank()) {
            return null // no city set → no weather
        }
        return try {
            val coords = geocode(city) ?: return null
            val url = "https://api.open-meteo.com/v1/forecast?latitude=" + coords.first +
                "&longitude=" + coords.second +
                "&current=temperature_2m,weather_code,is_day" +
                "&temperature_unit=" + (if (fahrenheit) "fahrenheit" else "celsius")
            val cur = JSONObject(httpGet(url)).getJSONObject("current")
            val t = cur.getDouble("temperature_2m")
            val code = cur.optInt("weather_code", 0)
            val day = cur.optInt("is_day", 1) == 1
            val moon = !day && (code == 0 || code == 1) // clear / mainly-clear night
            Now(emojiFor(code, day), t.roundToInt(), moon)
        } catch (e: Exception) {
            Log.w(TAG, "weather fetch failed", e)
            null
        }
    }

    /**
     * Resolve a city to (latitude, longitude) via Open-Meteo's free geocoding API
     * (HTTPS, no key — same provider as the forecast). Returns null when the city
     * can't be resolved.
     */
    private fun geocode(city: String): Pair<String, String>? {
        return try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                java.net.URLEncoder.encode(city.trim(), "UTF-8") +
                "&count=1&language=en&format=json"
            val first = JSONObject(httpGet(url)).optJSONArray("results")?.optJSONObject(0)
                ?: return null
            Pair(first.getDouble("latitude").toString(), first.getDouble("longitude").toString())
        } catch (e: Exception) {
            Log.w(TAG, "weather geocode failed for '$city'", e)
            null
        }
    }

    /** Map a WMO weather code (Open-Meteo) to a single emoji, day/night aware. */
    private fun emojiFor(c: Int, day: Boolean): String {
        if (c == 0) return if (day) "☀️" else "🌙"        // clear sky → moon at night
        if (c == 1) return if (day) "🌤️" else "🌙"       // mainly clear
        if (c == 2) return if (day) "⛅" else "☁️"          // partly cloudy
        if (c == 3) return "☁️"                    // overcast
        if (c == 45 || c == 48) return "🌫️"  // fog
        if (c in 51..57) return "🌦️"  // drizzle
        if (c in 61..67) return "🌧️"  // rain
        if (c in 71..77) return "❄️"        // snow
        if (c in 80..82) return "🌦️"  // rain showers
        if (c == 85 || c == 86) return "🌨️"  // snow showers
        if (c >= 95) return "⛈️"                   // thunderstorm
        return "🌡️"                          // fallback: thermometer
    }

    @Throws(Exception::class)
    private fun httpGet(urlStr: String): String {
        var c: HttpURLConnection? = null
        try {
            c = URL(urlStr).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 10000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", ImageLoader.UA)
            val input = BufferedInputStream(c.inputStream)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                bos.write(buf, 0, n)
            }
            input.close()
            return bos.toString("UTF-8")
        } finally {
            c?.disconnect()
        }
    }
}

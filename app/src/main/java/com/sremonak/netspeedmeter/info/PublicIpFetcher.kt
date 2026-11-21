package com.sremonak.netspeedmeter.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PublicInfo(
    val ip: String,
    val isp: String? = null,
    val asn: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val timezone: String? = null,
    val location: String? = null,
    val edge: String? = null,
    val source: String,
)

/** Looks up public IP / ISP information (Cloudflare first, ipinfo.io as fallback). */
object PublicIpFetcher {

    suspend fun fetch(): PublicInfo = withContext(Dispatchers.IO) {
        runCatching { cloudflare() }.getOrNull() ?: ipinfo()
    }

    fun cloudflare(): PublicInfo {
        val j = JSONObject(httpGet("https://speed.cloudflare.com/meta"))
        val lat = j.optString("latitude").ifBlank { null }
        val lon = j.optString("longitude").ifBlank { null }
        return PublicInfo(
            ip = j.getString("clientIp"),
            isp = j.optString("asOrganization").ifBlank { null },
            asn = j.opt("asn")?.toString()?.let { "AS$it" },
            city = j.optString("city").ifBlank { null },
            region = j.optString("region").ifBlank { null },
            country = j.optString("country").ifBlank { null },
            timezone = j.optString("timezone").ifBlank { null },
            location = if (lat != null && lon != null) "$lat, $lon" else null,
            edge = j.optString("colo").ifBlank { null },
            source = "Cloudflare",
        )
    }

    private fun ipinfo(): PublicInfo {
        val j = JSONObject(httpGet("https://ipinfo.io/json"))
        val org = j.optString("org")
        return PublicInfo(
            ip = j.getString("ip"),
            isp = org.substringAfter(' ', org).ifBlank { null },
            asn = org.substringBefore(' ').takeIf { it.startsWith("AS") },
            city = j.optString("city").ifBlank { null },
            region = j.optString("region").ifBlank { null },
            country = j.optString("country").ifBlank { null },
            timezone = j.optString("timezone").ifBlank { null },
            location = j.optString("loc").ifBlank { null },
            source = "ipinfo.io",
        )
    }

    fun httpGet(url: String, timeoutMs: Int = 8000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.useCaches = false
            c.setRequestProperty("User-Agent", "NetSpeedMeter/1.0 (Android)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

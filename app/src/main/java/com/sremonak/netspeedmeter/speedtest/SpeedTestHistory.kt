package com.sremonak.netspeedmeter.speedtest

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SpeedTestResult(
    val time: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val network: String,
    val server: String,
)

/** Keeps the last results of the built-in speed test. */
class SpeedTestHistory(context: Context) {
    private val prefs = context.getSharedPreferences("speedtest", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SpeedTestResult>> = _items.asStateFlow()

    @Synchronized
    fun add(r: SpeedTestResult) {
        val next = (listOf(r) + _items.value).take(MAX)
        _items.value = next
        save(next)
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save(emptyList())
    }

    private fun load(): List<SpeedTestResult> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SpeedTestResult(
                time = o.getLong("time"),
                downloadMbps = o.getDouble("down"),
                uploadMbps = o.getDouble("up"),
                pingMs = o.getDouble("ping"),
                jitterMs = o.optDouble("jitter", 0.0),
                network = o.optString("network"),
                server = o.optString("server"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<SpeedTestResult>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("time", it.time)
                    .put("down", it.downloadMbps)
                    .put("up", it.uploadMbps)
                    .put("ping", it.pingMs)
                    .put("jitter", it.jitterMs)
                    .put("network", it.network)
                    .put("server", it.server)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    companion object {
        private const val MAX = 20
    }
}

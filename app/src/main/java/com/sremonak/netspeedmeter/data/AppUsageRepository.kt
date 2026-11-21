package com.sremonak.netspeedmeter.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AppUsage(
    val uid: Int,
    val label: String,
    val packageName: String?,
    val mobile: Long,
    val wifi: Long,
    val icon: Bitmap?,
) {
    val total: Long get() = mobile + wifi
}

/** Per-app traffic (needs Usage access), split into mobile and Wi-Fi. */
class AppUsageRepository(private val context: Context, private val system: SystemStatsSource) {

    private val nsm: NetworkStatsManager? = context.getSystemService(NetworkStatsManager::class.java)
    private val pm: PackageManager = context.packageManager
    private val iconCache = HashMap<String, Bitmap?>()

    fun available(): Boolean = system.hasUsageAccess()

    @Suppress("DEPRECATION")
    suspend fun top(from: LocalDate, to: LocalDate, limit: Int = 15): List<AppUsage>? = withContext(Dispatchers.IO) {
        val manager = nsm ?: return@withContext null
        if (!system.hasUsageAccess()) return@withContext null
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis() + 60_000)

        val totals = HashMap<Int, LongArray>() // [mobile, wifi]
        fun collect(type: Int, subscriber: String?, index: Int) {
            val stats: NetworkStats? = try {
                manager.querySummary(type, subscriber, start, end)
            } catch (e: Exception) {
                null
            }
            if (stats == null) return
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val arr = totals.getOrPut(bucket.uid) { LongArray(2) }
                    arr[index] += bucket.rxBytes + bucket.txBytes
                }
            } finally {
                stats.close()
            }
        }
        collect(ConnectivityManager.TYPE_MOBILE, system.subscriberId(), 0)
        collect(ConnectivityManager.TYPE_WIFI, null, 1)

        totals.entries
            .filter { it.value[0] + it.value[1] > 0 }
            .sortedByDescending { it.value[0] + it.value[1] }
            .take(limit)
            .map { (uid, arr) ->
                val (label, pkg) = resolve(uid)
                AppUsage(uid, label, pkg, arr[0], arr[1], pkg?.let { icon(it) })
            }
    }

    private fun resolve(uid: Int): Pair<String, String?> {
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> return "Removed apps" to null
            NetworkStats.Bucket.UID_TETHERING -> return "Hotspot & tethering" to null
            0 -> return "Android OS (kernel)" to null
            1000 -> return "Android system" to "android"
        }
        val packages = runCatching { pm.getPackagesForUid(uid) }.getOrNull()
        if (packages.isNullOrEmpty()) return "App (uid $uid)" to null
        val pkg = packages[0]
        @Suppress("DEPRECATION")
        val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        val count = packages.size
        val suffix = if (count > 1) " +${count - 1}" else ""
        return (label + suffix) to pkg
    }

    private fun icon(pkg: String): Bitmap? = iconCache.getOrPut(pkg) {
        runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
    }
}

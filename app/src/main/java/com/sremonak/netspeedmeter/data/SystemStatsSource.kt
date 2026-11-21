package com.sremonak.netspeedmeter.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.telephony.TelephonyManager
import java.time.LocalDate

/**
 * Reads device-wide traffic from Android's NetworkStatsManager.
 * Needs the special "Usage access" permission, but gives exact numbers –
 * including traffic that happened while the app was not running.
 */
class SystemStatsSource(private val context: Context) {

    private val nsm: NetworkStatsManager? = context.getSystemService(NetworkStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Usage between [start] and [end] (epoch millis), attributed to [date]. Null when unavailable. */
    @Suppress("DEPRECATION")
    fun query(date: LocalDate, start: Long, end: Long): DailyUsage? {
        val manager = nsm ?: return null
        if (end <= start || !hasUsageAccess()) return null
        // Query each network type independently: on some Android 8/9 builds the mobile
        // query fails without a subscriber id, which must not hide the Wi-Fi numbers.
        val wifi = bucket(manager, ConnectivityManager.TYPE_WIFI, null, start, end)
        val mobile = bucket(manager, ConnectivityManager.TYPE_MOBILE, subscriberId(), start, end)
        if (wifi == null && mobile == null) return null
        return DailyUsage(
            date = date,
            mobileRx = mobile?.first ?: 0,
            mobileTx = mobile?.second ?: 0,
            wifiRx = wifi?.first ?: 0,
            wifiTx = wifi?.second ?: 0,
        )
    }

    private fun bucket(manager: NetworkStatsManager, type: Int, subscriber: String?, start: Long, end: Long): Pair<Long, Long>? =
        try {
            val b = manager.querySummaryForDevice(type, subscriber, start, end)
            if (b == null) null else b.rxBytes to b.txBytes
        } catch (e: Exception) {
            null
        }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun subscriberId(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            @Suppress("DEPRECATION")
            context.getSystemService(TelephonyManager::class.java)?.subscriberId
        } catch (e: Exception) {
            null
        }
    }
}

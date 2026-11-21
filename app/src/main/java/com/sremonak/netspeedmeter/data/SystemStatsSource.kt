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
        return try {
            val wifi = manager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, start, end)
            val mobile = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, subscriberId(), start, end)
            DailyUsage(
                date = date,
                mobileRx = mobile?.rxBytes ?: 0,
                mobileTx = mobile?.txBytes ?: 0,
                wifiRx = wifi?.rxBytes ?: 0,
                wifiTx = wifi?.txBytes ?: 0,
            )
        } catch (e: Exception) {
            null
        }
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

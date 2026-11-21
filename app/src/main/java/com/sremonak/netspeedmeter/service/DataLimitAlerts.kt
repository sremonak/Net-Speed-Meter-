package com.sremonak.netspeedmeter.service

import android.content.Context
import com.sremonak.netspeedmeter.core.Format

/** Fires one alert at 80 % and one at 100 % of a limit, once per period. */
class DataLimitAlerts(context: Context, private val renderer: NotificationRenderer) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    fun check(scope: String, period: String, used: Long, limitBytes: Long, label: String) {
        if (limitBytes <= 0) return
        if (prefs.getString("${scope}_period", null) != period) {
            prefs.edit().putString("${scope}_period", period).putInt("${scope}_level", 0).apply()
        }
        val pct = used * 100 / limitBytes
        val level = when {
            pct >= 100 -> 100
            pct >= 80 -> 80
            else -> 0
        }
        if (level > prefs.getInt("${scope}_level", 0)) {
            prefs.edit().putInt("${scope}_level", level).apply()
            val id = if (scope == "daily") 2001 else 2002
            if (level >= 100) {
                renderer.postAlert(id, "$label mobile data limit reached", "You have used ${Format.bytes(used)} of your ${Format.bytes(limitBytes)} ${label.lowercase()} limit.")
            } else {
                renderer.postAlert(id, "$pct% of $label data limit used", "${Format.bytes(used)} of ${Format.bytes(limitBytes)} used. Consider switching to Wi-Fi.")
            }
        }
    }
}

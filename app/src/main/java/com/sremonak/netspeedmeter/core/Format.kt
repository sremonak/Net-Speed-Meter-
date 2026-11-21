package com.sremonak.netspeedmeter.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human friendly formatting for speeds, byte counts and dates. */
object Format {

    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

    fun bytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < BYTE_UNITS.lastIndex) {
            value /= 1024
            i++
        }
        val pattern = when {
            value >= 100 -> "%.0f %s"
            value >= 10 -> "%.1f %s"
            else -> "%.2f %s"
        }
        return String.format(Locale.US, pattern, value, BYTE_UNITS[i])
    }

    /** Returns (value, unit), e.g. ("1.25", "MB/s") or ("10.0", "Mbps"). */
    fun speedParts(bytesPerSecond: Long, bits: Boolean): Pair<String, String> {
        val units = if (bits) arrayOf("bps", "Kbps", "Mbps", "Gbps") else arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        val base = if (bits) 1000.0 else 1024.0
        var v = if (bits) bytesPerSecond.coerceAtLeast(0) * 8.0 else bytesPerSecond.coerceAtLeast(0).toDouble()
        var i = 0
        while (v >= base && i < units.lastIndex) {
            v /= base
            i++
        }
        val text = when {
            i == 0 -> v.toLong().toString()
            v >= 100 -> String.format(Locale.US, "%.0f", v)
            v >= 10 -> String.format(Locale.US, "%.1f", v)
            else -> String.format(Locale.US, "%.2f", v)
        }
        return text to units[i]
    }

    fun speed(bytesPerSecond: Long, bits: Boolean): String =
        speedParts(bytesPerSecond, bits).let { "${it.first} ${it.second}" }

    /** Very short form used inside the status-bar icon: max ~3-4 chars + unit. */
    fun compactSpeed(bytesPerSecond: Long, bits: Boolean): Pair<String, String> {
        val base = if (bits) 1000.0 else 1024.0
        val units = if (bits) arrayOf("Kb/s", "Mb/s", "Gb/s") else arrayOf("KB/s", "MB/s", "GB/s")
        var v = (if (bits) bytesPerSecond.coerceAtLeast(0) * 8.0 else bytesPerSecond.coerceAtLeast(0).toDouble()) / base
        var i = 0
        while (v >= 999.5 && i < units.lastIndex) {
            v /= base
            i++
        }
        val text = if (v < 9.95) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%.0f", v)
        return text to units[i]
    }

    fun mbps(value: Double?): String =
        if (value == null) "--" else if (value >= 100) String.format(Locale.US, "%.0f", value)
        else String.format(Locale.US, "%.1f", value)

    fun ms(value: Double?): String = if (value == null) "--" else String.format(Locale.US, "%.0f", value)

    fun percent(part: Long, whole: Long): Int =
        if (whole <= 0) 0 else ((part.toDouble() / whole) * 100).toInt().coerceIn(0, 999)

    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormatter)
    }

    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val d = totalMinutes / (60 * 24)
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return when {
            d > 0 -> "${d}d ${h}h ${m}m"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun kbpsToReadable(kbps: Int): String = when {
        kbps <= 0 -> "Unknown"
        kbps >= 1000 -> String.format(Locale.US, "%.1f Mbps", kbps / 1000.0)
        else -> "$kbps Kbps"
    }
}

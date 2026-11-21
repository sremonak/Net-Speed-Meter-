package com.sremonak.netspeedmeter.data

import android.os.SystemClock
import com.sremonak.netspeedmeter.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single source of truth for data usage.
 *
 * - The meter service feeds every traffic delta into [onTraffic]; the app keeps its own
 *   per-day accounting in SQLite (works without any special permission).
 * - When "Usage access" is granted, Android's NetworkStatsManager is used as the base
 *   (exact, device-wide, survives app kills) and live deltas are added on top so the
 *   notification still updates every second.
 */
class UsageRepository(
    private val db: UsageDatabase,
    private val system: SystemStatsSource,
    private val settings: SettingsRepository,
) {
    private val lock = Any()

    private val _today = MutableStateFlow(DailyUsage(LocalDate.now()))
    val today: StateFlow<DailyUsage> = _today.asStateFlow()

    private val _source = MutableStateFlow(UsageSource.APP)
    val source: StateFlow<UsageSource> = _source.asStateFlow()

    private var currentDay: LocalDate = LocalDate.now()
    private var flushedToday: DailyUsage? = null
    private var pending = DailyUsage(currentDay)

    private var systemBase: DailyUsage? = null
    private var sinceSystemBase = DailyUsage(currentDay)
    private var lastSystemRefresh = 0L
    private var lastFlush = SystemClock.elapsedRealtime()
    private var systemAvailable = false
    private var lastPermissionCheck = 0L

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** True when exact device-wide statistics will be used. */
    fun useSystemStats(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPermissionCheck > 30_000 || lastPermissionCheck == 0L) {
            systemAvailable = system.hasUsageAccess()
            lastPermissionCheck = now
        }
        return settings.current.preferSystemStats && systemAvailable
    }

    fun invalidatePermissionCache() {
        lastPermissionCheck = 0L
        lastSystemRefresh = 0L
    }

    /** Called by the service for every sample (bytes since previous sample). */
    fun onTraffic(mobileRx: Long, mobileTx: Long, wifiRx: Long, wifiTx: Long) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val day = LocalDate.now()
            if (day != currentDay) rollOver(day)
            val delta = DailyUsage(day, mobileRx, mobileTx, wifiRx, wifiTx)
            pending += delta
            sinceSystemBase += delta
            if (now - lastFlush > FLUSH_INTERVAL_MS) flushLocked(now)
        }
        val useSystem = useSystemStats()
        if (useSystem && (now - lastSystemRefresh > SYSTEM_REFRESH_MS || systemBase == null)) {
            refreshSystemBase(now)
        }
        publish(useSystem)
    }

    /** Re-publish without new traffic (e.g. at startup or after settings changes). */
    fun refreshNow() {
        lastSystemRefresh = 0L
        onTraffic(0, 0, 0, 0)
    }

    fun flush() = synchronized(lock) { flushLocked(SystemClock.elapsedRealtime()) }

    private fun flushLocked(now: Long) {
        if (!pending.isEmpty) {
            db.add(pending)
            val flushed = flushedToday
            flushedToday = if (flushed == null) db.get(currentDay) else flushed + pending
            pending = DailyUsage(currentDay)
        }
        lastFlush = now
    }

    private fun rollOver(day: LocalDate) {
        flushLocked(SystemClock.elapsedRealtime())
        currentDay = day
        pending = DailyUsage(day)
        flushedToday = DailyUsage(day)
        systemBase = null
        sinceSystemBase = DailyUsage(day)
        lastSystemRefresh = 0L
        _today.value = DailyUsage(day)
    }

    private fun refreshSystemBase(now: Long) {
        val day = currentDay
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val result = system.query(day, start, System.currentTimeMillis() + 60_000)
        synchronized(lock) {
            lastSystemRefresh = now
            if (result != null && day == currentDay) {
                systemBase = result
                sinceSystemBase = DailyUsage(day)
            }
        }
    }

    private fun publish(useSystem: Boolean) {
        val value = synchronized(lock) {
            val base = systemBase
            if (useSystem && base != null) {
                _source.value = UsageSource.SYSTEM
                base + sinceSystemBase
            } else {
                _source.value = UsageSource.APP
                val flushed = flushedToday ?: db.get(currentDay).also { flushedToday = it }
                flushed + pending
            }
        }
        val previous = _today.value
        // Never let today's counters go backwards because of stats lag.
        _today.value = if (previous.date == value.date && _source.value == lastPublishedSource) value.atLeast(previous) else value
        lastPublishedSource = _source.value
    }

    private var lastPublishedSource: UsageSource? = null

    /** Per-day usage for an inclusive range, oldest first. Today is live. */
    suspend fun history(from: LocalDate, to: LocalDate): List<DailyUsage> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val result = if (useSystemStats()) {
            days.map { day ->
                if (day == today) _today.value
                else {
                    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    system.query(day, start, end) ?: DailyUsage(day)
                }
            }
        } else {
            flush()
            val stored = db.range(from, to).associateBy { it.date }
            days.map { day -> if (day == today) _today.value else stored[day] ?: DailyUsage(day) }
        }
        result
    }

    /** Total usage between two days (inclusive) – single query when system stats are available. */
    suspend fun total(from: LocalDate, to: LocalDate): DailyUsage = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        if (useSystemStats()) {
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val endDay = if (to.isBefore(today)) to else today.minusDays(1)
            val past = if (!endDay.isBefore(from)) {
                system.query(to, start, endDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            } else null
            val base = past ?: DailyUsage(to)
            if (!to.isBefore(today)) base + _today.value.copy(date = to) else base
        } else {
            history(from, to).fold(DailyUsage(to)) { acc, d -> acc + d.copy(date = to) }
        }
    }

    fun clearLocal() = synchronized(lock) {
        db.clear()
        pending = DailyUsage(currentDay)
        flushedToday = DailyUsage(currentDay)
        sinceSystemBase = DailyUsage(currentDay)
        lastPublishedSource = null
        _today.value = DailyUsage(currentDay)
        lastSystemRefresh = 0L
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 15_000L
        private const val SYSTEM_REFRESH_MS = 60_000L

        /** Start of the current billing cycle for a given cycle day (1..28/31). */
        fun cycleStart(cycleDay: Int, today: LocalDate = LocalDate.now()): LocalDate {
            val thisMonth = today.withDayOfMonth(cycleDay.coerceIn(1, today.lengthOfMonth()))
            return if (!today.isBefore(thisMonth)) thisMonth else {
                val prev = today.minusMonths(1)
                prev.withDayOfMonth(cycleDay.coerceIn(1, prev.lengthOfMonth()))
            }
        }
    }
}

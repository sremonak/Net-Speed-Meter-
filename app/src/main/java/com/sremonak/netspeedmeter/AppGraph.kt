package com.sremonak.netspeedmeter

import android.content.Context
import com.sremonak.netspeedmeter.core.SettingsRepository
import com.sremonak.netspeedmeter.data.AppUsageRepository
import com.sremonak.netspeedmeter.data.SystemStatsSource
import com.sremonak.netspeedmeter.data.UsageDatabase
import com.sremonak.netspeedmeter.data.UsageRepository
import com.sremonak.netspeedmeter.monitor.LiveTrafficStore
import com.sremonak.netspeedmeter.monitor.NetworkStateTracker
import com.sremonak.netspeedmeter.speedtest.SpeedTestHistory

/** Minimal manual dependency container (no DI framework needed for an app this size). */
object AppGraph {
    lateinit var settings: SettingsRepository
        private set
    lateinit var usage: UsageRepository
        private set
    lateinit var system: SystemStatsSource
        private set
    lateinit var appUsage: AppUsageRepository
        private set
    lateinit var network: NetworkStateTracker
        private set
    lateinit var speedTestHistory: SpeedTestHistory
        private set
    val live = LiveTrafficStore()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            settings = SettingsRepository(app)
            system = SystemStatsSource(app)
            usage = UsageRepository(UsageDatabase(app), system, settings)
            appUsage = AppUsageRepository(app, system)
            network = NetworkStateTracker(app).also { it.start() }
            speedTestHistory = SpeedTestHistory(app)
            initialized = true
        }
    }
}

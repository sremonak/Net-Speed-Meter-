package com.sremonak.netspeedmeter.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.data.UsageRepository
import com.sremonak.netspeedmeter.monitor.TrafficSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Always-on foreground service: samples traffic every second, updates the persistent
 * notification (speed in the status bar + today's mobile/Wi-Fi usage) and records usage.
 */
class SpeedMeterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var renderer: NotificationRenderer
    private lateinit var alerts: DataLimitAlerts
    private val sampler = TrafficSampler()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loopJob: Job? = null
    private var lastNotify = 0L
    private var lastCycleCheck = 0L
    private var cycleMobile = 0L
    private var todayMobileAtCycleCheck = 0L
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wake.trySend(Unit)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationRenderer.createChannels(this)
        renderer = NotificationRenderer(this)
        alerts = DataLimitAlerts(this, renderer)
        promoteToForeground()
        _running.value = true

        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        scope.launch {
            AppGraph.settings.state.collect { s ->
                if (!s.meterEnabled) {
                    stopSelf()
                } else {
                    wake.trySend(Unit)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NotificationRenderer.NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start foreground", e)
        }
    }

    private suspend fun runLoop() {
        sampler.reset()
        sampler.sample()
        AppGraph.usage.refreshNow()
        postNotification()
        while (currentCoroutineContext().isActive) {
            val settings = AppGraph.settings.current
            val interactive = powerManager?.isInteractive != false
            val interval = if (interactive) settings.updateIntervalMs else SCREEN_OFF_INTERVAL_MS
            withTimeoutOrNull(interval) { wake.receive() }
            val delta = sampler.sample() ?: continue
            try {
                tick(delta, interactive)
            } catch (e: Exception) {
                Log.e(TAG, "tick failed", e)
            }
        }
    }

    private suspend fun tick(d: TrafficSampler.Delta, interactive: Boolean) {
        val onWifi = AppGraph.network.isOnWifiOrEthernet
        val otherRx = (d.totalRx - d.mobileRx).coerceAtLeast(0)
        val otherTx = (d.totalTx - d.mobileTx).coerceAtLeast(0)
        AppGraph.usage.onTraffic(
            mobileRx = d.mobileRx,
            mobileTx = d.mobileTx,
            wifiRx = if (onWifi) otherRx else 0,
            wifiTx = if (onWifi) otherTx else 0,
        )
        val rxSpeed = d.totalRx * 1000 / d.elapsedMs
        val txSpeed = d.totalTx * 1000 / d.elapsedMs
        AppGraph.live.push(rxSpeed, txSpeed, d.totalRx, d.totalTx)

        val now = SystemClock.elapsedRealtime()
        if (interactive || now - lastNotify > SCREEN_OFF_NOTIFY_MS) {
            postNotification()
            lastNotify = now
        }
        checkLimits(now)
    }

    private suspend fun checkLimits(now: Long) {
        val s = AppGraph.settings.current
        val today = AppGraph.usage.today.value
        if (s.dailyLimitMb > 0) {
            alerts.check("daily", today.date.toString(), today.mobile, s.dailyLimitMb * MB, "Daily")
        }
        if (s.monthlyLimitMb > 0) {
            val start = UsageRepository.cycleStart(s.billingCycleDay, today.date)
            if (lastCycleCheck == 0L || now - lastCycleCheck > CYCLE_CHECK_MS) {
                cycleMobile = AppGraph.usage.total(start, today.date).mobile
                todayMobileAtCycleCheck = today.mobile
                lastCycleCheck = now
            }
            val estimate = cycleMobile + (today.mobile - todayMobileAtCycleCheck).coerceAtLeast(0)
            alerts.check("monthly", start.toString(), estimate, s.monthlyLimitMb * MB, "Monthly")
        }
    }

    private fun buildNotification() = renderer.build(
        AppGraph.live.state.value,
        AppGraph.usage.today.value,
        AppGraph.network.state.value,
        AppGraph.settings.current,
    )

    private fun postNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(NotificationRenderer.NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Some ROMs kill the service with the task; schedule a quick restart as a safety net.
        if (AppGraph.settings.current.meterEnabled) scheduleRestart(this, 1_500)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        runCatching { AppGraph.usage.flush() }
        _running.value = false
        if (AppGraph.settings.current.meterEnabled) scheduleRestart(this, 5_000)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SpeedMeterService"
        private const val MB = 1024L * 1024L
        private const val SCREEN_OFF_INTERVAL_MS = 5_000L
        private const val SCREEN_OFF_NOTIFY_MS = 60_000L
        private const val CYCLE_CHECK_MS = 5 * 60_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, SpeedMeterService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start service: ${e.message}")
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpeedMeterService::class.java))
        }

        private fun scheduleRestart(context: Context, delayMs: Long) {
            runCatching {
                val pi = PendingIntent.getForegroundService(
                    context, 7, Intent(context, SpeedMeterService::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                context.getSystemService(AlarmManager::class.java)
                    ?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
            }
        }
    }
}

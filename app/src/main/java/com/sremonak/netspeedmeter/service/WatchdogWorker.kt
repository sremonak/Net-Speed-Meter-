package com.sremonak.netspeedmeter.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sremonak.netspeedmeter.AppGraph
import java.util.concurrent.TimeUnit

/** Every 15 minutes: make sure the meter is still alive (aggressive OEM task killers). */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        if (AppGraph.settings.current.meterEnabled && !SpeedMeterService.running.value) {
            SpeedMeterService.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "meter_watchdog"

        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
                )
            }
        }
    }
}

package com.sremonak.netspeedmeter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sremonak.netspeedmeter.AppGraph

/** Restarts the meter after reboot and after app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val s = AppGraph.settings.current
        val isUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (s.meterEnabled && (s.startOnBoot || isUpdate)) {
            SpeedMeterService.start(context)
        }
        WatchdogWorker.schedule(context)
    }
}

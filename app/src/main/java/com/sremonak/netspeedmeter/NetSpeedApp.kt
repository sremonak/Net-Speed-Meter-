package com.sremonak.netspeedmeter

import android.app.Application
import com.sremonak.netspeedmeter.service.NotificationRenderer

class NetSpeedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        NotificationRenderer.createChannels(this)
    }
}

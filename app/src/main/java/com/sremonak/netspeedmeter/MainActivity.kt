package com.sremonak.netspeedmeter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sremonak.netspeedmeter.service.NotificationRenderer
import com.sremonak.netspeedmeter.service.SpeedMeterService
import com.sremonak.netspeedmeter.service.WatchdogWorker
import com.sremonak.netspeedmeter.ui.screens.AppTab
import com.sremonak.netspeedmeter.ui.screens.MainScreen
import com.sremonak.netspeedmeter.ui.theme.NetSpeedTheme

class MainActivity : ComponentActivity() {

    private val requestedTab = mutableStateOf<AppTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        handleIntent(intent)

        if (AppGraph.settings.current.meterEnabled) SpeedMeterService.start(this)
        WatchdogWorker.schedule(this)

        setContent {
            val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
            NetSpeedTheme(settings) {
                MainScreen(requestedTab = requestedTab.value, onTabConsumed = { requestedTab.value = null })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the app is a good moment to (re)start a meter killed by the OEM.
        if (AppGraph.settings.current.meterEnabled && !SpeedMeterService.running.value) {
            SpeedMeterService.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        requestedTab.value = when (intent?.getStringExtra(NotificationRenderer.EXTRA_TAB)) {
            NotificationRenderer.TAB_SPEED_TEST -> AppTab.TEST
            NotificationRenderer.TAB_USAGE -> AppTab.USAGE
            else -> requestedTab.value
        }
    }
}

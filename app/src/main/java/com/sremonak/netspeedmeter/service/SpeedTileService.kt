package com.sremonak.netspeedmeter.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.MainActivity
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.monitor.LiveState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile showing live download/upload speed. */
class SpeedTileService : TileService() {
    private val scope = MainScope()
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        AppGraph.init(this)
        if (AppGraph.settings.current.meterEnabled && !SpeedMeterService.running.value) {
            SpeedMeterService.start(this)
        }
        job?.cancel()
        job = scope.launch { AppGraph.live.state.collect { render(it) } }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(s: LiveState) {
        val tile = qsTile ?: return
        val bits = AppGraph.settings.current.useBits
        val (value, unit) = Format.compactSpeed(s.rx + s.tx, bits)
        tile.icon = Icon.createWithBitmap(SpeedIconFactory.create(value, unit))
        tile.label = "▼ ${Format.speed(s.rx, bits)}"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = "▲ ${Format.speed(s.tx, bits)}"
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}

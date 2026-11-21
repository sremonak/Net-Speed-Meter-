package com.sremonak.netspeedmeter.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class SpeedSample(val rx: Long, val tx: Long)

data class LiveState(
    val rx: Long = 0,
    val tx: Long = 0,
    val history: List<SpeedSample> = emptyList(),
    val peakRx: Long = 0,
    val peakTx: Long = 0,
    val sessionRx: Long = 0,
    val sessionTx: Long = 0,
    val sessionStart: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0,
)

/** In-memory real-time speed state shared by the service, the UI and the Quick Settings tile. */
class LiveTrafficStore {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()
    private val buffer = ArrayDeque<SpeedSample>()

    @Synchronized
    fun push(rxSpeed: Long, txSpeed: Long, rxBytes: Long, txBytes: Long) {
        buffer.addLast(SpeedSample(rxSpeed, txSpeed))
        while (buffer.size > HISTORY_SIZE) buffer.removeFirst()
        val s = _state.value
        _state.value = s.copy(
            rx = rxSpeed,
            tx = txSpeed,
            history = buffer.toList(),
            peakRx = max(s.peakRx, rxSpeed),
            peakTx = max(s.peakTx, txSpeed),
            sessionRx = s.sessionRx + rxBytes,
            sessionTx = s.sessionTx + txBytes,
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun resetSession() {
        buffer.clear()
        _state.value = LiveState()
    }

    companion object {
        const val HISTORY_SIZE = 60
    }
}

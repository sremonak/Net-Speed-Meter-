package com.sremonak.netspeedmeter.monitor

import android.net.TrafficStats
import android.os.SystemClock
import kotlin.math.max

/** Samples kernel traffic counters and returns byte deltas between calls. */
class TrafficSampler {

    data class Delta(
        val elapsedMs: Long,
        val totalRx: Long,
        val totalTx: Long,
        val mobileRx: Long,
        val mobileTx: Long,
    )

    private data class Snapshot(
        val time: Long,
        val totalRx: Long,
        val totalTx: Long,
        val mobileRx: Long,
        val mobileTx: Long,
    )

    private var last: Snapshot? = null

    fun reset() {
        last = null
    }

    fun sample(): Delta? {
        val cur = Snapshot(
            time = SystemClock.elapsedRealtime(),
            totalRx = TrafficStats.getTotalRxBytes(),
            totalTx = TrafficStats.getTotalTxBytes(),
            mobileRx = TrafficStats.getMobileRxBytes(),
            mobileTx = TrafficStats.getMobileTxBytes(),
        )
        val prev = last
        last = cur
        if (prev == null) return null
        val elapsed = cur.time - prev.time
        if (elapsed <= 0) return null

        val mRx = diff(cur.mobileRx, prev.mobileRx)
        val mTx = diff(cur.mobileTx, prev.mobileTx)
        val tRx = max(diff(cur.totalRx, prev.totalRx), mRx)
        val tTx = max(diff(cur.totalTx, prev.totalTx), mTx)

        // Ignore absurd spikes caused by counter resets on some ROMs (> 5 GB/s).
        val limit = 5L * 1024 * 1024 * 1024 * elapsed / 1000
        if (tRx > limit || tTx > limit) return Delta(elapsed, 0, 0, 0, 0)
        return Delta(elapsed, tRx, tTx, mRx, mTx)
    }

    private fun diff(current: Long, previous: Long): Long =
        if (current < 0 || previous < 0 || current < previous) 0 else current - previous
}

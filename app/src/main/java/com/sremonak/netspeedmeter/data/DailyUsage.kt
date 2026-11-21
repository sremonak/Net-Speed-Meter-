package com.sremonak.netspeedmeter.data

import java.time.LocalDate
import kotlin.math.max

/** Data usage of a single day, split by mobile and Wi-Fi, download (rx) / upload (tx). */
data class DailyUsage(
    val date: LocalDate,
    val mobileRx: Long = 0,
    val mobileTx: Long = 0,
    val wifiRx: Long = 0,
    val wifiTx: Long = 0,
) {
    val mobile: Long get() = mobileRx + mobileTx
    val wifi: Long get() = wifiRx + wifiTx
    val total: Long get() = mobile + wifi
    val totalRx: Long get() = mobileRx + wifiRx
    val totalTx: Long get() = mobileTx + wifiTx
    val isEmpty: Boolean get() = total == 0L

    operator fun plus(o: DailyUsage) = copy(
        mobileRx = mobileRx + o.mobileRx,
        mobileTx = mobileTx + o.mobileTx,
        wifiRx = wifiRx + o.wifiRx,
        wifiTx = wifiTx + o.wifiTx,
    )

    fun atLeast(o: DailyUsage) = copy(
        mobileRx = max(mobileRx, o.mobileRx),
        mobileTx = max(mobileTx, o.mobileTx),
        wifiRx = max(wifiRx, o.wifiRx),
        wifiTx = max(wifiTx, o.wifiTx),
    )
}

enum class UsageSource(val label: String) {
    SYSTEM("Android system stats"),
    APP("In-app tracking"),
}

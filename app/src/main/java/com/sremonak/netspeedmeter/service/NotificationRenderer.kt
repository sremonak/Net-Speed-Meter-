package com.sremonak.netspeedmeter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.sremonak.netspeedmeter.MainActivity
import com.sremonak.netspeedmeter.R
import com.sremonak.netspeedmeter.core.AppSettings
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.core.IconMode
import com.sremonak.netspeedmeter.data.DailyUsage
import com.sremonak.netspeedmeter.monitor.ConnectionKind
import com.sremonak.netspeedmeter.monitor.LiveState
import com.sremonak.netspeedmeter.monitor.NetState

/** Builds the persistent speed notification and data-limit alerts. */
class NotificationRenderer(private val context: Context) {

    private val downColor = 0xFF0EA5E9.toInt()
    private val upColor = 0xFF8B5CF6.toInt()
    private val mobileColor = 0xFFF59E0B.toInt()
    private val wifiColor = 0xFF10B981.toInt()

    private val openApp = openIntent(null, 10)
    private val openSpeedTest = openIntent(TAB_SPEED_TEST, 11)
    private val openUsage = openIntent(TAB_USAGE, 12)

    fun build(live: LiveState, today: DailyUsage, net: NetState, s: AppSettings): Notification {
        val bits = s.useBits
        val iconSpeed = when (s.iconMode) {
            IconMode.TOTAL -> live.rx + live.tx
            IconMode.DOWNLOAD -> live.rx
            IconMode.UPLOAD -> live.tx
        }
        val (value, unit) = Format.compactSpeed(iconSpeed, bits)
        val icon = IconCompat.createWithBitmap(SpeedIconFactory.create(value, unit))

        val down = Format.speed(live.rx, bits)
        val up = Format.speed(live.tx, bits)
        val mobile = Format.bytes(today.mobile)
        val wifi = Format.bytes(today.wifi)
        val connection = when {
            net.kind == ConnectionKind.NONE -> "Offline"
            net.vpn && net.kind != ConnectionKind.VPN -> "${net.kind.label} + VPN"
            else -> net.kind.label
        } + if (net.kind != ConnectionKind.NONE && !net.validated) " · no internet" else ""

        val compact = RemoteViews(context.packageName, R.layout.notification_compact).apply {
            setTextViewText(R.id.n_down, colored("▼", downColor, down))
            setTextViewText(R.id.n_up, colored("▲", upColor, up))
            setTextViewText(R.id.n_mobile, colored("●", mobileColor, "Mobile  $mobile"))
            setTextViewText(R.id.n_wifi, colored("●", wifiColor, "Wi-Fi  $wifi"))
        }
        val expanded = RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.x_down, down)
            setTextViewText(R.id.x_up, up)
            setTextViewText(R.id.x_mobile, mobile)
            setTextViewText(R.id.x_wifi, wifi)
            setTextViewText(R.id.x_total, Format.bytes(today.total))
            setTextViewText(
                R.id.x_footer,
                "$connection  ·  Peak ▼ ${Format.speed(live.peakRx, bits)}  ▲ ${Format.speed(live.peakTx, bits)}"
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_SPEED)
            .setSmallIcon(icon)
            .setContentTitle("▼ $down   ▲ $up")
            .setContentText("Today · Mobile $mobile · Wi-Fi $wifi")
            .setSubText(connection)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(if (s.showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, "Speed test", openSpeedTest)
            .addAction(0, "Usage details", openUsage)
            .build()
    }

    fun postAlert(id: Int, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openUsage)
            .build()
        runCatching { nm.notify(id, n) }
    }

    private fun colored(prefix: String, color: Int, text: String): CharSequence =
        SpannableString("$prefix $text").apply {
            setSpan(ForegroundColorSpan(color), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun openIntent(tab: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tab != null) putExtra(EXTRA_TAB, tab)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_SPEED = "live_speed"
        const val CHANNEL_ALERTS = "data_alerts"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TAB = "open_tab"
        const val TAB_SPEED_TEST = "speed_test"
        const val TAB_USAGE = "usage"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val speed = NotificationChannel(
                CHANNEL_SPEED, context.getString(R.string.channel_speed), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_speed_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val alerts = NotificationChannel(
                CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
            }
            nm.createNotificationChannels(listOf(speed, alerts))
        }
    }
}

package com.sremonak.netspeedmeter.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.sremonak.netspeedmeter.AppGraph

/** Permission checks and deep links into system settings needed for an always-on meter. */
object Perms {

    fun notifications(ctx: Context): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtime && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun usageAccess(): Boolean = AppGraph.system.hasUsageAccess()

    fun location(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun phone(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("BatteryLife")
    fun requestBatteryUnrestricted(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        if (!start(ctx, direct)) start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openUsageAccess(ctx: Context) {
        val withPkg = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        if (!start(ctx, withPkg)) start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openNotificationSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        if (!start(ctx, i)) openAppDetails(ctx)
    }

    fun openDataSaverException(ctx: Context) {
        val i = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        if (!start(ctx, i)) openAppDetails(ctx)
    }

    fun openAppDetails(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }

    /** Opens the OEM specific "auto-start / background launch" screen when one exists. */
    fun openAutoStart(ctx: Context): Boolean {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
            "com.realme.securitycheck" to "com.realme.securitycheck.ui.SecurityCheckActivity",
        )
        for ((pkg, cls) in candidates) {
            val i = Intent().setComponent(ComponentName(pkg, cls))
            if (start(ctx, i)) return true
        }
        openAppDetails(ctx)
        return false
    }

    fun start(ctx: Context, intent: Intent): Boolean = try {
        if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    val manufacturer: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}

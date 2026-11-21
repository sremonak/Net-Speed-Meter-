package com.sremonak.netspeedmeter.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.BuildConfig
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.core.IconMode
import com.sremonak.netspeedmeter.core.ThemeMode
import com.sremonak.netspeedmeter.service.SpeedMeterService
import com.sremonak.netspeedmeter.ui.components.AppCard
import com.sremonak.netspeedmeter.ui.components.IconBadge
import com.sremonak.netspeedmeter.ui.theme.Brand
import com.sremonak.netspeedmeter.util.Perms

private enum class NumberDialog { DAILY, MONTHLY, CYCLE }

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val s by AppGraph.settings.state.collectAsStateWithLifecycle()
    val running by SpeedMeterService.running.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
        AppGraph.usage.invalidatePermissionCache()
    }
    val batteryOk = remember(tick) { Perms.batteryUnrestricted(context) }
    val notifOk = remember(tick) { Perms.notifications(context) }
    val usageOk = remember(tick) { Perms.usageAccess() }
    var dialog by remember { mutableStateOf<NumberDialog?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Text("Make the meter yours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Group("Speed meter") {
                SwitchRow(Icons.Rounded.PowerSettingsNew, "Speed meter", if (running) "Running in the status bar" else "Stopped", s.meterEnabled) { on ->
                    AppGraph.settings.update { it.copy(meterEnabled = on) }
                    if (on) SpeedMeterService.start(context) else SpeedMeterService.stop(context)
                }
                ChoiceRow(Icons.Rounded.Speed, "Speed unit", listOf("Bytes (MB/s)", "Bits (Mbps)"), if (s.useBits) 1 else 0) { i ->
                    AppGraph.settings.update { it.copy(useBits = i == 1) }
                }
                ChoiceRow(Icons.Rounded.Tune, "Status bar icon shows", IconMode.entries.map { it.label }, s.iconMode.ordinal) { i ->
                    AppGraph.settings.update { it.copy(iconMode = IconMode.entries[i]) }
                }
                val intervals = listOf(1000L, 2000L, 3000L)
                ChoiceRow(Icons.Rounded.Timer, "Update interval", listOf("1 s", "2 s", "3 s"), intervals.indexOf(s.updateIntervalMs).coerceAtLeast(0)) { i ->
                    AppGraph.settings.update { it.copy(updateIntervalMs = intervals[i]) }
                }
                SwitchRow(Icons.Rounded.Lock, "Show on lock screen", "Display speed & usage on the lock screen", s.showOnLockScreen) { on ->
                    AppGraph.settings.update { it.copy(showOnLockScreen = on) }
                }
                ClickRow(Icons.Rounded.Notifications, "Notification settings", if (notifOk) "Allowed" else "Blocked – tap to allow", ok = notifOk) {
                    Perms.openNotificationSettings(context)
                }
            }
        }

        item {
            Group("Always running · no restrictions") {
                SwitchRow(Icons.Rounded.RestartAlt, "Start on boot", "Start automatically after the phone restarts", s.startOnBoot) { on ->
                    AppGraph.settings.update { it.copy(startOnBoot = on) }
                }
                ClickRow(
                    Icons.Rounded.BatteryChargingFull, "Battery optimization",
                    if (batteryOk) "Unrestricted – Android won't stop the meter" else "Restricted – tap to allow running in background",
                    ok = batteryOk
                ) { Perms.requestBatteryUnrestricted(context) }
                ClickRow(Icons.Rounded.Autorenew, "Auto-start (${Perms.manufacturer})", "Allow auto-launch on Xiaomi, Oppo, Vivo, Realme, Huawei, Samsung…") {
                    Perms.openAutoStart(context)
                }
                ClickRow(Icons.Rounded.DataUsage, "Unrestricted data", "Exclude the meter from Data Saver") {
                    Perms.openDataSaverException(context)
                }
                ClickRow(Icons.Rounded.Info, "App info", "Set battery usage to \"Unrestricted\" and lock the app in recents") {
                    Perms.openAppDetails(context)
                }
            }
        }

        item {
            Group("Data usage") {
                ClickRow(
                    Icons.Rounded.CheckCircle, "Usage access (exact stats)",
                    if (usageOk) "Granted – using Android system statistics" else "Not granted – using in-app tracking",
                    ok = usageOk
                ) { Perms.openUsageAccess(context) }
                SwitchRow(Icons.Rounded.SignalCellularAlt, "Prefer system statistics", "Use Android's exact counters when Usage access is granted", s.preferSystemStats) { on ->
                    AppGraph.settings.update { it.copy(preferSystemStats = on) }
                    AppGraph.usage.invalidatePermissionCache()
                }
                ClickRow(Icons.Rounded.Today, "Daily mobile limit", if (s.dailyLimitMb > 0) Format.bytes(s.dailyLimitMb * 1024 * 1024) else "Off") {
                    dialog = NumberDialog.DAILY
                }
                ClickRow(Icons.Rounded.Warning, "Monthly mobile limit", if (s.monthlyLimitMb > 0) Format.bytes(s.monthlyLimitMb * 1024 * 1024) else "Off") {
                    dialog = NumberDialog.MONTHLY
                }
                ClickRow(Icons.Rounded.CalendarMonth, "Billing cycle starts on", "Day ${s.billingCycleDay} of every month") {
                    dialog = NumberDialog.CYCLE
                }
                ClickRow(Icons.Rounded.DeleteSweep, "Reset in-app usage history", "System statistics are not affected") {
                    confirmReset = true
                }
            }
        }

        item {
            Group("Appearance") {
                ChoiceRow(Icons.Rounded.DarkMode, "Theme", ThemeMode.entries.map { it.label }, s.themeMode.ordinal) { i ->
                    AppGraph.settings.update { it.copy(themeMode = ThemeMode.entries[i]) }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(Icons.Rounded.Palette, "Material You colours", "Use colours from your wallpaper", s.dynamicColor) { on ->
                        AppGraph.settings.update { it.copy(dynamicColor = on) }
                    }
                }
                SwitchRow(Icons.Rounded.Contrast, "Pure black (AMOLED)", "Deeper blacks in dark theme", s.pureBlack) { on ->
                    AppGraph.settings.update { it.copy(pureBlack = on) }
                }
            }
        }

        item {
            Group("About") {
                ClickRow(Icons.Rounded.Info, "Net Speed Meter", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {}
                ClickRow(Icons.Rounded.Code, "Source code", "github.com/sremonak/Net-Speed-Meter-") {
                    Perms.start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sremonak/Net-Speed-Meter-")))
                }
            }
        }
    }

    dialog?.let { d ->
        val (title, initial, hint) = when (d) {
            NumberDialog.DAILY -> Triple("Daily mobile limit (MB)", s.dailyLimitMb, "0 = off · e.g. 1024 for 1 GB")
            NumberDialog.MONTHLY -> Triple("Monthly mobile limit (MB)", s.monthlyLimitMb, "0 = off · e.g. 30720 for 30 GB")
            NumberDialog.CYCLE -> Triple("Billing cycle start day", s.billingCycleDay.toLong(), "1 – 31")
        }
        NumberInputDialog(title, initial, hint, onDismiss = { dialog = null }) { v ->
            AppGraph.settings.update {
                when (d) {
                    NumberDialog.DAILY -> it.copy(dailyLimitMb = v.coerceAtLeast(0))
                    NumberDialog.MONTHLY -> it.copy(monthlyLimitMb = v.coerceAtLeast(0))
                    NumberDialog.CYCLE -> it.copy(billingCycleDay = v.toInt().coerceIn(1, 31))
                }
            }
            dialog = null
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset usage history?") },
            text = { Text("This clears the usage recorded by the app. Android's own statistics stay untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    AppGraph.usage.clearLocal()
                    AppGraph.live.resetSession()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        AppCard(contentPadding = PaddingValues(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun RowShell(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, tint: Color = MaterialTheme.colorScheme.primary, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, subtitle: String?, ok: Boolean? = null, onClick: () -> Unit) {
    val tint = when (ok) {
        true -> Brand.Wifi
        false -> Brand.Mobile
        null -> MaterialTheme.colorScheme.primary
    }
    RowShell(icon, title, subtitle, onClick = onClick, tint = tint) {
        if (ok == true) Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Wifi)
        if (ok == false) Icon(Icons.Rounded.Warning, null, tint = Brand.Mobile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(icon: ImageVector, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primary, size = 38.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun NumberInputDialog(title: String, initial: Long, hint: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                singleLine = true,
                supportingText = { Text(hint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toLongOrNull() ?: 0L) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

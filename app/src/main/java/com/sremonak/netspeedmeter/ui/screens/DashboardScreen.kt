package com.sremonak.netspeedmeter.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.data.UsageRepository
import com.sremonak.netspeedmeter.data.UsageSource
import com.sremonak.netspeedmeter.monitor.ConnectionKind
import com.sremonak.netspeedmeter.monitor.LiveTrafficStore
import com.sremonak.netspeedmeter.service.SpeedMeterService
import com.sremonak.netspeedmeter.ui.components.AppCard
import com.sremonak.netspeedmeter.ui.components.IconBadge
import com.sremonak.netspeedmeter.ui.components.LegendDot
import com.sremonak.netspeedmeter.ui.components.LiveSpeedChart
import com.sremonak.netspeedmeter.ui.components.ProgressBar
import com.sremonak.netspeedmeter.ui.components.SectionHeader
import com.sremonak.netspeedmeter.ui.components.SpeedGauge
import com.sremonak.netspeedmeter.ui.components.StatTile
import com.sremonak.netspeedmeter.ui.components.StatusChip
import com.sremonak.netspeedmeter.ui.components.UsageDonut
import com.sremonak.netspeedmeter.ui.theme.Brand
import com.sremonak.netspeedmeter.util.Perms
import java.time.LocalDate

@Composable
fun DashboardScreen(onOpenTab: (AppTab) -> Unit) {
    val context = LocalContext.current
    val live by AppGraph.live.state.collectAsStateWithLifecycle()
    val today by AppGraph.usage.today.collectAsStateWithLifecycle()
    val source by AppGraph.usage.source.collectAsStateWithLifecycle()
    val net by AppGraph.network.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
    val running by SpeedMeterService.running.collectAsStateWithLifecycle()
    val bits = settings.useBits

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++
        AppGraph.usage.invalidatePermissionCache()
    }
    val notifOk = remember(resumeTick) { Perms.notifications(context) }
    val batteryOk = remember(resumeTick) { Perms.batteryUnrestricted(context) }
    val usageOk = remember(resumeTick) { Perms.usageAccess() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
        SpeedMeterService.start(context)
    }
    LaunchedEffect(Unit) {
        if (!settings.onboardingDone) {
            AppGraph.settings.update { it.copy(onboardingDone = true) }
            if (Build.VERSION.SDK_INT >= 33 && !Perms.notifications(context)) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val rxParts = Format.speedParts(live.rx, bits)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Net Speed Meter", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (running) "Live monitoring · always on" else "Meter paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val (label, color) = when {
                    net.kind == ConnectionKind.NONE -> "Offline" to Brand.Danger
                    !net.validated -> "${net.kind.label} · no internet" to Brand.Mobile
                    else -> (net.kind.label + if (net.vpn && net.kind != ConnectionKind.VPN) " + VPN" else "") to
                        if (net.kind == ConnectionKind.MOBILE) Brand.Mobile else Brand.Wifi
                }
                StatusChip(label, color)
            }
        }

        if (!notifOk || !batteryOk || !usageOk) {
            item {
                AppCard {
                    SectionHeader("Finish setup", Icons.Rounded.RocketLaunch)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Give these permissions so the meter can run 24/7 and show exact usage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!notifOk) SetupRow(Icons.Rounded.Notifications, "Show speed notification", "Required for the status bar meter") {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else Perms.openNotificationSettings(context)
                    }
                    if (!batteryOk) SetupRow(Icons.Rounded.BatteryChargingFull, "Run without restrictions", "Disable battery optimization so Android never stops the meter") {
                        Perms.requestBatteryUnrestricted(context)
                    }
                    if (!usageOk) SetupRow(Icons.Rounded.DataUsage, "Exact data usage", "Allow Usage access for system-accurate mobile & Wi-Fi totals") {
                        Perms.openUsageAccess(context)
                    }
                }
            }
        }

        item {
            AppCard {
                SpeedGauge(
                    mbps = live.rx * 8 / 1_000_000.0,
                    value = rxParts.first,
                    unit = rxParts.second,
                    label = "Download",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Download", Format.speed(live.rx, bits), Icons.Rounded.ArrowDownward, Brand.Download,
                        Modifier.weight(1f), sub = "Peak ${Format.speed(live.peakRx, bits)}"
                    )
                    StatTile(
                        "Upload", Format.speed(live.tx, bits), Icons.Rounded.ArrowUpward, Brand.Upload,
                        Modifier.weight(1f), sub = "Peak ${Format.speed(live.peakTx, bits)}"
                    )
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Live activity", Icons.AutoMirrored.Rounded.ShowChart, trailing = {
                    Text("last ${LiveTrafficStore.HISTORY_SIZE}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                Spacer(Modifier.height(16.dp))
                LiveSpeedChart(live.history, Modifier.fillMaxWidth().height(150.dp), LiveTrafficStore.HISTORY_SIZE)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LegendDot(Brand.Download, "Download")
                    LegendDot(Brand.Upload, "Upload")
                }
            }
        }

        item {
            AppCard(onClick = { onOpenTab(AppTab.USAGE) }) {
                SectionHeader("Today's data usage", Icons.Rounded.DataUsage, trailing = {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                        UsageDonut(today.mobile, today.wifi, Modifier.size(130.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Format.bytes(today.total), style = MaterialTheme.typography.titleMedium)
                            Text("total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        UsageLine(Icons.Rounded.SignalCellularAlt, Brand.Mobile, "Mobile data", today.mobile, today.mobileRx, today.mobileTx)
                        UsageLine(Icons.Rounded.Wifi, Brand.Wifi, "Wi-Fi", today.wifi, today.wifiRx, today.wifiTx)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Source: ${source.label}" + if (source == UsageSource.APP) " · grant Usage access for exact totals" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settings.dailyLimitMb > 0) {
                    val limit = settings.dailyLimitMb * 1024 * 1024
                    val pct = Format.percent(today.mobile, limit)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Daily mobile limit", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("$pct% of ${Format.bytes(limit)}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(today.mobile / limit.toFloat(), if (pct >= 100) Brand.Danger else Brand.Mobile)
                }
            }
        }

        item {
            AppCard {
                SectionHeader("This session", Icons.Rounded.Insights)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Downloaded", Format.bytes(live.sessionRx), Icons.Rounded.ArrowDownward, Brand.Download, Modifier.weight(1f))
                    StatTile("Uploaded", Format.bytes(live.sessionTx), Icons.Rounded.ArrowUpward, Brand.Upload, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Running for", Format.duration(System.currentTimeMillis() - live.sessionStart),
                        Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, Modifier.weight(1f)
                    )
                    StatTile(
                        "Est. link", Format.kbpsToReadable(net.downKbps), Icons.Rounded.Public,
                        MaterialTheme.colorScheme.secondary, Modifier.weight(1f), sub = "reported by Android"
                    )
                }
                if (settings.monthlyLimitMb > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Billing cycle started ${Format.dayLabel(UsageRepository.cycleStart(settings.billingCycleDay), LocalDate.now())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(Icons.Rounded.NetworkCheck, "Speed test", "Ping · down · up", Modifier.weight(1f)) { onOpenTab(AppTab.TEST) }
                QuickAction(Icons.Rounded.Public, "Network info", "IP, DNS, Wi-Fi…", Modifier.weight(1f)) { onOpenTab(AppTab.INFO) }
            }
        }
    }
}

@Composable
private fun UsageLine(icon: ImageVector, color: androidx.compose.ui.graphics.Color, label: String, total: Long, rx: Long, tx: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.bytes(total), style = MaterialTheme.typography.titleLarge)
            Text("▼ ${Format.bytes(rx)}  ▲ ${Format.bytes(tx)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onClick) { Text("Allow") }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier, contentPadding = PaddingValues(18.dp), onClick = onClick) {
        IconBadge(icon, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

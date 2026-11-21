package com.sremonak.netspeedmeter.ui.screens

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.sremonak.netspeedmeter.util.Perms
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.data.AppUsage
import com.sremonak.netspeedmeter.data.DailyUsage
import com.sremonak.netspeedmeter.data.UsageRepository
import com.sremonak.netspeedmeter.ui.components.AppCard
import com.sremonak.netspeedmeter.ui.components.IconBadge
import com.sremonak.netspeedmeter.ui.components.LegendDot
import com.sremonak.netspeedmeter.ui.components.ProgressBar
import com.sremonak.netspeedmeter.ui.components.SectionHeader
import com.sremonak.netspeedmeter.ui.components.StatTile
import com.sremonak.netspeedmeter.ui.components.UsageBarChart
import com.sremonak.netspeedmeter.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class UsageRange(val label: String) { WEEK("7 days"), MONTH("30 days"), CYCLE("Billing cycle") }

class UsageViewModel(app: Application) : AndroidViewModel(app) {
    private val _range = MutableStateFlow(UsageRange.WEEK)
    val range = _range.asStateFlow()
    private val _days = MutableStateFlow<List<DailyUsage>>(emptyList())
    val days = _days.asStateFlow()
    private val _apps = MutableStateFlow<List<AppUsage>?>(emptyList())
    val apps = _apps.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    fun select(r: UsageRange) {
        _range.value = r
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val today = LocalDate.now()
            val from = when (_range.value) {
                UsageRange.WEEK -> today.minusDays(6)
                UsageRange.MONTH -> today.minusDays(29)
                UsageRange.CYCLE -> UsageRepository.cycleStart(AppGraph.settings.current.billingCycleDay, today)
            }
            _days.value = runCatching { AppGraph.usage.history(from, today) }.getOrDefault(emptyList())
            _loading.value = false
            _apps.value = runCatching { AppGraph.appUsage.top(from, today) }.getOrNull()
        }
    }

    init {
        refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: UsageViewModel = viewModel()) {
    val range by vm.range.collectAsStateWithLifecycle()
    val storedDays by vm.days.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today by AppGraph.usage.today.collectAsStateWithLifecycle()
    val source by AppGraph.usage.source.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            vm.refresh()
        }
    }

    // Keep today's entry live.
    val days = storedDays.map { if (it.date == today.date) today else it }
    val total = days.fold(DailyUsage(LocalDate.now())) { acc, d -> acc + d.copy(date = acc.date) }
    val avg = if (days.isNotEmpty()) total.total / days.size else 0L

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text("Data usage", style = MaterialTheme.typography.headlineSmall)
                Text("Mobile and Wi-Fi tracked separately · ${source.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UsageRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = r == range,
                        onClick = { vm.select(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, UsageRange.entries.size),
                    ) { Text(r.label, maxLines = 1) }
                }
            }
        }
        item {
            AppCard {
                SectionHeader("Overview", Icons.Rounded.BarChart)
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Mobile", Format.bytes(total.mobile), Icons.Rounded.SignalCellularAlt, Brand.Mobile, Modifier.weight(1f),
                        sub = "▼ ${Format.bytes(total.mobileRx)} ▲ ${Format.bytes(total.mobileTx)}")
                    StatTile("Wi-Fi", Format.bytes(total.wifi), Icons.Rounded.Wifi, Brand.Wifi, Modifier.weight(1f),
                        sub = "▼ ${Format.bytes(total.wifiRx)} ▲ ${Format.bytes(total.wifiTx)}")
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Total", Format.bytes(total.total), Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("Daily average", Format.bytes(avg), Icons.Rounded.Today, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                val every = when {
                    days.size <= 7 -> 1
                    days.size <= 15 -> 2
                    else -> 5
                }
                UsageBarChart(days, Modifier.fillMaxWidth(), labelEvery = every) { d ->
                    if (days.size <= 7) d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    else d.date.dayOfMonth.toString()
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LegendDot(Brand.Mobile, "Mobile")
                    LegendDot(Brand.Wifi, "Wi-Fi")
                }
            }
        }

        if (settings.monthlyLimitMb > 0) {
            item {
                val limit = settings.monthlyLimitMb * 1024 * 1024
                val cycleDays = days.filter { !it.date.isBefore(UsageRepository.cycleStart(settings.billingCycleDay)) }
                val used = cycleDays.sumOf { it.mobile }
                val pct = Format.percent(used, limit)
                AppCard {
                    SectionHeader("Mobile data plan", Icons.Rounded.SignalCellularAlt)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(Format.bytes(used), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        Text("of ${Format.bytes(limit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(used / limit.toFloat(), if (pct >= 100) Brand.Danger else Brand.Mobile)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (range == UsageRange.CYCLE) "$pct% used · ${Format.bytes((limit - used).coerceAtLeast(0))} left this cycle"
                        else "Switch to \"Billing cycle\" for the exact cycle total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            AppCard {
                SectionHeader("Top apps", Icons.Rounded.Apps, trailing = {
                    Text(range.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                Spacer(Modifier.height(10.dp))
                val list = apps
                when {
                    list == null -> {
                        Text(
                            "Allow Usage access to see which apps use your mobile data and Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { Perms.openUsageAccess(context) }) { Text("Allow usage access") }
                    }
                    list.isEmpty() -> Text("No app traffic recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        val maxApp = list.maxOf { it.total }.coerceAtLeast(1L)
                        list.forEach { AppRow(it, maxApp) }
                    }
                }
            }
        }

        item {
            Text("Daily breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        item {
            AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                val list = days.reversed()
                list.forEachIndexed { i, d ->
                    DayRow(d, maxOf(1L, days.maxOfOrNull { it.total } ?: 1L))
                    if (i != list.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (list.isEmpty()) {
                    Text("No data yet", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppUsage, max: Long) {
    val bmp = remember(app.packageName) { app.icon?.asImageBitmap() }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (bmp != null) {
            Image(bmp, null, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            IconBadge(Icons.Rounded.Android, MaterialTheme.colorScheme.primary, size = 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(Format.bytes(app.total), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val mf = app.mobile.toFloat() / max
                val wf = app.wifi.toFloat() / max
                if (mf > 0f) Box(Modifier.weight(mf.coerceAtLeast(0.001f)).height(6.dp).background(Brand.Mobile))
                if (wf > 0f) Box(Modifier.weight(wf.coerceAtLeast(0.001f)).height(6.dp).background(Brand.Wifi))
                val rest = 1f - mf - wf
                if (rest > 0.001f) Spacer(Modifier.weight(rest))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Mobile ${Format.bytes(app.mobile)} · Wi-Fi ${Format.bytes(app.wifi)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayRow(d: DailyUsage, max: Long) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.dayLabel(d.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Format.bytes(d.total), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val mf = d.mobile.toFloat() / max
            val wf = d.wifi.toFloat() / max
            if (mf > 0f) Box(Modifier.weight(mf.coerceAtLeast(0.001f)).height(6.dp).background(Brand.Mobile))
            if (wf > 0f) Box(Modifier.weight(wf.coerceAtLeast(0.001f)).height(6.dp).background(Brand.Wifi))
            val rest = 1f - mf - wf
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Mobile))
                Spacer(Modifier.width(6.dp))
                Text("Mobile ${Format.bytes(d.mobile)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Wifi))
                Spacer(Modifier.width(6.dp))
                Text("Wi-Fi ${Format.bytes(d.wifi)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

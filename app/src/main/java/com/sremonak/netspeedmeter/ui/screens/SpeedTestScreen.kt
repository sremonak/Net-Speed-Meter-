package com.sremonak.netspeedmeter.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sremonak.netspeedmeter.AppGraph
import com.sremonak.netspeedmeter.core.Format
import com.sremonak.netspeedmeter.monitor.ConnectionKind
import com.sremonak.netspeedmeter.speedtest.SpeedTestEngine
import com.sremonak.netspeedmeter.speedtest.SpeedTestResult
import com.sremonak.netspeedmeter.speedtest.SpeedTestState
import com.sremonak.netspeedmeter.speedtest.TestPhase
import com.sremonak.netspeedmeter.ui.components.AppCard
import com.sremonak.netspeedmeter.ui.components.GradientButton
import com.sremonak.netspeedmeter.ui.components.MiniLineChart
import com.sremonak.netspeedmeter.ui.components.SectionHeader
import com.sremonak.netspeedmeter.ui.components.SpeedGauge
import com.sremonak.netspeedmeter.ui.components.StatTile
import com.sremonak.netspeedmeter.ui.components.StatusChip
import com.sremonak.netspeedmeter.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class SpeedTestViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = SpeedTestEngine()
    private val _state = MutableStateFlow(SpeedTestState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            engine.run().collect { s ->
                _state.value = s
                if (s.phase == TestPhase.DONE && s.downloadMbps != null && s.uploadMbps != null) {
                    AppGraph.speedTestHistory.add(
                        SpeedTestResult(
                            time = System.currentTimeMillis(),
                            downloadMbps = s.downloadMbps,
                            uploadMbps = s.uploadMbps,
                            pingMs = s.pingMs ?: 0.0,
                            jitterMs = s.jitterMs ?: 0.0,
                            network = AppGraph.network.state.value.kind.label,
                            server = s.server ?: "Cloudflare",
                        )
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(phase = TestPhase.IDLE, liveMbps = 0.0, progress = 0f)
    }
}

@Composable
fun SpeedTestScreen(vm: SpeedTestViewModel = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val history by AppGraph.speedTestHistory.items.collectAsStateWithLifecycle()
    val net by AppGraph.network.state.collectAsStateWithLifecycle()

    val gaugeValue = when {
        st.running && st.phase in setOf(TestPhase.DOWNLOAD, TestPhase.UPLOAD) -> st.liveMbps
        st.phase == TestPhase.DONE -> st.downloadMbps ?: 0.0
        else -> 0.0
    }
    val gaugeLabel = when (st.phase) {
        TestPhase.DONE -> "Download"
        TestPhase.IDLE -> "Ready"
        else -> st.phase.label
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speed test", style = MaterialTheme.typography.headlineSmall)
                    Text(st.server ?: "Cloudflare global network", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(st.phase.label, if (st.phase == TestPhase.ERROR) Brand.Danger else MaterialTheme.colorScheme.primary)
            }
        }
        item {
            AppCard {
                SpeedGauge(
                    mbps = gaugeValue,
                    value = Format.mbps(if (st.phase == TestPhase.IDLE) null else gaugeValue),
                    unit = "Mbps",
                    label = gaugeLabel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    startColor = if (st.phase == TestPhase.UPLOAD) Brand.Upload else Brand.GradientStart,
                )
                AnimatedVisibility(st.running) {
                    Column {
                        LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        MiniLineChart(st.points, if (st.phase == TestPhase.UPLOAD) Brand.Upload else Brand.Download, Modifier.fillMaxWidth().height(60.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (st.running) {
                    OutlinedButton(onClick = vm::stop, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Rounded.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop test")
                    }
                } else {
                    GradientButton(
                        text = if (st.phase == TestPhase.DONE || st.phase == TestPhase.ERROR) "Test again" else "Start test",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = vm::start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (net.kind == ConnectionKind.MOBILE && !st.running) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Brand.Mobile, modifier = Modifier.padding(end = 8.dp))
                        Text("You're on mobile data – a test can use 50–500 MB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                st.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Download", "${Format.mbps(st.downloadMbps)} Mbps", Icons.Rounded.ArrowDownward, Brand.Download, Modifier.weight(1f))
                StatTile("Upload", "${Format.mbps(st.uploadMbps)} Mbps", Icons.Rounded.ArrowUpward, Brand.Upload, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Ping", "${Format.ms(st.pingMs)} ms", Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("Jitter", "${Format.ms(st.jitterMs)} ms", Icons.Rounded.GraphicEq, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        if (st.clientIp != null || st.dataUsedBytes > 0) {
            item {
                AppCard {
                    InfoLine("Public IP", st.clientIp ?: "--")
                    InfoLine("Provider", st.isp ?: "--")
                    InfoLine("Server", st.server ?: "--")
                    InfoLine("Data used by test", Format.bytes(st.dataUsedBytes))
                    if (st.phase == TestPhase.DONE) InfoLine("Rating", rating(st))
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                SectionHeader("History", Icons.Rounded.History, Modifier.padding(start = 4.dp, top = 6.dp), trailing = {
                    TextButton(onClick = { AppGraph.speedTestHistory.clear() }) { Text("Clear") }
                })
            }
            item {
                AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                    history.forEachIndexed { i, r ->
                        HistoryRow(r)
                        if (i != history.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun rating(st: SpeedTestState): String {
    val d = st.downloadMbps ?: 0.0
    val p = st.pingMs ?: 999.0
    return when {
        d >= 100 && p < 30 -> "Excellent – 4K streaming, gaming, large downloads"
        d >= 25 && p < 60 -> "Very good – HD streaming & video calls"
        d >= 10 -> "Good – browsing, social media, SD/HD video"
        d >= 3 -> "Fair – browsing and music"
        else -> "Slow – basic browsing only"
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun HistoryRow(r: SpeedTestResult) {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(r.time))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row {
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(r.network, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text("▼ ${Format.mbps(r.downloadMbps)}", style = MaterialTheme.typography.titleMedium, color = Brand.Download, modifier = Modifier.weight(1f))
            Text("▲ ${Format.mbps(r.uploadMbps)}", style = MaterialTheme.typography.titleMedium, color = Brand.Upload, modifier = Modifier.weight(1f))
            Text("${Format.ms(r.pingMs)} ms", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.8f))
        }
    }
}

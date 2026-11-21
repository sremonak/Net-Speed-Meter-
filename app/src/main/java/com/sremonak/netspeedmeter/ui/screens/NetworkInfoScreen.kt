package com.sremonak.netspeedmeter.ui.screens

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sremonak.netspeedmeter.info.InfoItem
import com.sremonak.netspeedmeter.info.InfoSection
import com.sremonak.netspeedmeter.info.NetworkInfoProvider
import com.sremonak.netspeedmeter.info.PublicInfo
import com.sremonak.netspeedmeter.info.PublicIpFetcher
import com.sremonak.netspeedmeter.info.SectionKind
import com.sremonak.netspeedmeter.ui.components.AppCard
import com.sremonak.netspeedmeter.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PublicState {
    data object Loading : PublicState
    data class Ready(val info: PublicInfo) : PublicState
    data class Failed(val message: String) : PublicState
}

class InfoViewModel(app: Application) : AndroidViewModel(app) {
    private val provider = NetworkInfoProvider(app)
    private val _sections = MutableStateFlow<List<InfoSection>>(emptyList())
    val sections = _sections.asStateFlow()
    private val _public = MutableStateFlow<PublicState>(PublicState.Loading)
    val public = _public.asStateFlow()

    val hasLocation get() = provider.hasLocation()
    val hasPhone get() = provider.hasPhone()

    suspend fun refreshLocal() {
        _sections.value = withContext(Dispatchers.IO) { runCatching { provider.collect() }.getOrDefault(emptyList()) }
    }

    fun refreshLocalAsync() {
        viewModelScope.launch { refreshLocal() }
    }

    fun refreshPublic() {
        viewModelScope.launch {
            _public.value = PublicState.Loading
            _public.value = runCatching { PublicState.Ready(PublicIpFetcher.fetch()) }
                .getOrElse { PublicState.Failed(it.message ?: "Unavailable") }
        }
    }

    init {
        refreshPublic()
    }
}

@Composable
fun NetworkInfoScreen(vm: InfoViewModel = viewModel()) {
    val context = LocalContext.current
    val sections by vm.sections.collectAsStateWithLifecycle()
    val public by vm.public.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshLocal()
                delay(3_000)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshLocalAsync()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network info", style = MaterialTheme.typography.headlineSmall)
                    Text("Live details · long-press any value to copy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = {
                    vm.refreshPublic()
                    vm.refreshLocalAsync()
                }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
        }

        if (!vm.hasLocation || !vm.hasPhone) {
            item {
                AppCard {
                    SectionHeader("Unlock full details", Icons.Rounded.LocationOn)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android requires Location for the Wi-Fi name/BSSID and Phone access for 4G/5G network type. Nothing leaves your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.READ_PHONE_STATE,
                            )
                        )
                    }) { Text("Grant permissions") }
                }
            }
        }

        item {
            val items = when (val p = public) {
                is PublicState.Loading -> listOf(InfoItem("Public IP", "Looking up…"))
                is PublicState.Failed -> listOf(InfoItem("Public IP", "Unavailable (${p.message})"))
                is PublicState.Ready -> listOfNotNull(
                    InfoItem("Public IP", p.info.ip),
                    p.info.isp?.let { InfoItem("ISP", it) },
                    p.info.asn?.let { InfoItem("ASN", it) },
                    listOfNotNull(p.info.city, p.info.region, p.info.country).takeIf { it.isNotEmpty() }
                        ?.let { InfoItem("Location", it.joinToString(", ")) },
                    p.info.timezone?.let { InfoItem("Timezone", it) },
                    p.info.location?.let { InfoItem("Coordinates", it) },
                    p.info.edge?.let { InfoItem("Nearest edge", it) },
                    InfoItem("Lookup source", p.info.source),
                )
            }
            InfoCard(InfoSection(SectionKind.PUBLIC, "Public internet", items), context)
        }

        items(sections, key = { it.kind.name }) { section ->
            InfoCard(section, context)
        }
    }
}

private fun iconFor(kind: SectionKind): ImageVector = when (kind) {
    SectionKind.CONNECTION -> Icons.Rounded.Router
    SectionKind.WIFI -> Icons.Rounded.Wifi
    SectionKind.MOBILE -> Icons.Rounded.SignalCellularAlt
    SectionKind.IP -> Icons.Rounded.Lan
    SectionKind.PUBLIC -> Icons.Rounded.Public
    SectionKind.TRAFFIC -> Icons.Rounded.SwapVert
    SectionKind.INTERFACES -> Icons.Rounded.Memory
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoCard(section: InfoSection, context: Context) {
    AppCard(contentPadding = PaddingValues(top = 18.dp, bottom = 8.dp)) {
        SectionHeader(section.title, iconFor(section.kind), Modifier.padding(horizontal = 20.dp))
        section.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        section.items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { copy(context, item) })
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.45f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    item.value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.55f)
                )
            }
            if (i != section.items.lastIndex) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        }
    }
}

private fun copy(context: Context, item: InfoItem) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(item.label, item.value))
    Toast.makeText(context, "${item.label} copied", Toast.LENGTH_SHORT).show()
}


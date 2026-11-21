package com.sremonak.netspeedmeter.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class ConnectionKind(val label: String) {
    WIFI("Wi-Fi"),
    MOBILE("Mobile data"),
    ETHERNET("Ethernet"),
    VPN("VPN"),
    OTHER("Connected"),
    NONE("Offline"),
}

data class NetState(
    val kind: ConnectionKind = ConnectionKind.NONE,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val vpn: Boolean = false,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
)

/** Tracks the default network and whether any Wi-Fi / Ethernet network is up. */
class NetworkStateTracker(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(NetState())
    val state: StateFlow<NetState> = _state.asStateFlow()

    private val wifiNetworks: MutableSet<Network> = ConcurrentHashMap.newKeySet()
    private val ethernetNetworks: MutableSet<Network> = ConcurrentHashMap.newKeySet()
    private var started = false

    /** Non-mobile traffic is attributed to Wi-Fi only when Wi-Fi/Ethernet is actually connected. */
    val isOnWifiOrEthernet: Boolean
        get() = wifiNetworks.isNotEmpty() || ethernetNetworks.isNotEmpty() ||
            _state.value.kind == ConnectionKind.WIFI || _state.value.kind == ConnectionKind.ETHERNET

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _state.value = caps.toNetState()
        }

        override fun onLost(network: Network) {
            _state.value = NetState()
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetworks.add(network)
        }

        override fun onLost(network: Network) {
            wifiNetworks.remove(network)
        }
    }

    private val ethernetCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            ethernetNetworks.add(network)
        }

        override fun onLost(network: Network) {
            ethernetNetworks.remove(network)
        }
    }

    @Synchronized
    fun start() {
        if (started || cm == null) return
        started = true
        runCatching {
            cm.activeNetwork?.let { n -> cm.getNetworkCapabilities(n)?.let { _state.value = it.toNetState() } }
            cm.registerDefaultNetworkCallback(defaultCallback)
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback
            )
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(),
                ethernetCallback
            )
        }
    }

    private fun NetworkCapabilities.toNetState(): NetState {
        val kind = when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionKind.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionKind.MOBILE
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionKind.ETHERNET
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionKind.VPN
            else -> ConnectionKind.OTHER
        }
        return NetState(
            kind = kind,
            validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            vpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            downKbps = linkDownstreamBandwidthKbps,
            upKbps = linkUpstreamBandwidthKbps,
        )
    }
}

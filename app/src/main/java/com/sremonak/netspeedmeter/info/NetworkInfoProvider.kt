package com.sremonak.netspeedmeter.info

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.sremonak.netspeedmeter.core.Format
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

data class InfoItem(val label: String, val value: String)

enum class SectionKind { CONNECTION, WIFI, MOBILE, IP, PUBLIC, TRAFFIC, INTERFACES }

data class InfoSection(
    val kind: SectionKind,
    val title: String,
    val items: List<InfoItem>,
    val note: String? = null,
)

/** Collects a detailed snapshot of the current network state. */
class NetworkInfoProvider(private val context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val tm = context.getSystemService(TelephonyManager::class.java)

    fun hasLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPhone() = granted(Manifest.permission.READ_PHONE_STATE)

    fun collect(): List<InfoSection> {
        val out = ArrayList<InfoSection>()
        val network = cm?.activeNetwork
        val caps = network?.let { cm?.getNetworkCapabilities(it) }
        val lp = network?.let { cm?.getLinkProperties(it) }

        out += safe(SectionKind.CONNECTION, "Connection") { connection(caps, lp) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (wifiConnected) {
            out += InfoSection(
                SectionKind.WIFI, "Wi-Fi", runCatching { wifiItems() }.getOrDefault(emptyList()),
                note = if (!hasLocation()) "Allow location to see Wi-Fi name (SSID) and BSSID." else null
            )
        }
        if (tm != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            out += InfoSection(
                SectionKind.MOBILE, "Mobile network", runCatching { mobileItems() }.getOrDefault(emptyList()),
                note = if (!hasPhone()) "Allow phone access to see network type (4G/5G) and signal details." else null
            )
        }
        if (lp != null) out += safe(SectionKind.IP, "IP & DNS") { ipItems(lp) }
        out += safe(SectionKind.TRAFFIC, "Traffic since boot") { trafficItems() }
        out += safe(SectionKind.INTERFACES, "Network interfaces") { interfaceItems() }
        return out
    }

    private inline fun safe(kind: SectionKind, title: String, block: () -> List<InfoItem>): InfoSection =
        InfoSection(kind, title, runCatching(block).getOrElse { listOf(InfoItem("Error", it.message ?: "Unavailable")) })

    private fun connection(caps: NetworkCapabilities?, lp: LinkProperties?): List<InfoItem> {
        if (caps == null) return listOf(InfoItem("Status", "Disconnected"))
        val items = ArrayList<InfoItem>()
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }
        items += InfoItem("Status", "Connected")
        items += InfoItem("Type", transports.joinToString(" + ").ifBlank { "Unknown" })
        items += InfoItem(
            "Internet",
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Working (validated)" else "No internet access"
        )
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) items += InfoItem("Captive portal", "Sign-in required")
        items += InfoItem("Metered", yesNo(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)))
        if (Build.VERSION.SDK_INT >= 28) {
            items += InfoItem("Roaming", yesNo(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)))
        }
        items += InfoItem("VPN", if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "Active" else "Not active")
        items += InfoItem("Est. download bandwidth", Format.kbpsToReadable(caps.linkDownstreamBandwidthKbps))
        items += InfoItem("Est. upload bandwidth", Format.kbpsToReadable(caps.linkUpstreamBandwidthKbps))
        if (Build.VERSION.SDK_INT >= 29 && caps.signalStrength != Int.MIN_VALUE) {
            items += InfoItem("Signal strength", "${caps.signalStrength} dBm")
        }
        lp?.interfaceName?.let { items += InfoItem("Interface", it) }
        if (Build.VERSION.SDK_INT >= 29 && lp != null && lp.mtu > 0) items += InfoItem("MTU", lp.mtu.toString())
        if (Build.VERSION.SDK_INT >= 28 && lp != null) {
            items += InfoItem(
                "Private DNS",
                when {
                    !lp.isPrivateDnsActive -> "Off"
                    lp.privateDnsServerName != null -> lp.privateDnsServerName!!
                    else -> "Automatic"
                }
            )
        }
        cm?.let {
            items += InfoItem(
                "Data Saver",
                when (it.restrictBackgroundStatus) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "On (this app restricted)"
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "On (this app allowed)"
                    else -> "Off"
                }
            )
        }
        return items
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    private fun wifiItems(): List<InfoItem> {
        val wm = wifi ?: return emptyList()
        val info = wm.connectionInfo ?: return emptyList()
        val items = ArrayList<InfoItem>()
        val ssid = info.ssid?.trim('"')
        items += InfoItem(
            "Network name (SSID)",
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") "Hidden – location permission needed" else ssid
        )
        val bssid = info.bssid
        items += InfoItem("BSSID", if (bssid == null || bssid == "02:00:00:00:00:00") "Unavailable" else bssid.uppercase())
        val rssi = info.rssi
        val quality = (2 * (rssi + 100)).coerceIn(0, 100)
        items += InfoItem("Signal (RSSI)", "$rssi dBm · $quality% · ${signalWord(quality)}")
        items += InfoItem("Link speed", "${info.linkSpeed} Mbps")
        if (Build.VERSION.SDK_INT >= 29) {
            items += InfoItem("Tx / Rx link speed", "${info.txLinkSpeedMbps} / ${info.rxLinkSpeedMbps} Mbps")
        }
        if (Build.VERSION.SDK_INT >= 30) {
            items += InfoItem("Max supported Tx / Rx", "${info.maxSupportedTxLinkSpeedMbps} / ${info.maxSupportedRxLinkSpeedMbps} Mbps")
        }
        val f = info.frequency
        if (f > 0) {
            items += InfoItem("Frequency", "$f MHz")
            items += InfoItem("Band", band(f))
            channel(f)?.let { items += InfoItem("Channel", it.toString()) }
        }
        if (Build.VERSION.SDK_INT >= 30) items += InfoItem("Wi-Fi standard", wifiStandard(info.wifiStandard))
        if (Build.VERSION.SDK_INT >= 31) items += InfoItem("Security", securityType(info.currentSecurityType))
        items += InfoItem("Hidden network", yesNo(info.hiddenSSID))
        info.macAddress?.let { mac ->
            items += InfoItem("Device MAC", if (mac == "02:00:00:00:00:00") "Randomized / hidden" else mac.uppercase())
        }
        runCatching {
            val dhcp = wm.dhcpInfo
            if (dhcp != null) {
                if (dhcp.serverAddress != 0) items += InfoItem("DHCP server", intToIp(dhcp.serverAddress))
                if (dhcp.leaseDuration > 0) items += InfoItem("DHCP lease", Format.duration(dhcp.leaseDuration * 1000L))
            }
        }
        items += InfoItem("5 GHz supported", yesNo(wm.is5GHzBandSupported))
        if (Build.VERSION.SDK_INT >= 30) {
            items += InfoItem("6 GHz supported", yesNo(wm.is6GHzBandSupported))
            items += InfoItem("Wi-Fi 6 supported", yesNo(wm.isWifiStandardSupported(6)))
        }
        if (Build.VERSION.SDK_INT >= 33) items += InfoItem("Wi-Fi 7 supported", yesNo(wm.isWifiStandardSupported(8)))
        return items
    }

    @SuppressLint("MissingPermission")
    private fun mobileItems(): List<InfoItem> {
        val t = tm ?: return emptyList()
        val items = ArrayList<InfoItem>()
        items += InfoItem("Operator", t.networkOperatorName.orEmpty().ifBlank { "Unknown" })
        items += InfoItem("SIM operator", t.simOperatorName.orEmpty().ifBlank { "Unknown" })
        val op = t.networkOperator.orEmpty()
        if (op.length >= 5) items += InfoItem("MCC / MNC", "${op.substring(0, 3)} / ${op.substring(3)}")
        t.networkCountryIso?.takeIf { it.isNotBlank() }?.let { items += InfoItem("Country", it.uppercase()) }
        items += InfoItem("SIM state", simState(t.simState))
        if (hasPhone()) {
            runCatching { items += InfoItem("Network type", networkType(t.dataNetworkType)) }
            runCatching { items += InfoItem("Mobile data", if (t.isDataEnabled) "Enabled" else "Disabled") }
        }
        items += InfoItem("Data state", dataState(t.dataState))
        items += InfoItem("Data activity", dataActivity(t.dataActivity))
        items += InfoItem("Roaming", yesNo(t.isNetworkRoaming))
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                t.signalStrength?.let { ss ->
                    val dbm = ss.cellSignalStrengths.firstOrNull()?.dbm
                    val levelText = listOf("None", "Poor", "Moderate", "Good", "Great").getOrElse(ss.level) { "Unknown" }
                    items += InfoItem("Signal", (if (dbm != null && dbm != Int.MAX_VALUE) "$dbm dBm · " else "") + levelText)
                }
            }
        }
        items += InfoItem("Phone type", phoneType(t.phoneType))
        val slots = if (Build.VERSION.SDK_INT >= 30) t.activeModemCount else @Suppress("DEPRECATION") t.phoneCount
        items += InfoItem("Active SIM slots", slots.toString())
        return items
    }

    private fun ipItems(lp: LinkProperties): List<InfoItem> {
        val items = ArrayList<InfoItem>()
        val v4 = lp.linkAddresses.filter { it.address is Inet4Address }
        val v6 = lp.linkAddresses.filter { it.address is Inet6Address }
        v4.forEach {
            items += InfoItem("IPv4 address", "${it.address.hostAddress}/${it.prefixLength}")
            items += InfoItem("Subnet mask", prefixToMask(it.prefixLength))
        }
        v6.forEach { la ->
            val a = la.address as Inet6Address
            val kind = when {
                a.isLinkLocalAddress -> "IPv6 (link-local)"
                a.isSiteLocalAddress -> "IPv6 (site-local)"
                else -> "IPv6 (global)"
            }
            items += InfoItem(kind, "${a.hostAddress?.substringBefore('%')}/${la.prefixLength}")
        }
        val gateways = lp.routes.filter { it.isDefaultRoute && it.gateway != null }
            .mapNotNull { it.gateway?.hostAddress?.substringBefore('%') }
            .filter { it != "0.0.0.0" && it != "::" }
            .distinct()
        items += InfoItem("Gateway", gateways.joinToString("\n").ifBlank { "Unknown" })
        items += InfoItem("DNS servers", lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.joinToString("\n").ifBlank { "Unknown" })
        lp.domains?.takeIf { it.isNotBlank() }?.let { items += InfoItem("Search domains", it) }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.dhcpServerAddress?.hostAddress?.let { items += InfoItem("DHCP server", it) }
            lp.nat64Prefix?.let { items += InfoItem("NAT64 prefix", it.toString()) }
        }
        items += InfoItem("HTTP proxy", lp.httpProxy?.let { "${it.host}:${it.port}" } ?: "None")
        return items
    }

    private fun trafficItems(): List<InfoItem> {
        val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        val mobRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0)
        val mobTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0)
        val appRx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)
        val appTx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
        return listOf(
            InfoItem("Device uptime", Format.duration(SystemClock.elapsedRealtime())),
            InfoItem("Total download", Format.bytes(totalRx)),
            InfoItem("Total upload", Format.bytes(totalTx)),
            InfoItem("Mobile ▼ / ▲", "${Format.bytes(mobRx)} / ${Format.bytes(mobTx)}"),
            InfoItem("Wi-Fi & other ▼ / ▲", "${Format.bytes((totalRx - mobRx).coerceAtLeast(0))} / ${Format.bytes((totalTx - mobTx).coerceAtLeast(0))}"),
            InfoItem("Total packets ▼ / ▲", "${TrafficStats.getTotalRxPackets().coerceAtLeast(0)} / ${TrafficStats.getTotalTxPackets().coerceAtLeast(0)}"),
            InfoItem("This app ▼ / ▲", "${Format.bytes(appRx)} / ${Format.bytes(appTx)}"),
        )
    }

    private fun interfaceItems(): List<InfoItem> {
        val list = NetworkInterface.getNetworkInterfaces()?.toList() ?: return emptyList()
        return list.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .sortedBy { it.name }
            .map { ni ->
                val addrs = ni.inetAddresses.toList().mapNotNull { it.hostAddress?.substringBefore('%') }
                val mtu = runCatching { ni.mtu }.getOrDefault(0)
                val flags = buildList {
                    if (runCatching { ni.isVirtual }.getOrDefault(false)) add("virtual")
                    if (runCatching { ni.isPointToPoint }.getOrDefault(false)) add("point-to-point")
                }
                InfoItem(
                    ni.name,
                    (addrs.ifEmpty { listOf("No address") } + listOfNotNull(
                        if (mtu > 0) "MTU $mtu" else null,
                        flags.takeIf { it.isNotEmpty() }?.joinToString()
                    )).joinToString("\n")
                )
            }
            .ifEmpty { listOf(InfoItem("Interfaces", "None active")) }
    }

    // ---- helpers ----------------------------------------------------------------

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

    private fun signalWord(q: Int) = when {
        q >= 80 -> "Excellent"
        q >= 60 -> "Good"
        q >= 40 -> "Fair"
        q >= 20 -> "Weak"
        else -> "Very weak"
    }

    private fun band(f: Int) = when (f) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5899 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> "$f MHz"
    }

    private fun channel(f: Int): Int? = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2407) / 5
        f in 4900..5899 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> null
    }

    private fun wifiStandard(s: Int) = when (s) {
        1 -> "Legacy (802.11a/b/g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> "Unknown"
    }

    private fun securityType(t: Int) = when (t) {
        0 -> "Open"
        1 -> "WEP"
        2 -> "WPA/WPA2-Personal"
        3 -> "WPA/WPA2-Enterprise"
        4 -> "WPA3-Personal (SAE)"
        5 -> "WPA3-Enterprise 192-bit"
        6 -> "Enhanced Open (OWE)"
        7 -> "WAPI-PSK"
        8 -> "WAPI-Certificate"
        9 -> "WPA3-Enterprise"
        10 -> "OSEN"
        11, 12 -> "Passpoint"
        13 -> "DPP"
        else -> "Unknown"
    }

    private fun networkType(t: Int) = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G (HSPA+)"
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G (HSPA)"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G (HSDPA)"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G (HSUPA)"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G (TD-SCDMA)"
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G (EV-DO)"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "3G (eHRPD)"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
        TelephonyManager.NETWORK_TYPE_GSM -> "2G (GSM)"
        TelephonyManager.NETWORK_TYPE_CDMA -> "2G (CDMA)"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "2G (1xRTT)"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN (Wi-Fi calling)"
        else -> "Unknown"
    }

    private fun simState(s: Int) = when (s) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
        else -> "Unknown"
    }

    private fun dataState(s: Int) = when (s) {
        TelephonyManager.DATA_CONNECTED -> "Connected"
        TelephonyManager.DATA_CONNECTING -> "Connecting"
        TelephonyManager.DATA_SUSPENDED -> "Suspended"
        TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
        else -> "Unknown"
    }

    private fun dataActivity(a: Int) = when (a) {
        TelephonyManager.DATA_ACTIVITY_IN -> "Receiving"
        TelephonyManager.DATA_ACTIVITY_OUT -> "Sending"
        TelephonyManager.DATA_ACTIVITY_INOUT -> "Sending & receiving"
        TelephonyManager.DATA_ACTIVITY_DORMANT -> "Dormant"
        else -> "Idle"
    }

    private fun phoneType(t: Int) = when (t) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        else -> "None"
    }

    private fun intToIp(i: Int) = "${i and 0xff}.${i shr 8 and 0xff}.${i shr 16 and 0xff}.${i shr 24 and 0xff}"

    private fun prefixToMask(prefix: Int): String {
        if (prefix !in 0..32) return "-"
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return "${mask shr 24 and 0xff}.${mask shr 16 and 0xff}.${mask shr 8 and 0xff}.${mask and 0xff}"
    }
}

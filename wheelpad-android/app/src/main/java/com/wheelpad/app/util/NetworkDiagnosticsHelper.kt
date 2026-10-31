package com.wheelpad.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

data class NetworkDiagnosticReport(
    val isWifiConnected: Boolean,
    val isEthernetOrUsbConnected: Boolean,
    val isCellularOnly: Boolean,
    val isVpnActive: Boolean,
    val localIp: String?,
    val gatewayIp: String?,
    val activeInterfaceName: String?,
    val warnings: List<String>
)

object NetworkDiagnosticsHelper {

    fun runDiagnostics(context: Context): NetworkDiagnosticReport {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }

        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false

        val isCellularOnly = isCellular && !isWifi && !isEthernet

        var localIp: String? = null
        var gatewayIp: String? = null

        lp?.linkAddresses?.forEach { linkAddr ->
            val addr = linkAddr.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                localIp = addr.hostAddress
            }
        }

        lp?.routes?.forEach { route ->
            val g = route.gateway
            if (g is Inet4Address && !g.isAnyLocalAddress) {
                val host = g.hostAddress
                if (!host.isNullOrBlank() && host != "0.0.0.0") {
                    gatewayIp = host
                }
            }
        }

        val warnings = mutableListOf<String>()

        if (isVpn) {
            warnings.add("VPN DETECTED: A VPN is active on this device. VPN tunnels block local LAN discovery and controller streaming. Turn off VPN or enable Local LAN Bypass / Split Tunneling.")
        }
        if (isCellularOnly) {
            warnings.add("CELLULAR DATA ONLY: Wi-Fi is disconnected. Connect both phone and PC to the same Wi-Fi router, or connect a USB cable and enable 'USB Tethering' in Android Settings.")
        }
        if (!isWifi && !isEthernet && !isCellular) {
            warnings.add("NO NETWORK DETECTED: Turn on Wi-Fi or plug in USB cable with USB Tethering enabled.")
        }

        return NetworkDiagnosticReport(
            isWifiConnected = isWifi,
            isEthernetOrUsbConnected = isEthernet,
            isCellularOnly = isCellularOnly,
            isVpnActive = isVpn,
            localIp = localIp,
            gatewayIp = gatewayIp,
            activeInterfaceName = lp?.interfaceName,
            warnings = warnings
        )
    }
}


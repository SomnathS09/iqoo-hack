package hack.pune.iqoo.bloomlens.provider

import java.net.Inet4Address
import java.net.NetworkInterface

/** Best-effort discovery of this device's local IPv4 addresses (e.g. its own Wi-Fi hotspot IP). */
object NetworkUtils {
    data class LocalAddress(val interfaceName: String, val address: String)

    fun localIpv4Addresses(): List<LocalAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .map { LocalAddress(iface.name, it.hostAddress.orEmpty()) }
            }
            .filter { it.address.isNotBlank() }
            .toList()
    }.getOrDefault(emptyList())
}

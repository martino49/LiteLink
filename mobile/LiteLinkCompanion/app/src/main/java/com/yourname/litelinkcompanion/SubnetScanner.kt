package com.yourname.litelinkcompanion

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans the phone's local /24 subnet for the LiteLink desktop by probing
 * its WebSocket port (8765) on every host, in parallel.
 */
class SubnetScanner(
    private val context: Context,
    private val port: Int = 8765,
    private val onFound: (String) -> Unit,
    private val onDone: (() -> Unit)? = null
) {

    companion object {
        private const val TIMEOUT_MS = 350
        private const val CONCURRENCY = 32
    }

    private val stopped = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(CONCURRENCY)

    fun start() {
        Thread {
            val prefix = networkPrefix() ?: run {
                onDone?.invoke()
                return@Thread
            }
            val jobs = (1..254).map { i -> pool.submit { probe("$prefix.$i") } }
            jobs.forEach { it.get() }
            pool.shutdown()
            onDone?.invoke()
        }.start()
    }

    fun stop() {
        stopped.set(true)
        pool.shutdownNow()
    }

    /** Returns the first three octets of the phone's Wi-Fi address, e.g. "192.168.100". */
    private fun networkPrefix(): String? {
        try {
            @Suppress("DEPRECATION")
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo?.ipAddress ?: return null
            val host = "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}"
            if (host.startsWith("192.168") || host.startsWith("10.") || host.startsWith("172.")) {
                return host
            }
        } catch (e: Exception) {
            // fall through to interface scan
        }
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement() as InetAddress
                    if (a.isLoopbackAddress || a.address.size != 4) continue
                    val parts = a.hostAddress?.split(".") ?: continue
                    if (parts.size == 4 &&
                        (parts[0] == "192" || parts[0] == "10" || parts[0] == "172")
                    ) {
                        return parts.take(3).joinToString(".")
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun probe(ip: String) {
        if (stopped.get()) return
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
                if (!stopped.get()) onFound(ip)
            }
        } catch (e: Exception) {
            // port closed / unreachable — ignore
        }
    }
}

package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Listens for the LiteLink desktop's UDP broadcast beacon on port 8770.
 *
 * The desktop announces itself every ~5s with a JSON payload:
 *   {"litelink":true,"v":1,"name":..,"ip":..,"port":8765,"code":..}
 * This gives the phone instant "your PC is here" discovery that works even
 * when mDNS/NSD is blocked (common on MIUI).
 */
class BroadcastListener(
    private val context: Context,
    private val onFound: (Map<String, Any>) -> Unit
) {

    companion object {
        private const val TAG = "LiteLink.Beacon"
        const val BEACON_PORT = 8770
        private const val TIMEOUT_MS = 4000
    }

    private val main = Handler(Looper.getMainLooper())
    private var socket: DatagramSocket? = null
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(InetSocketAddress(BEACON_PORT))
                s.soTimeout = TIMEOUT_MS
                socket = s
                val buf = ByteArray(2048)
                while (running) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        s.receive(packet)
                        val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val obj = JSONObject(json)
                        if (obj.optBoolean("litelink")) {
                            val info = mapOf<String, Any>(
                                "name" to obj.optString("name", packet.address.hostAddress ?: ""),
                                "ip" to obj.optString("ip", packet.address.hostAddress ?: ""),
                                "port" to obj.optInt("port", 8765),
                                "code" to obj.optString("code", "")
                            )
                            main.post { onFound(info) }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // keep listening
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "beacon recv failed: ${e.message}")
                    }
                }
                s.close()
            } catch (e: Exception) {
                Log.w(TAG, "beacon bind failed: ${e.message}")
            } finally {
                running = false
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
    }
}

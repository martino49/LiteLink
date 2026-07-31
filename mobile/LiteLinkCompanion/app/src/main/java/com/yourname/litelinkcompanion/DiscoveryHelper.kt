package com.yourname.litelinkcompanion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class DiscoveryHelper(
    private val context: Context,
    private val onFound: (String) -> Unit
) {

    companion object {
        const val SERVICE_TYPE = "_litelink._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress
            if (host != null) {
                stop()
                onFound(host)
            }
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, resolveListener)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    fun start() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stop() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            // ignore
        }
    }
}

package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.json.JSONObject

/**
 * Toggles USB / Wi-Fi tethering so the laptop can use the phone's mobile data.
 * Android 12+ hides the tethering APIs from the public SDK, so everything goes
 * through reflection. Expect a SecurityException on non-rooted devices running
 * Android 13+ unless tethering is already permitted.
 */
object TetherModule {

    private const val TAG = "LiteLink.Tether"
    private const val TETHERING_WIFI = 0
    private const val TETHERING_USB = 1

    fun register(app: Context) {
        CommandCenter.register("tether.set") { ctx, id, params ->
            val enabled = params.optBoolean("enabled", true)
            val kind = params.optString("kind", "wifi") // wifi | usb
            val result = if (kind == "usb") {
                setUsbTethering(app, enabled)
            } else {
                setWifiTethering(app, enabled)
            }
            ctx.reply(id, JSONObject().apply {
                put("ok", result)
                put("kind", kind)
                put("enabled", enabled)
            })
        }
        CommandCenter.register("tether.get") { ctx, id, _ ->
            ctx.reply(id, JSONObject().apply {
                put("wifi_hotspot", isWifiTetheringOn(app))
                put("usb_tethering", isUsbTetheringOn(app))
            })
        }
    }

    /** Tethering state for local UI display. */
    fun getState(app: Context): JSONObject {
        return JSONObject().apply {
            put("wifi_hotspot", isWifiTetheringOn(app))
            put("usb_tethering", isUsbTetheringOn(app))
        }
    }

    private fun setWifiTethering(app: Context, enabled: Boolean): Boolean {
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (enabled) {
                val callbackClass = Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
                val proxy = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { _, _, _ -> null }
                val method: Method = ConnectivityManager::class.java.getMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    callbackClass
                )
                method.invoke(cm, TETHERING_WIFI, false, proxy)
            } else {
                val method: Method = ConnectivityManager::class.java.getMethod(
                    "stopTethering", Int::class.javaPrimitiveType
                )
                method.invoke(cm, TETHERING_WIFI)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "wifi tether failed", e)
            false
        }
    }

    private fun setUsbTethering(app: Context, enabled: Boolean): Boolean {
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method: Method = ConnectivityManager::class.java.getMethod("setUsbTethering", Boolean::class.javaPrimitiveType)
            method.invoke(cm, enabled) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "usb tether failed", e)
            false
        }
    }

    private fun isWifiTetheringOn(app: Context): Boolean {
        return try {
            val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method: Method = WifiManager::class.java.getMethod("isWifiApEnabled")
            method.invoke(wm) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    private fun isUsbTetheringOn(app: Context): Boolean {
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method: Method = ConnectivityManager::class.java.getMethod("getTetheredIfaces")
            @Suppress("UNCHECKED_CAST")
            val ifaces = method.invoke(cm) as Array<String>
            ifaces.any { it.contains("rndis") || it.contains("usb") }
        } catch (e: Exception) {
            false
        }
    }
}

package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import org.json.JSONObject

/** Reads cellular signal strength and network info; supports periodic logging. */
object SignalModule {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    fun register(app: Context) {
        CommandCenter.register("signal.get") { ctx, id, _ ->
            ctx.reply(id, readSignal(app))
        }

        CommandCenter.register("signal.subscribe") { ctx, id, params ->
            val intervalMs = (params.optLong("interval_ms", 10000)).coerceIn(2000, 60000)
            stop()
            runnable = object : Runnable {
                override fun run() {
                    ctx.sendEvent("signal.reading", readSignal(app))
                    handler.postDelayed(this, intervalMs)
                }
            }
            handler.post(runnable!!)
            ctx.reply(id, JSONObject().put("status", "subscribed").put("interval_ms", intervalMs))
        }

        CommandCenter.register("signal.unsubscribe") { ctx, id, _ ->
            stop()
            ctx.reply(id, JSONObject().put("status", "unsubscribed"))
        }
    }

    fun stop() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    fun readSignal(app: Context): JSONObject {
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val data = JSONObject()
        data.put("timestamp", System.currentTimeMillis())
        data.put("operator", tm.networkOperatorName ?: "")
        data.put("network_type", networkTypeName(tm.dataNetworkType))
        data.put("data_state", tm.dataState)
        data.put("sim_state", tm.simState)

        val dbms = mutableListOf<Int>()
        val levels = mutableListOf<Int>()
        try {
            val cells = tm.allCellInfo ?: emptyList()
            for (cell in cells) {
                when (cell) {
                    is CellInfoGsm -> {
                        val s = cell.cellSignalStrength
                        dbms.add(s.dbm); levels.add(s.level)
                    }
                    is CellInfoLte -> {
                        val s = cell.cellSignalStrength
                        dbms.add(s.dbm); levels.add(s.level)
                    }
                    is CellInfoWcdma -> {
                        val s = cell.cellSignalStrength
                        dbms.add(s.dbm); levels.add(s.level)
                    }
                    is CellInfoNr -> {
                        val s = cell.cellSignalStrength
                        dbms.add(s.dbm); levels.add(s.level)
                    }
                }
            }
        } catch (e: Exception) {
            // allCellInfo needs location; fall through to getCellSignalStrength
        }
        // cellSignalStrength is @SystemApi on modern SDKs and hidden; allCellInfo above is
        // the public path, and on devices without location permission it simply stays empty.
        data.put("signal_dbm", if (dbms.isEmpty()) JSONObject.NULL else dbms.maxOrNull())
        data.put("signal_level", if (levels.isEmpty()) JSONObject.NULL else levels.maxOrNull())
        return data
    }

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        // NETWORK_TYPE_LTE_CA = 19 is hidden from the public SDK stubs
        19 -> "LTE CA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi"
        else -> "UNKNOWN($type)"
    }
}

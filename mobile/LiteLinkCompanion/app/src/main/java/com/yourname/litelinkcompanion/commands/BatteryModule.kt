package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject

/** Reads battery telemetry via BatteryManager + sticky ACTION_BATTERY_CHANGED. */
object BatteryModule {

    fun register(app: Context) {
        CommandCenter.register("battery.get") { ctx, id, _ ->
            ctx.reply(id, readBattery(app))
        }
    }

    fun readBattery(app: Context): JSONObject {
        val data = JSONObject()
        data.put("level", JSONObject.NULL)
        data.put("temperature_c", JSONObject.NULL)
        data.put("voltage_mv", JSONObject.NULL)
        data.put("charge_cycles", JSONObject.NULL)
        data.put("health_pct", JSONObject.NULL)
        data.put("timestamp", System.currentTimeMillis())

        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) data.put("level", (level * 100) / scale)
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (temp != Int.MIN_VALUE) data.put("temperature_c", temp / 10.0)
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            if (voltage != Int.MIN_VALUE) data.put("voltage_mv", voltage)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            data.put("charging", plugged != 0)
            data.put("plugged", when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            })
            data.put("status", status) // 2=charging 3=discharging 5=full
        }
        return data
    }
}

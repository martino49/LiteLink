package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.os.Build
import org.json.JSONObject

/** Device info, app version, permission state. */
object SystemModule {

    private const val VERSION = "1.0"

    fun register(app: Context) {
        CommandCenter.register("system.info") { ctx, id, _ ->
            val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            ctx.reply(id, JSONObject().apply {
                put("app_version", VERSION)
                put("android", Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("device", Build.DEVICE)
                put("pairing_code", prefs.getString("pairing_code", "").orEmpty())
                put("host", prefs.getString("host", "").orEmpty())
                put("connected", true)
            })
        }
    }
}

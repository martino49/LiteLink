package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persisted battery samples powering the Battery Health history (Screen 9). */
object BatteryHistory {

    private const val PREFS = "litelink_battery"
    private const val KEY = "samples"
    private const val MAX = 500

    fun add(context: Context, level: Int, tempC: Double, voltageMv: Int) {
        val samples = load(context).toMutableList()
        samples.add(JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("level", level)
            put("temp", tempC)
            put("volt", voltageMv)
        })
        val kept = samples.takeLast(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, JSONArray(kept).toString()).apply()
    }

    fun load(context: Context): List<JSONObject> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

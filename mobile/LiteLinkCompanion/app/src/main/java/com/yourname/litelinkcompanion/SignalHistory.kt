package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persisted signal samples powering the Signal Logger history (Screen 11). */
object SignalHistory {

    private const val PREFS = "litelink_signal"
    private const val KEY = "samples"
    private const val MAX = 2000

    fun add(context: Context, dbm: Int, level: Int, network: String, operator: String) {
        val samples = load(context).toMutableList()
        samples.add(JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("dbm", dbm)
            put("level", level)
            put("network", network)
            put("operator", operator)
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

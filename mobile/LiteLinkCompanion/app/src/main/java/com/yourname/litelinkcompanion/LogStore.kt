package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted debug log powering the Log Viewer screen (Screen 16).
 * Entries carry a level (INFO / WARN / ERROR), timestamp, and message.
 */
object LogStore {

    private const val PREFS = "litelink_logs"
    private const val KEY_ITEMS = "items"
    private const val KEY_AUTO_REFRESH = "auto_refresh"
    private const val MAX = 500

    const val LEVEL_INFO = "INFO"
    const val LEVEL_WARN = "WARN"
    const val LEVEL_ERROR = "ERROR"

    fun log(context: Context, level: String, message: String) {
        val items = load(context)
        val entry = JSONObject()
            .put("level", level)
            .put("message", message)
            .put("time", System.currentTimeMillis())
        val list = (items + entry).takeLast(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, JSONArray(list).toString()).apply()
    }

    fun list(
        context: Context,
        level: String = "",
        query: String = ""
    ): List<JSONObject> {
        return load(context).filter { entry ->
            val matchesLevel = level.isBlank() || entry.optString("level") == level
            val msg = entry.optString("message")
            val matchesQuery = query.isBlank() || msg.contains(query, ignoreCase = true)
            matchesLevel && matchesQuery
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ITEMS).apply()
    }

    fun export(context: Context): JSONArray = JSONArray(load(context))

    fun autoRefresh(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_REFRESH, true)

    fun setAutoRefresh(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    fun levels(): List<String> = listOf(LEVEL_INFO, LEVEL_WARN, LEVEL_ERROR)

    private fun load(context: Context): List<JSONObject> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

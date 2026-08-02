package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persisted capture of phone notifications powering the Notifications screen (Screen 6). */
object NotificationStore {

    private const val PREFS = "litelink_notifications"
    private const val KEY_ITEMS = "items"
    private const val MAX = 200

    fun add(context: Context, app: String, title: String, text: String) {
        val items = load(context)
        val now = System.currentTimeMillis()
        val item = JSONObject()
            .put("app", app)
            .put("title", title)
            .put("text", text)
            .put("time", now)
        val list = (items + item).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, JSONArray(list).toString()).apply()
    }

    fun list(context: Context, query: String = "", appFilter: String = ""): List<JSONObject> {
        return load(context).filter { item ->
            val matchesQuery = query.isBlank() ||
                item.optString("title").contains(query, ignoreCase = true) ||
                item.optString("text").contains(query, ignoreCase = true) ||
                item.optString("app").contains(query, ignoreCase = true)
            val matchesApp = appFilter.isBlank() || item.optString("app") == appFilter
            matchesQuery && matchesApp
        }
    }

    fun apps(context: Context): List<String> {
        return list(context, "", "")
            .map { it.optString("app") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ITEMS).apply()
    }

    fun export(context: Context): JSONArray = JSONArray(load(context))

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

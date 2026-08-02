package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-app activity log that powers the Home dashboard's stats and recent-activity
 * feed. Persisted in SharedPreferences so counts survive process death.
 */
object ActivityLog {

    private const val PREFS = "litelink_activity"
    private const val KEY_EVENTS = "events"
    private const val KEY_DAY = "day"
    private const val KEY_NOTIFS = "notifs"
    private const val KEY_SYNCS = "syncs"
    private const val MAX_EVENTS = 50

    fun log(context: Context, icon: String, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = loadEvents(context)
        val now = System.currentTimeMillis()
        val event = JSONObject()
            .put("icon", icon)
            .put("text", text)
            .put("time", now)
        val list = (listOf(event) + events).take(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, JSONArray(list).toString()).apply()
    }

    fun bumpNotifications(context: Context) {
        bump(context, KEY_NOTIFS)
    }

    fun bumpSyncs(context: Context) {
        bump(context, KEY_SYNCS)
    }

    fun notificationsToday(context: Context): Int = dailyCount(context, KEY_NOTIFS)

    fun syncsToday(context: Context): Int = dailyCount(context, KEY_SYNCS)

    fun recent(context: Context, limit: Int = 5): List<JSONObject> {
        return loadEvents(context).take(limit)
    }

    private fun bump(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        val today = prefs.getString(KEY_DAY, "") == day
        val current = if (today) prefs.getInt(key, 0) else 0
        prefs.edit()
            .putString(KEY_DAY, day)
            .putInt(key, current + 1)
            .apply()
    }

    private fun dailyCount(context: Context, key: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        if (prefs.getString(KEY_DAY, "") != day) return 0
        return prefs.getInt(key, 0)
    }

    private fun loadEvents(context: Context): List<JSONObject> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

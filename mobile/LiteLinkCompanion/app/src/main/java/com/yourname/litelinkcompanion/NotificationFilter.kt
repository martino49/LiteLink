package com.yourname.litelinkcompanion

import android.content.Context
import org.json.JSONArray

/**
 * Notification filter settings (Screen 13). Which apps may be mirrored to the
 * desktop, whether full text is shown, and whether system notifications are blocked.
 */
object NotificationFilter {

    private const val PREFS = "litelink_notif_filter"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FULL_TEXT = "full_text"
    private const val KEY_BLOCK_SYSTEM = "block_system"
    private const val KEY_BLOCKED = "blocked_apps"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun fullText(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FULL_TEXT, true)

    fun setFullText(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FULL_TEXT, value).apply()
    }

    fun blockSystem(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BLOCK_SYSTEM, false)

    fun setBlockSystem(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLOCK_SYSTEM, value).apply()
    }

    fun blockedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BLOCKED, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setBlockedApps(context: Context, apps: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BLOCKED, JSONArray(apps.toList()).toString()).apply()
    }

    fun isBlocked(context: Context, appLabel: String, isSystemApp: Boolean): Boolean {
        if (blockSystem(context) && isSystemApp) return true
        return blockedApps(context).contains(appLabel)
    }

    fun appCount(context: Context): Int = blockedApps(context).size
}

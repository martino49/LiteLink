package com.yourname.litelinkcompanion.commands

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/** Diagnostics, cache cleanup, and resource reporting. */
object MaintenanceModule {

    fun register(app: Context) {
        CommandCenter.register("maint.diagnostics") { ctx, id, _ ->
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mem)

            val stat = StatFs(Environment.getDataDirectory().path)
            val internalFree = stat.availableBytes
            val internalTotal = stat.totalBytes

            val extState = Environment.getExternalStorageState()
            val ext = if (extState == Environment.MEDIA_MOUNTED) {
                val s = StatFs(Environment.getExternalStorageDirectory().path)
                JSONObject().put("free", s.availableBytes).put("total", s.totalBytes)
            } else {
                JSONObject().put("state", extState)
            }

            ctx.reply(id, JSONObject().apply {
                put("uptime_ms", SystemClock.elapsedRealtime())
                put("total_mem", mem.totalMem)
                put("avail_mem", mem.availMem)
                put("low_memory", mem.lowMemory)
                put("internal_free", internalFree)
                put("internal_total", internalTotal)
                put("external", ext)
                put("cpu_cores", Runtime.getRuntime().availableProcessors())
            })
        }

        CommandCenter.register("maint.clear_cache") { ctx, id, _ ->
            val cache = app.cacheDir
            val files = cache.listFiles() ?: emptyArray()
            var freed = 0L
            var deleted = 0
            for (f in files) {
                freed += deleteRecursive(f)
                deleted++
            }
            ctx.reply(id, JSONObject().apply {
                put("files", deleted)
                put("freed", freed)
            })
        }

        CommandCenter.register("maint.free_space") { ctx, id, _ ->
            val stat = StatFs(Environment.getDataDirectory().path)
            ctx.reply(id, JSONObject().apply {
                put("free", stat.availableBytes)
                put("total", stat.totalBytes)
            })
        }
    }

    /** Diagnostics for local UI display. */
    fun diagnostics(app: Context): JSONObject {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val stat = StatFs(Environment.getDataDirectory().path)
        return JSONObject().apply {
            put("uptime_ms", SystemClock.elapsedRealtime())
            put("total_mem", mem.totalMem)
            put("avail_mem", mem.availMem)
            put("low_memory", mem.lowMemory)
            put("internal_free", stat.availableBytes)
            put("internal_total", stat.totalBytes)
            put("cpu_cores", Runtime.getRuntime().availableProcessors())
        }
    }

    /** Clears the app cache dir; returns bytes freed. */
    fun clearCache(app: Context): Long {
        var freed = 0L
        app.cacheDir.listFiles()?.forEach { freed += deleteRecursive(it) }
        return freed
    }

    private fun deleteRecursive(f: File): Long {
        var size = 0L
        if (f.isDirectory) {
            f.listFiles()?.forEach { size += deleteRecursive(it) }
        } else {
            size = f.length()
        }
        f.delete()
        return size
    }
}

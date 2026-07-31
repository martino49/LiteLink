package com.yourname.litelinkcompanion.commands

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** List and mirror phone folders (DCIM, Downloads, custom paths) to the laptop. */
object FolderSyncModule {

    fun register(app: Context) {
        CommandCenter.register("folder.list") { ctx, id, params ->
            val path = params.optString("path")
            if (path.isBlank()) {
                ctx.fail(id, "path required")
                return@register
            }
            val entries = JSONArray()
            walk(File(path), params.optInt("depth", 3), entries)
            ctx.reply(id, JSONObject().apply {
                put("path", path)
                put("entries", entries)
            })
        }

        CommandCenter.register("folder.push") { ctx, id, params ->
            val path = params.optString("path")
            if (path.isBlank()) {
                ctx.fail(id, "path required")
                return@register
            }
            val root = File(path)
            if (!root.exists() || !root.isDirectory) {
                ctx.fail(id, "not a directory: $path")
                return@register
            }
            val files = mutableListOf<File>()
            collectFiles(root, files, 100)
            var uploaded = 0
            var failed = 0
            for (f in files) {
                val rel = root.toURI().relativize(f.toURI()).path
                val ok = FileTransferModule.uploadFile(app, f.absolutePath, rel, "folder")
                if (ok) uploaded++ else failed++
            }
            ctx.reply(id, JSONObject().apply {
                put("scanned", files.size)
                put("uploaded", uploaded)
                put("failed", failed)
            })
        }

        CommandCenter.register("folder.sync") { ctx, id, params ->
            // Mirror-style sync: push any files newer than last sync.
            val path = params.optString("path")
            if (path.isBlank()) {
                ctx.fail(id, "path required")
                return@register
            }
            val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            val key = "folder_cursor_${path.hashCode()}"
            val cursor = prefs.getLong(key, 0L)
            val root = File(path)
            val files = mutableListOf<File>()
            if (root.exists() && root.isDirectory) {
                collectFilesSince(root, files, cursor, 100)
            }
            var uploaded = 0
            for (f in files) {
                val rel = root.toURI().relativize(f.toURI()).path
                if (FileTransferModule.uploadFile(app, f.absolutePath, rel, "folder")) uploaded++
            }
            val newCursor = System.currentTimeMillis()
            prefs.edit().putLong(key, newCursor).apply()
            ctx.reply(id, JSONObject().apply {
                put("uploaded", uploaded)
                put("cursor", newCursor)
            })
        }
    }

    private fun walk(dir: File, depth: Int, out: JSONArray) {
        if (depth <= 0 || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            out.put(JSONObject().apply {
                put("name", f.name)
                put("path", f.absolutePath)
                put("is_dir", f.isDirectory)
                put("size", if (f.isFile) f.length() else 0)
                put("last_modified", f.lastModified())
            })
            if (f.isDirectory) walk(f, depth - 1, out)
        }
    }

    private fun collectFiles(dir: File, out: MutableList<File>, max: Int) {
        if (out.size >= max || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (out.size >= max) return
            if (f.isFile) out.add(f) else collectFiles(f, out, max)
        }
    }

    private fun collectFilesSince(dir: File, out: MutableList<File>, since: Long, max: Int) {
        if (out.size >= max || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (out.size >= max) return
            if (f.isFile) {
                if (f.lastModified() > since) out.add(f)
            } else {
                collectFilesSince(f, out, since, max)
            }
        }
    }
}

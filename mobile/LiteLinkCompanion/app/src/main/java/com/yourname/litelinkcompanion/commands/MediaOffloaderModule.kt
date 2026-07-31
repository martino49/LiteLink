package com.yourname.litelinkcompanion.commands

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Offload photos/videos from MediaStore to the laptop, tracking a sync cursor. */
object MediaOffloaderModule {

    fun register(app: Context) {
        CommandCenter.register("media.list") { ctx, id, params ->
            val items = JSONArray()
            queryMedia(app, params.optLong("since", 0L), items)
            ctx.reply(id, JSONObject().apply {
                put("count", items.length())
                put("items", items)
            })
        }

        CommandCenter.register("media.offload") { ctx, id, params ->
            val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            val key = "media_cursor"
            val cursor = prefs.getLong(key, 0L)
            val since = params.optLong("since", cursor)
            val items = JSONArray()
            queryMedia(app, since, items)

            var uploaded = 0
            var failed = 0
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val real = materialize(app, item)
                if (real == null) {
                    failed++
                    continue
                }
                if (FileTransferModule.uploadFile(app, real, item.optString("name"), "media")) {
                    uploaded++
                } else {
                    failed++
                }
            }
            val newCursor = System.currentTimeMillis()
            prefs.edit().putLong(key, newCursor).apply()
            ctx.reply(id, JSONObject().apply {
                put("scanned", items.length())
                put("uploaded", uploaded)
                put("failed", failed)
                put("cursor", newCursor)
            })
        }
    }

    /** Scanned media items for local UI display. */
    fun scanMedia(app: Context, since: Long = 0L): JSONArray {
        val out = JSONArray()
        queryMedia(app, since, out)
        return out
    }

    private fun queryMedia(app: Context, since: Long, out: JSONArray) {
        val sources = arrayOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to MediaStore.Video.Media.DATE_ADDED
        )
        for ((uri, dateCol) in sources) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                dateCol
            )
            val cursor: Cursor? = try {
                app.contentResolver.query(uri, projection, "$dateCol > ?", arrayOf((since / 1000).toString()), "$dateCol ASC")
            } catch (e: Exception) {
                null
            }
            cursor?.use {
                val idIdx = it.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = it.getColumnIndex(dateCol)
                while (it.moveToNext()) {
                    out.put(JSONObject().apply {
                        put("id", it.getLong(idIdx))
                        put("uri", ContentUris.withAppendedId(uri, it.getLong(idIdx)).toString())
                        put("name", it.getString(nameIdx))
                        put("size", it.getLong(sizeIdx))
                        put("date_added", it.getLong(dateIdx) * 1000L)
                    })
                }
            }
        }
    }

    /** Returns a real readable file path for a media item (copying via content URI if needed). */
    private fun materialize(app: Context, item: JSONObject): String? {
        val name = item.optString("name")
        val size = item.optLong("size", 0L)
        val uri = Uri.parse(item.optString("uri"))
        val tmp = File(app.cacheDir, "media")
        tmp.mkdirs()
        val outFile = File(tmp, name)
        return try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (outFile.length() == size || size == 0L) outFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }
}

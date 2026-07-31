package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.util.Log
import com.yourname.litelinkcompanion.WebSocketManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Bidirectional file transfer: pull files from the phone, save pushed files. */
object FileTransferModule {

    private const val TAG = "LiteLink.File"
    private const val CHUNK = 60 * 1024

    private class Incoming(
        val file: File,
        val stream: FileOutputStream,
        var size: Long,
        var received: Long,
        val dir: String,
        val name: String
    )

    private val incoming = ConcurrentHashMap<String, Incoming>()

    fun register() {
        val app = CommandCenter.getContext() ?: return

        CommandCenter.register("file.get") { ctx, id, params ->
            val path = params.optString("path")
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                ctx.fail(id, "not a file: $path")
                return@register
            }
            val dir = params.optString("dir")
            ctx.uploadFile(file.absolutePath, file.name, dir)
            ctx.reply(id, JSONObject().put("status", "uploading").put("size", file.length()))
        }

        CommandCenter.register("file.info") { ctx, id, params ->
            val path = params.optString("path")
            val file = File(path)
            if (!file.exists()) {
                ctx.fail(id, "not found: $path")
                return@register
            }
            ctx.reply(id, JSONObject().apply {
                put("path", file.absolutePath)
                put("exists", true)
                put("is_dir", file.isDirectory)
                put("size", if (file.isFile) file.length() else 0)
                put("last_modified", file.lastModified())
            })
        }

        CommandCenter.register("file.dir") { ctx, id, params ->
            val path = params.optString("path")
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                ctx.fail(id, "not a directory: $path")
                return@register
            }
            val entries = JSONArray()
            dir.listFiles()?.forEach { f ->
                entries.put(JSONObject().apply {
                    put("name", f.name)
                    put("path", f.absolutePath)
                    put("is_dir", f.isDirectory)
                    put("size", if (f.isFile) f.length() else 0)
                    put("last_modified", f.lastModified())
                })
            }
            ctx.reply(id, JSONObject().apply {
                put("path", dir.absolutePath)
                put("entries", entries)
            })
        }

        // Wire desktop -> phone pushes into incremental file writes.
        WebSocketManager.onFileStart = { id, name, size, dir, _ ->
            try {
                val root = File(app.getExternalFilesDir(null), "inbox").apply { mkdirs() }
                val target = if (dir.isBlank()) root else File(root, dir)
                target.mkdirs()
                val file = File(target, File(name).name)
                incoming[id] = Incoming(file, FileOutputStream(file), size, 0, dir, name)
            } catch (e: Exception) {
                Log.e(TAG, "file start failed", e)
            }
        }
        WebSocketManager.onFileChunk = { id, _offset, bytes ->
            incoming[id]?.let { inc ->
                try {
                    inc.stream.write(bytes)
                    inc.received += bytes.size
                } catch (e: Exception) {
                    Log.e(TAG, "file chunk failed", e)
                }
            }
        }
        WebSocketManager.onFileDone = { id ->
            val inc = incoming.remove(id)
            if (inc != null) {
                try {
                    inc.stream.flush()
                    inc.stream.close()
                    val ok = inc.received >= inc.size
                    if (ok && inc.dir == "install") {
                        ApkModule.installApk(app, inc.file)
                    }
                    CommandBridge.sendEvent("file.received", JSONObject().apply {
                        put("name", inc.name)
                        put("dir", inc.dir)
                        put("size", inc.received)
                        put("ok", ok)
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "file done failed", e)
                }
            }
        }
    }

    /** Uploads a local file in base64 chunks. Returns true on success. */
    fun uploadFile(app: Context, path: String, name: String = "", dir: String = ""): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isFile) return false
        val display = name.ifBlank { file.name }
        try {
            val id = WebSocketManager.sendFileStart(display, file.length(), dir)
            file.inputStream().use { input ->
                val buffer = ByteArray(CHUNK)
                var offset = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    val chunk = if (n == buffer.size) buffer else buffer.copyOf(n)
                    WebSocketManager.sendFileChunk(id, offset, chunk)
                    offset += n
                }
            }
            WebSocketManager.sendFileDone(id, true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "upload failed: $path", e)
            return false
        }
    }
}

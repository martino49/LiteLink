package com.yourname.litelinkcompanion.commands

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONArray
import org.json.JSONObject

/** List installed apps, extract APKs to the laptop, install received APKs. */
object ApkModule {

    private const val TAG = "LiteLink.Apk"

    fun register(app: Context) {
        CommandCenter.register("apk.list") { ctx, id, _ ->
            val apps = mutableListOf<JSONObject>()
            val pm = app.packageManager
            val installed = pm.getInstalledApplications(0)
            for (ai in installed) {
                try {
                    val label = pm.getApplicationLabel(ai).toString()
                    val version = pm.getPackageInfo(ai.packageName, 0).versionName ?: ""
                    val apk = File(ai.publicSourceDir)
                    apps.add(JSONObject().apply {
                        put("package", ai.packageName)
                        put("label", label)
                        put("version", version)
                        put("size", apk.length())
                        put("system", (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    })
                } catch (e: Exception) {
                }
            }
            ctx.reply(id, JSONObject().put("apps", JSONArray(apps)))
        }

        CommandCenter.register("apk.extract") { ctx, id, params ->
            val pkg = params.optString("package")
            if (pkg.isEmpty()) {
                ctx.fail(id, "missing package")
                return@register
            }
            try {
                val pm = app.packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                val src = File(ai.publicSourceDir)
                val out = File(app.cacheDir, "${pkg.replace('.', '_')}.apk")
                src.copyTo(out, overwrite = true)
                ctx.uploadFile(out.absolutePath, "${pkg}.apk", "apks")
                ctx.reply(id, JSONObject().put("status", "uploading").put("size", out.length()))
            } catch (e: Exception) {
                Log.e(TAG, "extract failed", e)
                ctx.fail(id, e.message ?: "extract failed")
            }
        }

        CommandCenter.register("apk.install") { ctx, id, params ->
            val path = params.optString("path")
            if (path.isEmpty()) {
                ctx.fail(id, "missing path")
                return@register
            }
            val file = File(path)
            if (!file.exists()) {
                ctx.fail(id, "file not found: $path")
                return@register
            }
            try {
                installApk(app, file)
                ctx.reply(id, JSONObject().put("status", "installing").put("path", path))
            } catch (e: Exception) {
                Log.e(TAG, "install failed", e)
                ctx.fail(id, e.message ?: "install failed")
            }
        }
    }

    /** Installed apps as JSON for local UI display. */
    fun listApps(app: Context): JSONArray {
        val apps = JSONArray()
        val pm = app.packageManager
        for (ai in pm.getInstalledApplications(0)) {
            try {
                val label = pm.getApplicationLabel(ai).toString()
                val version = pm.getPackageInfo(ai.packageName, 0).versionName ?: ""
                apps.put(JSONObject().apply {
                    put("package", ai.packageName)
                    put("label", label)
                    put("version", version)
                    put("system", (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                })
            } catch (e: Exception) {
            }
        }
        return apps
    }

    /** Kick off a PackageInstaller session for the given APK (user confirms the prompt). */
    fun installApk(app: Context, apk: File) {
        val pm = app.packageManager
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val size = apk.length()
            val out: OutputStream = session.openWrite("base.apk", 0, size)
            apk.inputStream().use { it.copyTo(out) }
            session.fsync(out)
            out.close()

            val intent = Intent(app, InstallReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                app, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
        } finally {
            session.close()
        }
    }

    /** Direct install for received files via content resolver (used by FileTransfer for dir=install). */
    fun installFromUri(app: Context, uri: Uri) {
        try {
            val pfd = app.contentResolver.openFileDescriptor(uri, "r") ?: return
            val file = File(app.cacheDir, "incoming_${System.currentTimeMillis()}.apk")
            FileOutputStream(file).use { fos ->
                java.io.FileInputStream(pfd.fileDescriptor).use { it.copyTo(fos) }
            }
            pfd.close()
            installApk(app, file)
        } catch (e: Exception) {
            Log.e(TAG, "installFromUri failed", e)
        }
    }
}

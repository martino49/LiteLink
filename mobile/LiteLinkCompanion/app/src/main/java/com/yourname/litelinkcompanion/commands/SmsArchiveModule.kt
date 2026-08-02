package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** One-click backup of SMS + call history to CSV files uploaded to the laptop. */
object SmsArchiveModule {

    private const val TAG = "LiteLink.Sms"
    private const val CHUNK = 512

    fun register(app: Context) {
        CommandCenter.register("sms.backup") { ctx, id, _ ->
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ctx.fail(id, "READ_SMS permission not granted")
                return@register
            }
            try {
                val lines = readSms(app)
                val file = writeCsv(app, "sms_backup", lines)
                ctx.uploadFile(file.absolutePath, file.name, "backups")
                ctx.reply(id, JSONObject().apply {
                    put("rows", lines.size - 1)
                    put("file", file.name)
                })
            } catch (e: Exception) {
                Log.e(TAG, "sms backup failed", e)
                ctx.fail(id, e.message ?: "sms backup failed")
            }
        }

        CommandCenter.register("calls.backup") { ctx, id, _ ->
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ctx.fail(id, "READ_CALL_LOG permission not granted")
                return@register
            }
            try {
                val lines = readCalls(app)
                val file = writeCsv(app, "calls_backup", lines)
                ctx.uploadFile(file.absolutePath, file.name, "backups")
                ctx.reply(id, JSONObject().apply {
                    put("rows", lines.size - 1)
                    put("file", file.name)
                })
            } catch (e: Exception) {
                Log.e(TAG, "calls backup failed", e)
                ctx.fail(id, e.message ?: "calls backup failed")
            }
        }
    }

    /** Recent SMS as JSON for local UI display (empty if permission missing). */
    fun listRecentSms(app: Context, limit: Int = 50): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return out
        }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val cursor: Cursor? = app.contentResolver.query(
            Uri.parse("content://sms/inbox"), null, null, null, "date DESC LIMIT $limit"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val date = it.getLong(it.getColumnIndexOrThrow("date"))
                out.add(JSONObject().apply {
                    put("date", ts.format(Date(date)))
                    put("address", it.getString(it.getColumnIndexOrThrow("address")).orEmpty())
                    put("person", it.getString(it.getColumnIndexOrThrow("person")) ?: "")
                    put("body", it.getString(it.getColumnIndexOrThrow("body")).orEmpty())
                })
            }
        }
        return out
    }

    /** Recent call log entries as JSON for local UI display (empty if permission missing). */
    fun listRecentCalls(app: Context, limit: Int = 50): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return out
        }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val cursor: Cursor? = app.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                out.add(JSONObject().apply {
                    put("date", ts.format(Date(date)))
                    put("number", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).orEmpty())
                    put("name", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: "")
                    put("type", type)
                    put("duration", it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION)))
                })
            }
        }
        return out
    }

    private fun readSms(app: Context): List<String> {
        val header = "date,address,person,body,type"
        val lines = mutableListOf(header)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (box in arrayOf("inbox", "sent")) {
            val uri = Uri.parse("content://sms/$box")
            val cursor: Cursor? = app.contentResolver.query(uri, null, null, null, "date DESC")
            cursor?.use {
                while (it.moveToNext()) {
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    val address = it.getString(it.getColumnIndexOrThrow("address")).orEmpty()
                    val person = it.getString(it.getColumnIndexOrThrow("person")) ?: ""
                    val body = it.getString(it.getColumnIndexOrThrow("body")).orEmpty()
                        .replace("\n", "\\n").replace("\"", "'")
                    val type = it.getInt(it.getColumnIndexOrThrow("type"))
                    lines.add("\"${ts.format(Date(date))}\",\"${csv(address)}\",\"${csv(person)}\",\"${csv(body)}\",$type")
                }
            }
        }
        return lines
    }

    private fun readCalls(app: Context): List<String> {
        val header = "date,number,type,duration,name"
        val lines = mutableListOf(header)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val cursor: Cursor? = app.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, "${CallLog.Calls.DATE} DESC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).orEmpty()
                val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val name = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: ""
                lines.add("\"${ts.format(Date(date))}\",\"${csv(number)}\",$type,$duration,\"${csv(name)}\"")
            }
        }
        return lines
    }

    private fun writeCsv(app: Context, prefix: String, lines: List<String>): File {
        val file = File(app.cacheDir, "${prefix}_${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            for (line in lines) writer.write(line + "\n")
        }
        return file
    }

    private fun csv(s: String): String = s.replace("\"", "'")
}

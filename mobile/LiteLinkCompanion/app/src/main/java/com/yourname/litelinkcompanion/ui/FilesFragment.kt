package com.yourname.litelinkcompanion.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.WebSocketManager
import com.yourname.litelinkcompanion.commands.LocalExecutor
import org.json.JSONObject

/** Backups, media offload, WhatsApp, and APK tools. */
class FilesFragment : Fragment() {

    private var logView: TextView? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logView = view.findViewById(R.id.filesLog)
        val app = requireContext().applicationContext

        view.findViewById<Button>(R.id.smsBackupButton).setOnClickListener {
            runTransfer("sms.backup")
        }
        view.findViewById<Button>(R.id.callsBackupButton).setOnClickListener {
            runTransfer("calls.backup")
        }
        view.findViewById<Button>(R.id.mediaOffloadButton).setOnClickListener {
            runTransfer("media.offload", JSONObject())
        }
        view.findViewById<Button>(R.id.whatsappBackupButton).setOnClickListener {
            runTransfer("whatsapp.backup", JSONObject().put("include_media", true))
        }

        view.findViewById<Button>(R.id.apkListButton).setOnClickListener {
            LocalExecutor.run(app, "apk.list") { ok, data ->
                main.post {
                    if (ok) {
                        val count = data.optJSONArray("apps")?.length() ?: 0
                        log("$count apps installed")
                    } else {
                        log(data.optString("error", "list failed"))
                    }
                }
            }
        }

        val pkgInput = view.findViewById<EditText>(R.id.apkPackageInput)
        view.findViewById<Button>(R.id.apkExtractButton).setOnClickListener {
            val pkg = pkgInput.text.toString().trim()
            if (pkg.isEmpty()) {
                toast("Enter a package name")
                return@setOnClickListener
            }
            runTransfer("apk.extract", JSONObject().put("package", pkg))
        }
    }

    private fun runTransfer(cmd: String, params: JSONObject = JSONObject()) {
        if (!WebSocketManager.isConnected) {
            toast("Connect to the laptop first")
            return
        }
        log("Running $cmd...")
        LocalExecutor.run(requireContext().applicationContext, cmd, params) { ok, data ->
            main.post {
                if (ok) {
                    val summary = mutableListOf<String>()
                    for (k in arrayOf("rows", "uploaded", "scanned", "failed", "file", "status", "size", "count")) {
                        if (!data.isNull(k)) summary.add("$k=${data.opt(k)}")
                    }
                    log("$cmd done: ${summary.joinToString("  ")}")
                } else {
                    log("$cmd failed: ${data.optString("error")}")
                }
            }
        }
    }

    private fun log(text: String) {
        logView?.text = text
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}

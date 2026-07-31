package com.yourname.litelinkcompanion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.yourname.litelinkcompanion.commands.UssdModule
import org.json.JSONObject

/** USSD, tethering, clipboard, camera, and maintenance tools. */
class ToolsFragment : Fragment() {

    private var logView: TextView? = null
    private var ussdResultView: TextView? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logView = view.findViewById(R.id.toolsLog)
        ussdResultView = view.findViewById(R.id.ussdResult)
        val app = requireContext().applicationContext

        val ussdInput = view.findViewById<EditText>(R.id.ussdInput)
        view.findViewById<Button>(R.id.ussdSendButton).setOnClickListener {
            val code = ussdInput.text.toString().trim()
            if (code.isEmpty()) {
                toast("Enter a USSD code")
                return@setOnClickListener
            }
            ussdResultView?.text = "Sending $code..."
            UssdModule.send(app, code) { ok, data ->
                main.post {
                    if (ok) {
                        ussdResultView?.text = data.optString("response", "(no response)")
                    } else {
                        ussdResultView?.text = "Failed: ${data.optString("error")}"
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.wifiTetherButton).setOnClickListener {
            LocalExecutor.run(app, "tether.get") { _, state ->
                val target = !state.optBoolean("wifi_hotspot", false)
                LocalExecutor.run(app, "tether.set", JSONObject().put("enabled", target).put("kind", "wifi")) { ok, data ->
                    main.post {
                        toast(if (ok) "Hotspot ${if (target) "on" else "off"}" else "Hotspot toggle failed")
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.tetherStatusButton).setOnClickListener {
            LocalExecutor.run(app, "tether.get") { _, data ->
                main.post {
                    log("Wi-Fi hotspot: ${data.optBoolean("wifi_hotspot")}\nUSB tethering: ${data.optBoolean("usb_tethering")}")
                }
            }
        }

        val clipboardInput = view.findViewById<EditText>(R.id.clipboardInput)
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        view.findViewById<Button>(R.id.clipboardSetButton).setOnClickListener {
            val text = clipboardInput.text.toString()
            cm.setPrimaryClip(ClipData.newPlainText("litelink", text))
            toast("Copied to clipboard")
        }
        view.findViewById<Button>(R.id.clipboardGetButton).setOnClickListener {
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() ?: "(empty)"
            clipboardInput.setText(text)
            toast("Clipboard read")
        }

        view.findViewById<Button>(R.id.cameraButton).setOnClickListener {
            if (!WebSocketManager.isConnected) {
                toast("Connect to the laptop to receive the photo")
                return@setOnClickListener
            }
            LocalExecutor.run(app, "camera.capture") { ok, data ->
                main.post {
                    toast(if (ok) "Photo captured" else "Capture failed: ${data.optString("error")}")
                }
            }
        }

        view.findViewById<Button>(R.id.diagnosticsButton).setOnClickListener {
            LocalExecutor.run(app, "maint.diagnostics") { ok, data ->
                main.post {
                    if (ok) {
                        log(
                            "RAM: ${data.optLong("avail_mem") / 1048576} MB free\n" +
                                "Storage: ${data.optLong("internal_free") / 1048576} MB free\n" +
                                "CPU cores: ${data.optInt("cpu_cores")}\n" +
                                "Uptime: ${data.optLong("uptime_ms") / 60000} min"
                        )
                    } else {
                        log(data.optString("error", "diagnostics failed"))
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.clearCacheButton).setOnClickListener {
            LocalExecutor.run(app, "maint.clear_cache") { ok, data ->
                main.post {
                    if (ok) {
                        toast("Cache cleared (${data.optLong("freed") / 1024} KB)")
                    } else {
                        toast("Failed: ${data.optString("error")}")
                    }
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

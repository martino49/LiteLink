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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.WebSocketManager
import com.yourname.litelinkcompanion.commands.LocalExecutor
import org.json.JSONObject

/** Files: PC storage browser + phone backups, media, and APK tools. */
class FilesFragment : Fragment() {

    private var logView: TextView? = null
    private var pcStatus: TextView? = null
    private var pcAdapter: PcStorageAdapter? = null
    private var pcHistory = mutableListOf<String>()
    private var pcCurrent = ""
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
        pcStatus = view.findViewById(R.id.pcStorageStatus)
        val app = requireContext().applicationContext

        // ---- PC storage browser
        val list = view.findViewById<RecyclerView>(R.id.pcStorageList)
        list.layoutManager = LinearLayoutManager(requireContext())
        pcAdapter = PcStorageAdapter { entry -> onPcTap(entry) }
        list.adapter = pcAdapter

        val pathInput = view.findViewById<EditText>(R.id.pcPathInput)
        pathInput.setText("C:\\Users")
        view.findViewById<Button>(R.id.pcGoButton).setOnClickListener {
            browsePc(pathInput.text.toString().trim())
        }
        pathInput.setOnEditorActionListener { _, _, _ ->
            browsePc(pathInput.text.toString().trim()); true
        }
        // Back navigation (Android back key / on-screen back)
        view.findViewById<Button>(R.id.pcBackButton)?.setOnClickListener { pcBack() }

        WebSocketManager.onPcResult = { cmd, ok, data ->
            main.post {
                when (cmd) {
                    "files.list" -> onPcList(ok, data)
                    "files.get" -> pcStatus?.text = if (ok) "Downloading ${data.optString("path")}…" else "Failed: ${data.optString("error")}"
                }
            }
        }

        // ---- phone backups
        view.findViewById<Button>(R.id.smsBackupButton).setOnClickListener { runTransfer("sms.backup") }
        view.findViewById<Button>(R.id.callsBackupButton).setOnClickListener { runTransfer("calls.backup") }
        view.findViewById<Button>(R.id.mediaOffloadButton).setOnClickListener { runTransfer("media.offload", JSONObject()) }
        view.findViewById<Button>(R.id.whatsappBackupButton).setOnClickListener {
            runTransfer("whatsapp.backup", JSONObject().put("include_media", true))
        }

        // ---- APK
        view.findViewById<Button>(R.id.apkListButton).setOnClickListener { listApps() }
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

    private fun browsePc(path: String) {
        if (path.isEmpty()) return
        if (!WebSocketManager.isConnected) {
            pcStatus?.text = "Not connected"
            return
        }
        if (pcCurrent.isNotEmpty() && path != pcCurrent) pcHistory.add(pcCurrent)
        pcCurrent = path
        pcStatus?.text = "Loading $path…"
        pcAdapter?.submit(emptyList())
        WebSocketManager.sendPc("files.list", JSONObject().put("path", path))
    }

    private fun pcBack() {
        if (pcHistory.isNotEmpty()) {
            val prev = pcHistory.removeAt(pcHistory.size - 1)
            pcCurrent = prev
            pcStatus?.text = "Loading $prev…"
            pcAdapter?.submit(emptyList())
            WebSocketManager.sendPc("files.list", JSONObject().put("path", prev))
        } else {
            toast("At the top level")
        }
    }

    private fun onPcList(ok: Boolean, data: JSONObject) {
        if (!ok) {
            pcStatus?.text = "Failed: ${data.optString("error")}"
            return
        }
        val arr = data.optJSONArray("entries") ?: org.json.JSONArray()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        pcAdapter?.submit(items)
        pcStatus?.text = data.optString("path", "")
    }

    private fun onPcTap(entry: JSONObject) {
        if (entry.optBoolean("is_dir")) {
            browsePc(entry.optString("path"))
        } else {
            if (WebSocketManager.isConnected) {
                WebSocketManager.sendPc("files.get", JSONObject().put("path", entry.optString("path")))
            }
        }
    }

    private fun listApps() {
        val app = requireContext().applicationContext
        pcStatus?.text = "Listing apps…"
        LocalExecutor.run(app, "apk.list") { ok, data ->
            main.post {
                if (!ok) {
                    pcStatus?.text = "List failed: ${data.optString("error")}"
                    return@post
                }
                val arr = data.optJSONArray("apps") ?: org.json.JSONArray()
                val names = (0 until arr.length()).map { i ->
                    val a = arr.getJSONObject(i)
                    "${a.optString("label")}  ·  ${a.optString("package")}"
                }
                pcStatus?.text = "${names.size} apps installed"
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Installed apps")
                    .setItems(names.toTypedArray()) { _, which ->
                        val pkg = names[which].substringAfterLast("· ").trim()
                        val input = view?.findViewById<EditText>(R.id.apkPackageInput)
                        input?.setText(pkg)
                    }
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun runTransfer(cmd: String, params: JSONObject = JSONObject()) {
        if (!WebSocketManager.isConnected) {
            toast("Connect to the laptop first")
            return
        }
        log("Running $cmd...")
        // instant feedback
        pcStatus?.text = "Working: $cmd…"
        LocalExecutor.run(requireContext().applicationContext, cmd, params) { ok, data ->
            main.post {
                if (ok) {
                    val summary = mutableListOf<String>()
                    for (k in arrayOf("rows", "uploaded", "scanned", "failed", "file", "status", "size", "count")) {
                        if (!data.isNull(k)) summary.add("$k=${data.opt(k)}")
                    }
                    log("$cmd done: ${summary.joinToString("  ")}")
                    pcStatus?.text = "Done: $cmd"
                } else {
                    log("$cmd failed: ${data.optString("error")}")
                    pcStatus?.text = "Failed: ${data.optString("error")}"
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

private class PcStorageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
    val icon: android.widget.ImageView = view.findViewById(R.id.pcFileIcon)
    val name: TextView = view.findViewById(R.id.pcFileName)
    val size: TextView = view.findViewById(R.id.pcFileSize)
}

private class PcStorageAdapter(
    private val onClick: (JSONObject) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<PcStorageViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PcStorageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pc_storage, parent, false)
        return PcStorageViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PcStorageViewHolder, position: Int) {
        val e = items[position]
        val isDir = e.optBoolean("is_dir")
        val size = e.optLong("size")
        holder.name.text = e.optString("name")
        holder.size.text = if (isDir) "folder" else fmt(size)
        holder.icon.setImageResource(if (isDir) R.drawable.ic_files else R.drawable.ic_doc)
        holder.icon.setColorFilter(
            androidx.core.content.ContextCompat.getColor(
                holder.itemView.context,
                if (isDir) R.color.teal_700 else R.color.muted
            )
        )
        holder.itemView.setOnClickListener { onClick(e) }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun fmt(n: Long): String {
        val kb = n / 1024.0
        return when {
            n < 1024 -> "$n B"
            kb < 1024 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> String.format(java.util.Locale.US, "%.1f MB", kb / 1024)
        }
    }
}

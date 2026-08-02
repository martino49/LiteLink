package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.litelinkcompanion.commands.LocalExecutor
import org.json.JSONObject
import java.io.File

/** Camera Remote (Screen 20): capture photos and stream them to the desktop. */
class CameraRemoteActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var streaming = false
    private var runner: Runnable? = null
    private var facing = "back"
    private var flash = false
    private var resolution = "1080p"
    private var fps = "30"

    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_remote)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.camRemoteToolbar)
            .setNavigationOnClickListener { finish() }

        facing = prefs.getString("cam_facing", "back").orEmpty()
        flash = prefs.getBoolean("cam_flash", false)
        resolution = prefs.getString("cam_resolution", "1080p").orEmpty()
        fps = prefs.getString("cam_fps", "30").orEmpty()

        findViewById<View>(R.id.camToggleButton).setOnClickListener {
            facing = if (facing == "back") "front" else "back"
            prefs.edit().putString("cam_facing", facing).apply()
            toast("Camera: $facing")
        }
        findViewById<View>(R.id.camFlashButton).setOnClickListener {
            flash = !flash
            prefs.edit().putBoolean("cam_flash", flash).apply()
            findViewById<com.google.android.material.button.MaterialButton>(R.id.camFlashButton)
                .text = "Flash: ${if (flash) "ON" else "OFF"}"
        }

        val resButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.camResolutionButton)
        resButton.text = "Resolution: $resolution"
        val resMenu = android.widget.PopupMenu(this, resButton)
        for (r in arrayOf("720p", "1080p", "4K")) resMenu.menu.add(r)
        resMenu.setOnMenuItemClickListener { item ->
            resolution = item.title.toString()
            prefs.edit().putString("cam_resolution", resolution).apply()
            resButton.text = "Resolution: $resolution"
            true
        }
        resButton.setOnClickListener { resMenu.show() }

        val fpsButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.camFpsButton)
        fpsButton.text = "FPS: $fps"
        val fpsMenu = android.widget.PopupMenu(this, fpsButton)
        for (f in arrayOf("30", "60")) fpsMenu.menu.add(f)
        fpsMenu.setOnMenuItemClickListener { item ->
            fps = item.title.toString()
            prefs.edit().putString("cam_fps", fps).apply()
            fpsButton.text = "FPS: $fps"
            true
        }
        fpsButton.setOnClickListener { fpsMenu.show() }

        findViewById<View>(R.id.camStreamButton).setOnClickListener { toggleStreaming() }
    }

    override fun onResume() {
        super.onResume()
        showLastPhoto()
    }

    override fun onPause() {
        super.onPause()
        stopStreaming()
    }

    private fun toggleStreaming() {
        if (streaming) stopStreaming() else startStreaming()
    }

    private fun startStreaming() {
        if (!WebSocketManager.isConnected) {
            toast("Connect to the desktop to receive photos")
            return
        }
        streaming = true
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.camStreamButton)
        button.text = "Stop streaming"
        button.setIconResource(R.drawable.ic_close)
        findViewById<TextView>(R.id.camStatus).text = "\uD83D\uDCE4 Streaming to PC"
        findViewById<TextView>(R.id.camStatus).setTextColor(getColor(R.color.success))

        capture()
        runner = object : Runnable {
            override fun run() {
                if (!streaming) return
                capture()
                handler.postDelayed(this, intervalMs())
            }
        }
        handler.postDelayed(runner!!, intervalMs())
    }

    private fun intervalMs(): Long {
        return if (fps == "60") 500 else 1000
    }

    private fun stopStreaming() {
        streaming = false
        runner?.let { handler.removeCallbacks(it) }
        runner = null
        findViewById<com.google.android.material.button.MaterialButton>(R.id.camStreamButton).text = "Start streaming"
        findViewById<com.google.android.material.button.MaterialButton>(R.id.camStreamButton).setIconResource(R.drawable.ic_play)
        findViewById<TextView>(R.id.camStatus).text = "Status: Not streaming"
        findViewById<TextView>(R.id.camStatus).setTextColor(getColor(R.color.muted))
    }

    private fun capture() {
        val params = JSONObject().apply {
            put("dir", "camera")
            put("facing", facing)
        }
        LocalExecutor.run(applicationContext, "camera.capture", params) { ok, _ ->
            runOnUiThread {
                if (ok) showLastPhoto()
            }
        }
    }

    private fun showLastPhoto() {
        val dir = File(cacheDir, "camera")
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() }
        if (newest != null) {
            findViewById<TextView>(R.id.camLastPhoto).text =
                "Last photo: ${newest.name} (${newest.length() / 1024} KB)"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

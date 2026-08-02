package com.yourname.litelinkcompanion

import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

/** Second Screen (companion): render PC desktop via MJPEG + touch/keyboard control.
 *  Stays awake and forces landscape while streaming for a true full-screen view. */
class SecondScreenActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var streaming = false
    private var mode = "duplicate"
    private var monitor = 0
    private var frameW = 0
    private var frameH = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second_screen)

        // keep the screen awake while streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // landscape for a proper full-screen spanning view
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WebSocketManager.init(this)
        WebSocketManager.onScreenFrame = { data: JSONObject ->
            if (streaming) renderFrame(data)
        }

        val root = findViewById<FrameLayout>(R.id.streamRoot)
        val menu = findViewById<View>(R.id.streamMenu)

        // Tap the screen -> toggle the top menu; drags control the PC (mapped to the image)
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    if (!streaming) {
                        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        return@setOnTouchListener true
                    }
                    if (event.pointerCount == 1 && event.eventTime - event.downTime < 250) {
                        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                MotionEvent.ACTION_DOWN -> {
                    if (streaming && menu.visibility == View.GONE) {
                        val (nx, ny) = mapToImage(event.x, event.y, root)
                        if (nx != null && ny != null) WebSocketManager.sendPointer("down", nx, ny)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (streaming && menu.visibility == View.GONE) {
                        val (nx, ny) = mapToImage(event.x, event.y, root)
                        if (nx != null && ny != null) WebSocketManager.sendPointer("move", nx, ny)
                    }
                    true
                }
                else -> true
            }
        }

        // menu actions
        findViewById<View>(R.id.streamMenuMode).setOnClickListener {
            mode = if (mode == "duplicate") "extended" else "duplicate"
            findViewById<com.google.android.material.button.MaterialButton>(R.id.streamMenuMode).text =
                mode.replaceFirstChar { it.uppercase() }
            if (streaming) start()
        }
        findViewById<View>(R.id.streamMenuKeyboard).setOnClickListener {
            findViewById<View>(R.id.streamKeyboard).visibility = View.VISIBLE
            menu.visibility = View.GONE
        }
        findViewById<View>(R.id.streamMenuStop).setOnClickListener { stop() }

        // keyboard passthrough
        val textInput = findViewById<EditText>(R.id.streamTextInput)
        findViewById<View>(R.id.streamType).setOnClickListener { sendTyped() }
        textInput.setOnEditorActionListener { _, _, _ -> sendTyped(); true }

        // Ask which mode to use before starting (no silent duplicate default)
        showModeDialog()
    }

    /** Map a touch point to normalized (0..1) coords within the displayed image,
     *  accounting for fitCenter letterboxing. Returns null if outside the image. */
    private fun mapToImage(tx: Float, ty: Float, container: View): Pair<Double?, Double?> {
        if (frameW <= 0 || frameH <= 0 || container.width <= 0 || container.height <= 0) {
            return null to null
        }
        val viewAspect = container.width.toFloat() / container.height.toFloat()
        val imgAspect = frameW.toFloat() / frameH.toFloat()
        var drawW = container.width.toFloat()
        var drawH = container.height.toFloat()
        var offX = 0f
        var offY = 0f
        if (imgAspect > viewAspect) {
            drawH = container.width / imgAspect
            offY = (container.height - drawH) / 2
        } else {
            drawW = container.height * imgAspect
            offX = (container.width - drawW) / 2
        }
        if (tx < offX || tx > offX + drawW || ty < offY || ty > offY + drawH) {
            return null to null
        }
        val nx = ((tx - offX) / drawW).toDouble().coerceIn(0.0, 1.0)
        val ny = ((ty - offY) / drawH).toDouble().coerceIn(0.0, 1.0)
        return nx to ny
    }

    private fun showModeDialog() {
        val options = arrayOf("Duplicate — mirror my main screen", "Extended — use a 2nd display")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Second Screen")
            .setMessage("Which PC display should stream to this phone?")
            .setItems(options) { _, which ->
                mode = if (which == 0) "duplicate" else "extended"
                findViewById<com.google.android.material.button.MaterialButton>(R.id.streamMenuMode).text =
                    mode.replaceFirstChar { it.uppercase() }
                start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTyped() {
        val text = findViewById<EditText>(R.id.streamTextInput).text.toString()
        if (text.isNotEmpty()) {
            WebSocketManager.sendText(text)
            findViewById<EditText>(R.id.streamTextInput).setText("")
        }
    }

    override fun onResume() {
        super.onResume()
        if (WebSocketManager.isConnected && streaming) start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    private fun start() {
        if (!WebSocketManager.isConnected) {
            toast("Not connected")
            return
        }
        streaming = true
        findViewById<TextView>(R.id.streamHint).visibility = View.GONE
        WebSocketManager.sendPc("screen.stream.start", JSONObject().apply {
            put("mode", mode)
            put("monitor", monitor)
            put("fps", 12)
            put("scale", 0.7)
            put("quality", 60)
        })
        findViewById<TextView>(R.id.streamStatus).text = "Streaming ($mode)…"
    }

    private fun stop() {
        streaming = false
        if (WebSocketManager.isConnected) WebSocketManager.sendPc("screen.stream.stop")
        findViewById<TextView>(R.id.streamStatus).text = "Stopped"
        findViewById<TextView>(R.id.streamHint).visibility = View.VISIBLE
        findViewById<View>(R.id.streamMenu).visibility = View.GONE
        findViewById<View>(R.id.streamKeyboard).visibility = View.GONE
    }

    private fun renderFrame(data: JSONObject) {
        try {
            val b64 = data.optString("b64")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                frameW = bmp.width
                frameH = bmp.height
                main.post {
                    findViewById<ImageView>(R.id.streamView).setImageBitmap(bmp)
                }
            }
        } catch (e: Exception) {
            // skip a bad frame
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

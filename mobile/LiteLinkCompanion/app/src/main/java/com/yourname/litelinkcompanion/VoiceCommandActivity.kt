package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

/** Voice Commands (feature 86): speak -> text -> desktop command parser. */
class VoiceCommandActivity : AppCompatActivity() {

    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_command)

        WebSocketManager.init(this)

        findViewById<MaterialToolbar>(R.id.voiceToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.voiceMicButton).setOnClickListener { listen() }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
    }

    private fun listen() {
        val status = findViewById<TextView>(R.id.voiceStatus)
        status.text = "Listening…"

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition not available"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) {
                    findViewById<TextView>(R.id.voiceResult).text = "You said: \"$text\""
                    send(text)
                } else {
                    findViewById<TextView>(R.id.voiceStatus).text = "Didn't catch that"
                }
            }
            override fun onError(error: Int) {
                findViewById<TextView>(R.id.voiceStatus).text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timed out"
                    else -> "Error $error"
                }
            }
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command for your PC")
        }
        recognizer?.startListening(intent)
    }

    private fun send(text: String) {
        if (!WebSocketManager.isConnected) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        WebSocketManager.sendPc("voice.command", JSONObject().put("text", text))
        findViewById<TextView>(R.id.voiceStatus).text = "Sent to PC"
    }
}

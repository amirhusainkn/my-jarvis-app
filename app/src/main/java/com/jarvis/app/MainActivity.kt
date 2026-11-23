package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, RecognitionListener {

    private var tts: TextToSpeech? = null
    private lateinit var statusText: TextView
    private var speechService: SpeechService? = null
    private var isModelReady = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        statusText = TextView(this).apply {
            text = "Jarvis शुरू हो रहा है..."
            textSize = 20f
        }

        layout.addView(statusText)
        setContentView(layout)

        tts = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        } else {
            initVoskModel()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initVoskModel()
        } else {
            statusText.text = "माइक की अनुमति ज़रूरी है।"
        }
    }

    private fun initVoskModel() {
        StorageService.unpack(this, "model-en-us", "model-en-us",
            { model: Model ->
                try {
                    val recognizer = Recognizer(model, 8000.0f)
                    speechService = SpeechService(recognizer, 8000.0f)
                    isModelReady = true
                    statusText.text = "सुन रहा हूँ... टाइम पूछें।"
                    speechService?.startListening(this)
                } catch (e: Exception) {
                    statusText.text = "Model init error: ${e.message}"
                }
            },
            { exception ->
                statusText.text = "Failed to unpack model: ${exception.message}"
            }
        )
    }

    override fun onResult(hypothesis: String?) {
        val spoken = hypothesis?.lowercase(Locale.getDefault()) ?: ""
        if (spoken.contains("time") || spoken.contains("टाइम") || spoken.contains("समय")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val reply = "अभी समय $currentTime हो रहा है।"
            statusText.text = reply
            speakOut(reply)
        }
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}

    override fun onError(exception: Exception?) {
        statusText.text = "Error: ${exception?.message}"
    }

    override fun onTimeout() {
        if (isModelReady) {
            speechService?.startListening(this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale("hi", "IN"))
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    private fun speakOut(text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "JarvisTimeId")
    }

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

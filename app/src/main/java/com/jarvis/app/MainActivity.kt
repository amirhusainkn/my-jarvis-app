package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, RecognitionListener {

    private var tts: TextToSpeech? = null
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    private var speechService: SpeechService? = null
    private var isListening = false

    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        statusText = TextView(this).apply {
            text = "ऑफ़लाइन सिस्टम लोड हो रहा है..."
            textSize = 20f
        }

        actionButton = Button(this).apply {
            text = "Offline Jarvis Start"
            textSize = 18f
            setOnClickListener {
                toggleListening()
            }
        }

        layout.addView(statusText)
        layout.addView(actionButton)
        setContentView(layout)

        // माइक्रोफोन की परमिशन चेक करना
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }

        // Text-to-Speech initialization करना
        tts = TextToSpeech(this, this)

        // Vosk का ऑफ़लाइन मॉडल लोड करना
        initVoskModel()
    }

    private fun initVoskModel() {
        StorageService.unpack(this, "model-en-us", "model-en-us",
            { model ->
                try {
                    val recognizer = Recognizer(model, 8000.0f)
                    speechService = SpeechService(recognizer, 8000.0f)
                    statusText.text = "ऑफ़लाइन सिस्टम तैयार है, सर!"
                } catch (e: Exception) {
                    statusText.text = "Model init error: ${e.message}"
                }
            }, { exception ->
                statusText.text = "Failed to unpack model: ${exception.message}"
            }
        )
    }

    private fun toggleListening() {
        if (speechService == null) {
            statusText.text = "मॉडल अभी लोड हो रहा है, थोड़ा इंतज़ार करें..."
            return
        }

        if (!isListening) {
            isListening = true
            actionButton.text = "सुन रहा हूँ... (रोकने के लिए दबाएं)"
            statusText.text = "Listening offline..."
            speechService?.startListening(this)
        } else {
            isListening = false
            actionButton.text = "Offline Jarvis Start"
            statusText.text = "رک گیا हूँ"
            speechService?.stop()
        }
    }

    override fun onResult(hypothesis: String?) {
        // यहाँ Vosk की बिना इंटरनेट की जो आवाज सुनाई देगी, उसका टेक्स्ट मिलेगा
        hypothesis?.let {
            statusText.text = "बूल 'क्या' सर: $it"
            speakOut("Sir, I received your offline command.")
        }
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {
        statusText.text = "Error: ${exception?.message}"
    }
    override fun onTimeout() {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
        }
    }

    private fun speakOut(text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC.toString())
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "OfflineJarvisId")
    }

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

package com.jarvis.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        statusText = TextView(this).apply {
            text = "जार्विस कनेक्ट हो रहा है"
            textSize = 20f
        }

        actionButton = Button(this).apply {
            text = "Jarvis Active (बात करें)"
            textSize = 18f
            setOnClickListener {
                startListening()
            }
        }

        layout.addView(statusText)
        layout.addView(actionButton)
        setContentView(layout)

        // Text-to-Speech को इनिशियलाइज करना
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "सुन रहा हूँ सर..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText.text = "प्रोसेसिंग हो रही है..."
            }
            override fun onError(error: Int) {
                statusText.text = "आवाज़ समझ नहीं आई, दोबारा कोशिश करें।"
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    statusText.text = "आपने कहा: $spokenText"
                    
                    speakOut("मैंने आपकी बात सुन ली है, सर।")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                statusText.text = "Language not supported!"
            } else {
                speakOut("Sabhi systems online hain, Sir. Abhi samay ho raha hai")
            }
        }
    }

    private fun speakOut(text: String) {
        // यहाँ सीधे म्यूजिक स्ट्रीम और फ्लश का उपयोग किया गया है ताकि आवाज़ 100% स्पीकर से आए
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC.toString())
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "JarvisUtteranceId")
    }

private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            statusText.text = "Mic error: ${e.message}"
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}


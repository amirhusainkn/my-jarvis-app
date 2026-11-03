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
            text = "जार्विस कोर ऑनलाइन है"
            textSize = 20f
        }

        // सिर्फ एक ही स्मार्ट बटन - सुनने और बात करने के लिए
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

        // जुबान (TTS) और कान (SpeechRecognizer) को चालू करना
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
                    
                    // जार्विस का फिक्स डायलॉग
                    speakOut("सर, सभी सिस्टम चालू हैं। नेटवर्क और बैटरी पूरी तरह ठीक स्थिति में हैं। अगला आदेश क्या है?")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLocale(Locale("hi", "IN"))
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "जार्विस सुन रहा है, बोलिए...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        speechRecognizer.destroy()
        super.onDestroy()
    }
}


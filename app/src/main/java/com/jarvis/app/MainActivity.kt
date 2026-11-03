package com.jarvis.app
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private lateinit var statusText: TextView
    private lateinit var speakButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // यहाँ हम ऐप की स्क्रीन का ढांचा तैयार कर रहे हैं
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        statusText = TextView(this).apply {
            text = "जार्विस कोर ऑनलाइन है"
            textSize = 20f
        }

        speakButton = Button(this).apply {
            text = "जार्विस से बात करें"
            textSize = 18f
            setOnClickListener {
                speakOut("नमस्ते आमिर भाई, मैं आपका जार्विस हूँ। बताइए, मैं आपकी क्या मदद करूँ?")
            }
        }

        layout.addView(statusText)
        layout.addView(speakButton)
        setContentView(layout)

        // जुबान (TTS) इंजन को शुरू करना
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLocale(Locale("hi", "IN")) // हिंदी आवाज़ सेट करना
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                statusText.text = "भाषा समर्थित नहीं है!"
            }
        }
    }

    private fun speakOut(text: String) {
        statusText.text = text
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}


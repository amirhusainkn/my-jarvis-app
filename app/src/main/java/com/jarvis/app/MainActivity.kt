package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var statusText: TextView

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isModelReady: Boolean = false

    // ऑनलाइन आवाज़ के लिए अपनी ElevenLabs API Key और Voice ID डालें
    // अगर ऑफ़लाइन (लोकल आवाज़) से टेस्ट करना है तो इसे "YOUR_ELEVENLABS_API_KEY" ही रहने दें
    private val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY"
    private val elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM"

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

        // 1. पहले ऑफ़लाइन लोकल TTS इंजन तैयार करें
        initOfflineTts()

        // 2. माइक परमिशन चेक करें और Vosk मॉडल शुरू करें
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        } else {
            initVoskModel()
        }
    }

    private fun initOfflineTts() {
        val appContext = applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hiStatus = tts?.setLanguage(Locale("hi", "IN"))
                if (hiStatus == TextToSpeech.LANG_MISSING_DATA || hiStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val enStatus = tts?.setLanguage(Locale.US)
                    if (enStatus == TextToSpeech.LANG_MISSING_DATA || enStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                }
                ttsReady = true
            } else {
                ttsReady = false
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initVoskModel()
        } else {
            runOnUiThread {
                statusText.text = "माइक की अनुमति ज़रूरी है।"
            }
        }
    }

    private fun initVoskModel() {
        StorageService.unpack(this, "model-en-us", "model-en-us",
            { model: Model ->
                try {
                    recognizer = Recognizer(model, 8000.0f)
                    speechService = SpeechService(recognizer, 8000.0f)
                    isModelReady = true
                    runOnUiThread {
                        statusText.text = "सुन रहा हूँ... 'time' या 'टाइम' बोलें।"
                    }
                    speechService?.startListening(this)
                } catch (e: Exception) {
                    runOnUiThread {
                        statusText.text = "मॉडल शुरू करने में दिक्कत: ${e.message}"
                    }
                }
            },
            { exception ->
                runOnUiThread {
                    statusText.text = "मॉडल अनपैक नहीं हो सका: ${exception.message}"
                }
            }
        )
    }

    override fun onResult(hypothesis: String?) {
        val spoken = hypothesis?.lowercase(Locale.getDefault()) ?: ""
        if (spoken.contains("time") || spoken.contains("टाइम") || spoken.contains("समय")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val reply = "अभी समय $currentTime हो रहा है।"
            runOnUiThread {
                statusText.text = reply
            }
            speak(reply, speak = true)
        }
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}

    override fun onError(exception: Exception?) {
        runOnUiThread {
            statusText.text = "सुनने में दिक्कत: ${exception?.message}"
        }
    }

    override fun onTimeout() {
        if (isModelReady) {
            speechService?.startListening(this)
        }
    }

    fun speak(text: String, speak: Boolean = true) {
        if (!speak || text.isBlank()) return

        val hasElevenLabsKey = elevenLabsApiKey.isNotBlank() && elevenLabsApiKey != "YOUR_ELEVENLABS_API_KEY"
        if (hasElevenLabsKey) {
            speakOnlineElevenLabs(text)
        } else {
            speakOfflineLocal(text)
        }
    }

    private fun speakOfflineLocal(text: String) {
        runOnUiThread {
            if (!ttsReady || text.isBlank()) return@runOnUiThread
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "JarvisOfflineUtterance")
        }
    }

    private fun speakOnlineElevenLabs(text: String) {
        Thread {
            try {
                val endpoint = URL("https://api.elevenlabs.io/v1/text-to-speech/$elevenLabsVoiceId")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("xi-api-key", elevenLabsApiKey)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "audio/mpeg")
                }

                val payload = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_multilingual_v2")
                }.toString()

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val tempFile = File(cacheDir, "jarvis_reply.mp3")
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    runOnUiThread {
                        playMp3File(tempFile, text)
                    }
                } else {
                    speakOfflineLocal(text)
                }
                conn.disconnect()
            } catch (_: Exception) {
                speakOfflineLocal(text)
            }
        }.start()
    }

    private fun playMp3File(file: File, fallbackText: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            speakOfflineLocal(fallbackText)
        }
    }

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        recognizer?.close()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

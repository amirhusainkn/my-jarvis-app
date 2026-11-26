package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetManager
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
    private var startupSpoken: Boolean = false

    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isModelReady: Boolean = false

    private val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY"
    private val elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SAMPLE_RATE = 16000.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        statusText = TextView(this).apply {
            text = "सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव।"
            textSize = 15f
        }

        layout.addView(statusText)
        setContentView(layout)

        initOfflineTts()

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
                if (!startupSpoken) {
                    startupSpoken = true
                    speak("सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव।", speak = true)
                }
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
                statusText.text = "सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव। (माइक अनुमति दें)"
            }
        }
    }

    private fun inspectModelDir(dir: File): String {
        if (!dir.exists()) return "डायरेक्टरी मौजूद नहीं है"
        val details = mutableListOf<String>()
        for (sub in listOf("am", "conf", "graph", "ivector")) {
            val subDir = File(dir, sub)
            if (!subDir.exists()) {
                details.add("$sub: गायब")
            } else {
                val files = subDir.listFiles()
                if (files == null || files.isEmpty()) {
                    details.add("$sub: खाली (0 फ़ाइल)")
                } else {
                    val summary = files.take(3).joinToString(", ") { "${it.name}(${it.length()}B)" }
                    details.add("$sub: ${files.size} फ़ाइलें [$summary]")
                }
            }
        }
        return details.joinToString("\n")
    }

    private fun initVoskModel() {
        Thread {
            val outDir = File(filesDir, "model-en-us")
            var copiedCount = 0
            try {
                if (outDir.exists()) {
                    outDir.deleteRecursively()
                }
                copiedCount = copyAssetFolder(assets, "model-en-us", outDir.absolutePath)

                if (copiedCount == 0) {
                    val rootAssets = assets.list("")?.joinToString(", ") ?: "खाली"
                    val modelAssets = assets.list("model-en-us")?.joinToString(", ") ?: "नहीं मिला"
                    runOnUiThread {
                        statusText.text = "एसेट में फ़ाइलें नहीं मिलीं (कॉपी: 0)\nAssets root: $rootAssets\nmodel-en-us: $modelAssets"
                    }
                    return@Thread
                }

                val model = Model(outDir.absolutePath)
                recognizer = Recognizer(model, SAMPLE_RATE)
                speechService = SpeechService(recognizer, SAMPLE_RATE)
                isModelReady = true
                runOnUiThread {
                    statusText.text = "सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव। सुन रहा हूँ..."
                }
                speechService?.startListening(this)
            } catch (e: Exception) {
                val info = inspectModelDir(outDir)
                val traceLines = e.stackTraceToString().lines().take(4).joinToString("\n")
                runOnUiThread {
                    statusText.text = "एरर: ${e.message}\nकॉपी हुई फ़ाइलें: $copiedCount\n\n$info\n\n$traceLines"
                }
            }
        }.start()
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String): Int {
        var count = 0
        try {
            val files = assetManager.list(fromAssetPath)
            if (files != null && files.isNotEmpty()) {
                val targetDir = File(toPath)
                if (!targetDir.exists()) targetDir.mkdirs()
                for (file in files) {
                    count += copyAssetFolder(assetManager, "$fromAssetPath/$file", "$toPath/$file")
                }
            } else {
                try {
                    assetManager.open(fromAssetPath).use { input ->
                        val outFile = File(toPath)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    count = 1
                } catch (_: Exception) {
                    // खाली फ़ोल्डर होने पर open() फेल हो सकता है, इसलिए उसे सुरक्षित रूप से छोड़ दें
                }
            }
        } catch (_: Exception) {
        }
        return count
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

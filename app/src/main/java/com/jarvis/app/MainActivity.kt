package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
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

    private var lastCommandKey: String = ""
    private var lastCommandTimeMs: Long = 0L

    private val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY"
    private val elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SAMPLE_RATE = 16000.0f
        private const val COOLDOWN_MS = 2500L
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
                }
            }
        } catch (_: Exception) {
        }
        return count
    }

    private fun stopSpeakingNow() {
        runOnUiThread {
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getTimeInfoText(): String {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        return "अभी समय $currentTime हो रहा है।"
    }

    private fun getDateInfoText(): String {
        val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(Date())
        return "आज की तारीख़ $formattedDate है।"
    }

    private fun getDayInfoText(): String {
        val dayName = SimpleDateFormat("EEEE", Locale("hi", "IN")).format(Date())
        return "आज $dayName है।"
    }

    private fun getBatteryInfoText(): String {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        if (level < 0 || scale <= 0) {
            return "बैटरी की जानकारी अभी उपलब्ध नहीं है।"
        }
        val pct = (level * 100) / scale
        return if (isCharging) {
            "बैटरी $pct प्रतिशत है और फ़ोन चार्ज हो रहा है।"
        } else {
            "बैटरी बैकअप $pct प्रतिशत बाकी है।"
        }
    }

    private fun launchTargetApp(packageName: String, fallbackUrl: String, label: String): String {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                "$label खोल रहा हूँ।"
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
                "$label खोल रहा हूँ।"
            }
        } catch (_: Exception) {
            "$label खोलने में दिक्कत आई।"
        }
    }

    private fun openInstalledAppBySpokenName(spoken: String): String? {
        if (spoken.contains("youtube") || spoken.contains("यूट्यूब")) {
            return launchTargetApp("com.google.android.youtube", "https://www.youtube.com", "YouTube")
        }
        if (spoken.contains("instagram") || spoken.contains("इंस्टाग्राम") || spoken.contains("insta")) {
            return launchTargetApp("com.instagram.android", "https://www.instagram.com", "Instagram")
        }
        if (spoken.contains("whatsapp") || spoken.contains("व्हाट्सएप") || spoken.contains("वॉट्सएप")) {
            return launchTargetApp("com.whatsapp", "https://www.whatsapp.com", "WhatsApp")
        }

        val cleaned = spoken
            .replace("jarvis", " ")
            .replace("जार्विस", " ")
            .replace("open", " ")
            .replace("launch", " ")
            .replace("start", " ")
            .replace("kholo", " ")
            .replace("khol", " ")
            .replace("chalu", " ")
            .replace("karo", " ")
            .replace("करो", " ")
            .replace("खोलो", " ")
            .replace("खोल", " ")
            .replace("ऐप", " ")
            .replace("app", " ")
            .trim()

        val tokens = cleaned
            .split(Regex("[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

        if (tokens.isEmpty()) return null

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        var bestMatch: Pair<String, Intent>? = null
        var bestScore = 0

        for (info in activities) {
            val label = info.loadLabel(packageManager)?.toString()?.lowercase(Locale.getDefault())?.trim() ?: continue
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == packageName) continue

            var score = 0
            for (token in tokens) {
                if (label == token) {
                    score += 100
                } else if (label.contains(token)) {
                    score += 50
                } else if (token.contains(label) && label.length >= 3) {
                    score += 40
                }
            }

            if (score > bestScore) {
                bestScore = score
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(pkg, info.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                bestMatch = Pair(info.loadLabel(packageManager).toString(), launchIntent)
            }
        }

        if (bestMatch != null && bestScore >= 40) {
            return try {
                startActivity(bestMatch.second)
                "${bestMatch.first} खोल रहा हूँ।"
            } catch (_: Exception) {
                "${bestMatch.first} खोलने में दिक्कत आई।"
            }
        }

        return null
    }

    private fun extractHeardText(jsonText: String?): String {
        if (jsonText.isNullOrBlank()) return ""
        return try {
            val obj = JSONObject(jsonText)
            obj.optString("text", "")
        } catch (_: Exception) {
            jsonText
        }
    }

    private fun canRunCommand(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (key == lastCommandKey && (now - lastCommandTimeMs) < COOLDOWN_MS) {
            return false
        }
        lastCommandKey = key
        lastCommandTimeMs = now
        return true
    }

    private fun evaluateUtterance(rawHypothesis: String?): Pair<String, String>? {
        val spoken = extractHeardText(rawHypothesis).lowercase(Locale.getDefault()).trim()
        if (spoken.isEmpty()) return null

        if (spoken.contains("stop") || spoken.contains("ruko") || spoken.contains("रुको") || spoken.contains("बंद")) {
            stopSpeakingNow()
            return Pair("stop", "ठीक है, मैं चुप हो गया। सुन रहा हूँ...")
        }

        val hasTimeWord = spoken.contains("time") || spoken.contains("टाइम") || spoken.contains("समय") ||
            spoken.contains("samay") || spoken.contains("waqt") || spoken.contains("वक्त")
        val hasDateWord = spoken.contains("date") || spoken.contains("tarik") || spoken.contains("tariq") ||
            spoken.contains("tareekh") || spoken.contains("तारीख") || spoken.contains("तारीख़") ||
            spoken.contains("aaj ki") || spoken.contains("आज की")
        val hasDayWord = spoken.contains("day") || spoken.contains("din") || spoken.contains("दिन") ||
            spoken.contains("war") || spoken.contains("vaar") || spoken.contains("वार")
        val hasBatteryWord = spoken.contains("battery") || spoken.contains("बैटरी") ||
            spoken.contains("charge") || spoken.contains("charging") || spoken.contains("चार्ज")

        val hasOpenIntent = spoken.contains("open") || spoken.contains("launch") || spoken.contains("start") ||
            spoken.contains("kholo") || spoken.contains("khol") || spoken.contains("खोलो") ||
            spoken.contains("खोल") || spoken.contains("chalu") || spoken.contains("चालू")

        if (hasOpenIntent) {
            val appReply = openInstalledAppBySpokenName(spoken)
            if (appReply != null) {
                return Pair("app_open", appReply)
            }
        }

        return when {
            hasBatteryWord -> Pair("battery", getBatteryInfoText())
            hasDateWord -> Pair("date", getDateInfoText())
            hasDayWord -> Pair("day", getDayInfoText())
            hasTimeWord -> Pair("time", getTimeInfoText())
            else -> {
                val appReply = openInstalledAppBySpokenName(spoken)
                if (appReply != null) Pair("app_open", appReply) else null
            }
        }
    }

    private fun handleCompleteSpeech(rawHypothesis: String?) {
        val matched = evaluateUtterance(rawHypothesis) ?: return
        if (!canRunCommand(matched.first)) return

        val reply = matched.second
        runOnUiThread {
            statusText.text = reply
        }
        if (matched.first != "stop") {
            speak(reply, speak = true)
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        // अपनी ही आवाज़ पर खुद को चुप कराने से लूप बन रहा था, इसलिए यहाँ नहीं रोकते
    }

    override fun onResult(hypothesis: String?) {
        handleCompleteSpeech(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        // दोहराव रोकने के लिए केवल onResult से कमांड चलाते हैं
    }

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

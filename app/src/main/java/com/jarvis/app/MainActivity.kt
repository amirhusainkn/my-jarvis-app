package com.jarvis.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.max

class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var statusText: TextView

    private lateinit var dbHelper: MarcoDatabaseHelper

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var startupSpoken: Boolean = false
    private var activityCreatedAtMs: Long = 0L

    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isModelReady: Boolean = false
    private var isModelLoading: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLaunchRunnable: Runnable? = null

    private var lastCommandKey: String = ""
    private var lastCommandTimeMs: Long = 0L
    private var lastSpokenText: String = ""
    private var lastSpokenTimeMs: Long = 0L
    private var lastFailureReasonTimeMs: Long = 0L

    private var lastCheckTimeMs: Long = 0L
    private var lastCheckedLat: Double? = null
    private var lastCheckedLng: Double? = null
    private var awaiting500mNetworkPermission: Boolean = false
    private var pending500mLat: Double? = null
    private var pending500mLng: Double? = null

    private val noExactMatchHindi =
        "सर, जिसकी तलाश थी, वह तो नहीं मिली है; लेकिन उससे मिलती-जुलती ये कुछ हैं। क्या इनमें से कोई काम की है?"
    private val noExactMatchEnglish =
        "Sir, I didn't find what you were looking for, but here are a few similar ones. Is any of these useful?"

    private val fiveHundredMeterPromptHindi =
        "सर, आप 500 मीटर की दूरी तय कर चुके हैं। अब इसके आगे अगर लोकेशन सेव करवानी है तो ओपन नेटवर्क करो ताकि मैं इस जगह को उस जगह को जो है अपने लोकल में संभालूं।"
    private val fiveHundredMeterPromptEnglish =
        "Sir, you have covered 500 meters. If you want to save this location further, please open network so I can save this place in my local storage."

    private val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY"
    private val elevenLabsVoiceId = "21m00Tcm4TlvDq8ikWAM"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SAMPLE_RATE = 16000.0f
        private const val STARTUP_IGNORE_WINDOW_MS = 2500L
        private const val SAME_COMMAND_COOLDOWN_MS = 2500L
        private const val SAME_TEXT_REPEAT_GUARD_MS = 5000L
        private const val FAILURE_NOTICE_COOLDOWN_MS = 4500L
        private const val APP_LAUNCH_DELAY_MS = 1400L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCreatedAtMs = SystemClock.elapsedRealtime()
        dbHelper = MarcoDatabaseHelper(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        statusText = TextView(this).apply {
            text = "सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव। (कमांड के लिए 'Marco' बोलें)"
            textSize = 15f
        }

        layout.addView(statusText)
        setContentView(layout)

        initOfflineTts()
        checkAudioAndStartListening()
    }

    override fun onResume() {
        super.onResume()
        if (!isModelReady && !isModelLoading) {
            checkAudioAndStartListening()
        }
    }

    private fun checkAudioAndStartListening() {
        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (audioGranted) {
            val locPerms = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (locPerms.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, locPerms.toTypedArray(), 2)
            }
            initVoskModel()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val audioGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED ||
                (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)

            if (audioGranted) {
                runOnUiThread {
                    statusText.text = "माइक की अनुमति मिल गई है, वॉइस मॉडल लोड हो रहा है..."
                }
                initVoskModel()
            } else {
                runOnUiThread {
                    statusText.text = "माइक की अनुमति नहीं मिली, इसलिए मैं सुन नहीं पा रहा हूँ।"
                }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread {
                statusText.text = "माइक की अनुमति नहीं मिली, इसलिए मैं सुन नहीं पा रहा हूँ।"
            }
            return
        }
        if (isModelReady || isModelLoading) return
        isModelLoading = true

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
                    isModelLoading = false
                    runOnUiThread {
                        statusText.text = "वॉइस मॉडल फ़ाइलें नहीं मिलीं, इसलिए सुनना शुरू नहीं हो सका।\nAssets root: $rootAssets\nmodel-en-us: $modelAssets"
                    }
                    return@Thread
                }

                val model = Model(outDir.absolutePath)
                recognizer = Recognizer(model, SAMPLE_RATE)
                speechService = SpeechService(recognizer, SAMPLE_RATE)
                isModelReady = true
                isModelLoading = false
                runOnUiThread {
                    statusText.text = "सभी सिस्टम ऑनलाइन हैं सर, आई एम एक्टिव। 'Marco' कहकर कमांड दें..."
                }
                speechService?.startListening(this)
            } catch (e: Exception) {
                isModelLoading = false
                val info = inspectModelDir(outDir)
                val traceLines = e.stackTraceToString().lines().take(4).joinToString("\n")
                runOnUiThread {
                    statusText.text = "मॉडल लोड नहीं हो सका—वजह: ${e.message}\nकॉपी हुई फ़ाइलें: $copiedCount\n\n$info\n\n$traceLines"
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
        pendingLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLaunchRunnable = null
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

    private fun reportFailureReason(reasonText: String, forceSpeak: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        if (!forceSpeak && (now - lastFailureReasonTimeMs) < FAILURE_NOTICE_COOLDOWN_MS) {
            runOnUiThread { statusText.text = reasonText }
            return
        }
        lastFailureReasonTimeMs = now
        runOnUiThread {
            statusText.text = reasonText
        }
        if (forceSpeak && shouldSpeakOnce(reasonText)) {
            speak(reasonText, speak = true)
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
            return "बैटरी की जानकारी नहीं मिल सकी, क्योंकि सिस्टम ने बैटरी लेवल नहीं दिया।"
        }
        val pct = (level * 100) / scale
        return if (isCharging) {
            "बैटरी $pct प्रतिशत है और फ़ोन चार्ज हो रहा है।"
        } else {
            "बैटरी बैकअप $pct प्रतिशत बाकी है।"
        }
    }

    private fun getCurrentLocation(): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLoc = if (fineGranted) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return gpsLoc ?: netLoc
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    private fun saveVisitToLocal(placeName: String, lat: Double, lng: Double, summary: String) {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("place_name", placeName)
            put("lat", lat)
            put("lng", lng)
            put("visited_at", now)
            put("visit_count", 1)
            put("summary", summary)
        }
        db.insert("visits", null, values)
    }

    private fun markCurrentLocationAsHome(isEnglish: Boolean): String {
        val loc = getCurrentLocation()
        if (loc == null) {
            return if (isEnglish) {
                "Sir, I can't get your current location right now. Please enable location permission or GPS."
            } else {
                "सर, अभी लोकेशन नहीं मिल पाई है। कृपया लोकेशन अनुमति या GPS चालू करें।"
            }
        }

        val db = dbHelper.writableDatabase
        db.delete("home_location", null, null)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("lat", loc.latitude)
            put("lng", loc.longitude)
            put("updated_at", now)
        }
        db.insert("home_location", null, values)
        saveVisitToLocal("Home", loc.latitude, loc.longitude, "Home marked")

        lastCheckedLat = loc.latitude
        lastCheckedLng = loc.longitude
        lastCheckTimeMs = now

        return if (isEnglish) {
            "Got it, Sir. I have saved this place as home."
        } else {
            "ठीक है सर, यह जगह घर के तौर पर सेव कर ली है।"
        }
    }

    private fun getVisitsSummaryReply(isEnglish: Boolean): String {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT place_name, visited_at FROM visits ORDER BY id DESC", null)
        var totalCount = 0
        val recentPlaces = mutableListOf<String>()
        val sdf = SimpleDateFormat("dd MMM hh:mm a", Locale("hi", "IN"))

        cursor.use {
            while (it.moveToNext()) {
                totalCount++
                val place = it.getString(0) ?: "जगह"
                val ts = it.getLong(1)
                if (recentPlaces.size < 3) {
                    recentPlaces.add("$place (${sdf.format(Date(ts))})")
                }
            }
        }

        if (totalCount == 0) {
            return if (isEnglish) {
                "Sir, no visits are recorded in local memory yet."
            } else {
                "सर, लोकल मेमोरी में अभी तक बाहर जाने या विज़िट का कोई रिकॉर्ड दर्ज नहीं है।"
            }
        }

        val listText = recentPlaces.joinToString(", ")
        return if (isEnglish) {
            "Sir, you have $totalCount recorded visits. Recent ones: $listText."
        } else {
            "सर, अब तक कुल $totalCount विज़िट दर्ज हैं। हाल की जगहें: $listText।"
        }
    }

    private fun evaluate500MeterLocalRule(isEnglish: Boolean): String {
        val loc = getCurrentLocation() ?: return if (isEnglish) {
            "Sir, location is not available right now."
        } else {
            "सर, अभी लोकेशन उपलब्ध नहीं है।"
        }

        val now = System.currentTimeMillis()
        if (lastCheckedLat == null || lastCheckedLng == null) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            saveVisitToLocal("Start Point", loc.latitude, loc.longitude, "Initial point saved locally")
            return if (isEnglish) {
                "Sir, I have saved your starting point in local memory."
            } else {
                "सर, आपकी शुरुआती लोकेशन लोकल मेमोरी में सेव कर ली गई है।"
            }
        }

        val movedMeters = distanceMeters(lastCheckedLat!!, lastCheckedLng!!, loc.latitude, loc.longitude)
        val elapsedMinutes = (now - lastCheckTimeMs) / 60000.0

        if (movedMeters >= 500f) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            pending500mLat = loc.latitude
            pending500mLng = loc.longitude
            awaiting500mNetworkPermission = true
            return if (isEnglish) fiveHundredMeterPromptEnglish else fiveHundredMeterPromptHindi
        }

        return if (isEnglish) {
            "Sir, you have moved ${movedMeters.toInt()} meters (${"%.1f".format(elapsedMinutes)} min). I will alert you at 500 meters."
        } else {
            "सर, अभी ${movedMeters.toInt()} मीटर दूरी तय हुई है (${"%.1f".format(elapsedMinutes)} मिनट)। 500 मीटर पूरे होते ही आवाज़ आएगी।"
        }
    }

    private fun handle500mPermissionResponse(isApproved: Boolean, isEnglish: Boolean): String {
        val lat = pending500mLat ?: getCurrentLocation()?.latitude
        val lng = pending500mLng ?: getCurrentLocation()?.longitude
        awaiting500mNetworkPermission = false
        pending500mLat = null
        pending500mLng = null

        if (!isApproved) {
            return if (isEnglish) {
                "Understood, Sir. I will not save this 500-meter location right now."
            } else {
                "ठीक है सर, अभी यह 500 मीटर वाली लोकेशन सेव नहीं करूँगा।"
            }
        }

        if (lat != null && lng != null) {
            saveVisitToLocal("500m Checkpoint", lat, lng, "Saved after user permission")
        }

        return if (isEnglish) {
            "Permission received, Sir. Saved in local database. Please check that network is on if online sync is needed."
        } else {
            "अनुमति मिल गई सर, लोकेशन लोकल डेटाबेस में सेव कर ली गई है। आगे नेटवर्क ऑन करके देख लें।"
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findExactLearnedAnswer(queryNormalized: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT answer FROM learned_knowledge WHERE question_norm = ? LIMIT 1",
            arrayOf(queryNormalized)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun findSimilarLearnedAnswers(queryNormalized: String): List<String> {
        val queryWords = queryNormalized.split(" ")
            .filter { it.isNotBlank() && it != "marco" && it != "मार्को" }
            .toSet()
        if (queryWords.isEmpty()) return emptyList()

        val sixtyPercentMatches = mutableListOf<String>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT question_norm, answer FROM learned_knowledge", null)
        cursor.use {
            while (it.moveToNext()) {
                val qNorm = it.getString(0)
                val ans = it.getString(1)
                val storedWords = qNorm.split(" ").filter { w -> w.isNotBlank() }.toSet()
                if (storedWords.isNotEmpty()) {
                    val overlap = queryWords.intersect(storedWords).size.toFloat()
                    val ratio = overlap / max(queryWords.size, storedWords.size).toFloat()
                    if (ratio >= 0.60f) {
                        sixtyPercentMatches.add(ans)
                    }
                }
            }
        }
        return sixtyPercentMatches.take(3)
    }

    private fun looksEnglish(text: String): Boolean {
        val asciiLetters = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val devanagariLetters = text.count { it in '\u0900'..'\u097F' }
        return asciiLetters > devanagariLetters
    }

    private fun buildPackageLaunchAction(packageName: String, fallbackUrl: String, label: String): () -> Pair<Boolean, String> {
        return {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                    Pair(true, "")
                } else {
                    val mainCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolved = packageManager.queryIntentActivities(mainCategoryIntent, 0)
                    if (resolved.isNotEmpty()) {
                        val first = resolved[0].activityInfo
                        mainCategoryIntent.setClassName(first.packageName, first.name)
                        startActivity(mainCategoryIntent)
                        Pair(true, "")
                    } else if (fallbackUrl.isNotBlank()) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(webIntent)
                        Pair(true, "")
                    } else {
                        Pair(false, "$label नहीं खुल सका, क्योंकि यह ऐप फ़ोन में नहीं मिला या लॉन्च इंटेंट उपलब्ध नहीं है।")
                    }
                }
            } catch (e: SecurityException) {
                Pair(false, "$label नहीं खुल सका, क्योंकि Android सुरक्षा या अनुमति ने इसे रोका है। (${e.message ?: "SecurityException"})")
            } catch (e: Exception) {
                Pair(false, "$label नहीं खुल सका, वजह यह है: ${e.message ?: "लॉन्च त्रुटि"}।")
            }
        }
    }

    private fun scheduleSpeakThenLaunch(label: String, spokenAnnouncement: String, launchAction: () -> Pair<Boolean, String>) {
        pendingLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLaunchRunnable = null

        runOnUiThread {
            statusText.text = spokenAnnouncement
        }
        speak(spokenAnnouncement, speak = true)

        val runnable = Runnable {
            pendingLaunchRunnable = null
            val (opened, failReason) = launchAction()
            if (!opened) {
                val reason = if (failReason.isNotBlank()) {
                    failReason
                } else {
                    "$label नहीं खुल सका, क्योंकि ऐप लॉन्च नहीं हो पाया।"
                }
                reportFailureReason(reason, forceSpeak = true)
            }
        }
        pendingLaunchRunnable = runnable
        mainHandler.postDelayed(runnable, APP_LAUNCH_DELAY_MS)
    }

    private fun matchAppToLaunch(spoken: String): Triple<String, String, () -> Pair<Boolean, String>>? {
        if (spoken.contains("play store") || spoken.contains("playstore") || spoken.contains("प्ले स्टोर") || spoken.contains("प्लेस्टोर")) {
            val label = "Play Store"
            return Triple(
                label,
                "सर, $label खोला जा रहा है।",
                buildPackageLaunchAction("com.android.vending", "https://play.google.com/store", label)
            )
        }
        if (spoken.contains("whatsapp") || spoken.contains("व्हाट्सएप") || spoken.contains("वॉट्सएप") || spoken.contains("what's up")) {
            val label = "WhatsApp"
            return Triple(
                label,
                "सर, $label खोला जा रहा है।",
                buildPackageLaunchAction("com.whatsapp", "https://www.whatsapp.com", label)
            )
        }
        if (spoken.contains("youtube") || spoken.contains("यूट्यूब") || spoken.contains("you tube")) {
            val label = "YouTube"
            return Triple(
                label,
                "सर, $label खोला जा रहा है।",
                buildPackageLaunchAction("com.google.android.youtube", "https://www.youtube.com", label)
            )
        }
        if (spoken.contains("instagram") || spoken.contains("इंस्टाग्राम") || spoken.contains("insta")) {
            val label = "Instagram"
            return Triple(
                label,
                "सर, $label खोला जा रहा है।",
                buildPackageLaunchAction("com.instagram.android", "https://www.instagram.com", label)
            )
        }

        val cleaned = spoken
            .replace("marco", " ")
            .replace("मार्को", " ")
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
            .filter { it.length >= 3 }

        if (tokens.isEmpty()) return null

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        var bestMatch: Triple<String, String, Intent>? = null
        var bestScore = 0

        for (info in activities) {
            val label = info.loadLabel(packageManager)?.toString()?.lowercase(Locale.getDefault())?.trim() ?: continue
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == packageName) continue

            var score = 0
            for (token in tokens) {
                if (label == token) {
                    score += 100
                } else if (label.contains(token) && token.length >= 4) {
                    score += 55
                } else if (token.contains(label) && label.length >= 4) {
                    score += 45
                }
            }

            if (score > bestScore) {
                bestScore = score
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(pkg, info.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                bestMatch = Triple(info.loadLabel(packageManager).toString(), pkg, launchIntent)
            }
        }

        if (bestMatch != null && bestScore >= 55) {
            val appTitle = bestMatch.first
            val targetIntent = bestMatch.third
            return Triple(appTitle, "सर, $appTitle खोला जा रहा है।") {
                try {
                    startActivity(targetIntent)
                    Pair(true, "")
                } catch (e: Exception) {
                    Pair(false, "$appTitle नहीं खुल सका, वजह यह है: ${e.message ?: "लॉन्च त्रुटि"}।")
                }
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
        if (key == lastCommandKey && (now - lastCommandTimeMs) < SAME_COMMAND_COOLDOWN_MS) {
            return false
        }
        lastCommandKey = key
        lastCommandTimeMs = now
        return true
    }

    private fun shouldSpeakOnce(replyText: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (replyText == lastSpokenText && (now - lastSpokenTimeMs) < SAME_TEXT_REPEAT_GUARD_MS) {
            return false
        }
        lastSpokenText = replyText
        lastSpokenTimeMs = now
        return true
    }

    private fun evaluateUtterance(rawHypothesis: String?): Pair<String, Any>? {
        if ((SystemClock.elapsedRealtime() - activityCreatedAtMs) < STARTUP_IGNORE_WINDOW_MS) {
            return null
        }

        val rawHeard = extractHeardText(rawHypothesis).trim()
        val spoken = rawHeard.lowercase(Locale.getDefault())
        if (spoken.isEmpty()) return null

        val words = spoken.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val isEnglish = looksEnglish(rawHeard)
        val normalized = normalizeText(rawHeard)

        if (awaiting500mNetworkPermission) {
            val isYes = normalized.contains("haan") || normalized.contains("han") || normalized.contains("हाँ") ||
                normalized.contains("yes") || normalized.contains("ok") || normalized.contains("okay") ||
                normalized.contains("anumati") || normalized.contains("अनुमति") || normalized.contains("allow")
            val isNo = normalized.contains("nahi") || normalized.contains("nahin") || normalized.contains("नहीं") ||
                normalized.contains("no") || normalized.contains("mat") || normalized.contains("मत")
            if (isYes) {
                return Pair("five_hundred_perm_yes", handle500mPermissionResponse(isApproved = true, isEnglish = isEnglish))
            }
            if (isNo) {
                return Pair("five_hundred_perm_no", handle500mPermissionResponse(isApproved = false, isEnglish = isEnglish))
            }
        }

        val hasWakeWord = spoken.contains("marco") || spoken.contains("मार्को")
        if (!hasWakeWord) {
            return null
        }

        if (spoken.contains("stop") || spoken.contains("ruko") || spoken.contains("रुको") || spoken.contains("बंद")) {
            stopSpeakingNow()
            return Pair("stop", "ठीक है, मैं चुप हो गया। सुन रहा हूँ...")
        }

        val isSaveHome = (normalized.contains("ghar") && (normalized.contains("save") || normalized.contains("mark") || normalized.contains("set") || normalized.contains("सेव") || normalized.contains("मार्क") || normalized.contains("करो"))) ||
            (normalized.contains("save") && (normalized.contains("home") || normalized.contains("place")))
        if (isSaveHome) {
            return Pair("save_home", markCurrentLocationAsHome(isEnglish))
        }

        val isVisitsQuery = normalized.contains("kitni bar") || normalized.contains("kitni baar") ||
            normalized.contains("kab kab") || normalized.contains("kab-kab") ||
            normalized.contains("bahar gaya") || normalized.contains("bahar gaye") ||
            normalized.contains("visit") || normalized.contains("visits") ||
            normalized.contains("कब कब") || normalized.contains("कितनी बार") || normalized.contains("विज़िट")
        if (isVisitsQuery) {
            return Pair("visits_summary", getVisitsSummaryReply(isEnglish))
        }

        if (normalized.contains("500") || normalized.contains("meter") || normalized.contains("मीटर") || normalized.contains("location") || normalized.contains("लोकेशन")) {
            return Pair("five_hundred_check", evaluate500MeterLocalRule(isEnglish))
        }

        val hasTimeWord = spoken.contains("time") || spoken.contains("टाइम") || spoken.contains("समय") ||
            spoken.contains("samay") || spoken.contains("waqt") || spoken.contains("वक्त")
        val hasDateWord = spoken.contains("date") || spoken.contains("tarik") || spoken.contains("tariq") ||
            spoken.contains("tareekh") || spoken.contains("तारीख") || spoken.contains("तारीख़")
        val hasDayWord = spoken.contains("day") || spoken.contains("din") || spoken.contains("दिन") ||
            spoken.contains("war") || spoken.contains("vaar") || spoken.contains("वार")
        val hasBatteryWord = spoken.contains("battery") || spoken.contains("बैटरी") ||
            spoken.contains("charge") || spoken.contains("charging") || spoken.contains("चार्ज")

        val hasOpenIntent = spoken.contains("open") || spoken.contains("launch") || spoken.contains("start") ||
            spoken.contains("kholo") || spoken.contains("khol") || spoken.contains("खोलो") ||
            spoken.contains("खोल") || spoken.contains("chalu") || spoken.contains("चालू")

        if (hasOpenIntent) {
            val appTriple = matchAppToLaunch(spoken)
            if (appTriple != null) {
                return Pair("app_open", appTriple)
            }
            return Pair(
                "unmatched_open",
                "मैंने \"$rawHeard\" सुना, लेकिन यह ऐप फ़ोन में नहीं मिला या नाम मैच नहीं हुआ।"
            )
        }

        when {
            hasBatteryWord -> return Pair("battery", getBatteryInfoText())
            hasDateWord -> return Pair("date", getDateInfoText())
            hasDayWord -> return Pair("day", getDayInfoText())
            hasTimeWord -> return Pair("time", getTimeInfoText())
        }

        val exactAnswer = findExactLearnedAnswer(normalized)
        if (exactAnswer != null) {
            return Pair("learned_exact", exactAnswer)
        }

        val similarAnswers = findSimilarLearnedAnswers(normalized)
        if (similarAnswers.isNotEmpty()) {
            val intro = if (isEnglish) noExactMatchEnglish else noExactMatchHindi
            return Pair("learned_similar", "$intro\n\n• " + similarAnswers.joinToString("\n• "))
        }

        val fallbackIntro = if (isEnglish) noExactMatchEnglish else noExactMatchHindi
        return Pair("no_match", fallbackIntro)
    }

    private fun handleCompleteSpeech(rawHypothesis: String?) {
        val matched = evaluateUtterance(rawHypothesis) ?: return
        if (!canRunCommand(matched.first)) return

        when (matched.first) {
            "stop" -> {
                val msg = matched.second as String
                runOnUiThread { statusText.text = msg }
            }
            "app_open" -> {
                @Suppress("UNCHECKED_CAST")
                val appTriple = matched.second as Triple<String, String, () -> Pair<Boolean, String>>
                val announcement = appTriple.second
                if (shouldSpeakOnce(announcement)) {
                    scheduleSpeakThenLaunch(appTriple.first, announcement, appTriple.third)
                }
            }
            "unmatched_open" -> {
                val reason = matched.second as String
                reportFailureReason(reason, forceSpeak = false)
            }
            "learned_similar", "no_match" -> {
                val fullText = matched.second as String
                val spokenPart = fullText.substringBefore("\n\n").trim()
                runOnUiThread {
                    statusText.text = fullText
                }
                if (shouldSpeakOnce(spokenPart)) {
                    speak(spokenPart, speak = true)
                }
            }
            else -> {
                val reply = matched.second as String
                runOnUiThread {
                    statusText.text = reply
                }
                if (shouldSpeakOnce(reply)) {
                    speak(reply, speak = true)
                }
            }
        }
    }

    override fun onPartialResult(hypothesis: String?) {}

    override fun onResult(hypothesis: String?) {
        handleCompleteSpeech(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        // दोहराव रोकने के लिए केवल onResult से कमांड चलाते हैं
    }

    override fun onError(exception: Exception?) {
        val msg = "सुनने का काम रुक गया—वजह: ${exception?.message ?: "माइक या रिकॉग्नाइज़र त्रुटि"}"
        reportFailureReason(msg, forceSpeak = true)
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
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MarcoOfflineUtterance")
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
                    val tempFile = File(cacheDir, "marco_reply.mp3")
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
        pendingLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        speechService?.stop()
        speechService?.shutdown()
        recognizer?.close()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        tts = null
        dbHelper.close()
        super.onDestroy()
    }

    class MarcoDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "marco_local.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS home_location (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lat REAL NOT NULL,
                    lng REAL NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS visits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    place_name TEXT,
                    lat REAL,
                    lng REAL,
                    visited_at INTEGER NOT NULL,
                    visit_count INTEGER DEFAULT 1,
                    summary TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS learned_knowledge (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    question_norm TEXT UNIQUE NOT NULL,
                    answer TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS home_location")
            db.execSQL("DROP TABLE IF EXISTS visits")
            db.execSQL("DROP TABLE IF EXISTS learned_knowledge")
            onCreate(db)
        }
    }
}

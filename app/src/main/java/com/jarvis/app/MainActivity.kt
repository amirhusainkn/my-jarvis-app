package com.jarvis.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvHeard: TextView
    private lateinit var tvOutput: TextView

    private lateinit var dbHelper: JarvisDatabaseHelper
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastCheckTimeMs: Long = 0L
    private var lastCheckedLat: Double? = null
    private var lastCheckedLng: Double? = null

    private val noExactMatchHindi =
        "सर, जिसकी तलाश थी, वह तो नहीं मिली है; लेकिन उससे मिलती-जुलती ये कुछ हैं। क्या इनमें से कोई काम की है?"
    private val noExactMatchEnglish =
        "Sir, I didn't find what you were looking for, but here are a few similar ones. Is any of these useful?"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvHeard = findViewById(R.id.tvHeard)
        tvOutput = findViewById(R.id.tvOutput)

        dbHelper = JarvisDatabaseHelper(this)
        tts = TextToSpeech(this, this)

        requestPermissionsIfNeeded()
        setupSpeechRecognizer()
        startListeningLoop()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun speak(message: String, lang: Locale = Locale("hi", "IN")) {
        tts?.language = lang
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "marco_utt")
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Voice recognition unavailable"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    tvStatus.text = "Marco सुन रहा है..."
                }

                override fun onBeginningOfSpeech() {
                    tvStatus.text = "बात सुनी जा रही है..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    tvStatus.text = "Marco सोच रहा है..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    mainHandler.postDelayed({ startListeningLoop() }, 700)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim().orEmpty()
                    if (spokenText.isNotEmpty()) {
                        tvHeard.text = "आपने कहा: \"$spokenText\""
                        handleVoiceCommand(spokenText)
                    }
                    mainHandler.postDelayed({ startListeningLoop() }, 800)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListeningLoop() {
        if (isListening || speechRecognizer == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            tvStatus.text = "माइक की अनुमति दें"
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            isListening = false
        }
    }

    private fun getCurrentLocation(): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) return null

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return gpsLoc ?: netLoc
    }

    private fun markCurrentLocationAsHome(isEnglish: Boolean) {
        val loc = getCurrentLocation()
        if (loc == null) {
            val msg = if (isEnglish) {
                "Sir, I can't get your current location right now. Please check GPS or location permission."
            } else {
                "सर, अभी लोकेशन नहीं मिल पाई है। GPS या लोकेशन अनुमति चेक करें।"
            }
            tvOutput.text = msg
            speak(msg, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
            return
        }

        val db = dbHelper.writableDatabase
        db.delete("home_location", null, null)
        val values = ContentValues().apply {
            put("lat", loc.latitude)
            put("lng", loc.longitude)
            put("updated_at", System.currentTimeMillis())
        }
        db.insert("home_location", null, values)

        lastCheckedLat = loc.latitude
        lastCheckedLng = loc.longitude
        lastCheckTimeMs = System.currentTimeMillis()

        val msg = if (isEnglish) {
            "Got it, Sir. I've saved this place as home."
        } else {
            "ठीक है सर, यह जगह घर के तौर पर सेव कर ली है।"
        }
        tvStatus.text = "Marco Ready (Home Saved)"
        tvOutput.text = msg
        speak(msg, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
    }

    private fun check500MeterRule(isEnglish: Boolean): String {
        val loc = getCurrentLocation() ?: return if (isEnglish) {
            "Sir, location is not available right now."
        } else {
            "सर, अभी लोकेशन उपलब्ध नहीं है।"
        }
        val now = System.currentTimeMillis()

        val home = getHomeLocation()
        if (home != null) {
            val distFromHome = distanceMeters(loc.latitude, loc.longitude, home.first, home.second)
            if (distFromHome <= 100f) {
                return if (isEnglish) {
                    "Sir, you are right around home."
                } else {
                    "सर, आप अभी घर के दायरे में हैं।"
                }
            }
        }

        if (lastCheckedLat == null || lastCheckedLng == null) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            return if (isEnglish) {
                "Sir, I've noted your starting point."
            } else {
                "सर, शुरुआती लोकेशन नोट कर ली गई है।"
            }
        }

        val elapsedMinutes = (now - lastCheckTimeMs) / 60000.0
        val movedMeters = distanceMeters(lastCheckedLat!!, lastCheckedLng!!, loc.latitude, loc.longitude)

        return if (elapsedMinutes >= 5.0 && movedMeters >= 500f) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            val prompt = if (isEnglish) {
                "Sir, 500 meters are covered. Should I turn on network and log this location?"
            } else {
                "सर, 500 मीटर पूरे हो गए हैं। क्या मैं नेटवर्क ऑन करके लोकेशन नोट करूँ?"
            }
            speak(prompt, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
            prompt
        } else {
            if (isEnglish) {
                "Sir, not yet (Distance: ${movedMeters.toInt()}m, Time: ${"%.1f".format(elapsedMinutes)} min)."
            } else {
                "सर, अभी नियम पूरा नहीं हुआ (दूरी: ${movedMeters.toInt()} मी, समय: ${"%.1f".format(elapsedMinutes)} मि)।"
            }
        }
    }

    private fun getHomeLocation(): Pair<Double, Double>? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT lat, lng FROM home_location LIMIT 1", null)
        cursor.use {
            if (it.moveToFirst()) {
                return Pair(it.getDouble(0), it.getDouble(1))
            }
        }
        return null
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    private fun looksEnglish(text: String): Boolean {
        val asciiLetters = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val devanagariLetters = text.count { it in '\u0900'..'\u097F' }
        return asciiLetters > devanagariLetters
    }

    private fun handleVoiceCommand(input: String) {
        val isEnglish = looksEnglish(input)
        val normalized = normalizeText(input)

        val isSaveHomeIntent =
            (normalized.contains("save") && normalized.contains("place")) ||
                (normalized.contains("save") && normalized.contains("home")) ||
                (normalized.contains("ghar") && (normalized.contains("mark") || normalized.contains("save") || normalized.contains("yaad") || normalized.contains("sew") || normalized.contains("karo")))

        if (isSaveHomeIntent) {
            markCurrentLocationAsHome(isEnglish)
            return
        }

        if (normalized.contains("where am i") || normalized.contains("main kahan hoon") || normalized.contains("kahan hoon")) {
            val loc = getCurrentLocation()
            val reply = if (loc != null) {
                if (isEnglish) {
                    "Sir, you are at ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}."
                } else {
                    "सर, आप अभी ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)} पर हैं।"
                }
            } else {
                if (isEnglish) {
                    "Sir, I can't fetch your location right now."
                } else {
                    "सर, अभी आपकी लोकेशन नहीं मिल पा रही है।"
                }
            }
            tvOutput.text = reply
            speak(reply, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
            return
        }

        if (normalized.contains("500") || normalized.contains("bahar") || normalized.contains("location")) {
            val statusMsg = check500MeterRule(isEnglish)
            tvOutput.text = statusMsg
            return
        }

        val exactAnswer = findExactLearnedAnswer(normalized)
        if (exactAnswer != null) {
            tvOutput.text = exactAnswer
            speak(exactAnswer, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
            return
        }

        val similarList = findSimilarLearnedAnswers(normalized)
        if (similarList.isNotEmpty()) {
            val intro = if (isEnglish) noExactMatchEnglish else noExactMatchHindi
            val formatted = "$intro\n\n• " + similarList.joinToString("\n• ")
            tvOutput.text = formatted
            speak(intro, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
            return
        }

        val intro = if (isEnglish) noExactMatchEnglish else noExactMatchHindi
        tvOutput.text = intro
        speak(intro, if (isEnglish) Locale.ENGLISH else Locale("hi", "IN"))
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
        val queryWords = queryNormalized.split(" ").filter { it.isNotBlank() && it != "marco" }.toSet()
        if (queryWords.isEmpty()) return emptyList()

        val similar = mutableListOf<String>()
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
                    if (ratio >= 0.35f) {
                        similar.add(ans)
                    }
                }
            }
        }
        return similar.take(3)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        dbHelper.close()
        super.onDestroy()
    }

    class JarvisDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, "jarvis_local.db", null, 1) {

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

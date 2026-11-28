package com.jarvis.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button
    private lateinit var btnMarkHome: Button
    private lateinit var tvOutput: TextView

    private lateinit var dbHelper: JarvisDatabaseHelper
    private var tts: TextToSpeech? = null
    private var lastCheckTimeMs: Long = 0L
    private var lastCheckedLat: Double? = null
    private var lastCheckedLng: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etCommand = findViewById(R.id.etCommand)
        btnSend = findViewById(R.id.btnSend)
        btnMarkHome = findViewById(R.id.btnMarkHome)
        tvOutput = findViewById(R.id.tvOutput)

        dbHelper = JarvisDatabaseHelper(this)
        tts = TextToSpeech(this, this)

        requestLocationPermissionIfNeeded()

        btnMarkHome.setOnClickListener {
            markCurrentLocationAsHome()
        }

        btnSend.setOnClickListener {
            val cmd = etCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                handleCommand(cmd)
                etCommand.text.clear()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    private fun requestLocationPermissionIfNeeded() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                101
            )
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

    private fun markCurrentLocationAsHome() {
        val loc = getCurrentLocation()
        if (loc == null) {
            val msg = "अभी लोकेशन नहीं मिल पाई है। GPS या लोकेशन अनुमति चेक करें।"
            tvOutput.text = msg
            speak(msg)
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

        val msg = "घर की लोकेशन सेव हो गई है।"
        tvStatus.text = "Jarvis Ready (Home Set)"
        tvOutput.text = msg
        speak(msg)
    }

    private fun check500MeterRule(): String {
        val loc = getCurrentLocation() ?: return "लोकेशन उपलब्ध नहीं है।"
        val now = System.currentTimeMillis()

        val home = getHomeLocation()
        if (home != null) {
            val distFromHome = distanceMeters(loc.latitude, loc.longitude, home.first, home.second)
            if (distFromHome <= 100f) {
                return "आप अभी घर के दायरे में हैं।"
            }
        }

        if (lastCheckedLat == null || lastCheckedLng == null) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            return "पहली लोकेशन नोट कर ली गई है।"
        }

        val elapsedMinutes = (now - lastCheckTimeMs) / 60000.0
        val movedMeters = distanceMeters(lastCheckedLat!!, lastCheckedLng!!, loc.latitude, loc.longitude)

        return if (elapsedMinutes >= 5.0 && movedMeters >= 500f) {
            lastCheckedLat = loc.latitude
            lastCheckedLng = loc.longitude
            lastCheckTimeMs = now
            val prompt = "500 मीटर पूरे हो गए हैं। क्या मैं नेटवर्क ऑन करके लोकेशन नोट करूँ?"
            speak(prompt)
            prompt
        } else {
            "अभी नियम पूरा नहीं हुआ (दूरी: ${movedMeters.toInt()} मी, समय: ${"%.1f".format(elapsedMinutes)} मि)।"
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

    private fun handleCommand(input: String) {
        val normalized = normalizeText(input)

        if (normalized.contains("500") || normalized.contains("bahar") || normalized.contains("location")) {
            val statusMsg = check500MeterRule()
            tvOutput.text = statusMsg
            return
        }

        val localAnswer = findSimilarLearnedAnswer(normalized)
        if (localAnswer != null) {
            tvOutput.text = "लोकल मेमोरी: $localAnswer"
            speak(localAnswer)
            return
        }

        val reply = "मैंने आपकी बात सुन ली है: \"$input\"। (नया ज्ञान सेव करने के लिए 'सीखो: सवाल = जवाब' लिखें)"
        tvOutput.text = reply
        speak("मैंने आपकी बात सुन ली है।")
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s=]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findSimilarLearnedAnswer(queryNormalized: String): String? {
        val queryWords = queryNormalized.split(" ").filter { it.isNotBlank() }.toSet()
        if (queryWords.isEmpty()) return null

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT question_norm, answer FROM learned_knowledge", null)
        cursor.use {
            while (it.moveToNext()) {
                val qNorm = it.getString(0)
                val ans = it.getString(1)
                if (qNorm == queryNormalized) return ans

                val storedWords = qNorm.split(" ").filter { w -> w.isNotBlank() }.toSet()
                if (storedWords.isNotEmpty()) {
                    val overlap = queryWords.intersect(storedWords).size.toFloat()
                    val ratio = overlap / max(queryWords.size, storedWords.size).toFloat()
                    if (ratio >= 0.6f) {
                        return ans
                    }
                }
            }
        }
        return null
    }

    override fun onDestroy() {
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

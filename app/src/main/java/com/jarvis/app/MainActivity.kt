package com.dhanouriworld.jarvis

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val LOCATION_PERM_CODE = 101
        private const val DISTANCE_THRESHOLD_METERS = 500.0
        private const val SYNC_CYCLE_MS = 12L * 60L * 60L * 1000L // 12 घंटे का संतुलित चक्र
        private const val FIVE_MIN_MS = 5L * 60L * 1000L          // 5 मिनट का सुरक्षित अंतराल
        private const val PREFS_NAME = "jarvis_local_prefs"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LON = "home_lon"
        private const val KEY_HOME_SET = "home_set"
        private const val KEY_LAST_CHECK_LAT = "last_check_lat"
        private const val KEY_LAST_CHECK_LON = "last_check_lon"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }

    private lateinit var dbHelper: JarvisDatabaseHelper
    private lateinit var tvStatus: TextView
    private lateinit var etInput: EditText
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = JarvisDatabaseHelper(this)
        tts = TextToSpeech(this, this)

        tvStatus = findViewById(R.id.tvStatus)
        etInput = findViewById(R.id.etInput)

        val btnMarkHome: Button = findViewById(R.id.btnMarkHome)
        val btnCheck500m: Button = findViewById(R.id.btnCheck500m)
        val btnAsk: Button = findViewById(R.id.btnAsk)
        val btnVisitSummary: Button = findViewById(R.id.btnVisitSummary)

        // घर (जैसे जसौली) को एक बार मार्क करें ताकि बार-बार घर की फ़ालतू एंट्री न हो
        btnMarkHome.setOnClickListener {
            markCurrentLocationAsHome()
        }

        // फ़ोन एक्टिव/ऑन रहते हुए 500 मीटर दूरी जाँचें (ऑफ़लाइन दूरी पहचान + नेटवर्क/अनुमति के साथ सेव)
        btnCheck500m.setOnClickListener {
            check500MeterRule()
        }

        // सवाल/बात भेजें: ऑफ़लाइन लोकल SQLite से ढूँढें या ऑनलाइन में नई/गैर-डुप्लिकेट बात सीखें
        btnAsk.setOnClickListener {
            val userText = etInput.text.toString().trim()
            if (userText.isNotEmpty()) {
                handleUserQuery(userText)
                etInput.text.clear()
            }
        }

        // विज़िट गिनती देखें (कितनी बार और कब-कब गए)
        btnVisitSummary.setOnClickListener {
            val summary = dbHelper.getVisitsSummary()
            showAndSpeak(summary)
        }

        requestLocationPermissionIfNeeded()
        updateStatusText("जार्विस तैयार है। (500m ऑफ़लाइन दूरी + 12h चक्र + SQLite लोकल मेमोरी)")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            ttsReady = true
        }
    }

    private fun showAndSpeak(message: String) {
        tvStatus.text = message
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
        }
    }

    private fun updateStatusText(message: String) {
        tvStatus.text = message
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
                LOCATION_PERM_CODE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionIfNeeded()
            return null
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        return when {
            gpsLoc != null && netLoc != null -> if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
            gpsLoc != null -> gpsLoc
            netLoc != null -> netLoc
            else -> null
        }
    }

    // 1. घर (जसौली) को मार्क करना ताकि घर में रहते हुए बार-बार फ़ालतू एंट्री न बने
    private fun markCurrentLocationAsHome() {
        val loc = getCurrentLocation()
        if (loc == null) {
            showAndSpeak("अभी लोकेशन नहीं मिल सकी। कृपया लोकेशन ऑन करके दोबारा कोशिश करें।")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HOME_LAT, loc.latitude.toString())
            .putString(KEY_HOME_LON, loc.longitude.toString())
            .putBoolean(KEY_HOME_SET, true)
            .putString(KEY_LAST_CHECK_LAT, loc.latitude.toString())
            .putString(KEY_LAST_CHECK_LON, loc.longitude.toString())
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()

        showAndSpeak("घर की लोकेशन सेव कर ली गई है। अब घर में रहने पर बार-बार फ़ालतू एंट्री नहीं होगी।")
    }

    private fun isNearHome(lat: Double, lon: Double): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HOME_SET, false)) return false

        val homeLat = prefs.getString(KEY_HOME_LAT, null)?.toDoubleOrNull() ?: return false
        val homeLon = prefs.getString(KEY_HOME_LON, null)?.toDoubleOrNull() ?: return false

        val results = FloatArray(1)
        Location.distanceBetween(homeLat, homeLon, lat, lon, results)
        return results[0] <= DISTANCE_THRESHOLD_METERS
    }

    // 2. फ़ोन एक्टिव/ऑन रहते हुए 500 मीटर दूरी की जाँच (ऑफ़लाइन) -> फिर नेटवर्क व अनुमति के साथ सेव
    private fun check500MeterRule() {
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

        // फ़ालतू बार-बार लूप से बचाव (कम से कम 5 मिनट का संतुलन)
        if (lastCheckTime > 0L && (now - lastCheckTime) < FIVE_MIN_MS) {
            val remainingSec = ((FIVE_MIN_MS - (now - lastCheckTime)) / 1000L)
            showAndSpeak("बैटरी और रफ़्तार बचाने के लिए अभी 5 मिनट का अंतराल बाकी है ($remainingSec सेकंड)।")
            return
        }

        val loc = getCurrentLocation()
        if (loc == null) {
            showAndSpeak("लोकेशन नहीं मिल पाई। कृपया लोकेशन सेवा चालू रखें।")
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        // अगर आप घर के 500 मीटर के दायरे में हैं, तो घर की फ़ालतू विज़िट एंट्री नहीं जोड़ेंगे
        if (isNearHome(loc.latitude, loc.longitude)) {
            showAndSpeak("आप घर के दायरे में हैं। यहाँ बार-बार घर की एंट्री सेव नहीं की जाएगी।")
            return
        }

        val lastLat = prefs.getString(KEY_LAST_CHECK_LAT, null)?.toDoubleOrNull()
        val lastLon = prefs.getString(KEY_LAST_CHECK_LON, null)?.toDoubleOrNull()

        if (lastLat == null || lastLon == null) {
            prefs.edit()
                .putString(KEY_LAST_CHECK_LAT, loc.latitude.toString())
                .putString(KEY_LAST_CHECK_LON, loc.longitude.toString())
                .apply()
            showAndSpeak("शुरुआती लोकेशन नोट कर ली गई है। 500 मीटर आगे बढ़ने पर मैं आपसे पूछूँगा।")
            return
        }

        val distanceArr = FloatArray(1)
        Location.distanceBetween(lastLat, lastLon, loc.latitude, loc.longitude, distanceArr)
        val movedMeters = distanceArr[0].toDouble()

        if (movedMeters < DISTANCE_THRESHOLD_METERS) {
            showAndSpeak(
                "आप पिछली जगह से लगभग ${movedMeters.toInt()} मीटर चले हैं। 500 मीटर पूरे होने पर मैं पूछूँगा।"
            )
            return
        }

        // 500 मीटर की दूरी पूरी हो चुकी है — अब अनुमति और नेटवर्क के लिए पूछें
        promptFor500mSave(loc.latitude, loc.longitude, movedMeters.toInt())
    }

    private fun promptFor500mSave(lat: Double, lon: Double, meters: Int) {
        val msg = "सर, नेटवर्क ओपन कीजिए। आप $meters मीटर की दूरी पर आ चुके हैं। " +
            "अगर आपकी अनुमति हो तो मैं इस डेटा को सेव करूँ, इसके लिए आपको नेटवर्क ऑन करना पड़ेगा।"

        AlertDialog.Builder(this)
            .setTitle("500 मीटर नई दूरी — अनुमति")
            .setMessage(msg)
            .setPositiveButton("हाँ, अनुमति है") { _, _ ->
                if (!isNetworkAvailable()) {
                    showAndSpeak("नेटवर्क अभी बंद है। कृपया नेटवर्क ऑन करें, फिर अनुमति देकर सेव करें।")
                    return@setPositiveButton
                }

                val placeName = resolvePlaceNameOnline(lat, lon)
                val now = System.currentTimeMillis()
                dbHelper.insertVisit(placeName, lat, lon, now)

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_CHECK_LAT, lat.toString())
                    .putString(KEY_LAST_CHECK_LON, lon.toString())
                    .putLong(KEY_LAST_SYNC_TIME, now)
                    .apply()

                val count = dbHelper.getVisitCountForPlace(placeName)
                showAndSpeak("अनुमति मिल गई। '$placeName' का रिकॉर्ड सेव हो गया है। यहाँ आपकी यह $count वीं दर्ज विज़िट है।")
            }
            .setNegativeButton("नहीं, अभी नहीं") { dialog, _ ->
                dialog.dismiss()
                showAndSpeak("ठीक है, आपकी अनुमति के बिना यह डेटा सेव नहीं किया गया है।")
            }
            .show()

        if (ttsReady) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ask_500m")
        }
    }

    private fun resolvePlaceNameOnline(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale("hi", "IN"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull()
            val area = addr?.subLocality ?: addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            if (!area.isNullOrBlank()) area else "बाहर का इलाका (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)})"
        } catch (_: Exception) {
            "बाहर का इलाका (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)})"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 3. ऑफ़लाइन SQLite से जवाब या ऑनलाइन Gemini से सीखना (बिना हूबहू डुप्लिकेट के)
    private fun handleUserQuery(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.length < 2) {
            showAndSpeak("कृपया थोड़ा साफ़ और पूरा वाक्य लिखें या बोलें।")
            return
        }

        // पहले लोकल SQLite में खोजें (ऑफ़लाइन या तेज़ लोकल पकड़)
        val localMatch = dbHelper.findBestLocalMatch(cleanText)
        if (!isNetworkAvailable()) {
            if (localMatch != null) {
                showAndSpeak("[ऑफ़लाइन लोकल मेमोरी] $localMatch")
            } else {
                showAndSpeak("अभी इंटरनेट बंद है और इस अंदाज़ का रिकॉर्ड लोकल SQLite में नहीं मिला।")
            }
            return
        }

        // ऑनलाइन होने पर 12 घंटे चक्र / नई बात के हिसाब से सीखने की प्रक्रिया
        val now = System.currentTimeMillis()
        val lastSync = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_TIME, 0L)
        val within12hCycle = (now - lastSync) < SYNC_CYCLE_MS

        // यहाँ वास्तविक Gemini API कॉल जोड़ी जाएगी; अभी संरचित जवाब तैयार करने का स्थान:
        val instructorReply = fetchInstructorResponsePlaceholder(cleanText)

        if (!isValidCleanText(instructorReply)) {
            showAndSpeak("जवाब साफ़ या पूरा नहीं था, इसलिए इसे लोकल SQLite में सेव नहीं किया गया।")
            return
        }

        // हूबहू डुप्लिकेट जाँच: अगर बिल्कुल वही सवाल/जवाब पहले से है, तो दोबारा सेव न करें
        if (dbHelper.isExactDuplicate(cleanText, instructorReply)) {
            showAndSpeak("$instructorReply (यह बिल्कुल वैसी ही बात पहले से सेव है, डुप्लिकेट नहीं जोड़ा गया।)")
            return
        }

        // अगर कहने का अंदाज़/लफ़्ज़ नया है, तो लोकल SQLite में सेव करें
        dbHelper.insertLearnedItem(cleanText, instructorReply, now)
        if (!within12hCycle) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SYNC_TIME, now).apply()
        }

        showAndSpeak("$instructorReply (नया अंदाज़ लोकल SQLite में सुरक्षित कर लिया गया है।)")
    }

    private fun fetchInstructorResponsePlaceholder(query: String): String {
        // नोट: यहाँ आपकी Gemini API Key और HTTP/SDK कॉल आएगी।
        // जब तक API की नहीं जुड़ती, यह सुरक्षित फ़ॉलबैक है ताकि कुछ गलत-सलत सेव न हो।
        return "मैंने आपकी बात '$query' समझ ली है। (Gemini API की जोड़ने पर यह यहाँ से ताज़ा जवाब लाएगा।)"
    }

    private fun isValidCleanText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length >= 4 && !trimmed.contains("error", ignoreCase = true)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        dbHelper.close()
        super.onDestroy()
    }
}

// 4. लोकल SQLite डेटाबेस (सिर्फ़ आपके फ़ोन के अंदर, प्राइवेट और ऑफ़लाइन)
class JarvisDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "jarvis_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE visits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                place_name TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                visited_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE learned_knowledge (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prompt_text TEXT NOT NULL,
                response_text TEXT NOT NULL,
                normalized_key TEXT NOT NULL,
                learned_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS visits")
        db.execSQL("DROP TABLE IF EXISTS learned_knowledge")
        onCreate(db)
    }

    fun insertVisit(placeName: String, lat: Double, lon: Double, timestamp: Long) {
        val values = ContentValues().apply {
            put("place_name", placeName)
            put("lat", lat)
            put("lon", lon)
            put("visited_at", timestamp)
        }
        writableDatabase.insert("visits", null, values)
    }

    fun getVisitCountForPlace(placeName: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM visits WHERE place_name = ?",
            arrayOf(placeName)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun getVisitsSummary(): String {
        val sb = StringBuilder()
        readableDatabase.rawQuery(
            """
            SELECT place_name, COUNT(*) as cnt
            FROM visits
            GROUP BY place_name
            ORDER BY cnt DESC
            LIMIT 10
            """.trimIndent(),
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return "अभी तक बाहर की कोई विज़िट सेव नहीं हुई है।"
            }
            sb.append("दर्ज विज़िट का हिसाब: ")
            do {
                val place = cursor.getString(0)
                val cnt = cursor.getInt(1)
                sb.append("$place ($cnt बार), ")
            } while (cursor.moveToNext())
        }
        return sb.toString().removeSuffix(", ")
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isExactDuplicate(prompt: String, response: String): Boolean {
        val normPrompt = normalize(prompt)
        val normResp = normalize(response)
        readableDatabase.rawQuery(
            "SELECT prompt_text, response_text FROM learned_knowledge",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val oldP = normalize(cursor.getString(0))
                val oldR = normalize(cursor.getString(1))
                if (oldP == normPrompt && oldR == normResp) {
                    return true
                }
            }
        }
        return false
    }

    fun insertLearnedItem(prompt: String, response: String, timestamp: Long) {
        val values = ContentValues().apply {
            put("prompt_text", prompt)
            put("response_text", response)
            put("normalized_key", normalize(prompt))
            put("learned_at", timestamp)
        }
        writableDatabase.insert("learned_knowledge", null, values)
    }

    fun findBestLocalMatch(query: String): String? {
        val normQuery = normalize(query)
        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }.toSet()
        if (queryTokens.isEmpty()) return null

        var bestScore = 0.0
        var bestResponse: String? = null

        readableDatabase.rawQuery(
            "SELECT normalized_key, response_text FROM learned_knowledge ORDER BY id DESC LIMIT 200",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val resp = cursor.getString(1)

                if (key == normQuery) {
                    return resp
                }

                val keyTokens = key.split(" ").filter { it.length >= 2 }.toSet()
                if (keyTokens.isEmpty()) continue

                val overlap = queryTokens.intersect(keyTokens).size.toDouble()
                val denom = max(1, min(queryTokens.size, keyTokens.size)).toDouble()
                val score = overlap / denom

                // सख़्त मिलान (कम से कम 60% शब्द मेल खाएँ) ताकि गलत चीज़ न उठे
                if (score >= 0.6 && score > bestScore) {
                    bestScore = score
                    bestResponse = resp
                }
            }
        }
        return bestResponse
    }
}

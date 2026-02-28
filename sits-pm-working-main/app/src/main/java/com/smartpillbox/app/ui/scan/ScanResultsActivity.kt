package com.smartpillbox.app.ui.scan

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartpillbox.app.BuildConfig
import com.smartpillbox.app.SmartPillBoxApp
import com.smartpillbox.app.data.local.AlarmEntity
import com.smartpillbox.app.data.local.MedicineEntity
import com.smartpillbox.app.data.model.Medicine
import com.smartpillbox.app.data.remote.FirebaseSyncManager
import com.smartpillbox.app.databinding.ActivityScanResultsBinding
import com.smartpillbox.app.util.ScheduleGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ScanResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultsBinding
    private val medicineList = mutableListOf<Medicine>()
    private lateinit var adapter: MedicineAdapter
    private val TAG = "ScanDebug"

    private var mode: String? = null
    private var patientCode: String? = null

    // ── Change this to your laptop's IP (run `ipconfig` on Windows / `ifconfig` on Mac) ──
    private val FLASK_IP   = "192.168.1.50"
    private val FLASK_URL  = "http://$FLASK_IP:5000/upload"

    private val GEMINI_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Dose slot times collected from user (HH:MM strings) ──
    // These are set ONCE for the whole prescription
    // morning → index 0, afternoon → index 1, night → index 2
    private var morningTime:   String = "08:00"
    private var afternoonTime: String = "13:00"
    private var nightTime:     String = "21:00"

    // medicines with times_per_day stored from Gemini parse
    // key = medicine name, value = times_per_day count
    private val timesPerDayMap = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode        = intent.getStringExtra("mode")
        patientCode = intent.getStringExtra("patient_code")
            ?: FirebaseSyncManager.getPatientCode(this)

        adapter = MedicineAdapter(medicineList) { position ->
            adapter.removeItem(position)
        }
        binding.rvMedicines.layoutManager = LinearLayoutManager(this)
        binding.rvMedicines.adapter = adapter

        showProgress("Preparing scan...")

        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString != null) {
            runPipeline(Uri.parse(imageUriString))
        } else {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnRescan.setOnClickListener { finish() }
        // Confirm button → ask user for their meal times → send to Flask + save to DB
        binding.btnConfirm.setOnClickListener { askMealTimesAndSend() }
    }

    // ===================================================
    //  FULL OCR + GEMINI PIPELINE
    // ===================================================
    private fun runPipeline(imageUri: Uri) {
        binding.btnConfirm.isEnabled = false
        val debug = StringBuilder()

        lifecycleScope.launch {
            try {
                showProgress("Step 1/3: Reading prescription text...")
                debug.append("=== STAGE 1: OCR ===\n")

                val rawText = withContext(Dispatchers.IO) { doOcr(imageUri) }

                if (rawText.isBlank()) {
                    showDebugError("❌ No text detected.\n\nTips:\n• Use good lighting\n• Hold camera steady\n• Ensure text is in focus")
                    return@launch
                }

                debug.append("✅ OCR OK: ${rawText.length} chars\n\n")
                debug.append("--- OCR TEXT ---\n$rawText\n--- END ---\n\n")

                showProgress("Step 2/3: AI analyzing medicines...\n\nOCR found ${rawText.length} chars")
                debug.append("=== STAGE 2: Gemini API ===\n")

                val apiKeys = getApiKeys()
                if (apiKeys.isEmpty()) {
                    showDebugError("❌ No Gemini API keys configured.\n\nAdd to local.properties:\nGEMINI_API_KEY_1=your_key\n\nOCR worked! Text:\n$rawText")
                    return@launch
                }

                debug.append("Keys: ${apiKeys.size}, Models: ${GEMINI_MODELS.size}\n")

                val medicines = withContext(Dispatchers.IO) {
                    callGeminiAndParse(rawText, apiKeys, debug)
                }

                debug.append("Final result: ${medicines.size} medicines\n")

                withContext(Dispatchers.Main) {
                    if (medicines.isNotEmpty()) {
                        medicineList.clear()
                        medicineList.addAll(medicines)
                        adapter.notifyDataSetChanged()
                        showResults(medicines.size)
                        binding.btnConfirm.isEnabled = true
                    } else {
                        showDebugError("⚠️ No medicines extracted.\n\nDebug:\n$debug\n\nOCR text:\n$rawText")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline crashed", e)
                showDebugError("❌ Fatal error: ${e.javaClass.simpleName}\n${e.message}\n\nDebug:\n$debug")
            }
        }
    }

    // ===================================================
    //  OCR — handles both file:// and content:// URIs
    // ===================================================
    private suspend fun doOcr(uri: Uri): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val image = createInputImage(uri)
                if (image == null) {
                    if (cont.isActive) cont.resume("")
                    return@suspendCancellableCoroutine
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result.text)
                        recognizer.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed", e)
                        if (cont.isActive) cont.resume("")
                        recognizer.close()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "OCR setup crashed", e)
                if (cont.isActive) cont.resume("")
            }
        }
    }

    private fun createInputImage(uri: Uri): InputImage? {
        return try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val bmp = loadAndCorrectBitmap(uri)
                if (bmp != null) InputImage.fromBitmap(bmp, 0) else null
            } else {
                InputImage.fromFilePath(this, uri)
            }
        } catch (e: Exception) {
            try {
                val bmp = loadAndCorrectBitmap(uri)
                if (bmp != null) InputImage.fromBitmap(bmp, 0) else null
            } catch (e2: Exception) { null }
        }
    }

    private fun loadAndCorrectBitmap(uri: Uri): Bitmap? {
        return try {
            var stream: InputStream? = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (original == null) return null

            stream = contentResolver.openInputStream(uri)
            val rotation = try {
                val exif = ExifInterface(stream!!)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f } finally { stream?.close() }

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } else original
        } catch (e: Exception) { null }
    }

    // ===================================================
    //  API Key helpers
    // ===================================================
    private fun getApiKeys(): List<String> {
        val keys = mutableListOf<String>()
        val k1 = BuildConfig.GEMINI_API_KEY_1
        val k2 = BuildConfig.GEMINI_API_KEY_2
        val k3 = BuildConfig.GEMINI_API_KEY_3
        val k4 = BuildConfig.GEMINI_API_KEY_4
        if (k1.isNotBlank()) keys.add(k1)
        if (k2.isNotBlank()) keys.add(k2)
        if (k3.isNotBlank()) keys.add(k3)
        if (k4.isNotBlank()) keys.add(k4)
        return keys
    }

    // ===================================================
    //  GEMINI CALL + PARSE
    // ===================================================
    private fun callGeminiAndParse(rawText: String, apiKeys: List<String>, debug: StringBuilder): List<Medicine> {
        for ((keyIndex, apiKey) in apiKeys.withIndex()) {
            debug.append("\n-- Key #${keyIndex + 1} --\n")
            for (model in GEMINI_MODELS) {
                debug.append("  $model -> ")
                val result = callGeminiRaw(rawText, apiKey, model, debug)
                when {
                    result == null               -> { debug.append("null\n"); continue }
                    result == "RATE_LIMITED"     -> { debug.append("429 rate limited\n"); break }
                    result == "NOT_FOUND"        -> { debug.append("404\n"); continue }
                    result.startsWith("ERROR:")  -> { debug.append("$result\n"); continue }
                    else -> {
                        debug.append("SUCCESS (${result.length} chars)\n")
                        debug.append("\n=== STAGE 3: Parse ===\n")
                        val medicines = parseGeminiResponse(result, debug)
                        if (medicines.isNotEmpty()) return medicines
                        debug.append("Parse returned 0 — trying next model\n")
                    }
                }
            }
        }
        return emptyList()
    }

    private fun callGeminiRaw(rawText: String, apiKey: String, model: String, debug: StringBuilder): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val prompt = buildString {
                appendLine("You are a medical prescription parser. Your ONLY job is to output a JSON array.")
                appendLine()
                appendLine("EXTRACT all medicines from the OCR text below.")
                appendLine("RULES:")
                appendLine("- Output ONLY a JSON array, starting with [ and ending with ]")
                appendLine("- Do NOT output markdown, do NOT output ``` fences, do NOT explain anything")
                appendLine("- Every object must have exactly these keys: name, dosage, frequency, times_per_day, meal_timing, duration")
                appendLine("- times_per_day: 1-0-1 = 2 times, 1-1-1 = 3 times, 0-0-1 = 1 time, 1-0-0 = 1 time")
                appendLine("- Dose pattern M-A-N means Morning-Afternoon-Night, count non-zero values for times_per_day")
                appendLine("- mouthwash / ointment / gel are also medicines — include them")
                appendLine("- Fix OCR errors (e.g. 'clohex heal' = 'Clohex Heal', 'disperzyme' = 'Disperzyme')")
                appendLine()
                appendLine("EXAMPLE OUTPUT FORMAT:")
                appendLine("""[{"name":"Clohex Heal Mouthwash","dosage":"10ml","frequency":"Twice daily","times_per_day":2,"meal_timing":"After meals","duration":"15 days"},{"name":"Ketorol DT","dosage":"1 tablet","frequency":"Twice daily","times_per_day":2,"meal_timing":"Before meals","duration":"5 days"}]""")
                appendLine()
                appendLine("NOW PARSE THIS PRESCRIPTION:")
                append(rawText)
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("topK", 1)
                    put("topP", 1.0)
                    put("maxOutputTokens", 4096)
                })
            }

            val body     = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request  = Request.Builder().url(url).post(body).build()
            val response = httpClient.newCall(request).execute()
            val code     = response.code
            val respBody = response.body?.string() ?: ""

            when (code) {
                200 -> {
                    val obj = JSONObject(respBody)
                    val candidates = obj.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        val reason = obj.optJSONObject("promptFeedback")?.optString("blockReason", "unknown")
                        return "ERROR: blocked ($reason)"
                    }
                    val text = candidates.getJSONObject(0)
                        .optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.getJSONObject(0)
                        ?.optString("text", "") ?: ""
                    if (text.isBlank()) "ERROR: empty text" else text
                }
                429  -> "RATE_LIMITED"
                404  -> "NOT_FOUND"
                401  -> "ERROR: 401 Invalid API key"
                403  -> "ERROR: 403 API not enabled"
                else -> "ERROR: HTTP $code"
            }
        } catch (e: java.net.UnknownHostException) { "ERROR: No internet" }
        catch (e: java.net.SocketTimeoutException) { "ERROR: Timeout" }
        catch (e: Exception) { "ERROR: ${e.javaClass.simpleName}: ${e.message}" }
    }

    // ===================================================
    //  PARSE GEMINI RESPONSE — ultra robust
    // ===================================================
    private fun parseGeminiResponse(response: String, debug: StringBuilder): List<Medicine> {
        var text = response.trim()
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        debug.append("Cleaned (first 300): ${text.take(300)}\n")

        val directResult = tryParseJsonArray(text, debug)
        if (directResult.isNotEmpty()) return directResult

        val start = text.indexOf('[')
        val end   = text.lastIndexOf(']')

        if (start == -1) return tryExtractObjects(text, debug)
        if (end == -1 || end <= start) return tryFixTruncatedJson(text.substring(start), debug)

        val slice = text.substring(start, end + 1)
        val sliceResult = tryParseJsonArray(slice, debug)
        if (sliceResult.isNotEmpty()) return sliceResult

        return tryFixTruncatedJson(text.substring(start), debug)
    }

    private fun tryParseJsonArray(json: String, debug: StringBuilder): List<Medicine> {
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = Gson().fromJson(json, type)
            debug.append("Direct parse: ${rawList.size} items\n")
            mapToMedicines(rawList, debug)
        } catch (e: JsonSyntaxException) { debug.append("Direct parse failed: ${e.message?.take(80)}\n"); emptyList() }
        catch (e: Exception) { debug.append("Parse exception: ${e.message?.take(80)}\n"); emptyList() }
    }

    private fun tryFixTruncatedJson(truncated: String, debug: StringBuilder): List<Medicine> {
        debug.append("Attempting truncated JSON recovery\n")
        val medicines = mutableListOf<Medicine>()
        try {
            var depth = 0; var inString = false; var escape = false; var objStart = -1
            for (i in truncated.indices) {
                val c = truncated[i]
                if (escape) { escape = false; continue }
                if (c == '\\' && inString) { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (inString) continue
                when (c) {
                    '{' -> { if (depth == 0) objStart = i; depth++ }
                    '}' -> {
                        depth--
                        if (depth == 0 && objStart >= 0) {
                            try {
                                val type = object : TypeToken<Map<String, Any>>() {}.type
                                val obj: Map<String, Any> = Gson().fromJson(truncated.substring(objStart, i + 1), type)
                                mapSingleToMedicine(obj)?.let { medicines.add(it); debug.append("  Recovered: ${it.name}\n") }
                            } catch (e: Exception) { /* skip */ }
                            objStart = -1
                        }
                    }
                }
            }
        } catch (e: Exception) { debug.append("Recovery crashed: ${e.message}\n") }
        debug.append("Recovery found: ${medicines.size}\n")
        return medicines
    }

    private fun tryExtractObjects(text: String, debug: StringBuilder): List<Medicine> {
        debug.append("Trying object extraction\n")
        val medicines = mutableListOf<Medicine>()
        try {
            val objRegex = Regex("""\{[^{}]*"name"\s*:\s*"[^"]+[^{}]*\}""")
            for (match in objRegex.findAll(text)) {
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val obj: Map<String, Any> = Gson().fromJson(match.value, type)
                    mapSingleToMedicine(obj)?.let { medicines.add(it) }
                } catch (e: Exception) { /* skip */ }
            }
        } catch (e: Exception) { debug.append("Extraction failed: ${e.message}\n") }
        return medicines
    }

    private fun mapToMedicines(rawList: List<Map<String, Any>>, debug: StringBuilder): List<Medicine> {
        return rawList.mapNotNull { m ->
            val med = mapSingleToMedicine(m)
            if (med != null) {
                // Store times_per_day for schedule building
                val tpd = when (val v = m["times_per_day"]) {
                    is Double -> v.toInt()
                    is Int    -> v
                    is String -> v.toIntOrNull() ?: 1
                    else      -> 1
                }
                timesPerDayMap[med.name] = tpd
                debug.append("  ✓ ${med.name} | ${med.dosage} | ${med.frequency} | tpd=$tpd\n")
            }
            med
        }
    }

    private fun mapSingleToMedicine(m: Map<String, Any>): Medicine? {
        val name   = (m["name"]        as? String)?.trim() ?: return null
        if (name.length < 2) return null
        val dosage = (m["dosage"]      as? String)?.trim()
        val freq   = (m["frequency"]   as? String)?.trim()
        val meal   = (m["meal_timing"] as? String)?.trim()
        val dur    = (m["duration"]    as? String)?.trim()

        val freqStr = buildString {
            append(if (freq.isNullOrBlank()) "As prescribed" else freq)
            if (!dur.isNullOrBlank()) append(" ($dur)")
        }
        return Medicine(
            name      = name,
            dosage    = if (dosage.isNullOrBlank()) "See prescription" else dosage,
            frequency = freqStr,
            timing    = if (meal.isNullOrBlank()) "As directed" else meal
        )
    }

    // ===================================================
    //  STEP 1: Ask user for their Morning / Afternoon / Night times
    //  Called when Confirm button is tapped
    // ===================================================
    private fun askMealTimesAndSend() {
        if (medicineList.isEmpty()) {
            Toast.makeText(this, "No medicines to save", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnConfirm.isEnabled = false

        AlertDialog.Builder(this)
            .setTitle("⏰ Set Your Meal Times")
            .setMessage(
                "We need your usual meal times to schedule medicine reminders.\n\n" +
                        "You'll set:\n" +
                        "  🌅 Morning time\n" +
                        "  ☀️ Afternoon time\n" +
                        "  🌙 Night time\n\n" +
                        "Tap OK to begin."
            )
            .setCancelable(false)
            .setPositiveButton("Set Times") { _, _ -> pickMorningTime() }
            .setNegativeButton("Cancel") { _, _ -> binding.btnConfirm.isEnabled = true }
            .show()
    }

    private fun pickMorningTime() {
        TimePickerDialog(this, { _, h, m ->
            morningTime = "%02d:%02d".format(h, m)
            pickAfternoonTime()
        }, 8, 0, true).apply {
            setTitle("🌅 Morning dose time")
            setOnCancelListener { pickAfternoonTime() } // skip = keep default
            show()
        }
    }

    private fun pickAfternoonTime() {
        TimePickerDialog(this, { _, h, m ->
            afternoonTime = "%02d:%02d".format(h, m)
            pickNightTime()
        }, 13, 0, true).apply {
            setTitle("☀️ Afternoon dose time")
            setOnCancelListener { pickNightTime() }
            show()
        }
    }

    private fun pickNightTime() {
        TimePickerDialog(this, { _, h, m ->
            nightTime = "%02d:%02d".format(h, m)
            buildAndSend()
        }, 21, 0, true).apply {
            setTitle("🌙 Night dose time")
            setOnCancelListener { buildAndSend() }
            show()
        }
    }

    // ===================================================
    //  STEP 2: Build schedule JSON + send to Flask + save to DB
    // ===================================================
    private fun buildAndSend() {
        showProgress("📡 Sending schedule to pill box server...")

        lifecycleScope.launch {
            // ── Build the Flask schedule payload ──
            // Group medicines by their dose slots → one entry per time slot
            // e.g. medicines taken at Morning: [med1, med2], compartments [1,2]
            //      medicines taken at Night:   [med3],       compartments [3]

            data class SlotEntry(
                val time: String,
                val label: String,
                val medicines: MutableList<String>,
                val compartments: MutableList<Int>
            )

            val morningEntry   = SlotEntry(morningTime,   "Morning",   mutableListOf(), mutableListOf())
            val afternoonEntry = SlotEntry(afternoonTime, "Afternoon", mutableListOf(), mutableListOf())
            val nightEntry     = SlotEntry(nightTime,     "Night",     mutableListOf(), mutableListOf())

            medicineList.forEachIndexed { index, med ->
                val compartment = index + 1  // compartment 1 = first medicine, etc.
                val tpd = timesPerDayMap[med.name] ?: 1

                when (tpd) {
                    1 -> {
                        // Once daily → morning
                        morningEntry.medicines.add("${med.name} ${med.dosage}")
                        morningEntry.compartments.add(compartment)
                    }
                    2 -> {
                        // Twice daily → morning + night
                        morningEntry.medicines.add("${med.name} ${med.dosage}")
                        morningEntry.compartments.add(compartment)
                        nightEntry.medicines.add("${med.name} ${med.dosage}")
                        nightEntry.compartments.add(compartment)
                    }
                    3 -> {
                        // Three times → morning + afternoon + night
                        morningEntry.medicines.add("${med.name} ${med.dosage}")
                        morningEntry.compartments.add(compartment)
                        afternoonEntry.medicines.add("${med.name} ${med.dosage}")
                        afternoonEntry.compartments.add(compartment)
                        nightEntry.medicines.add("${med.name} ${med.dosage}")
                        nightEntry.compartments.add(compartment)
                    }
                    else -> {
                        // 4+ times or SOS → just morning as safe default
                        morningEntry.medicines.add("${med.name} ${med.dosage}")
                        morningEntry.compartments.add(compartment)
                    }
                }
            }

            // Build JSON array — only include slots that have medicines
            val scheduleArray = JSONArray()
            for (entry in listOf(morningEntry, afternoonEntry, nightEntry)) {
                if (entry.compartments.isEmpty()) continue
                val obj = JSONObject().apply {
                    put("time",        entry.time)
                    put("label",       entry.label)
                    put("compartments", JSONArray(entry.compartments))
                    put("medicines",   JSONArray(entry.medicines))
                }
                scheduleArray.put(obj)
            }

            Log.d(TAG, "Schedule JSON:\n$scheduleArray")

            // ── Send to Flask ──
            val flaskResult = withContext(Dispatchers.IO) {
                try {
                    val body    = scheduleArray.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder().url(FLASK_URL).post(body).build()
                    val resp    = httpClient.newCall(request).execute()
                    if (resp.isSuccessful) {
                        "✅ Schedule sent to server!\n${resp.body?.string()?.take(100)}"
                    } else {
                        "⚠️ Server responded: ${resp.code}"
                    }
                } catch (e: java.net.ConnectException) {
                    "⚠️ Cannot reach Flask server ($FLASK_IP:5000)\n\nCheck:\n• Laptop & phone on same WiFi\n• server.py is running\n• Correct IP in FLASK_IP"
                } catch (e: java.net.SocketTimeoutException) {
                    "⚠️ Flask server timed out"
                } catch (e: Exception) {
                    "⚠️ Flask error: ${e.message}"
                }
            }

            Log.d(TAG, "Flask result: $flaskResult")

            // ── Always save to local DB regardless of Flask result ──
            saveToDatabase()

            // ── Show result ──
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ScanResultsActivity)
                    .setTitle("Schedule Set!")
                    .setMessage(
                        "$flaskResult\n\n" +
                                "📅 Schedule:\n" +
                                buildString {
                                    for (entry in listOf(morningEntry, afternoonEntry, nightEntry)) {
                                        if (entry.medicines.isEmpty()) continue
                                        appendLine("${entry.label} (${entry.time}):")
                                        entry.medicines.forEach { appendLine("  • $it") }
                                    }
                                }
                    )
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }
    }

    // ===================================================
    //  STEP 3: Save to local Room DB + Firebase
    // ===================================================
    private fun saveToDatabase() {
        val db      = SmartPillBoxApp.instance.database
        val addedBy = if (mode == "caregiver") "caregiver" else "patient"
        val code    = patientCode ?: ""

        // Build TimeSelection list from global meal times
        val customTimes = mutableMapOf<String, List<TimeSelectionLocal>>()
        for (med in medicineList) {
            val tpd = timesPerDayMap[med.name] ?: 1
            val slots = mutableListOf<TimeSelectionLocal>()
            val mParts = morningTime.split(":")
            val aParts = afternoonTime.split(":")
            val nParts = nightTime.split(":")

            when (tpd) {
                1    -> slots.add(TimeSelectionLocal(mParts[0].toInt(), mParts[1].toInt(), "Morning"))
                2    -> {
                    slots.add(TimeSelectionLocal(mParts[0].toInt(), mParts[1].toInt(), "Morning"))
                    slots.add(TimeSelectionLocal(nParts[0].toInt(), nParts[1].toInt(), "Night"))
                }
                3    -> {
                    slots.add(TimeSelectionLocal(mParts[0].toInt(), mParts[1].toInt(), "Morning"))
                    slots.add(TimeSelectionLocal(aParts[0].toInt(), aParts[1].toInt(), "Afternoon"))
                    slots.add(TimeSelectionLocal(nParts[0].toInt(), nParts[1].toInt(), "Night"))
                }
                else -> slots.add(TimeSelectionLocal(mParts[0].toInt(), mParts[1].toInt(), "Morning"))
            }
            customTimes[med.name] = slots
        }

        // Run DB insert on IO thread (fire-and-forget from coroutine already running)
        lifecycleScope.launch(Dispatchers.IO) {
            for (med in medicineList) {
                val entity = MedicineEntity(
                    name = med.name, dosage = med.dosage,
                    frequency = med.frequency, timing = med.timing,
                    addedBy = addedBy, ownerPatientCode = code
                )
                val id    = db.medicineDao().insertMedicine(entity)
                val saved = entity.copy(id = id.toInt())

                if (code.isNotBlank()) FirebaseSyncManager.syncMedicineToFirebase(code, saved)

                val pickedTimes = customTimes[med.name]
                val alarms: List<AlarmEntity> = if (!pickedTimes.isNullOrEmpty()) {
                    pickedTimes.map { ts ->
                        AlarmEntity(
                            medicineId = saved.id, medicineName = saved.name,
                            dosage = saved.dosage, hour = ts.hour, minute = ts.minute,
                            label = ts.label, isEnabled = true, isAutoGenerated = false
                        )
                    }
                } else {
                    ScheduleGenerator.generateAlarms(saved)
                }

                if (alarms.isNotEmpty()) {
                    db.alarmDao().insertAll(alarms)
                    if (code.isNotBlank()) alarms.forEach { FirebaseSyncManager.syncAlarmToFirebase(code, it) }
                }
            }
        }
    }

    // Local data class (not Medicine's TimeSelection which is in Time Picker Flow)
    private data class TimeSelectionLocal(val hour: Int, val minute: Int, val label: String)

    // ===================================================
    //  UI Helpers
    // ===================================================
    private fun showProgress(statusText: String) {
        runOnUiThread {
            binding.layoutProgress.visibility  = View.VISIBLE
            binding.tvProgressStatus.text      = statusText
            binding.layoutDebug.visibility     = View.GONE
            binding.tvSuccessHeader.visibility = View.GONE
            binding.tvDetectedLabel.visibility = View.GONE
        }
    }

    private fun showResults(count: Int) {
        runOnUiThread {
            binding.layoutProgress.visibility  = View.GONE
            binding.layoutDebug.visibility     = View.GONE
            binding.tvSuccessHeader.text       = "✅ Detected $count medicine(s)"
            binding.tvSuccessHeader.visibility = View.VISIBLE
            binding.tvDetectedLabel.visibility = View.VISIBLE
        }
    }

    private fun showDebugError(text: String) {
        runOnUiThread {
            binding.layoutProgress.visibility  = View.GONE
            binding.tvSuccessHeader.visibility = View.GONE
            binding.tvDetectedLabel.visibility = View.GONE
            binding.layoutDebug.visibility     = View.VISIBLE
            binding.tvRawText.text             = text
        }
    }
}
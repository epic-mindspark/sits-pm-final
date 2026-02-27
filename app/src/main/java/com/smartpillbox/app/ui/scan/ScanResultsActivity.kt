package com.smartpillbox.app.ui.scan

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartpillbox.app.BuildConfig
import com.smartpillbox.app.SmartPillBoxApp
import com.smartpillbox.app.data.local.MedicineEntity
import com.smartpillbox.app.data.model.Medicine
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ScanResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultsBinding
    private val medicineList = mutableListOf<Medicine>()
    private lateinit var adapter: MedicineAdapter
    private val TAG = "ScanDebug"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MedicineAdapter(medicineList) { position ->
            adapter.removeItem(position)
        }
        binding.rvMedicines.layoutManager = LinearLayoutManager(this)
        binding.rvMedicines.adapter = adapter

        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString != null) {
            runPipeline(Uri.parse(imageUriString))
        } else {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.tvRawText.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()

        binding.btnRescan.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { saveAndCreateAlarms() }
    }

    // ═══════════════════════════════════════════════════
    //  FULL PIPELINE
    // ═══════════════════════════════════════════════════
    private fun runPipeline(imageUri: Uri) {
        binding.btnConfirm.isEnabled = false
        val debug = StringBuilder()

        lifecycleScope.launch {
            try {
                // ── STAGE 1: OCR ──
                showStatus("Step 1/3: Reading text from image...")
                debug.append("═══ STAGE 1: OCR ═══\n")

                val rawText = withContext(Dispatchers.IO) { doOcr(imageUri) }

                if (rawText.isBlank()) {
                    debug.append("FAILED: No text found\n")
                    showStatus(debug.toString() + "\nNo text detected. Try better lighting.")
                    return@launch
                }

                debug.append("OK: ${rawText.length} chars\n")
                debug.append(rawText).append("\n\n")
                showStatus(debug.toString())

                // ── STAGE 2: Gemini API ──
                debug.append("Step 2/3: AI analyzing medicines...\n")
                showStatus(debug.toString())
                debug.append("═══ STAGE 2: Gemini API ═══\n")

                val apiKey = BuildConfig.GEMINI_API_KEY
                debug.append("Key: ${apiKey.take(12)}... (${apiKey.length} chars)\n")

                if (apiKey.isBlank()) {
                    debug.append("FAILED: No API key\n")
                    showStatus(debug.toString())
                    return@launch
                }

                val geminiJson = withContext(Dispatchers.IO) {
                    callGeminiDirectHttp(rawText, apiKey, debug)
                }

                showStatus(debug.toString())

                if (geminiJson == null) {
                    debug.append("FAILED: No response from Gemini\n")
                    showStatus(debug.toString())
                    return@launch
                }

                // ── STAGE 3: Parse JSON ──
                debug.append("\n═══ STAGE 3: Parse ═══\n")
                debug.append("Step 3/3: Processing results...\n")
                showStatus(debug.toString())

                val medicines = parseResponse(geminiJson, debug)
                debug.append("Result: ${medicines.size} medicines\n")

                // ── DISPLAY ──
                withContext(Dispatchers.Main) {
                    if (medicines.isNotEmpty()) {
                        val cleanText = buildString {
                            append(rawText)
                            append("\n\n✅ ${medicines.size} medicine(s) detected by AI")
                        }
                        binding.tvRawText.text = cleanText
                        medicineList.clear()
                        medicineList.addAll(medicines)
                        adapter.notifyDataSetChanged()
                        binding.btnConfirm.isEnabled = true
                    } else {
                        showStatus(debug.toString())
                    }
                }

            } catch (e: Exception) {
                debug.append("\nFATAL ERROR: ${e.javaClass.simpleName}: ${e.message}\n")
                Log.e(TAG, "Pipeline failed", e)
                showStatus(debug.toString())
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  STAGE 1: ML Kit OCR
    // ═══════════════════════════════════════════════��═══
    private suspend fun doOcr(uri: Uri): String {
        return suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(this@ScanResultsActivity, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { v ->
                        if (cont.isActive) cont.resume(v.text)
                        recognizer.close()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR fail", e)
                        if (cont.isActive) cont.resume("")
                        recognizer.close()
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  STAGE 2: Gemini via Direct HTTP (no SDK needed)
    // ═══════════════════════════════════════════════════
    private fun callGeminiDirectHttp(
        rawText: String,
        apiKey: String,
        debug: StringBuilder
    ): String? {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"
            val prompt = """You are a medical prescription parser. Extract ONLY medicines explicitly present in the OCR text.

RULES:
- NEVER invent medicine names. Only extract what is in the text.
- Fix OCR typos (e.g. "CALP0L"→"CALPOL")
- "Syp"=Syrup, "Tab"=Tablet, "Cap"=Capsule, "Inj"=Injection
- OD=once daily, BD=twice daily, TDS=3x daily, QID=4x daily, Q6H=every 6 hours, SOS=as needed
- "x 3d"=for 3 days
- Add generic name in parentheses if known
- Return ONLY a JSON array. No markdown. No code fences. No explanation.

Format: [{"name":"","dosage":"","frequency":"","times_per_day":0,"meal_timing":"","duration":""}]

OCR TEXT:
$rawText"""

            // Build JSON request body
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("topP", 0.8)
                    put("maxOutputTokens", 2048)
                })
            }

            debug.append("URL: ${url.take(80)}...\n")
            debug.append("Request body: ${requestJson.toString().length} chars\n")
            debug.append("Sending request...\n")

            val body = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            debug.append("Response code: $responseCode\n")
            debug.append("Response length: ${responseBody.length} chars\n")

            if (responseCode != 200) {
                debug.append("ERROR RESPONSE:\n${responseBody.take(500)}\n")
                Log.e(TAG, "Gemini HTTP error $responseCode: $responseBody")
                return null
            }

            // Extract text from Gemini response
            val responseObj = JSONObject(responseBody)
            val candidates = responseObj.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                debug.append("ERROR: No candidates in response\n")
                debug.append("Full response:\n${responseBody.take(500)}\n")
                return null
            }

            val content = candidates.getJSONObject(0)
                .optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.getJSONObject(0)?.optString("text", "") ?: ""

            debug.append("Gemini text: ${text.take(300)}\n")
            Log.d(TAG, "Gemini response text: $text")

            return text

        } catch (e: Exception) {
            debug.append("HTTP ERROR: ${e.javaClass.simpleName}: ${e.message}\n")
            Log.e(TAG, "HTTP call failed", e)
            return null
        }
    }

    // ═══════════════════════════════════════════════════
    //  STAGE 3: Parse JSON → List<Medicine>
    // ═══════════════════════════════════════════════════
    private fun parseResponse(response: String, debug: StringBuilder): List<Medicine> {
        try {
            var json = response.trim()

            // Remove markdown fences
            if (json.contains("```")) {
                json = json.replace("```json", "")
                    .replace("```JSON", "")
                    .replace("```", "")
                    .trim()
            }

            val start = json.indexOf('[')
            val end = json.lastIndexOf(']')
            debug.append("Array: [$start..$end]\n")

            if (start == -1 || end == -1 || end <= start) {
                debug.append("No JSON array found in:\n${json.take(200)}\n")
                return emptyList()
            }

            json = json.substring(start, end + 1)
            debug.append("JSON: ${json.take(200)}\n")

            val gson = Gson()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(json, type)

            debug.append("Items parsed: ${rawList.size}\n")

            return rawList
                .filter { m ->
                    val n = (m["name"] as? String)?.trim() ?: ""
                    n.isNotBlank() && n.length >= 2
                }
                .map { m ->
                    val name = (m["name"] as? String)?.trim() ?: ""
                    val dosage = (m["dosage"] as? String)?.trim()
                    val freq = (m["frequency"] as? String)?.trim()
                    val meal = (m["meal_timing"] as? String)?.trim()
                    val dur = (m["duration"] as? String)?.trim()

                    val freqStr = buildString {
                        append(if (freq.isNullOrBlank()) "As prescribed" else freq)
                        if (!dur.isNullOrBlank()) append(" ($dur)")
                    }

                    debug.append("  ✓ $name | ${dosage ?: "-"} | $freqStr\n")

                    Medicine(
                        name = name,
                        dosage = if (dosage.isNullOrBlank()) "See prescription" else dosage,
                        frequency = freqStr,
                        timing = if (meal.isNullOrBlank()) "As directed" else meal
                    )
                }
        } catch (e: Exception) {
            debug.append("Parse error: ${e.message}\n")
            Log.e(TAG, "Parse failed", e)
            return emptyList()
        }
    }

    // ═══════════════════════════════════════════════════
    //  SAVE + ALARMS
    // ═══════════════════════════════════════════════════
    private fun saveAndCreateAlarms() {
        if (medicineList.isEmpty()) {
            Toast.makeText(this, "No medicines to save", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnConfirm.isEnabled = false
        val db = SmartPillBoxApp.instance.database

        lifecycleScope.launch {
            var alarmCount = 0

            withContext(Dispatchers.IO) {
                for (med in medicineList) {
                    val entity = MedicineEntity(
                        name = med.name,
                        dosage = med.dosage,
                        frequency = med.frequency,
                        timing = med.timing
                    )
                    val id = db.medicineDao().insertMedicine(entity)
                    val saved = entity.copy(id = id.toInt())
                    val alarms = ScheduleGenerator.generateAlarms(saved)
                    if (alarms.isNotEmpty()) {
                        db.alarmDao().insertAll(alarms)
                        alarmCount += alarms.size
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ScanResultsActivity,
                    "${medicineList.size} medicine(s) saved, $alarmCount alarm(s) created",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun showStatus(text: String) {
        runOnUiThread { binding.tvRawText.text = text }
    }
}
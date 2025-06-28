package com.example.blurr.api

import android.content.Context
import com.example.blurr.agent.ClickableInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.blurr.utilities.ImageHelper
import com.example.blurr.utilities.withRetry
import java.io.ByteArrayOutputStream
import java.io.IOException

class Retina(
    private val context: Context,
    private val eyes: Eyes,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val clientWithTimeouts = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Connection timeout
        .readTimeout(60, TimeUnit.SECONDS)    // Read timeout
        .writeTimeout(60, TimeUnit.SECONDS)   // Write timeout
        .build()
    private suspend fun callGeminiApi(bitmap: Bitmap): String? {
        return try {
            // Use the generic withRetry function. All retry logic is handled for us.
            withRetry(times = 3) {
                val imgHelp = ImageHelper()
                val base64Image = imgHelp.bitmapToBase64(bitmap)
                val payload = JSONObject().apply {
                    put("model", "gemini-2.0-flash")
                    val contentArray = JSONArray().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("inline_data", JSONObject()
                                .put("mime_type", "image/png")
                                .put("data", base64Image)
                            ))
                            put(JSONObject().put("text", boundingBoxSystemInstructionsv3))
                        })
                    }
                    put("contents", contentArray)
                    put("generationConfig", JSONObject().apply { /* ... your schema ... */ })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                clientWithTimeouts.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Gemini API call failed: ${response.code}")
                    val resultJson = JSONObject(response.body?.string() ?: "")
                    resultJson.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                }
            }
        } catch (e: Exception) {
            Log.e("Retina", "Gemini API failed after all retries.", e)
            null // Return null if all retries fail
        }
    }

    private suspend fun callOcrApi(bitmap: Bitmap): String? {
        return try {
            // Use the same generic withRetry function for the OCR call.
            withRetry(times = 3) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val imageBytes = outputStream.toByteArray()

                val requestBodyOCR = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "screenshot.png", imageBytes.toRequestBody("image/png".toMediaType()))
                    .build()

                val requestOCR = Request.Builder()
                    .url("http://10.0.2.2:8000/ocr")
                    .post(requestBodyOCR)
                    .build()

                client.newCall(requestOCR).execute().use { responseOCR ->
                    if (!responseOCR.isSuccessful) throw IOException("OCR API call failed: ${responseOCR.code}")
                    responseOCR.body?.string() ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e("Retina", "OCR API failed after all retries.", e)
            null // Return null if all retries fail
        }
    }

    private fun extractJsonArray(text: String): List<JSONObject> {
        val regex = Regex("""\[\s*\{.*?\}\s*]""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(text) ?: return emptyList()
        val array = JSONArray(match.value)
        return List(array.length()) { array.getJSONObject(it) }
    }

    fun sanitizeJson(json: String): String {
        var cleanJson = json

        // Remove trailing commas before object/array close
        cleanJson = cleanJson.replace(Regex(",\\s*([}\\]])"), "$1")

        // Remove dangling commas before closing brackets of arrays
        cleanJson = cleanJson.replace(Regex(",\\s*\\]"), "]")

        // Remove dangling commas before closing brackets of objects
        cleanJson = cleanJson.replace(Regex(",\\s*\\}"), "}")

        // Remove invalid commas after top-level object or array
        cleanJson = cleanJson.trim().removeSuffix(",")

        return cleanJson
    }

    private val boundingBoxSystemInstructionsv3 = """
        You are an expert at analyzing Android app screenshots. Your task is to extract clickable or recognizable UI elements such as:

        - App icons, logos (e.g., YouTube logo)
        - Buttons, toggles, and input fields
        - Icons (e.g., search, mic, share, play)
        - Text elements (titles, labels, placeholders)

        Return a **valid JSON array** where each element is a JSON object with:
        - "label": a short string (e.g., "search icon", "login button", "YouTube logo")
        - "box_2d": an array of 4 numbers [ymin, xmin, ymax, xmax] in 1000-scale relative coordinates

        Important formatting instructions:
        - The response MUST be a valid JSON array, and ONLY a JSON array.
        - DO NOT include any text outside the JSON (no comments, explanations, or formatting).
        - DO NOT use markdown, backticks, or ellipsis (`...`) anywhere in the response.
        - Ensure proper JSON syntax: use double quotes for all strings, and no trailing commas.

        To help identify the search function, always label magnifying glass icons as `"search icon"`.

        Examples of valid output format:

        [
          {
            "label": "search icon",
            "box_2d": [100, 200, 180, 260]
          },
          {
            "label": "login button",
            "box_2d": [500, 100, 580, 900]
          }
        ]

        Only output such a JSON array. Any extra formatting or explanation will cause failure in parsing.
        """.trimIndent()

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getPerceptionInfos(context: Context?, bitmap: Bitmap?): Quadruple<List<ClickableInfo>, Int, Int, Boolean> {
        if (bitmap == null ){
            return Quadruple(emptyList(), 0, 0, true)
        }
        val width = bitmap.width
        val height = bitmap.height
        val clickableInfos = mutableListOf<ClickableInfo>()
        val keyBoardMap = mutableMapOf<String, Int>()

        val geminiResponseText = callGeminiApi(bitmap)
        if (geminiResponseText != null) {
            val sanitizedJson = sanitizeJson(geminiResponseText)
            val boxes = extractJsonArray(sanitizedJson)
            boxes.mapNotNull { obj ->
                try {
                    // ... (your existing logic to parse boxes and add to clickableInfos)
                    val box = obj.getJSONArray("box_2d")
                    val label = obj.getString("label")
                    val ymin = box.getDouble(0) / 1000 * height
                    val xmin = box.getDouble(1) / 1000 * width
                    val ymax = box.getDouble(2) / 1000 * height
                    val xmax = box.getDouble(3) / 1000 * width
                    val centerX = ((xmin + xmax) / 2).toInt()
                    val centerY = ((ymin + ymax) / 2).toInt()
                    keyBoardMap[label] = 10
                    clickableInfos.add(ClickableInfo("icon: $label", centerX to centerY))
                } catch (e: Exception) { null }
            }
        }

        // Call the OCR API to get text elements
        val ocrResponseText = callOcrApi(bitmap)
        if (ocrResponseText != null) {
            val json = JSONObject(ocrResponseText)
            val results = json.getJSONArray("results")
            for (i in 0 until results.length()) {
                // ... (your existing logic to parse OCR results and add to clickableInfos)
                val obj = results.getJSONObject(i)
                val text = obj.getString("text")
                val center = obj.getJSONArray("center")
                val cx = center.getDouble(0).toInt()
                val cy = center.getDouble(1).toInt()
                keyBoardMap[text] = 10
                clickableInfos.add(ClickableInfo("text: $text", cx to cy))
            }
        }

        if (context != null) {
//            logPerceptionInfo(context, clickableInfos)
        }

        val keyboardOpen = ('a'..'z').all { char -> keyBoardMap.containsKey(char.toString()) }

        return Quadruple(clickableInfos, width, height, keyboardOpen)
    }
}



data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

//
//    @RequiresApi(Build.VERSION_CODES.R)
//    suspend fun logPerceptionInfo(context: Context, clickableInfos: List<ClickableInfo>) {
//        println("Logging perception info")
//        val eyes = Eyes(context)
//        eyes.openEyes()
//        val screenshotFile = eyes.getScreenshotFile()
//
//        val bitmap = BitmapFactory.decodeFile(screenshotFile?.absolutePath)
//            .copy(Bitmap.Config.ARGB_8888, true)
//
//        val canvas = Canvas(bitmap)
//
//        val circlePaint = Paint().apply {
//            color = Color.RED
//            style = Paint.Style.FILL
//            isAntiAlias = true
//        }
//
//        val textPaint = Paint().apply {
//            color = Color.BLUE
//            textSize = 32f
//            isAntiAlias = true
//        }
//
//        // Draw all ClickableInfo points with text
//        for ((index, info) in clickableInfos.withIndex()) {
//            val (label, coord) = info
//            val (x, y) = coord
//
//            // Draw circle
//            canvas.drawCircle(x.toFloat(), y.toFloat(), 10f, circlePaint)
//
//            // Draw label
//            canvas.drawText(label, x + 12f, y - 12f, textPaint)
//        }
//
//        // Save annotated screenshot
//        val logDir = File(context.filesDir, "infoPerceptionLogs")
//        logDir.mkdirs()
//        val timestamp = System.currentTimeMillis()
//        val logFile = File(logDir, "perception_$timestamp.jpg")
//        FileOutputStream(logFile).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
//        }
//        println("Logging of preceptionInfo saved in data/data/com.example.blurr/files/tap_logs/perception_$timestamp.jpg")
//    }
//

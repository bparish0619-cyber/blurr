package com.example.blurr.service

import android.R
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.blurr.agent.ClickableInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.FileOutputStream
import java.io.IOException

class Retina(
    private val context: Context,
    private val eyes: Eyes,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val boundingBoxSystemInstructionsv1 = """
        Return bounding boxes as a JSON array with labels. Never return masks or code fencing.
        If an object is present multiple times, name them according to their unique characteristic (colors, size, position, unique characteristics, etc..).
    """.trimIndent()

    private val boundingBoxSystemInstructionsv2 = """
        You are an expert in understanding Android app screenshots. Analyze the UI in the image and identify all distinct, clickable, or recognizable visual elements. This includes:
        - App icons, logos (e.g., YouTube logo, app logo)
        - Buttons, toggles, and input fields
        - Icons (e.g., search, mic, share, play, pause)
        - Visible text elements (e.g., titles, labels, placeholders)
        
        Return the result as a JSON array, where each object contains:
        - "label": a short description of the element (e.g., "YouTube logo", "search icon", "login button", "video title")
        - "box_2d": the bounding box as [ymin, xmin, ymax, xmax] **in 1000-scale relative coordinates**
        
        If similar items appear multiple times, distinguish them with spatial or visual cues (e.g., "top right icon", "blue button", "bottom search bar").
        
           """.trimIndent()

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


    fun logPerceptionInfo(context: Context, clickableInfos: List<ClickableInfo>) {
        println("Logging perception info")
        val eyes = Eyes(context)
        eyes.openEyes()
        val screenshotFile = eyes.getScreenshotFile()

        val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
            .copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)

        val circlePaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLUE
            textSize = 32f
            isAntiAlias = true
        }

        // Draw all ClickableInfo points with text
        for ((index, info) in clickableInfos.withIndex()) {
            val (label, coord) = info
            val (x, y) = coord

            // Draw circle
            canvas.drawCircle(x.toFloat(), y.toFloat(), 10f, circlePaint)

            // Draw label
            canvas.drawText(label, x + 12f, y - 12f, textPaint)
        }

        // Save annotated screenshot
        val logDir = File(context.filesDir, "infoPerceptionLogs")
        logDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val logFile = File(logDir, "perception_$timestamp.jpg")
        FileOutputStream(logFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        println("Logging of preceptionInfo saved in data/data/com.example.blurr/files/tap_logs/perception_$timestamp.jpg")
    }


    fun sanitizeJsonArray(input: String): JSONArray {
        val sanitized = input
            .replace("```json", "")
            .replace("```", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        return try {
            // Try to parse the full array first
            JSONArray(sanitized)
        } catch (e: Exception) {
            // Try to fix broken arrays by extracting valid objects manually
            val fixedArray = JSONArray()
            val regex = Regex("""\{.*?"label"\s*:\s*".+?",\s*"box_2d"\s*:\s*\[.*?]}\s*""")
            regex.findAll(sanitized).forEach { match ->
                try {
                    fixedArray.put(JSONObject(match.value))
                } catch (_: Exception) { /* skip malformed entries */ }
            }
            fixedArray
        }
    }
    fun getPerceptionInfos(context: Context?): Quadruple<List<ClickableInfo>, Int, Int, Boolean> {
        // Step 1: Capture screenshot
        eyes.openEyes()
        val screenshotFile: File = eyes.getScreenshotFile()

        // Step 2: Read image and encode as base64
        val imageBytes = screenshotFile.readBytes()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val width = bitmap.width
        val height = bitmap.height

// Step 3: Prepare JSON request body with schema
        val payload = JSONObject().apply {
            put("model", "gemini-2.0-flash")

            // Contents array
            val content = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/png")
                        .put("data", base64Image)
                    ))
                    put(JSONObject().put("text", boundingBoxSystemInstructionsv3))
                })
            }
            put("contents", JSONArray().put(content))

            // Generation config with response schema
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", JSONObject().apply {
                    put("type", "ARRAY")
                    put("items", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("label", JSONObject().put("type", "STRING"))
                            put("box_2d", JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().put("type", "NUMBER"))
                            })
                        })
                        put("propertyOrdering", JSONArray().apply {
                            put("label")
                            put("box_2d")
                        })
                    })
                })
            })
        }

        // Step 4: Send request to Gemini

        val clientWithTimeouts = client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val maxRetries = 3
        var attempt = 0
        var lastException: Exception? = null
        var responseText: String? = null

        while (attempt < maxRetries && responseText == null) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = clientWithTimeouts.newCall(request).execute()
                response.use {
                    if (!response.isSuccessful) {
                        throw IOException("Gemini API failed: ${response.code} ${response.message}")
                    }

                    val resultJson = JSONObject(response.body?.string() ?: "")
                    responseText = resultJson
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < maxRetries) {
                    val delay = 1000L * attempt
                    println("Gemini request failed (attempt $attempt), retrying in ${delay}ms: ${e.message}")
                    Thread.sleep(delay)
                }
            }
        }

        if (responseText == null) {
            throw lastException ?: Exception("Unknown error in Gemini API call")
        }

        val clickableInfos = mutableListOf<ClickableInfo>()
        val sanitizedJson = sanitizeJson(responseText)
//        println(sanitizedJson)

        var keyBoardMap = mutableMapOf<String, Int>()

        val boxes = extractJsonArray(sanitizedJson)
        boxes.mapNotNull { obj ->
            try {
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
            } catch (e: Exception) {
                null
            }
        }

        // Step 6 Send to FastAPI OCR
        val requestBodyOCR = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                screenshotFile.name,
                screenshotFile.readBytes().toRequestBody("image/png".toMediaType())
            )
            .build()

        val requestOCR = Request.Builder()
            .url("http://10.0.2.2:8000/ocr")
            .post(requestBodyOCR)
            .build()

        val responseOCR = client.newCall(requestOCR).execute()
        if (!responseOCR.isSuccessful) {
            throw Exception("OCR API failed: ${responseOCR.code} ${responseOCR.message}")
        }

        val json = JSONObject(responseOCR.body.string())
        val results = json.getJSONArray("results")

        // step 7, add parsed stuff too to the info
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            val text = obj.getString("text")
            val center = obj.getJSONArray("center")
            val cx = center.getDouble(0).toInt()
            val cy = center.getDouble(1).toInt()
            keyBoardMap[text] = 10
            clickableInfos.add(ClickableInfo("text: $text", cx to cy))
        }

        // if in debug or testing, save the perception and coord in the logs
        if (context != null) {
            logPerceptionInfo(context, clickableInfos)
        }

        // Check if keyboardMap contains all characters from 'a' to 'z'
        val keyboardOpen = ('a'..'z').all { char ->
            keyBoardMap.containsKey(char.toString())
        }


        return Quadruple(clickableInfos, width, height, keyboardOpen)
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

}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)


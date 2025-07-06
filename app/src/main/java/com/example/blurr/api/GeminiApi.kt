package com.example.blurr.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Refactored GeminiApi as a singleton object.
 * It now gets a rotated API key from ApiKeyManager for every request.
 */
object GeminiApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        prompt: String,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-2.5-flash",
        maxRetry: Int = 4,
        context: Context? = null
    ): String? {
        // Get a new key for this specific request.
        val currentApiKey = ApiKeyManager.getNextKey()
        Log.d("GeminiApi", "Using API key ending in: ...${currentApiKey.takeLast(4)}")

        var attempts = 0
        while (attempts < maxRetry) {
            val attemptStartTime = System.currentTimeMillis()
            try {
                val payload = buildPayload(prompt, images)
                
                // Log the request details
                Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
                Log.d("GeminiApi", "Model: $modelName")
                Log.d("GeminiApi", "Images count: ${images.size}")
                Log.d("GeminiApi", "Prompt length: ${prompt.length} characters")
                Log.d("GeminiApi", "Prompt preview: ${prompt.take(200)}${if (prompt.length > 200) "..." else ""}")
                
                // Log full payload for debugging (be careful with sensitive data)
                Log.d("GeminiApi", "Full payload: ${payload.toString()}")

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val requestStartTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val responseEndTime = System.currentTimeMillis()
                    val totalTime = responseEndTime - requestStartTime
                    val attemptTime = responseEndTime - attemptStartTime
                    
                    val responseBody = response.body?.string()
                    
                    // Log response details
                    Log.d("GeminiApi", "=== GEMINI API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("GeminiApi", "HTTP Status: ${response.code}")
                    Log.d("GeminiApi", "Response time: ${totalTime}ms")
                    Log.d("GeminiApi", "Total attempt time: ${attemptTime}ms")
                    Log.d("GeminiApi", "Response body length: ${responseBody?.length ?: 0} characters")
                    
                    if (!response.isSuccessful) {
                        Log.e("GeminiApi", "API call failed with HTTP ${response.code} using key ...${currentApiKey.takeLast(4)}")
                        Log.e("GeminiApi", "Error response: $responseBody")
                        throw Exception("API Error ${response.code}")
                    }

                    if (responseBody.isNullOrEmpty()) {
                        Log.e("GeminiApi", "Received empty response body from API.")
                        throw Exception("Received empty response body from API.")
                    }

                    // Log successful response
                    Log.d("GeminiApi", "Response preview: ${responseBody.take(500)}${if (responseBody.length > 500) "..." else ""}")
                    
                    val parsedResponse = parseSuccessResponse(responseBody)
                    Log.d("GeminiApi", "Parsed response length: ${parsedResponse?.length ?: 0} characters")
                    Log.d("GeminiApi", "Parsed response preview: ${parsedResponse?.take(200)}${if ((parsedResponse?.length ?: 0) > 200) "..." else ""}")
                    Log.d("GeminiApi", "=== END GEMINI API CALL ===")
                    
                    // Save detailed log to file if context is provided
                    context?.let { ctx ->
                        val logEntry = createLogEntry(
                            attempts + 1,
                            modelName,
                            prompt,
                            images.size,
                            payload.toString(),
                            response.code,
                            responseBody,
                            totalTime,
                            attemptTime
                        )
                        saveLogToFile(ctx, logEntry)
                    }
                    
                    return parsedResponse
                }
            } catch (e: Exception) {
                val attemptEndTime = System.currentTimeMillis()
                val attemptTime = attemptEndTime - attemptStartTime
                
                Log.e("GeminiApi", "=== GEMINI API ERROR (Attempt ${attempts + 1}) ===")
                Log.e("GeminiApi", "Attempt time: ${attemptTime}ms")
                Log.e("GeminiApi", "Error: ${e.printStackTrace()}")
                Log.e("GeminiApi", "=== END GEMINI API ERROR ===")
                
                // Save error log to file if context is provided
                context?.let { ctx ->
                    val logEntry = createLogEntry(
                        attempts + 1,
                        modelName,
                        prompt,
                        images.size,
                        "",
                        500,
                        "",
                        0,
                        attemptTime,
                        e.message
                    )
                    saveLogToFile(ctx, logEntry)
                }
                
                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all ${maxRetry} retries.")
                    return null
                }
            }
        }
        return null
    }

    // buildPayload and parseSuccessResponse methods remain the same, just remove the 'model' parameter from buildPayload
    private fun buildPayload(prompt: String, images: List<Bitmap>): JSONObject {
        // ... same implementation as before
        val jsonParts = JSONArray()
        jsonParts.put(JSONObject().put("text", prompt))
        images.forEach { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            val imagePart = JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", base64Image)
            )
            jsonParts.put(imagePart)
        }
        val contents = JSONObject().put("role", "user").put("parts", jsonParts)
        return JSONObject().put("contents", JSONArray().put(contents))
    }

    private fun parseSuccessResponse(responseBody: String): String? {
        // ... same implementation as before
        val json = JSONObject(responseBody)
        if (!json.has("candidates")) {
            Log.w("GeminiApi", "API response has no 'candidates'. It was likely blocked.")
            Log.w("GeminiApi", "Full response: $responseBody")
            return null
        }
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    /**
     * Saves detailed Gemini API logs to a file for persistent debugging
     */
    fun saveLogToFile(context: Context, logEntry: String) {
        try {
            val logDir = File(context.filesDir, "gemini_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "gemini_debug_${timestamp}.log")
            
            FileWriter(logFile, true).use { writer ->
                writer.append("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())} - $logEntry\n")
            }
            
            Log.d("GeminiApi", "Log saved to: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to save log to file", e)
        }
    }

    /**
     * Gets the path to the latest log file for easy access
     */
    fun getLatestLogFilePath(context: Context): String? {
        try {
            val logDir = File(context.filesDir, "gemini_logs")
            if (!logDir.exists()) return null
            
            val logFiles = logDir.listFiles { file -> file.name.startsWith("gemini_debug_") }
            return logFiles?.maxByOrNull { it.lastModified() }?.absolutePath
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to get latest log file path", e)
            return null
        }
    }

    /**
     * Reads the content of the latest log file
     */
    fun getLatestLogContent(context: Context): String? {
        try {
            val logPath = getLatestLogFilePath(context)
            if (logPath == null) return null
            
            val logFile = File(logPath)
            return logFile.readText()
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to read latest log file", e)
            return null
        }
    }

    /**
     * Creates a comprehensive log entry for debugging
     */
    private fun createLogEntry(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        payload: String,
        responseCode: Int?,
        responseBody: String?,
        responseTime: Long,
        totalTime: Long,
        error: String? = null
    ): String {
        return buildString {
            appendLine("=== GEMINI API DEBUG LOG ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}")
            appendLine("Attempt: $attempt")
            appendLine("Model: $modelName")
            appendLine("Images count: $imagesCount")
            appendLine("Prompt length: ${prompt.length}")
            appendLine("Prompt: $prompt")
            appendLine("Payload: $payload")
            appendLine("Response code: $responseCode")
            appendLine("Response time: ${responseTime}ms")
            appendLine("Total time: ${totalTime}ms")
            if (error != null) {
                appendLine("Error: $error")
            } else {
                appendLine("Response body: $responseBody")
            }
            appendLine("=== END LOG ===")
        }
    }
}
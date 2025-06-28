package com.example.blurr.api

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
        modelName: String = "gemini-2.0-flash",
        maxRetry: Int = 3
    ): String? {
        // Get a new key for this specific request.
        val currentApiKey = ApiKeyManager.getNextKey()
        Log.d("GeminiApi", "Using API key ending in: ...${currentApiKey.takeLast(4)}")

        var attempts = 0
        while (attempts < maxRetry) {
            try {
                val payload = buildPayload(prompt, images) // Removed modelName from here

                val request = Request.Builder()
                    // Use the key fetched for this attempt
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    // ... (The rest of the function is EXACTLY the same)
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e("GeminiApi", "API call failed with HTTP ${response.code} using key ...${currentApiKey.takeLast(4)}")
                        Log.e("GeminiApi", "Response: $responseBody")
                        throw Exception("API Error ${response.code}")
                    }

                    if (responseBody.isNullOrEmpty()) {
                        throw Exception("Received empty response body from API.")
                    }

                    return parseSuccessResponse(responseBody)
                }
            } catch (e: Exception) {
                Log.e("GeminiApi", "Attempt ${attempts + 1} failed.", e)
                attempts++
                if (attempts < maxRetry) {
                    delay(1000L * attempts)
                } else {
                    Log.e("GeminiApi", "Request failed after all retries.")
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
}
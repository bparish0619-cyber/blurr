package com.example.blurr.api

import android.util.Log
import com.example.blurr.BuildConfig
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A simple, direct wrapper for the Anthropic Claude API.
 * This object uses a static API key for quick development and iteration.
 */
object ClaudeApi {

    // --- IMPORTANT ---
    // Replace this with your actual Anthropic API key.
    // For better security, consider moving this to a local.properties file
    // and accessing it via BuildConfig.
    private const val ANTHROPIC_API_KEY = BuildConfig.ANTHROPIC_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Generates content using the specified Claude model.
     *
     * @param prompt The user's text prompt.
     * @param modelName The specific Claude model to use (e.g., "claude-opus-4-20250514").
     * @param maxTokens The maximum number of tokens to generate in the response.
     * @param maxRetry The number of times to retry the request on failure.
     * @return The text content of the response, or null if the request fails after all retries.
     */
    suspend fun generateContent(
        prompt: String,
        modelName: String = "claude-opus-4-20250514",
        maxTokens: Int = 2048,
        maxRetry: Int = 3
    ): String? {
        if (ANTHROPIC_API_KEY == "YOUR_ANTHROPIC_API_KEY_HERE") {
            Log.e("ClaudeApi", "API Key is not set. Please replace the placeholder in ClaudeApi.kt.")
            return "API Key not configured."
        }

        var attempts = 0
        while (attempts < maxRetry) {
            try {
                // 1. Build the JSON payload according to Anthropic's documentation
                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("max_tokens", maxTokens)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                Log.d("ClaudeApi", "Request Payload: ${payload.toString(2)}")

                // 2. Build the HTTP request with the required headers
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("x-api-key", ANTHROPIC_API_KEY)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .build()

                // 3. Execute the request and handle the response
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e("ClaudeApi", "API call failed with HTTP ${response.code}")
                        Log.e("ClaudeApi", "Error response: $responseBody")
                        throw Exception("API Error ${response.code}")
                    }

                    if (responseBody.isNullOrEmpty()) {
                        Log.e("ClaudeApi", "Received empty response body.")
                        throw Exception("Empty response body")
                    }

                    Log.d("ClaudeApi", "Successful Response: $responseBody")
                    return parseSuccessResponse(responseBody)
                }

            } catch (e: Exception) {
                Log.e("ClaudeApi", "Error on attempt ${attempts + 1}: ${e.message}", e)
                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("ClaudeApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("ClaudeApi", "Request failed after all $maxRetry retries.")
                    return null
                }
            }
        }
        return null
    }

    /**
     * Parses the successful JSON response to extract the text content.
     */
    private fun parseSuccessResponse(responseBody: String): String? {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val contentArray = jsonResponse.getJSONArray("content")
            if (contentArray.length() > 0) {
                // Get the first object in the content array
                val firstContentObject = contentArray.getJSONObject(0)
                // Return the text from that object
                firstContentObject.getString("text")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ClaudeApi", "Failed to parse JSON response: ${e.message}")
            null
        }
    }
}

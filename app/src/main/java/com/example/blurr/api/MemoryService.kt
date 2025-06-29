package com.example.blurr.api

import android.util.Log
import com.example.blurr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * A service to interact with the Mem0 API for persistent memory storage and retrieval.
 */
class MemoryService {

    private val client = OkHttpClient()
    // --- CORRECTED: Use the correct BuildConfig field name ---
    private val apiKey = BuildConfig.MEM0_API

    /**
     * Adds a new memory to Mem0 based on the user's instruction.
     */
    suspend fun addMemory(instruction: String, userId: String) {
        if (apiKey.isEmpty()) {
            Log.w("MemoryService", "Mem0 API key is not set. Skipping add memory.")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val message = JSONObject().put("role", "user").put("content", instruction)
                val messagesArray = JSONArray().put(message)
                val payload = JSONObject().apply {
                    put("messages", messagesArray)
                    put("user_id", userId)
                    put("version", "v2")
                }
                println("Payload being sent to Mem0: ${payload.toString(2)}") // Pretty print JSON

                val request = Request.Builder()
                    .url("https://api.mem0.ai/v1/memories/")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Token $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    // --- THIS IS THE FIX ---
                    // Read the response body into a variable ONCE.
                    val responseBodyString = response.body?.string()

                    if (response.isSuccessful) {
                        // Now, print the body you captured.
                        Log.d("MemoryService", "Successfully added memory. Response: $responseBodyString")
                    } else {
                        // Log the body on failure as well.
                        Log.e("MemoryService", "Failed to add memory. Code: ${response.code}, Body: $responseBodyString")
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryService", "Error adding memory", e)
            }
        }
    }

    /**
     * Searches for memories relevant to the current query for a specific user.
     */
    suspend fun searchMemory(query: String, userId: String): String {
        if (apiKey.isEmpty()) {
            Log.w("MemoryService", "Mem0 API key is not set. Skipping search.")
            return "No relevant memories found."
        }

        return withContext(Dispatchers.IO) {
            try {
                val filters = JSONObject().put("user_id", userId)
                val payload = JSONObject().apply {
                    put("query", query)
                    put("filters", filters)
                }

                val request = Request.Builder()
                    .url("https://api.mem0.ai/v2/memories/search/")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Token $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        throw Exception("Mem0 search failed with HTTP ${response.code}")
                    }

                    val resultsArray = JSONArray(body)
                    if (resultsArray.length() == 0) {
                        return@withContext "No relevant memories found."
                    }

                    // Format the results into a clean string for the prompt
                    val memories = (0 until resultsArray.length()).joinToString("\n") { i ->
                        val memoryObj = resultsArray.getJSONObject(i)
                        // Use optString to avoid crashing if 'memory' key is missing
                        "- ${memoryObj.optString("memory", "Corrupted memory entry")}"
                    }
                    memories
                }
            } catch (e: Exception) {
                Log.e("MemoryService", "Error searching memory", e)
                "Could not retrieve memories due to an error."
            }
        }
    }
}

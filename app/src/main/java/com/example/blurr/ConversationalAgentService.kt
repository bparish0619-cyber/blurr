package com.example.blurr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.blurr.utilities.SpeechCoordinator
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        isRunning = true
        // Create the notification channel as soon as the service is created
        createNotificationChannel()
        initializeConversation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")

        // FIXED: This is the critical step to prevent the crash.
        // It tells Android that this service is actively running in the foreground.
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            // Check if the conversation is just starting
            if (conversationHistory.size == 1) {
                val greeting = "Hello! How can I help you today?"
                // Use your helper to add the model's greeting
                conversationHistory = addResponse("model", greeting, conversationHistory)
                speakAndThenListen(greeting)
            }
        }
        return START_STICKY
    }

    private suspend fun speakAndThenListen(text: String) {
        // First speak the text
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "Panda said: $text")

        // Then start listening for user input
        speechCoordinator.startListening(
            onResult = { recognizedText ->
                Log.d("ConvAgent", "User said: $recognizedText")
                processUserInput(recognizedText)
            },
            onError = { error ->
                Log.e("ConvAgent", "STT Error: $error")
                serviceScope.launch {
                    speakAndThenListen("I'm sorry, I didn't catch that.")
                }
            },
            onListeningStateChange = { listening ->
                Log.d("ConvAgent", "Listening state: $listening")
            }
        )
    }
    // --- CHANGED: Rewritten to process structured JSON from the model ---
    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            conversationHistory = addResponse("user", userInput, conversationHistory)

            try {
                // Hardcoded exit command for the user remains as a fallback
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    speakAndThenListen("Goodbye!")
                    delay(2000)
                    stopSelf()
                    return@launch
                }

                // Get the raw JSON string from the API
                val rawModelResponse = getReasoningModelApiResponse(conversationHistory, "") ?: """{"reply": "I'm sorry, I'm having trouble thinking right now.", "shouldEndConversation": false}"""

                // Parse the structured response
                val (reply, shouldEnd) = parseModelResponse(rawModelResponse)

                if (shouldEnd) {
                    Log.d("ConvAgent", "Model decided to end the conversation.")
                    // Speak the final reply without listening again
                    speechCoordinator.speakText(reply)
                    // Wait for speech to finish (adjust delay as needed or use a proper callback)
                    delay(2000)
                    stopSelf() // Stop the service
                } else {
                    // Continue the conversation
                    conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                    speakAndThenListen(reply)
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("I encountered an error. Let's try again.")
            }
        }
    }

//    private fun processUserInput(userInput: String) {
//        serviceScope.launch {
//            conversationHistory = addResponse("user", userInput, conversationHistory)
//
//            try {
//                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
//                    speakAndThenListen("Goodbye!")
//                    delay(2000)
//                    stopSelf()
//                    return@launch
//                }
//
//                // Call the API with the conversation history
//                val response = getReasoningModelApiResponse(conversationHistory, "")
//
//                // FIXED: Correctly extract the text from the Gemini API response.
//                // Calling .toString() on the response object was causing the error.
//                // The .text property contains the model's actual reply.
//                val modelResponse = response ?: "Sorry, I had trouble understanding. Could you repeat that?"
//
//                conversationHistory = addResponse("model", modelResponse, conversationHistory)
//                speakAndThenListen(modelResponse)
//
//            } catch (e: Exception) {
//                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
//                speakAndThenListen("I encountered an error. Let's try again.")
//            }
//        }
//    }

    private fun initializeConversation() {
        val systemPrompt = """
            You are a friendly and helpful voice assistant. Your goal is to have a natural conversation with the user.
            ALWAYS respond in a JSON format with two keys: "reply" and "shouldEndConversation".
            - "reply": Your text response to the user. Keep it conversational.
            - "shouldEndConversation": A boolean (true or false). Set it to true ONLY when the conversation is clearly over (e.g., the user says "goodbye", "thank you, that's all", etc.). Otherwise, it must be false.

            Example 1:
            User: "What's the weather like today?"
            You: {"reply": "It looks like a sunny day in Ghaziabad, with a high of 35 degrees Celsius.", "shouldEndConversation": false}

            Example 2:
            User: "Okay, thanks, goodbye!"
            You: {"reply": "You're welcome! Goodbye!", "shouldEndConversation": true}
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversational Agent")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure you have this drawable
            .setOngoing(true)
            .build()
    }

    // FIXED: Corrected and uncommented the notification channel creation.
    // This is required for notifications to appear on Android 8.0 (Oreo) and higher.
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Conversational Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            // Correctly get the NotificationManager system service
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
    // --- NEW: Helper function to parse the model's JSON response safely ---
    private fun parseModelResponse(jsonString: String): Pair<String, Boolean> {
        return try {
            val jsonObject = JSONObject(jsonString)
            val reply = jsonObject.getString("reply")
            val shouldEnd = jsonObject.optBoolean("shouldEndConversation", false)
            Pair(reply, shouldEnd)
        } catch (e: JSONException) {
            Log.e("ConvAgent", "Failed to parse JSON from model: $jsonString")
            // If parsing fails, use the whole string as the reply and don't end the conversation
            Pair(jsonString, false)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        speechCoordinator.shutdown()
        serviceScope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
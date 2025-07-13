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

    private fun processUserInput(userInput: String) {
        serviceScope.launch {
            conversationHistory = addResponse("user", userInput, conversationHistory)

            try {
                if (userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
                    speakAndThenListen("Goodbye!")
                    delay(2000)
                    stopSelf()
                    return@launch
                }

                // Call the API with the conversation history
                val response = getReasoningModelApiResponse(conversationHistory, "")

                // FIXED: Correctly extract the text from the Gemini API response.
                // Calling .toString() on the response object was causing the error.
                // The .text property contains the model's actual reply.
                val modelResponse = response ?: "Sorry, I had trouble understanding. Could you repeat that?"

                conversationHistory = addResponse("model", modelResponse, conversationHistory)
                speakAndThenListen(modelResponse)

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("I encountered an error. Let's try again.")
            }
        }
    }

    private fun initializeConversation() {
        val systemPrompt = """
            You are a friendly and helpful voice assistant. Your goal is to have a natural conversation with the user.
            - Try to keep the user happy
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        speechCoordinator.shutdown()
        serviceScope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
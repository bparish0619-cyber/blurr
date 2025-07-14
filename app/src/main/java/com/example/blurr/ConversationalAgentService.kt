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
        createNotificationChannel()
        initializeConversation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConvAgent", "Service onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            if (conversationHistory.size == 1) {
                val greeting = "Hello! How can I help you today?"
                // Add the initial greeting to history (doesn't need to follow the complex format)
                conversationHistory = addResponse("model", greeting, conversationHistory)
                speakAndThenListen(greeting)
            }
        }
        return START_STICKY
    }

    private suspend fun speakAndThenListen(text: String) {
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

    // --- CHANGED: Rewritten to process the new custom text format ---
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

                // Get the raw text response from the API
                val rawModelResponse = getReasoningModelApiResponse(conversationHistory, "")
                    ?: "### Response ###\nI'm sorry, I'm having trouble thinking right now. \n ### Should End ###\nContinue"

                // Parse the custom structured response
                val (thought, shouldEnd) = parseCustomFormatResponse(rawModelResponse)

                if (shouldEnd) {
                    Log.d("ConvAgent", "Model decided to end the conversation.")
                    speechCoordinator.speakText(thought)
                    delay(2000)
                    stopSelf()
                } else {
                    // Add the full model response to history for context, but only speak the thought
                    conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                    speakAndThenListen(thought)
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("I encountered an error. Let's try again.")
            }
        }
    }

    // --- CHANGED: Updated prompt to define the new text format ---
    private fun initializeConversation() {
        val systemPrompt = """
            You are a friendly and helpful voice assistant. Your goal is to have a natural conversation with the user.
            
            Use Below format only:
            ### Response ###
            Your conversational response to the user. This is what will be spoken aloud. Keep it friendly and concise.
            ### Should End ###
            The next subgoal. If the conversation is over (e.g., user says goodbye), write \"Finished\". Otherwise, write \"Continue\".
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    // --- NEW: Helper function to parse the custom text format ---
    private fun parseCustomFormatResponse(response: String): Pair<String, Boolean> {
        val responseMarker = "### Response ###"
        val endMarker = "### Should End ###"


        // Extract the text between "### Thought ###" and "### Plan ###"
        val reply = response.substringAfter(responseMarker, "")
            .substringBefore(endMarker, "").trim()

        // Extract the text after "### Current Subgoal ###"
        val subgoal = response.substringAfter(endMarker, "").trim()

        // End the conversation if the subgoal is "Finished"
        val shouldEnd = subgoal.contains("Finished", ignoreCase = true)

        return Pair(reply, shouldEnd)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Conversational Agent")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Conversational Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        serviceScope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
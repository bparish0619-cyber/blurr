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
import com.example.blurr.agent.AgentConfig
import com.example.blurr.agent.ClarificationAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.VisionHelper
import com.example.blurr.agent.VisionMode
import com.example.blurr.services.AgentTaskService
import com.example.blurr.utilities.SpeechCoordinator
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelDecision(
    val type: String = "Reply",
    val reply: String,
    val instruction: String = "",
    val shouldEnd: Boolean = false
)

class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()

     private val clarificationAgent = ClarificationAgent() // Example initialization

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        var isRunning = false
    }
    private enum class AgentState {
        IDLE,
        AWAITING_CLARIFICATION,
        EXECUTING_TASK
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

                val rawModelResponse = getReasoningModelApiResponse(conversationHistory, "") ?: "### Type ###\nReply\n### Reply ###\nI'm sorry, I had an issue.\n### Instruction ###\n\n### Should End ###\nContinue"
                val decision = parseModelResponse(rawModelResponse)

                when (decision.type) {
                    "Task" -> {
                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        // --- NEW: Check if the task instruction needs clarification ---
                        val (needsClarification, questions) = checkIfClarificationNeeded(decision.instruction)

                        if (needsClarification) {
                            // If clarification is needed, ask the questions and continue the conversation.
                            val questionToAsk = "I can help with that, but first: ${questions.joinToString(" ")}"
                            Log.d("ConvAgent", "Task needs clarification. Asking: '$questionToAsk'")
                            conversationHistory = addResponse("model", "Clarification needed for task: ${decision.instruction}", conversationHistory)
                            speakAndThenListen(questionToAsk)
                        } else {
                            // If no clarification is needed, execute the task.
                            Log.d("ConvAgent", "Task is clear. Executing: ${decision.instruction}")
                            speechCoordinator.speakText(decision.reply)
                            delay(1500)

                            val taskIntent = Intent(this@ConversationalAgentService, AgentTaskService::class.java).apply {
                                putExtra("TASK_INSTRUCTION", decision.instruction)
                                putExtra("VISION_MODE", "XML")
                            }
                            startService(taskIntent)
                            stopSelf()
                        }
                    }
                    else -> { // Default to "Reply"
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            speechCoordinator.speakText(decision.reply)
                            delay(2000)
                            stopSelf()
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                            speakAndThenListen(decision.reply)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                speakAndThenListen("I encountered an error. Let's try again.")
            }
        }
    }

    // --- NEW: Added the clarification check logic directly into the service ---
    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        try {
            val tempInfoPool = InfoPool(instruction = instruction)
            // Use 'this' as the context for the service
            val config = AgentConfig(visionMode = VisionMode.XML, apiKey = "", context = this)
            val prompt = clarificationAgent.getPrompt(tempInfoPool, config)
            val chat = clarificationAgent.initChat()
            val combined = VisionHelper.createChatResponse("user", prompt, chat, config)
            val response = withContext(Dispatchers.IO) {
                getReasoningModelApiResponse(combined, apiKey = config.apiKey)
            }

            val parsedResult = clarificationAgent.parseResponse(response.toString())
            val status = parsedResult["status"] ?: "CLEAR"
            val questionsText = parsedResult["questions"] ?: ""

            return if (status == "NEEDS_CLARIFICATION" && questionsText.isNotEmpty()) {
                val questions = clarificationAgent.parseQuestions(questionsText)
                Pair(true, questions)
            } else {
                Pair(false, emptyList())
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error checking for clarification", e)
            return Pair(false, emptyList())
        }
    }


    private fun initializeConversation() {
        val systemPrompt = """
            You are a helpful voice assistant that can either have a conversation or execute tasks on the user's phone.

            Analyze the user's request and respond in the following format:

            ### Type ###
            Either "Task" or "Reply".
            - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom", "post a tweet").
            - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke", "how are you?").

            ### Reply ###
            The conversational text to speak to the user.
            - If it's a task, this should be a confirmation, like "Okay, opening settings." or "Sure, I can do that.".
            - If it's a reply, this is the answer to the user's question.

            ### Instruction ###
            - If Type is "Task", provide the precise, literal instruction for the task agent here. This should be a complete command.
            - If Type is "Reply", this field should be empty.

            ### Should End ###
            "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.
        """.trimIndent()

        conversationHistory = addResponse("user", systemPrompt, emptyList())
    }

    private fun parseModelResponse(response: String): ModelDecision {
        try {
            val type = response.substringAfter("### Type ###", "").substringBefore("###").trim()
            val reply = response.substringAfter("### Reply ###", "").substringBefore("###").trim()
            val instruction = response.substringAfter("### Instruction ###", "").substringBefore("###").trim()
            val shouldEndStr = response.substringAfter("### Should End ###", "").trim()
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)

            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error parsing custom format, falling back. Response: $response")
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        }
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
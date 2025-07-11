package com.example.blurr.utilities

import android.content.Context
import android.content.Intent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.blurr.utilities.STTManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages user input for agent questions
 * This class handles the communication between the agent and user for interactive tasks
 */
class UserInputManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UserInputManager"
        private const val SPEECH_TIMEOUT_MS = 30000L // 30 seconds timeout for speech input
        private const val FALLBACK_TIMEOUT_MS = 5000L // 5 seconds for fallback response
        private var currentQuestion: String? = null
        private var currentResponse: String? = null
        private var responseCallback: ((String) -> Unit)? = null
    }

    private val sttManager: STTManager by lazy { STTManager(context) }
    
    /**
     * Check if speech recognition is available on this device
     * @return true if speech recognition is available
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Ask a question to the user and wait for their response using speech-to-text
     * @param question The question to ask the user
     * @return The user's response
     */
    suspend fun askQuestion(question: String): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                currentQuestion = question
                responseCallback = { response ->
                    currentResponse = response
                    continuation.resume(response)
                }
                
                Log.d(TAG, "Agent asked: $question")
                
                // Check if speech recognition is available
                if (!isSpeechRecognitionAvailable()) {
                    Log.w(TAG, "Speech recognition not available, using fallback")
                    useFallbackResponse(question)
                    return@suspendCancellableCoroutine
                }
                
                Log.d(TAG, "Starting speech recognition for user response...")
                
                // Start speech recognition with timeout
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val response = withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
                            suspendCancellableCoroutine<String> { speechContinuation ->
                                sttManager.startListening(
                                    onResult = { recognizedText ->
                                        Log.d(TAG, "Speech recognized: $recognizedText")
                                        speechContinuation.resume(recognizedText)
                                    },
                                    onError = { errorMessage ->
                                        Log.e(TAG, "Speech recognition error: $errorMessage")
                                        // Don't throw exception, use fallback instead
                                        speechContinuation.resume("")
                                    },
                                    onListeningStateChange = { isListening ->
                                        Log.d(TAG, "Listening state changed: $isListening")
                                    }
                                )
                            }
                        }
                        
                        if (response != null && response.isNotEmpty()) {
                            Log.d(TAG, "User responded via speech: $response")
                            responseCallback?.invoke(response)
                        } else {
                            Log.w(TAG, "Speech recognition failed or timed out, using fallback")
                            useFallbackResponse(question)
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during speech recognition", e)
                        useFallbackResponse(question)
                    } finally {
                        // Stop listening and clean up
                        sttManager.stopListening()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error asking question", e)
                continuation.resume("Error: Could not get user response")
            }
        }
    }
    
    /**
     * Use fallback response when STT is not available or fails
     * @param question The original question
     */
    private fun useFallbackResponse(question: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(FALLBACK_TIMEOUT_MS) // Give user time to read the question
            val fallbackResponse = "User provided fallback response to: $question"
            Log.d(TAG, "Using fallback response: $fallbackResponse")
            responseCallback?.invoke(fallbackResponse)
        }
    }
    
    /**
     * Provide a response to the current question
     * This method can be called from the UI or other parts of the app
     * @param response The user's response
     */
    fun provideResponse(response: String) {
        Log.d(TAG, "User responded: $response")
        currentResponse = response
        responseCallback?.invoke(response)
    }
    
    /**
     * Get the current question being asked
     * @return The current question or null if no question is active
     */
    fun getCurrentQuestion(): String? = currentQuestion
    
    /**
     * Check if there's an active question waiting for response
     * @return true if there's an active question
     */
    fun hasActiveQuestion(): Boolean = currentQuestion != null
    
    /**
     * Clear the current question and response
     */
    fun clearQuestion() {
        currentQuestion = null
        currentResponse = null
        responseCallback = null
        sttManager.stopListening()
    }


    fun shutdown() {
        sttManager.shutdown()
    }
} 
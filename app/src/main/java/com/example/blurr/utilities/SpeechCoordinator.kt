package com.example.blurr.utilities

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

/**
 * Coordinates between TTS and STT to prevent them from running simultaneously.
 * This ensures a better user experience by avoiding audio conflicts.
 */
class SpeechCoordinator private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechCoordinator"
        
        @Volatile private var INSTANCE: SpeechCoordinator? = null
        
        fun getInstance(context: Context): SpeechCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val ttsManager = TTSManager.getInstance(context)
    private val sttManager = STTManager(context)
    
    // Mutex to ensure only one speech operation at a time
    private val speechMutex = Mutex()
    
    // State tracking
    private var isSpeaking = false
    private var isListening = false
    
    /**
     * Speak text using TTS, ensuring STT is not listening
     * @param text The text to speak
     * @param forceSpeak If true, will stop any ongoing listening and speak immediately
     */
    suspend fun speakText(text: String, forceSpeak: Boolean = false) {
        speechMutex.withLock {
            try {
                // If STT is listening, stop it first
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(500) // Brief pause to ensure STT is fully stopped
                }
                
                isSpeaking = true
                Log.d(TAG, "Starting TTS: $text")
                ttsManager.speakText(text)
                
                // Wait for TTS to complete (approximate)
                val estimatedDuration = text.length * 100L // Rough estimate: 100ms per character
                delay(estimatedDuration.coerceAtLeast(1000L)) // Minimum 1 second
                
            } finally {
                isSpeaking = false
                Log.d(TAG, "TTS completed: $text")
            }
        }
    }
    
    /**
     * Speak text to user (always spoken regardless of debug mode), ensuring STT is not listening
     * @param text The text to speak to the user
     * @param forceSpeak If true, will stop any ongoing listening and speak immediately
     */
    suspend fun speakToUser(text: String, forceSpeak: Boolean = false) {
        speechMutex.withLock {
            try {
                // If STT is listening, stop it first
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking to user: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(500) // Brief pause to ensure STT is fully stopped
                }
                
                isSpeaking = true
                Log.d(TAG, "Starting TTS to user: $text")
                ttsManager.speakToUser(text)
                
                // Wait for TTS to complete (approximate)
                val estimatedDuration = text.length * 100L // Rough estimate: 100ms per character
                delay(estimatedDuration.coerceAtLeast(1000L)) // Minimum 1 second
                
            } finally {
                isSpeaking = false
                Log.d(TAG, "TTS to user completed: $text")
            }
        }
    }
    
    /**
     * Start listening with STT, ensuring TTS is not speaking
     * @param onResult Callback for speech recognition results
     * @param onError Callback for speech recognition errors
     * @param onListeningStateChange Callback for listening state changes
     * @param waitForTTS If true, will wait for any ongoing TTS to complete before starting STT
     */
    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        waitForTTS: Boolean = true
    ) {
        speechMutex.withLock {
            try {
                // If TTS is speaking and we should wait, wait for it to complete
                if (isSpeaking && waitForTTS) {
                    Log.d(TAG, "Waiting for TTS to complete before starting STT")
                    while (isSpeaking) {
                        delay(100) // Check every 100ms
                    }
                    delay(500) // Additional pause after TTS completes
                }
                
                // If TTS is still speaking and we shouldn't wait, stop it
                if (isSpeaking && !waitForTTS) {
                    Log.d(TAG, "Stopping TTS to start STT")
                    // Note: We can't directly stop TTS, but we can mark it as not speaking
                    isSpeaking = false
                    delay(500) // Brief pause
                }
                
                isListening = true
                Log.d(TAG, "Starting STT")
                sttManager.startListening(
                    onResult = { result ->
                        Log.d(TAG, "STT result: $result")
                        onResult(result)
                    },
                    onError = { error ->
                        Log.d(TAG, "STT error: $error")
                        onError(error)
                    },
                    onListeningStateChange = { listening ->
                        Log.d(TAG, "STT listening state: $listening")
                        isListening = listening
                        onListeningStateChange(listening)
                    }
                )
                
            } catch (e: Exception) {
                isListening = false
                Log.e(TAG, "Error starting STT", e)
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }
    
    /**
     * Stop listening with STT
     */
    fun stopListening() {
        if (isListening) {
            Log.d(TAG, "Stopping STT")
            sttManager.stopListening()
            isListening = false
        }
    }
    
    /**
     * Check if currently speaking
     */
    fun isCurrentlySpeaking(): Boolean = isSpeaking
    
    /**
     * Check if currently listening
     */
    fun isCurrentlyListening(): Boolean = isListening
    
    /**
     * Check if any speech operation is in progress
     */
    fun isSpeechActive(): Boolean = isSpeaking || isListening
    
    /**
     * Wait for any ongoing speech operations to complete
     */
    suspend fun waitForSpeechCompletion() {
        while (isSpeechActive()) {
            delay(100)
        }
    }
    
    /**
     * Shutdown the coordinator and clean up resources
     */
    fun shutdown() {
        stopListening()
        sttManager.shutdown()
        // Note: We don't shutdown TTSManager as it's a singleton used elsewhere
        Log.d(TAG, "SpeechCoordinator shutdown")
    }
} 
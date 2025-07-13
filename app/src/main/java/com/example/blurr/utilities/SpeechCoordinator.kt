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
     */
    suspend fun speakText(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250) // Brief pause to ensure STT is fully stopped
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS: $text")

                // This is a suspend call that will wait until TTS is actually done.
                ttsManager.speakText(text)

                // FIXED: The inaccurate, estimated delay has been removed!

                Log.d(TAG, "TTS completed: $text")

            } finally {
                // Ensure the speaking flag is always reset
                isSpeaking = false
            }
        }
    }

    /**
     * Speak text to user, ensuring STT is not listening
     * @param text The text to speak to the user
     */
    suspend fun speakToUser(text: String) {
        speechMutex.withLock {
            try {
                if (isListening) {
                    Log.d(TAG, "Stopping STT before speaking to user: $text")
                    sttManager.stopListening()
                    isListening = false
                    delay(250) // Brief pause
                }

                isSpeaking = true
                Log.d(TAG, "Starting TTS to user: $text")

                // This is a suspend call that will wait until TTS is actually done.
                ttsManager.speakToUser(text)

                // FIXED: The inaccurate, estimated delay has been removed!

                Log.d(TAG, "TTS to user completed: $text")

            } finally {
                // Ensure the speaking flag is always reset
                isSpeaking = false
            }
        }
    }

    /**
     * Start listening with STT, ensuring TTS is not speaking
     * @param onResult Callback for speech recognition results
     * @param onError Callback for speech recognition errors
     * @param onListeningStateChange Callback for listening state changes
     */
    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit
    ) {
        speechMutex.withLock {
            try {
                // If TTS is speaking, wait for it to complete. This loop is now
                // much more efficient as isSpeaking is updated accurately.
                if (isSpeaking) {
                    Log.d(TAG, "Waiting for TTS to complete before starting STT")
                    while (isSpeaking) {
                        delay(100) // Check every 100ms
                    }
                    delay(250) // Additional pause after TTS completes
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

    fun stopListening() {
        if (isListening) {
            Log.d(TAG, "Stopping STT")
            sttManager.stopListening()
            isListening = false
        }
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    fun isCurrentlyListening(): Boolean = isListening

    fun isSpeechActive(): Boolean = isSpeaking || isListening

    suspend fun waitForSpeechCompletion() {
        while (isSpeechActive()) {
            delay(100)
        }
    }

    fun shutdown() {
        stopListening()
        sttManager.shutdown()
        Log.d(TAG, "SpeechCoordinator shutdown")
    }
}
package com.example.blurr.utilities

import android.content.Context
import android.util.Log
import com.example.blurr.api.GoogleTts
import com.example.blurr.api.TTSVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

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
    private val speechMutex = Mutex()
    private var ttsPlaybackJob: Job? = null
    private var isListening = false

    private suspend fun performSpeak(text: String, isUserFacing: Boolean) {
        if (isUserFacing) {
            ttsManager.speakToUser(text)
        } else {
            ttsManager.speakText(text)
        }
    }

    private suspend fun speak(text: String, forceSpeak: Boolean, isUserFacing: Boolean) {
        ttsPlaybackJob?.cancel(CancellationException("New speech request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    if (isListening) {
                        sttManager.stopListening()
                        isListening = false
                        delay(200)
                    }
                    performSpeak(text, isUserFacing)
                } catch (e: CancellationException) {
                    throw e // Re-throw to ensure the coroutine stops
                } catch (e: Exception) {
                    Log.e(TAG, "Error during speech", e)
                }
            }
        }
    }

    suspend fun speakText(text: String, forceSpeak: Boolean = false) {
        speak(text, forceSpeak, isUserFacing = false)
    }

    suspend fun speakToUser(text: String, forceSpeak: Boolean = false) {
        speak(text, forceSpeak, isUserFacing = true)
    }

    /**
     * Plays raw audio data directly using TTSManager, bypassing synthesis.
     * Ideal for playing cached voice samples.
     */
    suspend fun playAudioData(data: ByteArray) {
        ttsPlaybackJob?.cancel(CancellationException("New audio data request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    if (isListening) {
                        sttManager.stopListening()
                        isListening = false
                        delay(200)
                    }
                    // Directly use the TTSManager's playback function
                    ttsManager.playAudioData(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during audio data playback", e)
                }
            }
        }
    }

    /**
     * Synthesizes text with a specific voice and plays it. This method
     * performs the synthesis here, instead of relying on TTSManager's default voice.
     */
    suspend fun testVoice(text: String, voice: TTSVoice) {
        ttsPlaybackJob?.cancel(CancellationException("New voice test request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    if (isListening) {
                        sttManager.stopListening()
                        isListening = false
                        delay(200)
                    }
                    // 1. Synthesize audio with the specific voice HERE
                    val audioData = GoogleTts.synthesize(text, voice)

                    // 2. Play the synthesized audio data
                    ttsManager.playAudioData(audioData)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during voice test", e)
                }
            }
        }
    }

    fun stop() {
        // Cancel the coroutine managing the playback
        ttsPlaybackJob?.cancel(CancellationException("Playback stopped by user action"))
        // Call the underlying TTS Manager's stop function to halt the hardware
        ttsManager.stop()
        Log.d(TAG, "All TTS playback stopped by coordinator.")
    }

    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit
    ) {
        stop() // Use our new stop function to ensure TTS is stopped before listening
        speechMutex.withLock {
            try {
                isListening = true
                sttManager.startListening(
                    onResult = { result -> onResult(result) },
                    onError = { error -> onError(error) },
                    onListeningStateChange = { listening ->
                        isListening = listening
                        onListeningStateChange(listening)
                    }
                )
            } catch (e: Exception) {
                isListening = false
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }

    fun stopListening() {
        if (isListening) {
            sttManager.stopListening()
            isListening = false
        }
    }

    fun shutdown() {
        stop()
        stopListening()
        sttManager.shutdown()
    }
}
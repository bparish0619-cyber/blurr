package com.example.blurr.utilities

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred

// REMOVE the TtsHolder object. It is no longer needed.
// object TtsHolder { ... }

class TTSManager private constructor(context: Context) : OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTTSInitialized = CompletableDeferred<Unit>()
    val audioSessionId: Int
    var utteranceListener: ((isSpeaking: Boolean) -> Unit)? = null
    
    // Debug flag to control TTS output
    private var isDebugMode: Boolean = try {
        // Try to get from BuildConfig, fallback to true for safety
        com.example.blurr.BuildConfig.SPEAK_INSTRUCTIONS
    } catch (e: Exception) {
        true // Default to true if BuildConfig is not available
    }

    init {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioSessionId = audioManager.generateAudioSessionId()
        tts = TextToSpeech(context, this)
    }
    
    // --- 1. ADD THIS COMPANION OBJECT ---
    companion object {
        @Volatile private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            // Double-check locking ensures thread safety
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceListener?.invoke(true)
                }
                override fun onDone(utteranceId: String?) {
                    utteranceListener?.invoke(false)
                }
                override fun onError(utteranceId: String?) {
                    utteranceListener?.invoke(false)
                }
            })
            isTTSInitialized.complete(Unit)
            println("TTS Singleton is ready with Audio Session ID: $audioSessionId (Debug Mode: $isDebugMode)")
        } else {
            println("TTS Initialization failed")
            isTTSInitialized.completeExceptionally(Exception("TTS Initialization failed"))
        }
    }

    suspend fun speakText(text: String) {
        // Only speak if in debug mode
        if (!isDebugMode) {
            println("TTS: Skipping speech in release mode - '$text'")
            return
        }
        
        isTTSInitialized.await()
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, audioSessionId)
        }
        val utteranceId = this.hashCode().toString() + "" + System.currentTimeMillis()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        println("TTS: Speaking '$text'")
    }

    /**
     * Override the debug mode setting
     * @param debugMode true to enable TTS, false to disable
     */
    fun setDebugMode(debugMode: Boolean) {
        isDebugMode = debugMode
        println("TTS Debug Mode set to: $isDebugMode")
    }

    /**
     * Get current debug mode status
     * @return true if TTS is enabled, false if disabled
     */
    fun isDebugModeEnabled(): Boolean = isDebugMode

    fun shutdown() {
        // This should only be called if the entire app is closing
        tts?.stop()
        tts?.shutdown()
        INSTANCE = null
        println("TTS Singleton has been shut down.")
    }
}
package com.example.blurr.api

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.blurr.utilities.STTManager
import java.util.Locale

class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private val sttManager = STTManager(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wakeWord = "Panda"
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private val restartDelayMs = 250L

    fun start() {
        if (isListening) {
            Log.d("WakeWordDetector", "Already started.")
            return
        }
        isListening = true
        Log.d("WakeWordDetector", "Starting to listen for wake word.")
        startContinuousListening()
    }

    fun stop() {
        if (!isListening) {
            Log.d("WakeWordDetector", "Already stopped.")
            return
        }
        isListening = false
        handler.removeCallbacksAndMessages(null)
        sttManager.stopListening()
        sttManager.shutdown()

        // Ensure the sound is unmuted when the service is stopped
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        Log.d("WakeWordDetector", "Stopped listening for wake word.")
    }

    private fun startContinuousListening() {
        if (!isListening) return

        // Mute the notification stream to prevent the startup chime
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)

        sttManager.startListening(
            onResult = { recognizedText ->
                Log.d("WakeWordDetector", "Recognized: '$recognizedText'")
                if (recognizedText.lowercase(Locale.ROOT).contains(wakeWord.lowercase(Locale.ROOT))) {
                    onWakeWordDetected()
                }
                restartListening()
            },
            onError = { errorMessage ->
                Log.e("WakeWordDetector", "STT Error: $errorMessage")
                restartListening()
            },
            onListeningStateChange = { }
        )

        // Unmute the stream shortly after starting to ensure other notifications can be heard
        handler.postDelayed({
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        }, 500) // 500ms is enough time to suppress the chime
    }

    private fun restartListening() {
        if (!isListening) return
        handler.postDelayed({
            sttManager.stopListening()
            startContinuousListening()
        }, restartDelayMs)
    }
}
package com.example.blurr.api

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.*

class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var sttDetector: WakeWordDetector? = null
    private var isListening = false
    private var useSTTFallback = false

    companion object {
        private const val TAG = "PorcupineWakeWordDetector"
        // You'll need to replace this with your actual Picovoice AccessKey
        // Get from BuildConfig or environment variable
        private val ACCESS_KEY: String = try {
            // Try to get from BuildConfig first
            com.example.blurr.BuildConfig.PICOVOICE_ACCESS_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access key from BuildConfig: ${e.message}")
            // Fallback to environment variable or default
            System.getenv("PICOVOICE_ACCESS_KEY") ?: "YOUR_PICOVOICE_ACCESS_KEY_HERE"
        }
    }

    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }

        // Check if access key is valid
        if (ACCESS_KEY.isEmpty() || ACCESS_KEY == "YOUR_PICOVOICE_ACCESS_KEY_HERE") {
            Log.e(TAG, "Invalid or missing Picovoice access key. Falling back to STT-based detection.")
            startSTTFallback()
            return
        }

        try {
            // Create the wake word callback
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                Log.d(TAG, "Wake word detected! Keyword index: $keywordIndex")
                onWakeWordDetected()
                // PorcupineManager should automatically continue listening after detection
            }

            // Create error callback for debugging
            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                // If there's an error, fall back to STT
                if (isListening && !useSTTFallback) {
                    Log.d(TAG, "Falling back to STT due to Porcupine error")
                    startSTTFallback()
                }
            }

            // Build and start PorcupineManager
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPaths(arrayOf("Panda_en_android_v3_0_0.ppn"))
                .setSensitivity(0.5f) // Set sensitivity to 0.5 for better detection
                .setErrorCallback(errorCallback)
                .build(context, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            useSTTFallback = false
            Log.d(TAG, "Porcupine wake word detection started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
            // Fallback to STT-based detection if Porcupine fails
            Log.d(TAG, "Falling back to STT-based wake word detection")
            startSTTFallback()
        }
    }

    fun stop() {
        if (!isListening) {
            Log.d(TAG, "Already stopped.")
            return
        }

        try {
            if (useSTTFallback) {
                sttDetector?.stop()
                sttDetector = null
                Log.d(TAG, "STT fallback wake word detection stopped.")
            } else {
                porcupineManager?.stop()
                porcupineManager?.delete()
                porcupineManager = null
                Log.d(TAG, "Porcupine wake word detection stopped.")
            }
            isListening = false
            useSTTFallback = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }

    private fun startSTTFallback() {
        try {
            sttDetector = WakeWordDetector(context, onWakeWordDetected)
            sttDetector?.start()
            isListening = true
            useSTTFallback = true
            Log.d(TAG, "STT fallback wake word detection started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting STT fallback: ${e.message}")
        }
    }
} 
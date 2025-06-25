package com.example.blurr.utilities
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import kotlinx.coroutines.CompletableDeferred
import java.util.*

class TTSManager(context: Context) : OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTTSInitialized = CompletableDeferred<Unit>() // A deferred to track initialization

    init {
        // Initialize the TextToSpeech engine
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
//            val langResult = tts?.setLanguage(Locale.US)
//            if (langResult == TextToSpeech.LANG_AVAILABLE) {
//                // TTS is initialized and ready
                isTTSInitialized.complete(Unit) // Mark the initialization as complete
                println("TTS is ready")
//            } else {
//                println("Language not supported")
//                isTTSInitialized.completeExceptionally(Exception("Language not available"))
//            }
        } else {
            println("TTS Initialization failed")
            isTTSInitialized.completeExceptionally(Exception("TTS Initialization failed"))
        }
    }

    // Suspend function to wait for TTS to be initialized before speaking
    suspend fun speakText(text: String) {
        // Wait for TTS initialization
        isTTSInitialized.await()

        // After initialization, speak the text
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Cleanup method to release the TTS engine
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

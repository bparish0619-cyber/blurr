package com.example.blurr.utilities

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.blurr.BuildConfig
import com.example.blurr.api.GoogleTts
import com.example.blurr.api.TTSVoice
import com.example.blurr.utilities.VoicePreferenceManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

class TTSManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsInitialized = CompletableDeferred<Unit>()

    // --- NEW: Properties for Caption Management ---
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captionView: View? = null
    private var captionsEnabled = false


    private var audioTrack: AudioTrack? = null
    private var googleTtsPlaybackDeferred: CompletableDeferred<Unit>? = null

    var utteranceListener: ((isSpeaking: Boolean) -> Unit)? = null

    private var isDebugMode: Boolean = try {
        BuildConfig.SPEAK_INSTRUCTIONS
    } catch (e: Exception) {
        true
    }

    companion object {
        @Volatile private var INSTANCE: TTSManager? = null
        private const val SAMPLE_RATE = 24000

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        nativeTts = TextToSpeech(context, this)
        setupAudioTrack()
    }

    private fun setupAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                googleTtsPlaybackDeferred?.complete(Unit)
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        }, Handler(Looper.getMainLooper()))
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        this.captionsEnabled = enabled
        // If captions are disabled while one is showing, remove it immediately.
        if (!enabled) {
            mainHandler.post { removeCaption() }
        }
    }

    fun getCaptionStatus(): Boolean{
        return this.captionsEnabled
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { utteranceListener?.invoke(true) }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { removeCaption() }

                    utteranceListener?.invoke(false) }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { removeCaption() }

                    utteranceListener?.invoke(false) }
            })
            isNativeTtsInitialized.complete(Unit)
        } else {
            isNativeTtsInitialized.completeExceptionally(Exception("Native TTS Initialization failed"))
        }
    }

    // --- NEW PUBLIC FUNCTION TO STOP PLAYBACK ---
    fun stop() {
        // Stop the AudioTrack if it's currently playing
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.stop()
            audioTrack?.flush() // Clear any buffered data
        }
        // Immediately cancel any coroutine that is awaiting playback completion
        if (googleTtsPlaybackDeferred?.isActive == true) {
            googleTtsPlaybackDeferred?.completeExceptionally(CancellationException("Playback stopped by new request."))
        }
    }
    // --- END OF NEW FUNCTION ---

    suspend fun speakText(text: String) {
        if (!isDebugMode) return
        speak(text)
    }

    suspend fun speakToUser(text: String) {
        speak(text)
    }
    // Add this new function inside your TTSManager class
    fun getAudioSessionId(): Int {
        return audioTrack?.audioSessionId ?: 0
    }
    private suspend fun speak(text: String) {
        try {
            val selectedVoice = VoicePreferenceManager.getSelectedVoice(context)
            val audioData = GoogleTts.synthesize(text, selectedVoice)


            // This deferred will complete when onMarkerReached is called.
            googleTtsPlaybackDeferred = CompletableDeferred()

            // Correctly signal start and wait for completion.
            withContext(Dispatchers.Main) {
                showCaption(text)
                utteranceListener?.invoke(true)
            }

            // Write and play audio on a background thread
            withContext(Dispatchers.IO) {
                audioTrack?.play()
                // The number of frames is the size of the data divided by the size of each frame (2 bytes for 16-bit audio).
                val numFrames = audioData.size / 2
                audioTrack?.setNotificationMarkerPosition(numFrames)
                audioTrack?.write(audioData, 0, audioData.size)
            }

            // Wait for the playback to complete, with a timeout for safety.
            withTimeoutOrNull(30000) { // 30-second timeout
                googleTtsPlaybackDeferred?.await()
            }

            audioTrack?.stop()
            audioTrack?.flush()

            withContext(Dispatchers.Main) {
                removeCaption()
                utteranceListener?.invoke(false)
            }

            Log.d("TTSManager", "Successfully played audio from Google TTS.")

        } catch (e: Exception) {
            if (e is CancellationException) throw e // Re-throw cancellation to stop execution
            Log.e("TTSManager", "Google TTS failed: ${e.message}. Falling back to native engine.")
            isNativeTtsInitialized.await()
            nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode().toString())
        }
    }

    suspend fun playAudioData(audioData: ByteArray) {
        try {
            googleTtsPlaybackDeferred = CompletableDeferred()
            withContext(Dispatchers.Main) {
                utteranceListener?.invoke(true)
            }


            withContext(Dispatchers.IO) {
                audioTrack?.play()
                val numFrames = audioData.size / 2
                audioTrack?.setNotificationMarkerPosition(numFrames)
                audioTrack?.write(audioData, 0, audioData.size)
            }

            withTimeoutOrNull(30000) { googleTtsPlaybackDeferred?.await() }

            withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("TTSManager", "Error playing audio data", e)
        } finally {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
                audioTrack?.flush()
            }
            if (utteranceListener != null && Looper.myLooper() != Looper.getMainLooper()) {
                withContext(Dispatchers.Main) { utteranceListener?.invoke(false) }
            } else {
                utteranceListener?.invoke(false)
            }
        }
    }

    // --- NEW: Private method to display the caption view ---
    private fun showCaption(text: String) {
        if (!captionsEnabled) return

        removeCaption() // Remove any previous caption first

        // Create and style the new TextView.
        val textView = TextView(context).apply {
            this.text = text
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt()) // 80% opaque black
                cornerRadius = 24f
            }
            setTextColor(0xFFFFFFFF.toInt()) // White text
            textSize = 16f
            setPadding(24, 16, 24, 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 250 // Pixels up from the bottom of the screen
        }

        try {
            windowManager.addView(textView, params)
            captionView = textView // Save a reference to the new view.
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to display caption on screen.", e)
        }
    }
    // --- NEW: Private method to remove the caption view ---
    private fun removeCaption() {
        captionView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e("TTSManager", "Error removing caption view.", e)
                }
            }
        }
        captionView = null
    }
    fun shutdown() {
        stop()
        nativeTts?.shutdown()
        audioTrack?.release()
        audioTrack = null
        INSTANCE = null
    }
}
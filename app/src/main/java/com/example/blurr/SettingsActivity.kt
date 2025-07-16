package com.example.blurr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.blurr.agent.VisionMode
import com.example.blurr.api.GoogleTts
import com.example.blurr.api.TTSVoice
import com.example.blurr.services.EnhancedWakeWordService
import com.example.blurr.utilities.SpeechCoordinator
import com.example.blurr.utilities.VoicePreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class SettingsActivity : AppCompatActivity() {

    private lateinit var ttsVoicePicker: NumberPicker
    private lateinit var backButton: Button
    private lateinit var permissionsInfoButton: TextView
    private lateinit var visionModeGroup: RadioGroup
    private lateinit var wakeWordEngineGroup: RadioGroup
    private lateinit var visionModeDescription: TextView
    private lateinit var wakeWordButton: Button

    private lateinit var sc: SpeechCoordinator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var availableVoices: List<TTSVoice>
    private var voiceTestJob: Job? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                lifecycleScope.launch {
                    startWakeWordService()
                }
            } else {
                Toast.makeText(this, "Microphone permission is required for wake word.", Toast.LENGTH_LONG).show()
            }
        }

    companion object {
        private const val PREFS_NAME = "BlurrSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SELECTED_VISION_MODE = "selected_vision_mode"
        private const val KEY_SELECTED_WAKE_WORD_ENGINE = "selected_wake_word_engine"
        private const val TEST_TEXT = "Hello, I'm Panda, and this is a test of the selected voice."
        private val DEFAULT_VOICE = TTSVoice.CHIRP_PUCK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initialize()
        setupUI()
        loadAllSettings()
        setupAutoSavingListeners()
        cacheVoiceSamples()
    }

    override fun onStop() {
        super.onStop()
        // Stop any lingering voice tests when the user leaves the screen
        sc.stop()
        voiceTestJob?.cancel()
    }

    private fun initialize() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sc = SpeechCoordinator.getInstance(this)
        availableVoices = GoogleTts.getAvailableVoices()
    }

    private fun setupUI() {
        ttsVoicePicker = findViewById(R.id.ttsVoicePicker)
        backButton = findViewById(R.id.id_backButtonSettings)
        permissionsInfoButton = findViewById(R.id.permissionsInfoButton)
        visionModeGroup = findViewById(R.id.visionModeGroup)
        visionModeDescription = findViewById(R.id.visionModeDescription)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        wakeWordEngineGroup = findViewById(R.id.wakeWordEngineGroup)

        setupClickListeners()
        setupVoicePicker()
    }

    private fun setupVoicePicker() {
        val voiceDisplayNames = availableVoices.map { it.displayName }.toTypedArray()
        ttsVoicePicker.minValue = 0
        ttsVoicePicker.maxValue = voiceDisplayNames.size - 1
        ttsVoicePicker.displayedValues = voiceDisplayNames
        ttsVoicePicker.wrapSelectorWheel = false
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        permissionsInfoButton.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            startActivity(intent)
        }
        wakeWordButton.setOnClickListener {
            if (EnhancedWakeWordService.isRunning) {
                stopService(Intent(this, EnhancedWakeWordService::class.java))
                Toast.makeText(this, getString(R.string.wake_word_disabled), Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    updateUIState()
                }
            } else {
                lifecycleScope.launch {
                    startWakeWordService()
                }
            }
        }

    }

    private fun setupAutoSavingListeners() {
        var isInitialLoad = true

        ttsVoicePicker.setOnValueChangedListener { _, _, newVal ->
            val selectedVoice = availableVoices[newVal]
            saveSelectedVoice(selectedVoice)

            if (!isInitialLoad) {
                voiceTestJob?.cancel()
                voiceTestJob = lifecycleScope.launch {
                    delay(400L)
                    // First, stop any currently playing voice
                    sc.stop()
                    // Then, play the new sample
                    playVoiceSample(selectedVoice)
                }
            }
        }

        visionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val modeName = if (checkedId == R.id.xmlModeRadio) "XML" else "Screenshot"
            visionModeDescription.text = if (checkedId == R.id.xmlModeRadio) VisionMode.XML.description else VisionMode.SCREENSHOT.description
            saveVisionMode(checkedId)
            if (!isInitialLoad) {
                Toast.makeText(this, "Vision Mode set to $modeName", Toast.LENGTH_SHORT).show()
            }
        }

        wakeWordEngineGroup.setOnCheckedChangeListener { _, checkedId ->
            saveWakeWordEngine(checkedId)
            val engineName = if (checkedId == R.id.sttEngineRadio) "STT" else "Porcupine"
            if (!isInitialLoad) {
                Toast.makeText(this, "Wake Word Engine set to $engineName", Toast.LENGTH_SHORT).show()
            }
        }

        ttsVoicePicker.post {
            isInitialLoad = false
        }
    }

    private fun playVoiceSample(voice: TTSVoice) {
        lifecycleScope.launch {
            val cacheDir = File(cacheDir, "voice_samples")
            val voiceFile = File(cacheDir, "${voice.name}.wav")

            try {
                if (voiceFile.exists()) {
                    // ✅ If sample is cached, read the raw audio bytes
                    val audioData = voiceFile.readBytes()
                    // Play the raw audio data directly
                    sc.playAudioData(audioData)
                    Log.d("SettingsActivity", "Playing cached sample for ${voice.displayName}")
                } else {
                    // ✅ If not cached, synthesize and play using the specific voice
                    sc.testVoice(TEST_TEXT, voice)
                    Log.d("SettingsActivity", "Synthesizing test for ${voice.displayName}")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("SettingsActivity", "Error playing voice sample", e)
                    Toast.makeText(this@SettingsActivity, "Error playing voice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cacheVoiceSamples() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheDir = File(cacheDir, "voice_samples")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            var downloadedCount = 0
            for (voice in availableVoices) {
                val voiceFile = File(cacheDir, "${voice.name}.wav")
                if (!voiceFile.exists()) {
                    try {
                        val audioData = GoogleTts.synthesize(TEST_TEXT, voice)
                        voiceFile.writeBytes(audioData)
                        downloadedCount++
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Failed to cache voice ${voice.name}", e)
                    }
                }
            }
            if (downloadedCount > 0) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "$downloadedCount voice samples prepared.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun startWakeWordService() {
        val usePorcupine = wakeWordEngineGroup.checkedRadioButtonId == R.id.porcupineEngineRadio
        if (usePorcupine && !isPorcupineAccessKeyConfigured()) {
            Toast.makeText(this, getString(R.string.porcupine_access_key_required), Toast.LENGTH_LONG).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(this, EnhancedWakeWordService::class.java).apply {
                putExtra(EnhancedWakeWordService.EXTRA_USE_PORCUPINE, usePorcupine)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            val engineName = if (usePorcupine) "Porcupine" else "STT"
            Toast.makeText(this, getString(R.string.wake_word_enabled, engineName), Toast.LENGTH_SHORT).show()
            updateUIState()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private suspend fun updateUIState() {
        delay(500)
        wakeWordButton.text = if (EnhancedWakeWordService.isRunning) getString(R.string.wake_word_disabled) else getString(R.string.enable_wake_word)
    }

    private fun loadAllSettings() {
        val savedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE.name)
        val savedVoice = availableVoices.find { it.name == savedVoiceName } ?: DEFAULT_VOICE
        ttsVoicePicker.value = availableVoices.indexOf(savedVoice)

        val savedVisionId = sharedPreferences.getInt(KEY_SELECTED_VISION_MODE, R.id.xmlModeRadio)
        visionModeGroup.check(savedVisionId)

        val savedWakeWordId = sharedPreferences.getInt(KEY_SELECTED_WAKE_WORD_ENGINE, R.id.sttEngineRadio)
        wakeWordEngineGroup.check(savedWakeWordId)
    }

    private fun saveSelectedVoice(voice: TTSVoice) {
        VoicePreferenceManager.saveSelectedVoice(this, voice)
        Log.d("SettingsActivity", "Saved voice: ${voice.displayName}")
    }



    private fun saveVisionMode(checkedId: Int) {
        sharedPreferences.edit {
            putInt(KEY_SELECTED_VISION_MODE, checkedId)
        }
    }

    private fun saveWakeWordEngine(checkedId: Int) {
        sharedPreferences.edit {
            putInt(KEY_SELECTED_WAKE_WORD_ENGINE, checkedId)
        }
    }

    private fun isPorcupineAccessKeyConfigured(): Boolean {
        return try {
            val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
            accessKey.isNotEmpty() && accessKey != "your_actual_access_key_here"
        } catch (e: Exception) {
            false
        }
    }
}
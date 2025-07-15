package com.example.blurr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
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
import com.example.blurr.utilities.VoicePreferenceManager // Import VoicePreferenceManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    // --- UI Views ---
    private lateinit var ttsVoiceSpinner: Spinner
    private lateinit var testVoiceButton: Button
    private lateinit var backButton: Button
    private lateinit var permissionsInfoButton: TextView
    private lateinit var visionModeGroup: RadioGroup
    private lateinit var wakeWordEngineGroup: RadioGroup
    private lateinit var visionModeDescription: TextView
    private lateinit var wakeWordButton: Button

    // --- Utilities & Data ---
    private lateinit var sc: SpeechCoordinator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var availableVoices: List<TTSVoice>

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("SettingsActivity", "Permission GRANTED.")
                startWakeWordService()
            } else {
                Log.w("SettingsActivity", "Permission DENIED.")
                Toast.makeText(this, "Microphone permission is required for wake word.", Toast.LENGTH_LONG).show()
            }
        }

    companion object {
        private const val PREFS_NAME = "BlurrSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SELECTED_VISION_MODE = "selected_vision_mode"
        private const val KEY_SELECTED_WAKE_WORD_ENGINE = "selected_wake_word_engine"
        private const val TEST_TEXT = "Hello! This is a test of the selected voice."
        private val DEFAULT_VOICE = TTSVoice.CHIRP_PUCK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initialize()
        setupUI()

        // Load settings before setting up the auto-save listeners to prevent unwanted triggers
        loadAllSettings()
        // Attach listeners after the initial state is set
        setupAutoSavingListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun initialize() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sc = SpeechCoordinator.getInstance(this)
        availableVoices = GoogleTts.getAvailableVoices()
    }

    private fun setupUI() {
        // Find views by their IDs
        ttsVoiceSpinner = findViewById(R.id.ttsVoiceSpinner)
        testVoiceButton = findViewById(R.id.testVoiceButton)
        backButton = findViewById(R.id.id_backButtonSettings) // Updated ID as per XML below
        permissionsInfoButton = findViewById(R.id.permissionsInfoButton)
        visionModeGroup = findViewById(R.id.visionModeGroup)
        visionModeDescription = findViewById(R.id.visionModeDescription)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        wakeWordEngineGroup = findViewById(R.id.wakeWordEngineGroup)

        setupClickListeners()
        setupSpinner()
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        testVoiceButton.setOnClickListener { testSelectedVoice() }
        permissionsInfoButton.setOnClickListener {
            val intent = Intent(this, PermissionsActivity::class.java)
            startActivity(intent)
        }
        wakeWordButton.setOnClickListener {
            if (EnhancedWakeWordService.isRunning) {
                stopService(Intent(this, EnhancedWakeWordService::class.java))
                Toast.makeText(this, getString(R.string.wake_word_disabled), Toast.LENGTH_SHORT).show()
                updateUIState()
            } else {
                startWakeWordService()
            }
        }
    }

    private fun setupAutoSavingListeners() {
        // Use a flag to prevent toasts from firing during initial load
        var isInitialLoad = true

        ttsVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedVoice = availableVoices[position]
                saveSelectedVoice(selectedVoice)
                if (!isInitialLoad) { // Only show toast if not during initial load
                    Toast.makeText(this@SettingsActivity, "Voice set to ${selectedVoice.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        visionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val modeName = if (checkedId == R.id.xmlModeRadio) "XML" else "Screenshot"
            visionModeDescription.text = if (checkedId == R.id.xmlModeRadio) VisionMode.XML.description else VisionMode.SCREENSHOT.description
            saveVisionMode(checkedId)
            if (!isInitialLoad) { // Only show toast if not during initial load
                Toast.makeText(this, "Vision Mode set to $modeName", Toast.LENGTH_SHORT).show()
            }
        }

        wakeWordEngineGroup.setOnCheckedChangeListener { _, checkedId ->
            saveWakeWordEngine(checkedId)
            val engineName = if (checkedId == R.id.sttEngineRadio) "STT" else "Porcupine"
            if (!isInitialLoad) { // Only show toast if not during initial load
                Toast.makeText(this, "Wake Word Engine set to $engineName", Toast.LENGTH_SHORT).show()
            }
        }

        // Ensure this runs after onCreate finishes its layout and initial selections
        // so isInitialLoad properly transitions to false after initial setup.
        ttsVoiceSpinner.post {
            isInitialLoad = false
        }
    }

    private fun startWakeWordService() {
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

    private fun updateUIState() {
        wakeWordButton.text = if (EnhancedWakeWordService.isRunning) getString(R.string.wake_word_disabled) else getString(R.string.enable_wake_word)
    }

    private fun loadAllSettings() {
        // Load TTS voice
        val savedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE.name)
        val savedVoice = availableVoices.find { it.name == savedVoiceName } ?: DEFAULT_VOICE
        ttsVoiceSpinner.setSelection(availableVoices.indexOf(savedVoice), false)

        // Load Vision Mode
        val savedVisionId = sharedPreferences.getInt(KEY_SELECTED_VISION_MODE, R.id.xmlModeRadio)
        visionModeGroup.check(savedVisionId)

        // Load Wake Word Engine
        val savedWakeWordId = sharedPreferences.getInt(KEY_SELECTED_WAKE_WORD_ENGINE, R.id.sttEngineRadio)
        wakeWordEngineGroup.check(savedWakeWordId)
    }

    private fun testSelectedVoice() {
        // Get the selected voice from preferences (using the utility object)
        val selectedVoice = VoicePreferenceManager.getSelectedVoice(this) // Uses the global VoicePreferenceManager

        testVoiceButton.isEnabled = false
        testVoiceButton.text = getString(R.string.testing_voice)

        lifecycleScope.launch {
            try {
                // sc is your SpeechCoordinator instance, which uses TTSManager internally
                sc.speakToUser(TEST_TEXT)
                Toast.makeText(this@SettingsActivity, "Playing: ${selectedVoice.displayName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error testing voice: ${e.message}", e)
                Toast.makeText(this@SettingsActivity, "Error testing voice.", Toast.LENGTH_LONG).show()
            } finally {
                testVoiceButton.isEnabled = true
                testVoiceButton.text = getString(R.string.test_selected_voice)
            }
        }
    }

    // Removed the private fun getSelectedVoice(): TTSVoice method as it's no longer needed.

    private fun saveSelectedVoice(voice: TTSVoice) {
        // Now directly use VoicePreferenceManager to save
        VoicePreferenceManager.saveSelectedVoice(this, voice)
        Log.d("SettingsActivity", "Saved voice: ${voice.displayName}")
    }

    private fun saveVisionMode(checkedId: Int) {
        sharedPreferences.edit {
            putInt(KEY_SELECTED_VISION_MODE, checkedId)
        }
        Log.d("SettingsActivity", "Saved vision mode ID: $checkedId")
    }

    private fun saveWakeWordEngine(checkedId: Int) {
        sharedPreferences.edit {
            putInt(KEY_SELECTED_WAKE_WORD_ENGINE, checkedId)
        }
        Log.d("SettingsActivity", "Saved wake word engine ID: $checkedId")
    }

    private fun setupSpinner() {
        val voiceDisplayNames = availableVoices.map { it.displayName }
        val adapter = ArrayAdapter(this, R.layout.custom_spinner_item, voiceDisplayNames)
        adapter.setDropDownViewResource(R.layout.custom_spinner_item)
        ttsVoiceSpinner.adapter = adapter
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
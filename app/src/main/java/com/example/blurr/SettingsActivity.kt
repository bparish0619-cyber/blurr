package com.example.blurr

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.blurr.api.GoogleTts
import com.example.blurr.api.TTSVoice
import com.example.blurr.utilities.SpeechCoordinator
import com.example.blurr.utilities.TTSManager
import kotlinx.coroutines.launch
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {

    // --- UI Views ---
    private lateinit var ttsVoiceSpinner: Spinner
    private lateinit var testVoiceButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var backButton: TextView

    // --- Utilities & Data ---
    private lateinit var sc: SpeechCoordinator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var availableVoices: List<TTSVoice>

    companion object {
        private const val PREFS_NAME = "TTSVoiceSettings"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val TEST_TEXT = "Hello! This is a test of the selected voice."
        // Use a valid default voice from your new list
        private val DEFAULT_VOICE = TTSVoice.CHIRP_ZEPHYR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure the layout name matches your file, e.g., R.layout.activity_settings
        setContentView(R.layout.activity_settings)

        initialize()
        setupUI()
    }

    /**
     * Initializes core components like SharedPreferences, TTSManager, and fetches voices.
     */
    private fun initialize() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sc = SpeechCoordinator.getInstance(this)
        availableVoices = GoogleTts.getAvailableVoices()
    }

    /**
     * Sets up all the user interface elements and their listeners.
     */
    private fun setupUI() {
        // Find views by their new IDs from your updated layout
        ttsVoiceSpinner = findViewById(R.id.ttsVoiceSpinner)
        testVoiceButton = findViewById(R.id.testVoiceButton)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)

        // Populate the spinner with voice options
        setupSpinner()

        // Load the last saved voice selection
        loadSavedVoice()

        // Set up button click actions
        setupClickListeners()
    }

    /**
     * Creates an adapter and populates the spinner with the voice display names.
     */
    private fun setupSpinner() {
        val voiceDisplayNames = availableVoices.map { it.displayName }
        val adapter = ArrayAdapter(this, R.layout.custom_spinner_item, voiceDisplayNames)

        // You can also apply the custom style to the dropdown view
        adapter.setDropDownViewResource(R.layout.custom_spinner_item)

        ttsVoiceSpinner.adapter = adapter
    }

    /**
     * Sets OnClickListeners for the interactive buttons.
     */
    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        testVoiceButton.setOnClickListener { testSelectedVoice() }

        saveButton.setOnClickListener { saveSelectedVoice() }
    }

    /**
     * Loads the saved voice from SharedPreferences and updates the spinner's selection.
     * This now safely handles cases where the saved voice is no longer valid.
     */
    private fun loadSavedVoice() {
        val savedVoiceName = sharedPreferences.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE.name)
        // Find the voice by its name, or fall back to the default if not found
        val savedVoice = availableVoices.find { it.name == savedVoiceName } ?: DEFAULT_VOICE
        val selectionIndex = availableVoices.indexOf(savedVoice)

        if (selectionIndex != -1) {
            ttsVoiceSpinner.setSelection(selectionIndex)
        }
    }

    /**
     * Gets the currently selected TTSVoice from the spinner.
     */
    private fun getSelectedVoice(): TTSVoice {
        val selectedPosition = ttsVoiceSpinner.selectedItemPosition
        // Return the selected voice, or default if the position is somehow invalid
        return availableVoices.getOrElse(selectedPosition) { DEFAULT_VOICE }
    }

    /**
     * Tests the currently selected voice by synthesizing and playing a sample text.
     */
    private fun testSelectedVoice() {
        val selectedVoice = getSelectedVoice()
        saveSelectedVoice()
        testVoiceButton.isEnabled = false
        testVoiceButton.text = "Testing..." // Use string resource for better practice

        lifecycleScope.launch {
            try {
                val tts = TTSManager.getInstance(this@SettingsActivity)
                tts.speakText(TEST_TEXT)
                Toast.makeText(this@SettingsActivity, "Playing: ${selectedVoice.displayName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error testing voice: ${e.message}", e)
                Toast.makeText(this@SettingsActivity, "Error testing voice.", Toast.LENGTH_LONG).show()
            } finally {
                testVoiceButton.isEnabled = true
                testVoiceButton.text = "Test Selected Voice" // Use string resource
            }
        }
    }

    /**
     * Saves the currently selected voice name to SharedPreferences.
     */
    private fun saveSelectedVoice() {
        val selectedVoice = getSelectedVoice()
        sharedPreferences.edit {
            putString(KEY_SELECTED_VOICE, selectedVoice.name)
        }

        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        Log.d("SettingsActivity", "Saved voice: ${selectedVoice.displayName}")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Gracefully shut down the TTS manager to release resources

    }
}
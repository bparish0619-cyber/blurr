package com.example.blurr

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

import com.example.blurr.api.Finger
import kotlinx.coroutines.*
import java.io.File

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blurr.agent.DeepSearch
import com.example.blurr.api.Eyes
import com.example.blurr.api.Quadruple
import com.example.blurr.utilities.TTSManager
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var inputField: EditText
    private lateinit var contentModerationInputField: EditText
    private lateinit var performTaskButton: TextView
    private lateinit var contentModerationButton: TextView
    private lateinit var runnable: Runnable
    private lateinit var handler: Handler
    private lateinit var startAgent : Button
    private lateinit var stopAgent : Button
    private lateinit var grantPermission: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvServiceStatus: TextView

    // The TTSManager to speak results
    private lateinit var ttsManager: TTSManager
    // Our new, intelligent DeepSearch agent
    private lateinit var deepSearchAgent: DeepSearch

    private lateinit var speechRecognizer: SpeechRecognizer
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("MainActivity", "Notification permission GRANTED.")
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("MainActivity", "Notification permission DENIED.")
                Toast.makeText(this, "Notification permission denied. The service notification will not be visible.", Toast.LENGTH_LONG).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        askForNotificationPermission()

        startAgent = findViewById(R.id.btn_start_service)
        stopAgent = findViewById(R.id.btn_stop_service)
        grantPermission = findViewById(R.id.btn_request_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        inputField = findViewById(R.id.inputField)
        contentModerationInputField = findViewById(R.id.contentMoniterInputField)
        performTaskButton = findViewById(R.id.performTaskButton)
        contentModerationButton = findViewById(R.id.contentMoniterButton)
        statusText = findViewById(R.id.tv_service_status)

        grantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        ttsManager = TTSManager(this)
        // Initialize our DeepSearch agent
        deepSearchAgent = DeepSearch()
        setupClickListeners()
        handler = Handler(Looper.getMainLooper())

    }

    fun appendToFile(file: File, content: String) {
        file.appendText(content + "\n")
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun setupClickListeners() {
        performTaskButton.setOnClickListener {
            val instruction = inputField.text.toString().trim()
            if (instruction.isBlank()) {
                Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText.text = "Thinking..."
            performTaskButton.isEnabled = false

            lifecycleScope.launch {
                // 1. Let the DeepSearch agent classify the instruction and execute if necessary
                val finalAnswer = deepSearchAgent.execute(instruction)

                // NOTE: The logic to decide between SEARCH or UI_TASK is now fully
                // encapsulated within the DeepSearch agent. Our MainActivity only
                // needs to know the final result. For this implementation, we will assume
                // DeepSearch returns a user-facing string for both cases.
                // A more robust implementation could return a data class with action type + payload.

                // Check if the response indicates a UI task or is a direct answer
                if (finalAnswer == "NO-SEARCH") {
                    // If it's a UI task, start the background service
                    Log.d("MainActivity", "This is a UI Task. Starting AgentTaskService.")
                    statusText.text = "Agent started to perform task..."
                    Toast.makeText(this@MainActivity, "Agent Task Started", Toast.LENGTH_SHORT).show()

                    val serviceIntent = Intent(this@MainActivity, AgentTaskService::class.java).apply {
                        putExtra("TASK_INSTRUCTION", instruction)
                    }
                    startService(serviceIntent)

                    // Go to the home screen to let the agent work
                    val fin = Finger(this@MainActivity)
                    fin.home()
                } else {
                    println("Final Answer: $finalAnswer")
                    // If it was a search, show the result and speak it
                    Log.d("MainActivity", "Deep Search complete. Answer: $finalAnswer")
                    statusText.text = finalAnswer // Display the answer in your UI
                    ttsManager.speakText(finalAnswer) // Use TTS to speak the answer
                }
            }
        }

        contentModerationButton.setOnClickListener {
            // You should apply the same fix here!
            lifecycleScope.launch {
                val instruction = contentModerationInputField.text.toString()
                if (instruction.isBlank()) {
                    Toast.makeText(this@MainActivity, "Please enter an instruction", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fin = Finger(this@MainActivity)
                fin.home()

                delay(1500)

                Log.d("MainActivity", "Starting ContentModerationService after delay.")
                val serviceIntent = Intent(this@MainActivity, ContentModerationService::class.java).apply {
                    putExtra("MODERATION_INSTRUCTION", instruction)
                }
                startService(serviceIntent)

                updateUI()
                Toast.makeText(this@MainActivity, "Content Moderation Started", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isUiTask(response: String): Boolean {
        // You can define specific phrases that indicate a UI task.
        val uiTaskPhrases = listOf(
            "agent started to perform task",
            "this is a task for the agent",
            "initiating ui automation"
        )
        // For now, let's assume any response that isn't a long-form answer is a UI task trigger.
        // A better way would be for the agent to return a specific keyword or JSON object.
        // Let's simulate that for now.
        return response.contains("task for the agent")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w("AppMemory", "onTrimMemory event received with level: $level")

        // Use a 'when' statement to react to the different levels
        when (level) {
            // --- While your app is running in the foreground ---
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.i("AppMemory", "MEMORY WARNING: Running Moderate. Consider releasing some cache.")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w("AppMemory", "MEMORY WARNING: Running Low. Release non-essential resources.")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e("AppMemory", "MEMORY WARNING: Running CRITICAL. System is killing other apps.")
            }


            // --- When your app's UI is no longer visible ---
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.i("AppMemory", "UI is hidden. Release all UI-related resources (Bitmaps, etc.).")
                // This is the best place to free up memory used by your UI.
            }


            // --- When your app is in the background and at risk of being killed ---
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.e("AppMemory", "CRITICAL WARNING: App is in the background and is a prime candidate for termination.")
                // If you get this, your process could be killed at any moment.
                // This is your last chance to save any critical state.
            }

            else -> {
                Log.d("AppMemory", "Unhandled memory trim level: $level")
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Start the periodic task when the activity is resumed
        updateUI()
//        handler.post(runnable)
    }
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    val componentName = splitter.next()
                    if (componentName.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        val isPermissionGranted = isAccessibilityServiceEnabled()

        tvPermissionStatus.text = if (isPermissionGranted) "Permission: Granted" else "Permission: Not Granted"
        tvPermissionStatus.setTextColor(if (isPermissionGranted) Color.GREEN else Color.RED)
    }
    private fun askForNotificationPermission() {
        // This is only required for Android 13 (API 33) and higher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                // Check if the permission is already granted.
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    Log.i("MainActivity", "Notification permission is already granted.")
                }
                // Explain to the user why you need the permission.
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // In a real app, you'd show a dialog explaining why you need this.
                    // For now, we'll just launch the request.
                    Log.w("MainActivity", "Showing rationale and requesting permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                // Directly ask for the permission.
                else -> {
                    Log.i("MainActivity", "Requesting notification permission for the first time.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

}

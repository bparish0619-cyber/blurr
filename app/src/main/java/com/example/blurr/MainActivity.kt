package com.example.blurr

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.api.Finger
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blurr.agent.DeepSearch
import com.example.blurr.agent.VisionMode
import com.example.blurr.agent.ClarificationAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.AgentConfig
import com.example.blurr.utilities.TTSManager
import com.example.blurr.utilities.STTManager
import com.example.blurr.utilities.UserIdManager
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.blurr.agent.VisionHelper
import com.example.blurr.services.AgentTaskService
import com.example.blurr.services.WakeWordService
import com.example.blurr.services.EnhancedWakeWordService
import com.example.blurr.utilities.getReasoningModelApiResponse

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var inputField: EditText
    private lateinit var contentModerationInputField: EditText
    private lateinit var performTaskButton: TextView
    private lateinit var contentModerationButton: TextView
    private lateinit var handler: Handler
    private lateinit var grantPermission: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var visionModeGroup: RadioGroup
    private lateinit var xmlModeRadio: RadioButton
    private lateinit var screenshotModeRadio: RadioButton
    private lateinit var visionModeDescription: TextView
    private lateinit var voiceInputButton: ImageButton
    private lateinit var voiceStatusText: TextView
    private lateinit var wakeWordButton: TextView
    private lateinit var wakeWordEngineGroup: RadioGroup
    private lateinit var sttEngineRadio: RadioButton
    private lateinit var porcupineEngineRadio: RadioButton

    private lateinit var ttsManager: TTSManager
    private lateinit var sttManager: STTManager
    private lateinit var deepSearchAgent: DeepSearch
    private lateinit var clarificationAgent: ClarificationAgent
    private lateinit var userId: String

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("MainActivity", "Permission GRANTED.")
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("MainActivity", "Permission DENIED.")
                Toast.makeText(this, "Permission denied. Some features may not work properly.", Toast.LENGTH_LONG).show()
            }
        }

    private val dialogueLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val enhancedInstruction = result.data?.getStringExtra(DialogueActivity.EXTRA_ENHANCED_INSTRUCTION)
            if (!enhancedInstruction.isNullOrEmpty()) {
                // Execute the enhanced instruction
                executeTask(enhancedInstruction)
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Dialogue cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Creating a unique uuid
        val userIdManager = UserIdManager(applicationContext)
        userId = userIdManager.getOrCreateUserId()
        println(userId)

        askForNotificationPermission()

        grantPermission = findViewById(R.id.btn_request_permission)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        inputField = findViewById(R.id.inputField)
        contentModerationInputField = findViewById(R.id.contentMoniterInputField)
        performTaskButton = findViewById(R.id.performTaskButton)
        contentModerationButton = findViewById(R.id.contentMoniterButton)
        statusText = findViewById(R.id.tv_service_status)
        visionModeGroup = findViewById(R.id.visionModeGroup)
        xmlModeRadio = findViewById(R.id.xmlModeRadio)
        screenshotModeRadio = findViewById(R.id.screenshotModeRadio)
        visionModeDescription = findViewById(R.id.visionModeDescription)
        voiceInputButton = findViewById(R.id.voiceInputButton)
        voiceStatusText = findViewById(R.id.voiceStatusText)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        wakeWordEngineGroup = findViewById(R.id.wakeWordEngineGroup)
        sttEngineRadio = findViewById(R.id.sttEngineRadio)
        porcupineEngineRadio = findViewById(R.id.porcupineEngineRadio)

        grantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        ttsManager = TTSManager.getInstance(this)

        sttManager = STTManager(this)
        deepSearchAgent = DeepSearch()
        clarificationAgent = ClarificationAgent()
        setupClickListeners()
        setupVisionModeListener()
        setupVoiceInput()
        handler = Handler(Looper.getMainLooper())


        val karanTextView = findViewById<TextView>(R.id.karan_textview_gradient)
        karanTextView.measure(0, 0)
        val textShader: Shader = LinearGradient(
            0f,
            0f,
            karanTextView.measuredWidth.toFloat(),
            0f,
            intArrayOf(
                "#BE63F3".toColorInt(),
                "#5880F7".toColorInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
        karanTextView.paint.shader = textShader
        checkAndRequestOverlayPermission()
        val githubLink = findViewById<TextView>(R.id.github_link_textview)
        githubLink.setOnClickListener {
            val url = "https://github.com/Ayush0Chaudhary/blurr"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            startActivity(intent)
        }
        wakeWordButton.setOnClickListener {
            if (EnhancedWakeWordService.isRunning) {
                // Service is running, so stop it
                Log.d("MainActivity", "Stopping EnhancedWakeWordService.")
                stopService(Intent(this, EnhancedWakeWordService::class.java))
                Toast.makeText(this, getString(R.string.wake_word_disabled), Toast.LENGTH_SHORT).show()
            } else {
                // Service is not running, so start it
                Log.d("MainActivity", "Starting EnhancedWakeWordService.")
                val usePorcupine = porcupineEngineRadio.isChecked
                
                if (usePorcupine) {
                    // Check if Porcupine access key is configured
                    if (!isPorcupineAccessKeyConfigured()) {
                        Toast.makeText(this, getString(R.string.porcupine_access_key_required), Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }
                
                val serviceIntent = Intent(this, EnhancedWakeWordService::class.java).apply {
                    putExtra(EnhancedWakeWordService.EXTRA_USE_PORCUPINE, usePorcupine)
                }
                
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                    val engineName = if (usePorcupine) "Porcupine" else "STT"
                    Toast.makeText(this, getString(R.string.wake_word_enabled, engineName), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission is required for wake word.", Toast.LENGTH_LONG).show()
                    askForNotificationPermission()
                }
            }
            updateUI() // Update button text immediately
        }
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
                try {
                    // Check if clarification is needed
                    val needsClarification = checkIfClarificationNeeded(instruction)

                    if (needsClarification.first) {
                        // Start dialogue for clarification
                        startClarificationDialogue(instruction, needsClarification.second)
                    } else {
                        // Execute the task directly
                        executeTask(instruction)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing instruction", e)
                    val errorMessage = "Error processing your instruction: ${e.message}"
                    statusText.text = errorMessage
                    ttsManager.speakText(errorMessage)
                } finally {
                    performTaskButton.isEnabled = true
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

    private fun setupVisionModeListener() {
        visionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            visionModeDescription.text = when (checkedId) {
                R.id.xmlModeRadio -> VisionMode.XML.description
                R.id.screenshotModeRadio -> VisionMode.SCREENSHOT.description
                else -> VisionMode.XML.description
            }
        }
    }

    private fun setupVoiceInput() {
        voiceInputButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Start listening when button is pressed
                    startVoiceInput()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // Stop listening when button is released
                    stopVoiceInput()
                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceInput() {
        voiceStatusText.text = getString(R.string.listening)
        voiceInputButton.isPressed = true

        sttManager.startListening(
            onResult = { recognizedText ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    inputField.setText(recognizedText)
                    Toast.makeText(this, "Recognized: $recognizedText", Toast.LENGTH_SHORT).show()

                    // Automatically perform the task
                    performTaskFromVoiceInput(recognizedText)
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    voiceStatusText.text = getString(R.string.hold_to_speak)
                    voiceInputButton.isPressed = false
                    Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            },
            onListeningStateChange = { isListening ->
                runOnUiThread {
                    voiceInputButton.isPressed = isListening
                    voiceStatusText.text = if (isListening) getString(R.string.listening) else getString(R.string.hold_to_speak)
                }
            }
        )
    }

    private fun performTaskFromVoiceInput(instruction: String) {
        statusText.text = "Processing voice command..."

        lifecycleScope.launch {
            try {
                // Check if clarification is needed
                val needsClarification = checkIfClarificationNeeded(instruction)

                if (needsClarification.first) {
                    // Start dialogue for clarification
                    startClarificationDialogue(instruction, needsClarification.second)
                } else {
                    // Execute the task directly
                    executeTask(instruction)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing voice command", e)
                val errorMessage = "Error processing your command: ${e.message}"
                statusText.text = errorMessage
                ttsManager.speakText(errorMessage)
            }
        }
    }

    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        try {
            // Create a temporary InfoPool for the clarification agent
            val tempInfoPool = InfoPool(instruction = instruction)
            println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            // Get clarification response
            val config = AgentConfig(visionMode = VisionMode.XML, apiKey = "", context = this)
            val prompt = clarificationAgent.getPrompt(tempInfoPool, config)
            val chat = clarificationAgent.initChat()
            val combined = VisionHelper.createChatResponse(
                "user",
                prompt,
                chat,
                config
            )
            val response = withContext(Dispatchers.IO) {
                getReasoningModelApiResponse(combined, apiKey = config.apiKey)
            }
            println(response)
            val parsedResult = clarificationAgent.parseResponse(response.toString())
            println(parsedResult)
            val status = parsedResult["status"] ?: "CLEAR"
            val questionsText = parsedResult["questions"] ?: ""
            println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

            return if (status == "NEEDS_CLARIFICATION" && questionsText.isNotEmpty()) {
                val questions = clarificationAgent.parseQuestions(questionsText)
                Pair(true, questions)
            } else {
                Pair(false, emptyList())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking clarification", e)
            return Pair(false, emptyList())
        }
    }

    private fun startClarificationDialogue(originalInstruction: String, questions: List<String>) {
        val intent = Intent(this, DialogueActivity::class.java).apply {
            putExtra(DialogueActivity.EXTRA_ORIGINAL_INSTRUCTION, originalInstruction)
            putExtra(DialogueActivity.EXTRA_QUESTIONS, ArrayList(questions))
        }
        dialogueLauncher.launch(intent)
    }

    private fun executeTask(instruction: String) {
        lifecycleScope.launch {
            try {
                // Announce the task being performed
                val announcement = "I will now perform the task"
                ttsManager.speakText(announcement)

                // Wait a bit for TTS to complete
                delay(1000)

//                val finalAnswer = deepSearchAgent.execute(instruction)

                if (instruction == "a") {
                    ttsManager.speakText("I am ready to win Hundred Agents Hackathon, and start new era of personal agents")
                    return@launch
                }
                //TODO Removing the tavily deepsearch agent as it is bad at searching, sorry but it is. Plan to replace tavily, so leaving the code

                // if (finalAnswer == "NO-SEARCH") {
                     Log.d("MainActivity", "This is a UI Task. Starting AgentTaskService.")
                     statusText.text = "Agent started to perform task..."

                     // Announce the agent task
                     ttsManager.speakText("Starting agent task for: $instruction")

                     // Determine vision mode based on radio button selection
                     val visionMode = if (xmlModeRadio.isChecked) VisionMode.XML.name else VisionMode.SCREENSHOT.name
                     Log.d("MainActivity", "Selected vision mode: $visionMode")

                     val serviceIntent = Intent(this@MainActivity, AgentTaskService::class.java).apply {
                         putExtra("TASK_INSTRUCTION", instruction)
                         putExtra("VISION_MODE", visionMode)
                     }
                     startService(serviceIntent)
                     val fin = Finger(this@MainActivity)
                     fin.home()
                // } else {
//                    println("Final Answer: $finalAnswer")
//                    Log.d("MainActivity", "Deep Search complete. Answer: $finalAnswer")
//                    statusText.text = finalAnswer
//                    ttsManager.speakText(finalAnswer)
                // }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error executing task", e)
                val errorMessage = "Error executing task: ${e.message}"
                statusText.text = errorMessage
                ttsManager.speakText(errorMessage)
            }
        }
    }

    private fun stopVoiceInput() {
        sttManager.stopListening()
        voiceStatusText.text = getString(R.string.hold_to_speak)
        voiceInputButton.isPressed = false
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w("AppMemory", "onTrimMemory event received with level: $level")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.i("AppMemory", "UI is hidden. Release all UI-related resources (Bitmaps, etc.).")
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                Log.e("AppMemory", "CRITICAL WARNING: App is in the background and is a prime candidate for termination.")
            }
            else -> {
                Log.d("AppMemory", "Unhandled memory trim level: $level")
            }
        }
    }
    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager.shutdown()
        ttsManager.shutdown()
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
        if (EnhancedWakeWordService.isRunning) {
            wakeWordButton.text = "DISABLE WAKE WORD"
        } else {
            wakeWordButton.text = "ENABLE WAKE WORD"
        }
    }

    private fun isPorcupineAccessKeyConfigured(): Boolean {
        // Check if the access key is configured in BuildConfig
        return try {
            val accessKey = com.example.blurr.BuildConfig.PICOVOICE_ACCESS_KEY
            accessKey.isNotEmpty() && accessKey != "YOUR_PICOVOICE_ACCESS_KEY_HERE"
        } catch (e: Exception) {
            false
        }
    }
    private fun askForNotificationPermission() {
        // Request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    Log.i("MainActivity", "Notification permission is already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.w("MainActivity", "Showing rationale and requesting permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.i("MainActivity", "Requesting notification permission for the first time.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Request microphone permission for voice input
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> {
                Log.i("MainActivity", "Microphone permission is already granted.")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Log.w("MainActivity", "Showing rationale and requesting microphone permission.")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                Log.i("MainActivity", "Requesting microphone permission for the first time.")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    private fun checkAndRequestOverlayPermission() {
        // Check if we already have the permission
        if (!Settings.canDrawOverlays(this)) {
            // If not, create an intent to ask the user to grant it
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // Start the activity that will show the permission screen
            startActivity(intent)
        }
    }
}

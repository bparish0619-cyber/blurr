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
import com.example.blurr.api.Eyes
import com.example.blurr.api.Quadruple
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
        tvServiceStatus = findViewById(R.id.tv_service_status)
        inputField = findViewById(R.id.inputField)
        contentModerationInputField = findViewById(R.id.contentMoniterInputField)
        performTaskButton = findViewById(R.id.performTaskButton)
        contentModerationButton = findViewById(R.id.contentMoniterButton)

        setupClickListeners()
        grantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        handler = Handler(Looper.getMainLooper())

//        performTaskButton.setOnClickListener {
//            val instruction = inputField.text.toString()
//            val fin = Finger(this)
//            fin.home()
//            Thread.sleep(1000)
//            if (instruction.isNotBlank()) {
//                Log.d("MainActivity", "Requesting to start ContentModerationService.")
//
//                // Create an Intent to target our new service
//                val serviceIntent = Intent(this, AgentTaskService::class.java).apply {
//                    // Pass the user's instruction to the service
//                    putExtra("TASK_INSTRUCTION", instruction)
//                }
//
//                // Use startService() to start it
//                startService(serviceIntent)
//                updateUI() // Update button states
//                Toast.makeText(this, "Agent Task Started", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show()
//            }
////            handleUserInput(this, userInput, statusText)
//        }
//
//        // --- START THE SERVICE ---
//        contentModerationButton.setOnClickListener {
//            val instruction = contentModerationInputField.text.toString()
//            val fin = Finger(this)
//            fin.home()
//            Thread.sleep(1000)
//            if (instruction.isNotBlank()) {
//                Log.d("MainActivity", "Requesting to start ContentModerationService.")
//
//                // Create an Intent to target our new service
//                val serviceIntent = Intent(this, ContentModerationService::class.java).apply {
//                    // Pass the user's instruction to the service
//                    putExtra("MODERATION_INSTRUCTION", instruction)
//                }
//
//                // Use startService() to start it
//                startService(serviceIntent)
//                updateUI() // Update button states
//                Toast.makeText(this, "Content Moderation Started", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show()
//            }
//        }

//        // --- STOP THE SERVICE ---
//        stopModerationButton.setOnClickListener {
//            Log.d("MainActivity", "Requesting to stop ContentModerationService.")
//
//            val serviceIntent = Intent(this, ContentModerationService::class.java)
//            stopService(serviceIntent)
//            updateUI() // Update button states
//            Toast.makeText(this, "Content Moderation Stopped", Toast.LENGTH_SHORT).show()
//        }
    }

    fun appendToFile(file: File, content: String) {
        file.appendText(content + "\n")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setupClickListeners() {
        performTaskButton.setOnClickListener {
            // Launch a coroutine to handle the task without blocking the UI
            lifecycleScope.launch {
                val instruction = inputField.text.toString()
                if (instruction.isBlank()) {
                    Toast.makeText(this@MainActivity, "Please enter an instruction", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("MainActivity", "Starting AgentTaskService after delay.")
                val serviceIntent = Intent(this@MainActivity, AgentTaskService::class.java).apply {
                    putExtra("TASK_INSTRUCTION", instruction)
                }
                startService(serviceIntent)
//
//                // 1. Go to the home screen
//                val fin = Finger(this@MainActivity)
//                fin.home()
//
//                // 2. Wait for 1.5 seconds (non-blocking) for the UI to settle
//                delay(1500)
//
//                // 3. Now, start the service when the UI is stable
//
//                updateUI()
                Toast.makeText(this@MainActivity, "Agent Task Started", Toast.LENGTH_SHORT).show()
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

//    private fun handleUserInput(context: Context, inputText: String, statusText: TextView) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val taskStartTime = System.currentTimeMillis()
//            val finger = Finger(this)
//            finger.home()
//            Thread.sleep((500))
//
//            val API_KEY = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg"
//
//
//            val shortcutsFile = File(context.filesDir, "shortcuts.json")
//            val tipsFile = File(context.filesDir, "tips.txt")
//
//
//
//            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
//            val logDir = File(context.filesDir, "logs/mobile_agent_E/test/$timestamp").apply { mkdirs() }
//            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }
//
//            val eyes = Eyes(context)
//            val retina = Retina(context, eyes, API_KEY)
//            val infoPool = InfoPool()
//            val persistent = Persistent()
//
//            val manager = Manager()
//            val operator = Operator(finger)
//            val actionReflector = ActionReflector()
//            val reflectorShortCut = ReflectorShortCut()
//            val reflectorTips = ReflectorTips()
//            val noteTaker = Notetaker()
//
//            var iteration = 0
//            val maxItr = 200000
//            val maxConsecutiveFailure = 3
//            val maxRepetitiveActions = 3
//
//            val taskLog = File(logDir, "taskLog.txt")
//
//            infoPool.instruction = inputText
//            infoPool.shortcuts = persistent.loadShortcutsFromFile(shortcutsFile)
//            infoPool.tips = persistent.loadTipsFromFile(tipsFile)
//            infoPool.errToManagerThresh = 2
//
//            if (infoPool.shortcuts.isEmpty()){
//                infoPool.shortcuts = operator.initShortcuts
//            }
//
//            appendToFile(taskLog, "{step: 0, operation: init, instruction: $inputText, maxItr: $maxItr }")
//
//            var screenshotFile: File = eyes.getScreenshotFile()
//            var postScreenshotFile: File
//            var xmlMode = false
//            while (true) {
//                iteration++
//
////              Iteration LIMIT
//                if (maxItr < iteration) {
//                    Log.e("MainActivity", "Max iteration reached: $maxItr")
//                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_iteration, maxItr: $maxItr, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}" )
//                    return@launch
//                }
//
////              Consecutive Failure limit
//                if (infoPool.actionOutcomes.size >= maxConsecutiveFailure) {
//                    val lastKActionOutcomes = infoPool.actionOutcomes.takeLast(maxConsecutiveFailure)
//                    val errFlags = lastKActionOutcomes.map { if (it == "B" || it == "C") 1 else 0 }
//                    if (errFlags.sum() == maxConsecutiveFailure) {
//                        Log.e("MainActivity", "Consecutive failures reach the limit. Stopping...")
//                        appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_consecutive_failures, max_consecutive_failures: $maxConsecutiveFailure, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
//                        return@launch
//                    }
//                }
//
////               max repetition allowed
//                if (infoPool.actionHistory.size >= maxRepetitiveActions){
//                    val lastKActions = infoPool.actionHistory.takeLast(maxRepetitiveActions)
//                    val lastKActionsSet = mutableSetOf<String>()
//                    try {
//                        for (actStr in lastKActions) {
//                            val actObj = JSONObject(actStr) // Assuming actStr is a JSON string
//                            var hashKey = if (actObj.has("name")) actObj.getString("name") else actStr
//                            if (actObj.has("arguments")) {
//                                val arguments = actObj.optJSONObject("arguments")
//                                if (arguments != null) {
//                                    arguments.keys().forEach { arg ->
//                                        hashKey += "-$arg-${arguments.get(arg)}"
//                                    }
//                                } else {
//                                    hashKey += "-None"
//                                }
//                            }
//                            Log.d("MainActivity", "hashable action key: $hashKey")
//                            lastKActionsSet.add(hashKey)
//                        }
//                    } catch (e: Exception) {
//                        // not stopping if there is any error
//                        Log.e("MainActivity", "Error hashing actions: ${e.message}")
//                    }
//                    if (lastKActionsSet.size == 1) {
//                        val repeatedActionKey = lastKActionsSet.first()
//                        if (!repeatedActionKey.contains("Swipe") && !repeatedActionKey.contains("Back")) {
//                            Log.e("MainActivity", "Repetitive actions reaches the limit. Stopping...")
//                            appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_repetitive_actions, max_repetitive_actions: $maxRepetitiveActions, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
//                            return@launch
//                        }
//                    }
//                }
//
//
//                // Step 1: Take Perception
//                if (iteration == 1){
//                    val perceptionTimeStart = System.currentTimeMillis()
//                    val screenshotPath = File(screenshotsDir, "screenshot.jpg")
//                    screenshotFile.copyTo(screenshotPath, overwrite = true)
//                    val lastScreenshotFile = screenshotPath // Store for potential removal
//                    val (perceptionInfos, width, height, keyboardOn) = retina.getPerceptionInfos(
//                        context
//                    )
//                    infoPool.width = width
//                    infoPool.height = height
//                    appendToFile(
//                        taskLog,
//                        "{step: $iteration, operation: perception, screenshot: $screenshotPath, perception_infos: ${
//                            perceptionInfos.forEach { (text, coordinates) ->
//                                println("Text: $text, Coordinates: $coordinates \n")
//                            }
//                        }, duration: ${(System.currentTimeMillis() - perceptionTimeStart) / 1000} seconds}"
//                    )
//                    infoPool.keyboardPre = keyboardOn
//                    if (xmlMode){
//                        eyes.openXMLEyes()
//                        val xml = eyes.getWindowDumpFile()
//                        infoPool.perceptionInfosPreXML = xml.readText()
//                    }
//                    infoPool.perceptionInfosPre = perceptionInfos as MutableList<ClickableInfo>
//
//                }
//
//
//
//                infoPool.errorFlagPlan = false
//                if (infoPool.actionOutcomes.size >= infoPool.errToManagerThresh) {
//                    val latestOutcomes = infoPool.actionOutcomes.takeLast(infoPool.errToManagerThresh)
//                    var count = 0
//                    for (outcome in latestOutcomes) {
//                        if (outcome in listOf("B", "C")) {
//                            count++
//                        }
//                    }
//                    if (count == infoPool.errToManagerThresh) {
//                        infoPool.errorFlagPlan = true
//                    }
//                }
//                infoPool.prevSubgoal = infoPool.currentSubgoal
//
//
//                // Step 2: Manager Planning
//                val managerPlanningStart = System.currentTimeMillis()
//                val promptPlan = manager.getPrompt(infoPool)
//                val chatPlan = manager.initChat()
//                val combinedChatPlan  = addResponse("user",promptPlan, chatPlan, screenshotFile )
//                // Request to Gemini
//                val outputPlan = getReasoningModelApiResponse(combinedChatPlan, apiKey = API_KEY)
//                val parsedManagerPlan = manager.parseResponse(outputPlan)
//
//                // Updating the InfoPool
//                infoPool.plan = parsedManagerPlan["plan"].toString()
//                infoPool.currentSubgoal =  parsedManagerPlan["current_subgoal"].toString()
//
//                appendToFile(taskLog, "{ \n" +
//                        "                    \"step\": $iteration, \n" +
//                        "                    \"operation\": \"planning\", \n" +
//                        "                    \"prompt_planning\": $promptPlan, \n" +
//                        "                    \"error_flag_plan\": ${infoPool.errorFlagPlan}, \n" +
//                        "                    \"raw_response\": $outputPlan, \n" +
//                        "                    \"thought\": ${parsedManagerPlan["thought"]}, \n" +
//                        "                    \"plan\": ${parsedManagerPlan["plan"]}, \n" +
//                        "                    \"current_subgoal\": ${parsedManagerPlan["current_subgoal"]}, \n" +
//                        "                    \"duration\": ${(System.currentTimeMillis() - managerPlanningStart)/1000}, \n" +
//                        "                }")
//                Log.d("MainActivity", "Thought: ${parsedManagerPlan["thought"]}")
//                Log.d("MainActivity", "Overall Plan: ${infoPool.plan}")
//                Log.d("MainActivity", "Current Subgoal: ${infoPool.currentSubgoal}")
//
//
//                // Experience Reflection
//                if (infoPool.actionOutcomes.isNotEmpty()) {
//                    if (infoPool.currentSubgoal.trim().contains("Finished")) {
//                        Log.d("MainActivity", "\n### Experience Reflector ... ###\n")
//                        val experienceReflectionStartTime = System.currentTimeMillis()
//
//                        // Shortcuts
//                        val promptKnowledgeShortcuts = reflectorShortCut.getPrompt(infoPool)
//                        var chatKnowledgeShortcuts = reflectorShortCut.initChat()
//                        var combined =
//                            addResponse("user", promptKnowledgeShortcuts, chatKnowledgeShortcuts)
//                        val outputKnowledgeShortcuts = getReasoningModelApiResponse(combined, apiKey = API_KEY) // Assuming KNOWLEDGE_REFLECTION_MODEL is similar to other models
//                        val parsedResultKnowledgeShortcuts = reflectorShortCut.parseResponse(outputKnowledgeShortcuts)
//                        val newShortcutStr = parsedResultKnowledgeShortcuts["new_shortcut"].toString()
//                        if (newShortcutStr != "None" && newShortcutStr.isNotEmpty()) {
//                            reflectorShortCut.addNewShortcut(newShortcutStr, infoPool)
//                        }
//                        Log.d("MainActivity", "New Shortcut: $newShortcutStr")
//
//                        // Tips
//                        val promptKnowledgeTips = reflectorTips.getPrompt(infoPool)
//                        var chatKnowledgeTips = reflectorTips.initChat()
//                        var combinedTips = addResponse("user", promptKnowledgeTips, chatKnowledgeTips)
//                        val outputKnowledgeTips = getReasoningModelApiResponse(combinedTips, apiKey = API_KEY) // Assuming KNOWLEDGE_REFLECTION_MODEL
//                        val parsedResultKnowledgeTips = reflectorTips.parseResponse(outputKnowledgeTips)
//                        val updatedTips = parsedResultKnowledgeTips["updated_tips"].toString()
//                        infoPool.tips = updatedTips
//                        Log.d("MainActivity", "Updated Tips: $updatedTips")
//
//                        val experienceReflectionEndTime = System.currentTimeMillis()
//                        appendToFile(taskLog, "{step: $iteration, operation: experience_reflection, prompt_knowledge_shortcuts: \"$promptKnowledgeShortcuts\", prompt_knowledge_tips: \"$promptKnowledgeTips\", raw_response_shortcuts: \"$outputKnowledgeShortcuts\", raw_response_tips: \"$outputKnowledgeTips\", new_shortcut: \"$newShortcutStr\", updated_tips: \"$updatedTips\", duration: ${(experienceReflectionEndTime - experienceReflectionStartTime) / 1000} seconds}")
//
//                        // Save updated tips and shortcuts
//                        persistent.saveTipsToFile(tipsFile, infoPool.tips)
//                        persistent.saveShortcutsToFile(shortcutsFile, infoPool.shortcuts)
//                    }
//                }
//
//                // Stopping by planner
//                if (infoPool.currentSubgoal.trim().contains("Finished")) {
//                    infoPool.finishThought = parsedManagerPlan["thought"].toString()
//                    val taskEndTime = System.currentTimeMillis()
//                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: success, final_info_pool: $infoPool, task_duration: ${(taskEndTime - taskStartTime)/1000} seconds}")
//                    Log.i("MainActivity", "Task finished successfully by planner.")
//                    statusText.text = infoPool.importantNotes
//                    return@launch
//                }
//
//                var actionThinkingTimeStart = System.currentTimeMillis()
//                // Step 3 Operator's turn, he will execute on the plan of manager
//                val actionPrompt = operator.getPrompt(infoPool)
//                val actionChat = operator.initChat()
//                val actionCombinedChat  = addResponse("user",actionPrompt, actionChat, screenshotFile )
//                var actionOutput = getReasoningModelApiResponse(actionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
//                var parsedAction = operator.parseResponse(actionOutput)
//                var actionThought = parsedAction["thought"]
//                var actionObjStr = parsedAction["action"]
//                var actionDesc = parsedAction["description"]
//
//                var actionThinkingTimeEnd = System.currentTimeMillis()
//
//                infoPool.lastActionThought = actionThought.toString()
//
//                var actionExecTimeStart = System.currentTimeMillis()
//
//                val (actionObject, numOfAtomicAction, shortcutErrorMessage) = operator.execute(
//                    actionObjStr.toString(), infoPool, context
//                )
//
//                var actionExecTimeEnd = System.currentTimeMillis()
//
//                if (actionObject == null) {
//                    val taskEndTime = System.currentTimeMillis()
//                    appendToFile(
//                        taskLog,
//                        """
//                        {
//                            "step": $iteration,
//                            "operation": "finish",
//                            "finish_flag": "abnormal",
//                            "final_info_pool": $infoPool,
//                            "task_duration": ${(taskEndTime - taskStartTime) / 1000} seconds
//                        }
//                        """.trimIndent()
//                    )
//                    Log.w("MainActivity", "WARNING!!: Abnormal finishing: $actionObjStr")
//                    return@launch
//                }
//
//
//                infoPool.lastAction = actionObjStr.toString()
//                infoPool.lastSummary = actionDesc.toString()
//
//                appendToFile(taskLog, "{\n" +
//                        "    \"step\": $iteration,\n" +
//                        "    \"operation\": \"action\",\n" +
//                        "    \"prompt_action\": \"$actionPrompt\",\n" +
//                        "    \"raw_response\": \"$actionOutput\",\n" +
//                        "    \"action_object\": \"${actionObject.toString()}\",\n" +
//                        "    \"action_object_str\": \"${actionObjStr.toString()}\",\n" +
//                        "    \"action_thought\": \"${actionThought.toString()}\",\n" +
//                        "    \"action_description\": \"${actionDesc.toString()}\",\n" +
//                        "    \"duration\": ${(actionThinkingTimeEnd - actionThinkingTimeStart) / 1000},\n" +
//                        "    \"execution_duration\": ${(actionExecTimeEnd - actionExecTimeStart) / 1000}\n" +
//                        "}")
//                Log.d("MainActivity","Action Thought: ${actionThought.toString()}")
//                Log.d("MainActivity","Action Description: ${actionDesc.toString()}")
//                Log.d("MainActivity","Action: ${actionObjStr.toString()}")
//
//
////               Step 4 : Take the Perception after the action by operator has been performed
//                val perceptionPostStartTime = System.currentTimeMillis()
//                postScreenshotFile = eyes.getScreenshotFile()
//                val postScreenshotPath = File(screenshotsDir, "screenshot_post_${iteration}.jpg")
//                postScreenshotFile.copyTo(postScreenshotPath, overwrite = true)
//                val (postPerceptionInfos, _, _, keyBoardOnPost) = retina.getPerceptionInfos(context)
//                val perceptionPostEndTime = System.currentTimeMillis()
//                appendToFile(taskLog, "{step: $iteration, operation: perception_post, screenshot: $postScreenshotPath, perception_infos: ${postPerceptionInfos.forEach { (text, coordinates) -> println("Text: $text, Coordinates: $coordinates \n") }}, duration: ${(perceptionPostEndTime - perceptionPostStartTime)/1000} seconds}")
//                infoPool.perceptionInfosPost = postPerceptionInfos as MutableList<ClickableInfo>
//                eyes.openXMLEyes()
//                val xmlPost = eyes.getWindowDumpFile()
//                infoPool.perceptionInfosPostXML = xmlPost.readText()
//                infoPool.keyboardPost = keyBoardOnPost
//
//
//
//
//
////                Step5 The Reflector of our actions
//                val reflectionStartTime = System.currentTimeMillis()
//                val reflectionPrompt = actionReflector.getPrompt(infoPool)
//                val reflectionChat = actionReflector.initChat()
//                val reflectionCombinedChat  = addResponsePrePost("user",reflectionPrompt, reflectionChat, screenshotFile, postScreenshotFile )
//
//                // Sending to the GEMINI
//                val reflectionLLMOutput = getReasoningModelApiResponse(reflectionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
//                val parsedReflection = actionReflector.parseResponse(reflectionLLMOutput)
//
//                val outcome = parsedReflection["outcome"].toString()
//                val errorDescription = parsedReflection["error_description"].toString()
//                val progressStatus = parsedReflection["progress_status"].toString()
//
//                infoPool.progressStatusHistory.add(progressStatus)
//                val actionReflectionEndTime = System.currentTimeMillis()
//
//                var actionOutcome: String
//                var currentErrorDescription = errorDescription
//
//                when {
//                    "A" in outcome -> { // Successful. The result of the last action meets the expectation.
//                        actionOutcome = "A"
//                    }
//                    "B" in outcome -> { // Failed. The last action results in a wrong page. I need to return to the previous state.
//                        actionOutcome = "B"
//                        // NOTE: removing the automatic backing; always stopping at the failed state and then there will be a new perception step
//                        // no automatic backing
//                        // check how many backs to take
//                        val name = (actionObject["name"] as String).trim()
//                        val arguments = actionObject["arguments"] as Map<*, *>
//
//                        val actionName = actionObject["name"] // Assuming actionObject is a JSONObject
//                        if (atomicActionSignatures.containsKey(actionName)) {
//                            // back(ADB_PATH) // back one step for atomic actions
//                            // No operation needed as per python code 'pass'
//                        } else if (infoPool.shortcuts.containsKey(actionName)) {
//                            if (shortcutErrorMessage != null) {
//                                currentErrorDescription += "; Error occurred while executing the shortcut: $shortcutErrorMessage"
//                            }
//                        } else {
//                            throw IllegalArgumentException("Invalid action name: $actionName")
//                        }
//                    }
//                    "C" in outcome -> { // Failed. The last action produces no changes.
//                        actionOutcome = "C"
//                    }
//                    else -> {
//                        throw IllegalArgumentException("Invalid outcome: $outcome")
//                    }
//                }
//                infoPool.actionHistory.add(actionObjStr.toString())
//                infoPool.summaryHistory.add(actionDesc.toString())
//                infoPool.actionOutcomes.add(actionOutcome)
//                infoPool.errorDescriptions.add(currentErrorDescription)
//                infoPool.progressStatus = progressStatus
//
//                appendToFile(taskLog, "{\n" +
//                        "    \"step\": $iteration,\n" +
//                        "    \"operation\": \"action_reflection\",\n" +
//                        "    \"prompt_action_reflect\": \"$reflectionPrompt\",\n" +
//                        "    \"raw_response\": \"$reflectionLLMOutput\",\n" +
//                        "    \"outcome\": \"$outcome\",\n" +
//                        "    \"error_description\": \"$errorDescription\",\n" +
//                        "    \"progress_status\": \"$progressStatus\",\n" +
//                        "    \"duration\": ${(actionReflectionEndTime - reflectionStartTime) / 1000}\n" +
//                        "}")
//
//                Log.d("MainActivity","Outcome: $actionOutcome")
//                Log.d("MainActivity","Progress Status: $progressStatus")
//                Log.d("MainActivity","Error Description: $errorDescription")
//
//                // NoteTaker: Record Important Content
//                if (actionOutcome == "A") {
//                    Log.d("MainActivity", "\n### NoteKeeper ... ###\n")
//                    val noteTakingStartTime = System.currentTimeMillis()
//                    val promptNote = noteTaker.getPrompt(infoPool)
//                    var chatNote = noteTaker.initChat()
//                    var combined = addResponse("user", promptNote, chatNote, postScreenshotFile) // Use the post-action screenshot
//                    val outputNote = getReasoningModelApiResponse(combined, apiKey = API_KEY)
//                    val parsedResultNote = noteTaker.parseResponse(outputNote)
//                    val importantNotes = parsedResultNote["important_notes"].toString()
//                    infoPool.importantNotes = importantNotes
//
//
//                    val noteTakingEndTime = System.currentTimeMillis()
//                    appendToFile(taskLog, "{\n" +
//                            "    \"step\": $iteration,\n" +
//                            "    \"operation\": \"notetaking\",\n" +
//                            "    \"prompt_note\": \"$promptNote\",\n" +
//                            "    \"raw_response\": \"$outputNote\",\n" +
//                            "    \"important_notes\": \"$importantNotes\",\n" +
//                            "    \"duration\": ${(noteTakingEndTime - noteTakingStartTime) / 1000}\n" +
//                            "}")
//                    Log.d("MainActivity", "Important Notes: $importantNotes")
//                }
//
//                screenshotFile = postScreenshotFile
//                infoPool.keyboardPre = infoPool.keyboardPost
//                infoPool.perceptionInfosPre = infoPool.perceptionInfosPost
//                infoPool.perceptionInfosPreXML = infoPool.perceptionInfosPostXML
//            }
//        }
//    }


    override fun onDestroy() {
        // Remove callbacks to prevent memory leaks when the activity is destroyed
        handler.removeCallbacks(runnable)

        super.onDestroy()
    }


// ... inside your Service or Activity class

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

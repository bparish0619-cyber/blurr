package com.example.blurr

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import com.example.blurr.agent.ActionReflector
import com.example.blurr.agent.ClickableInfo
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.Manager
import com.example.blurr.agent.Notetaker
import com.example.blurr.agent.Operator
import com.example.blurr.agent.atomicActionSignatures
import com.example.blurr.agent.shortcut.ReflectorShortCut
import com.example.blurr.agent.tips.ReflectorTips
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.example.blurr.api.Retina
import com.example.blurr.utilities.Persistent
import com.example.blurr.utilities.addResponse
import com.example.blurr.utilities.addResponsePrePost
import com.example.blurr.utilities.getReasoningModelApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AgentTaskService : Service() {

    private var agentJob: Job? = null // To keep track of our coroutine

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AgentTaskChannel"
        const val NOTIFICATION_ID = 2 // Use a different ID from your other service
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputText = intent?.getStringExtra("TASK_INSTRUCTION")
        if (inputText == null) {
            Log.e("AgentTaskService", "Service started without user input. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d("AgentTaskService", "Starting agent task with input: $inputText")

        agentJob = CoroutineScope(Dispatchers.IO).launch {
            runAgentLogic(inputText)

            Log.d("AgentTaskService", "Agent task finished. Stopping service.")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    fun appendToFile(file: File, content: String) {
        file.appendText(content + "\n")
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun runAgentLogic(inputText: String) {
            delay(2000)

        val taskStartTime = System.currentTimeMillis()
            val context = this
            val API_KEY = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg"


            val shortcutsFile = File(context.filesDir, "shortcuts.json")
            val tipsFile = File(context.filesDir, "tips.txt")



            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val logDir = File(context.filesDir, "logs/mobile_agent_E/test/$timestamp").apply { mkdirs() }
            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }

            //NOTE LIMITED RESOURCE, YOUR CAREFULLY, for more info read about Hardware Buffer
            val eyes = Eyes(context)

            val retina = Retina(context, eyes, API_KEY)
            val infoPool = InfoPool()
            val persistent = Persistent()
            val finger = Finger(context)

            val manager = Manager()
            val operator = Operator(finger)
            val actionReflector = ActionReflector()
            val reflectorShortCut = ReflectorShortCut()
            val reflectorTips = ReflectorTips()
            val noteTaker = Notetaker()

            var iteration = 0
            val maxItr = 200000
            val maxConsecutiveFailure = 3
            val maxRepetitiveActions = 3

            val taskLog = File(logDir, "taskLog.txt")

            infoPool.instruction = inputText
            infoPool.shortcuts = persistent.loadShortcutsFromFile(shortcutsFile)
            infoPool.tips = persistent.loadTipsFromFile(tipsFile)
            infoPool.errToManagerThresh = 2

            if (infoPool.shortcuts.isEmpty()){
                infoPool.shortcuts = operator.initShortcuts
            }

            appendToFile(taskLog, "{step: 0, operation: init, instruction: $inputText, maxItr: $maxItr }")

        var screenshotFile = eyes.openEyes()

        var postScreenshotFile: Bitmap?
            var xmlMode = true
            while (true) {
                iteration++

//              Iteration LIMIT
                if (maxItr < iteration) {
                    Log.e("MainActivity", "Max iteration reached: $maxItr")
                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_iteration, maxItr: $maxItr, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}" )
                    return
                }

//              Consecutive Failure limit
                if (infoPool.actionOutcomes.size >= maxConsecutiveFailure) {
                    val lastKActionOutcomes = infoPool.actionOutcomes.takeLast(maxConsecutiveFailure)
                    val errFlags = lastKActionOutcomes.map { if (it == "B" || it == "C") 1 else 0 }
                    if (errFlags.sum() == maxConsecutiveFailure) {
                        Log.e("MainActivity", "Consecutive failures reach the limit. Stopping...")
                        appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_consecutive_failures, max_consecutive_failures: $maxConsecutiveFailure, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
                        return
                    }
                }

//               max repetition allowed
                if (infoPool.actionHistory.size >= maxRepetitiveActions){
                    val lastKActions = infoPool.actionHistory.takeLast(maxRepetitiveActions)
                    val lastKActionsSet = mutableSetOf<String>()
                    try {
                        for (actStr in lastKActions) {
                            val sanitizedJson = actStr
                                .replace("```json", "")
                                .replace("```", "")
                                .trim()

                            val actObj = JSONObject(sanitizedJson) // Assuming actStr is a JSON string
                            var hashKey = if (actObj.has("name")) actObj.getString("name") else actStr
                            if (actObj.has("arguments")) {
                                val arguments = actObj.optJSONObject("arguments")
                                if (arguments != null) {
                                    arguments.keys().forEach { arg ->
                                        hashKey += "-$arg-${arguments.get(arg)}"
                                    }
                                } else {
                                    hashKey += "-None"
                                }
                            }
                            Log.d("MainActivity", "hashable action key: $hashKey")
                            lastKActionsSet.add(hashKey)
                        }
                    } catch (e: Exception) {
                        // not stopping if there is any error
                        Log.e("MainActivity", "Error hashing actions: ${e.message}")
                    }
                    if (lastKActionsSet.size == 1) {
                        val repeatedActionKey = lastKActionsSet.first()
                        if (!repeatedActionKey.contains("Swipe") && !repeatedActionKey.contains("Back")) {
                            Log.e("MainActivity", "Repetitive actions reaches the limit. Stopping...")
                            appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: max_repetitive_actions, max_repetitive_actions: $maxRepetitiveActions, final_info_pool: $infoPool, task_duration: ${(System.currentTimeMillis() - taskStartTime)/1000} seconds}")
                            return
                        }
                    }
                }


                // Step 1: Take Perception
                if (iteration == 1 && screenshotFile != null){
                    val perceptionTimeStart = System.currentTimeMillis()
                    val screenshotPath = File(screenshotsDir, "screenshot.jpg")
//                    screenshotFile.copyTo(screenshotPath, overwrite = true)
//                    val lastScreenshotFile = screenshotPath // Store for potential removal
                    val (perceptionInfos, width, height, keyboardOn) = retina.getPerceptionInfos(
                        context, screenshotFile
                    )
                    infoPool.width = width
                    infoPool.height = height
                    appendToFile(
                        taskLog,
                        "{step: $iteration, operation: perception, screenshot: $screenshotPath, perception_infos: ${
                            perceptionInfos.forEach { (text, coordinates) ->
                                println("Text: $text, Coordinates: $coordinates \n")
                            }
                        }, duration: ${(System.currentTimeMillis() - perceptionTimeStart) / 1000} seconds}"
                    )
                    infoPool.keyboardPre = keyboardOn
                    if (xmlMode){
                        val xml = eyes.openXMLEyes()
                        println(xml)
                        infoPool.perceptionInfosPreXML = xml
                    }
                    if(perceptionInfos.isEmpty()) {
//                        println(bitmap)
                        return
                    }
                    infoPool.perceptionInfosPre = perceptionInfos as MutableList<ClickableInfo>

                }



                infoPool.errorFlagPlan = false
                if (infoPool.actionOutcomes.size >= infoPool.errToManagerThresh) {
                    val latestOutcomes = infoPool.actionOutcomes.takeLast(infoPool.errToManagerThresh)
                    var count = 0
                    for (outcome in latestOutcomes) {
                        if (outcome in listOf("B", "C")) {
                            count++
                        }
                    }
                    if (count == infoPool.errToManagerThresh) {
                        infoPool.errorFlagPlan = true
                    }
                }
                infoPool.prevSubgoal = infoPool.currentSubgoal


                // Step 2: Manager Planning
                val managerPlanningStart = System.currentTimeMillis()
                val promptPlan = manager.getPrompt(infoPool)
                val chatPlan = manager.initChat()
                val combinedChatPlan  = addResponse("user",promptPlan, chatPlan, screenshotFile )
                // Request to Gemini
                val outputPlan = getReasoningModelApiResponse(combinedChatPlan, apiKey = API_KEY)
                val parsedManagerPlan = manager.parseResponse(outputPlan.toString())

                // Updating the InfoPool
                infoPool.plan = parsedManagerPlan["plan"].toString()
                infoPool.currentSubgoal =  parsedManagerPlan["current_subgoal"].toString()

                appendToFile(taskLog, "{ \n" +
                        "                    \"step\": $iteration, \n" +
                        "                    \"operation\": \"planning\", \n" +
                        "                    \"prompt_planning\": $promptPlan, \n" +
                        "                    \"error_flag_plan\": ${infoPool.errorFlagPlan}, \n" +
                        "                    \"raw_response\": $outputPlan, \n" +
                        "                    \"thought\": ${parsedManagerPlan["thought"]}, \n" +
                        "                    \"plan\": ${parsedManagerPlan["plan"]}, \n" +
                        "                    \"current_subgoal\": ${parsedManagerPlan["current_subgoal"]}, \n" +
                        "                    \"duration\": ${(System.currentTimeMillis() - managerPlanningStart)/1000}, \n" +
                        "                }")
                Log.d("MainActivity", "Thought: ${parsedManagerPlan["thought"]}")
                Log.d("MainActivity", "Overall Plan: ${infoPool.plan}")
                Log.d("MainActivity", "Current Subgoal: ${infoPool.currentSubgoal}")


                // Experience Reflection
                if (infoPool.actionOutcomes.isNotEmpty()) {
                    if (infoPool.currentSubgoal.trim().contains("Finished")) {
                        Log.d("MainActivity", "\n### Experience Reflector ... ###\n")
                        val experienceReflectionStartTime = System.currentTimeMillis()

                        // Shortcuts
                        val promptKnowledgeShortcuts = reflectorShortCut.getPrompt(infoPool)
                        var chatKnowledgeShortcuts = reflectorShortCut.initChat()
                        var combined =
                            addResponse("user", promptKnowledgeShortcuts, chatKnowledgeShortcuts)
                        val outputKnowledgeShortcuts = getReasoningModelApiResponse(combined, apiKey = API_KEY) // Assuming KNOWLEDGE_REFLECTION_MODEL is similar to other models
                        val parsedResultKnowledgeShortcuts = reflectorShortCut.parseResponse(
                            outputKnowledgeShortcuts.toString()
                        )
                        val newShortcutStr = parsedResultKnowledgeShortcuts["new_shortcut"].toString()
                        if (newShortcutStr != "None" && newShortcutStr.isNotEmpty()) {
                            reflectorShortCut.addNewShortcut(newShortcutStr, infoPool)
                        }
                        Log.d("MainActivity", "New Shortcut: $newShortcutStr")

                        // Tips
                        val promptKnowledgeTips = reflectorTips.getPrompt(infoPool)
                        var chatKnowledgeTips = reflectorTips.initChat()
                        var combinedTips = addResponse("user", promptKnowledgeTips, chatKnowledgeTips)
                        val outputKnowledgeTips = getReasoningModelApiResponse(combinedTips, apiKey = API_KEY) // Assuming KNOWLEDGE_REFLECTION_MODEL
                        val parsedResultKnowledgeTips = reflectorTips.parseResponse(
                            outputKnowledgeTips.toString()
                        )
                        val updatedTips = parsedResultKnowledgeTips["updated_tips"].toString()
                        infoPool.tips = updatedTips
                        Log.d("MainActivity", "Updated Tips: $updatedTips")

                        val experienceReflectionEndTime = System.currentTimeMillis()
                        appendToFile(taskLog, "{step: $iteration, operation: experience_reflection, prompt_knowledge_shortcuts: \"$promptKnowledgeShortcuts\", prompt_knowledge_tips: \"$promptKnowledgeTips\", raw_response_shortcuts: \"$outputKnowledgeShortcuts\", raw_response_tips: \"$outputKnowledgeTips\", new_shortcut: \"$newShortcutStr\", updated_tips: \"$updatedTips\", duration: ${(experienceReflectionEndTime - experienceReflectionStartTime) / 1000} seconds}")

                        // Save updated tips and shortcuts
                        persistent.saveTipsToFile(tipsFile, infoPool.tips)
                        persistent.saveShortcutsToFile(shortcutsFile, infoPool.shortcuts)
                    }
                }

                // Stopping by planner
                if (infoPool.currentSubgoal.trim().contains("Finished")) {
                    infoPool.finishThought = parsedManagerPlan["thought"].toString()
                    val taskEndTime = System.currentTimeMillis()
                    appendToFile(taskLog, "{step: $iteration, operation: finish, finish_flag: success, final_info_pool: $infoPool, task_duration: ${(taskEndTime - taskStartTime)/1000} seconds}")
                    Log.i("MainActivity", "Task finished successfully by planner.")
                    return
                }

                var actionThinkingTimeStart = System.currentTimeMillis()
                // Step 3 Operator's turn, he will execute on the plan of manager
                val actionPrompt = operator.getPrompt(infoPool)
                val actionChat = operator.initChat()
                val actionCombinedChat  = addResponse("user",actionPrompt, actionChat, screenshotFile )
                var actionOutput = getReasoningModelApiResponse(actionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
                var parsedAction = operator.parseResponse(actionOutput.toString())
                var actionThought = parsedAction["thought"]
                var actionObjStr = parsedAction["action"]
                var actionDesc = parsedAction["description"]

                var actionThinkingTimeEnd = System.currentTimeMillis()

                infoPool.lastActionThought = actionThought.toString()

                var actionExecTimeStart = System.currentTimeMillis()

                val (actionObject, numOfAtomicAction, shortcutErrorMessage) = operator.execute(
                    actionObjStr.toString(), infoPool, context
                )

                var actionExecTimeEnd = System.currentTimeMillis()

                if (actionObject == null) {
                    val taskEndTime = System.currentTimeMillis()
                    appendToFile(
                        taskLog,
                        """
                        {
                            "step": $iteration,
                            "operation": "finish",
                            "finish_flag": "abnormal",
                            "final_info_pool": $infoPool,
                            "task_duration": ${(taskEndTime - taskStartTime) / 1000} seconds
                        }
                        """.trimIndent()
                    )
                    Log.w("MainActivity", "WARNING!!: Abnormal finishing: $actionObjStr")
                    return
                }


                infoPool.lastAction = actionObjStr.toString()
                infoPool.lastSummary = actionDesc.toString()

                appendToFile(taskLog, "{\n" +
                        "    \"step\": $iteration,\n" +
                        "    \"operation\": \"action\",\n" +
                        "    \"prompt_action\": \"$actionPrompt\",\n" +
                        "    \"raw_response\": \"$actionOutput\",\n" +
                        "    \"action_object\": \"${actionObject.toString()}\",\n" +
                        "    \"action_object_str\": \"${actionObjStr.toString()}\",\n" +
                        "    \"action_thought\": \"${actionThought.toString()}\",\n" +
                        "    \"action_description\": \"${actionDesc.toString()}\",\n" +
                        "    \"duration\": ${(actionThinkingTimeEnd - actionThinkingTimeStart) / 1000},\n" +
                        "    \"execution_duration\": ${(actionExecTimeEnd - actionExecTimeStart) / 1000}\n" +
                        "}")
                Log.d("MainActivity","Action Thought: ${actionThought.toString()}")
                Log.d("MainActivity","Action Description: ${actionDesc.toString()}")
                Log.d("MainActivity","Action: ${actionObjStr.toString()}")

                val perceptionPostStartTime = System.currentTimeMillis()
                postScreenshotFile = eyes.openEyes()
//               Step 4 : Take the Perception after the action by operator has been performed
                if (postScreenshotFile!= null && screenshotFile != null)
                {
                    val (postPerceptionInfos, _, _, keyBoardOnPost) = retina.getPerceptionInfos(
                        context,
                        screenshotFile
                    )
                    val perceptionPostEndTime = System.currentTimeMillis()
                    appendToFile(
                        taskLog,
                        "{step: $iteration, operation: perception_post, screenshot: NUll, perception_infos: ${
                            postPerceptionInfos.forEach { (text, coordinates) ->
                                println("Text: $text, Coordinates: $coordinates \n")
                            }
                        }, duration: ${(perceptionPostEndTime - perceptionPostStartTime) / 1000} seconds}"
                    )
                    infoPool.perceptionInfosPost = postPerceptionInfos as MutableList<ClickableInfo>
                    eyes.openXMLEyes()
                    val xmlPost = eyes.openXMLEyes()
                    infoPool.perceptionInfosPostXML = xmlPost
                    infoPool.keyboardPost = keyBoardOnPost
                }



//                Step5 The Reflector of our actions
                val reflectionStartTime = System.currentTimeMillis()
                val reflectionPrompt = actionReflector.getPrompt(infoPool)
                val reflectionChat = actionReflector.initChat()
                val reflectionCombinedChat  = addResponsePrePost("user",reflectionPrompt, reflectionChat, screenshotFile, postScreenshotFile )

                // Sending to the GEMINI
                val reflectionLLMOutput = getReasoningModelApiResponse(reflectionCombinedChat, apiKey = API_KEY)
                val parsedReflection = actionReflector.parseResponse(reflectionLLMOutput.toString())

                val outcome = parsedReflection["outcome"].toString()
                val errorDescription = parsedReflection["error_description"].toString()
                val progressStatus = parsedReflection["progress_status"].toString()

                infoPool.progressStatusHistory.add(progressStatus)
                val actionReflectionEndTime = System.currentTimeMillis()

                var actionOutcome: String
                var currentErrorDescription = errorDescription

                when {
                    "A" in outcome -> { // Successful. The result of the last action meets the expectation.
                        actionOutcome = "A"
                    }
                    "B" in outcome -> { // Failed. The last action results in a wrong page. I need to return to the previous state.
                        actionOutcome = "B"
                        // NOTE: removing the automatic backing; always stopping at the failed state and then there will be a new perception step
                        // no automatic backing
                        // check how many backs to take
                        val name = (actionObject["name"] as String).trim()
                        val arguments = actionObject["arguments"] as Map<*, *>

                        val actionName = actionObject["name"] // Assuming actionObject is a JSONObject
                        if (atomicActionSignatures.containsKey(actionName)) {
                            // back(ADB_PATH) // back one step for atomic actions
                            // No operation needed as per python code 'pass'
                        } else if (infoPool.shortcuts.containsKey(actionName)) {
                            if (shortcutErrorMessage != null) {
                                currentErrorDescription += "; Error occurred while executing the shortcut: $shortcutErrorMessage"
                            }
                        } else {
                            throw IllegalArgumentException("Invalid action name: $actionName")
                        }
                    }
                    "C" in outcome -> { // Failed. The last action produces no changes.
                        actionOutcome = "C"
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid outcome: $outcome")
                    }
                }
                infoPool.actionHistory.add(actionObjStr.toString())
                infoPool.summaryHistory.add(actionDesc.toString())
                infoPool.actionOutcomes.add(actionOutcome)
                infoPool.errorDescriptions.add(currentErrorDescription)
                infoPool.progressStatus = progressStatus

                appendToFile(taskLog, "{\n" +
                        "    \"step\": $iteration,\n" +
                        "    \"operation\": \"action_reflection\",\n" +
                        "    \"prompt_action_reflect\": \"$reflectionPrompt\",\n" +
                        "    \"raw_response\": \"$reflectionLLMOutput\",\n" +
                        "    \"outcome\": \"$outcome\",\n" +
                        "    \"error_description\": \"$errorDescription\",\n" +
                        "    \"progress_status\": \"$progressStatus\",\n" +
                        "    \"duration\": ${(actionReflectionEndTime - reflectionStartTime) / 1000}\n" +
                        "}")

                Log.d("MainActivity","Outcome: $actionOutcome")
                Log.d("MainActivity","Progress Status: $progressStatus")
                Log.d("MainActivity","Error Description: $errorDescription")

                // NoteTaker: Record Important Content
                if (actionOutcome == "A") {
                    Log.d("MainActivity", "\n### NoteKeeper ... ###\n")
                    val noteTakingStartTime = System.currentTimeMillis()
                    val promptNote = noteTaker.getPrompt(infoPool)
                    var chatNote = noteTaker.initChat()
                    var combined = addResponse("user", promptNote, chatNote, postScreenshotFile) // Use the post-action screenshot
                    val outputNote = getReasoningModelApiResponse(combined, apiKey = API_KEY)
                    val parsedResultNote = noteTaker.parseResponse(outputNote.toString())
                    val importantNotes = parsedResultNote["important_notes"].toString()
                    infoPool.importantNotes = importantNotes


                    val noteTakingEndTime = System.currentTimeMillis()
                    appendToFile(taskLog, "{\n" +
                            "    \"step\": $iteration,\n" +
                            "    \"operation\": \"notetaking\",\n" +
                            "    \"prompt_note\": \"$promptNote\",\n" +
                            "    \"raw_response\": \"$outputNote\",\n" +
                            "    \"important_notes\": \"$importantNotes\",\n" +
                            "    \"duration\": ${(noteTakingEndTime - noteTakingStartTime) / 1000}\n" +
                            "}")
                    Log.d("MainActivity", "Important Notes: $importantNotes")
                }

                screenshotFile = postScreenshotFile
                infoPool.keyboardPre = infoPool.keyboardPost
                infoPool.perceptionInfosPre = infoPool.perceptionInfosPost
                infoPool.perceptionInfosPreXML = infoPool.perceptionInfosPostXML
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the service is destroyed unexpectedly, cancel the coroutine
        agentJob?.cancel()
        Log.d("AgentTaskService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
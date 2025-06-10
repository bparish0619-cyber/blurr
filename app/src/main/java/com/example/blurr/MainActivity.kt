package com.example.blurr

import android.content.Context
import android.graphics.BitmapFactory
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.blurr.agent.ActionReflector
import com.example.blurr.agent.ClickableInfo
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.Manager
import com.example.blurr.agent.Operator
import com.example.blurr.agent.addResponse
import com.example.blurr.agent.addResponsePrePost
import com.example.blurr.agent.getReasoningModelApiResponse
import com.example.blurr.service.Retina
import com.example.blurr.service.Eyes
import com.example.blurr.service.Finger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var screenshotView: ImageView
    private lateinit var statusText: TextView
    private lateinit var logsText: TextView
    private lateinit var showLogsButton: Button
    private lateinit var inputField: EditText
    private lateinit var performTaskButton: TextView

    private val logs = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenshotView = findViewById(R.id.screenshotView)
        statusText = findViewById(R.id.statusText)
        logsText = findViewById(R.id.logsText)
        showLogsButton = findViewById(R.id.showLogsButton)
        inputField = findViewById(R.id.inputField)
        performTaskButton = findViewById(R.id.performTaskButton)

        showLogsButton.setOnClickListener {
            logsText.text = logs.joinToString("\n")
        }

        performTaskButton.setOnClickListener {
            val userInput = inputField.text.toString()
            handleUserInput(this,userInput)
        }

        Shell.getShell()
        val hasRoot = Shell.isAppGrantedRoot()

        statusText.text = if (hasRoot == true) {
            "✅ Root access granted!"
        } else {
            "❌ No root access!"
        }

    }

    fun appendToFile(file: File, content: String) {
        file.appendText(content + "\n")
    }


    private fun handleUserInput(context: Context, inputText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val finger = Finger()
            finger.home()
            Thread.sleep((500))
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val logDir = File(context.filesDir, "logs/mobile_agent_E/test/$timestamp").apply { mkdirs() }
            val screenshotsDir = File(logDir, "screenshots").apply { mkdirs() }

            val eyes = Eyes(context)
            val retina = Retina(context, eyes, "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg") // Replace with actual key or inject
            val infoPool = InfoPool(inputText) // Assuming constructor exists

            var iteration = 0
            val maxItr = 20

            val managerPromptFile = File(logDir, "manager_prompt_$.txt")
            val managerOutputFile = File(logDir, "manager_output_$.txt")
            val operatorPromptFile = File(logDir, "operator_prompt_$.txt")
            val operatorOutputFile = File(logDir, "operator_output_$.txt")
            val reflectorOutputFile = File(logDir, "reflector_output_$.txt")
            val reflectorPromptFile = File(logDir, "reflector_output_$.txt")

            while (iteration < maxItr) {
                iteration++
                // Step 1: Take Perception
                val screenshotFile = eyes.getScreenshotFile()
                val screenshotPath = File(screenshotsDir, "screenshot.jpg")
                screenshotFile.copyTo(screenshotPath, overwrite = true)
                val (perceptionInfos, width, height) = retina.getPerceptionInfos(context)
                infoPool.width = width
                infoPool.height = height
                infoPool.perceptionInfosPre = perceptionInfos as MutableList<ClickableInfo>

                // Step 2: Manager Planning
                val manager = Manager()
                val promptPlan = manager.getPrompt(infoPool)
                val chatPlan = manager.initChat()
                val combinedChatPlan  = addResponse("user",promptPlan, chatPlan, screenshotFile )

                // Logging the State
                appendToFile(managerPromptFile, promptPlan+ timestamp)
                appendToFile(managerPromptFile, chatPlan[0].second[0].text+ timestamp)

                // Request to Gemini
                val outputPlan = getReasoningModelApiResponse(combinedChatPlan, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
                val parsedManagerPlan = manager.parseResponse(outputPlan)

                // Updating the InfoPool
                infoPool.plan = parsedManagerPlan["plan"].toString()
                infoPool.currentSubgoal =  parsedManagerPlan["current_subgoal"].toString()
                infoPool.lastActionThought = parsedManagerPlan["thought"].toString()
                infoPool.lastSummary = infoPool.lastActionThought

                println(parsedManagerPlan)

                // Logging the State
                appendToFile(managerOutputFile, parsedManagerPlan.toString()+ timestamp)

                // Step 3 Operator's turn, he will execute on the plan of manager
                val operator = Operator(finger)
                val actionPrompt = operator.getPrompt(infoPool)
                val actionChat = operator.initChat()
                val actionCombinedChat  = addResponse("user",actionPrompt, actionChat, screenshotPath )

                // Logging the State
                appendToFile(operatorPromptFile, actionPrompt+ timestamp)
                appendToFile(operatorPromptFile, actionChat[0].second[0].toString()+ timestamp)

                // Request to Gemini
                var actionOutput = getReasoningModelApiResponse(actionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")

                // Updating the infopool
                infoPool.lastAction = actionOutput

                // Updating the InfoPool
                infoPool.actionHistory.add(actionOutput + infoPool.currentSubgoal)

                println("ACTION OUTPUT :::: $actionOutput")

                // Logging the State
                appendToFile(operatorOutputFile, actionOutput + timestamp)

                // Sanitizing the Output
                if (actionOutput.startsWith("```json")){
                    actionOutput = actionOutput
                        .replace(Regex("^```(?:json)?\\s*"), "") // remove opening code block
                        .replace(Regex("\\s*```\\s*$"), "")     // remove closing code block
                        .trim()
                }

                // THE EXECUTION HAPPEN HERE
                if(actionOutput != "") {
                    val result = operator.execute(
                        actionOutput.toString(), infoPool, context
                    )
                }


//                 Step 4 : Take the Perception after the action by operator has been performed
                val postScreenshotFile = eyes.getScreenshotFile()
                postScreenshotFile.copyTo(screenshotPath, overwrite = true)
                val (postPerceptionInfos, _, _) = retina.getPerceptionInfos(context)
                infoPool.perceptionInfosPost = postPerceptionInfos as MutableList<ClickableInfo>


//                Step5 The Reflector of our actions
                val actionReflector = ActionReflector()
                val reflectionPrompt = actionReflector.getPrompt(infoPool)
                val reflectionChat = actionReflector.initChat()
                val reflectionCombinedChat  = addResponsePrePost("user",reflectionPrompt, reflectionChat, screenshotFile, postScreenshotFile )

                // Logging the State
                appendToFile(reflectorPromptFile, reflectionPrompt+ timestamp)
                appendToFile(reflectorPromptFile, reflectionChat[0].second[0].toString()+ timestamp)


                // Sending to the GEMINI
                val reflectionLLMOutput = getReasoningModelApiResponse(reflectionCombinedChat, apiKey = "AIzaSyBlepfkVTJAS6oVquyYlctE299v8PIFbQg")
                val parsedReflection = actionReflector.parseResponse(reflectionLLMOutput)
                println("Parsed LLM Output $parsedReflection")
//                break

                // Logging the State
                appendToFile(reflectorOutputFile, parsedReflection.toString()+ timestamp)

//              Updating the InfoPool
                infoPool.progressStatusHistory.add(parsedReflection["progress_status"].toString())
                infoPool.actionHistory.add(actionOutput)
                infoPool.summaryHistory.add(actionOutput)
                infoPool.actionOutcomes.add(parsedReflection["outcome"].toString())
                infoPool.errorDescriptions.add(parsedReflection["error_description"].toString())
                infoPool.progressStatus = parsedReflection["progress_status"].toString()
//                // Step 7: Delay before next step
                Thread.sleep(150)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
    }
}

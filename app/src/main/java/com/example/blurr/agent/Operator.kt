package com.example.blurr.agent

import android.content.Context
import com.example.blurr.service.Eyes
import com.example.blurr.service.Finger
import com.google.ai.client.generativeai.type.TextPart
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.blurr.agent.Operator.ShortcutStep
import java.io.FileOutputStream
import kotlin.math.min

data class AtomicActionSignature(
    val arguments: List<String>,
    val description: (InfoPool) -> String
)

val atomicActionSignatures = mapOf(
    "Tap" to AtomicActionSignature(
        listOf("x", "y")
    ) { "Tap the position (x, y) in current screen." },

    "Swipe" to AtomicActionSignature(
        listOf("x1", "y1", "x2", "y2")
    ) { info -> "Swipe from position (x1, y1) to position (x2, y2). To swipe up or down to review more content, you can adjust the y-coordinate offset based on the desired scroll distance. For example, setting x1 = x2 = ${(0.5 * info.width)}, y1 = ${(0.5 * info.height)}, and y2 = ${(0.1 * info.height)} will swipe upwards to review additional content below. To swipe left or right in the App switcher screen to choose between open apps, set the x-coordinate offset to at least ${(0.5 * info.width)}." },

    "Type" to AtomicActionSignature(
        listOf("text")
    ) { "Type the \"text\" in an input box. You dont need to type everything on keyboard 1 by 1, this is fast" },

    "Enter" to AtomicActionSignature(emptyList()) { "Press the Enter key after typing." },

    "Switch_App" to AtomicActionSignature(emptyList()) { "Show the App switcher." },

    "Back" to AtomicActionSignature(emptyList()) { "Go back to the previous state." },

    "Home" to AtomicActionSignature(emptyList()) { "Go to the home page." },

    "Wait" to AtomicActionSignature(emptyList()) { "Wait for 10 seconds to give more time for a page loading" },

    "Open_App" to AtomicActionSignature(listOf("app_name")) { "If the current screen is Home or App screen, you can use this action to open the app named \\\"app_name\\\" on the visible on the current screen." }
)
data class Shortcut(
    val name: String,
    val arguments: List<String>,
    val description: String,
    val precondition: String,
    val atomicActionSequence: List<ShortcutStep>
)


class Operator(private val finger: Finger) : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
//        val systemPromptv1 = """
//            You are a helpful AI assistant for operating mobile phones.
//            Your goal is to choose the correct actions to complete the user's instruction.
//            Think as if you are a human user operating the phone.
//        """.trimIndent()

        val systemPrompt = """
            You are a UI automation agent responsible for selecting and executing the next best action on a mobile phone screen.
    
            Your goal is to analyze the current screen context, understand the user’s goal, and choose a valid atomic action or shortcut that brings the user closer to their objective.
            
            ## NOTE ##
            - If you need to tap the search bar or icon try looking for Magnifying Glass icon. Generally it is used for search.
            - Make sure to add a magnifying glass and label it as search. It is generally used for search.
            - Only return the JSON array. Do not include any explanation, markdown, or code formatting.
            - Do not include text outside of the JSON array. Do not use markdown, do not wrap in code blocks, and do not use ellipsis. [Sometime when parsing time, it give json parse error, example 6:23am]

            Guidelines:
            - Think like a real user: imagine tapping, typing, swiping, or navigating based on what is visible on the screen.
            - Instead taping every key when keyboard is open, use the Action Type.
            - Only choose actions that are feasible given the visible UI elements.
            - Use shortcuts when they are applicable to speed up common tasks.
            - Use accurate pixel coordinates for actions such as 'Tap' or 'Swipe'.
            - You may use available shortcuts, but you must ensure the preconditions are satisfied.
            - Don't click on Logos, if you want search, just stick to the search bar/icons.
            - If you see a keyboard on the screen, and your goals are to type, just start typing instead of enabling the input text box (Keyboard means the inputbox enabled)

            Output format:
            Return the action in strict JSON format:
            {
              "name": "ActionName",
              "arguments": {
                "arg1": value1,
                "arg2": value2
              }
            }

            Do not return any explanation or extra formatting (like Markdown code blocks)
            BAD -> ```json{"name": "Tap", "arguments": {"x": 1356, "y": 89}}```
            GOOD -> {"name": "Tap", "arguments": {"x": 1356, "y": 89}}
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Overall Plan ###")
        sb.appendLine(infoPool.plan)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("### Screen Information ###")
        sb.appendLine("The attached image is a screenshot showing the current state of the phone. ")
        sb.appendLine("Its width and height are ${infoPool.width} and ${infoPool.height} pixels, respectively.\n")
        sb.appendLine()


        sb.appendLine("To help you better understand the content in this screenshot, we have extracted positional information for the text elements and icons, including interactive elements such as search bars. ")
        sb.appendLine("The format is: (coordinates; content). The coordinates are [x, y], where x represents the horizontal pixel position (from left to right) ")
        sb.appendLine("and y represents the vertical pixel position (from top to bottom).")

        sb.appendLine("\nThe extracted information is as follows:\n")

        if (infoPool.perceptionInfosPre.isNotEmpty()) {
            infoPool.perceptionInfosPre.forEach { element ->
                sb.appendLine("- Element \"${element.text}\" at position ${element.coordinates}")
            }
            sb.appendLine()
        }
        sb.appendLine("Note that a search bar is often a long, rounded rectangle. If no search bar is presented and you want to perform a search, you may need to tap a search button, which is commonly represented by a magnifying glass.\n")
        sb.appendLine("Also, the information above might not be entirely accurate. ")
        sb.appendLine("You should combine it with the screenshot to gain a better understanding.")

        sb.appendLine("\n")
        sb.appendLine("### Keyboard status ###")
        sb.appendLine(if (infoPool.keyboardPre) "The keyboard has been activated and you can type." else "The keyboard has not been activated and you can\\'t type.")
        sb.appendLine("\n")

        if (infoPool.tips.isNotEmpty()){
            sb.appendLine("### Tips ###")
            sb.appendLine("From previous experience interacting with the device, you have collected the following tips that might be useful for deciding what to do next:\n")
            sb.appendLine(infoPool.tips)
        }

        if (infoPool.importantNotes.isNotBlank()) {
            sb.appendLine("\n### Important Notes ###")
            sb.appendLine("Here are some potentially important content relevant to the user's request you already recorded:\n")
            sb.appendLine(infoPool.importantNotes)
        }

        sb.appendLine("\n---")
        sb.appendLine("Carefully examine all the information provided above and decide on the next action to perform. If you notice an unsolved error in the previous action, think as a human user and attempt to rectify them. You must choose your action from one of the atomic actions or the shortcuts. The shortcuts are predefined sequences of actions that can be used to speed up the process. Each shortcut has a precondition specifying when it is suitable to use. If you plan to use a shortcut, ensure the current phone state satisfies its precondition first.\n\n")

        sb.appendLine("#### Atomic Actions ####")
        sb.appendLine("The atomic action functions are listed in the format of `name(arguments): description` as follows:\n")
        val validActions = if (infoPool.keyboardPre) atomicActionSignatures else atomicActionSignatures.filterKeys { it != "Type" }

        validActions.forEach { (name, sig) ->
            sb.appendLine("- $name(${sig.arguments.joinToString()}): ${sig.description(infoPool)}")
        }

        if (!infoPool.keyboardPre) {
            sb.appendLine("NOTE: Unable to type. The keyboard has not been activated. To type, please activate the keyboard by tapping on an input box or using a shortcut, which includes tapping on an input box first.\n")

        }

        sb.appendLine("\n#### Shortcuts ####")
        sb.appendLine("The shortcut functions are listed in the format of `name(arguments): description | Precondition: precondition` as follows:\n")

        (infoPool.shortcuts).forEach { (name, shortcut) ->
            sb.appendLine("- ${shortcut.name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
        }
        if (infoPool.shortcuts.isEmpty()) {
            sb.appendLine("No shortcuts available.")
        }

        sb.appendLine("### Latest Action History ###\n")
        if (infoPool.actionHistory.isNotEmpty()){
            sb.appendLine("Recent actions you took previously and whether they were successful:\n")
            val numOfActions = min(5, infoPool.actionHistory.size)
//            latest_actions = info_pool.action_history[-num_actions:]
//            latest_summary = info_pool.summary_history[-num_actions:]
//            latest_outcomes = info_pool.action_outcomes[-num_actions:]
//            error_descriptions = info_pool.error_descriptions[-num_actions:]
//            action_log_strs = []
//            for act, summ, outcome, err_des in zip(latest_actions, latest_summary, latest_outcomes, error_descriptions):
//            if outcome == "A":
//            action_log_str = f"Action: {act} | Description: {summ} | Outcome: Successful\n"
//            else:
//            action_log_str = f"Action: {act} | Description: {summ} | Outcome: Failed | Feedback: {err_des}\n"
//            prompt += action_log_str
//            action_log_strs.append(action_log_str)
//            if latest_outcomes[-1] == "C" and "Tap" in action_log_strs[-1] and "Tap" in action_log_strs[-2]:
//            prompt += "\nHINT: If multiple Tap actions failed to make changes to the screen, consider using a \"Swipe\" action to view more content or use another way to achieve the current subgoal."
//
//            prompt += "\n"
            val latestActions = infoPool.actionHistory.takeLast(numOfActions)
            val latestSummaries = infoPool.summaryHistory.takeLast(numOfActions)
            val latestOutcomes = infoPool.actionOutcomes.takeLast(numOfActions)
            val latestErrorDescriptions = infoPool.errorDescriptions.takeLast(numOfActions)
            val actionLogStrs = mutableListOf<String>()
            for (i in latestActions.indices) {
                if (latestOutcomes[i] == "A") {
                    sb.appendLine("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Successful")
                }
                else {
                    sb.appendLine("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Failed | Feedback: ${latestErrorDescriptions[i]}")
                }

            }

        }


//        sb.appendLine("Format: valid JSON {\"name\": \"ActionName\", \"arguments\": {key: value}}")

        return sb.toString()
    }

    override fun parseResponse(response: String):  Map<String, String>  {
        val thought = extractSection(response, "### Thought ###", "### Action ###")
        val action = extractSection(response, "### Action ###", "### Description ###")
        val description = extractSection(response, "### Description ###", null)
        return mapOf(
            "thought" to thought,
            "action" to action,
            "description" to description
        )
    }

    fun executeAtomicAction(name: String, args: Map<*, *>, context: Context) {

        when (name.lowercase()) {
            "tap" -> finger.tap((args["x"] as Number).toInt(), (args["y"] as Number).toInt())
            "swipe" -> finger.swipe(
                (args["x1"] as Number).toInt(),
                (args["y1"] as Number).toInt(),
                (args["x2"] as Number).toInt(),
                (args["y2"] as Number).toInt()
            )
            "type" -> finger.type(args["text"].toString())
            "enter" -> finger.enter()
            "back" -> finger.back()
            "home" -> finger.home()
            "switch_app" -> finger.switchApp()
            "wait" -> Thread.sleep(10_000)
            else -> println("Unsupported action: $name")
        }

        if (name.lowercase() != "wait") Thread.sleep(3000)
    }

    fun logActionOnScreenshot(action: String, args: Map<*, *>, context: Context) {

        val eyes = Eyes(context)
        val screenshotFile = eyes.getScreenshotFile()

        val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
            .copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            textSize = 40f
        }

        when (action.lowercase()) {
            "tap" -> {
                val x = (args["x"] as? Number)?.toInt() ?: return
                val y = (args["y"] as? Number)?.toInt() ?: return
                canvas.drawCircle(x.toFloat(), y.toFloat(), 30f, paint)
                canvas.drawText("TAP", x + 35f, y.toFloat(), paint)
            }
            "swipe" -> {
                val x1 = (args["x1"] as? Number)?.toFloat() ?: return
                val y1 = (args["y1"] as? Number)?.toFloat() ?: return
                val x2 = (args["x2"] as? Number)?.toFloat() ?: return
                val y2 = (args["y2"] as? Number)?.toFloat() ?: return
                paint.strokeWidth = 8f
                canvas.drawLine(x1, y1, x2, y2, paint)
                canvas.drawText("SWIPE", x1 + 10f, y1 - 10f, paint)
            }
            else -> return
        }
        val logDir = File(context.filesDir, "actionLogs")
        logDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val logFile = File(logDir, "action_$timestamp.jpg")
        FileOutputStream(logFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        println("Logging of actionInfo saved in data/data/com.example.blurr/files/actionLogs/action_$timestamp.jpg")

//        filePath.parentFile?.mkdirs()
//        FileOutputStream(filePath).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
//        }
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }


    data class ShortcutStep(
        val name: String,
        val argumentsMap: Map<String, String>
    )

    val initShortcuts = mapOf(
        "Tap_Type_and_Enter" to Shortcut(
            name = "Tap_Type_and_Enter",
            arguments = listOf("x", "y", "text"),
            description = "Tap an input box at (x, y), type the text, then press Enter.",
            precondition = "Text input box is empty.",
            atomicActionSequence = listOf(
                ShortcutStep("Tap", mapOf("x" to "x", "y" to "y")),
                ShortcutStep("Type", mapOf("text" to "text")),
                ShortcutStep("Enter", emptyMap())
            )
        )
    )

    fun execute(
        actionStr: String,
        infoPool: InfoPool,
        context: Context,
        extraArgs: Map<String, Any?> = emptyMap()
    ): Triple<Map<String, Any>?, Int, String?> {
        val actionObj = try {

            val json = org.json.JSONObject(actionStr)

            val name = json.getString("name")

            val arguments = json.optJSONObject("arguments")?.let {
                it.keys().asSequence().associateWith { key -> it.get(key) }
            } ?: emptyMap()
            mapOf("name" to name, "arguments" to arguments)
        } catch (e: Exception) {
            println("Invalid JSON for executing action: $actionStr")
            return Triple(null, 0, null)
        }

        val name = (actionObj["name"] as String).trim()
        val arguments = actionObj["arguments"] as Map<*, *>

        // Execute atomic action
        if (atomicActionSignatures.containsKey(name)) {
            if (name.equals("Open_App", ignoreCase = true)) {
                val appName = arguments["app_name"]?.toString()?.trim() ?: return Triple(null, 0, "Missing app_name")
                val textBlocks = extraArgs["textBlocks"] as? List<String> ?: return Triple(null, 0, "Missing textBlocks")
                val coords = extraArgs["coordinates"] as? List<List<Int>> ?: return Triple(null, 0, "Missing coordinates")

                for (i in textBlocks.indices) {
                    if (textBlocks[i] == appName) {
                        val box = coords[i]
                        val centerX = (box[0] + box[2]) / 2
                        val centerY = (box[1] + box[3]) / 2 - (box[3] - box[1])
                        finger.tap(centerX, centerY)
                        break
                    }
                }
//                if (appName in listOf("Fandango", "Walmart", "Best Buy")) {
//                    Thread.sleep(10000)
//                }
//                Thread.sleep(10000)
            } else {
                executeAtomicAction(name, arguments, context)
                logActionOnScreenshot(name, arguments, context)
            }
            return Triple(actionObj, 1, null)
        }

        // Execute shortcut
// Execute shortcut (search in both initShortcuts and infoPool)
        val shortcut = infoPool.shortcuts[name] ?: initShortcuts[name]
        if (shortcut != null) {
            println("Executing shortcut: $name")
            shortcut.atomicActionSequence.forEachIndexed { i, step ->
                try {
                    val atomicArgs = step.argumentsMap.mapValues { (_, mappedKey) ->
                        arguments[mappedKey] ?: mappedKey
                    }
                    println("\t Executing sub-step $i: ${step.name}, $atomicArgs")
                    executeAtomicAction(step.name, atomicArgs, context)
                } catch (e: Exception) {
                    val errorMsg = "${e.message} in executing step $i: ${step.name} $arguments"
                    println("Error in shortcut: $name: $errorMsg")
                    return Triple(actionObj, i, errorMsg)
                }
            }
            return Triple(actionObj, shortcut.atomicActionSequence.size, null)
        }


        if (name.lowercase() in listOf("null", "none", "finish", "exit", "stop")) {
            println("Agent chose to finish the task. Action: $name")
        } else {
            println("Error! Invalid action name: $name")
        }
        infoPool.finishThought = infoPool.lastActionThought
        return Triple(null, 0, null)
    }

}

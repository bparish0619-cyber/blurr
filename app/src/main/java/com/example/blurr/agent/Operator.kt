package com.example.blurr.agent

import android.content.Context
import com.example.blurr.service.Eyes
import com.example.blurr.service.Finger
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import java.io.File
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.currentComposer
import com.example.blurr.agent.Operator.ShortcutStep
import java.io.FileOutputStream

data class AtomicActionSignature(
    val arguments: List<String>,
    val description: (InfoPool) -> String
)

val atomicActionSignatures = mapOf(
    "Tap" to AtomicActionSignature(
        listOf("x", "y"),
        { "Tap the position (x, y) in current screen." }
    ),
    "Swipe" to AtomicActionSignature(
        listOf("x1", "y1", "x2", "y2"),
        { info -> "Swipe from (x1, y1) to (x2, y2). E.g., swipe up: x1 = x2 = ${(info.width * 0.5).toInt()}, y1 = ${(info.height * 0.5).toInt()}, y2 = ${(info.height * 0.1).toInt()}." }
    ),
    "Type" to AtomicActionSignature(
        listOf("text"),
        { "Type the \"text\" in an input box. You dont need to type everything on keyboard 1 by 1, this is fast" }
    ),
    "Enter" to AtomicActionSignature(emptyList()) { "Press the Enter key after typing." },
    "Switch_App" to AtomicActionSignature(emptyList()) { "Show the App switcher." },
    "Back" to AtomicActionSignature(emptyList()) { "Go back to the previous screen." },
    "Home" to AtomicActionSignature(emptyList()) { "Go to the home screen." },
    "Wait" to AtomicActionSignature(emptyList()) { "Wait for 10 seconds." }
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
        val systemPromptv1 = """
            You are a helpful AI assistant for operating mobile phones. 
            Your goal is to choose the correct actions to complete the user's instruction. 
            Think as if you are a human user operating the phone.
        """.trimIndent()

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
//        NOTE: Uncomment this ASAP
//        sb.appendLine("### Progress Status ###")
//        sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
//        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        println("CURRENT SUBGOAL : ${infoPool.currentSubgoal}")
        sb.appendLine()

        sb.appendLine("### Screen Information ###")
        sb.appendLine("Width: ${infoPool.width}, Height: ${infoPool.height}")
        sb.appendLine()
        if (infoPool.perceptionInfosPre.isNotEmpty()) {
            sb.appendLine("### Visible Screen Elements ###")
            sb.appendLine("The following UI elements are currently visible on the screen:")
            infoPool.perceptionInfosPre.forEach { element ->
                sb.appendLine("- Text: \"${element.text}\" at position ${element.coordinates}")
            }
            sb.appendLine()
        }
        sb.appendLine()
//        sb.appendLine("### Keyboard status ###")
//        sb.appendLine(if (infoPool.keyboardPre) "Keyboard is active." else "Keyboard is not active.")

        if (infoPool.tips.isNotBlank()) {
            sb.appendLine("\n### Tips ###")
            sb.appendLine(infoPool.tips)
        }

        if (infoPool.importantNotes.isNotBlank()) {
            sb.appendLine("\n### Important Notes ###")
            sb.appendLine(infoPool.importantNotes)
        }

        if (infoPool.actionHistory.isNotEmpty()) {
            sb.appendLine("### Action History (Order of execution) ###")
            var cnt = 0
            infoPool.actionHistory.forEach { element ->
                cnt++
                sb.appendLine("- $cnt. $element")
            }
        }


        sb.appendLine("\n---")
        sb.appendLine("Carefully decide the next action. Choose one from atomic actions or available shortcuts.")
        sb.appendLine("Format: valid JSON {\"name\": \"ActionName\", \"arguments\": {key: value}}")


        sb.appendLine("#### Atomic Actions ####")
//        val validActions = if (infoPool.keyboardPre) atomicActionSignatures else atomicActionSignatures.filterKeys { it != "Type" }
        val validActions = atomicActionSignatures
        validActions.forEach { (name, sig) ->
            sb.appendLine("- $name(${sig.arguments.joinToString()}): ${sig.description(infoPool)}")
        }

        sb.appendLine("\n#### Shortcuts (Built-in only) ####")
        sb.appendLine("The following shortcuts are predefined and often useful (especially Tap_Type_and_Enter). Use them when their preconditions are satisfied.")

        (initShortcuts + infoPool.shortcuts).forEach { (name, shortcut) ->
            sb.appendLine("- ${shortcut.name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
            println("Shortcut: ${shortcut.name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
        }
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
        screenshotLogger: ((String) -> Unit)? = null,
        context: Context,
        iter: String = "",
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
            screenshotLogger?.invoke("${iter}__${name.replace(" ", "")}.png")
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
                    screenshotLogger?.invoke("${iter}__${name.replace(" ", "")}__${i}-${step.name.replace(" ", "")}.png")
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

package com.example.blurr.agent

import android.content.Context
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.google.ai.client.generativeai.type.TextPart
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.annotation.RequiresApi
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
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones.
            Your goal is to choose the correct actions to complete the user's instruction.
            Think as if you are a human user operating the phone.
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, xmlMode: Boolean): String {
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

        if (infoPool.perceptionInfosPreXML.isNotEmpty() && xmlMode) {
            sb.appendLine("### Visible Screen Elements in XML format ###")
            sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
            sb.appendLine(infoPool.perceptionInfosPreXML)
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
            sb.appendLine("- ${name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
        }

        if (infoPool.shortcuts.isEmpty()) {
            sb.appendLine("No shortcuts available.")
        }

        sb.appendLine("### Latest Action History ###\n")
        if (infoPool.actionHistory.isNotEmpty()){
            sb.appendLine("Recent actions you took previously and whether they were successful:\n")
            val numOfActions = min(5, infoPool.actionHistory.size)
            val latestActions = infoPool.actionHistory.takeLast(numOfActions)
            val latestSummaries = infoPool.summaryHistory.takeLast(numOfActions)
            val latestOutcomes = infoPool.actionOutcomes.takeLast(numOfActions)
            val latestErrorDescriptions = infoPool.errorDescriptions.takeLast(numOfActions)
            val actionLogStrs = mutableListOf<String>()
            for (i in latestActions.indices) {

                val actionLogString: String = if (latestOutcomes[i] == "A") {
                    ("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Successful")
                } else {
                    ("- Action: ${latestActions[i]} | Description: ${latestSummaries[i]} | Outcome: Failed | Feedback: ${latestErrorDescriptions[i]}")
                }

                sb.appendLine(actionLogString)
                actionLogStrs.add(actionLogString)
            }
            if (latestOutcomes.last() == "C" && "Tap" in actionLogStrs[actionLogStrs.size - 1] && "Tap" in actionLogStrs[actionLogStrs.size - 2]) {
                sb.appendLine(" \nHINT: If multiple Tap actions failed to make changes to the screen, consider using a \\\"Swipe\\\" action to view more content or use another way to achieve the current subgoal.\n")
            }
        }
        else {
            sb.appendLine("\nNo actions have been taken yet.\n")
        }

        sb.appendLine("---\n")
        sb.appendLine("Provide your output in the following format, which contains three parts:\n")

        sb.appendLine("### Thought ###\n")
        sb.appendLine("Provide a detailed explanation of your rationale for the chosen action. IMPORTANT: If you decide to use a shortcut, first verify that its precondition is met in the current phone state. For example, if the shortcut requires the phone to be at the Home screen, check whether the current screenshot shows the Home screen. If not, perform the appropriate atomic actions instead.\n\n")

        sb.appendLine("### Action ###\n")
        sb.appendLine("Choose only one action or shortcut from the options provided. IMPORTANT: Do NOT return invalid actions like null or stop. Do NOT repeat previously failed actions.\n")
        sb.appendLine("Use shortcuts whenever possible to expedite the process, but make sure that the precondition is met.\n")
        sb.appendLine("You must provide your decision using a valid JSON format specifying the name and arguments of the action. For example, if you choose to tap at position (100, 200), you should write {\"name\":\"Tap\", \"arguments\":{\"x\":100, \"y\":100}}. If an action does not require arguments, such as Home, fill in null to the \"arguments\" field. Ensure that the argument keys match the action function's signature exactly.\n\n")

        sb.appendLine("### Description ###\n")
        sb.appendLine("A brief description of the chosen action and the expected outcome.")

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
//
//    fun logActionOnScreenshot(action: String, args: Map<*, *>, context: Context) {
//
//        val eyes = Eyes(context)
//        val screenshotFile = eyes.getScreenshotFile()
//
//        val bitmap = BitmapFactory.decodeFile(screenshotFile?.absolutePath)
//            .copy(Bitmap.Config.ARGB_8888, true)
//
//        val canvas = Canvas(bitmap)
//        val paint = Paint().apply {
//            color = Color.RED
//            style = Paint.Style.FILL
//            textSize = 40f
//        }
//
//        when (action.lowercase()) {
//            "tap" -> {
//                val x = (args["x"] as? Number)?.toInt() ?: return
//                val y = (args["y"] as? Number)?.toInt() ?: return
//                canvas.drawCircle(x.toFloat(), y.toFloat(), 30f, paint)
//                canvas.drawText("TAP", x + 35f, y.toFloat(), paint)
//            }
//            "swipe" -> {
//                val x1 = (args["x1"] as? Number)?.toFloat() ?: return
//                val y1 = (args["y1"] as? Number)?.toFloat() ?: return
//                val x2 = (args["x2"] as? Number)?.toFloat() ?: return
//                val y2 = (args["y2"] as? Number)?.toFloat() ?: return
//                paint.strokeWidth = 8f
//                canvas.drawLine(x1, y1, x2, y2, paint)
//                canvas.drawText("SWIPE", x1 + 10f, y1 - 10f, paint)
//            }
//            else -> return
//        }
//        val logDir = File(context.filesDir, "actionLogs")
//        logDir.mkdirs()
//        val timestamp = System.currentTimeMillis()
//        val logFile = File(logDir, "action_$timestamp.jpg")
//        FileOutputStream(logFile).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
//        }
//        println("Logging of actionInfo saved in data/data/com.example.blurr/files/actionLogs/action_$timestamp.jpg")
//
//    }

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

    val initShortcuts = mutableMapOf(
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

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        actionStr: String,
        infoPool: InfoPool,
        context: Context,
    ): Triple<Map<String, Any>?, Int, String?> {

        val actionObj = try {

            var cleanedActionStr = actionStr.lines().filterNot { it.trim().startsWith("#") }.joinToString("\n")
            cleanedActionStr = cleanedActionStr.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")

            // Extract JSON from potential markdown code blocks
            if (cleanedActionStr.startsWith("```json")){
                cleanedActionStr = cleanedActionStr
                    .replace(Regex("^```(?:json)?\\s*"), "") // remove opening code block
                    .replace(Regex("\\s*```\\s*$"), "")     // remove closing code block
                    .trim()
            }

            val json = org.json.JSONObject(cleanedActionStr)
            val name = json.getString("name")
            val arguments = json.optJSONObject("arguments")?.let {
                it.keys().asSequence().associateWith { key -> it.get(key) }
            } ?: emptyMap()
            mapOf("name" to name, "arguments" to arguments)
        } catch (e: Exception) {
            println("Invalid JSON for executing action: $actionStr")
            return Triple(null, 0, "Invalid JSON format: ${e.message}")
        }

        val name = (actionObj["name"] as String).trim()
        val arguments = actionObj["arguments"] as Map<*, *>
        val shortcut = infoPool.shortcuts[name]

        // Execute atomic action
        if (atomicActionSignatures.containsKey(name)) {
            if (name.equals("Open_App", ignoreCase = true)) {
                val appName = arguments["app_name"]?.toString()?.trim() ?: return Triple(null, 0, "Missing app_name")
                infoPool.perceptionInfosPre.forEach { clickableInfo ->
                    if (clickableInfo.text.lowercase() == appName.lowercase()) {
                        val tapArgs = mapOf(
                            "x" to clickableInfo.coordinates.first,
                            "y" to clickableInfo.coordinates.second
                        )
                        executeAtomicAction("Tap", tapArgs, context)
//                        logActionOnScreenshot("Tap", tapArgs, context)
                        return Triple(actionObj, 1, null)
                }
                    if (appName in listOf("Fandango", "Walmart", "Best Buy")) {
                        Thread.sleep(10000)
                    }
                }
                Thread.sleep(10000)
            } else {
                executeAtomicAction(name, arguments, context)
//                logActionOnScreenshot(name, arguments, context)
            }
            return Triple(actionObj, 1, null)
        }
        // Execute shortcut (search in both initShortcuts and infoPool)
        else if (shortcut != null) {
            println("Executing shortcut: $name")
            try {
                shortcut.atomicActionSequence.forEachIndexed { i, atomicAction ->
                    val atomicActionName = atomicAction.name
                    val atomicActionArgs = mutableMapOf<String, Any?>()

                    if (atomicAction.argumentsMap.isNotEmpty()) {
                        atomicAction.argumentsMap.forEach { (atomicArgKey, value) ->
                            if (arguments.containsKey(value)) { // if the mapped key is in the shortcut arguments
                                atomicActionArgs[atomicArgKey] = arguments[value]
                            } else { // if not: the values are directly passed
                                atomicActionArgs[atomicArgKey] = value
                            }
                        }
                    }

                    println("\t Executing sub-step $i: $atomicActionName $atomicActionArgs ...")
                    executeAtomicAction(atomicActionName, atomicActionArgs, context)

//                    logActionOnScreenshot(atomicActionName, atomicActionArgs, context)
//                    val eyes = Eyes(context)
//                    eyes.openEyes()
                    Thread.sleep(800)
                }
                return Triple(actionObj, shortcut.atomicActionSequence.size, null)
            } catch (e: Exception) {
                println("Error in executing shortcut: $name, ${e.message}")
                return Triple(actionObj, shortcut.atomicActionSequence.indexOfFirst { it.name == e.message?.substringAfterLast(": ")?.substringBefore(" ") }, e.message) // This is a rough way to get the step index
            }
        }
        else {
            if (name.lowercase() in listOf("null", "none", "finish", "exit", "stop")) {
                println("Agent chose to finish the task. Action: $name")
            } else {
                println("Error! Invalid action name: $name")
            }
        }

        infoPool.finishThought = infoPool.lastActionThought

        return Triple(null, 0, null)
    }

}

package com.example.blurr.agent.shortcut

import com.example.blurr.agent.BaseAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.agent.Operator
import com.example.blurr.agent.Shortcut
import com.example.blurr.agent.atomicActionSignatures
import com.example.blurr.utilities.JsonExtraction
import com.google.ai.client.generativeai.type.TextPart
import org.json.JSONObject

class ReflectorShortCut : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>>  {
        val systemPrompt = "You are a helpful AI assistant specializing in mobile phone operations. Your goal is to reflect on past experiences and provide insights to improve future interactions."
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    val shortcutExample = """
{
    "name": "Tap_Type_and_Enter",
    "arguments": ["x", "y", "text"],
    "description": "Tap an input box at position (x, y), Type the \"text\", and then perform the Enter operation (useful for searching or sending messages).",
    "precondition": "There is a text input box on the screen.",
    "atomic_action_sequence":[
        {"name": "Tap", "arguments_map": {"x":"x", "y":"y"}},
        {"name": "Type", "arguments_map": {"text":"text"}},
        {"name": "Enter", "arguments_map": {}}
    ]
}
"""

    override fun getPrompt(infoPool: InfoPool, xmlMode: Boolean): String {
        var prompt = "### Current Task ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "### Overall Plan ###\n"
        prompt += "${infoPool.plan}\n\n"

        prompt += "### Progress Status ###\n"
        prompt += "${infoPool.progressStatus}\n\n"

        prompt += "### Atomic Actions ###\n"
        prompt += "Here are the atomic actions in the format of `name(arguments): description` as follows:\n"
        atomicActionSignatures.forEach { (name, sig) ->
            prompt += ("- $name(${sig.arguments.joinToString()}): ${sig.description(infoPool)}")
        }
        prompt += "\n"

        prompt += "### Existing Shortcuts from Past Experience ###\n"
        if (infoPool.shortcuts.isNotEmpty()) {
            prompt += "Here are some existing shortcuts you have created:\n"
            (infoPool.shortcuts).forEach { (name, shortcut) ->
                prompt += ("- ${name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
            }
        } else {
            prompt += "No shortcuts are provided.\n"
        }
        prompt += "\n"

        prompt += "### Full Action History ###\n"
        if (infoPool.actionHistory.isNotEmpty()) {
            val latestActions = infoPool.actionHistory
            val latestSummary = infoPool.summaryHistory
            val actionOutcomes = infoPool.actionOutcomes
            val errorDescriptions = infoPool.errorDescriptions
            val progressStatusHistory = infoPool.progressStatusHistory
            for (i in latestActions.indices) {
                val act = latestActions[i]
                val summ = latestSummary[i]
                val outcome = actionOutcomes[i]
                val errDes = errorDescriptions[i]
                val progress = progressStatusHistory[i]
                prompt += if (outcome == "A") {
                    "- Action: $act | Description: $summ | Outcome: Successful | Progress: $progress\n"
                } else {
                    "- Action: $act | Description: $summ | Outcome: Failed | Feedback: $errDes\n"
                }
            }
            prompt += "\n"
        } else {
            prompt += "No actions have been taken yet.\n\n"
        }

        if (infoPool.futureTasks.isNotEmpty()) {
            prompt += "---\n"
            prompt += "### Future Tasks ###\n"
            prompt += "Here are some tasks that you might be asked to do in the future:\n"
            for (task in infoPool.futureTasks) {
                prompt += "- $task\n"
            }
            prompt += "\n"
        }

        prompt += "---\n"
        prompt += "Carefully reflect on the interaction history of the current task. Check if there are any subgoals that are accomplished by a sequence of successful actions and can be consolidated into new \"Shortcuts\" to improve efficiency for future tasks? These shortcuts are subroutines consisting of a series of atomic actions that can be executed under specific preconditions. For example, tap, type and enter text in a search bar or creating a new note in Notes."

        prompt += "Provide your output in the following format:\n\n"

        prompt += "### New Shortcut ###\n"
        prompt += "If you decide to create a new shortcut (not already in the existing shortcuts), provide your shortcut object in a valid JSON format which is detailed below. If not, put \"None\" here.\n"
        prompt += "A shortcut object contains the following fields: name, arguments, description, precondition, and atomic_action_sequence. The keys in the arguements need to be unique. The atomic_action_sequence is a list of dictionaries, each containing the name of an atomic action and a mapping of its atomic argument names to the shortcut's argument name. If an atomic action in the atomic_action_sequence does not take any arugments, set the `arguments_map` to an empty dict. \n"
        prompt += "IMPORTANT: The shortcut must ONLY include the Atomic Actions listed above. Create a new shortcut only if you are confident it will be useful in the future. Ensure that duplicated shortcuts with overly similar functionality are not included.\n"
        prompt += "PRO TIP: Avoid creating shortcuts with too many arguments, such as involving multiple taps at different positions. All coordinate arguments required for the shortcut should be visible on the current screen. Imagine that when you start executing the shortcut, you are essentially blind.\n"
        prompt += "Follow the example below to format the shortcut. Avoid adding comments that could cause errors with json.loads().\n $shortcutExample\n\n"

        return prompt
    }

    fun addNewShortcut(shortCutStr: String?, infoPool: InfoPool) {
        if (shortCutStr.isNullOrBlank() || shortCutStr == "None") return

        val json = JsonExtraction()
        val shortCutObject = json.extractJsonObject(shortCutStr)

        if (shortCutObject !is JSONObject) {
            println("Error! Invalid JSON object for adding new shortcut: $shortCutStr")
            return
        }

        val shortCutName = shortCutObject.optString("name", null)
        if (shortCutName.isNullOrBlank()) {
            println("Error! Shortcut name missing.")
            return
        }

        if (infoPool.shortcuts.containsKey(shortCutName)) {
            println("Error! The shortcut already exists: $shortCutName")
            return
        }

        try {
            val arguments = shortCutObject.getJSONArray("arguments").let { arr ->
                List(arr.length()) { i -> arr.getString(i) }
            }

            val description = shortCutObject.getString("description")
            val precondition = shortCutObject.getString("precondition")

            val actionStepsJson = shortCutObject.getJSONArray("atomicActionSequence")
            val atomicActionSequence = List(actionStepsJson.length()) { i ->
                val stepObj = actionStepsJson.getJSONObject(i)
                val name = stepObj.getString("name")

                val argsMapJson = stepObj.getJSONObject("argumentsMap")
                val argumentsMap = argsMapJson.keys().asSequence().associateWith { key ->
                    argsMapJson.getString(key)
                }

                Operator.ShortcutStep(name, argumentsMap)
            }

            val shortcut = Shortcut(
                name = shortCutName,
                arguments = arguments,
                description = description,
                precondition = precondition,
                atomicActionSequence = atomicActionSequence
            )

            infoPool.shortcuts[shortCutName] = shortcut
            println("Updated shortcuts: ${infoPool.shortcuts}")

        } catch (e: Exception) {
            println("Error parsing shortcut JSON: ${e.message}")
        }
    }

    override fun parseResponse(response: String): Map<String, String> {
        val newShortcut = response.substringAfter("### New Shortcut ###").replace("\n", " ").replace("  ", " ").trim()
        return mapOf("new_shortcut" to newShortcut)
    }
}
package com.example.blurr.agent

import com.example.blurr.data.ATOMIC_ACTION_SIGNATURES
import com.example.blurr.data.InfoPool
import com.example.blurr.data.SHORTCUT_EXAMPLE
import com.example.blurr.utils.extractJsonObject
import org.json.JSONObject

class ExperienceReflectorShortCut : BaseAgent() {
    override fun initChat(): MutableList<Pair<String, List<Map<String, String>>>> {
        val operationHistory = mutableListOf<Pair<String, List<Map<String, String>>>>()
        val systemPrompt = "You are a helpful AI assistant specializing in mobile phone operations. Your goal is to reflect on past experiences and provide insights to improve future interactions."
        operationHistory.add(Pair("system", listOf(mapOf("type" to "text", "text" to systemPrompt))))
        return operationHistory
    }

    override fun getPrompt(infoPool: InfoPool): String {
        var prompt = "### Current Task ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "### Overall Plan ###\n"
        prompt += "${infoPool.plan}\n\n"

        prompt += "### Progress Status ###\n"
        prompt += "${infoPool.progressStatus}\n\n"

        prompt += "### Atomic Actions ###\n"
        prompt += "Here are the atomic actions in the format of `name(arguments): description` as follows:\n"
        for ((action, value) in ATOMIC_ACTION_SIGNATURES) {
            prompt += "${action}(${value.arguments.joinToString(", ")}): ${value.description(infoPool)}\n"
        }
        prompt += "\n"

        prompt += "### Existing Shortcuts from Past Experience ###\n"
        if (infoPool.shortcuts.isNotEmpty()) {
            prompt += "Here are some existing shortcuts you have created:\n"
            for ((shortcut, value) in infoPool.shortcuts) {
                prompt += "- ${shortcut}(${value.getStringArrayList("arguments").joinToString(", ")}): ${value.getString("description")} | Precondition: ${value.getString("precondition")}\n"
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
                if (outcome == "A") {
                    prompt += "- Action: $act | Description: $summ | Outcome: Successful | Progress: $progress\n"
                } else {
                    prompt += "- Action: $act | Description: $summ | Outcome: Failed | Feedback: $errDes\n"
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
        prompt += "Follow the example below to format the shortcut. Avoid adding comments that could cause errors with json.loads().\n $SHORTCUT_EXAMPLE\n\n"

        return prompt
    }

    fun addNewShortcut(shortCutStr: String?, infoPool: InfoPool) {
        if (shortCutStr == null || shortCutStr == "None") {
            return
        }
        val shortCutObject = extractJsonObject(shortCutStr)
        if (shortCutObject == null) {
            println("Error! Invalid JSON for adding new shortcut: $shortCutStr")
            return
        }
        val shortCutName = shortCutObject.getString("name")
        if (infoPool.shortcuts.containsKey(shortCutName)) {
            println("Error! The shortcut already exists: $shortCutName")
            return
        }
        infoPool.shortcuts[shortCutName] = shortCutObject
        println("Updated short_cuts: ${infoPool.shortcuts}")
    }

    override fun parseResponse(response: String): Map<String, String> {
        val newShortcut = response.substringAfter("### New Shortcut ###").replace("\n", " ").replace("  ", " ").trim()
        return mapOf("new_shortcut" to newShortcut)
    }
}
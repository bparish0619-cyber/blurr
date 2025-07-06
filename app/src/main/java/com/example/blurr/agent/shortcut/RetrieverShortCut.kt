package com.example.blurr.agent.shortcut

import com.example.blurr.agent.AgentConfig
import com.example.blurr.agent.BaseAgent
import com.example.blurr.agent.InfoPool
import com.example.blurr.utilities.JsonExtraction
import com.google.ai.client.generativeai.type.TextPart

class ExperienceRetrieverShortCut : BaseAgent() {
    override fun initChat(): List<Pair<String, List<TextPart>>>  {
        val systemPrompt = "You are a helpful AI assistant specializing in mobile phone operations. Your goal is to select relevant shortcuts from previous experience to the current task."
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool,config: AgentConfig ): String {
        var prompt = "### Existing Shortcuts from Past Experience ###\n"

        (infoPool.shortcuts).forEach { (name, shortcut) ->
            prompt += ("- ${name}(${shortcut.arguments.joinToString()}): ${shortcut.description} | Precondition: ${shortcut.precondition}")
        }

        prompt += "\n"
        prompt += "### Current Task ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "---\n"
        prompt += "Carefully examine the information provided above to pick the shortcuts that can be helpful to the current task. Remove shortcuts that are irrelevant to the current task.\n"

        prompt += "Provide your output in the following format:\n\n"

        prompt += "### Selected Shortcuts ###\n"
        prompt += "Provide your answer as a list of selected shortcut names: [\"shortcut1\", \"shortcut2\", ...]. If there are no relevant shortcuts, put \"None\" here.\n"
        return prompt
    }

    override fun parseResponse(response: String): Map<String, String> {
        TODO("Not yet implemented")
    }

    fun parseResponse2(response: String): Map<String, List<String>> {
        val selectedShortcutsStr = response.split("### Selected Shortcuts ###").last().replace("\n", " ").replace("  ", " ").trim()
        var selectedShortcutNames = mutableListOf<String>()
        try {
            // Basic JSON array parsing, consider using a robust library for complex cases
            val json = JsonExtraction()
            selectedShortcutNames = json.extractJsonObject(selectedShortcutsStr, "list") as MutableList<String>
//            selected_shortcut_names = [s.strip() for s in selected_shortcut_names]
        } catch (e: Exception) {
            // Log error or handle appropriately
            println("Error parsing shortcuts: $e")
        }
        return mapOf("selected_shortcut_names" to selectedShortcutNames)
    }
}
package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class Manager : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {

        val systemPromptv2 = """
            You are a strategic planner and intelligent assistant designed to operate a mobile phone like a highly capable user.
            
            Your role is to understand the user's goal from their instruction and create a clear, structured plan to achieve it. 
            Think as if you are a human using the phone — tapping, scrolling, typing, or navigating — and apply common sense at every step.
            
            When faced with complex or ambiguous tasks, break them down into manageable subgoals. Always reason step-by-step and anticipate what is visible or clickable on the screen.
            
            You are expected to:
            - Think logically and sequentially.
            - Identify what subgoal should be tackled next.
            - Adjust the plan if progress is blocked or results differ from expectations.
            - If you see a keyboard on the screen, and your goals are to type, just start typing instead of enabling the input text box (Keyboard means the inputbox enabled)
            - Leverage any past shortcuts or user experience if relevant.
            - for search use chrome
        """.trimIndent()

        return listOf("user" to listOf(TextPart(systemPromptv2)))
    }

    override fun getPrompt(infoPool: InfoPool): String {
        val sb = StringBuilder()
        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()
        if (infoPool.perceptionInfosPre.isNotEmpty()) {
            sb.appendLine("### Visible Screen Elements ###")
            sb.appendLine("The following UI elements are currently visible on the screen:")
            infoPool.perceptionInfosPre.forEach { element ->
                sb.appendLine("- Text: \"${element.text}\" at position ${element.coordinates}")
            }
            sb.appendLine()
        }
        if (infoPool.plan.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine("Think step by step and make a high-level plan to achieve the user's instruction.")
            sb.appendLine("Break down complex tasks into subgoals. The screenshot displays the starting state of the phone.\n")

//            if (infoPool.shortcuts.isNotEmpty()) {
//                sb.appendLine("### Available Shortcuts from Past Experience ###")
//                infoPool.shortcuts.forEach { (name, shortcut) ->
//                    sb.appendLine("- $name: ${shortcut.description} | Precondition: ${shortcut.precondition}")
//                }
//                sb.appendLine()
//            }

            sb.appendLine("---")
            sb.appendLine("Provide your output in the following format:\n")
            sb.appendLine("### Thought ###")
            sb.appendLine("Explain your rationale for the plan and subgoals.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("1. first subgoal")
            sb.appendLine("2. second subgoal")
            sb.appendLine("...\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("The first subgoal you should work on.\n")

        } else {
            sb.appendLine("### Current Plan ###")
            sb.appendLine(infoPool.plan)
            sb.appendLine()
            sb.appendLine("### Previous Subgoal ###")
            sb.appendLine(infoPool.currentSubgoal)
//            sb.appendLine()
//            sb.appendLine("### Progress Status ###")
//            sb.appendLine(infoPool.progressStatus.ifEmpty { "No progress yet." })
            sb.appendLine()
            sb.appendLine("The sections above provide an overview of the plan you are following, the current subgoal you are working on, the overall progress made, and any important notes you have recorded. The screenshot displays the current state of the phone.\n")
            sb.appendLine()
            sb.appendLine("Carefully assess the current status to determine if the task has been fully completed. If the user's request involves exploration, ensure you have conducted sufficient investigation. If you are confident that no further actions are required, mark the task as \"Finished\" in your output. If the task is not finished, outline the next steps. If you are stuck with errors, think step by step about whether the overall plan needs to be revised to address the error.\n")
            sb.appendLine()
            sb.appendLine("NOTE: If the current situation prevents proceeding with the original plan or requires clarification from the user, make reasonable assumptions and revise the plan accordingly. Act as though you are the user in such cases.\n\n")
            sb.appendLine()


            if (infoPool.errorFlagPlan) {
                sb.appendLine("### Potentially Stuck! ###")
                val k = infoPool.errToManagerThresh
                val lastActions = infoPool.actionHistory.takeLast(k)
                val lastSummaries = infoPool.summaryHistory.takeLast(k)
                val lastErrors = infoPool.errorDescriptions.takeLast(k)
                for (i in lastActions.indices) {
                    sb.appendLine("- Attempt: Action: ${lastActions[i]} | Description: ${lastSummaries[i]} | Outcome: Failed | Feedback: ${lastErrors[i]}")
                }
            }

            if (infoPool.shortcuts.isNotEmpty()) {
                sb.appendLine("\n### Available Shortcuts from Past Experience ###")
                infoPool.shortcuts.forEach { (name, shortcut) ->
                    sb.appendLine("- $name: ${shortcut.description} | Precondition: ${shortcut.precondition}")
                }
            }

            sb.appendLine("\n---")
            sb.appendLine("### Thought ###")
            sb.appendLine("Explain whether the plan needs to be revised and why.\n")
            sb.appendLine("### Plan ###")
            sb.appendLine("Updated or continued plan.\n")
            sb.appendLine("### Current Subgoal ###")
            sb.appendLine("Next subgoal to execute or write \"Finished\".")
        }


        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val thought = extractSection(response, "### Thought ###", "### Plan ###")
        val plan = extractSection(response, "### Plan ###", "### Current Subgoal ###")
        val currentSubgoal = extractSection(response, "### Current Subgoal ###", null)

        return mapOf(
            "thought" to thought,
            "plan" to plan,
            "current_subgoal" to currentSubgoal,
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""

        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length

        if (to == -1 || from >= text.length || from > to) return ""
        return text.substring(from, to).trim()
    }

}

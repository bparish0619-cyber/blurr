package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart

class ActionReflector : BaseAgent() {

    override fun initChat(): List<Pair<String, List<TextPart>>> {
        val systemPrompt = """
            You are a helpful AI assistant for operating mobile phones. Your goal is to verify whether the last action produced the expected behavior and to keep track of the overall progress.
        """.trimIndent()
        val systemPromptv2 = """
        You are an intelligent agent responsible for verifying whether the last action taken on a mobile device achieved its intended outcome.
        
        Your responsibilities include:
        - Comparing visual and textual changes before and after the action.
        - Judging whether the result matches the user's intention.
        - Updating the progress status or explaining failure cases.
        
        Use common sense and screen context to determine success or failure.
    """.trimIndent()
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        val sb = StringBuilder()

        sb.appendLine("### User Instruction ###")
        sb.appendLine(infoPool.instruction)
        sb.appendLine()

        sb.appendLine("### Progress Status ###")
        sb.appendLine(infoPool.progressStatus)

        if(infoPool.progressStatus.isEmpty()) {
            sb.appendLine("No progress yet.")
        }
        sb.appendLine()

        sb.appendLine("### Current Subgoal ###")
        sb.appendLine(infoPool.currentSubgoal)
        sb.appendLine()

        sb.appendLine("---")

        sb.appendLine("The attached two images are two phone screenshots before and after your last action. ")
        sb.appendLine("The width and height are {info_pool.width} and {info_pool.height} pixels, respectively.\n")

        sb.appendLine("To help you better perceive the content in these screenshots, we have extracted positional information for the text elements and icons. ")
        sb.appendLine("The format is: ( content: coordinates). The coordinates are [x, y], where x represents the horizontal pixel position (from left to right) ")
        sb.appendLine("and y represents the vertical pixel position (from top to bottom).\n")

        sb.appendLine("Note that these information might not be entirely accurate. ")
        sb.appendLine("You should combine them with the screenshots to gain a better understanding.")
        sb.appendLine("\n\n")



        sb.appendLine("### Screen Information Before the Action ###")
        infoPool.perceptionInfosPre.forEach {
            if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                sb.appendLine("${it.text}; ${it.coordinates}")
            }
        }
        sb.appendLine("Keyboard status before the action: ${if (infoPool.keyboardPre) "The keyboard has been activated and you can type." else "The keyboard has not been activated and you can\\'t type."}\n")
        if (infoPool.perceptionInfosPreXML.isNotEmpty() && config.isXmlMode) {
            sb.appendLine("### Visible Screen Elements in XML Form ###")
            sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
            sb.appendLine(infoPool.perceptionInfosPreXML)
            sb.appendLine()
        }
        sb.appendLine("\n\n")

        sb.appendLine("### Screen Information After the Action ###")
        infoPool.perceptionInfosPost.forEach {
            if (it.text.isNotBlank() && it.coordinates != Pair(0, 0)) {
                sb.appendLine("${it.text}; ${it.coordinates}")
            }
        }
        if (infoPool.perceptionInfosPostXML.isNotEmpty() && config.isXmlMode) {
            sb.appendLine("### Visible Screen Elements in XML Form ###")
            sb.appendLine("The following UI elements are currently visible on the screen in XML format:")
            sb.appendLine(infoPool.perceptionInfosPreXML)
            sb.appendLine()
        }
        sb.appendLine("Keyboard status before the action: ${if (infoPool.keyboardPost) "The keyboard has been activated and you can type." else "The keyboard has not been activated and you can\\'t type."}\n")
        sb.appendLine("\n\n")


        sb.appendLine("---")
        sb.appendLine("### Latest Action ###")
        sb.appendLine("Action: ${infoPool.lastAction}")
        sb.appendLine("Expectation: ${infoPool.lastSummary}")
        sb.appendLine()

        sb.appendLine("---")
        sb.appendLine("Carefully examine the information provided above to determine whether the last action produced the expected behavior. If the action was successful, update the progress status accordingly. If the action failed, identify the failure mode and provide reasoning on the potential reason causing this failure. Note that for the 'Swipe' action, it may take multiple attempts to display the expected content. Thus, for a 'Swipe' action, if the screen shows new content, it usually meets the expectation.")
        sb.appendLine("\n")


        sb.appendLine("Provide your output in the following format containing three parts:\n")
        sb.appendLine("### Outcome ###")
        sb.appendLine("Choose from the following options. Give your answer as \\\"A\\\", \\\"B\\\" or \\\"C\\\":\\n\"")
        sb.appendLine("A: Successful or Partially Successful. The result of the last action meets the expectation.\\n")
        sb.appendLine("B: Failed. The last action results in a wrong page. I need to return to the previous state.\\n")
        sb.appendLine("C: Failed. The last action produces no changes.\\n\\n")
        sb.appendLine("### Error Description ###")
        sb.appendLine("If the action failed, provide a detailed description of the error and the potential reason causing this failure. If the action succeeded, put \\\"None\\\" here.\\n\\n")
        sb.appendLine("### Progress Status ###")
        sb.appendLine("If the action was successful or partially successful, update the progress status. If the action failed, copy the previous progress status")

        return sb.toString()
    }

    override fun parseResponse(response: String): Map<String, String> {
        val outcome = extractSection(response, "### Outcome ###", "### Error Description ###")
        val errorDescription = extractSection(response, "### Error Description ###", "### Progress Status ###")
        val progressStatus = extractSection(response, "### Progress Status ###", null)
        return mapOf(
            "outcome" to outcome,
            "error_description" to errorDescription,
            "progress_status" to progressStatus
        )
    }

    private fun extractSection(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        if (startIndex == -1) return ""
        val from = startIndex + start.length
        val to = end?.let { text.indexOf(it, from) } ?: text.length
        return text.substring(from, to).trim()
    }
}

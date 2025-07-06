package com.example.blurr.agent

import com.google.ai.client.generativeai.type.TextPart


class Notetaker: BaseAgent() {

    override fun initChat():  List<Pair<String, List<TextPart>>> {
        val systemPrompt = "You are a helpful AI assistant for operating mobile phones. Your goal is to take notes of important content relevant to the user's request."
        return listOf("user" to listOf(TextPart(systemPrompt)))
    }

    override fun getPrompt(infoPool: InfoPool, config: AgentConfig): String {
        var prompt = "### User Instruction ###\n"
        prompt += "${infoPool.instruction}\n\n"

        prompt += "### Overall Plan ###\n"
        prompt += "${infoPool.plan}\n\n"

        prompt += "### Current Subgoal ###\n"
        prompt += "${infoPool.currentSubgoal}\n\n"

        prompt += "### Progress Status ###\n"
        prompt += "${infoPool.progressStatus}\n\n"

        prompt += "### Existing Important Notes ###\n"
        prompt += if (infoPool.importantNotes.isNotEmpty()) {
            "${infoPool.importantNotes}\n\n"
        } else {
            "No important notes recorded.\n\n"
        }

        prompt += "### Current Screen Information ###\n"
        prompt += "The attached image is a screenshot showing the current state of the phone. "
        prompt += "Its width and height are ${infoPool.width} and ${infoPool.height} pixels, respectively.\n"
        prompt += "To help you better perceive the content in this screenshot, we have extracted positional information for the text elements and icons. "
        prompt += "The format is: (coordinates; content). The coordinates are [x, y], where x represents the horizontal pixel position (from left to right) "
        prompt += "and y represents the vertical pixel position (from top to bottom).\n"

        prompt += "The extracted information is as follows:\n"

        for (clickableInfo in infoPool.perceptionInfosPost) {
            if (clickableInfo.text != "" && clickableInfo.coordinates != Pair(0, 0)) {
                prompt += "${clickableInfo.coordinates}; ${clickableInfo.text}\n"
            }
        }
        prompt += "\n"
        prompt += "Note that this information might not be entirely accurate. "
        prompt += "You should combine it with the screenshot to gain a better understanding.\n\n"

        if (infoPool.perceptionInfosPostXML.isNotEmpty() && config.isXmlMode) {
            prompt+=("### Visible Screen Elements ###")
            prompt+=("The following UI elements are currently visible on the screen in XML format:")
            prompt+=(infoPool.perceptionInfosPostXML)
            prompt +="\n"
        }

        prompt += "---\n"
        prompt += "Carefully examine the information above to identify any important content that needs to be recorded. IMPORTANT: Do not take notes on low-level actions; only keep track of significant textual or visual information relevant to the user's request.\n\n"

        prompt += "Provide your output in the following format:\n"
        prompt += "### Important Notes ###\n"
        prompt += "The updated important notes, combining the old and new ones. If nothing new to record, copy the existing important notes.\n"

        return prompt
    }

    override fun parseResponse(response: String): Map<String, String> {
        val importantNotes = response.split("### Important Notes ###").last().replace("\n", " ").replace("  ", " ").trim()
        return mapOf("important_notes" to importantNotes)
    }
}
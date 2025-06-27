package com.example.blurr.utilities

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

class JsonExtraction {


    fun extractJsonObject(text: String, jsonType: String = "dict"): Any? {
        var cleanedText = text

        try {
            // Remove comments starting with // or #
            cleanedText = cleanedText.replace(Regex("//.*"), "")
            cleanedText = cleanedText.replace(Regex("#.*"), "")

            // Try to parse the entire cleaned text
            return if (jsonType == "dict") {
                JSONObject(cleanedText)
            } else {
                JSONArray(cleanedText)
            }
        } catch (_: JSONException) {
            // Not a valid JSON, proceed to extract from parts
        }

        // Define JSON pattern based on type
        val jsonPattern = if (jsonType == "dict") """\{.*?\}""" else """\[.*?]"""

        // Check for code block with ```json ... ```
        val codeBlockRegex = Regex("""```json\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            val jsonStr = codeBlockMatch.groupValues[1]
            try {
                return if (jsonType == "dict") {
                    JSONObject(jsonStr)
                } else {
                    JSONArray(jsonStr)
                }
            } catch (_: JSONException) {
                // Failed to parse JSON in code block
            }
        }

        // Fallback: try finding JSON objects in the text
        val jsonRegex = Regex(jsonPattern, RegexOption.DOT_MATCHES_ALL)
        val matches = jsonRegex.findAll(text)
        for (match in matches) {
            val candidate = match.value
            try {
                return if (jsonType == "dict") {
                    JSONObject(candidate)
                } else {
                    JSONArray(candidate)
                }
            } catch (_: JSONException) {
                continue
            }
        }

        // If all fails
        return null
    }
}
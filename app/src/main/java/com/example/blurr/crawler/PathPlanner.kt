package com.example.blurr.agent

import android.util.Log
import com.example.blurr.api.ClaudeApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Data classes to parse the incoming App Map JSON
private data class AppMapScreen(val screenId: String, val elements: List<AppMapElement>)
private data class AppMapElement(
    val resource_id: String?,
    val text: String?,
    val content_description: String?,
    val information: String?
)

/**
 * Represents a single step in the execution plan ("Sheet Music").
 * UPDATED: Added element_class_name to resolve ambiguity.
 */
data class ActionStep(
    val action: String,
    val app_name: String? = null,
    val element_text: String? = null,
    val text: String? = null,
    val element_class_name: String? = null // To distinguish between elements with the same text
)

/**
 * The "Conductor" that uses an LLM to create a high-level plan.
 */
class PathPlanner {

    private val gson = Gson()

    companion object {
        private const val TAG = "PathPlanner"
    }

    // The function signature remains the same
    suspend fun createPlan(
        highLevelTask: String,
        appMapJson: String
    ): List<ActionStep>? {
        val prompt = buildPlannerPrompt(highLevelTask, appMapJson)
        Log.d(TAG, prompt)
//        val responseJson = ClaudeApi.generateContent(prompt)
//        Log.d(TAG, responseJson.toString())
        val responseJson = "[\n" +
                "  {\n" +
                "    \"action\": \"open_app\",\n" +
                "    \"app_name\": \"WhatsApp\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"tap\",\n" +
                "    \"element_text\": \"New chat\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"tap\",\n" +
                "    \"element_text\": \"Search\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"type\",\n" +
                "    \"text\": \"Ayush Chaudhary\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"tap\",\n" +
                "    \"element_text\": \"Ayush Chaudhary\",\n" +
                "    \"element_class_name\": \"android.widget.TextView\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"type\",\n" +
                "    \"text\": \"Wishing you a very warm and happy birthday! May your day be filled with joy, laughter, and wonderful moments. Have an amazing year ahead! \uD83C\uDF82\uD83C\uDF89\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"action\": \"tap\",\n" +
                "    \"element_text\": \"Send\"\n" +
                "  }\n" +
                "]\n"
        return if (responseJson != null) {
            try {
                val listType = object : TypeToken<List<ActionStep>>() {}.type
                gson.fromJson<List<ActionStep>>(responseJson, listType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse LLM's plan JSON: $responseJson", e)
                null
            }
        } else {
            Log.e(TAG, "LLM returned a null or empty response for the plan.")
            null
        }
    }

    private fun transformAppMapForPrompt(appMapJson: String): String {
        // This function remains the same.
        return try {
            val listType = object : TypeToken<List<AppMapScreen>>() {}.type
            val screens = gson.fromJson<List<AppMapScreen>>(appMapJson, listType)
            val promptBuilder = StringBuilder()
            screens.forEach { screen ->
                promptBuilder.append("Screen: ${screen.screenId}\n")
                screen.elements.forEach { element ->
                    val elementType = element.resource_id?.substringAfterLast('/') ?: "unknown_type"
                    val containedText = element.content_description?.takeIf { it.isNotBlank() } ?: element.text?.takeIf { it.isNotBlank() }
                    val info = element.information?.takeIf { it.isNotBlank() }
                    if (containedText != null || info != null) {
                        promptBuilder.append("- Type: $elementType")
                        containedText?.let { promptBuilder.append(", Text: \"$it\"") }
                        info?.let { promptBuilder.append(", Info: \"$it\"") }
                        promptBuilder.append("\n")
                    }
                }
                promptBuilder.append("\n")
            }
            promptBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse and transform App Map JSON.", e)
            appMapJson
        }
    }

    // UPDATED: The prompt now instructs the LLM to use `element_class_name`.
    private fun buildPlannerPrompt(task: String, appMapJson: String): String {
        val transformedAppMap = transformAppMapForPrompt(appMapJson)

        return """
            You are an expert AI agent task planner, the "Conductor". Your job is to create a step-by-step execution plan to accomplish a high-level user task on an Android device.

            **Your Goal:**
            Generate a JSON array of actions (the "Sheet Music") to achieve the following task:
            `$task`

            **Your Tools & Knowledge:**
            1.  **App Map (Simplified):** This is your primary source of knowledge about the application's structure.
            ```text
            $transformedAppMap
            ```

            **Available Actions:**
            - `open_app`: Opens an application. Requires `app_name`.
            - `tap`: Clicks on an element. Requires `element_text`.
            - `type`: Types text into an input field. Requires `text`.
            - `back`: Navigates back.
            - `home`: Goes to the home screen.

            **Output Format Rules (CRITICAL):**
            - Your response MUST be a valid JSON array `[]` and nothing else.
            - For `tap` actions, use the `element_text` key.
            - **IMPORTANT FOR ACCURACY:** If you perform a "type" action (like searching), the next "tap" action might be ambiguous (e.g., tapping the search result vs. the search box you just typed in). To avoid this, you can optionally add `element_class_name` to the `tap` action to be more specific. For example, after typing, tap the result which is likely a `android.widget.TextView`, not the `android.widget.EditText`.

            **Example Plan:**
            If the task is "Search for Ayush and tap his profile", your output should be more specific:
            [
              {
                "action": "open_app",
                "app_name": "WhatsApp"
              },
              {
                "action": "tap",
                "element_text": "Search"
              },
              {
                "action": "type",
                "text": "Ayush Chaudhary"
              },
              {
                "action": "tap",
                "element_text": "Ayush Chaudhary",
                "element_class_name": "android.widget.TextView"
              }
            ]

            Now, generate the plan for the given task.
        """.trimIndent()
    }
}

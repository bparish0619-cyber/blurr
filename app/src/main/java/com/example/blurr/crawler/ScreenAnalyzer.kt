package com.example.blurr.crawler

import android.util.Log
import com.example.blurr.api.GeminiApi
import com.google.ai.client.generativeai.type.TextPart
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

/**
 * Defines the purpose of a clickable element, as determined by the LLM.
 * NOTE: The @SerializedName annotation helps Gson correctly map the JSON string to the enum member.
 */
enum class ElementType {
    @SerializedName("STATIC_NAVIGATION")
    STATIC_NAVIGATION,

    @SerializedName("DYNAMIC_CONTENT_LINK")
    DYNAMIC_CONTENT_LINK,

    @SerializedName("ACTION_BUTTON")
    ACTION_BUTTON,

    @SerializedName("IGNORE")
    IGNORE
}

/**
 * The expected top-level JSON response object from the LLM.
 */
data class ScreenAnalysisResult(
    @SerializedName("screenName") val screenName: String,
    @SerializedName("elements") val elements: List<ClassifiedElement>
)

/**
 * Represents a single classified element within the LLM's JSON response.
 */
data class ClassifiedElement(
    @SerializedName("id") val id: Int, // The index of the element in the input list
    @SerializedName("classification") val classification: ElementType
)

/**
 * A simplified data class representing a clickable element sent to the LLM for analysis.
 */
private data class ElementForLlm(
    val id: Int,
    val resource_id: String?,
    val text: String?,
    val content_description: String?,
    val class_name: String?
)


/**
 * Uses a single LLM call to identify a screen and classify its clickable elements.
 * This class replaces the previous `ScreenIdentifier` and `ElementClassifier`.
 */
class ScreenAnalyzer {
    private val gson: Gson = Gson()

    /**
     * Analyzes the current screen's elements to produce a screen name and element classifications.
     *
     * @param allElements The complete list of UIElements parsed from the screen's XML.
     * @param knownScreenTypes A list of screen names already discovered by the crawler.
     * @return A `ScreenAnalysisResult` containing the screen name and a list of classified elements, or null on failure.
     */
    suspend fun analyzeScreen(allElements: List<UIElement>, knownScreenTypes: List<String>): ScreenAnalysisResult? {
        // 1. Filter for clickable elements and create a simplified list for the LLM prompt.
        //    We use the list index as a simple, reliable ID to map the response back.
        val clickableElementsForLlm = allElements.mapIndexedNotNull { index, element ->
            if (element.is_clickable) {
                ElementForLlm(
                    id = index, // Use list index as the unique ID
                    resource_id = element.resource_id,
                    text = element.text,
                    content_description = element.content_description,
                    class_name = element.class_name
                )
            } else {
                null
            }
        }

        // If there are no clickable elements, we can't do much.
        if (clickableElementsForLlm.isEmpty()) {
            // You might want to still identify the screen, but for now, we'll bail.
            // A potential improvement is to have a separate prompt for non-interactive screens.
            return null
        }

        // 2. Create the unified prompt for the LLM.
        val prompt = createAnalysisPrompt(clickableElementsForLlm, knownScreenTypes)
        val chat = listOf("user" to listOf(TextPart(prompt)))

        // 3. Call the LLM API.
        val responseJson = GeminiApi.generateContent(chat = chat, modelName = "gemini-1.5-flash")

        // 4. Parse the JSON response into our data classes.
        return try {
            // Clean the response to ensure it's just the JSON object.
            val cleanedJson = responseJson?.substringAfter("```json")?.substringBefore("```")?.trim()
            gson.fromJson(cleanedJson, ScreenAnalysisResult::class.java)
        } catch (e: Exception) {
            Log.e("ScreenAnalyzer", "Failed to parse LLM JSON response: $responseJson", e)
            null
        }
    }

    private fun createAnalysisPrompt(elements: List<ElementForLlm>, knownTypes: List<String>): String {
        val elementsJson = GsonBuilder().setPrettyPrinting().create().toJson(elements)
        val knownTypesJson = gson.toJson(knownTypes)

        return """
            You are an expert Android UI analyst. Your task is to perform two actions in a single step:
            1.  **Identify Screen**: Analyze the overall screen layout to determine its purpose. Either match it to a known screen type or create a new, unique `PascalCase` name for it.
            2.  **Classify Elements**: Analyze each provided clickable UI element and classify its function.

            **Output Format:**
            Your response MUST be a single, valid JSON object wrapped in ```json ... ```. Do not include any other text or explanations.
            The JSON object must conform to this structure:
            {
              "screenName": "string",
              "elements": [
                {
                  "id": integer,
                  "classification": "string"
                }
              ]
            }

            **Rules for `screenName`:**
            - First, check if the screen's purpose matches any in the `KNOWN_SCREEN_TYPES` list. If it's a match (e.g., a chat list screen, even with different people in it), use the existing name from the list.
            - If it's a new type of screen, create a new, descriptive name in `PascalCase`. The name must describe the screen's purpose, ignoring dynamic content (e.g., use `UserProfileScreen` not `JohnDoeProfileScreen`).

            **Rules for `elements` classification:**
            - For each element in the `CLICKABLE_ELEMENTS_TO_ANALYZE` input, create a corresponding object in the output `elements` array.
            - The `id` in your output MUST match the `id` of the element from the input.
            - The `classification` MUST be one of these exact four strings:
                - "STATIC_NAVIGATION": For elements that lead to a major, static part of the app (e.g., Settings, Profile, Home tab).
                - "DYNAMIC_CONTENT_LINK": For links to a specific item in a list (e.g., a single chat, a news article, a contact).
                - "ACTION_BUTTON": For buttons that perform an action on the current page (e.g., "Send", "Like", "Delete", "Reply").
                - "IGNORE": For any other unimportant, decorative, or non-functional clickable element.

            ---
            **INPUT DATA**

            **1. KNOWN_SCREEN_TYPES:**
            ```json
            $knownTypesJson
            ```

            **2. CLICKABLE_ELEMENTS_TO_ANALYZE:**
            ```json
            $elementsJson
            ```

            ---
            **YOUR JSON RESPONSE:**
        """.trimIndent()
    }
}
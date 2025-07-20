package com.example.blurr.crawler

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import com.google.gson.GsonBuilder
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileWriter


// A task in our crawler's to-do list.
data class CrawlTask(
    val sourceScreenId: String,
    val elementToClick: UIElement
)

/**
 * A hybrid intelligence crawler that maps an application's navigation graph.
 * It uses a SemanticParser for perception and a unified ScreenAnalyzer for LLM-based analysis.
 */
class Cartographer(
    private val context: Context,
    private val semanticParser: SemanticParser,
    private val screenAnalyzer: ScreenAnalyzer,
    private val finger: Finger,
    private val eyes: Eyes
) {
    private val crawlQueue = ArrayDeque<CrawlTask>()
    private val masterAppMap = mutableMapOf<String, Screen>()
    private val knownScreenTypes = mutableListOf<String>()

    // --- Companion Object for constants ---
    companion object {
        private const val TAG = "Cartographer" // Logging Tag
        private const val APP_MAP_FILENAME = "app_map_progress_v3.json"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun crawl(maxDepth: Int = 20): String {
        Log.i(TAG, "Starting crawl with max depth: $maxDepth")
        // Process the initial screen to populate the queue.
        processScreen(isInitialScreen = true)

        var clicksPerformed = 0
        while (crawlQueue.isNotEmpty() && clicksPerformed < maxDepth) {
            val task = crawlQueue.removeFirst()
            val screenIdBeforeClick = task.sourceScreenId
            val activityBeforeClick = eyes.getCurrentActivityName()

            Log.i(TAG, "--- [TASK START | Clicks: $clicksPerformed | Queue: ${crawlQueue.size}] ---")
            Log.d(TAG, "Executing task: Click '${task.elementToClick.text ?: task.elementToClick.resource_id}' from screen '$screenIdBeforeClick'")

            // --- Action: Tap ---
            task.elementToClick.bounds?.let { boundsString ->
                val coords = boundsString.removeSurrounding("[", "]").split("][")
                val startCoords = coords[0].split(",").map { it.toInt() }
                val endCoords = coords[1].split(",").map { it.toInt() }
                val tapX = (startCoords[0] + endCoords[0]) / 2
                val tapY = (startCoords[1] + endCoords[1]) / 2
                Log.d(TAG, "Action: Tapping at coordinates ($tapX, $tapY)")
                finger.tap(tapX, tapY)
            } ?: Log.e(TAG, "Element bounds are null, cannot tap. Skipping task.")
            clicksPerformed++
            Log.d(TAG, "Waiting for 2 seconds for screen to settle...")
            delay(2000)

            // --- Perception: Analyze Destination Screen ---
            Log.d(TAG, "Perception: Analyzing the destination screen...")
            val destinationScreenId = processScreen()

            val activityAfterClick = eyes.getCurrentActivityName()
            Log.d(TAG, "Landed on Activity: $activityAfterClick")


            // --- Graph Update ---
            if (destinationScreenId != null) {
                // *** IMPROVED: Find the original element in the map and update it directly. ***
                masterAppMap[screenIdBeforeClick]?.let { screen ->
                    // Find the element in our map that matches the one from the task.
                    // A direct equality check works because UIElement is a data class.
                    val originalElement = screen.elements.find { it == task.elementToClick }
                    originalElement?.let {
                        it.leadsToScreen = destinationScreenId
                        val elementId = it.resource_id ?: it.text ?: "unknown_element"
                        Log.i(TAG, "Graph Edge Added: Element '${elementId}' on screen '$screenIdBeforeClick' now leads to '$destinationScreenId'")
                    } ?: Log.w(TAG, "Could not find original element in map to update edge. This should not happen.")
                }
            } else {
                Log.w(TAG, "Could not identify destination screen. No graph edge will be added.")
            }

            // --- Action: Go Back (Only if the ACTIVITY has changed) ---
            if (activityAfterClick != null && activityAfterClick != activityBeforeClick) {
                Log.d(TAG, "Activity changed from '$activityBeforeClick' to '$activityAfterClick'. Navigating back.")
                finger.back()
                Log.d(TAG, "Waiting for 2 seconds for return navigation...")
                delay(2000)
            } else {
                Log.w(TAG, "Activity did not change after click. Skipping back press to avoid exiting app.")
            }
            // --- Persistence: Save progress after every task ---
            saveProgressToFile()

            Log.i(TAG, "--- [TASK END] ---")
        }

        Log.i(TAG, "Crawl finished. Reason: ${if (crawlQueue.isEmpty()) "Queue empty" else "Max depth reached"}. Total clicks: $clicksPerformed. Discovered screens: ${masterAppMap.size}")
        return getAppMapJson()
    }

    private fun getAppMapJson(): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(masterAppMap)
    }

    private fun saveProgressToFile() {
        try {
            val file = File(context.filesDir, APP_MAP_FILENAME)
            FileWriter(file).use { writer ->
                writer.write(getAppMapJson())
            }
            Log.i(TAG, "Progress saved successfully to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save progress to file.", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processScreen(isInitialScreen: Boolean = false): String? {
        Log.d(TAG, "Processing screen. Initial screen: $isInitialScreen")
        val xml = eyes.openPureXMLEyes()
        if (xml.isBlank()) {
            Log.e(TAG, "Received blank XML from accessibility service. Cannot process screen.")
            return null
        }
        val (width, height) = getScreenDimensionsFromContext(context)
        val allElements = semanticParser.parse(xml, width, height)
        Log.d(TAG, "Parsed ${allElements.size} elements from the screen.")

        val analysisResult = screenAnalyzer.analyzeScreen(allElements, knownScreenTypes)

        if (analysisResult == null) {
            Log.e(TAG, "LLM screen analysis failed. Cannot identify screen.")
            return null
        }

        val screenId = analysisResult.screenName
        Log.i(TAG, "Screen identified as: '$screenId'")

        if (!masterAppMap.containsKey(screenId)) {
            Log.i(TAG, "New screen type discovered: '$screenId'. Adding to map and queueing tasks.")
            if (!knownScreenTypes.contains(screenId)) {
                knownScreenTypes.add(screenId)
            }
            masterAppMap[screenId] = Screen(screenId, allElements)

            val exploredDynamicTypes = mutableSetOf<String>()
            for (classifiedElement in analysisResult.elements) {
                if (classifiedElement.id >= allElements.size) {
                    Log.e(TAG, "LLM returned an invalid element ID: ${classifiedElement.id}. Skipping.")
                    continue
                }
                val originalElement = allElements[classifiedElement.id]
                val elementDesc = "'${originalElement.text ?: originalElement.resource_id}'"

                when (classifiedElement.classification) {
                    ElementType.STATIC_NAVIGATION -> {
                        crawlQueue.addLast(CrawlTask(screenId, originalElement))
                        Log.d(TAG, "Queueing STATIC_NAVIGATION: $elementDesc. Queue size: ${crawlQueue.size}")
                    }
                    ElementType.DYNAMIC_CONTENT_LINK -> {
                        val templateId = originalElement.resource_id ?: "dynamic_link"
                        if (!exploredDynamicTypes.contains(templateId)) {
                            crawlQueue.addLast(CrawlTask(screenId, originalElement))
                            exploredDynamicTypes.add(templateId)
                            Log.d(TAG, "Queueing one instance of DYNAMIC_CONTENT_LINK: $elementDesc. Queue size: ${crawlQueue.size}")
                        } else {
                            Log.d(TAG, "Skipping duplicate DYNAMIC_CONTENT_LINK type: $elementDesc")
                        }
                    }
                    else -> {
                        Log.d(TAG, "Ignoring element of type ${classifiedElement.classification}: $elementDesc")
                    }
                }
            }
        } else {
            Log.d(TAG, "Screen '$screenId' already visited. No new tasks added.")
        }
        return screenId
    }
}

// Helper function remains the same
fun getScreenDimensionsFromContext(context: Context): Pair<Int, Int> {
    val displayMetrics = context.resources.displayMetrics
    return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
}

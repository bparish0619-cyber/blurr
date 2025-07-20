package com.example.blurr.crawler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.blurr.agent.PathPlanner
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import kotlinx.coroutines.*
import java.io.File
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * A foreground service that orchestrates the execution of a high-level task.
 * It loads a pre-crawled App Map, uses a PathPlanner to generate a plan,
 * and then executes the plan step-by-step.
 */
class AgentService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Agent Components
    private lateinit var pathPlanner: PathPlanner
    private lateinit var finger: Finger
    private lateinit var eyes: Eyes

    companion object {
        private const val TAG = "AgentService"
        const val ACTION_START_TASK = "com.example.blurr.START_TASK"
        const val EXTRA_TASK_DESCRIPTION = "task_description"
        const val EXTRA_APP_MAP_FILE = "app_map_file"

        const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannel"
        const val NOTIFICATION_ID = 3 // Use a different ID from CrawlerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AgentService creating...")
        // Initialize all necessary components
        createNotificationChannel()

        pathPlanner = PathPlanner()
        finger = Finger(this)
        eyes = Eyes(this)
        Log.i(TAG, "AgentService components initialized.")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Agent is initializing..."))

        if (intent?.action == ACTION_START_TASK) {
            val taskDescription = intent.getStringExtra(EXTRA_TASK_DESCRIPTION)
            val appMapFile = intent.getStringExtra(EXTRA_APP_MAP_FILE)

            if (taskDescription.isNullOrBlank() || appMapFile.isNullOrBlank()) {
                Log.e(TAG, "Task description or App Map file is missing. Stopping service.")
                stopSelf()
                return START_NOT_STICKY
            }

//            startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))
            Log.i(TAG, "Received task: '$taskDescription'")

            // Launch the main execution logic in a coroutine
            serviceScope.launch {
                Log.d(TAG, taskDescription)
                Log.d(TAG, appMapFile)

                runTask(taskDescription, appMapFile)
            }
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun runTask(task: String, appMapFileName: String) {
        try {
            // 1. Load the App Map from the specified file
            updateNotification("Loading App Map...")
            val appMapJson = loadAppMapFromFile(appMapFileName)
            if (appMapJson.isNullOrBlank()) {
                throw IllegalStateException("Failed to load or find App Map file: $appMapFileName")
            }

            // 2. Get the current state
            updateNotification("Analyzing current screen...")


            // 3. Call the "Conductor" to get a plan
            updateNotification("Creating a plan...")
            val plan = pathPlanner.createPlan(task, appMapJson)

            if (plan.isNullOrEmpty()) {
                throw IllegalStateException("PathPlanner failed to generate a plan.")
            }

            Log.i(TAG, "Plan generated with ${plan.size} steps. Starting execution.")

            // 4. Execute the plan step-by-step
            for ((index, step) in plan.withIndex()) {
                val stepNum = index + 1
                updateNotification("Executing step $stepNum/${plan.size}: ${step.action}")
                Log.i(TAG, "--- [Executing Step $stepNum/${plan.size}] ---")
                Log.d(TAG, "Action: ${step.action}, Details: ${step.element_text ?: step.text}")

                // Give the UI a moment to settle before analyzing
                delay(500)
                val currentXml = eyes.openPureXMLEyes()

                when (step.action.lowercase()) {
                    "open_app" -> { // NEW ACTION HANDLER
                        val appName = step.app_name
                        if (appName.isNullOrBlank()) {
                            Log.e(TAG, "Open_App action is missing 'app_name'. Skipping.")
                            continue
                        }
                        val packageName = findPackageNameFromAppName(appName)
                        if (packageName != null) {
                            Log.d(TAG, "Opening app: $appName (Package: $packageName)")
                            val success = finger.openApp(packageName)
                            if (!success) {
                                throw IllegalStateException("Failed to open app: $appName")
                            }
                            delay(2000) // Extra delay for app to load
                        } else {
                            throw IllegalStateException("Could not find package for app: $appName")
                        }
                    }
                    "tap" -> {
                        val elementText = step.element_text
                        if (elementText.isNullOrBlank()) {
                            Log.e(TAG, "Tap action is missing 'element_text'. Skipping.")
                            continue
                        }

                        val elementToTap = ElementFinder.findElement(currentXml, elementText)
                        if (elementToTap?.bounds != null) {
                            val bounds = parseBounds(elementToTap.bounds)
                            if (bounds != null) {
                                val tapX = bounds.centerX()
                                val tapY = bounds.centerY()
                                Log.d(TAG, "Tapping on '$elementText' at ($tapX, $tapY)")
                                finger.tap(tapX, tapY)
                            } else {
                                throw IllegalStateException("Could not parse bounds for element '$elementText'")
                            }
                        } else {
                            throw IllegalStateException("Could not find element '$elementText' on the current screen.")
                        }
                    }
                    "type" -> {
                        val textToType = step.text
                        if (!textToType.isNullOrBlank()) {
                            Log.d(TAG, "Typing text: '$textToType'")
                            finger.type(textToType)
                        } else {
                            Log.e(TAG, "Type action is missing 'text'. Skipping.")
                        }
                    }
                    "back" -> {
                        Log.d(TAG, "Performing back action.")
                        finger.back()
                    }
                    "home" -> {
                        Log.d(TAG, "Performing home action.")
                        finger.home()
                    }
                    // Add other atomic actions here as needed
                    else -> Log.w(TAG, "Unknown action in plan: ${step.action}")
                }
                // Wait for the action to complete and UI to update
            }

            Log.i(TAG, "Plan execution finished successfully.")
            updateNotification("Task completed successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed.", e)
            updateNotification("Task failed: ${e.message}")
        } finally {
            // Wait a moment before stopping, so the final notification is visible
            delay(5000)
            stopSelf()
        }
    }

    private fun loadAppMapFromFile(fileName: String): String? {
        return try {
            val file = File(filesDir, fileName)
            if (file.exists()) {
                Log.d(TAG, "Reading App Map from ${file.absolutePath}")
                file.readText()
            } else {
                Log.e(TAG, "App Map file not found at ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading App Map file", e)
            null
        }
    }
    // NEW HELPER FUNCTION
    private fun findPackageNameFromAppName(appName: String): String? {
        val pm = packageManager
        // Get the list of all installed applications
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in packages) {
            // Compare the user-facing label of the app
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return appInfo.packageName // Return the package name if found
            }
        }
        return null // Return null if no match is found
    }
    private fun parseBounds(boundsString: String?): Rect? {
        if (boundsString == null) return null
        val pattern = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
        val matcher = pattern.matcher(boundsString)
        return if (matcher.matches()) {
            try {
                val left = matcher.group(1).toInt()
                val top = matcher.group(2).toInt()
                val right = matcher.group(3).toInt()
                val bottom = matcher.group(4).toInt()
                Rect(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
            } catch (e: NumberFormatException) { null }
        } else { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "AgentService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
    // --- Notification Management ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Service Channel",
                NotificationManager.IMPORTANCE_LOW // Use LOW to be less intrusive
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Blurr Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

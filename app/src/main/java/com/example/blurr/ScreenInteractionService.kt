package com.example.blurr

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import android.util.Xml
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import kotlin.coroutines.resumeWithException
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView


class ScreenInteractionService : AccessibilityService() {

    companion object {
        var instance: ScreenInteractionService? = null
        const val ACTION_SCREENSHOT_TAKEN = "com.example.accessiblity_service_test.SCREENSHOT_TAKEN"

    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("InteractionService", "Accessibility Service connected.")
    }
    @RequiresApi(Build.VERSION_CODES.O)


    private fun showDebugTap(tapX: Float, tapY: Float) {
        // Check if we have the necessary permission to draw overlays
        if (!Settings.canDrawOverlays(this)) {
            Log.w("InteractionService", "Cannot show debug tap: 'Draw over other apps' permission not granted.")
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayView = ImageView(this)

        // Programmatically create a red circle drawable
        val tapIndicator = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x80FF0000.toInt()) // Semi-transparent red
            setSize(100, 100)
            setStroke(4, 0xFFFF0000.toInt()) // Solid red border
        }
        overlayView.setImageDrawable(tapIndicator)

        // ================================== THE FIX ==================================
        // Initialize with basic parameters first.
        val params = WindowManager.LayoutParams(
            100, // width
            100, // height
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        // Explicitly set the gravity and absolute coordinates.
        // This ensures the (x, y) values are treated as absolute positions
        // from the top-left corner of the screen.
        params.gravity = Gravity.TOP or Gravity.START
        params.x = tapX.toInt() - 50 // Offset to center the 100x100 circle
        params.y = tapY.toInt() - 50 // Offset to center the 100x100 circle
        // ===========================================================================

        // UI operations must be on the main thread
        Handler(Looper.getMainLooper()).post {
            try {
                windowManager.addView(overlayView, params)
                // Schedule the removal of the view after a short duration
                Handler(Looper.getMainLooper()).postDelayed({
                    if (overlayView.isAttachedToWindow) {
                        windowManager.removeView(overlayView)
                    }
                }, 500L) // Display for 500 milliseconds
            } catch (e: Exception) {
                Log.e("InteractionService", "Failed to add debug tap view", e)
            }
        }
    }

    suspend fun dumpWindowHierarchy(): String {
        // Ensure this potentially long-running operation happens off the main thread.
        // Dispatchers.Default is perfect for CPU-bound work like traversing a tree.
        return withContext(Dispatchers.Default) {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e("InteractionService", "Root node is null, cannot dump hierarchy.")
                return@withContext "<hierarchy/>" // Return empty, valid XML
            }

            // Use a StringWriter to build the XML in memory instead of a file
            val stringWriter = StringWriter()
            try {
                val serializer: XmlSerializer = Xml.newSerializer()
                serializer.setOutput(stringWriter)
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "hierarchy")

                // The recursive dumpNode helper works perfectly here without any changes!
                dumpNode(rootNode, serializer, 0)

                serializer.endTag(null, "hierarchy")
                serializer.endDocument()

                Log.d("InteractionService", "UI hierarchy dumped to string successfully.")
            } catch (e: Exception) {
                Log.e("InteractionService", "Error dumping UI hierarchy to string", e)
                return@withContext "<hierarchy error=\"${e.message}\"/>" // Return error XML
            }
            logLongString("UI_DUMP", stringWriter.toString())

            // Return the final XML string
            stringWriter.toString()
        }
    }
    private fun dumpNode(node: android.view.accessibility.AccessibilityNodeInfo?, serializer: XmlSerializer, index: Int) {
        if (node == null) return

        serializer.startTag(null, "node")

        // Add common attributes to the XML node
        serializer.attribute(null, "index", index.toString())
        serializer.attribute(null, "text", node.text?.toString() ?: "")
        serializer.attribute(null, "resource-id", node.viewIdResourceName ?: "")
        serializer.attribute(null, "class", node.className?.toString() ?: "")
        serializer.attribute(null, "package", node.packageName?.toString() ?: "")
        serializer.attribute(null, "content-desc", node.contentDescription?.toString() ?: "")
        serializer.attribute(null, "checkable", node.isCheckable.toString())
        serializer.attribute(null, "checked", node.isChecked.toString())
        serializer.attribute(null, "clickable", node.isClickable.toString())
        serializer.attribute(null, "enabled", node.isEnabled.toString())
        serializer.attribute(null, "focusable", node.isFocusable.toString())
        serializer.attribute(null, "focused", node.isFocused.toString())
        serializer.attribute(null, "scrollable", node.isScrollable.toString())
        serializer.attribute(null, "long-clickable", node.isLongClickable.toString())
        serializer.attribute(null, "password", node.isPassword.toString())
        serializer.attribute(null, "selected", node.isSelected.toString())

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        serializer.attribute(null, "bounds", bounds.toShortString())

        // Recursively dump children
        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), serializer, i)
        }

        serializer.endTag(null, "node")
    }


    fun logLongString(tag: String, message: String) {
        val maxLogSize = 2000 // Split into chunks of 2000 characters
        for (i in 0..message.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > message.length) message.length else end
            Log.d(tag, message.substring(start, end))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We are triggering actions proactively, so we don't need to react to events.
    }

    override fun onInterrupt() {
        Log.e("InteractionService", "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("InteractionService", "Accessibility Service destroyed.")
    }

    fun clickOnPoint(x: Float, y: Float) {
        showDebugTap(x, y)

        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Performs a swipe gesture on the screen.
     * @param duration The time in milliseconds the swipe should take.
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, null, null)
    }

    /**
     * Types the given text into the currently focused editable field.
     */
    fun typeTextInFocusedField(textToType: String) {
        // Find the node that currently has input focus
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            // To append text rather than replacing it, we get existing text first
            val existingText =  ""
            val newText = existingText.toString() + textToType

            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            Log.e("InteractionService", "Could not find a focused editable field to type in.")
        }
    }

    /**
     * Triggers the 'Back' button action.
     */
    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Triggers the 'Home' button action.
     */
    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Triggers the 'App Switch' (Recents) action.
     */
    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Simulates an 'Enter' key press on the focused element.
     */
    fun performEnter() {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            // A simple click often works for 'Enter' on buttons or submitting forms
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            Log.e("InteractionService", "Could not find a focused node to 'Enter' on.")
        }
    }
    /**
     * Asynchronously captures a screenshot from an AccessibilityService in a safe and reliable way.
     * This function follows the "Strict Librarian" rule: it always closes the screenshot resource
     * after use to prevent leaks and allow subsequent screenshots to succeed.
     *
     * @return A nullable Bitmap. Returns the screenshot Bitmap on success, or null if any part of the process fails.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(): Bitmap? {
        // A top-level try-catch block ensures that no matter what fails inside the coroutine,
        // the app will not crash. It will log the error and return null.
        return try {
            // suspendCancellableCoroutine is the standard way to wrap a modern callback-based
            // Android API into a clean, suspendable coroutine.
            suspendCancellableCoroutine { continuation ->
                // The executor ensures the result callbacks happen on the main UI thread,
                // which is a requirement for many UI-related APIs.
                val executor = ContextCompat.getMainExecutor(this)

                // STEP 1: Ask the "Librarian" (Android OS) to check out the "book" (Screenshot).
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {

                        // This block is called if the system successfully grants us the screenshot buffer.
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            // The HardwareBuffer is the actual low-level resource. It's the "special book".
                            val hardwareBuffer = screenshotResult.hardwareBuffer

                            if (hardwareBuffer == null) {
                                // If, for some reason, the buffer is null even on success, fail gracefully.
                                continuation.resumeWithException(Exception("Screenshot hardware buffer was null."))
                                return
                            }

                            // STEP 2: "Photocopy the book" by wrapping the HardwareBuffer into a standard Bitmap.
                            // We make a mutable copy so we can work with it after closing the original buffer.
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)

                            // STEP 3: THIS IS THE MOST IMPORTANT STEP.
                            // "Return the book to the librarian." We immediately close the original HardwareBuffer
                            // to release the system resource. This allows the *next* screenshot call to succeed.
                            hardwareBuffer.close()

                            // STEP 4: Give the "photocopy" (the Bitmap) back to our agent.
                            if (bitmap != null) {
                                // If the bitmap was created successfully, resume the coroutine with the result.
                                continuation.resume(bitmap)
                            } else {
                                // If bitmap creation failed, resume with an error.
                                continuation.resumeWithException(Exception("Failed to wrap hardware buffer into a Bitmap."))
                            }
                        }

                        // This block is called if the "Librarian" denies our request for any reason.
                        override fun onFailure(errorCode: Int) {
                            // We don't crash the app. We just tell the coroutine that it failed,
                            // which will be caught by our top-level try-catch block.
                            continuation.resumeWithException(Exception("Screenshot failed with error code: $errorCode"))
                        }
                    }
                )
            }
        } catch (e: Exception) {
            // Any exception from resumeWithException will be caught here.
            // We log the full error with its stack trace for easy debugging.
            Log.e("ScreenshotUtil", "Screenshot capture failed", e)
            // We return null to the caller, signaling that the operation did not succeed.
            null
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            file.parentFile?.mkdirs()

            val fos: OutputStream = FileOutputStream(file)
            fos.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Log.d("InteractionService", "Screenshot saved to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("InteractionService", "Failed to save bitmap to file.", e)
        }
    }


    /**
     * Traverses the UI tree and returns a list of all enabled, interactable elements.
     */
    suspend fun getInteractableElements(): List<InteractableElement> {
        return withContext(Dispatchers.Default) {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.e("InteractionService", "Root node is null, cannot get elements.")
                return@withContext emptyList<InteractableElement>()
            }

            val interactableElements = mutableListOf<InteractableElement>()
            findInteractableNodesRecursive(rootNode, interactableElements)
            interactableElements
        }
    }

    /**
     * A private recursive helper to find and collect interactable nodes.
     */
    private fun findInteractableNodesRecursive(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        list: MutableList<InteractableElement>
    ) {
        if (node == null) return
//
        // =================================================================
        // THIS IS THE CORE LOGIC: Check if the node is interactable
        // =================================================================
//        val isInteractable = (node.isClickable || node.isLongClickable || node.isScrollable || node.isFocusable || node.isLongClickable || node.is) && node.isEnabled

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        // We only care about elements that are actually visible on screen
        if (!bounds.isEmpty) {
            list.add(
                InteractableElement(
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    resourceId = node.viewIdResourceName,
                    className = node.className?.toString(),
                    bounds = bounds,
                    node = node // Keep the original node reference to perform actions
                )
            )
        }

        // Continue searching through the children
        for (i in 0 until node.childCount) {
            findInteractableNodesRecursive(node.getChild(i), list)
        }
    }

}

data class InteractableElement(
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: android.graphics.Rect,
    // We can also hold a reference to the original node if needed for performing actions
    val node: android.view.accessibility.AccessibilityNodeInfo
) {
    // A helper to get the center coordinates, useful for tapping
    fun getCenter(): android.graphics.Point {
        return android.graphics.Point(bounds.centerX(), bounds.centerY())
    }
}

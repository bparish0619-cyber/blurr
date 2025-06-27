package com.example.blurr


import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import org.xmlpull.v1.XmlSerializer
import java.io.IOException


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
// Add these imports to the top of ScreenInteractionService.kt

    // Add this new method inside your ScreenInteractionService class
    fun dumpWindowHierarchyToFile(outputFile: File) {
        val rootNode = rootInActiveWindow ?: return // Get the root node of the active window

        try {
            val fileOutputStream = FileOutputStream(outputFile)
            val serializer: XmlSerializer = Xml.newSerializer()

            serializer.setOutput(fileOutputStream, "UTF-8")
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "hierarchy")

            // Start the recursive traversal and XML generation
            dumpNode(rootNode, serializer, 0)

            serializer.endTag(null, "hierarchy")
            serializer.endDocument()
            fileOutputStream.close()

            Log.d("InteractionService", "UI hierarchy dumped to ${outputFile.absolutePath}")

        } catch (e: IOException) {
            Log.e("InteractionService", "Error dumping UI hierarchy", e)
        }
    }

    // Add this private helper function inside your ScreenInteractionService class
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
            val existingText = focusedNode.text ?: ""
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


    // Replace the old captureScreenshot(onComplete:...) with this new suspend function
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(): Bitmap = suspendCancellableCoroutine { continuation ->
        takeScreenshot(0, executor, @RequiresApi(Build.VERSION_CODES.R)
        object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardwareBuffer = screenshot.hardwareBuffer
                val colorSpace = screenshot.colorSpace
                if (hardwareBuffer != null) {
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                    if (bitmap != null) {
                        // Resume the coroutine with the successful result
                        continuation.resume(bitmap)
                    } else {
                        continuation.resumeWith(Result.failure(Exception("Failed to wrap hardware buffer")))
                    }
                    hardwareBuffer.close()
                } else {
                    continuation.resumeWith(Result.failure(Exception("Screenshot hardware buffer was null")))
                }
            }

            override fun onFailure(errorCode: Int) {
                // Resume the coroutine with an exception
                continuation.resumeWith(Result.failure(Exception("Screenshot failed with error code: $errorCode")))
            }
        })
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

}
package com.example.blurr.api

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.blurr.ScreenInteractionService

/**
 * A rewritten Finger class that uses the AccessibilityService for all actions,
 * requiring no root access.
 */
class Finger(private val context: Context) {

    private val TAG = "Finger (Accessibility)"

    // A helper to safely get the service instance
    private val service: ScreenInteractionService?
        get() {
            val instance = ScreenInteractionService.instance
            if (instance == null) {
                Log.e(TAG, "ScreenInteractionService is not running or not connected!")
            }
            return instance
        }

    /**
     * Starts the ChatActivity within the app using a standard Android Intent.
     */
    fun goToChatRoom(message: String) {
        Log.d(TAG, "Opening ChatActivity with message: $message")
        try {
            val intent = Intent().apply {
                // Use the app's own context to find the activity class
                setClassName(context, "com.example.blurr.ChatActivity")
                putExtra("custom_message", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ChatActivity. Make sure it's defined in your AndroidManifest.xml", e)
        }
    }

    /**
     * Opens an app directly using package manager (requires QUERY_ALL_PACKAGES permission).
     * This method is intended for debugging purposes only and should be disabled in production.
     * 
     * @param packageName The package name of the app to open
     * @return true if the app was successfully launched, false otherwise
     */
    fun openApp(packageName: String): Boolean {
        Log.d(TAG, "Attempting to open app with package: $packageName")
        return try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Successfully launched app: $packageName")
                true
            } else {
                Log.e(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
            false
        }
    }

    /**
     * Taps a point on the screen.
     */
    fun tap(x: Int, y: Int) {
        Log.d(TAG, "Tapping at ($x, $y)")
        service?.clickOnPoint(x.toFloat(), y.toFloat())
    }

    /**
     * Swipes between two points on the screen.
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 500) {
        Log.d(TAG, "Swiping from ($x1, $y1) to ($x2, $y2)")
        service?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())
    }

    /**
     * Types text into the focused input field. This is now much more efficient.
     */
    fun type(text: String) {
        Log.d(TAG, "Typing text: $text")
        service?.typeTextInFocusedField(text)
    }

    /**
     * Simulates pressing the 'Enter' key.
     */
    fun enter() {
        Log.d(TAG, "Performing 'Enter' action")
        service?.performEnter()
    }

    /**
     * Navigates back.
     */
    fun back() {
        Log.d(TAG, "Performing 'Back' action")
        service?.performBack()
    }

    /**
     * Goes to the home screen.
     */
    fun home() {
        Log.d(TAG, "Performing 'Home' action")
        service?.performHome()
    }

    /**
     * Opens the app switcher (recents).
     */
    fun switchApp() {
        Log.d(TAG, "Performing 'App Switch' action")
        service?.performRecents()
    }
}
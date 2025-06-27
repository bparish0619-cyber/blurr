package com.example.blurr.service

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.blurr.ScreenInteractionService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EyesController(context: Context) {

    // This now points to the public directory where your screenshots will be saved.
    private val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    // This will now point to the specific screenshot file after it's created.
    private var latestScreenshotFile: File? = null

    // The path for the XML file can remain internal as you don't need to view it manually.
    private val xmlFile: File = File(context.filesDir, "window_dump.xml")

    /**
     * Takes a screenshot and saves it to the public Pictures/ScreenAgent directory.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreenshot() {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return
        }

        // 1. Define where to save the image (e.g., Pictures/ScreenAgent/)
        val screenshotDir = File(publicPicturesDir, "ScreenAgent")
        screenshotDir.mkdirs() // Create the directory if it doesn't exist

        // 2. Create a unique filename with a timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(screenshotDir, "SS_$timestamp.png")
        this.latestScreenshotFile = file // Store the path of the latest file

        Log.d("AccessibilityController", "Requesting screenshot to be saved at: ${file.absolutePath}")

        // 3. Call the modified service method, passing the output file
        service.captureScreenshot(file)
    }

    /**
     * Dumps the current UI layout to an XML file using the Accessibility Service.
     */
    fun captureLayout() {
        val service = ScreenInteractionService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Accessibility Service is not running!")
            return
        }
        Log.d("AccessibilityController", "Requesting UI layout dump...")
        service.dumpWindowHierarchyToFile(xmlFile)
    }

    /**
     * Returns the File object of the most recently captured screenshot.
     * Returns null if no screenshot has been taken yet.
     */
    fun getScreenshotFile(): File? {
        return latestScreenshotFile
    }

    fun getWindowDumpFile(): File {
        return xmlFile
    }
}
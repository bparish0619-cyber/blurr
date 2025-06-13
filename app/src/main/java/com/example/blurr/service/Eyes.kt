package com.example.blurr.service

import android.R
import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

class Eyes(private val context: Context) {
    private val screenshotFile: File = File(context.filesDir, "latest.png")
    private val xmlFile: File = File(context.filesDir, "window_dump.xml")
    fun openEyes() {
        println("Eyes are open")
        val output = screenshotFile.absolutePath
        Shell.cmd("screencap -p $output").exec()
    }

    fun openXMLEyes() {
        val output = xmlFile.absolutePath
        Shell.cmd("uiautomator dump $output").exec()
    }



    fun getScreenshotFile(): File {
        return screenshotFile
    }

    fun getWindowDumpFile(): File {
        return xmlFile
    }
}

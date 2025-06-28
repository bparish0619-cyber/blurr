package com.example.blurr.utilities

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.example.blurr.agent.Operator.ShortcutStep
import com.example.blurr.agent.Shortcut
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Persistent {
    fun saveShortcutsToFile(file: File, shortcuts: Map<String, Shortcut>) {
        val json = JSONObject()
        for ((name, shortcut) in shortcuts) {
            val shortcutJson = JSONObject().apply {
                put("name", shortcut.name)
                put("arguments", JSONArray(shortcut.arguments))
                put("description", shortcut.description)
                put("precondition", shortcut.precondition)
                put("atomicActionSequence", JSONArray().apply {
                    shortcut.atomicActionSequence.forEach { step ->
                        put(JSONObject().apply {
                            put("name", step.name)
                            put("argumentsMap", JSONObject(step.argumentsMap))
                        })
                    }
                })
            }
            json.put(name, shortcutJson)
        }
        file.writeText(json.toString(2))
    }

    fun loadShortcutsFromFile(file: File): MutableMap<String, Shortcut> {
        val result = mutableMapOf<String, Shortcut>()
        if (!file.exists()) return result

        val json = JSONObject(file.readText())
        for (key in json.keys()) {
            val obj = json.getJSONObject(key)
            val atomicActions = obj.getJSONArray("atomicActionSequence").let { arr ->
                List(arr.length()) { i ->
                    val stepObj = arr.getJSONObject(i)
                    val argsMap = stepObj.getJSONObject("argumentsMap").let { mapObj ->
                        mapObj.keys().asSequence().associateWith { k -> mapObj.getString(k) }
                    }
                    ShortcutStep(stepObj.getString("name"), argsMap)
                }
            }

            val shortcut = Shortcut(
                name = obj.getString("name"),
                arguments = obj.getJSONArray("arguments").let { arr -> List(arr.length()) { i -> arr.getString(i) } },
                description = obj.getString("description"),
                precondition = obj.getString("precondition"),
                atomicActionSequence = atomicActions
            )

            result[key] = shortcut
        }
        return result
    }

    fun saveTipsToFile(file: File, tips: String) {
        file.writeText(tips)
    }
    fun loadTipsFromFile(file: File): String {
        return if (file.exists()) file.readText() else ""
    }

    fun saveBitmapForDebugging(bitmap: Bitmap) {
        val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val screenshotDir = File(publicPicturesDir, "ScreenAgent")
        screenshotDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(screenshotDir, "SS_$timestamp.png")
        try {
            val fos = java.io.FileOutputStream(file)
            fos.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.d("MainActivity", "Debug screenshot saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save debug screenshot", e)
        }
    }


}
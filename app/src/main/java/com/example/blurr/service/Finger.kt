package com.example.blurr.service//package com.example.blurr.service
//
//import android.content.Context
//import android.util.Log
//import com.topjohnwu.superuser.Shell
//import java.io.File
//
//class Finger {
//
//    private val TAG = "Finger"
//
//    fun goToChatRoom(message: String) {
//        println("am start -n com.example.blurr/.ChatActivity -e custom_message \"$message\"")
//        Shell.cmd("am start -n com.example.blurr/.ChatActivity -e custom_message \"$message\"").exec()
//    }
//
//    fun tap(x: Int, y: Int) {
//        Shell.cmd("input tap $x $y").exec()
//    }
//
//    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 500) {
//        Shell.cmd("input swipe $x1 $y1 $x2 $y2 $duration").exec()
//    }
//
//    fun type(text: String) {
//        for (char in text) {
//            when {
//                char == ' ' -> Shell.cmd("input keyevent 62").exec()
//                char == '\n' || char == '_' -> Shell.cmd("input keyevent 66").exec()
//                char.isLetterOrDigit() -> Shell.cmd("input text \"$char\"").exec()
//                char in "-.,!?@'°/:;()" -> Shell.cmd("input text \"\\$char\"").exec()
//                else -> Shell.cmd("am broadcast -a ADB_INPUT_TEXT --es msg \"$char\"").exec()
//            }
//        }
//    }
//
//    fun enter() {
//        Shell.cmd("input keyevent KEYCODE_ENTER").exec()
//    }
//
//
//    fun back() {
//        Shell.cmd("input keyevent 4").exec()
//    }
//
//    fun home() {
//        Shell.cmd("input keyevent KEYCODE_HOME").exec()
//    }
//
//    fun switchApp() {
//        Shell.cmd("input keyevent KEYCODE_APP_SWITCH").exec()
//    }
//}

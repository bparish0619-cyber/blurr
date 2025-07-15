package com.example.blurr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var microphoneStatus: TextView
    private lateinit var overlayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        // Find all the new status TextViews
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        microphoneStatus = findViewById(R.id.microphoneStatus)
        overlayStatus = findViewById(R.id.overlayStatus)

        val backButton: Button = findViewById(R.id.backButtonPermissions)
        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update statuses every time the user returns to this screen
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        // 1. Accessibility Service Check
        if (isAccessibilityServiceEnabled()) {
            accessibilityStatus.text = "Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#4CAF50")) // Green text
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_granted)
        } else {
            accessibilityStatus.text = "Not Granted"
            accessibilityStatus.setTextColor(Color.parseColor("#F44336")) // Red text
            accessibilityStatus.setBackgroundResource(R.drawable.status_background_denied)
        }

        // 2. Microphone Permission Check
        if (isMicrophonePermissionGranted()) {
            microphoneStatus.text = "Granted"
            microphoneStatus.setTextColor(Color.parseColor("#4CAF50")) // Green text
            microphoneStatus.setBackgroundResource(R.drawable.status_background_granted)
        } else {
            microphoneStatus.text = "Not Granted"
            microphoneStatus.setTextColor(Color.parseColor("#F44336")) // Red text
            microphoneStatus.setBackgroundResource(R.drawable.status_background_denied)
        }

        // 3. Display Over Other Apps Check
        if (isOverlayPermissionGranted()) {
            overlayStatus.text = "Granted"
            overlayStatus.setTextColor(Color.parseColor("#4CAF50")) // Green text
            overlayStatus.setBackgroundResource(R.drawable.status_background_granted)
        } else {
            overlayStatus.text = "Not Granted"
            overlayStatus.setTextColor(Color.parseColor("#F44336")) // Red text
            overlayStatus.setBackgroundResource(R.drawable.status_background_denied)
        }
    }

    // --- Helper functions to check each permission ---

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    if (splitter.next().equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isOverlayPermissionGranted(): Boolean {
        // Settings.canDrawOverlays is available from API 23 (Android 6.0) onwards
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            // On older versions, this permission is granted at install time if declared.
            // For simplicity, we can assume it's granted if the app runs on an old OS.
            true
        }
    }
}
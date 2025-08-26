package com.blurr.voice.utilities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blurr.voice.ScreenInteractionService

/**
 * Utility class to handle all permission-related functionality
 */
class PermissionManager(private val activity: AppCompatActivity) {

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResult: ((Map<String, Boolean>) -> Unit)? = null

    /**
     * Checks if all essential permissions are granted.
     */
    fun areAllPermissionsGranted(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    /**
     * Initialize the permission launcher to handle multiple permissions.
     */
    fun initializePermissionLauncher() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle the result for each permission
            val allGranted = permissions.all { it.value }

            if (allGranted) {
                Toast.makeText(activity, "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Some features may not work without all permissions.", Toast.LENGTH_LONG).show()
            }
            onPermissionResult?.invoke(permissions)
        }
    }

    /**
     * Builds a list of permissions that have not been granted yet and requests them all at once.
     */
    fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check for Notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted()) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Check for Microphone permission
        if (!isMicrophonePermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // If there are any permissions to request, launch the system dialog.
        if (permissionsToRequest.isNotEmpty()) {
            Log.i("PermissionManager", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            permissionLauncher?.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.i("PermissionManager", "All necessary runtime permissions are already granted.")
        }
    }

    /**
     * Check if microphone permission is granted
     */
    fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Notification permission not required before Android 13
        }
    }

    /**
     * Check if accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val service = activity.packageName + "/" + ScreenInteractionService::class.java.canonicalName
        val accessibilityEnabled = Settings.Secure.getInt(
            activity.applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                activity.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    val componentName = splitter.next()
                    if (componentName.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Open accessibility settings
     */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }

    /**
     * Check and request overlay permission
     */
    fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    /**
     * Check if overlay permission is granted
     */
    fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true // Granted at install time on older versions
        }
    }

    /**
     * Set callback for permission results
     */
    fun setPermissionResultCallback(callback: (Map<String, Boolean>) -> Unit) {
        onPermissionResult = callback
    }

    /**
     * Gets a list of names for all essential permissions that are currently missing.
     * @return A list of strings, e.g., ["Accessibility Service", "Microphone"]. An empty list if all are granted.
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (!isAccessibilityServiceEnabled()) {
            missing.add("Accessibility Service")
        }
        if (!isMicrophonePermissionGranted()) {
            missing.add("Microphone")
        }
        if (!isOverlayPermissionGranted()) {
            missing.add("Display Over Other Apps")
        }
        if (!isNotificationPermissionGranted()) {
            missing.add("Notifications")
        }
        return missing
    }

    /**
     * Get permission status summary
     */
    fun getPermissionStatusSummary(): String {
        val status = mutableListOf<String>()

        if (isMicrophonePermissionGranted()) {
            status.add("Microphone: ✓")
        } else {
            status.add("Microphone: ✗")
        }

        if (isNotificationPermissionGranted()) {
            status.add("Notifications: ✓")
        } else {
            status.add("Notifications: ✗")
        }

        if (isAccessibilityServiceEnabled()) {
            status.add("Accessibility: ✓")
        } else {
            status.add("Accessibility: ✗")
        }

        if (isOverlayPermissionGranted()) {
            status.add("Overlay: ✓")
        } else {
            status.add("Overlay: ✗")
        }

        return status.joinToString(", ")
    }
}
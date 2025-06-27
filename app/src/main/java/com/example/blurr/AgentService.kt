package com.example.blurr


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.random.Random

class AgentService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tapRunnable: Runnable
    private lateinit var screenshotRunnable: Runnable

    companion object {
        var isRunning = false
        const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannel"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Agent")
            .setContentText("Agent is running in the background.")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AgentService", "Service started")
        startTasks()
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startTasks() {
        val interactionService = ScreenInteractionService.instance
        if (interactionService == null) {
            Log.e("AgentService", "Accessibility Service not running. Stopping tasks.")
            stopSelf()
            return
        }

        // Task 1: Perform random tap every 5 seconds
        tapRunnable = Runnable {
            val displayMetrics = resources.displayMetrics
            val randomX = Random.nextInt(0, displayMetrics.widthPixels).toFloat()
            val randomY = Random.nextInt(0, displayMetrics.heightPixels).toFloat()
            Log.d("AgentService", "Performing tap at ($randomX, $randomY)")
            interactionService.clickOnPoint(randomX, randomY)
            handler.postDelayed(tapRunnable, 5000)
        }
        handler.post(tapRunnable)

        // Task 2: Take screenshot every 5 seconds
        screenshotRunnable = Runnable {
            Log.d("AgentService", "Requesting screenshot")
//            interactionService.captureScreenshot()
            // Add a small delay so it doesn't happen at the exact same time as the tap
            handler.postDelayed(screenshotRunnable, 5000)
        }
        handler.postDelayed(screenshotRunnable, 2500) // Start screenshot task with a 2.5s offset
    }

    private fun stopTasks() {
        if (::tapRunnable.isInitialized) {
            handler.removeCallbacks(tapRunnable)
        }
        if (::screenshotRunnable.isInitialized) {
            handler.removeCallbacks(screenshotRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AgentService", "Service destroyed")
        stopTasks()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Agent Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
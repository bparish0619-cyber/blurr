// In a new file: CrawlerService.kt
package com.example.blurr.crawler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.blurr.api.Eyes
import com.example.blurr.api.Finger
import kotlinx.coroutines.*

class CrawlerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var appCartographer: Cartographer

    // --- Companion Object for constants ---
    companion object {
        private const val TAG = "CrawlerService" // Logging Tag
        const val ACTION_START_CRAWL = "com.example.blurr.START_CRAWL"
        const val NOTIFICATION_CHANNEL_ID = "CrawlerServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service creating and initializing components...")
        val eyes = Eyes(this)
        val finger = Finger(this)
        val semanticParser = SemanticParser(this)
        val screenAnalyzer = ScreenAnalyzer()
        appCartographer = Cartographer(this, semanticParser, screenAnalyzer, finger, eyes)
        Log.i(TAG, "Service components initialized.")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_CRAWL) {
            startForeground(NOTIFICATION_ID, createNotification("Crawler starting in 10 seconds..."))
            Log.i(TAG, "Crawl command received. Starting foreground service.")

            serviceScope.launch {
                try {
                    Log.d(TAG, "Coroutine started. Waiting for 10 seconds before crawl.")
                    delay(10000)
                    Log.i(TAG, "Delay finished. Beginning crawl process...")

                    updateNotification("Crawling in progress...")
                    val finalAppMapJson = appCartographer.crawl()

                    Log.i(TAG, "Crawl process finished successfully.")
                    Log.d(TAG, "Final App Map JSON size: ${finalAppMapJson.length} characters.")
                    // TODO: Save the finalAppMapJson to a file or database.

                    updateNotification("Crawl complete!")
                    Log.i(TAG, "Notification updated to 'Crawl complete!'")

                } catch (e: Exception) {
                    Log.e(TAG, "The crawl process failed with an unhandled exception.", e)
                    updateNotification("Crawl failed! Check logs.")
                } finally {
                    Log.i(TAG, "Crawl coroutine finished. Stopping service.")
                    stopSelf() // Stop the service when the work is done or fails.
                }
            }
        } else {
            Log.w(TAG, "Service started without a valid action. Stopping self.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancel all coroutines when the service is destroyed.
        Log.i(TAG, "Service destroyed and all coroutines cancelled.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding.
    }

    // --- Notification Management ---
    private fun createNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Crawler Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Cartographer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
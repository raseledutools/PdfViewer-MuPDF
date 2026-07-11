package com.rasel.RasFocus.combo.selfcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BpAppBlockerService — ButtonPhone focus lock এর সহায়ক service।
 * মূল blocking logic BpOverlayService এ আছে।
 * এই service টি foreground notification দিয়ে process alive রাখে।
 */
class BpAppBlockerService : Service() {
    companion object {
        private const val CHANNEL_ID = "bp_app_blocker_channel"
        private const val NOTIF_ID = 3001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps Focus Lock running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Focus Lock Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Focus Lock Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
    }
}

/**
 * BlockerForegroundService — VPN-based website blocker এর foreground companion।
 * BlockerVpnService চলার সময় persistent notification দিয়ে process alive রাখে।
 */
class BlockerForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "blocker_foreground_channel"
        private const val NOTIF_ID = 3003
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps app blocker running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RasFocus Blocker Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RasFocus Blocker Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * UsageNotificationService — screen time usage notification দেওয়ার service।
 * Here we also run the background Auto-Updater loop every 12 hours.
 */
class UsageNotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "usage_notif_channel"
        private const val NOTIF_ID = 3002
        
        fun start(context: android.content.Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private var updateJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage Notifications",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        // Start Background Updater Loop
        updateJob = serviceScope.launch {
            while (isActive) {
                try {
                    val prefs = this@UsageNotificationService.getSharedPreferences("AutoUpdaterPrefs", Context.MODE_PRIVATE)
                    val lastTag = prefs.getString(com.rasel.pdfviewer.AutoUpdater.LAST_TAG_KEY, "") ?: ""
                    
                    com.rasel.pdfviewer.AutoUpdater.fetchLatestReleaseInfo { info ->
                        if (info != null && info.tagName != lastTag) {
                            // Found a new update, silently download it
                            com.rasel.pdfviewer.AutoUpdater.silentDownloadUpdate(
                                this@UsageNotificationService, 
                                com.rasel.pdfviewer.AutoUpdater.APK_UNIVERSAL, 
                                info.tagName
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore exceptions in background loop
                }
                
                // Wait 12 hours before checking again
                kotlinx.coroutines.delay(12 * 60 * 60 * 1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RasFocus Usage Tracker")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RasFocus Usage Tracker")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        }
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

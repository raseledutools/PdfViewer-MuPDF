package com.rasel.RasFocus.child

// ============================================================
// RASFOCUS+ CHILD APP MODULE (BACKGROUND SERVICES ONLY)
// UI moved to ChildControlModule.kt
// ============================================================

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// ============================================================
// PART 1: ADDITIONAL HELPERS (Needed by background services)
// ============================================================

object ChildPermissions {
    fun startAllServices(context: Context) {
        // ChildFirebaseService — Firebase command listener
        val firebaseIntent = Intent(context, ChildFirebaseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(firebaseIntent)
        } else {
            context.startService(firebaseIntent)
        }

        // LocationTracker — GPS location push to Firebase (if exists)
        try {
            val locationClass = Class.forName("com.rasel.RasFocus.child.LocationTracker")
            val locationIntent = Intent(context, locationClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(locationIntent)
            } else {
                context.startService(locationIntent)
            }
        } catch (e: Exception) {
            // LocationTracker not found, skip
        }

        // FirebaseCommandListener — parent command receiver (if exists)
        try {
            val commandClass = Class.forName("com.rasel.RasFocus.child.FirebaseCommandListener")
            val commandIntent = Intent(context, commandClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(commandIntent)
            } else {
                context.startService(commandIntent)
            }
        } catch (e: Exception) {
            // FirebaseCommandListener not found, skip
        }
    }
}

// ============================================================
// PART 2: ALTERNATIVE UI ENTRY POINT (if needed)
// Use ChildControlModule.ChildRootScreen instead
// ============================================================

@Composable
fun ChildRootScreenAlternate(context: Context) {
    // Redirect to the main implementation in ChildControlModule
    ChildRootScreen(context)
}

// ============================================================
// PART 3: ADDITIONAL BACKGROUND SERVICES (if extra monitoring needed)
// ============================================================

// Optional: LocationTracker stub (if not defined elsewhere)
class LocationTrackerStub : Service() {
    override fun onCreate() {
        super.onCreate()
        val channelId = "rasfocus_location"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RasFocus+ Location")
            .setContentText("Location tracking active.")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build()
        startForeground(2, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}

// Optional: FirebaseCommandListener stub (if not defined elsewhere)
class FirebaseCommandListenerStub : Service() {
    override fun onCreate() {
        super.onCreate()
        val channelId = "rasfocus_commands"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Command Listener", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RasFocus+ Commands")
            .setContentText("Listening for parent commands.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(3, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}

// ============================================================
// DEPRECATED: Use ChildControlModule.kt for all main logic
// ============================================================

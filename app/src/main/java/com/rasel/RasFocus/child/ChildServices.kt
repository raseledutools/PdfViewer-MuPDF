package com.rasel.RasFocus.child

// ============================================================
//  RasFocus+ — Child Background Services
//  Services  : ScreenCaptureService, LocationTracker,
//               FirebaseCommandListener, AppUsageSyncService
//  Author    : RasFocus+ Architecture Team
// ============================================================

import android.app.*
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val SERVICES_TAG = "RasFocus_ChildSvc"

// ─────────────────────────────────────────────────────────────────────────────
// Cloudinary helper functions
// initCloudinary — MediaManager একবারই init হয়, duplicate init crash করে
// uploadScreenshotToCloudinary — file upload করে public URL return করে
// ─────────────────────────────────────────────────────────────────────────────

private var cloudinaryInitialized = false

fun initCloudinary(context: Context) {
    if (cloudinaryInitialized) return
    try {
        val config = mapOf(
            "cloud_name" to "rasfocus",   // ← তোমার Cloudinary cloud name
            "api_key"    to "",            // public upload-এ api_key optional
            "api_secret" to ""
        )
        MediaManager.init(context, config)
        cloudinaryInitialized = true
        Log.i(SERVICES_TAG, "Cloudinary initialized")
    } catch (e: Exception) {
        Log.e(SERVICES_TAG, "Cloudinary init failed: ${e.message}")
    }
}

suspend fun uploadScreenshotToCloudinary(file: File, childUid: String): String =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            MediaManager.get().upload(file.absolutePath)
                .option("folder", "rasfocus/$childUid/screenshots")
                .option("public_id", "ss_${System.currentTimeMillis()}")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"]?.toString() ?: ""
                        Log.i(SERVICES_TAG, "Upload success: $url")
                        if (cont.isActive) cont.resume(url) {}
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e(SERVICES_TAG, "Upload error: ${error.description}")
                        if (cont.isActive) cont.resume("") {}
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        if (cont.isActive) cont.resume("") {}
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e(SERVICES_TAG, "Upload exception: ${e.message}")
            if (cont.isActive) cont.resume("") {}
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Notification Channel Ids
// ─────────────────────────────────────────────────────────────────────────────

private const val CHANNEL_SCREEN    = "rasfocus_child_screen"
private const val CHANNEL_LOCATION  = "rasfocus_child_location"
private const val CHANNEL_COMMANDS  = "rasfocus_child_commands"

private fun createChannel(context: Context, id: String, name: String, importance: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(id, name, importance).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

private fun buildForegroundNotif(
    context: Context,
    channelId: String,
    title: String,
    text: String
): Notification {
    val pi = PendingIntent.getActivity(
        context, 0,
        context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(context, channelId)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(pi)
        .build()
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. ScreenCaptureService
//    Parent "Screenshot নাও" command পেলে screen capture করে Cloudinary-তে
//    upload করে, URL Firebase-এ save করে।
//    MediaProjection token MainActivity থেকে Intent-এ আসে।
// ─────────────────────────────────────────────────────────────────────────────

class ScreenCaptureService : Service() {

    companion object {
        const val NOTIF_ID            = 9101
        const val EXTRA_RESULT_CODE   = "result_code"
        const val EXTRA_RESULT_DATA   = "result_data"

        /** Parent-এর screenshot command এলে MainActivity এই Intent fire করে */
        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val db          by lazy { Firebase.database.reference }
    private val childUid    get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay:  VirtualDisplay?  = null
    private var imageReader:     ImageReader?      = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_SCREEN, "RasFocus Screen Monitor", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(this, CHANNEL_SCREEN, "RasFocus+ Active", "Screen monitoring ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            takeScreenshot(resultCode, resultData)
        } else {
            Log.w(SERVICES_TAG, "ScreenCaptureService: no valid MediaProjection token, staying idle")
        }
        return START_STICKY
    }

    private fun takeScreenshot(resultCode: Int, data: Intent) {
        scope.launch {
            try {
                val metrics = DisplayMetrics()
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.currentWindowMetrics.bounds
                    metrics.widthPixels  = bounds.width()
                    metrics.heightPixels = bounds.height()
                    metrics.densityDpi   = resources.configuration.densityDpi
                } else {
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getMetrics(metrics)
                }

                val width  = metrics.widthPixels
                val height = metrics.heightPixels
                val dpi    = metrics.densityDpi

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mpm.getMediaProjection(resultCode, data)

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "RasFocusCapture",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, null
                )

                // Short delay — display needs a frame
                delay(500)

                val image: Image? = imageReader?.acquireLatestImage()
                if (image != null) {
                    val file = saveImageToFile(image, width, height)
                    image.close()
                    if (file != null && childUid.isNotEmpty()) {
                        initCloudinary(applicationContext)
                        val url = uploadScreenshotToCloudinary(file, childUid)
                        if (url.isNotEmpty()) {
                            val ts = System.currentTimeMillis()
                            db.child("children/$childUid/screenshots").push()
                                .setValue(mapOf("url" to url, "timestamp" to ts))
                            Log.i(SERVICES_TAG, "Screenshot uploaded: $url")
                        }
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(SERVICES_TAG, "Screenshot failed: ${e.message}", e)
            } finally {
                virtualDisplay?.release()
                mediaProjection?.stop()
                imageReader?.close()
                virtualDisplay  = null
                mediaProjection = null
                imageReader     = null
            }
        }
    }

    private fun saveImageToFile(image: Image, width: Int, height: Int): File? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride  = planes[0].pixelStride
            val rowStride    = planes[0].rowStride
            val rowPadding   = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "screenshot_${ts}.jpg")
            FileOutputStream(file).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            croppedBitmap.recycle()
            file
        } catch (e: Exception) {
            Log.e(SERVICES_TAG, "Image save failed: ${e.message}", e)
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. LocationTracker
//    প্রতি 5 মিনিটে child-এর GPS location Firebase-এ update করে।
//    Parent real-time map-এ দেখতে পারবে।
// ─────────────────────────────────────────────────────────────────────────────

class LocationTracker : Service() {

    companion object {
        const val NOTIF_ID            = 9102
        private const val INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
        private const val FASTEST_MS  = 60 * 1000L       // 1 minute fastest
    }

    private val db       by lazy { Firebase.database.reference }
    private val childUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest:     LocationRequest
    private lateinit var locationCallback:    LocationCallback

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_LOCATION, "RasFocus Location", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(
            this, CHANNEL_LOCATION,
            "RasFocus+ Location Active",
            "Sharing location with parent"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    pushLocationToFirebase(loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.i(SERVICES_TAG, "LocationTracker: updates started")
        } catch (e: SecurityException) {
            Log.e(SERVICES_TAG, "Location permission missing: ${e.message}")
            stopSelf()
        }
    }

    private fun pushLocationToFirebase(lat: Double, lng: Double, accuracy: Float) {
        if (childUid.isEmpty()) return
        val data = mapOf(
            "lat"       to lat,
            "lng"       to lng,
            "accuracy"  to accuracy,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("children/$childUid/location").setValue(data)
        Log.d(SERVICES_TAG, "Location updated → lat=$lat, lng=$lng")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. FirebaseCommandListener
//    Parent-এর real-time commands শোনে:
//      • isLocked       → device lock/unlock
//      • takeScreenshot → ScreenCaptureService trigger করে
//      • blockedApps    → FocusAccessibilityService-এর blocked list update
//      • screenTimeLimit→ limit update
// ─────────────────────────────────────────────────────────────────────────────

class FirebaseCommandListener : Service() {

    companion object {
        const val NOTIF_ID = 9103

        // Intent extra — MainActivity screenshot permission result পাঠাবে
        const val ACTION_SCREENSHOT_RESULT = "com.rasel.RasFocus.SCREENSHOT_RESULT"
        const val EXTRA_RESULT_CODE        = "result_code"
        const val EXTRA_RESULT_DATA        = "result_data"
    }

    private val db          by lazy { Firebase.database.reference }
    private val childUid    get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var commandListener:   ValueEventListener? = null
    private var deviceRef:         DatabaseReference?  = null

    // MediaProjection token — MainActivity থেকে broadcast এলে store করি
    private var projectionResultCode: Int?    = null
    private var projectionResultData: Intent? = null

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SCREENSHOT_RESULT) {
                projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                projectionResultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                Log.i(SERVICES_TAG, "Screenshot permission received, ready to capture")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_COMMANDS, "RasFocus Parental Commands", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(
            this, CHANNEL_COMMANDS,
            "RasFocus+ Connected",
            "Listening for parent commands"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        // Screenshot permission broadcast dingi
        val filter = IntentFilter(ACTION_SCREENSHOT_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }

        attachFirebaseListener()
    }

    private fun attachFirebaseListener() {
        if (childUid.isEmpty()) {
            Log.w(SERVICES_TAG, "FirebaseCommandListener: no uid, retrying in 10s")
            Handler(Looper.getMainLooper()).postDelayed({ attachFirebaseListener() }, 10_000)
            return
        }

        // Child নিজের device id হিসেবে childUid use করে
        deviceRef = db.child("children/$childUid/commands")

        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handleCommands(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(SERVICES_TAG, "Firebase command listener cancelled: ${error.message}")
            }
        }
        deviceRef!!.addValueEventListener(commandListener!!)

        // Online presence — child device online দেখাও
        db.child("children/$childUid/isOnline").setValue(true)
        db.child("children/$childUid/lastSeen").setValue(ServerValue.TIMESTAMP)
        Log.i(SERVICES_TAG, "FirebaseCommandListener attached for uid=$childUid")
    }

    private fun handleCommands(snapshot: DataSnapshot) {
        // ── Lock command ──────────────────────────────────────────────────────
        val isLocked = snapshot.child("isLocked").getValue(Boolean::class.java) ?: false
        if (isLocked) {
            lockDevice()
        }

        // ── Bedtime Schedule ──────────────────────────────────────────────────
        // Firebase: children/<uid>/commands/bedtime → {enabled, startHour, startMin, endHour, endMin}
        val bedtimeEnabled = snapshot.child("bedtime/enabled").getValue(Boolean::class.java) ?: false
        if (bedtimeEnabled) {
            val startH = snapshot.child("bedtime/startHour").getValue(Long::class.java)?.toInt() ?: 22
            val startM = snapshot.child("bedtime/startMin").getValue(Long::class.java)?.toInt() ?: 0
            val endH   = snapshot.child("bedtime/endHour").getValue(Long::class.java)?.toInt() ?: 7
            val endM   = snapshot.child("bedtime/endMin").getValue(Long::class.java)?.toInt() ?: 0
            scheduleBedtimeLock(startH, startM, endH, endM)
        }

        // ── YouTube Safe Mode ─────────────────────────────────────────────────
        val ytSafe = snapshot.child("youtubeSafeMode").getValue(Boolean::class.java) ?: false
        // Broadcast to UnifiedBlockerService
        sendBroadcast(Intent("com.rasel.RasFocus.YOUTUBE_SAFE_MODE").apply {
            putExtra("enabled", ytSafe)
            setPackage(packageName)
        })

        // ── Screenshot command ────────────────────────────────────────────────
        val takeScreenshot = snapshot.child("takeScreenshot").getValue(Boolean::class.java) ?: false
        if (takeScreenshot) {
            triggerScreenshot()
            // Command consume করো
            snapshot.ref.child("takeScreenshot").setValue(false)
        }

        // ── Screen time limit ─────────────────────────────────────────────────
        val limitMin = snapshot.child("screenTimeLimit").getValue(Long::class.java)
        if (limitMin != null) {
            // Broadcast to main app (ViewModel শুনবে)
            sendBroadcast(Intent("com.rasel.RasFocus.SCREEN_TIME_LIMIT").apply {
                putExtra("limitMinutes", limitMin.toInt())
                setPackage(packageName)
            })
        }

        // ── Emergency SOS reply ───────────────────────────────────────────────
        val sosReply = snapshot.child("sosReply").getValue(String::class.java)
        if (!sosReply.isNullOrEmpty()) {
            showParentReply(sosReply)
            snapshot.ref.child("sosReply").removeValue()
        }
    }

    private fun lockDevice() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            dpm.lockNow()
            Log.i(SERVICES_TAG, "Device locked by parent command")
        } catch (e: SecurityException) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            Log.w(SERVICES_TAG, "Device admin not granted, sent to home instead")
        }
    }

    // ── Bedtime Schedule Lock ────────────────────────────────────────────────
    // AlarmManager দিয়ে রাতের শুরু + সকালের শেষ দুটো alarm set করে।
    // alarm fire হলে BedtimeReceiver lockDevice() call করে।
    private fun scheduleBedtimeLock(startH: Int, startM: Int, endH: Int, endM: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        fun nextAlarm(hour: Int, min: Int): Long {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, min)
            cal.set(java.util.Calendar.SECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        // Bedtime START — lock device
        val startPi = PendingIntent.getBroadcast(
            this, 7001,
            Intent("com.rasel.RasFocus.BEDTIME_LOCK").setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Bedtime END — unlock (clear isLocked flag in Firebase)
        val endPi = PendingIntent.getBroadcast(
            this, 7002,
            Intent("com.rasel.RasFocus.BEDTIME_UNLOCK").setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setRepeating(AlarmManager.RTC_WAKEUP, nextAlarm(startH, startM), AlarmManager.INTERVAL_DAY, startPi)
            am.setRepeating(AlarmManager.RTC_WAKEUP, nextAlarm(endH, endM),   AlarmManager.INTERVAL_DAY, endPi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, nextAlarm(startH, startM), startPi)
            am.set(AlarmManager.RTC_WAKEUP, nextAlarm(endH, endM),   endPi)
        }
        Log.i(SERVICES_TAG, "Bedtime schedule: lock at $startH:$startM, unlock at $endH:$endM")
    }

    private fun triggerScreenshot() {
        val code = projectionResultCode
        val data = projectionResultData
        if (code != null && code == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startCapture(this, code, data)
            Log.i(SERVICES_TAG, "Screenshot triggered by parent command")
        } else {
            // Permission নেই — parent-কে Firebase-এ জানাও
            if (childUid.isNotEmpty()) {
                db.child("children/$childUid/alerts").push()
                    .setValue(mapOf(
                        "type"      to "Screenshot permission required",
                        "message"   to "Open RasFocus app to grant screen capture permission",
                        "timestamp" to System.currentTimeMillis()
                    ))
            }
            Log.w(SERVICES_TAG, "Screenshot requested but no projection permission available")
        }
    }

    private fun showParentReply(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_COMMANDS)
            .setContentTitle("💬 Message from Parent")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(9199, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commandListener?.let { deviceRef?.removeEventListener(it) }
        if (childUid.isNotEmpty()) {
            db.child("children/$childUid/isOnline").setValue(false)
            db.child("children/$childUid/lastSeen").setValue(ServerValue.TIMESTAMP)
        }
        try { unregisterReceiver(screenshotReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. AppUsageSyncService
//    প্রতি 15 মিনিটে child ফোনের per-app usage stats collect করে
//    Firebase Realtime Database এ push করে।
//    Parent app এই data পড়ে chart/list দেখায়।
//
//    Firebase path:
//      children/<childUid>/usageStats/today/<packageName> →
//        { appName, totalMinutes, lastUsed, iconBase64? }
//      children/<childUid>/usageStats/summary →
//        { totalMinutesToday, updatedAt }
// ─────────────────────────────────────────────────────────────────────────────
class AppUsageSyncService : Service() {

    companion object {
        const val NOTIF_ID    = 9104
        const val SYNC_INTERVAL_MS = 15 * 60 * 1000L  // 15 মিনিট

        fun start(context: Context) {
            val intent = Intent(context, AppUsageSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }
    }

    private val db       by lazy { Firebase.database.reference }
    private val childUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val handler  = Handler(Looper.getMainLooper())

    private val syncRunnable = object : Runnable {
        override fun run() {
            syncUsageStats()
            handler.postDelayed(this, SYNC_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this, "usage_sync_channel", "App Usage Sync", NotificationManager.IMPORTANCE_MIN)
        val notif = buildForegroundNotif(this, "usage_sync_channel",
            "RasFocus+ Monitoring", "Tracking screen time")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(NOTIF_ID, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // সাথে সাথে একবার sync করো, তারপর 15 মিনিট পরপর
        handler.post(syncRunnable)
        return START_STICKY
    }

    private fun syncUsageStats() {
        if (childUid.isEmpty()) return
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val pm  = packageManager

        val now      = System.currentTimeMillis()
        val midnight = run {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, now)
            ?: return

        // System / launcher apps বাদ দাও
        val excluded = setOf(
            "com.android.systemui", "com.android.launcher", "com.android.launcher3",
            "com.sec.android.app.launcher", "com.miui.home", "com.huawei.android.launcher",
            "android", "com.android.settings"
        )

        var totalMs = 0L
        val appData = mutableMapOf<String, Any>()

        stats
            .filter { it.totalTimeInForeground > 60_000L } // কমপক্ষে 1 মিনিট
            .filter { it.packageName !in excluded }
            .sortedByDescending { it.totalTimeInForeground }
            .take(30) // top 30 apps
            .forEach { stat ->
                totalMs += stat.totalTimeInForeground
                val appName = try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(stat.packageName, 0)
                    ).toString()
                } catch (e: Exception) { stat.packageName }

                // package name এ "/" থাকলে Firebase key হিসেবে invalid — replace করো
                val safeKey = stat.packageName.replace(".", "_").replace("/", "_")
                appData[safeKey] = mapOf(
                    "packageName"   to stat.packageName,
                    "appName"       to appName,
                    "totalMinutes"  to (stat.totalTimeInForeground / 60_000L).toInt(),
                    "lastUsed"      to stat.lastTimeUsed
                )
            }

        val totalMins = (totalMs / 60_000L).toInt()
        val ref = db.child("children/$childUid/usageStats")

        // Today's per-app breakdown
        ref.child("today").setValue(appData)

        // Summary (parent screen এ quick view এর জন্য)
        ref.child("summary").setValue(mapOf(
            "totalMinutesToday" to totalMins,
            "updatedAt"         to now
        ))

        // Daily history — weekly report এর জন্য
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(midnight))
        ref.child("history/$dateKey").setValue(mapOf(
            "totalMinutes" to totalMins,
            "date"         to dateKey
        ))

        Log.i(SERVICES_TAG, "AppUsageSync: pushed $totalMins mins total, ${appData.size} apps")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(syncRunnable)
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. BedtimeReceiver — AlarmManager থেকে bedtime lock/unlock trigger হলে
//    এখানে আসে।
// ─────────────────────────────────────────────────────────────────────────────
class BedtimeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        when (intent.action) {
            "com.rasel.RasFocus.BEDTIME_LOCK" -> {
                try { dpm.lockNow() } catch (e: SecurityException) {
                    context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
                // Firebase এ isLocked = true
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                Firebase.database.reference.child("children/$uid/commands/isLocked").setValue(true)
            }
            "com.rasel.RasFocus.BEDTIME_UNLOCK" -> {
                // Bedtime শেষ — Firebase এ isLocked = false
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                Firebase.database.reference.child("children/$uid/commands/isLocked").setValue(false)
            }
        }
    }
}

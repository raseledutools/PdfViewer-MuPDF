package com.rasel.RasFocus.combo.selfcontrol

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rasel.RasFocus.DataManager
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.*
import kotlin.math.*

// ─────────────────────────────────────────
// DEEP STUDY — Color System (Dark Theme)
// Background: Deep Navy  |  Text: Crisp White
// ─────────────────────────────────────────
val DClrBg           = Color(0xFF0B1220)                    // Deep navy background
val DClrSurface      = Color(0xFF141E30)                    // Card surface — distinct from bg
val DClrSurface2     = Color(0xFF1C2840)                    // Slightly lighter card variant
val DClrTeal         = Color(0xFF00C6B2)                    // Primary accent — vibrant teal
val DClrTealDark     = Color(0xFF009E8C)                    // Pressed / darker teal
val DClrWhite        = Color(0xFFFFFFFF)                    // Pure white for timer digits
val DClrDark         = Color(0xFFF0F4FF)                    // Primary text — bright near-white
val DClrGray         = Color(0xFF8090A8)                    // Secondary / hint text
val DClrBorderMuted  = Color(0xFF2A3A52)                    // Card borders
val DClrPillBg       = Color(0xFF1C2840)                    // Pill / toggle track bg
val DClrPillSelectedBg = Color(0xFF253350)                  // Selected pill state
val DClrGlassBorder  = Color(0xFF2E4060)                    // Glass card border
val DClrBadgeTeal    = Color(0xFF00C6B2).copy(alpha = 0.18f) // Teal icon badge bg
val DClrBadgeGreen   = Color(0xFF22C55E).copy(alpha = 0.18f) // Green icon badge bg
val DClrBadgePurple  = Color(0xFF8B5CF6).copy(alpha = 0.18f) // Purple icon badge bg
val DClrBadgeAmber   = Color(0xFFF59E0B).copy(alpha = 0.18f) // Amber icon badge bg
val DClrRed          = Color(0xFFFF4E4E)                    // Error / stop
val DClrGreen        = Color(0xFF22C55E)                    // Success / break
val DClrAmber        = Color(0xFFF59E0B)                    // Warning / strict

data class BlockItem(val name: String)

// ─────────────────────────────────────────
// PERMISSION HELPERS
// ─────────────────────────────────────────

private fun hasUsageStatsPermission(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

fun hasOverlayPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

// ─────────────────────────────────────────
// ALLOW-LIST BLOCKER (USAGE STATS)
// ─────────────────────────────────────────
// FIX: this section header existed with NO implementation under it at all —
// isFocusMode only ran a countdown timer, nothing ever actually enforced the
// allow-list against other apps. That's the reported bug ("everything
// except the allow list is supposed to auto-block, but it doesn't") — there
// was simply nothing here to do the blocking. Implemented below: a
// foreground Service that polls UsageStatsManager for the current
// foreground app while a focus session is active, and shows a full-screen
// overlay (WindowManager, same general mechanism as FloatingStopwatch
// above, but full-screen and touch-capturing rather than a small draggable
// widget) whenever that app isn't RasFocus itself, the device launcher, the
// default dialer (emergency calls must never be blockable), or in
// DataManager.dsAllowAppList.
class DeepStudyBlockerService : android.app.Service() {

    companion object {
        private const val CHANNEL_ID = "deep_study_blocker"
        private const val NOTIF_ID = 8891
        @Volatile private var running = false

        fun start(context: Context) {
            if (running) return
            val intent = Intent(context, DeepStudyBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeepStudyBlockerService::class.java))
        }
    }

    private var pollJob: Job? = null
    private var wm: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var lastBlockedPkg: String? = null

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL_ID, "Deep Study Blocker", android.app.NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notif = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Deep Study focus session active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollJob == null) {
            pollJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    checkForegroundApp()
                    delay(1500)
                }
            }
        }
        return android.app.Service.START_STICKY
    }

    private fun homePackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun dialerPackage(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
        } else null
    } catch (_: Exception) { null }

    private fun currentForegroundPackage(): String? {
        if (!hasUsageStatsPermission(this)) return null
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10_000, end)
        val event = android.app.usage.UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) lastPkg = event.packageName
        }
        return lastPkg
    }

    private suspend fun checkForegroundApp() {
        val fg = currentForegroundPackage() ?: return
        val allowed = DataManager.dsAllowAppList.toSet()
        // Always-exempt: this app itself, the launcher, the dialer (emergency
        // calls), and core system UI — never trap the user with no way out.
        val exempt = setOfNotNull(packageName, homePackage(), dialerPackage(), "com.android.systemui", "android")
        val isOk = fg in allowed || fg in exempt
        withContext(Dispatchers.Main) {
            if (!isOk) showOverlay(fg) else hideOverlay()
        }
    }

    private fun showOverlay(blockedPkg: String) {
        if (overlayView != null && lastBlockedPkg == blockedPkg) return // already showing for this app
        hideOverlay()
        lastBlockedPkg = blockedPkg
        if (!hasOverlayPermission(this)) return // can't show without permission, silently skip rather than crash

        val mgr = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = mgr

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(blockedPkg, 0)).toString()
        } catch (_: Exception) { blockedPkg }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#F00B1220"))
            isClickable = true; isFocusable = true
        }
        val icon = android.widget.TextView(this).apply {
            text = "🔒"; textSize = 56f; gravity = Gravity.CENTER
        }
        val title = android.widget.TextView(this).apply {
            text = "Deep Study চলছে"
            textSize = 22f; setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 8)
        }
        val subtitle = android.widget.TextView(this).apply {
            text = "$label allow-list এ নেই — focus session চলাকালীন ব্লক করা আছে"
            textSize = 15f; setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER; setPadding(64, 0, 64, 32)
        }
        val backBtn = android.widget.TextView(this).apply {
            text = "  ← Deep Study তে ফিরে যান  "
            textSize = 15f; setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#0EA5E9"))
            setPadding(32, 20, 32, 20)
            setOnClickListener {
                val i = packageManager.getLaunchIntentForPackage(packageName)
                i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i?.let { startActivity(it) }
            }
        }
        root.addView(icon); root.addView(title); root.addView(subtitle); root.addView(backBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // Deliberately NOT FLAG_NOT_FOCUSABLE — this must capture touches
            // and fully block interaction with whatever's underneath, unlike
            // FloatingStopwatch's small pass-through widget above.
            // 0 = no flags: focusable and touch-modal, so this actually
            // captures input instead of passing touches through underneath
            // (unlike FloatingStopwatch's FLAG_NOT_FOCUSABLE widget above).
            0,
            PixelFormat.TRANSLUCENT
        )
        try {
            mgr.addView(root, params)
            overlayView = root
        } catch (_: Exception) { overlayView = null }
    }

    private fun hideOverlay() {
        overlayView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        lastBlockedPkg = null
    }

    override fun onDestroy() {
        pollJob?.cancel(); pollJob = null
        hideOverlay()
        running = false
        super.onDestroy()
    }
}// ─────────────────────────────────────────
// FLOATING STOPWATCH
// ─────────────────────────────────────────

object FloatingStopwatch {
    private var wm: WindowManager? = null
    private var rootView: android.view.View? = null
    private var tickJob: Job? = null
    var isShowing = false; private set

    fun show(context: Context, onDismiss: () -> Unit) {
        if (isShowing) return
        val mgr = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = mgr

        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E60F172A")) // Darker overlay
                cornerRadius = 100f
            }
            setPadding(32, 16, 24, 16)
        }

        val timerTv = android.widget.TextView(context).apply {
            text = "00:00"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }

        val closeTv = android.widget.TextView(context).apply {
            text = "  ✕"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#94A3B8")) // Slate 400
            setPadding(12, 0, 0, 0)
            setOnClickListener { dismiss(onDismiss) }
        }

        root.addView(timerTv); root.addView(closeTv)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 32; y = 200 }

        var lx = 0; var ly = 0
        root.setOnTouchListener { _, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> { lx = e.rawX.toInt(); ly = e.rawY.toInt() }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x += lx - e.rawX.toInt(); params.y += e.rawY.toInt() - ly
                    lx = e.rawX.toInt(); ly = e.rawY.toInt()
                    mgr.updateViewLayout(root, params)
                }
            }; false
        }

        mgr.addView(root, params); rootView = root; isShowing = true
        var sec = 0
        val startTime = System.currentTimeMillis()
        tickJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(30)
                val elapsed = System.currentTimeMillis() - startTime
                val m = elapsed / 60000
                val s = (elapsed / 1000) % 60
                val ms = elapsed % 1000
                timerTv.text = "%02d:%02d.%03d".format(m, s, ms)
            }
        }
    }

    fun dismiss(onDismiss: (() -> Unit)? = null) {
        tickJob?.cancel(); tickJob = null
        rootView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        rootView = null; wm = null; isShowing = false
        onDismiss?.invoke()
    }
}

// ─────────────────────────────────────────
// SOUND ENGINE
// ─────────────────────────────────────────

enum class SoundType(val label: String, val emoji: String, val downloadUrl: String? = null) {
    WHITE_NOISE("White Noise", "💨"), CLASSIC_BROWN("Classic Brown", "🟤"),
    DEEP_BROWN("Deep Brown", "🐻"), WARM_BROWN("Warm Brown", "🪵"),
    HEAVY_RAIN("Heavy Rain", "🌧️"), WATERFALL("Waterfall", "🌊"),
    WIND("Wind", "🌬️"), DEEP_FOCUS("Deep Focus", "🎯"),
    SPACE_DRONE("Space Drone", "🛸"), COSMIC_BROWN("Cosmic Brown", "🌌"),
    ALPHA_NOISE_MP3("Alpha Noise", "🧠", "https://bigsoundbank.com/UPLOAD/mp3/1112.mp3"),
    BROWN_NOISE_MP3("Brown Noise (HQ)", "🎧", "https://bigsoundbank.com/UPLOAD/mp3/1111.mp3")
}

object AmbientSoundEngine {
    private var track: AudioTrack? = null
    private var genJob: Job? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private const val SR = 44100
    private const val BUF = 4096

    fun play(context: android.content.Context, type: SoundType) {
        stop()
        if (type.downloadUrl != null) {
            val file = java.io.File(context.filesDir, type.name + ".mp3")
            if (file.exists()) {
                mediaPlayer = android.media.MediaPlayer.create(context, android.net.Uri.fromFile(file))
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            }
            return
        }
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(BUF * 4).setTransferMode(AudioTrack.MODE_STREAM).build()
        track?.play()

        genJob = CoroutineScope(Dispatchers.IO).launch {
            val buf = FloatArray(BUF)
            var bL = 0f; var bR = 0f; var sampleIdx = 0L

            while (isActive) {
                for (i in 0 until BUF / 2) {
                    val w1 = (Math.random() * 2 - 1).toFloat()
                    val w2 = (Math.random() * 2 - 1).toFloat()
                    val t = sampleIdx.toDouble() / SR
                    sampleIdx++

                    val (sL, sR) = when (type) {
                        SoundType.WHITE_NOISE -> Pair(w1 * 0.3f, w2 * 0.3f)
                        SoundType.CLASSIC_BROWN -> { bL = (bL + w1 * 0.02f).coerceIn(-1f,1f); bR = (bR + w2 * 0.02f).coerceIn(-1f,1f); Pair(bL * 3.5f, bR * 3.5f) }
                        SoundType.DEEP_BROWN -> { bL = (bL * 0.998f + w1 * 0.012f).coerceIn(-1f,1f); bR = (bR * 0.998f + w2 * 0.012f).coerceIn(-1f,1f); Pair(bL * 4f, bR * 4f) }
                        SoundType.WARM_BROWN -> { bL = (bL * 0.99f + w1 * 0.025f).coerceIn(-1f,1f); bR = (bR * 0.99f + w2 * 0.025f).coerceIn(-1f,1f); Pair(bL * 3f, bR * 3f) }
                        SoundType.HEAVY_RAIN -> { val drop = if (Math.random() < 0.008) w1 * 0.5f else 0f; bL = (bL + w1 * 0.018f).coerceIn(-1f,1f); Pair(bL * 2f + drop, bL * 2f + w2 * 0.15f) }
                        SoundType.WATERFALL -> { val m = w1 * 0.5f + w2 * 0.3f; Pair(m * 0.55f, (w2 * 0.5f + w1 * 0.3f) * 0.55f) }
                        SoundType.WIND -> { bL = (bL + w1 * 0.022f).coerceIn(-1f,1f); bR = (bR + w2 * 0.022f).coerceIn(-1f,1f); val mod = (0.5f + 0.5f * sin(t * 0.3)).toFloat(); Pair(bL * mod * 3f, bR * mod * 3f) }
                        SoundType.DEEP_FOCUS -> { val drone = sin(2 * PI * 40.0 * t).toFloat() * 0.22f; bL = (bL + w1 * 0.015f).coerceIn(-1f,1f); Pair(bL * 1.8f + drone, bL * 1.8f + drone) }
                        SoundType.SPACE_DRONE -> { val d1 = sin(2 * PI * 60.0 * t).toFloat() * 0.18f; val d2 = sin(2 * PI * 90.0 * t).toFloat() * 0.09f; bL = (bL + w1 * 0.008f).coerceIn(-1f,1f); bR = (bR + w2 * 0.008f).coerceIn(-1f,1f); Pair(bL * 0.8f + d1 + d2, bR * 0.8f + d1 - d2) }
                        SoundType.COSMIC_BROWN -> { val sub = sin(2 * PI * 30.0 * t).toFloat() * 0.12f; bL = (bL * 0.9995f + w1 * 0.008f).coerceIn(-1f,1f); bR = (bR * 0.9995f + w2 * 0.008f).coerceIn(-1f,1f); Pair(bL * 4f + sub, bR * 4f + sub) }
                        else -> Pair(0f, 0f)
                    }
                    buf[i * 2] = sL.coerceIn(-1f, 1f); buf[i * 2 + 1] = sR.coerceIn(-1f, 1f)
                }
                track?.write(buf, 0, BUF, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun downloadSound(context: android.content.Context, type: SoundType, onComplete: () -> Unit, onError: () -> Unit) {
        if (type.downloadUrl == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL(type.downloadUrl)
                val file = java.io.File(context.filesDir, type.name + ".mp3")
                url.openStream().use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError() }
            }
        }
    }

    fun stop() { 
        genJob?.cancel(); genJob = null
        track?.stop(); track?.release(); track = null 
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
    }
}

// ─────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Deep_study() {
    val context = LocalContext.current

    // ── Session state ────────────────────────────────────────────────────
    var isFocusMode    by remember { mutableStateOf(false) }
    var isBreak        by remember { mutableStateOf(false) }
    var focusMin       by remember { mutableIntStateOf(25) }
    var restMin        by remember { mutableIntStateOf(5) }
    var totalSessions  by remember { mutableIntStateOf(4) }
    var currentSession by remember { mutableIntStateOf(1) }
    var timeLeftMillis by remember { mutableLongStateOf(25 * 60 * 1000L) }
    var isStrict       by remember { mutableStateOf(com.rasel.RasFocus.DataManager.isDeepStudyStrict) }
    var sessionDone    by remember { mutableStateOf(false) }  // all sessions complete flag

    var chkSound  by remember { mutableStateOf(false) }
    var chkFloat  by remember { mutableStateOf(false) }
    var soundType by remember { mutableStateOf(SoundType.WHITE_NOISE) }

    val allowWebs = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.dsAllowWebList.map { BlockItem(it) }) } }
    val allowApps = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.dsAllowAppList.map { BlockItem(it) }) } }

    var showBottomSheet by remember { mutableStateOf(false) }

    // ── Helpers ──────────────────────────────────────────────────────────
    fun stopEverything() {
        AmbientSoundEngine.stop()
        FloatingStopwatch.dismiss()
        DeepStudyBlockerService.stop(context)
    }

    // Reset timer when focusMin changes (only when idle)
    LaunchedEffect(focusMin) {
        if (!isFocusMode && !isBreak) {
            timeLeftMillis = focusMin * 60 * 1000L
        }
    }

    // ── Master timer coroutine — single loop, no LaunchedEffect races ────
    // Runs once. Reads mutable state via snapshot reads inside the loop.
    // Avoids the bug where LaunchedEffect(isFocusMode, isBreak) restarts
    // after setting isBreak=true while timeLeftMillis is already 0, causing
    // the break timer to skip immediately.
    LaunchedEffect(Unit) {
        while (true) {
            // Wait until a session or break starts
            if (!isFocusMode && !isBreak) {
                delay(100)
                continue
            }

            val isCurrentlyBreak = isBreak
            val durationMillis   = if (isCurrentlyBreak) restMin * 60 * 1000L else focusMin * 60 * 1000L
            val targetTime       = System.currentTimeMillis() + timeLeftMillis

            // Tick until time runs out OR session is manually stopped
            while (true) {
                delay(16) // ~60fps tick
                val remaining = targetTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    timeLeftMillis = 0L
                    break
                }
                // Manual stop check
                if (!isFocusMode && !isBreak) {
                    timeLeftMillis = focusMin * 60 * 1000L
                    break
                }
                timeLeftMillis = remaining
            }

            // Only process auto-advance if session wasn't manually stopped
            val stillActive = if (isCurrentlyBreak) isBreak else isFocusMode
            if (!stillActive) continue  // manual stop — loop back to wait

            if (isCurrentlyBreak) {
                // Break finished → next focus session
                isBreak = false
                isFocusMode = true
                currentSession++
                timeLeftMillis = focusMin * 60 * 1000L
            } else {
                // Focus session finished
                com.rasel.RasFocus.DataManager.totalFocusTimeMillis += (focusMin * 60 * 1000L)
                com.rasel.RasFocus.DataManager.totalSessions++

                if (currentSession < totalSessions) {
                    // More sessions → start break
                    isFocusMode = false
                    isBreak = true
                    timeLeftMillis = restMin * 60 * 1000L
                } else {
                    // ALL SESSIONS DONE — auto stop everything
                    isFocusMode = false
                    isBreak = false
                    timeLeftMillis = focusMin * 60 * 1000L
                    currentSession = 1
                    sessionDone = true
                    stopEverything()
                    android.widget.Toast.makeText(
                        context,
                        "🎉 All sessions complete! Great work!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Side effects — start/stop services when session state changes ────
    LaunchedEffect(isFocusMode, isBreak) {
        when {
            isFocusMode -> DeepStudyBlockerService.start(context)
            !isFocusMode && !isBreak -> DeepStudyBlockerService.stop(context)
        }
        when {
            chkSound && (isFocusMode || isBreak) -> AmbientSoundEngine.play(context, soundType)
            !isFocusMode && !isBreak -> AmbientSoundEngine.stop()
        }
        when {
            chkFloat && (isFocusMode || isBreak) && hasOverlayPermission(context) ->
                FloatingStopwatch.show(context) { chkFloat = false }
            !isFocusMode && !isBreak -> FloatingStopwatch.dismiss()
        }
    }

    // Restart sound if user changes type mid-session
    LaunchedEffect(soundType) {
        if (chkSound && (isFocusMode || isBreak)) {
            AmbientSoundEngine.play(context, soundType)
        }
    }

    // Session complete celebration toast already shown above;
    // reset the flag so it doesn't re-trigger on recompose
    LaunchedEffect(sessionDone) {
        if (sessionDone) sessionDone = false
    }

    DisposableEffect(Unit) {
        onDispose { stopEverything() }
    }

    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF0F1B33))))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Section
                TimerHeroCard(
                    isFocusMode = isFocusMode,
                    isBreak = isBreak,
                    timeLeftMillis = timeLeftMillis,
                    currentSession = currentSession,
                    totalSessions = totalSessions
                )

                // Start / Stop button
                StartStopButton(
                    isActive = isFocusMode || isBreak,
                    onClick = {
                        if (isFocusMode || isBreak) {
                            if (isStrict) {
                                android.widget.Toast.makeText(context, "Strict Mode active! Cannot stop early.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                isFocusMode = false
                                isBreak = false
                                timeLeftMillis = focusMin * 60 * 1000L
                                currentSession = 1
                            }
                        } else {
                            if (!hasUsageStatsPermission(context)) {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } else {
                                isFocusMode = true
                            }
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                SectionCard(title = "Session Setup", icon = Icons.Default.Timer) {
                    TimerSetupRow("Focus Time", focusMin, 5, 120, 5, !isFocusMode && !isBreak) { focusMin = it }
                    TimerSetupRow("Rest Time", restMin, 1, 30, 1, !isFocusMode && !isBreak) { restMin = it }
                    TimerSetupRow("Total Sessions", totalSessions, 1, 10, 1, !isFocusMode && !isBreak) { totalSessions = it }
                }

                SectionCard(title = "Focus Aids", icon = Icons.Default.Headphones) {
                    SoundRow(
                        checked = chkSound, enabled = true, soundType = soundType, context = context,
                        onCheckedChange = { chkSound = it }, onSoundTypeChange = { soundType = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    FloatRow(
                        checked = chkFloat, enabled = true, context = context,
                        onCheckedChange = { newVal ->
                            if (newVal && !hasOverlayPermission(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                            } else {
                                chkFloat = newVal; if (!newVal) FloatingStopwatch.dismiss()
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    StrictRow(
                        checked = isStrict, enabled = !isFocusMode && !isBreak,
                        onCheckedChange = { 
                            isStrict = it
                            com.rasel.RasFocus.DataManager.isDeepStudyStrict = it 
                        }
                    )
                }

                // Show basic requirement warning if missing Usage Stats
                if (!hasUsageStatsPermission(context)) {
                    PermBanner(
                        color = DClrBadgeTeal, tint = DClrTeal,
                        text = "Allow-list blocking requires Usage Stats permission.",
                        btnText = "Enable", onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
                }

                AllowListCard(
                    appCount = allowApps.size, siteCount = allowWebs.size,
                    enabled = !isFocusMode && !isBreak, onClick = { showBottomSheet = true }
                )
                Spacer(Modifier.height(40.dp))
            }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            modifier = Modifier.fillMaxHeight(0.9f),
            containerColor = Color(0xFF0F1B33),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            BlocklistPickerSheet(
                onClose = { showBottomSheet = false },
                onSave = { selectedApps, selectedSites ->
                    allowApps.clear(); allowApps.addAll(selectedApps.map { BlockItem(it) })
                    DataManager.dsAllowAppList = selectedApps
                    allowWebs.clear(); allowWebs.addAll(selectedSites.map { BlockItem(it) })
                    DataManager.dsAllowWebList = selectedSites
                    showBottomSheet = false
                },
                initialApps  = allowApps.map { it.name }, initialSites = allowWebs.map { it.name }
            )
        }
    }
}

// ─────────────────────────────────────────
// SUB-COMPOSABLES
// ─────────────────────────────────────────

@Composable
private fun TimerHeroCard(
    isFocusMode: Boolean, isBreak: Boolean,
    timeLeftMillis: Long, currentSession: Int, totalSessions: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isFocusMode) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_anim"
    )

    Box(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing pulse
        Box(
            Modifier
                .size(260.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    if (isBreak) Color(0xFF22C55E).copy(alpha = 0.15f)
                    else DClrTeal.copy(alpha = 0.15f)
                )
        )
        
        // Inner Circular Timer
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isBreak) listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                                 else listOf(DClrTeal, DClrTealDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(
                        text = "%02d:%02d".format(timeLeftMillis / 60000, (timeLeftMillis / 1000) % 60),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DClrWhite,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = ".%02d".format((timeLeftMillis % 1000) / 10),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = DClrWhite.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isBreak) "Break Time ☕" else "Session $currentSession of $totalSessions",
                    fontSize = 15.sp,
                    color = DClrWhite.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                
                if (isFocusMode) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(DClrAmber))
                        Text(
                            "Allow-List Mode",
                            fontSize = 11.sp, color = DClrWhite, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartStopButton(isActive: Boolean, onClick: () -> Unit) {
    val bg = when {
        isActive -> DClrRed
        else     -> DClrTeal
    }
    val label = when {
        isActive -> "STOP POMODORO"
        else     -> "START POMODORO"
    }
    val icon = when {
        isActive -> Icons.Default.Stop
        else     -> Icons.Default.PlayArrow
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(60.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = DClrWhite, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DClrWhite, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    com.rasel.RasFocus.ui.theme.PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = DClrSurface,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(DClrBadgeTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = DClrTeal, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DClrDark)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundRow(
    checked: Boolean, enabled: Boolean, soundType: SoundType, context: android.content.Context,
    onCheckedChange: (Boolean) -> Unit, onSoundTypeChange: (SoundType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isDownloading by remember(soundType) { mutableStateOf(false) }
    val isDownloaded = remember(soundType, isDownloading) { 
        soundType.downloadUrl == null || java.io.File(context.filesDir, soundType.name + ".mp3").exists()
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(12.dp))
        Text("Ambient Sound", fontSize = 15.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        
        if (checked) {
            if (!isDownloaded) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DClrTeal, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        isDownloading = true
                        AmbientSoundEngine.downloadSound(context, soundType, 
                            onComplete = { isDownloading = false; if (checked) AmbientSoundEngine.play(context, soundType) },
                            onError = { isDownloading = false; android.widget.Toast.makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT).show() }
                        )
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Download, null, tint = DClrTeal)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DClrPillBg,
                    modifier = Modifier.menuAnchor()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clickable { if (enabled) expanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(soundType.emoji, fontSize = 14.sp)
                        Text(soundType.label, fontSize = 13.sp, color = DClrDark, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = DClrGray, modifier = Modifier.size(18.dp))
                    }
                }
                ExposedDropdownMenu(
                    expanded = expanded, 
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DClrSurface)
                ) {
                    SoundType.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.emoji}  ${s.label}", fontSize = 14.sp, color = DClrDark) },
                            onClick = { onSoundTypeChange(s); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatRow(checked: Boolean, enabled: Boolean, context: Context, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(12.dp))
        Text("Floating Stopwatch", fontSize = 15.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (!hasOverlayPermission(context)) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }) {
                Text("Grant", fontSize = 13.sp, color = DClrAmber, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StrictRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Strict Mode", fontSize = 15.sp, color = if (enabled) DClrDark else DClrGray, fontWeight = FontWeight.Medium)
            Text("Cannot stop or unlock until session ends. Blocks all apps except allowed apps, Phone, and SMS.", fontSize = 11.sp, color = DClrGray, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PermBanner(color: Color, tint: Color, text: String, btnText: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = DClrDark, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(btnText, fontSize = 13.sp, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AllowListCard(appCount: Int, siteCount: Int, enabled: Boolean, onClick: () -> Unit) {
    com.rasel.RasFocus.ui.theme.PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (enabled) DClrTeal.copy(alpha = 0.1f) else DClrSurface,
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(DClrBadgeGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = DClrGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow List", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DClrDark)
                Spacer(Modifier.height(2.dp))
                Text("$appCount Apps Allowed · $siteCount Sites Allowed", fontSize = 13.sp, color = DClrGray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = DClrGray)
        }
    }
}

// ─────────────────────────────────────────
// BOTTOM SHEET
// ─────────────────────────────────────────

@Composable
fun BlocklistPickerSheet(
    onClose: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit,
    initialApps: List<String>,
    initialSites: List<String>
) {
    val context = LocalContext.current
    val tempApps = remember { mutableStateListOf<String>().apply { addAll(initialApps) } }

    // ── App loading — system + user apps, fast ───────────────────────────
    data class AppInfo(val name: String, val pkg: String, val icon: android.graphics.drawable.Drawable)

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Popular system/google apps to always include
    val PRIORITY_PKGS = setOf(
        "com.android.chrome",
        "com.google.android.youtube",
        "com.google.android.gm",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.maps",
        "com.google.android.apps.photos",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.android.messaging",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.settings",
        "com.samsung.android.settings",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.whatsapp",
        "com.twitter.android",
        "org.telegram.messenger",
        "com.snapchat.android",
        "com.tiktok.musically",
        "com.spotify.music",
        "com.netflix.mediaclient",
        "com.amazon.mShop.android.shopping",
        "com.google.android.apps.youtube.music",
        "com.microsoft.teams",
        "com.slack",
        "com.discord",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val allInstalled = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            // Separate user apps + priority system apps
            val userApps = allInstalled
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }

            val systemApps = allInstalled
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    && it.packageName in PRIORITY_PKGS }

            val combined = (systemApps + userApps)
                .distinctBy { it.packageName }
                .mapNotNull { info ->
                    try {
                        AppInfo(
                            name = pm.getApplicationLabel(info).toString(),
                            pkg  = info.packageName,
                            icon = pm.getApplicationIcon(info.packageName)
                        )
                    } catch (_: Exception) { null }
                }
                .sortedWith(compareBy(
                    { it.packageName !in PRIORITY_PKGS }, // priority apps first
                    { it.name.lowercase() }
                ))

            withContext(Dispatchers.Main) {
                allApps = combined
                isLoading = false
            }
        }
    }

    val filtered = remember(allApps, searchQuery) {
        if (searchQuery.isEmpty()) allApps
        else allApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().background(DClrBg)) {

        // ── Header ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(DClrSurface).padding(horizontal = 20.dp, vertical = 14.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Column {
                Text("Allow List", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DClrDark)
                Text(
                    "${tempApps.size} app${if (tempApps.size != 1) "s" else ""} selected",
                    fontSize = 13.sp, color = DClrTeal
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tempApps.isNotEmpty()) {
                    TextButton(
                        onClick = { tempApps.clear() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp, color = DClrRed, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(DClrPillBg)
                ) {
                    Icon(Icons.Default.Close, null, tint = DClrDark, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Search bar ───────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps…", color = DClrGray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = DClrGray, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, null, tint = DClrGray, modifier = Modifier.size(18.dp))
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DClrDark,
                unfocusedTextColor = DClrDark,
                focusedContainerColor = DClrSurface,
                unfocusedContainerColor = DClrSurface,
                focusedBorderColor = DClrTeal,
                unfocusedBorderColor = DClrBorderMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // ── Launcher always-allowed banner ───────────────────────────────
        val defaultLauncher = remember {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .apply { addCategory(android.content.Intent.CATEGORY_HOME) }
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName ?: ""
        }
        if (defaultLauncher.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DClrTeal.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, null, tint = DClrTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Launcher সবসময় allow থাকবে",
                    fontSize = 12.sp, color = DClrTeal, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── App list ──────────────────────────────────────────────────────
        if (isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DClrTeal, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Loading apps…", color = DClrGray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = filtered,
                    key = { it.pkg }
                ) { app ->
                    val isLauncher = app.pkg == defaultLauncher
                    val isSelected = tempApps.contains(app.pkg) || isLauncher
                    val isPriority = app.pkg in PRIORITY_PKGS

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> DClrTeal.copy(alpha = 0.12f)
                                    else       -> DClrSurface
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) DClrTeal.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isLauncher) {
                                if (isSelected) tempApps.remove(app.pkg)
                                else tempApps.add(app.pkg)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App icon
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    app.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DClrDark,
                                    maxLines = 1
                                )
                                if (isLauncher) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .background(DClrTeal.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("Launcher", fontSize = 9.sp, color = DClrTeal, fontWeight = FontWeight.Bold)
                                    }
                                } else if (isPriority && (app.pkg.startsWith("com.android") || app.pkg.startsWith("com.google") || app.pkg.startsWith("com.samsung"))) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .background(DClrBadgePurple, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("System", fontSize = 9.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                app.pkg,
                                fontSize = 11.sp,
                                color = DClrGray,
                                maxLines = 1
                            )
                        }
                        // Checkbox
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) DClrTeal else DClrPillBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Save button ──────────────────────────────────────────────────
        Button(
            onClick = { onSave(tempApps.toList(), emptyList()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DClrTeal),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Save  •  ${tempApps.size} apps",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}


// ─────────────────────────────────────────
// HELPER COMPOSABLES
// ─────────────────────────────────────────

@Composable
fun TimerSetupRow(label: String, value: Int, minVal: Int, maxVal: Int, step: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(DClrPillBg).padding(2.dp)
        ) {
            IconButton(
                onClick = { if (enabled && value > minVal) onValueChange(value - step) },
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if(enabled) DClrSurface else Color.Transparent)
            ) { Icon(Icons.Default.Remove, null, tint = if (enabled) DClrDark else DClrGray, modifier = Modifier.size(18.dp)) }
            
            Text(
                "$value",
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, color = DClrDark
            )
            
            IconButton(
                onClick = { if (enabled && value < maxVal) onValueChange(value + step) },
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if(enabled) DClrSurface else Color.Transparent)
            ) { Icon(Icons.Default.Add, null, tint = if (enabled) DClrDark else DClrGray, modifier = Modifier.size(18.dp)) }
        }
    }
}

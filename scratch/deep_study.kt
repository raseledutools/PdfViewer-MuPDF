package com.rasel.RasFocus.selfcontrol

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
import kotlinx.coroutines.*
import kotlin.math.*

// ─────────────────────────────────────────
// PREMIUM COLORS
// ─────────────────────────────────────────
val DClrTeal     = Color(0xFF0EA5E9) // Sky Blue
val DClrTealDark = Color(0xFF0284C7)
val DClrWhite    = Color(0xFFFFFFFF)
val DClrDark     = Color(0xFF0F172A) // Slate 900
val DClrGray     = Color(0xFF64748B) // Slate 500
val DClrBg       = Color(0xFFF8FAFC) // Slate 50
val DClrSurface  = Color(0xFFFFFFFF)
val DClrRed      = Color(0xFFEF4444)
val DClrGreen    = Color(0xFF10B981) // Emerald
val DClrAmber    = Color(0xFFF59E0B)

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

object AllowListBlocker {
    private var job: Job? = null
    private val ALWAYS_ALLOWED = setOf(
        "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
        "com.google.android.apps.nexuslauncher", "com.miui.home", "com.huawei.android.launcher",
        "com.sec.android.app.launcher", "com.oneplus.launcher", "com.oppo.launcher",
        "com.realme.launcher", "com.android.systemui", "android"
    )

    fun start(context: Context, allowedPackages: Set<String>) {
        stop()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ownPkg = context.packageName

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3000, now)
                val fg = stats
                    ?.filter { it.lastTimeUsed > now - 1500 }
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName ?: ""

                val isBlocked = fg.isNotEmpty()
                    && fg !in ALWAYS_ALLOWED
                    && fg != ownPkg
                    && fg !in allowedPackages

                if (isBlocked) {
                    withContext(Dispatchers.Main) {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
                delay(800)
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}

// ─────────────────────────────────────────
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
        tickJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) { delay(1000); sec++; timerTv.text = "%02d:%02d".format(sec / 60, sec % 60) }
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

enum class SoundType(val label: String, val emoji: String) {
    WHITE_NOISE("White Noise", "🌫️"), CLASSIC_BROWN("Classic Brown", "🟤"),
    DEEP_BROWN("Deep Brown", "🪵"), WARM_BROWN("Warm Brown", "☕"),
    HEAVY_RAIN("Heavy Rain", "🌧️"), WATERFALL("Waterfall", "💧"),
    WIND("Wind", "🌬️"), DEEP_FOCUS("Deep Focus", "🎯"),
    SPACE_DRONE("Space Drone", "🚀"), COSMIC_BROWN("Cosmic Brown", "🌌")
}

object AmbientSoundEngine {
    private var track: AudioTrack? = null
    private var genJob: Job? = null
    private const val SR = 44100
    private const val BUF = 4096

    fun play(type: SoundType) {
        stop()
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
                    }
                    buf[i * 2] = sL.coerceIn(-1f, 1f); buf[i * 2 + 1] = sR.coerceIn(-1f, 1f)
                }
                track?.write(buf, 0, BUF, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop() { genJob?.cancel(); genJob = null; track?.stop(); track?.release(); track = null }
}

// ─────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Deep_study() {
    val context = LocalContext.current

    var activeSubTab      by remember { mutableIntStateOf(0) }
    var isFocusMode       by remember { mutableStateOf(false) }
    var isBreak           by remember { mutableStateOf(false) }
    var focusMin          by remember { mutableIntStateOf(25) }
    var restMin           by remember { mutableIntStateOf(5) }
    var totalSessions     by remember { mutableIntStateOf(4) }
    var currentSession    by remember { mutableIntStateOf(1) }
    var timeLeftSeconds   by remember { mutableIntStateOf(25 * 60) }
    
    var chkSound          by remember { mutableStateOf(false) }
    var chkFloat          by remember { mutableStateOf(false) }
    var soundType         by remember { mutableStateOf(SoundType.WHITE_NOISE) }

    val allowWebs = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.dsAllowWebList.map { BlockItem(it) }) } }
    val allowApps = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.dsAllowAppList.map { BlockItem(it) }) } }

    var showBottomSheet   by remember { mutableStateOf(false) }

    // Sync timer logic
    LaunchedEffect(focusMin) {
        if (!isFocusMode && !isBreak) {
            timeLeftSeconds = focusMin * 60
        }
    }

    LaunchedEffect(isFocusMode, isBreak) {
        if (isFocusMode) {
            while (timeLeftSeconds > 0 && isFocusMode) {
                delay(1000)
                timeLeftSeconds--
            }
            if (timeLeftSeconds <= 0 && isFocusMode) {
                if (currentSession < totalSessions) {
                    isFocusMode = false
                    isBreak = true
                    timeLeftSeconds = restMin * 60
                } else {
                    isFocusMode = false
                    isBreak = false
                    timeLeftSeconds = focusMin * 60
                    currentSession = 1
                }
            }
        } else if (isBreak) {
            while (timeLeftSeconds > 0 && isBreak) {
                delay(1000)
                timeLeftSeconds--
            }
            if (timeLeftSeconds <= 0 && isBreak) {
                isBreak = false
                isFocusMode = true
                currentSession++
                timeLeftSeconds = focusMin * 60
            }
        }
    }

    // Sync floating stopwatch
    LaunchedEffect(chkFloat, isFocusMode) {
        when {
            !chkFloat -> FloatingStopwatch.dismiss()
            chkFloat && (isFocusMode || isBreak) && hasOverlayPermission(context) ->
                FloatingStopwatch.show(context) { chkFloat = false }
        }
    }

    // Sync sound engine
    LaunchedEffect(chkSound, soundType, isFocusMode, isBreak) {
        if (chkSound && (isFocusMode || isBreak)) AmbientSoundEngine.play(soundType)
        else AmbientSoundEngine.stop()
    }

    // Sync Allow List Blocker
    LaunchedEffect(isFocusMode) {
        if (isFocusMode && hasUsageStatsPermission(context)) {
            val allowed = (allowApps.map { it.name } + context.packageName).toSet()
            AllowListBlocker.start(context, allowed)
        } else {
            AllowListBlocker.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { AmbientSoundEngine.stop(); FloatingStopwatch.dismiss(); AllowListBlocker.stop() }
    }

    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(DClrBg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Tab Bar
        TabBar(activeSubTab) { activeSubTab = it }

        if (activeSubTab == 0) {
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
                    timeLeftSeconds = timeLeftSeconds,
                    currentSession = currentSession,
                    totalSessions = totalSessions
                )

                // Start / Stop button
                StartStopButton(
                    isActive = isFocusMode || isBreak,
                    onClick = {
                        if (isFocusMode || isBreak) {
                            isFocusMode = false
                            isBreak = false
                            timeLeftSeconds = focusMin * 60
                            currentSession = 1
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
                        checked = chkSound, enabled = true, soundType = soundType,
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
                }

                // Show basic requirement warning if missing Usage Stats
                if (!hasUsageStatsPermission(context)) {
                    PermBanner(
                        color = Color(0xFFEFF6FF), tint = DClrTeal,
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
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Coming soon...", color = DClrGray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            modifier = Modifier.fillMaxHeight(0.9f),
            containerColor = DClrBg,
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
private fun TabBar(active: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = Color(0xFFF1F5F9), // Slight inset look
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Pomodoro", "Active Recall", "Spaced Rep.").forEachIndexed { i, title ->
                val selected = active == i
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) DClrSurface else Color.Transparent)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) DClrDark else DClrGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerHeroCard(
    isFocusMode: Boolean, isBreak: Boolean,
    timeLeftSeconds: Int, currentSession: Int, totalSessions: Int
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
                Text(
                    text = "%02d:%02d".format(timeLeftSeconds / 60, timeLeftSeconds % 60),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DClrWhite,
                    letterSpacing = 2.sp
                )
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
        isActive     -> DClrDark
        else         -> DClrTeal
    }
    val label = when {
        isActive     -> "STOP POMODORO"
        else         -> "START POMODORO"
    }
    val icon = when {
        isActive     -> Icons.Default.Stop
        else         -> Icons.Default.PlayArrow
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DClrSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF0F9FF)),
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
    checked: Boolean, enabled: Boolean, soundType: SoundType,
    onCheckedChange: (Boolean) -> Unit, onSoundTypeChange: (SoundType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(12.dp))
        Text("Ambient Sound", fontSize = 15.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (checked) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DClrSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFECFDF5)),
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(1) }
    val tempApps  = remember { mutableStateListOf<String>().apply { addAll(initialApps) } }
    val tempSites = remember { mutableStateListOf<String>().apply { addAll(initialSites) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Manage Allow List", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DClrDark)
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F5F9))) {
                Icon(Icons.Default.Close, null, tint = DClrDark, modifier = Modifier.size(18.dp))
            }
        }
        
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search or add new website…", color = DClrGray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = DClrGray, modifier = Modifier.size(20.dp)) },
            shape = RoundedCornerShape(16.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DClrSurface, unfocusedContainerColor = DClrSurface,
                focusedBorderColor = DClrTeal, unfocusedBorderColor = Color(0xFFE2E8F0)
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F5F9)).padding(4.dp)) {
            listOf("Apps", "Sites", "Keywords").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == i) DClrSurface else Color.Transparent)
                        .clickable { selectedTab = i },
                    Alignment.Center
                ) { Text(t, color = if (selectedTab == i) DClrDark else DClrGray, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        
        LazyColumn(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(DClrSurface).padding(8.dp)) {
            if (selectedTab == 1 && searchQuery.isNotEmpty()) {
                if (!tempSites.any { it.contains(searchQuery, ignoreCase = true) }) {
                    val url = if (searchQuery.contains(".")) searchQuery else "$searchQuery.com"
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F9FF)), Alignment.Center) {
                                Text(url.first().uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DClrTeal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(url, modifier = Modifier.weight(1f), color = DClrDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            FilledTonalButton(
                                onClick = { tempSites.add(url); searchQuery = "" },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFF0F9FF), contentColor = DClrTeal),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) { Text("Add", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                        HorizontalDivider(color = Color(0xFFF8FAFC))
                    }
                }
            }
            if (selectedTab == 1) {
                items(tempSites.size) { i ->
                    val site = tempSites[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage("https://www.google.com/s2/favicons?domain=$site&sz=128", null, Modifier.size(42.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(site, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DClrDark)
                            Text("Website allowed", color = DClrGray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { tempSites.remove(site) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, null, tint = DClrRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF8FAFC))
                }
            }
            if (selectedTab == 0) {
                items(tempApps.size) { i ->
                    val app = tempApps[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFECFDF5)), Alignment.Center) {
                            Icon(Icons.Default.Android, null, tint = DClrGreen, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(app, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DClrDark, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tempApps.remove(app) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, null, tint = DClrRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF8FAFC))
                }
            }
        }
        
        Button(
            onClick = { onSave(tempApps, tempSites) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DClrTeal),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) { Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) }
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
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)).padding(2.dp)
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

package com.rasel.RasFocus.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.alpha
import com.rasel.RasFocus.ui.theme.SoftWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.*
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity

private val PrimaryBlue    = Color(0xFF4A6FE3)
private val DarkBlue       = Color(0xFF2E4BC6)
private val LightBlue      = Color(0xFF6B8EF5)
private val SoftBlue       = Color(0xFFDDE6FF)
private val AccentGreen    = Color(0xFF4CAF50)
private val SoftRed        = Color(0xFFFFEBEB)
private val RedAccent      = Color(0xFFE53935)
private val PurpleCard     = Color(0xFFE8D5F5)
private val OrangeCard     = Color(0xFFFFF3DC)
private val GrayBg         = Color(0xFFF8FAFC)
private val TextDark       = Color(0xFF1A1A2E)
private val TextGray       = Color(0xFF8A8A9A)
private val White          = Color(0xFFFFFFFF)
private val CardBlue       = Color(0xFF3A5FD4)
private val DarkerCardBlue = Color(0xFF2E4FBE)

// Premium Teal Colors
private val PremiumTealDark = Color(0xFF032220)
private val PremiumTealMid  = Color(0xFF08504B)
private val PremiumTealAccent = Color(0xFF14C3B2)

// ── Premium Trial Management ──
object PremiumTrialManager {
    private const val PREFS_NAME = "premium_trial_prefs"
    private const val INSTALL_TIME_KEY = "install_time"
    private const val TRIAL_DAYS = 7
    
    fun initializeTrial(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(INSTALL_TIME_KEY, 0L) == 0L) {
            prefs.edit().putLong(INSTALL_TIME_KEY, System.currentTimeMillis()).apply()
        }
    }
    
    fun getTrialStatus(context: Context): TrialStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installTime = prefs.getLong(INSTALL_TIME_KEY, System.currentTimeMillis())
        val elapsedDays = ((System.currentTimeMillis() - installTime) / (1000L * 60 * 60 * 24)).toInt()
        val daysLeft = maxOf(0, TRIAL_DAYS - elapsedDays)
        val isTrialActive = daysLeft > 0
        
        return TrialStatus(isTrialActive, daysLeft, elapsedDays)
    }
    
    data class TrialStatus(
        val isActive: Boolean,
        val daysLeft: Int,
        val daysElapsed: Int
    )
}

class SelfFocusAccessibilityService : AccessibilityService() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val blockedKeywords = listOf("reels", "shorts", "tiktok")

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.i("SelfFocus", "SelfFocusAccessibilityService Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        val blockingPrefs = getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        val isBlockingActive = blockingPrefs.getBoolean("is_blocking_active", false)
        if (!isBlockingActive) return
        val prefs = getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val isStrictMode = prefs.getBoolean("strict_mode", false)
                if (isStrictMode && packageName != this.packageName) {
                    if (packageName.contains("packageinstaller")) {
                        val nodeText = collectNodeText(rootInActiveWindow).lowercase()
                        if (nodeText.contains("rasfocus") && nodeText.contains("uninstall")) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            Toast.makeText(this, "Strict Mode is ON!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val isKeywordsEnabled = prefs.getBoolean("keywords_enabled", false)
                if (isKeywordsEnabled) {
                    val nodeText = collectNodeText(rootInActiveWindow).lowercase()
                    if (blockedKeywords.any { nodeText.contains(it) }) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Toast.makeText(this, "Distracting Keyword Blocked!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    private fun collectNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        for (i in 0 until node.childCount) sb.append(collectNodeText(node.getChild(i)))
        return sb.toString()
    }

    private fun showBlockedOverlay(appName: String, message: String) {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#E62E4BC6"))
            addView(TextView(this@SelfFocusAccessibilityService).apply {
                text = "🧘\n\n$message"
                textSize = 24f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(android.graphics.Color.WHITE)
                setPadding(64, 64, 64, 64)
            })
            val btn = android.widget.Button(this@SelfFocusAccessibilityService).apply {
                text = "Take a Deep Breath & Go Back"
                setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            }
            addView(btn)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try { windowManager?.addView(layout, params); overlayView = layout } catch (e: Exception) {}
    }

    private fun removeOverlay() {
        overlayView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; overlayView = null }
    }
}

class SelfControlViewModel : ViewModel() {
    private val _keywordsEnabled = MutableStateFlow(true)
    val keywordsEnabled: StateFlow<Boolean> = _keywordsEnabled.asStateFlow()

    fun toggleKeywords(enabled: Boolean, context: Context) {
        _keywordsEnabled.update { enabled }
        context.getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("keywords_enabled", enabled).apply()
    }
}

@Composable
fun SelfControlAppIconImage(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                drawable.toBitmap().asImageBitmap()
            } catch (e: Exception) {
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(Color.Gray, CircleShape))
    }
}

@Composable
fun StayFocusedApp(
    navController: NavController,
    onSettingsClick: () -> Unit = {},
    viewModel: SelfControlViewModel = viewModel(),
    isComboMode: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    var bpSessionActive by remember { mutableStateOf(
        context.getSharedPreferences("take_rest_prefs", android.content.Context.MODE_PRIVATE)
            .getLong("break_end_time", 0L) > System.currentTimeMillis()
    ) }

    if (bpSessionActive) {
        // Session চলছে — BpBlockingService overlay দেখাচ্ছে
        // শুধু session শেষ হলে state update করি
        LaunchedEffect(Unit) {
            while (context.getSharedPreferences("take_rest_prefs", android.content.Context.MODE_PRIVATE)
                    .getLong("break_end_time", 0L) > System.currentTimeMillis()) {
                kotlinx.coroutines.delay(2000)
            }
            bpSessionActive = false
            context.stopService(android.content.Intent(context, BpBlockingService::class.java))
        }
        return
    }

    LaunchedEffect(Unit) { }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    onNavigate = { route -> navController.navigate(route) },
                    closeDrawer = { scope.launch { drawerState.close() } }
                )
            }
        ) {
        if (isComboMode) {
            Box(Modifier.fillMaxSize().background(GrayBg)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    FocusLauncherCard(onSessionStart = { bpSessionActive = true })
                    Spacer(Modifier.height(16.dp))
                    ExtremBlockCard(onClick = { navController.navigate("extreme_block") })
                    Spacer(Modifier.height(16.dp))
                    BlockingPlanCard(navController)
                    Spacer(Modifier.height(16.dp))
                    FamilyBrowserCard(context)
                    Spacer(Modifier.height(16.dp))
                    PermissionBanner(context)
                    Spacer(Modifier.height(20.dp))
                    AnalyticsSection(navController)
                    Spacer(Modifier.height(20.dp))
                    TakeABreakCard(onSessionStart = { bpSessionActive = true })
                    Spacer(Modifier.height(16.dp))
                    NormalModeCard()
                    Spacer(Modifier.height(16.dp))
                    TakeRestCard()
                    Spacer(Modifier.height(20.dp))
                    QuickActionsSection(viewModel, navController, context)
                    Spacer(Modifier.height(20.dp))
                    ProfileTemplatesSection(navController)
                    Spacer(Modifier.height(20.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(GrayBg)) {
                Column(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                TopHeader(navController) { scope.launch { drawerState.open() } }
                                Spacer(Modifier.height(16.dp))
                                FocusLauncherCard(onSessionStart = { bpSessionActive = true })
                                Spacer(Modifier.height(16.dp))
                                ExtremBlockCard(onClick = { navController.navigate("extreme_block") })
                                Spacer(Modifier.height(16.dp))
                                BlockingPlanCard(navController)
                                Spacer(Modifier.height(16.dp))
                                FamilyBrowserCard(context)
                                Spacer(Modifier.height(16.dp))
                                PermissionBanner(context)
                                Spacer(Modifier.height(20.dp))
                                AnalyticsSection(navController)
                                Spacer(Modifier.height(20.dp))
                                TakeABreakCard(onSessionStart = { bpSessionActive = true })
                                Spacer(Modifier.height(16.dp))
                                NormalModeCard()
                                Spacer(Modifier.height(16.dp))
                                TakeRestCard()
                                Spacer(Modifier.height(20.dp))
                                QuickActionsSection(viewModel, navController, context)
                                Spacer(Modifier.height(20.dp))
                                ProfileTemplatesSection(navController)
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        1 -> {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                TopHeader(navController) { scope.launch { drawerState.open() } }
                                Spacer(Modifier.height(16.dp))
                                FocusLauncherCard(onSessionStart = { bpSessionActive = true })
                                Spacer(Modifier.height(16.dp))
                                ExtremBlockCard(onClick = { navController.navigate("extreme_block") })
                                Spacer(Modifier.height(16.dp))
                                BlockingPlanCard(navController)
                                Spacer(Modifier.height(16.dp))
                                FamilyBrowserCard(context)
                                Spacer(Modifier.height(16.dp))
                                NormalModeCard()
                                Spacer(Modifier.height(16.dp))
                                TakeABreakCard(onSessionStart = { bpSessionActive = true })
                                Spacer(Modifier.height(16.dp))
                                TakeRestCard()
                                Spacer(Modifier.height(20.dp))
                                ProfileTemplatesSection(navController)
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        2 -> {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                TopHeader(navController) { scope.launch { drawerState.open() } }
                                Spacer(Modifier.height(16.dp))
                                AnalyticsSection(navController)
                                Spacer(Modifier.height(16.dp))
                                QuickActionsSection(viewModel, navController, context)
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        3 -> {
                            // Fix: don't keep composing Column/TopHeader while navigating away.
                            // Previously this caused "CompositionLocal LocalLifecycleOwner not present"
                            // crashes on release/minified builds due to a race between navigation
                            // tearing down this composable and it still trying to recompose.
                            LaunchedEffect(Unit) {
                                onSettingsClick()
                                selectedTab = 1
                            }
                        }
                    }
                    SelfControlBottomNav(selectedTab) { selectedTab = it }
                }
            }
        }
        }
    }
}

@Composable
fun TopHeader(navController: NavController? = null, onMenuClick: () -> Unit = {}) {
    val context = LocalContext.current
    
    // Initialize trial on first launch
    LaunchedEffect(Unit) {
        PremiumTrialManager.initializeTrial(context)
    }
    
    // Get trial status
    val trialStatus = PremiumTrialManager.getTrialStatus(context)
    val trialText = if (trialStatus.isActive) "${trialStatus.daysLeft} days free!" else "Basic Free Package"
    val badgeColor = if (trialStatus.isActive) PremiumTealAccent else Color(0xFFB0BEC5)

    Column {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(PremiumTealMid, PremiumTealDark)),
                    RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(46.dp).background(SoftWhite.copy(alpha = 0.12f), CircleShape).clickable { onMenuClick() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = SoftWhite, modifier = Modifier.size(24.dp))
                    }
                    Box(Modifier.background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                        .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (trialStatus.isActive) "💎" else "🛡️", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(trialText, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Box(Modifier.size(46.dp).background(SoftWhite.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = SoftWhite, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text("Welcome to", color = SoftWhite.copy(alpha = 0.75f), fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("RasFocus+", color = SoftWhite, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp)
                }
            }
        }

        // Adult Block + Deep Study quick-access buttons
        if (navController != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Adult Block Button
                Card(
                    modifier = Modifier.weight(1f).clickable { navController.navigate("adult_block") },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0059)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFF6A0DAD), Color(0xFF2D0059))))
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Box(
                                Modifier.size(44.dp).background(White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFF6BFF), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Adult Block", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("100% Safe Browsing", color = White.copy(alpha = 0.65f), fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.background(Color(0xFFFF6BFF).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(6.dp).background(Color(0xFF00FF88), CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("Tap to Enable", color = Color(0xFFFF6BFF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Deep Study Button
                Card(
                    modifier = Modifier.weight(1f).clickable { navController.navigate("deep_study") },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF001A0A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFF005C3B), Color(0xFF001A0A))))
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Box(
                                Modifier.size(44.dp).background(White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF00FFB2), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Deep Study", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Full Focus Mode", color = White.copy(alpha = 0.65f), fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.background(Color(0xFF00FFB2).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(6.dp).background(Color(0xFF00FFB2), CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("Start Session", color = Color(0xFF00FFB2), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // ── Study Tools Card ──────────────────────────────────────────────
        if (navController != null) {
            Spacer(Modifier.height(16.dp))
            PremiumFeatureWrapper(
                featureName = "Study Tools",
                onClick = { val intent = Intent(context, com.rasel.RasFocus.selfcontrol.StudyToolsActivity::class.java); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }
            ) {
                StudyToolsCard(context)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: StudyToolsCard — StudyToolsActivity লঞ্চ করে
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StudyToolsCard(context: Context) {
    com.rasel.RasFocus.ui.theme.PremiumCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = { val intent = Intent(context, com.rasel.RasFocus.selfcontrol.StudyToolsActivity::class.java); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF0D0D1A), Color(0xFF1A1A35))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            // Subtle glow accent top-right
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4FACFE).copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon box with gradient border effect
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📚", fontSize = 26.sp)
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Study Tools",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = SoftWhite
                        )
                        Spacer(Modifier.width(10.dp))
                        // Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "NEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0D0D1A),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "PDF Tools • AI Tools • Diary • Tasks",
                        fontSize = 12.sp,
                        color = Color(0xFF4FACFE).copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    // Mini tool pills
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("📄 PDF" to Color(0xFFFF6B6B), "🤖 AI" to Color(0xFF4FACFE), "📓 Diary" to Color(0xFFA18CD1), "✅ Tasks" to Color(0xFF43E97B)).forEach { (label, color) ->
                            Box(
                                modifier = Modifier
                                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF4FACFE).copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionBanner(context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityOn by remember { mutableStateOf(false) }
    var needsBatteryFix by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityOn = try {
                    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    enabled.contains(context.packageName, ignoreCase = true)
                } catch (e: Exception) { false }

                needsBatteryFix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    pm.isIgnoringBatteryOptimizations(context.packageName).not()
                } else false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isAccessibilityOn && !needsBatteryFix) return

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().background(SoftRed, RoundedCornerShape(10.dp)).padding(12.dp)) {
                Text("Grant the following permissions for Stay Focused to work properly!",
                    fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))

            if (!isAccessibilityOn) {
                PermissionRow(
                    icon = Icons.Default.Accessibility,
                    label = "Accessibility\nPermission",
                    buttonLabel = "Enable",
                    buttonColor = PrimaryBlue,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(context, "Turn on RasFocus+ Accessibility", Toast.LENGTH_LONG).show()
                    }
                )
                if (needsBatteryFix) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = GrayBg, thickness = 1.dp)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (needsBatteryFix) {
                PermissionRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery Optimisation",
                    buttonLabel = "Disable",
                    buttonColor = PrimaryBlue,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionRow(icon: ImageVector, label: String, buttonLabel: String, buttonColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextDark, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = TextDark, fontWeight = FontWeight.Medium)
        }
        Button(onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(50.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            Text(buttonLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AnalyticsSection(navController: NavController? = null) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = PremiumTealDark, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Analytics", color = PremiumTealDark, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text("See All", color = PremiumTealMid, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalyticsCard(Modifier.weight(1f), "⏰", "Screen Time", "1 hrs 19 m", "-66%", true)
            AnalyticsCard(Modifier.weight(1f), "🚀", "App Launches", "129", "-33%", true)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { navController?.navigate("statistics") }, modifier = Modifier.weight(1f).height(48.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = PremiumTealMid.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("Timeline", color = PremiumTealDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { navController?.navigate("statistics") }, modifier = Modifier.weight(1f).height(48.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = PremiumTealMid.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("Weekly Report", color = PremiumTealDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalyticsCard(modifier: Modifier, icon: String, label: String, value: String, change: String, positive: Boolean) {
    com.rasel.RasFocus.ui.theme.PremiumCard(modifier) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary, com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary.copy(alpha = 0.8f)))).padding(16.dp)) {
            Column {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(change, fontSize = 12.sp, color = if (positive) Color(0xFF00FFB2) else Color(0xFFFF5252), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun NormalModeCard() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
    var isStrict by remember { mutableStateOf(prefs.getBoolean("strict_mode", false)) }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable {
            isStrict = !isStrict
            prefs.edit().putBoolean("strict_mode", isStrict).apply()
            Toast.makeText(context, if (isStrict) "Strict Mode Active: Uninstall Blocked" else "Strict Mode Disabled", Toast.LENGTH_SHORT).show()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isStrict) SoftRed else OrangeCard)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (isStrict) RedAccent else TextDark)
                    Spacer(Modifier.width(10.dp))
                    Text("Strict Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                }
                Switch(
                    checked = isStrict, onCheckedChange = {
                        isStrict = it
                        prefs.edit().putBoolean("strict_mode", isStrict).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = RedAccent, checkedTrackColor = RedAccent.copy(alpha = 0.3f))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Cannot disable Accessibility or uninstall the app during active sessions.",
                fontSize = 13.sp, color = TextDark.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun QuickActionsSection(viewModel: SelfControlViewModel, navController: NavController, context: Context) {
    val keywordsEnabled by viewModel.keywordsEnabled.collectAsState()

    // SharedPreferences থেকে real blocked apps ও sites count পড়া
    var appsBlockedCount  by remember { mutableIntStateOf(BlockedData.getBlockedApps(context).size) }
    var sitesBlockedCount by remember { mutableIntStateOf(BlockedData.getBlockedSites(context).size) }
    // refresh counts when screen resumes
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            appsBlockedCount  = BlockedData.getBlockedApps(context).size
            sitesBlockedCount = BlockedData.getBlockedSites(context).size
        }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Quick Actions", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextGray)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBlue)) {
            Column {
                QuickActionRow(icon = Icons.Default.MobileOff, label = "Apps Blocked",
                    value = appsBlockedCount.toString(), bgColor = CardBlue, divider = true,
                    onClick = { navController.navigate("single_apps") })
                QuickActionRow(icon = Icons.Default.DesktopWindows, label = "Sites Blocked",
                    value = sitesBlockedCount.toString(), bgColor = DarkerCardBlue, divider = true,
                    onClick = { navController.navigate("single_website") })
                QuickActionRow(icon = Icons.Default.Schedule, label = "Schedule Blocks",
                    value = "Profiles", bgColor = CardBlue.copy(alpha = 0.85f), divider = true,
                    onClick = { navController.navigate("schedule_blocks") })
                QuickActionRow(icon = Icons.Default.Shield, label = "Adult Block",
                    value = "Safe", bgColor = DarkerCardBlue.copy(alpha = 0.9f), divider = true,
                    onClick = { navController.navigate("adult_block") })
                Row(Modifier.fillMaxWidth().background(CardBlue.copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("A|", fontSize = 18.sp, color = White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (keywordsEnabled) "Active" else "Inactive", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Keywords Blocked (Shorts/Reels)", color = White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    Switch(checked = keywordsEnabled,
                        onCheckedChange = { viewModel.toggleKeywords(it, context) },
                        colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = White, uncheckedTrackColor = White.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

@Composable
fun QuickActionRow(icon: ImageVector, label: String, value: String, bgColor: Color, divider: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().background(bgColor).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(value, color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(label, color = White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
        if (divider) HorizontalDivider(color = White.copy(alpha = 0.1f), thickness = 1.dp)
    }
}

@Composable
fun ProfileTemplatesSection(navController: NavController) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Profile Templates", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryBlue)
        Spacer(Modifier.height(4.dp))
        Text("Tap to start creating a profile with these presets", fontSize = 13.sp, color = TextGray)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TemplateCard("💼", "Work Focus", "9am – 5pm", "Block apps during work hours to focus deeply.", SoftBlue, onClick = { navController.navigate("deep_study") }) }
            item { TemplateCard("⏰", "Social Limit", "Every day · 30 min limit", "Cap your social media time to just half an hour a day.", SoftRed, badgeText = "Blocklist", badgeColor = RedAccent, onClick = { navController.navigate("single_apps") }) }
            item { TemplateCard("🌙", "Night Mode", "10pm – 7am", "Wind down and improve sleep quality.", PurpleCard, onClick = { navController.navigate("schedule_blocks") }) }
        }
    }
}

@Composable
fun TemplateCard(emoji: String, title: String, subtitle: String, detail: String, bgColor: Color, badgeText: String? = null, badgeColor: Color = RedAccent, onClick: () -> Unit) {
    Card(Modifier.width(200.dp).clickable { onClick() }, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(emoji, fontSize = 26.sp)
                Box(Modifier.size(32.dp).background(White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextDark)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.sp, color = TextDark.copy(alpha = 0.7f))
            if (badgeText != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(badgeText, color = badgeColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(detail, fontSize = 12.sp, color = TextDark.copy(alpha = 0.65f), lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SelfControlBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Dashboard", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
        Triple("Modes", Icons.Default.FlashOn, Icons.Outlined.FlashOn),
        Triple("Analytics", Icons.Default.BarChart, Icons.Outlined.BarChart),
        Triple("Account", Icons.Default.Person, Icons.Outlined.Person)
    )
    NavigationBar(containerColor = White, tonalElevation = 8.dp) {
        items.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
            NavigationBarItem(
                selected = selected == index, onClick = { onSelect(index) },
                icon = {
                    if (selected == index) {
                        Box(Modifier.background(SoftBlue, RoundedCornerShape(50.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Icon(filledIcon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }
                    } else { Icon(outlinedIcon, contentDescription = label, tint = TextGray, modifier = Modifier.size(22.dp)) }
                },
                label = { Text(label, fontSize = 11.sp, color = if (selected == index) PrimaryBlue else TextGray, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun AccountSection(context: Context) {
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: "1.0"

    Column(Modifier.padding(horizontal = 20.dp)) {
        // Profile Card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).background(
                        Brush.verticalGradient(listOf(PrimaryBlue, DarkBlue)), CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("RasFocus User", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                Text("Stay Focused · v$versionName", fontSize = 13.sp, color = TextGray)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().background(SoftBlue, RoundedCornerShape(12.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("5", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryBlue)
                        Text("Days Free", fontSize = 11.sp, color = TextGray)
                    }
                    Box(Modifier.width(1.dp).height(36.dp).background(TextGray.copy(alpha = 0.3f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Active", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentGreen)
                        Text("Plan", fontSize = 11.sp, color = TextGray)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Settings Options
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column {
                AccountRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy & Security",
                    tint = PrimaryBlue,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                HorizontalDivider(color = GrayBg)
                AccountRow(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    tint = AccentGreen,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                HorizontalDivider(color = GrayBg)
                AccountRow(
                    icon = Icons.Default.Accessibility,
                    label = "Accessibility Permission",
                    tint = PrimaryBlue,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                HorizontalDivider(color = GrayBg)
                AccountRow(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery Optimization",
                    tint = RedAccent,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                HorizontalDivider(color = GrayBg)
                AccountRow(
                    icon = Icons.Default.Info,
                    label = "App Version $versionName",
                    tint = TextGray,
                    showArrow = false,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("version", versionName))
                        Toast.makeText(context, "Version copied!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = TextDark, fontWeight = FontWeight.Medium)
        if (showArrow) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FAMILY BROWSER SECTION — Entry card + bottom-sheet chooser
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun FamilyBrowserCard(context: Context) {
    var showChooser by remember { mutableStateOf(false) }

    com.rasel.RasFocus.ui.theme.PremiumCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        onClick   = { showChooser = true }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0F2847), Color(0xFF0D2240))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column {
                // ── Title row ──────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF60A5FA).copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "RasBrowser",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF1F5F9)
                            )
                            Spacer(Modifier.width(7.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF4ADE80).copy(alpha = 0.18f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "SAFE",
                                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4ADE80), letterSpacing = 1.sp
                                )
                            }
                        }
                        Text(
                            "Family browser · ad blocking · content filter",
                            fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 15.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ── Stats row ──────────────────────────────────────────────
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BrowserStatChip(Icons.Default.Shield,      "Adult filter")
                    BrowserStatChip(Icons.Default.Block,       "Ads blocked")
                    BrowserStatChip(Icons.Default.Apps,        "3 apps")
                }
            }
        }
    }

    if (showChooser) {
        BrowserChooserDialog(context = context, onDismiss = { showChooser = false })
    }
}

@Composable
private fun BrowserStatChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF475569))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chooser Dialog — full-width bottom sheet style
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BrowserChooserDialog(context: Context, onDismiss: () -> Unit) {
    var showSettingsFor by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {}
                    .background(
                        Color(0xFF111827),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                // Handle
                Box(
                    modifier = Modifier
                        .width(32.dp).height(3.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 0.dp)
                ) {}
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp).height(3.dp)
                        .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(6.dp))

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Choose app",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9)
                    )
                    Text(
                        "3 available",
                        fontSize = 11.sp, color = Color(0xFF475569)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                // App rows
                BrowserAppRow(
                    context      = context,
                    iconVector   = Icons.Default.Security,
                    iconBgStart  = Color(0xFF1D4ED8),
                    iconBgEnd    = Color(0xFF3B82F6),
                    title        = "RasBrowser",
                    description  = "Safe · Ad-free · Full control",
                    onLaunch     = {
                        onDismiss()
                        val intent = Intent(context, FamilyBrowserActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    onSettings   = { showSettingsFor = "RasBrowser" },
                    onAddHome    = { pinSingleHomeShortcut(context, "RasBrowser") }
                )

                BrowserAppRow(
                    context      = context,
                    iconVector   = Icons.Default.PlayCircle,
                    iconBgStart  = Color(0xFFB91C1C),
                    iconBgEnd    = Color(0xFFEF4444),
                    title        = "YouTube Premium",
                    description  = "No ads · Shorts blocked",
                    onLaunch     = {
                        onDismiss()
                        val intent = Intent(
                            context,
                            com.rasel.RasFocus.selfcontrol.familybrowser.YoutubeActivity::class.java
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    },
                    onSettings   = { showSettingsFor = "YouTube" },
                    onAddHome    = { pinSingleHomeShortcut(context, "YouTube") }
                )

                BrowserAppRow(
                    context      = context,
                    iconVector   = Icons.Default.Groups,
                    iconBgStart  = Color(0xFF1D4ED8),
                    iconBgEnd    = Color(0xFF2563EB),
                    title        = "Facebook",
                    description  = "Reels blocked · Filtered",
                    onLaunch     = {
                        onDismiss()
                        val intent = Intent(
                            context,
                            com.rasel.RasFocus.selfcontrol.familybrowser.FacebookActivity::class.java
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    },
                    onSettings   = { showSettingsFor = "Facebook" },
                    onAddHome    = { pinSingleHomeShortcut(context, "Facebook") }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSettingsFor != null) {
        BrowserSettingsDialog(context, appType = showSettingsFor!!) { showSettingsFor = null }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single app row inside the chooser sheet
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BrowserAppRow(
    context:     Context,
    iconVector:  ImageVector,
    iconBgStart: Color,
    iconBgEnd:   Color,
    title:       String,
    description: String,
    onLaunch:    () -> Unit,
    onSettings:  () -> Unit,
    onAddHome:   () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    brush = Brush.linearGradient(listOf(iconBgStart, iconBgEnd)),
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconVector, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Title + description
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F5F9), maxLines = 1)
            Text(description, fontSize = 10.sp, color = Color(0xFF475569), maxLines = 1)
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // Add to home
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                    .clickable { onAddHome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AddToHomeScreen, null, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
            }
            // Settings
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Settings, null, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
            }
            // Open
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .background(Color(0xFF60A5FA).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .clickable { onLaunch() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Open",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF60A5FA)
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = Color.White.copy(alpha = 0.04f)
    )
}


// ── Update Center Section inside dashboard ──
@Composable
fun UpdateCenterSection(context: Context) {
    var releaseInfo by remember { mutableStateOf<com.rasel.RasFocus.ReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(true) }
    
    val prefs = context.getSharedPreferences("AutoUpdaterPrefs", Context.MODE_PRIVATE)
    val lastTag = prefs.getString(com.rasel.RasFocus.AutoUpdater.LAST_TAG_KEY, "") ?: ""

    LaunchedEffect(Unit) {
        com.rasel.RasFocus.AutoUpdater.fetchLatestReleaseInfo { info ->
            releaseInfo = info
            checking = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, tint = Color(0xFF4FACFE), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Update Center", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SoftWhite)
            }
            Spacer(Modifier.height(16.dp))
            
            if (checking) {
                Text("Checking for updates...", color = Color.LightGray, fontSize = 14.sp)
            } else if (releaseInfo != null) {
                val currentVersion = "v" + com.rasel.RasFocus.BuildConfig.VERSION_NAME
                val isLatest = releaseInfo!!.tagName == currentVersion
                if (isLatest) {
                    Text("Latest version installed ✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Version: $currentVersion", color = Color.LightGray, fontSize = 12.sp)
                } else {
                    Text("Update Available! \uD83C\uDF89", color = Color(0xFF4FACFE), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("New version: ${releaseInfo!!.tagName}", color = SoftWhite, fontSize = 14.sp)
                    Text("Released: ${releaseInfo!!.publishedAt}", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    
                    val downloadedFile = com.rasel.RasFocus.AutoUpdater.getDownloadedUpdateFile(context, releaseInfo!!.tagName)
                    
                    if (downloadedFile != null) {
                        Button(
                            onClick = { com.rasel.RasFocus.AutoUpdater.installDownloadedUpdate(context, downloadedFile) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = SoftWhite)
                            Spacer(Modifier.width(8.dp))
                            Text("Install Update Now", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SoftWhite)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { com.rasel.RasFocus.AutoUpdater.downloadAndInstallUpdate(context, com.rasel.RasFocus.AutoUpdater.APK_UNIVERSAL, releaseInfo!!.tagName) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FACFE)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Universal", fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = { com.rasel.RasFocus.AutoUpdater.downloadAndInstallUpdate(context, com.rasel.RasFocus.AutoUpdater.APK_LIGHT, releaseInfo!!.tagName) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FACFE)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Light", fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = { com.rasel.RasFocus.AutoUpdater.downloadAndInstallUpdate(context, com.rasel.RasFocus.AutoUpdater.APK_FULL_SPLIT, releaseInfo!!.tagName) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FACFE)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Split", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                Text("Could not check for updates. Check internet.", color = Color(0xFFFF3B30), fontSize = 14.sp)
            }
        }
    }
}

// ── Add Home Shortcut for single app ──

@Composable
fun BrowserSettingsDialog(context: Context, appType: String, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    // Facebook
    var fbHideVideo by remember { mutableStateOf(prefs.getBoolean("fb_hide_videos", false)) }
    var fbHideReels by remember { mutableStateOf(prefs.getBoolean("fb_hide_reels", false)) }
    var fbHideNewsfeed by remember { mutableStateOf(prefs.getBoolean("fb_hide_newsfeed", false)) }
    var fbGrayscale by remember { mutableStateOf(prefs.getBoolean("fb_grayscale", false)) }
    var fbTextOnly by remember { mutableStateOf(prefs.getBoolean("fb_text_only", false)) }

    // YouTube
    var ytHideShorts by remember { mutableStateOf(prefs.getBoolean("yt_hide_shorts", false)) }
    var ytHideComments by remember { mutableStateOf(prefs.getBoolean("yt_hide_comments", false)) }
    var ytGrayscale by remember { mutableStateOf(prefs.getBoolean("yt_grayscale", false)) }

    // RasBrowser
    var rbBlockAds by remember { mutableStateOf(prefs.getBoolean("rb_block_ads", true)) }
    var rbStrictBlacklist by remember { mutableStateOf(prefs.getBoolean("rb_strict_blacklist", false)) }
    var rbWhitelistMode by remember { mutableStateOf(prefs.getBoolean("rb_whitelist_mode", false)) }
    var rbForceDark by remember { mutableStateOf(prefs.getBoolean("rb_force_dark", false)) }

    var pendingToggleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var activeLockConfig by remember { mutableStateOf<ToggleLockConfig?>(null) }
    var showLockConfigFor by remember { mutableStateOf<String?>(null) }
    var showDomainListFor by remember { mutableStateOf<String?>(null) }

    val handleToggle: (String, Boolean, (Boolean) -> Unit) -> Unit = { id, newValue, applyToggle ->
        if (!newValue) {
            val config = ToggleLockManager.getConfig(context, id)
            if (config.lockMode != "none") {
                activeLockConfig = config
                pendingToggleAction = { applyToggle(newValue) }
            } else {
                applyToggle(newValue)
            }
        } else {
            applyToggle(newValue)
        }
    }

    if (activeLockConfig != null) {
        LockVerificationDialog(
            context = context,
            lockConfig = activeLockConfig!!,
            onSuccess = {
                pendingToggleAction?.invoke()
                pendingToggleAction = null
                activeLockConfig = null
            },
            onCancel = {
                pendingToggleAction = null
                activeLockConfig = null
            }
        )
    }

    if (showLockConfigFor != null) {
        ToggleLockModeSheet(
            context = context,
            toggleId = showLockConfigFor!!,
            onDismiss = { showLockConfigFor = null }
        )
    }

    if (showDomainListFor != null) {
        DomainListSheet(
            context = context,
            mode = showDomainListFor!!,
            onDismiss = { showDomainListFor = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                    .background(color = Color(0xFF16162A), shape = RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Text(
                    "$appType Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftWhite
                )
                Spacer(Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (appType) {
                        "Facebook" -> {
                            item {
                                SettingToggleRow("Hide Video Section", fbHideVideo, onSettingsClick = { showLockConfigFor = "fb_hide_videos" }) { newValue ->
                                    handleToggle("fb_hide_videos", newValue) { fbHideVideo = it; prefs.edit().putBoolean("fb_hide_videos", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Hide Reels Section", fbHideReels, onSettingsClick = { showLockConfigFor = "fb_hide_reels" }) { newValue ->
                                    handleToggle("fb_hide_reels", newValue) { fbHideReels = it; prefs.edit().putBoolean("fb_hide_reels", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Hide Newsfeed", fbHideNewsfeed, onSettingsClick = { showLockConfigFor = "fb_hide_newsfeed" }) { newValue ->
                                    handleToggle("fb_hide_newsfeed", newValue) { fbHideNewsfeed = it; prefs.edit().putBoolean("fb_hide_newsfeed", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Grayscale Mode", fbGrayscale, onSettingsClick = { showLockConfigFor = "fb_grayscale" }) { newValue ->
                                    handleToggle("fb_grayscale", newValue) { fbGrayscale = it; prefs.edit().putBoolean("fb_grayscale", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Text-only Mode", fbTextOnly, onSettingsClick = { showLockConfigFor = "fb_text_only" }) { newValue ->
                                    handleToggle("fb_text_only", newValue) { fbTextOnly = it; prefs.edit().putBoolean("fb_text_only", it).apply() }
                                }
                            }
                        }
                        "YouTube" -> {
                            item {
                                SettingToggleRow("Hide Shorts Section", ytHideShorts, onSettingsClick = { showLockConfigFor = "yt_hide_shorts" }) { newValue ->
                                    handleToggle("yt_hide_shorts", newValue) { ytHideShorts = it; prefs.edit().putBoolean("yt_hide_shorts", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Hide Comments", ytHideComments, onSettingsClick = { showLockConfigFor = "yt_hide_comments" }) { newValue ->
                                    handleToggle("yt_hide_comments", newValue) { ytHideComments = it; prefs.edit().putBoolean("yt_hide_comments", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Grayscale Mode", ytGrayscale, onSettingsClick = { showLockConfigFor = "yt_grayscale" }) { newValue ->
                                    handleToggle("yt_grayscale", newValue) { ytGrayscale = it; prefs.edit().putBoolean("yt_grayscale", it).apply() }
                                }
                            }
                        }
                        "RasBrowser" -> {
                            item {
                                SettingToggleRow("Block Ads", rbBlockAds, onSettingsClick = { showLockConfigFor = "rb_block_ads" }) { newValue ->
                                    handleToggle("rb_block_ads", newValue) { rbBlockAds = it; prefs.edit().putBoolean("rb_block_ads", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Strict Blacklist", rbStrictBlacklist, onSettingsClick = { showLockConfigFor = "rb_strict_blacklist" }, onEditClick = { showDomainListFor = "blacklist" }) { newValue ->
                                    handleToggle("rb_strict_blacklist", newValue) { rbStrictBlacklist = it; prefs.edit().putBoolean("rb_strict_blacklist", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Whitelist Mode", rbWhitelistMode, onSettingsClick = { showLockConfigFor = "rb_whitelist_mode" }, onEditClick = { showDomainListFor = "whitelist" }) { newValue ->
                                    handleToggle("rb_whitelist_mode", newValue) { rbWhitelistMode = it; prefs.edit().putBoolean("rb_whitelist_mode", it).apply() }
                                }
                            }
                            item {
                                SettingToggleRow("Force Dark Mode", rbForceDark, onSettingsClick = { showLockConfigFor = "rb_force_dark" }) { newValue ->
                                    handleToggle("rb_force_dark", newValue) { rbForceDark = it; prefs.edit().putBoolean("rb_force_dark", it).apply() }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FACFE)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

fun pinSingleHomeShortcut(context: Context, appType: String) {
    val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as? android.content.pm.ShortcutManager
    if (shortcutManager == null || !androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        Toast.makeText(context, "এই launcher shortcut pin করা support করে না", Toast.LENGTH_LONG).show()
        return
    }

    val icon = androidx.core.graphics.drawable.IconCompat.createWithResource(
        context, com.rasel.RasFocus.R.mipmap.ic_launcher
    )

    when (appType) {
        "RasBrowser" -> {
            val rasBrowserIntent = Intent(context, FamilyBrowserActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val rasBrowserShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "rasbrowser_home_shortcut")
                .setShortLabel("RasBrowser")
                .setLongLabel("RasBrowser — Safe Browser")
                .setIcon(icon)
                .setIntent(rasBrowserIntent)
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, rasBrowserShortcut, null)
        }
        "YouTube" -> {
            val youtubeIntent = Intent(context, com.rasel.RasFocus.selfcontrol.familybrowser.YoutubeActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val youtubeShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "youtube_native_home_shortcut")
                .setShortLabel("YouTube")
                .setLongLabel("YouTube — RasFocus")
                .setIcon(icon)
                .setIntent(youtubeIntent)
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, youtubeShortcut, null)
        }
        "Facebook" -> {
            val facebookIntent = Intent(context, com.rasel.RasFocus.selfcontrol.familybrowser.FacebookActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val facebookShortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "facebook_native_home_shortcut")
                .setShortLabel("Facebook")
                .setLongLabel("Facebook — RasFocus")
                .setIcon(icon)
                .setIntent(facebookIntent)
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, facebookShortcut, null)
        }
    }
}

// ── Premium Upgrade Dialog ──
@Composable
fun PremiumUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF0F0F1E), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(40.dp)
                        .background(Color(0xFF2A2A3E), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.height(16.dp))

                // Crown icon
                Text("👑", fontSize = 56.sp, modifier = Modifier.padding(bottom = 12.dp))

                Spacer(Modifier.height(12.dp))

                // Title
                Text(
                    "Upgrade to Premium",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Subtitle
                Text(
                    "Your trial period has ended. Unlock all premium features now!",
                    fontSize = 14.sp,
                    color = Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(28.dp))

                // Features list
                val features = listOf(
                    "🔒 Unlock Blocking Plans",
                    "📊 Advanced Analytics",
                    "🎯 Deep Study Tools",
                    "🌐 Browser Phone Mode",
                    "⏰ Take Rest Features",
                    "🚀 Priority Support"
                )

                features.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(PremiumTealAccent.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = PremiumTealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            feature,
                            fontSize = 14.sp,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Upgrade button
                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumTealAccent
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Upgrade Now", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F0F1E))
                        Spacer(Modifier.width(8.dp))
                        Text("→", fontSize = 18.sp, color = Color(0xFF0F0F1E))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Maybe later button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A3E)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Maybe Later", fontSize = 15.sp, color = Color(0xFFB0BEC5), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Premium Feature Guard Wrapper ──
@Composable
fun PremiumFeatureWrapper(
    featureName: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showUpgradeDialog by remember { mutableStateOf(false) }
    
    val trialStatus = PremiumTrialManager.getTrialStatus(context)

    if (showUpgradeDialog) {
        PremiumUpgradeDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgradeClick = {
                showUpgradeDialog = false
                onClick()
            }
        )
    }

    if (trialStatus.isActive) {
        // Trial is active - show content normally
        content()
    } else {
        // Trial expired - show locked state with upgrade trigger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUpgradeDialog = true }
        ) {
            // Dimmed version of content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.5f)
            ) {
                content()
            }
            
            // Lock overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F1E).copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("🔒", fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Premium Feature", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("Upgrade to unlock", fontSize = 12.sp, color = Color(0xFFB0BEC5), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

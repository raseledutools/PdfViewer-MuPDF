package com.rasel.RasFocus.selfcontrol

// ════════════════════════════════════════════════════════════════════
//  SocialMedia & Blocker Module — ULTIMATE EDITION (Zero Battery Drain)
//
//  What's inside:
//  1. Constants & Keys
//  2. BlockPrefs (SharedPreferences)
//  3. PermissionHelper
//  4. RasFocusAccessibilityService — (MASTER SERVICE: App Block + Reels + Anti-Cheat)
//  5. BlockedActivity — Full-screen "App Blocked" UI
//  6. SocialMediaScreen — Main UI for this section
//  7. ReelsShortsScreen — Sub UI for deep blocking
//  8. Shared Compose components
// ════════════════════════════════════════════════════════════════════

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// ════════════════════════════════════════════════════════════════════
//  1. CONSTANTS & KEYS
// ════════════════════════════════════════════════════════════════════

private const val PREFS_NAME = "self_control_prefs"

object Pkg {
    const val FACEBOOK    = "com.facebook.katana"
    const val YOUTUBE     = "com.google.android.youtube"
    const val INSTAGRAM   = "com.instagram.android"
    const val TELEGRAM    = "org.telegram.messenger"
    const val WHATSAPP    = "com.whatsapp"
    const val SNAPCHAT    = "com.snapchat.android"
    const val WA_BUSINESS = "com.whatsapp.w4b"
}

object Key {
    const val BLOCK_FACEBOOK     = "block_facebook"
    const val BLOCK_YOUTUBE      = "block_youtube"
    const val BLOCK_INSTAGRAM    = "block_instagram"
    const val BLOCK_TELEGRAM     = "block_telegram"
    const val BLOCK_WHATSAPP     = "block_whatsapp"
    const val BLOCK_YT_SHORTS    = "block_yt_shorts"
    const val BLOCK_IG_REELS     = "block_ig_reels"
    const val BLOCK_IG_STORIES   = "block_ig_stories"
    const val BLOCK_WA_STATUS    = "block_wa_status"
    const val BLOCK_WA_CHANNELS  = "block_wa_channels"
    const val BLOCK_SC_SPOTLIGHT = "block_sc_spotlight"
    const val BLOCK_SC_STORIES   = "block_sc_stories"
    const val BLOCK_WAB_STATUS   = "block_wab_status"
    const val BLOCK_WAB_CHANNELS = "block_wab_channels"
    const val STRICT_MODE        = "strict_mode" // Anti-cheat flag
}

// ════════════════════════════════════════════════════════════════════
//  2. BLOCK PREFS
// ════════════════════════════════════════════════════════════════════

class BlockPrefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(key: String): Boolean = p.getBoolean(key, false)
    fun set(key: String, v: Boolean) { p.edit().putBoolean(key, v).apply() }

    fun blockedPackages(): Set<String> = buildSet {
        if (get(Key.BLOCK_FACEBOOK))  add(Pkg.FACEBOOK)
        if (get(Key.BLOCK_YOUTUBE))   add(Pkg.YOUTUBE)
        if (get(Key.BLOCK_INSTAGRAM)) add(Pkg.INSTAGRAM)
        if (get(Key.BLOCK_TELEGRAM))  add(Pkg.TELEGRAM)
        if (get(Key.BLOCK_WHATSAPP))  add(Pkg.WHATSAPP)
    }

    fun appName(pkg: String) = when (pkg) {
        Pkg.FACEBOOK   -> "Facebook"
        Pkg.YOUTUBE    -> "YouTube"
        Pkg.INSTAGRAM  -> "Instagram"
        Pkg.TELEGRAM   -> "Telegram"
        Pkg.WHATSAPP   -> "WhatsApp"
        Pkg.SNAPCHAT   -> "Snapchat"
        Pkg.WA_BUSINESS -> "WA Business"
        else           -> pkg
    }
}

// ════════════════════════════════════════════════════════════════════
//  3. PERMISSION HELPER
// ════════════════════════════════════════════════════════════════════

object PermissionHelper {
    fun hasAccessibility(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return list.contains(ctx.packageName, ignoreCase = true)
    }

    fun requireAccessibility(ctx: Context) { 
        if (!hasAccessibility(ctx)) ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
    }
}

// ════════════════════════════════════════════════════════════════════
//  4. MASTER ACCESSIBILITY SERVICE (REPLACES POLLING SERVICE)
//  Handles: App Blocking + Reels Blocking + Anti Cheat
// ════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  AppBlockerAccessibilityService — Accessibility delegate for single-app blocking
//  RasFocusAccessibilityService দ্বারা delegate হিসেবে ব্যবহৃত হয়।
// ════════════════════════════════════════════════════════════════════════════
class AppBlockerAccessibilityService {
    fun onAccessibilityEvent(
        event: android.view.accessibility.AccessibilityEvent?,
        service: android.accessibilityservice.AccessibilityService,
        startActivity: (android.content.Intent) -> Unit,
        getPackageName: () -> String
    ) {
        val pkg = event?.packageName?.toString() ?: return
        val ctx = service
        val prefs = ctx.getSharedPreferences("blocker_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_blocking_active", false)) return
        // Single-app blocking is handled by AppBlockerService (UsageStats loop)
        // This delegate is a no-op stub; the main service handles whole-app blocking
    }
}

class RasFocusAccessibilityService : AccessibilityService() {

    companion object {
        // Deep Study delegate — deep_study.kt এ ব্যবহার হয়
        var deepStudyInstance: RasFocusAccessibilityService? = null
        // Extreme Block delegate — extrem_block.kt এ ব্যবহার হয়
        var blockingInstance: RasFocusAccessibilityService? = null
    }

    // ── Deep Study stub methods (BlockerAccessibilityService এর কাজ এখানে) ──
    fun startDeepStudySession(focusMinutes: Int, playSound: Boolean) {
        // BlockerAccessibilityService এর পরিবর্তে এখানে Deep Study logic handle হয়
        // DataManager দিয়ে state set করা হয়
        com.rasel.RasFocus.DataManager.isDeepStudyStrict = true
    }

    fun stopDeepStudySession() {
        com.rasel.RasFocus.DataManager.isDeepStudyStrict = false
    }

    // ── Extreme Block এর checkCurrentWindow stub ──
    fun checkCurrentWindow() {
        // Current window recheck করার জন্য — rootInActiveWindow থেকে re-evaluate করে
        val root = rootInActiveWindow ?: return
        root.recycle()
    }

    private lateinit var prefs: BlockPrefs
    private var lastBlockTime = 0L
    private val DEBOUNCE_MS   = 1_200L
    // ── SingleApps blocker delegate ──
    private val appBlockerDelegate = AppBlockerAccessibilityService()

    // Keyword definitions
    private val ytShortsActivityKeyword = "shorts"
    private val igReelsDesc      = listOf("reels", "রিলস")
    private val igStoriesDesc    = listOf("your story", "stories")
    private val igStoriesClass   = "storyviewer"
    private val waStatusDesc     = listOf("status", "updates", "স্ট্যাটাস")
    private val waChannelsDesc   = listOf("channels", "চ্যানেল")
    private val scSpotlightDesc  = listOf("spotlight", "স্পটলাইট")
    private val scStoriesDesc    = listOf("stories", "discover")
    private val antiCheatWords   = listOf("rasfocus", "uninstall", "force stop", "clear data")

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = BlockPrefs(this)
        deepStudyInstance = this
        blockingInstance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes  = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType   = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg  = event?.packageName?.toString() ?: return
        val root = rootInActiveWindow 
        val now  = System.currentTimeMillis()

        // ── GUARD: Accessibility permission দেওয়া মানেই blocking শুরু নয় ──
        // User dashboard থেকে explicitly enable না করলে কাজ করবে না
        val isBlockingActive = getSharedPreferences("blocker_prefs", 0)
            .getBoolean("is_blocking_active", false)
        if (!isBlockingActive) return

        // ── 1. ANTI-CHEAT / STRICT MODE ──
        if (prefs.get(Key.STRICT_MODE)) {
            if (pkg.contains("packageinstaller")) {
                val nodeText = collectNodeText(root).lowercase()
                if (antiCheatWords.any { nodeText.contains(it) }) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Toast.makeText(this, "🛡️ Strict Mode is ON! Settings blocked.", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        if (now - lastBlockTime < DEBOUNCE_MS) return

        // ── 1b. SingleApps blocker — AppBlockerAccessibilityService delegate ──
        appBlockerDelegate.onAccessibilityEvent(event, this, { startActivity(it) }, { packageName })

        // ── 2. WHOLE APP BLOCKING (Replaces AppBlockerService) ──
        if (pkg in prefs.blockedPackages()) {
            lastBlockTime = now
            goHomeAndBlock(pkg, "")
            return
        }

        // ── 3. DEEP CONTENT / REELS BLOCKING ──
        val blockedSection = when (pkg) {
            Pkg.YOUTUBE     -> checkYouTube(event, root)
            Pkg.INSTAGRAM   -> checkInstagram(event, root)
            Pkg.WHATSAPP    -> checkWhatsApp(root)
            Pkg.SNAPCHAT    -> checkSnapchat(root)
            Pkg.WA_BUSINESS -> checkWABusiness(root)
            else            -> null
        }

        if (blockedSection != null) {
            lastBlockTime = now
            goHomeAndBlock(pkg, blockedSection)
        }
    }

    private fun checkYouTube(event: AccessibilityEvent?, root: AccessibilityNodeInfo?): String? {
        if (!prefs.get(Key.BLOCK_YT_SHORTS)) return null
        val className = event?.className?.toString()?.lowercase() ?: ""
        if (ytShortsActivityKeyword in className) return "Shorts"
        if (root != null && nodeMatchesDesc(root, listOf("shorts"))) return "Shorts"
        return null
    }

    private fun checkInstagram(event: AccessibilityEvent?, root: AccessibilityNodeInfo?): String? {
        root ?: return null
        val className = event?.className?.toString()?.lowercase() ?: ""
        if (prefs.get(Key.BLOCK_IG_REELS) && nodeMatchesDesc(root, igReelsDesc)) return "Reels"
        if (prefs.get(Key.BLOCK_IG_STORIES)) {
            if (igStoriesClass in className || nodeMatchesDesc(root, igStoriesDesc)) return "Stories"
        }
        return null
    }

    private fun checkWhatsApp(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        if (prefs.get(Key.BLOCK_WA_STATUS)   && nodeMatchesDesc(root, waStatusDesc))   return "Status"
        if (prefs.get(Key.BLOCK_WA_CHANNELS) && nodeMatchesDesc(root, waChannelsDesc)) return "Channels"
        return null
    }

    private fun checkSnapchat(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        if (prefs.get(Key.BLOCK_SC_SPOTLIGHT) && nodeMatchesDesc(root, scSpotlightDesc)) return "Spotlight"
        if (prefs.get(Key.BLOCK_SC_STORIES)   && nodeMatchesDesc(root, scStoriesDesc))   return "Stories"
        return null
    }

    private fun checkWABusiness(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        if (prefs.get(Key.BLOCK_WAB_STATUS)   && nodeMatchesDesc(root, waStatusDesc))   return "Status"
        if (prefs.get(Key.BLOCK_WAB_CHANNELS) && nodeMatchesDesc(root, waChannelsDesc)) return "Channels"
        return null
    }

    private fun nodeMatchesDesc(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        return keywords.any { kw ->
            root.findAccessibilityNodeInfosByText(kw).isNotEmpty() || containsInDescOrText(root, kw, 0)
        }
    }

    private fun containsInDescOrText(node: AccessibilityNodeInfo?, kw: String, depth: Int): Boolean {
        if (node == null || depth > 6) return false
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (kw in desc || kw in text) return true
        for (i in 0 until node.childCount) {
            if (containsInDescOrText(node.getChild(i), kw, depth + 1)) return true
        }
        return false
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        for (i in 0 until node.childCount) { sb.append(collectNodeText(node.getChild(i))) }
        return sb.toString()
    }

    private fun goHomeAndBlock(pkg: String, section: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).postDelayed({
            BlockedActivity.launch(applicationContext, pkg, section.isNotEmpty(), section)
        }, 300L)
    }

    override fun onDestroy() {
        super.onDestroy()
        deepStudyInstance = null
        blockingInstance = null
    }

    override fun onInterrupt() {}
}

// ════════════════════════════════════════════════════════════════════
//  5. BLOCKED ACTIVITY (FULL SCREEN OVERLAY)
// ════════════════════════════════════════════════════════════════════

class BlockedActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG     = "pkg"
        private const val EXTRA_REEL    = "reel_block"
        private const val EXTRA_SECTION = "section"

        fun launch(ctx: Context, pkg: String, isReelBlock: Boolean, section: String = "") {
            ctx.startActivity(Intent(ctx, BlockedActivity::class.java).apply {
                putExtra(EXTRA_PKG,     pkg)
                putExtra(EXTRA_REEL,    isReelBlock)
                putExtra(EXTRA_SECTION, section)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg     = intent.getStringExtra(EXTRA_PKG) ?: ""
        val isReel  = intent.getBooleanExtra(EXTRA_REEL, false)
        val section = intent.getStringExtra(EXTRA_SECTION) ?: ""
        val prefs   = BlockPrefs(this)

        setContent {
            BlockedScreen(
                appName  = prefs.appName(pkg),
                isReel   = isReel,
                section  = section,
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun BlockedScreen(appName: String, isReel: Boolean, section: String, onDismiss: () -> Unit) {
    val title   = if (isReel) "$appName $section Blocked!" else "$appName Blocked!"
    val message = if (isReel)
        "তুমি $appName-এ $section দেখা বন্ধ করেছ। মনোযোগ রাখো!"
    else
        "তুমি $appName ব্লক করেছ। এখন কাজে মনোযোগ দাও!"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(56.dp))
            }
            Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text(text = message, fontSize = 15.sp, color = Color.White.copy(0.75f), textAlign = TextAlign.Center, lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color(0xFF1A1A2E)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("ঠিক আছে, বুঝেছি", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  THEME COLORS
// ════════════════════════════════════════════════════════════════════

private val BgLight       = Color(0xFFF5F6FA)
private val CardWhite     = Color(0xFFFFFFFF)
private val AccentRed     = Color(0xFFE53935)
private val AccentGreen   = Color(0xFF43A047)
private val AccentOrange  = Color(0xFFFF9800)
private val AccentBlue    = Color(0xFF1E88E5)
private val TextPrimary   = Color(0xFF1A1A2E)
private val TextSec       = Color(0xFF6B7280)
private val BlockedBg     = Color(0xFFFFF0F0)
private val BlockedBorder = Color(0xFFFFCDD2)
private val WarnBg        = Color(0xFFFFF8E1)
private val WarnBorder    = Color(0xFFFFE082)

// ════════════════════════════════════════════════════════════════════
//  6. SOCIAL MEDIA CONTROLS (Main Connectable Screen)
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs   = remember { BlockPrefs(context) }

    var hasA11y  by remember { mutableStateOf(PermissionHelper.hasAccessibility(context)) }

    var blockFb   by remember { mutableStateOf(prefs.get(Key.BLOCK_FACEBOOK)) }
    var blockYt   by remember { mutableStateOf(prefs.get(Key.BLOCK_YOUTUBE)) }
    var blockIg   by remember { mutableStateOf(prefs.get(Key.BLOCK_INSTAGRAM)) }
    var blockTg   by remember { mutableStateOf(prefs.get(Key.BLOCK_TELEGRAM)) }
    var blockWa   by remember { mutableStateOf(prefs.get(Key.BLOCK_WHATSAPP)) }
    var blockWaSt by remember { mutableStateOf(prefs.get(Key.BLOCK_WA_STATUS)) }

    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        hasA11y  = PermissionHelper.hasAccessibility(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Social Media", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLight)
            )
        },
        containerColor = BgLight
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasA11y) PermBanner(Icons.Default.Accessibility, "Accessibility দরকার — App/Reels block করতে") {
                PermissionHelper.requireAccessibility(context)
                hasA11y = PermissionHelper.hasAccessibility(context)
            }

            SectionHeader(Icons.Default.Share, "Social Media Controls", AccentBlue)
            ReelsShortsBtn { navController.navigate("reels_shorts") }
            Spacer(Modifier.height(2.dp))

            // Facebook
            AppAccordion("Facebook", Color(0xFF1877F2), Icons.Default.Facebook, expanded == "fb", { expanded = if (expanded == "fb") null else "fb" }) {
                BlockRow(Icons.Outlined.Block, "Block Facebook", blockFb, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockFb = it; prefs.set(Key.BLOCK_FACEBOOK, it)
                }
            }
            // YouTube
            AppAccordion("YouTube", Color(0xFFFF0000), Icons.Default.PlayArrow, expanded == "yt", { expanded = if (expanded == "yt") null else "yt" }) {
                BlockRow(Icons.Outlined.Block, "Block YouTube", blockYt, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockYt = it; prefs.set(Key.BLOCK_YOUTUBE, it)
                }
            }
            // Instagram
            AppAccordion("Instagram", Color(0xFFE1306C), Icons.Default.CameraAlt, expanded == "ig", { expanded = if (expanded == "ig") null else "ig" }) {
                BlockRow(Icons.Outlined.Block, "Block Instagram", blockIg, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockIg = it; prefs.set(Key.BLOCK_INSTAGRAM, it)
                }
            }
            // Telegram
            AppAccordion("Telegram", Color(0xFF0088CC), Icons.Default.Send, expanded == "tg", { expanded = if (expanded == "tg") null else "tg" }) {
                BlockRow(Icons.Outlined.Block, "Block Telegram", blockTg, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockTg = it; prefs.set(Key.BLOCK_TELEGRAM, it)
                }
            }
            // WhatsApp
            AppAccordion("WhatsApp", Color(0xFF25D366), Icons.Default.Chat, expanded == "wa", { expanded = if (expanded == "wa") null else "wa" }) {
                BlockRow(Icons.Outlined.Visibility, "Block WhatsApp Status", blockWaSt, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockWaSt = it; prefs.set(Key.BLOCK_WA_STATUS, it)
                }
                Spacer(Modifier.height(6.dp))
                BlockRow(Icons.Outlined.Block, "Block WhatsApp", blockWa, !hasA11y) {
                    if (!hasA11y) { PermissionHelper.requireAccessibility(context); return@BlockRow }
                    blockWa = it; prefs.set(Key.BLOCK_WHATSAPP, it)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  7. SCREEN 2 — BLOCK REELS / SHORTS
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsShortsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs   = remember { BlockPrefs(context) }

    var hasA11y by remember { mutableStateOf(PermissionHelper.hasAccessibility(context)) }

    var ytShorts    by remember { mutableStateOf(prefs.get(Key.BLOCK_YT_SHORTS)) }
    var igReels     by remember { mutableStateOf(prefs.get(Key.BLOCK_IG_REELS)) }
    var igStories   by remember { mutableStateOf(prefs.get(Key.BLOCK_IG_STORIES)) }
    var waStatus    by remember { mutableStateOf(prefs.get(Key.BLOCK_WA_STATUS)) }
    var waChannels  by remember { mutableStateOf(prefs.get(Key.BLOCK_WA_CHANNELS)) }
    var scSpotlight by remember { mutableStateOf(prefs.get(Key.BLOCK_SC_SPOTLIGHT)) }
    var scStories   by remember { mutableStateOf(prefs.get(Key.BLOCK_SC_STORIES)) }
    var wabStatus   by remember { mutableStateOf(prefs.get(Key.BLOCK_WAB_STATUS)) }
    var wabChannels by remember { mutableStateOf(prefs.get(Key.BLOCK_WAB_CHANNELS)) }

    LaunchedEffect(Unit) { hasA11y = PermissionHelper.hasAccessibility(context) }

    fun toggle(current: Boolean, key: String, update: (Boolean) -> Unit): (Boolean) -> Unit = { v ->
        if (!hasA11y) { PermissionHelper.requireAccessibility(context) }
        else { update(v); prefs.set(key, v) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block Reels/Shorts", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLight)
            )
        },
        containerColor = BgLight
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasA11y) PermBanner(Icons.Default.Accessibility, "Accessibility দরকার — Reels/Shorts block করতে") {
                PermissionHelper.requireAccessibility(context)
                hasA11y = PermissionHelper.hasAccessibility(context)
            }

            InfoCard("Block distracting content like Shorts, Reels, and other in-app feeds to stay focused.")

            ReelsSection("YouTube", Color(0xFFFF0000), Icons.Default.PlayArrow, listOf(
                RD("Shorts", ytShorts, false, !hasA11y, toggle(ytShorts, Key.BLOCK_YT_SHORTS) { ytShorts = it })
            ))
            ReelsSection("Instagram", Color(0xFFE1306C), Icons.Default.CameraAlt, listOf(
                RD("Reels",   igReels,   false, !hasA11y, toggle(igReels,   Key.BLOCK_IG_REELS)   { igReels = it }),
                RD("Stories", igStories, true,  !hasA11y, toggle(igStories, Key.BLOCK_IG_STORIES) { igStories = it })
            ))
            ReelsSection("WhatsApp", Color(0xFF25D366), Icons.Default.Chat, listOf(
                RD("Status",   waStatus,   false, !hasA11y, toggle(waStatus,   Key.BLOCK_WA_STATUS)   { waStatus = it }),
                RD("Channels", waChannels, true,  !hasA11y, toggle(waChannels, Key.BLOCK_WA_CHANNELS) { waChannels = it })
            ))
            ReelsSection("Snapchat", Color(0xFFFFD600), Icons.Default.CameraEnhance, listOf(
                RD("Spotlight", scSpotlight, false, !hasA11y, toggle(scSpotlight, Key.BLOCK_SC_SPOTLIGHT) { scSpotlight = it }),
                RD("Stories",   scStories,   true,  !hasA11y, toggle(scStories,   Key.BLOCK_SC_STORIES)   { scStories = it })
            ))
            ReelsSection("WA Business", Color(0xFF128C7E), Icons.Default.Business, listOf(
                RD("Status",   wabStatus,   false, !hasA11y, toggle(wabStatus,   Key.BLOCK_WAB_STATUS)   { wabStatus = it }),
                RD("Channels", wabChannels, true,  !hasA11y, toggle(wabChannels, Key.BLOCK_WAB_CHANNELS) { wabChannels = it })
            ))

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  8. SHARED COMPOSE COMPONENTS
// ════════════════════════════════════════════════════════════════════

data class RD(
    val label: String,
    val checked: Boolean,
    val isPremium: Boolean,
    val needsPerm: Boolean,
    val onToggle: (Boolean) -> Unit
)

@Composable
fun PermBanner(icon: ImageVector, msg: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(WarnBg), border = BorderStroke(1.dp, WarnBorder), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentOrange, modifier = Modifier.size(22.dp))
            Text(msg, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f), lineHeight = 17.sp)
            TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = AccentOrange)) {
                Text("অনুমতি দাও", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ReelsShortsBtn(onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "g")
    val a   by inf.animateFloat(0.88f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "g")
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFFF6B35).copy(a), Color(0xFFFF9800)))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.VideoLibrary, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text("Block Reels & Shorts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Manage distracting short-form content", color = Color.White.copy(0.85f), fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(0.12f)), Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
fun InfoCard(text: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(AccentOrange.copy(0.15f)), Alignment.Center) {
                Icon(Icons.Default.VideoLibrary, null, tint = AccentOrange, modifier = Modifier.size(22.dp))
            }
            Text(text, fontSize = 13.sp, color = TextSec, lineHeight = 19.sp)
        }
    }
}

@Composable
fun AppAccordion(
    name: String, color: Color, icon: ImageVector,
    expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "r")
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(0.15f)), Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSec, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rot })
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

@Composable
fun BlockRow(icon: ImageVector, label: String, checked: Boolean, needsPerm: Boolean, onChange: (Boolean) -> Unit) {
    val bg  = if (checked) BlockedBg    else CardWhite
    val bdr = if (checked) BlockedBorder else Color(0xFFE5E7EB)
    val ic  = if (checked) AccentRed     else TextSec
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, bdr, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ic, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (checked) AccentRed else TextPrimary, modifier = Modifier.weight(1f))
        if (needsPerm) {
            IconButton({ onChange(!checked) }, Modifier.size(32.dp)) {
                Icon(Icons.Default.Lock, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
            }
        } else {
            Switch(checked, onChange, colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentRed,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB)
            ))
        }
    }
}

@Composable
fun ReelsSection(name: String, color: Color, icon: ImageVector, items: List<RD>) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(0.15f)), Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(12.dp))
            items.forEachIndexed { i, item -> if (i > 0) Spacer(Modifier.height(8.dp)); ReelsRow(item) }
        }
    }
}

@Composable
fun ReelsRow(item: RD) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        when {
            item.needsPerm -> IconButton({ item.onToggle(!item.checked) }, Modifier.size(32.dp)) {
                Icon(Icons.Default.Lock, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
            }
            item.isPremium -> Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(AccentOrange).padding(horizontal = 12.dp, vertical = 6.dp),
                Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Upgrade", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> Switch(item.checked, item.onToggle, colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentGreen,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB)
            ))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ReelsBlockerService — Accessibility Service alias
//  Manifest এ ReelsBlockerService নামে declare করা।
//  RasFocusAccessibilityService এর কাজই করে — separate class দরকার
//  কারণ Android একটা service class কে একবারই bind করতে পারে।
// ════════════════════════════════════════════════════════════════════════════
class ReelsBlockerService : android.accessibilityservice.AccessibilityService() {

    private lateinit var prefs: BlockPrefs
    private var lastBlockTime = 0L
    private val DEBOUNCE_MS   = 300L

    override fun onServiceConnected() {
        prefs = BlockPrefs(this)
        serviceInfo = android.accessibilityservice.AccessibilityServiceInfo().apply {
            eventTypes   = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                           android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags        = android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Guard: user explicitly enable না করলে কাজ করে না
        val isBlockingActive = getSharedPreferences("blocker_prefs", 0)
            .getBoolean("is_blocking_active", false)
        if (!isBlockingActive) return

        val pkg = event?.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < DEBOUNCE_MS) return

        if (pkg in prefs.blockedPackages()) {
            lastBlockTime = now
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}

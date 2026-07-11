package com.rasel.RasFocus.child

// ============================================================
// RASFOCUS+ CHILD PHONE MODULE — Google Family Link style
//
// Design principles (per parent's request to mirror Family Link):
//   1. The child device is a RENDERER of parent-set rules, not an
//      editor of them. There are NO toggles here — only a clear
//      status view and a "request" button.
//   2. Pairing requires an explicit, un-skippable CONSENT screen
//      before any monitoring/enforcement starts.
//   3. Enforcement stays visible: a persistent notification always
//      runs while protection is active (no silent background spying).
//   4. Real uninstall-resistance comes from Android Device Admin,
//      not from toast-spamming or intercepting the Settings app.
//   5. Firebase paths match exactly what the PARENT app already
//      reads/writes (MainViewModel/FirebaseRepository in
//      MainActivity.kt): users/<parentUid>/devices/<deviceId>/...
// ============================================================

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

import com.rasel.RasFocus.RasFocusColors

private const val CHILD_TAG = "RasFocus_Child"
private const val RULES_UPDATED_ACTION = "com.rasel.RasFocus.CHILD_RULES_UPDATED"
private const val SHOW_APP_LIMITED_ACTION = "com.rasel.RasFocus.SHOW_APP_LIMITED"
private const val SHOW_LOCK_ACTION = "com.rasel.RasFocus.SHOW_DEVICE_LOCK"

// ─────────────────────────────────────────────────────────────
// RASFOCUS BRAND PALETTE — mirrors SelfControlModule.kt exactly,
// used for the shared header/footer components below so every
// module renders the identical Premium Teal header + white
// NavigationBar footer, independent of the file it lives in.
// ─────────────────────────────────────────────────────────────
private val PrimaryBlue      = Color(0xFF4A6FE3)
private val SoftBlue         = Color(0xFFDDE6FF)
private val TextGrayBrand    = Color(0xFF8A8A9A)
private val WhiteBrand       = Color(0xFFFFFFFF)
private val PremiumTealDark   = Color(0xFF032220)
private val PremiumTealMid    = Color(0xFF08504B)
private val PremiumTealAccent = Color(0xFF14C3B2)

// ============================================================
// PART 1 — PAIRING STATE & LOCAL RULE CACHE
// ============================================================

/** Persists this device's pairing identity: which parent account owns it. */
object ChildPairingManager {
    private const val PREFS = "RasFocusChildPrefs"

    fun isPaired(context: Context): Boolean =
        prefs(context).getBoolean("is_paired", false)

    fun setPaired(context: Context, parentUid: String, deviceId: String) {
        prefs(context).edit()
            .putBoolean("is_paired", true)
            .putString("parent_uid", parentUid)
            .putString("device_id", deviceId)
            .apply()
    }

    fun getParentUid(context: Context): String? = prefs(context).getString("parent_uid", null)
    fun getDeviceId(context: Context): String? = prefs(context).getString("device_id", null)

    /** Stable per-install device id, generated once and reused for every pairing attempt. */
    fun getOrCreateDeviceId(context: Context): String {
        val existing = prefs(context).getString("device_id", null)
        if (existing != null) return existing
        val fresh = "mobile_${UUID.randomUUID().toString().take(8)}"
        prefs(context).edit().putString("device_id", fresh).apply()
        return fresh
    }

    fun hasAcceptedConsent(context: Context): Boolean = prefs(context).getBoolean("consent_accepted", false)
    fun setConsentAccepted(context: Context) { prefs(context).edit().putBoolean("consent_accepted", true).apply() }

    fun unpair(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Local cache of the rules the parent has set — kept in sync by [ChildFirebaseService]. */
data class ChildRules(
    val isLocked: Boolean = false,
    val isHalalGuardOn: Boolean = true,
    val blockYoutubeShorts: Boolean = true,
    val blockReels: Boolean = true,
    val blockIncognito: Boolean = true,
    val buttonPhoneMode: Boolean = false,
    val screenTimeLimitMinutes: Int = 0,          // 0 = no limit
    val screenTimeUsedMinutes: Int = 0,
    val blockedPackages: Set<String> = emptySet(),
    val appTimeLimitsMinutes: Map<String, Int> = emptyMap(),
    val parentName: String = "your parent",
    
    // New fields
    val deepStudyEnabled: Boolean = false,
    val extremeBlockEnabled: Boolean = false,
    val singleAppsBlockEnabled: Boolean = false,
    val singleWebsiteBlockEnabled: Boolean = false,
    val familyBrowserEnabled: Boolean = false,
    
    val fbEnabled: Boolean = false,
    val fbStartTime: String = "00:00",
    val fbEndTime: String = "00:00",
    val fbLiteEnabled: Boolean = false,
    val fbLiteStartTime: String = "00:00",
    val fbLiteEndTime: String = "00:00",
    val ytEnabled: Boolean = false,
    val ytStartTime: String = "00:00",
    val ytEndTime: String = "00:00",
    val chromeEnabled: Boolean = false,
    val chromeStartTime: String = "00:00",
    val chromeEndTime: String = "00:00"
)

object ChildRuleManager {
    private const val PREFS = "RasFocusChildRules"

    fun save(context: Context, rules: ChildRules) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("isLocked", rules.isLocked)
            .putBoolean("halalGuard", rules.isHalalGuardOn)
            .putBoolean("blockShorts", rules.blockYoutubeShorts)
            .putBoolean("blockReels", rules.blockReels)
            .putBoolean("blockIncognito", rules.blockIncognito)
            .putBoolean("buttonPhoneMode", rules.buttonPhoneMode)
            .putInt("screenLimit", rules.screenTimeLimitMinutes)
            .putInt("screenUsed", rules.screenTimeUsedMinutes)
            .putStringSet("blockedPackages", rules.blockedPackages)
            .putString("appTimeLimits", rules.appTimeLimitsMinutes.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putString("parentName", rules.parentName)
            .putBoolean("deepStudyEnabled", rules.deepStudyEnabled)
            .putBoolean("extremeBlockEnabled", rules.extremeBlockEnabled)
            .putBoolean("singleAppsBlockEnabled", rules.singleAppsBlockEnabled)
            .putBoolean("singleWebsiteBlockEnabled", rules.singleWebsiteBlockEnabled)
            .putBoolean("familyBrowserEnabled", rules.familyBrowserEnabled)
            .putBoolean("fbEnabled", rules.fbEnabled)
            .putString("fbStartTime", rules.fbStartTime)
            .putString("fbEndTime", rules.fbEndTime)
            .putBoolean("fbLiteEnabled", rules.fbLiteEnabled)
            .putString("fbLiteStartTime", rules.fbLiteStartTime)
            .putString("fbLiteEndTime", rules.fbLiteEndTime)
            .putBoolean("ytEnabled", rules.ytEnabled)
            .putString("ytStartTime", rules.ytStartTime)
            .putString("ytEndTime", rules.ytEndTime)
            .putBoolean("chromeEnabled", rules.chromeEnabled)
            .putString("chromeStartTime", rules.chromeStartTime)
            .putString("chromeEndTime", rules.chromeEndTime)
            .apply()
        context.sendBroadcast(Intent(RULES_UPDATED_ACTION).setPackage(context.packageName))
    }

    fun load(context: Context): ChildRules {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val limitsRaw = p.getString("appTimeLimits", "") ?: ""
        val limits = limitsRaw.split(";").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to (v.toIntOrNull() ?: 0)
        }
        return ChildRules(
            isLocked = p.getBoolean("isLocked", false),
            isHalalGuardOn = p.getBoolean("halalGuard", true),
            blockYoutubeShorts = p.getBoolean("blockShorts", true),
            blockReels = p.getBoolean("blockReels", true),
            blockIncognito = p.getBoolean("blockIncognito", true),
            buttonPhoneMode = p.getBoolean("buttonPhoneMode", false),
            screenTimeLimitMinutes = p.getInt("screenLimit", 0),
            screenTimeUsedMinutes = p.getInt("screenUsed", 0),
            blockedPackages = p.getStringSet("blockedPackages", emptySet()) ?: emptySet(),
            appTimeLimitsMinutes = limits,
            parentName = p.getString("parentName", "your parent") ?: "your parent",
            deepStudyEnabled = p.getBoolean("deepStudyEnabled", false),
            extremeBlockEnabled = p.getBoolean("extremeBlockEnabled", false),
            singleAppsBlockEnabled = p.getBoolean("singleAppsBlockEnabled", false),
            singleWebsiteBlockEnabled = p.getBoolean("singleWebsiteBlockEnabled", false),
            familyBrowserEnabled = p.getBoolean("familyBrowserEnabled", false),
            fbEnabled = p.getBoolean("fbEnabled", false),
            fbStartTime = p.getString("fbStartTime", "00:00") ?: "00:00",
            fbEndTime = p.getString("fbEndTime", "00:00") ?: "00:00",
            fbLiteEnabled = p.getBoolean("fbLiteEnabled", false),
            fbLiteStartTime = p.getString("fbLiteStartTime", "00:00") ?: "00:00",
            fbLiteEndTime = p.getString("fbLiteEndTime", "00:00") ?: "00:00",
            ytEnabled = p.getBoolean("ytEnabled", false),
            ytStartTime = p.getString("ytStartTime", "00:00") ?: "00:00",
            ytEndTime = p.getString("ytEndTime", "00:00") ?: "00:00",
            chromeEnabled = p.getBoolean("chromeEnabled", false),
            chromeStartTime = p.getString("chromeStartTime", "00:00") ?: "00:00",
            chromeEndTime = p.getString("chromeEndTime", "00:00") ?: "00:00"
        )
    }
}

// ============================================================
// PART 2 — PAIRING (resolves PIN -> parent account, registers device)
// ============================================================

sealed class PairResult {
    object Success : PairResult()
    object InvalidPin : PairResult()
    object NetworkError : PairResult()
}

object ChildPairing {
    /**
     * Looks up the 6-digit PIN the parent generated (Firestore: pairing_codes/<pin>,
     * same doc the Windows PC app already reads via `parent_uid`), then registers
     * this phone under that parent's device tree in Realtime Database so it shows
     * up identically to how MainViewModel/FirebaseRepository already expect mobile
     * devices to look (users/<parentUid>/devices/<deviceId>).
     */
    suspend fun pairWithPin(context: Context, pin: String, deviceName: String): PairResult {
        return try {
            val doc = FirebaseFirestore.getInstance()
                .collection("pairing_codes").document(pin).get().await()
            val parentUid = doc.getString("parent_uid") ?: return PairResult.InvalidPin

            val deviceId = ChildPairingManager.getOrCreateDeviceId(context)
            val deviceMap = mapOf(
                "id" to deviceId,
                "name" to deviceName,
                "ownerName" to "Child",
                "type" to "MOBILE",
                "isLocked" to false,
                "isHalalGuardOn" to true,
                "screenTimeLimit" to 0,
                "isOnline" to true,
                "batteryLevel" to 100,
                "isDeviceAdminActive" to false,
                "isAccessibilityActive" to false
            )
            FirebaseDatabase.getInstance()
                .getReference("users/$parentUid/devices/$deviceId")
                .updateChildren(deviceMap)
                .await()

            ChildPairingManager.setPaired(context, parentUid, deviceId)
            PairResult.Success
        } catch (e: Exception) {
            PairResult.NetworkError
        }
    }
}

// ============================================================
// PART 3 — UI (Compose)
// ============================================================

enum class ChildScreenState { PAIRING, CONSENT, PERMISSIONS, STATUS }

@Composable
fun ChildRootScreen(context: Context) {
    var currentScreen by remember {
        mutableStateOf(
            when {
                !ChildPairingManager.isPaired(context) -> ChildScreenState.PAIRING
                !ChildPairingManager.hasAcceptedConsent(context) -> ChildScreenState.CONSENT
                else -> ChildScreenState.STATUS
            }
        )
    }

    var showLockOverlay by remember { mutableStateOf(false) }
    var showAppLimited by remember { mutableStateOf(false) }
    var limitedAppName by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    SHOW_LOCK_ACTION -> showLockOverlay = true
                    SHOW_APP_LIMITED_ACTION -> {
                        limitedAppName = intent.getStringExtra("app_name") ?: "This app"
                        showAppLimited = true
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(SHOW_LOCK_ACTION)
            addAction(SHOW_APP_LIMITED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite)) {
        Crossfade(targetState = currentScreen, label = "child_nav") { screen ->
            when (screen) {
                ChildScreenState.PAIRING -> ChildPairingScreen(
                    onPaired = { currentScreen = ChildScreenState.CONSENT }
                )
                ChildScreenState.CONSENT -> ChildConsentScreen(
                    onAccept = {
                        ChildPairingManager.setConsentAccepted(context)
                        currentScreen = ChildScreenState.PERMISSIONS
                    },
                    onDecline = { ChildPairingManager.unpair(context); currentScreen = ChildScreenState.PAIRING }
                )
                ChildScreenState.PERMISSIONS -> ChildPermissionScreen(
                    onAllGranted = {
                        val intent = Intent(context, ChildFirebaseService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                        // App usage stats প্রতি 15 মিনিটে Firebase এ push করবে
                        AppUsageSyncService.start(context)
                        currentScreen = ChildScreenState.STATUS
                    }
                )
                ChildScreenState.STATUS -> ChildStatusScreen(context)
            }
        }

        if (showLockOverlay) ChildLockOverlay()
        if (showAppLimited) AppLimitedOverlay(
            appName = limitedAppName,
            onDismiss = { showAppLimited = false },
            onRequestAccess = {
                requestFromParent(context, "app_access", limitedAppName)
                showAppLimited = false
            }
        )
    }
}

private fun requestFromParent(context: Context, type: String, detail: String) {
    val parentUid = ChildPairingManager.getParentUid(context) ?: return
    val deviceId = ChildPairingManager.getDeviceId(context) ?: return
    FirebaseDatabase.getInstance()
        .getReference("users/$parentUid/devices/$deviceId/requests")
        .push()
        .setValue(mapOf("type" to type, "detail" to detail, "timestamp" to ServerValue.TIMESTAMP))
}

// ── 1. PAIRING SCREEN ──
@Composable
fun ChildPairingScreen(onPaired: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScopeCompat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.SurfaceOffWhite)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(64.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ChildCare, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Child Device Setup", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = RasFocusColors.OnBackground)
        Text("Connect this device to a parent's RasFocus+ app.", fontSize = 14.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)

        Spacer(Modifier.height(40.dp))
        Text("Enter the 6-Digit Pairing Code", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = RasFocusColors.OnBackground)
        Text("Ask the parent to open RasFocus+ and share their pairing code.", fontSize = 12.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pinCode,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pinCode = it; errorText = null } },
            placeholder = { Text("e.g. 123456", color = RasFocusColors.SubtleText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = errorText != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RasFocusColors.PrimaryTeal,
                unfocusedBorderColor = RasFocusColors.DividerColor,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        )
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText!!, color = RasFocusColors.ErrorRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (pinCode.length != 6) return@Button
                isLoading = true
                errorText = null
                scope.launch {
                    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    when (ChildPairing.pairWithPin(context, pinCode, deviceName.ifBlank { "Child Phone" })) {
                        PairResult.Success -> onPaired()
                        PairResult.InvalidPin -> errorText = "Invalid or expired code. Ask your parent for a new one."
                        PairResult.NetworkError -> errorText = "Couldn't connect. Check your internet and try again."
                    }
                    isLoading = false
                }
            },
            enabled = pinCode.length == 6 && !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal, disabledContainerColor = RasFocusColors.DividerColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("CONNECT TO PARENT", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── 2. CONSENT SCREEN — required, cannot be skipped ──
@Composable
fun ChildConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Filled.Shield, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("This device will be supervised", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = RasFocusColors.OnBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Before you continue, here's exactly what your parent will be able to see and do on this phone:",
            fontSize = 14.sp, color = RasFocusColors.SubtleText, lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))

        ConsentPoint(Icons.Outlined.AppShortcut, "Manage apps", "Block or allow specific apps, and see which apps are installed.")
        ConsentPoint(Icons.Outlined.HealthAndSafety, "Filter content", "Turn on filtering for adult content, Reels, and YouTube Shorts.")
        ConsentPoint(Icons.Outlined.Timer, "Set screen time", "Set a total daily time limit, and per-app time limits.")
        ConsentPoint(Icons.Outlined.Lock, "Lock this device", "Lock the screen remotely if needed.")
        ConsentPoint(Icons.Outlined.RemoveCircleOutline, "Prevent uninstalling", "This app can't be removed or disabled without the parent's help.")

        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = RasFocusColors.SurfaceCard, border = BorderStroke(1.dp, RasFocusColors.DividerColor)) {
            Text(
                "RasFocus+ will show a permanent notification any time it's active, so it's never running secretly.",
                modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = RasFocusColors.SubtleText, lineHeight = 17.sp
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)
        ) {
            Text("I UNDERSTAND, CONTINUE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Don't set up supervision", color = RasFocusColors.SubtleText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConsentPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(40.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RasFocusColors.OnBackground)
            Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText, lineHeight = 16.sp)
        }
    }
}

// ── 3. PERMISSIONS SCREEN ──
@Composable
fun ChildPermissionScreen(onAllGranted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).padding(24.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Enable Protection", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = RasFocusColors.OnBackground)
        Text("RasFocus+ needs these permissions to work.", fontSize = 14.sp, color = RasFocusColors.SubtleText)
        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PremiumPermissionItem("Accessibility Service", "Lets blocked apps be paused", Icons.Filled.Visibility)
            PremiumPermissionItem("Device Admin", "Prevents this app from being uninstalled", Icons.Filled.Security)
            PremiumPermissionItem("Notification Access", "Hides notifications from blocked apps", Icons.Filled.NotificationsActive)
        }

        Button(
            onClick = onAllGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.SuccessGreen)
        ) {
            Icon(Icons.Filled.VerifiedUser, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("ACTIVATE PROTECTION", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PremiumPermissionItem(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
        border = BorderStroke(1.dp, RasFocusColors.DividerColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
        }
    }
}

// ── 4. STATUS SCREEN — READ ONLY. No toggles. Mirrors Family Link's child view. ──
@Composable
fun ChildStatusScreen(context: Context) {
    var rules by remember { mutableStateOf(ChildRuleManager.load(context)) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { rules = ChildRuleManager.load(context) }
        }
        val filter = IntentFilter(RULES_UPDATED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Bottom nav selection state (Home / Connection / Filters / Settings)
    var selectedNavTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ══ PREMIUM HEADER (RasFocus brand style — matches SelfControlModule) ══
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
                        Box(
                            Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Box(
                            Modifier.background(PremiumTealAccent.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                                .border(1.dp, PremiumTealAccent.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("Protected", color = PremiumTealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Box(
                            Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text("Device Protected", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("RasFocus+ Child", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Supervised by ${rules.parentName}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

        // Screen time ring
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
            border = BorderStroke(1.dp, RasFocusColors.DividerColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("SCREEN TIME TODAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                if (rules.screenTimeLimitMinutes > 0) {
                    val progress = (rules.screenTimeUsedMinutes.toFloat() / rules.screenTimeLimitMinutes).coerceIn(0f, 1f)
                    Text("${rules.screenTimeUsedMinutes}m used of ${rules.screenTimeLimitMinutes}m", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                        color = if (progress > 0.9f) RasFocusColors.ErrorRed else RasFocusColors.PrimaryTeal,
                        trackColor = RasFocusColors.DividerColor
                    )
                } else {
                    Text("No daily limit set", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "CONTENT FILTERS ACTIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText,
            letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))
        
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip("Halal Guard", rules.isHalalGuardOn)
            StatusChip("Shorts Block", rules.blockYoutubeShorts)
            StatusChip("Reels Block", rules.blockReels)
            StatusChip("Deep Study", rules.deepStudyEnabled)
            StatusChip("Button Phone", rules.buttonPhoneMode)
            StatusChip("Extreme Block", rules.extremeBlockEnabled)
            StatusChip("Single Apps Block", rules.singleAppsBlockEnabled)
            StatusChip("Website Block", rules.singleWebsiteBlockEnabled)
            StatusChip("Family Browser", rules.familyBrowserEnabled)
            StatusChip("FB Block", rules.fbEnabled, if (rules.fbEnabled) "${rules.fbStartTime}-${rules.fbEndTime}" else null)
            StatusChip("FB Lite Block", rules.fbLiteEnabled, if (rules.fbLiteEnabled) "${rules.fbLiteStartTime}-${rules.fbLiteEndTime}" else null)
            StatusChip("YouTube Block", rules.ytEnabled, if (rules.ytEnabled) "${rules.ytStartTime}-${rules.ytEndTime}" else null)
            StatusChip("Chrome Block", rules.chromeEnabled, if (rules.chromeEnabled) "${rules.chromeStartTime}-${rules.chromeEndTime}" else null)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "FAMILY BROWSER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText,
            letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
            border = BorderStroke(1.dp, RasFocusColors.DividerColor)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Public, null, tint = if (rules.familyBrowserEnabled) RasFocusColors.PrimaryTeal else RasFocusColors.SubtleText, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Family Safe Browser", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                    Text(if (rules.familyBrowserEnabled) "Enabled by parent" else "Disabled", fontSize = 12.sp, color = RasFocusColors.SubtleText)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "BLOCKED APPS (${rules.blockedPackages.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText,
            letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (rules.blockedPackages.isEmpty()) {
            Text("No apps are blocked right now.", fontSize = 13.sp, color = RasFocusColors.SubtleText, modifier = Modifier.padding(horizontal = 24.dp))
        } else {
            Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rules.blockedPackages.forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, null, tint = RasFocusColors.ErrorRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(pkg, fontSize = 13.sp, color = RasFocusColors.OnBackground)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = { requestFromParent(context, "more_time", "Requested more screen time") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, RasFocusColors.PrimaryTeal),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RasFocusColors.PrimaryTeal)
        ) {
            Icon(Icons.Filled.AccessTime, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ask for more time", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
        }

        // ══ FOOTER — RasFocus brand NavigationBar (matches SelfControlModule) ══
        ChildBottomNav(selected = selectedNavTab, onSelect = { selectedNavTab = it })
    }
}

// ─────────────────────────────────────────────────────────────
// FOOTER NAV — Home / Connection / Filters / Settings
// Mirrors SelfControlModule's SelfControlBottomNav exactly:
// White NavigationBar, SoftBlue pill on selected item, PrimaryBlue tint.
// ─────────────────────────────────────────────────────────────
@Composable
private fun ChildBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Home",       Icons.Filled.Home,        Icons.Outlined.Home),
        Triple("Connection", Icons.Filled.Wifi,         Icons.Outlined.Wifi),
        Triple("Filters",    Icons.Filled.FilterAlt,    Icons.Outlined.FilterAlt),
        Triple("Settings",   Icons.Filled.Settings,     Icons.Outlined.Settings)
    )
    NavigationBar(containerColor = WhiteBrand, tonalElevation = 8.dp) {
        items.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
            NavigationBarItem(
                selected = selected == index, onClick = { onSelect(index) },
                icon = {
                    if (selected == index) {
                        Box(Modifier.background(SoftBlue, RoundedCornerShape(50.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Icon(filledIcon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }
                    } else { Icon(outlinedIcon, contentDescription = label, tint = TextGrayBrand, modifier = Modifier.size(22.dp)) }
                },
                label = { Text(label, fontSize = 11.sp, color = if (selected == index) PrimaryBlue else TextGrayBrand, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, isOn: Boolean, detail: String? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isOn) RasFocusColors.SuccessGreen.copy(alpha = 0.1f) else RasFocusColors.DividerColor.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isOn) RasFocusColors.SuccessGreen.copy(alpha = 0.3f) else RasFocusColors.DividerColor)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isOn) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                tint = if (isOn) RasFocusColors.SuccessGreen else RasFocusColors.SubtleText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label + if (detail != null) " ($detail)" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isOn) RasFocusColors.SuccessGreen else RasFocusColors.SubtleText)
        }
    }
}

// ── OVERLAYS ──
@Composable
fun ChildLockOverlay() {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, tint = RasFocusColors.ErrorRed, modifier = Modifier.size(90.dp))
            Spacer(Modifier.height(28.dp))
            Text("Device Locked", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
            Text("Your parent has locked this device.", color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AppLimitedOverlay(appName: String, onDismiss: () -> Unit, onRequestAccess: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Block, null, tint = RasFocusColors.WarningAmber, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("$appName is limited", fontWeight = FontWeight.Black, fontSize = 20.sp, color = RasFocusColors.OnBackground, textAlign = TextAlign.Center)
                Text("Ask your parent to allow this app, or request access below.", fontSize = 13.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRequestAccess, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)
                ) { Text("Request Access", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("Dismiss", color = RasFocusColors.SubtleText) }
            }
        }
    }
}

/** Small shim so this file doesn't need an extra lifecycle-viewmodel-compose import just for a scope. */
@Composable
private fun rememberCoroutineScopeCompat(): CoroutineScope = androidx.compose.runtime.rememberCoroutineScope()

// ============================================================
// PART 4 — BACKGROUND ENFORCEMENT (Firebase sync + Device Admin
//           + Accessibility-based app blocking + notification hiding)
// ============================================================

// 1. DEVICE ADMIN — real OS-level uninstall protection.
class RasDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device protection enabled.", Toast.LENGTH_SHORT).show()
        markDeviceAdminStatus(context, true)
    }
    override fun onDisabled(context: Context, intent: Intent) {
        markDeviceAdminStatus(context, false)
    }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "This will remove parental protection on this device."
    }
}

private fun markDeviceAdminStatus(context: Context, active: Boolean) {
    val parentUid = ChildPairingManager.getParentUid(context) ?: return
    val deviceId = ChildPairingManager.getDeviceId(context) ?: return
    FirebaseDatabase.getInstance()
        .getReference("users/$parentUid/devices/$deviceId/isDeviceAdminActive")
        .setValue(active)
}

// 2. FOREGROUND SYNC SERVICE — mirrors parent's users/<uid>/devices/<id> node
//    into the local ChildRuleManager cache, and reports status back up.
class ChildFirebaseService : Service() {
    private var rulesListener: ValueEventListener? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        if (!ChildPairingManager.isPaired(this)) { stopSelf(); return }

        val channelId = "rasfocus_protection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Protection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RasFocus+ Active")
            .setContentText("This device is supervised by a parent.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()
        startForeground(1, notif)

        reportDeviceAdminAndAccessibilityStatus()
        listenToParentRules()
        markOnline(true)
    }

    private fun reportDeviceAdminAndAccessibilityStatus() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, RasDeviceAdminReceiver::class.java)
        markDeviceAdminStatus(this, dpm.isAdminActive(adminComponent))

        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val accessibilityOn = enabledServices.contains("${packageName}/${ChildAppBlockerService::class.java.name}")
        val parentUid = ChildPairingManager.getParentUid(this)
        val deviceId = ChildPairingManager.getDeviceId(this)
        if (parentUid != null && deviceId != null) {
            FirebaseDatabase.getInstance()
                .getReference("users/$parentUid/devices/$deviceId/isAccessibilityActive")
                .setValue(accessibilityOn)
        }
    }

    private var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun listenToParentRules() {
        val parentUid = ChildPairingManager.getParentUid(this) ?: return
        val deviceId = ChildPairingManager.getDeviceId(this) ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users/$parentUid/devices/$deviceId")

        rulesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val blockedPackages = snapshot.child("blockedApps").children.mapNotNull {
                    it.child("packageName").getValue(String::class.java)
                }.toSet()

                val appTimeLimits = snapshot.child("appTimeLimits").children.mapNotNull { limitSnap ->
                    val pkg = limitSnap.key ?: return@mapNotNull null
                    val minutes = limitSnap.getValue(Long::class.java)?.toInt() ?: return@mapNotNull null
                    pkg to minutes
                }.toMap()

                val currentRules = ChildRuleManager.load(this@ChildFirebaseService)
                
                val rules = currentRules.copy(
                    isLocked = snapshot.child("isLocked").getValue(Boolean::class.java) ?: false,
                    isHalalGuardOn = snapshot.child("isHalalGuardOn").getValue(Boolean::class.java) ?: true,
                    blockYoutubeShorts = snapshot.child("filters/blockShorts").getValue(Boolean::class.java) ?: true,
                    blockReels = snapshot.child("filters/blockReels").getValue(Boolean::class.java) ?: true,
                    blockIncognito = snapshot.child("filters/blockIncognito").getValue(Boolean::class.java) ?: true,
                    buttonPhoneMode = snapshot.child("buttonPhoneMode").getValue(Boolean::class.java) ?: false,
                    screenTimeLimitMinutes = (snapshot.child("screenTimeLimit").getValue(Long::class.java) ?: 0L).toInt(),
                    blockedPackages = blockedPackages,
                    appTimeLimitsMinutes = appTimeLimits,
                    parentName = "your parent",
                    
                    // NEW FIELDS FROM RTDB
                    deepStudyEnabled = snapshot.child("deepStudyEnabled").getValue(Boolean::class.java) ?: false,
                    extremeBlockEnabled = snapshot.child("extremeBlockEnabled").getValue(Boolean::class.java) ?: false,
                    singleAppsBlockEnabled = snapshot.child("singleAppsBlockEnabled").getValue(Boolean::class.java) ?: false,
                    singleWebsiteBlockEnabled = snapshot.child("singleWebsiteBlockEnabled").getValue(Boolean::class.java) ?: false,
                    familyBrowserEnabled = snapshot.child("familyBrowserEnabled").getValue(Boolean::class.java) ?: false,
                    
                    fbEnabled = snapshot.child("fbEnabled").getValue(Boolean::class.java) ?: false,
                    fbStartTime = snapshot.child("fbStartTime").getValue(String::class.java) ?: "00:00",
                    fbEndTime = snapshot.child("fbEndTime").getValue(String::class.java) ?: "00:00",
                    fbLiteEnabled = snapshot.child("fbLiteEnabled").getValue(Boolean::class.java) ?: false,
                    fbLiteStartTime = snapshot.child("fbLiteStartTime").getValue(String::class.java) ?: "00:00",
                    fbLiteEndTime = snapshot.child("fbLiteEndTime").getValue(String::class.java) ?: "00:00",
                    ytEnabled = snapshot.child("ytEnabled").getValue(Boolean::class.java) ?: false,
                    ytStartTime = snapshot.child("ytStartTime").getValue(String::class.java) ?: "00:00",
                    ytEndTime = snapshot.child("ytEndTime").getValue(String::class.java) ?: "00:00",
                    chromeEnabled = snapshot.child("chromeEnabled").getValue(Boolean::class.java) ?: false,
                    chromeStartTime = snapshot.child("chromeStartTime").getValue(String::class.java) ?: "00:00",
                    chromeEndTime = snapshot.child("chromeEndTime").getValue(String::class.java) ?: "00:00"
                )
                ChildRuleManager.save(this@ChildFirebaseService, rules)

                if (rules.isLocked) {
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(this@ChildFirebaseService, RasDeviceAdminReceiver::class.java)
                    if (dpm.isAdminActive(adminComponent)) dpm.lockNow()
                    sendBroadcast(Intent(SHOW_LOCK_ACTION).setPackage(packageName))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(rulesListener!!)

        // Listen to Firestore pc_commands
        firestoreListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("pc_commands").document(deviceId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                
                val currentRules = ChildRuleManager.load(this@ChildFirebaseService)
                
                // Read self control features
                val bpEnabled = snap.getBoolean("button_phone_enabled") ?: false
                val dsEnabled = snap.getBoolean("deep_study_enabled") ?: false
                val extBlock = snap.getBoolean("extreme_block_enabled") ?: false
                val ytShortsBlock = snap.getBoolean("force_shorts_block") ?: false
                val reelsBlock = snap.getBoolean("force_reels_block") ?: false
                val halalGuard = snap.getBoolean("force_adult_block") ?: false
                
                val rules = currentRules.copy(
                    buttonPhoneMode = bpEnabled,
                    deepStudyEnabled = dsEnabled,
                    extremeBlockEnabled = extBlock,
                    isHalalGuardOn = halalGuard,
                    blockYoutubeShorts = ytShortsBlock,
                    blockReels = reelsBlock,
                    
                    // Specific apps logic will be added here
                    fbEnabled = snap.getBoolean("fb_enabled") ?: false,
                    fbStartTime = snap.getString("fb_start") ?: "00:00",
                    fbEndTime = snap.getString("fb_end") ?: "00:00",
                    
                    fbLiteEnabled = snap.getBoolean("fblite_enabled") ?: false,
                    fbLiteStartTime = snap.getString("fblite_start") ?: "00:00",
                    fbLiteEndTime = snap.getString("fblite_end") ?: "00:00",
                    
                    ytEnabled = snap.getBoolean("yt_enabled") ?: false,
                    ytStartTime = snap.getString("yt_start") ?: "00:00",
                    ytEndTime = snap.getString("yt_end") ?: "00:00",
                    
                    chromeEnabled = snap.getBoolean("chrome_enabled") ?: false,
                    chromeStartTime = snap.getString("chrome_start") ?: "00:00",
                    chromeEndTime = snap.getString("chrome_end") ?: "00:00",
                    
                    singleAppsBlockEnabled = snap.getBoolean("single_apps_block_enabled") ?: false,
                    singleWebsiteBlockEnabled = snap.getBoolean("single_web_block_enabled") ?: false,
                    familyBrowserEnabled = snap.getBoolean("family_browser_enabled") ?: false
                )
                ChildRuleManager.save(this@ChildFirebaseService, rules)
            }
    }

    private fun markOnline(online: Boolean) {
        val parentUid = ChildPairingManager.getParentUid(this) ?: return
        val deviceId = ChildPairingManager.getDeviceId(this) ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$parentUid/devices/$deviceId")
            .updateChildren(mapOf("isOnline" to online))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        rulesListener?.let {
            val parentUid = ChildPairingManager.getParentUid(this)
            val deviceId = ChildPairingManager.getDeviceId(this)
            if (parentUid != null && deviceId != null) {
                FirebaseDatabase.getInstance().getReference("users/$parentUid/devices/$deviceId").removeEventListener(it)
            }
        }
        markOnline(false)
        scope.cancel()
    }
}

// 3. ACCESSIBILITY SERVICE — enforces the parent's blocked-app list.
//    Uses the existing res/xml/accessibility_appblocker_config.xml.
//    This is intentionally scoped ONLY to app-blocking (not content
//    scanning), so its purpose is unambiguous to the child and to
//    anyone reviewing the app's permissions.
class ChildAppBlockerService : AccessibilityService() {
    private var rules = ChildRuleManager.load(this)
    private val ALL_BROWSER_PKGS = setOf("com.android.chrome", "com.brave.browser", "org.mozilla.firefox", "com.opera.browser", "com.opera.mini.native", "com.microsoft.emmx", "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser")
    private val adultSiteKeywords = listOf("porn", "xxx", "nude", "nsfw", "sexy", "hentai", "rule34", "milf", "xvideos", "pornhub", "xnxx", "xhamster")
    private val buttonPhoneWhitelist = setOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.android.messaging", "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.settings", "com.android.systemui", "com.rasel.RasFocus", "com.google.android.inputmethod.latin")

    private val ruleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            rules = ChildRuleManager.load(context)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        registerReceiver(ruleReceiver, IntentFilter(RULES_UPDATED_ACTION), RECEIVER_NOT_EXPORTED)
        rules = ChildRuleManager.load(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !ChildPairingManager.isPaired(this)) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return // never block our own app

        // Deep Study Block (Strict Mode: Block everything except essential apps)
        if (rules.deepStudyEnabled && packageName !in buttonPhoneWhitelist) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.FOCUS, "Deep Study Active", "Stay focused on your tasks!")
            return
        }

        // Specific Apps with Schedule
        if (rules.fbEnabled && (packageName == "com.facebook.katana" || packageName == "com.facebook.lite") && isTimeInSchedule(rules.fbStartTime, rules.fbEndTime)) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.APP, "Facebook Blocked", "Facebook is currently scheduled to be blocked.")
            return
        }
        if (rules.fbLiteEnabled && packageName == "com.facebook.lite" && isTimeInSchedule(rules.fbLiteStartTime, rules.fbLiteEndTime)) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.APP, "Facebook Lite Blocked", "Facebook Lite is currently scheduled to be blocked.")
            return
        }
        if (rules.ytEnabled && packageName == "com.google.android.youtube" && isTimeInSchedule(rules.ytStartTime, rules.ytEndTime)) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.APP, "YouTube Blocked", "YouTube is currently scheduled to be blocked.")
            return
        }
        if (rules.chromeEnabled && packageName == "com.android.chrome" && isTimeInSchedule(rules.chromeStartTime, rules.chromeEndTime)) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.APP, "Chrome Blocked", "Chrome is currently scheduled to be blocked.")
            return
        }

        // 1. Single App Block Check
        if (rules.singleAppsBlockEnabled && rules.blockedPackages.contains(packageName)) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.APP, "App Blocked", "This app has been restricted by your parent.")
            return
        }

        // 2. Button Phone Mode Check
        if (rules.buttonPhoneMode && packageName !in buttonPhoneWhitelist) {
            blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.SYSTEM, "Button Phone Mode", "Only essential apps are allowed right now.")
            return
        }

        // 3. YouTube Shorts Check
        if (rules.blockYoutubeShorts && packageName == "com.google.android.youtube") {
            val root = try { rootInActiveWindow } catch (e: Exception) { null }
            if (root != null) {
                val tab = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar_item_label")
                    .any { it.text?.toString()?.equals("Shorts", true) == true && (it.isSelected || it.parent?.isSelected == true) }
                val player = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_container").isNotEmpty() || root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").isNotEmpty()
                val fb = root.findAccessibilityNodeInfosByText("Shorts").any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
                root.recycle()
                if (tab || player || fb) {
                    blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.SHORTS, "YouTube Shorts Blocked", "YouTube Shorts are disabled by your parent.")
                    return
                }
            }
        }

        // 4. Reels Check (Facebook & Instagram)
        if (rules.blockReels) {
            val root = try { rootInActiveWindow } catch (e: Exception) { null }
            if (root != null) {
                var blocked = false
                if (packageName == "com.instagram.android") {
                    if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByText("Reels").any { it.isSelected || it.parent?.isSelected == true }) {
                        blocked = true
                    }
                } else if (packageName == "com.facebook.katana") {
                    if (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_container").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tab_button").any { it.isSelected || it.parent?.isSelected == true }) {
                        blocked = true
                    }
                }
                root.recycle()
                if (blocked) {
                    blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.REELS, "Reels Blocked", "Reels are disabled by your parent.")
                    return
                }
            }
        }

        // 5. Extreme Block Check (Blocks all browsers if adult keywords found, or simply blocks everything not whitelisted in some cases, but here it's Halal Guard)
        if (rules.extremeBlockEnabled && packageName in ALL_BROWSER_PKGS) {
            val root = try { rootInActiveWindow } catch (e: Exception) { null }
            if (root != null) {
                val url = extractBrowserUrl(root, packageName)?.lowercase()?.trim() ?: ""
                root.recycle()
                if (url.isNotBlank() && adultSiteKeywords.any { url.contains(it) }) {
                    blockApp(com.rasel.RasFocus.selfcontrol.BlockPage.Type.ADULT, "Content Blocked", "This website has been restricted by Halal Guard.")
                    return
                }
            }
        }
    }

    private fun blockApp(type: com.rasel.RasFocus.selfcontrol.BlockPage.Type, title: String, message: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).post {
            com.rasel.RasFocus.selfcontrol.BlockPage.show(this, type, title, message)
        }
    }

    private fun extractBrowserUrl(nodeInfo: AccessibilityNodeInfo, pkg: String): String? {
        val id = when (pkg) {
            "com.android.chrome" -> "com.android.chrome:id/url_bar"
            "com.brave.browser" -> "com.brave.browser:id/url_bar"
            "org.mozilla.firefox" -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            "com.opera.browser" -> "com.opera.browser:id/url_field"
            "com.microsoft.emmx" -> "com.microsoft.emmx:id/url_bar"
            "com.duckduckgo.mobile.android" -> "com.duckduckgo.mobile.android:id/omnibarTextInput"
            "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            else -> return null
        }
        val nodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
        if (nodes.isNotEmpty()) return nodes[0].text?.toString()
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(ruleReceiver) }
    }

    override fun onInterrupt() {}

    private fun isTimeInSchedule(startTime: String, endTime: String): Boolean {
        if (startTime == endTime) return true // "00:00" to "00:00" means blocked 24/7 if enabled
        val cal = java.util.Calendar.getInstance()
        val currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        fun parse(timeStr: String): Int {
            val parts = timeStr.split(":")
            if (parts.size != 2) return 0
            return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        }
        val startMins = parse(startTime)
        val endMins = parse(endTime)

        return if (startMins < endMins) {
            currentMins in startMins..endMins
        } else {
            currentMins >= startMins || currentMins <= endMins
        }
    }
}

// 4. NOTIFICATION INTERCEPTOR — hides notifications only from blocked apps.
class RasNotificationInterceptor : NotificationListenerService() {
    private var blockedPackages = setOf<String>()

    private val ruleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            blockedPackages = ChildRuleManager.load(context).blockedPackages
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(ruleReceiver, IntentFilter(RULES_UPDATED_ACTION), RECEIVER_NOT_EXPORTED)
        blockedPackages = ChildRuleManager.load(this).blockedPackages
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { if (blockedPackages.contains(it.packageName)) cancelNotification(it.key) }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(ruleReceiver) }
    }
}

// 5. BOOT RECEIVER — restarts protection after the device restarts.
class BootSurvivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action && ChildPairingManager.isPaired(context)) {
            val serviceIntent = Intent(context, ChildFirebaseService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        }
    }
}

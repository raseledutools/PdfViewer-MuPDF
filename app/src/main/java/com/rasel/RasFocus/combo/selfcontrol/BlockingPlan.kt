package com.rasel.RasFocus.combo.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Compose Imports
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter

// ==========================================
// 1. COLORS & THEME
// ==========================================
private val PrimaryTeal = Color(0xFF0096B4)
private val DarkText      = Color(0xFF1A1A1A)
private val GrayText      = Color(0xFF6B7280)
private val CardBg        = Color(0xFFFFFFFF)
private val RedIcon       = Color(0xFFEF4444)
private val BackgroundColor = Color(0xFFF3F4F6)

// Default Long Text for Mode 2
private const val DEFAULT_LONG_TEXT = "Focus is the key to success. I choose to block distractions and work with full dedication. Social media and games can wait, but my goals cannot. By typing this text, I acknowledge that I am breaking my commitment. I will take a deep breath and rethink before unlocking this profile. Consistency builds the foundation of a greater future. I will not give up easily."

// ==========================================
// 2. DATA CLASSES & ENUMS
// ==========================================
data class BlockingFocusProfile(
    val id: Int,
    val profileName: String,
    val webCount: Int,
    val appCount: Int,
    val mode: String,
    var isActive: Boolean,
    
    val profileType: String = "Block", // "Block" or "Allow"
    val blockPhoneSettings: Boolean = false,
    val uninstallProtection: Boolean = false,

    val lockMode: Int = 0,          
    val selfDays: Int = 0,
    val selfHours: Int = 0,
    val selfMinutes: Int = 25,
    val dailyLimitMinutes: Int = 60,
    val hourlyLimitMinutes: Int = 10,
    val parentPin: String = "",
    val customLongText: String = DEFAULT_LONG_TEXT, // User customizable long text
    val blockedPackages: List<String> = emptyList(),
    val blockedSites: List<String> = emptyList()
)

data class LockModeOption(
    val index: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

data class BlockingInstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

private val lockModeOptions = listOf(
    LockModeOption(0, "Self Control", "Day / Hour / Minute timer", Icons.Default.Timer, Color(0xFF0096B4)),
    LockModeOption(1, "Parents Control", "আনলক করতে Password লাগবে", Icons.Default.LockPerson, Color(0xFFEAB308)),
    LockModeOption(2, "Long Text", "আনলক করতে paragraph টাইপ করতে হবে", Icons.Default.Subject, Color(0xFF3B82F6)),
    LockModeOption(3, "Daily Limit", "সারাদিনে মোট কতক্ষণ ব্যবহার করা যাবে (মিনিট)", Icons.Default.CalendarToday, Color(0xFF8B5CF6)),
    LockModeOption(4, "Hourly Limit", "প্রতি ঘণ্টায় কতক্ষণ ব্যবহার করা যাবে (মিনিট)", Icons.Default.HourglassEmpty, Color(0xFFF97316))
)

// ==========================================
// 3. MAIN BLOCKING PLAN SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingPlanScreen(navController: NavController) {
    val context = LocalContext.current

    var hasUsagePerm         by remember { mutableStateOf(BlockingManager.hasUsageStatsPermission(context)) }
    var hasOverlayPerm       by remember { mutableStateOf(BlockingManager.hasOverlayPermission(context)) }
    var hasAccessibilityPerm by remember { mutableStateOf(BlockingManager.isWebsiteBlockingServiceEnabled(context)) }

    LaunchedEffect(Unit) {
        hasUsagePerm         = BlockingManager.hasUsageStatsPermission(context)
        hasOverlayPerm       = BlockingManager.hasOverlayPermission(context)
        hasAccessibilityPerm = BlockingManager.isWebsiteBlockingServiceEnabled(context)
    }

    // Resume থেকে ফিরে এলে permission recheck করো
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsagePerm         = BlockingManager.hasUsageStatsPermission(context)
                hasOverlayPerm       = BlockingManager.hasOverlayPermission(context)
                hasAccessibilityPerm = BlockingManager.isWebsiteBlockingServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsOk = hasUsagePerm && hasOverlayPerm

    var profiles by remember { mutableStateOf(BlockingManager.loadProfiles(context)) }

    LaunchedEffect(profiles) {
        BlockingManager.saveProfiles(context, profiles)
        val anyActive = profiles.any { it.isActive }
        if (anyActive && permissionsOk) AppBlockerService.start(context)
        else if (!anyActive)            AppBlockerService.stop(context)
    }

    var pendingProfile by remember { mutableStateOf<BlockingFocusProfile?>(null) }
    var profileToTurnOff by remember { mutableStateOf<BlockingFocusProfile?>(null) }
    
    var showSelfControlDialog  by remember { mutableStateOf(false) }
    var showParentsSetDialog   by remember { mutableStateOf(false) }
    var showParentsUnlockDialog by remember { mutableStateOf(false) }
    var showLongTextUnlockDialog by remember { mutableStateOf(false) }
    var showDailyLimitDialog   by remember { mutableStateOf(false) }
    var showHourlyLimitDialog  by remember { mutableStateOf(false) }
    
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingProfile  by remember { mutableStateOf<BlockingFocusProfile?>(null) }

    // Dialogs for Activating Profiles
    if (showSelfControlDialog && pendingProfile != null) {
        val p = pendingProfile!!
        SelfControlActivateDialog(
            initialDays = p.selfDays, initialHours = p.selfHours, initialMinutes = p.selfMinutes,
            onDismiss = { showSelfControlDialog = false; pendingProfile = null },
            onConfirm = { d, h, m ->
                profiles = profiles.map { if (it.id == p.id) it.copy(isActive = true, selfDays = d, selfHours = h, selfMinutes = m) else it }
                context.getSharedPreferences("blocking_profiles", Context.MODE_PRIVATE).edit().putLong("activated_at_${p.id}", System.currentTimeMillis()).apply()
                showSelfControlDialog = false; pendingProfile = null
            }
        )
    }

    if (showParentsSetDialog && pendingProfile != null) {
        val p = pendingProfile!!
        ParentsSetPinDialog(
            onDismiss = { showParentsSetDialog = false; pendingProfile = null },
            onConfirm = { pin -> 
                profiles = profiles.map { if (it.id == p.id) it.copy(isActive = true, parentPin = pin) else it }
                showParentsSetDialog = false; pendingProfile = null 
            }
        )
    }

    if (showDailyLimitDialog && pendingProfile != null) {
        val p = pendingProfile!!
        DailyLimitActivateDialog(
            initialMinutes = p.dailyLimitMinutes,
            onDismiss = { showDailyLimitDialog = false; pendingProfile = null },
            onConfirm = { mins -> profiles = profiles.map { if (it.id == p.id) it.copy(isActive = true, dailyLimitMinutes = mins) else it }; showDailyLimitDialog = false; pendingProfile = null }
        )
    }

    if (showHourlyLimitDialog && pendingProfile != null) {
        val p = pendingProfile!!
        HourlyLimitActivateDialog(
            initialMinutes = p.hourlyLimitMinutes,
            onDismiss = { showHourlyLimitDialog = false; pendingProfile = null },
            onConfirm = { mins -> profiles = profiles.map { if (it.id == p.id) it.copy(isActive = true, hourlyLimitMinutes = mins) else it }; showHourlyLimitDialog = false; pendingProfile = null }
        )
    }

    // Dialogs for Deactivating Profiles
    if (showParentsUnlockDialog && profileToTurnOff != null) {
        val p = profileToTurnOff!!
        ParentsUnlockDialog(
            storedPin = p.parentPin,
            onDismiss = { showParentsUnlockDialog = false; profileToTurnOff = null },
            onConfirm = { 
                profiles = profiles.map { if (it.id == p.id) it.copy(isActive = false) else it }
                showParentsUnlockDialog = false; profileToTurnOff = null 
            }
        )
    }

    if (showLongTextUnlockDialog && profileToTurnOff != null) {
        val p = profileToTurnOff!!
        LongTextUnlockDialog(
            targetText = p.customLongText,
            onDismiss = { showLongTextUnlockDialog = false; profileToTurnOff = null },
            onConfirm = { 
                profiles = profiles.map { if (it.id == p.id) it.copy(isActive = false) else it }
                showLongTextUnlockDialog = false; profileToTurnOff = null 
            }
        )
    }

    // Sheets for Edit & Create
    if (editingProfile != null) {
        EditProfileSheet(
            profile   = editingProfile!!,
            onDismiss = { editingProfile = null },
            onSave    = { updated -> 
                profiles = profiles.map { if (it.id == updated.id) updated else it }
                editingProfile = null
                Toast.makeText(context, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    if (showCreateSheet) {
        CreateProfileSheet(
            onDismiss = { showCreateSheet = false },
            onCreate  = { newProfile -> 
                profiles = profiles + newProfile.copy(id = (profiles.maxOfOrNull { it.id } ?: 0) + 1)
                showCreateSheet = false 
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Focus Profiles", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
                        Text("Apps ও websites ব্লক করো", fontSize = 12.sp, color = GrayText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        containerColor = BackgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // কোনো profile-এ app থাকলেই usage/overlay banner দেখাও
            val anyProfileHasApps     = profiles.any { it.blockedPackages.isNotEmpty() }
            // কোনো profile-এ website থাকলেই accessibility banner দেখাও
            val anyProfileHasWebsites = profiles.any { it.blockedSites.isNotEmpty() }

            if (anyProfileHasApps && !hasUsagePerm) {
                PermissionBanner(Icons.Default.QueryStats, "Usage Access দাও", "কোন app foreground-এ আছে সেটা জানতে এই permission দরকার।", Color(0xFFF97316)) {
                    BlockingManager.openUsageAccessSettings(context)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (anyProfileHasApps && !hasOverlayPerm) {
                PermissionBanner(Icons.Default.Layers, "Overlay Permission দাও", "Block চলাকালীন full-screen overlay দেখাতে এই permission দরকার।", Color(0xFF3B82F6)) {
                    BlockingManager.openOverlaySettings(context)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (anyProfileHasWebsites && !hasAccessibilityPerm) {
                PermissionBanner(Icons.Default.Accessibility, "Accessibility Permission দাও", "Website block করতে Accessibility Service enable করতে হবে।", Color(0xFF8B5CF6)) {
                    BlockingManager.openAccessibilitySettings(context)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))

            Button(
                onClick = { showCreateSheet = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("নতুন Profile যোগ করো", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(profiles) { profile ->
                    // Self Control restrictions logic
                    val isSelfExpired = BlockingManager.isSelfControlExpired(context, profile.id, profile)
                    val isLockedBySelfControl = profile.isActive && profile.lockMode == 0 && !isSelfExpired

                    ProfileCardItem(
                        profile  = profile,
                        isLockedBySelfControl = isLockedBySelfControl,
                        onToggle = { shouldActivate ->
                            if (shouldActivate) {
                                // ── Smart Permission Check ──────────────────────────
                                val profileHasWebsites = profile.blockedSites.isNotEmpty()
                                val profileHasApps     = profile.blockedPackages.isNotEmpty()

                                // Website block থাকলে → accessibility check
                                if (profileHasWebsites && !hasAccessibilityPerm) {
                                    Toast.makeText(
                                        context,
                                        "Website block করতে Accessibility Permission দিন",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    BlockingManager.openAccessibilitySettings(context)
                                    return@ProfileCardItem
                                }
                                // App block থাকলে → usageStats + overlay check
                                if (profileHasApps && !hasUsagePerm) {
                                    Toast.makeText(
                                        context,
                                        "App block করতে Usage Access Permission দিন",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    BlockingManager.openUsageAccessSettings(context)
                                    return@ProfileCardItem
                                }
                                if (profileHasApps && !hasOverlayPerm) {
                                    Toast.makeText(
                                        context,
                                        "App block করতে Overlay Permission দিন",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    BlockingManager.openOverlaySettings(context)
                                    return@ProfileCardItem
                                }
                                // ────────────────────────────────────────────────────

                                pendingProfile = profile
                                when (profile.lockMode) {
                                    0 -> showSelfControlDialog = true
                                    1 -> showParentsSetDialog   = true // Set password on activation
                                    2 -> {
                                        // Long Text activates directly
                                        profiles = profiles.map { if (it.id == profile.id) it.copy(isActive = true) else it }
                                    }
                                    3 -> showDailyLimitDialog  = true
                                    4 -> showHourlyLimitDialog = true
                                }
                            } else {
                                profileToTurnOff = profile
                                when (profile.lockMode) {
                                    0 -> {
                                        if (isLockedBySelfControl) {
                                            Toast.makeText(context, "Self Control: Time has not expired yet!", Toast.LENGTH_SHORT).show()
                                            profileToTurnOff = null
                                        } else {
                                            profiles = profiles.map { if (it.id == profile.id) it.copy(isActive = false) else it }
                                            profileToTurnOff = null
                                        }
                                    }
                                    1 -> showParentsUnlockDialog = true // Unlock with password
                                    2 -> showLongTextUnlockDialog = true // Unlock with typing
                                    else -> {
                                        profiles = profiles.map { if (it.id == profile.id) it.copy(isActive = false) else it }
                                        profileToTurnOff = null
                                    }
                                }
                                if (profiles.none { it.isActive }) AppBlockerService.stop(context)
                            }
                        },
                        onDelete = { 
                            if (isLockedBySelfControl) Toast.makeText(context, "Cannot delete while Self Control is active!", Toast.LENGTH_SHORT).show()
                            else profiles = profiles.filter { it.id != profile.id } 
                        },
                        onEdit   = { 
                            if (isLockedBySelfControl) Toast.makeText(context, "Cannot edit while Self Control is active!", Toast.LENGTH_SHORT).show()
                            else editingProfile = profile 
                        }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

// ==========================================
// 4. PROFILE CARD 
// ==========================================
@Composable
fun ProfileCardItem(
    profile: BlockingFocusProfile,
    isLockedBySelfControl: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val modeInfo = lockModeOptions.getOrNull(profile.lockMode) ?: lockModeOptions[0]
    val modeDetail = when (profile.lockMode) {
        0 -> buildString {
            if (profile.selfDays > 0) append("${profile.selfDays}d ")
            if (profile.selfHours > 0) append("${profile.selfHours}h ")
            append("${profile.selfMinutes}m")
        }
        1 -> "Password protected"
        2 -> "Long text unlock"
        3 -> "${profile.dailyLimitMinutes} min/day"
        4 -> "${profile.hourlyLimitMinutes} min/hour"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Top row: icon + name + active badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (profile.profileType == "Block") Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                            RoundedCornerShape(7.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (profile.profileType == "Block") Icons.Default.Block else Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = if (profile.profileType == "Block") Color(0xFFEF4444) else com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.profileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText, lineHeight = 16.sp)
                    Text(
                        "${profile.profileType} · ${profile.webCount} Web · ${profile.appCount} App",
                        fontSize = 10.sp,
                        color = GrayText
                    )
                }
                // Active badge
                if (profile.isActive) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCFCE7), RoundedCornerShape(50.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Active", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Mode chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(modeInfo.tint.copy(alpha = 0.10f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(modeInfo.icon, contentDescription = null, tint = modeInfo.tint, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(modeInfo.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = modeInfo.tint)
                    }
                }
                Spacer(Modifier.width(5.dp))
                Text(modeDetail, fontSize = 10.sp, color = GrayText)
            }

            Spacer(Modifier.height(7.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6))
            Spacer(Modifier.height(6.dp))

            // Bottom row: toggle + edit/delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = profile.isActive,
                        onCheckedChange = { onToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = PrimaryTeal,
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB)
                        ),
                        modifier = Modifier.scale(0.65f)
                    )
                    Spacer(Modifier.width(0.dp))
                    Text(
                        if (profile.isActive) "চালু" else "বন্ধ",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (profile.isActive) PrimaryTeal else GrayText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, if(isLockedBySelfControl) Color(0xFFF3F4F6) else Color(0xFFD1E8FF), RoundedCornerShape(6.dp))
                            .background(if(isLockedBySelfControl) Color(0xFFF9FAFB) else Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if(isLockedBySelfControl) Color(0xFF9CA3AF) else Color(0xFF3B82F6), modifier = Modifier.size(13.dp)) }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, if(isLockedBySelfControl) Color(0xFFF3F4F6) else Color(0xFFFEE2E2), RoundedCornerShape(6.dp))
                            .background(if(isLockedBySelfControl) Color(0xFFF9FAFB) else Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = if(isLockedBySelfControl) Color(0xFF9CA3AF) else RedIcon, modifier = Modifier.size(13.dp)) }
                }
            }
        }
    }
}

// ==========================================
// 5. CREATE PROFILE SHEET 
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileSheet(onDismiss: () -> Unit, onCreate: (BlockingFocusProfile) -> Unit) {
    var step by remember { mutableStateOf(0) }
    
    var profileType by remember { mutableStateOf("") } 
    var selectedTabIndex by remember { mutableStateOf(0) }
    var blockedApps by remember { mutableStateOf(listOf<String>()) }
    var blockedSites by remember { mutableStateOf(listOf<String>()) }
    var siteInput by remember { mutableStateOf("") }

    var selectedMode by remember { mutableStateOf(-1) }
    var selfDays by remember { mutableStateOf(0) }
    var selfHours by remember { mutableStateOf(0) }
    var selfMinutes by remember { mutableStateOf(25) }
    var customLongText by remember { mutableStateOf(DEFAULT_LONG_TEXT) }
    var dailyLimit by remember { mutableStateOf(60) }
    var hourlyLimit by remember { mutableStateOf(10) }
    var profileName by remember { mutableStateOf("") }
    var blockPhoneSettings by remember { mutableStateOf(false) }
    var uninstallProtection by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = CardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (step > 0) step-- else onDismiss() }) {
                            Icon(if (step > 0) Icons.Default.ArrowBack else Icons.Default.Close, contentDescription = "Back/Close")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (step) { 0 -> "Select Profile Type"; 1 -> "Select Apps & Websites"; else -> "Protection Settings" },
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    when (step) {
                        0 -> {
                            Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SelectionCard("Block Apps and Websites", "Selected apps and websites will be restricted.", Icons.Default.Block, profileType == "Block") { profileType = "Block" }
                                SelectionCard("Allow Only Apps and Websites", "Everything is blocked EXCEPT the selected ones.", Icons.Default.CheckCircleOutline, profileType == "Allow") { profileType = "Allow" }
                            }
                        }
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TabRow(selectedTabIndex = selectedTabIndex, containerColor = BackgroundColor, contentColor = PrimaryTeal) {
                                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Apps (${blockedApps.size})", fontWeight = FontWeight.Bold) })
                                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Websites (${blockedSites.size})", fontWeight = FontWeight.Bold) })
                                }
                                Spacer(Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (selectedTabIndex == 0) {
                                        InstalledAppPicker(selectedPackages = blockedApps, onSelectionChanged = { blockedApps = it }, profileType = profileType)
                                    } else {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            WebsitePicker(
                                                selectedSites = blockedSites,
                                                onSelectionChanged = { blockedSites = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Profile Name") }, placeholder = { Text("e.g. Deep Study Mode") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                Text("Blocking Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                lockModeOptions.forEach { mode ->
                                    val isSelected = selectedMode == mode.index
                                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedMode = mode.index }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) mode.tint.copy(alpha = 0.09f) else Color.White), border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) mode.tint else Color(0xFFE5E7EB))) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(mode.icon, contentDescription = null, tint = mode.tint, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) { Text(mode.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText); Text(mode.subtitle, fontSize = 12.sp, color = GrayText) }
                                            RadioButton(selected = isSelected, onClick = { selectedMode = mode.index })
                                        }
                                    }
                                }
                                if (selectedMode == 0) {
                                    NumberPickerRow("Days", selfDays, 0, 30) { selfDays = it }
                                    NumberPickerRow("Hours", selfHours, 0, 23) { selfHours = it }
                                    NumberPickerRow("Minutes", selfMinutes, 1, 59) { selfMinutes = it }
                                } else if (selectedMode == 1) {
                                    Text("প্রোফাইল তৈরি হয়ে গেলে টগল চালু করার সময় আপনাকে একটি Password সেট করতে হবে।", color = GrayText, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                                } else if (selectedMode == 2) {
                                    OutlinedTextField(
                                        value = customLongText,
                                        onValueChange = { customLongText = it },
                                        label = { Text("Text to type for Unlock") },
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        supportingText = { Text("এই টেক্সটটি টাইপ করে প্রোফাইল অফ করতে হবে (১৫০ শব্দের কাছাকাছি হলে ভালো)", color = GrayText, fontSize = 11.sp) }
                                    )
                                } else if (selectedMode == 3) {
                                    NumberPickerRow("Minutes / day", dailyLimit, 5, 480) { dailyLimit = it }
                                } else if (selectedMode == 4) {
                                    NumberPickerRow("Minutes / hour", hourlyLimit, 1, 55) { hourlyLimit = it }
                                }
                                HorizontalDivider()
                                Text("Extra Protections", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) { Text("Block Phone Settings", fontWeight = FontWeight.Bold, color = DarkText); Text("Prevents bypassing restrictions", fontSize = 12.sp, color = GrayText) }
                                            Switch(checked = blockPhoneSettings, onCheckedChange = { blockPhoneSettings = it })
                                        }
                                        HorizontalDivider(color = Color(0xFFF3F4F6))
                                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) { Text("Uninstall Protection", fontWeight = FontWeight.Bold, color = DarkText); Text("Prevents app uninstallation", fontSize = 12.sp, color = GrayText) }
                                            Switch(checked = uninstallProtection, onCheckedChange = { uninstallProtection = it })
                                        }
                                    }
                                }
                                Spacer(Modifier.height(30.dp))
                            }
                        }
                    }
                }

                Surface(color = CardBg, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    val modeOk = selectedMode >= 0 && (selectedMode != 2 || customLongText.isNotBlank())
                    val btnEnabled = when (step) {
                        0 -> profileType.isNotEmpty()
                        1 -> blockedApps.isNotEmpty() || blockedSites.isNotEmpty()
                        2 -> profileName.isNotBlank() && modeOk
                        else -> false
                    }
                    Button(
                        onClick = {
                            if (step < 2) step++
                            else onCreate(
                                BlockingFocusProfile(
                                    id = 0, profileName = profileName, webCount = blockedSites.size, appCount = blockedApps.size,
                                    mode = lockModeOptions[selectedMode].title, isActive = false, profileType = profileType,
                                    blockPhoneSettings = blockPhoneSettings, uninstallProtection = uninstallProtection,
                                    lockMode = selectedMode, selfDays = selfDays, selfHours = selfHours, selfMinutes = selfMinutes,
                                    dailyLimitMinutes = dailyLimit, hourlyLimitMinutes = hourlyLimit, parentPin = "", customLongText = customLongText,
                                    blockedPackages = blockedApps, blockedSites = blockedSites
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(12.dp),
                        enabled = btnEnabled, colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) { Text(text = if (step == 2) "✓ Create Profile" else "Next →", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun SelectionCard(title: String, subtitle: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimaryTeal.copy(alpha = 0.1f) else Color.White),
        border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) PrimaryTeal else Color(0xFFE5E7EB))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isSelected) PrimaryTeal else GrayText, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = GrayText)
            }
            RadioButton(selected = isSelected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal))
        }
    }
}

// ==========================================
// 6. PERMISSION BANNER & COMPONENTS
// ==========================================
@Composable
fun PermissionBanner(icon: ImageVector, title: String, message: String, color: Color, onGrant: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)), border = BorderStroke(1.dp, color.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color); Text(message, fontSize = 12.sp, color = GrayText, lineHeight = 16.sp) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onGrant, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = color), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("দাও", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun NumberPickerRow(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DarkText, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onValueChange(value - 1) }, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp)) }
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryTeal, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { if (value < max) onValueChange(value + 1) }, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppPicker(
    selectedPackages: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    profileType: String = "Block"  // "Block" or "Allow"
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<BlockingInstalledApp>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var searchQuery   by remember { mutableStateOf("") }

    val defaultLauncherPkg = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { BlockingInstalledApp(pm.getApplicationLabel(it).toString(), it.packageName, pm.getApplicationIcon(it)) }
                .sortedBy { it.name }
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoading = false
            }
        }
    }

    val isAllowMode = profileType == "Allow"
    val filtered = installedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isAllowMode && defaultLauncherPkg.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryTeal.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Default Launcher সবসময় Allow থাকবে", fontSize = 13.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
        }
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("App search করো...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryTeal) }
        } else {
            LazyColumn {
                items(filtered) { app ->
                    val isLauncher = isAllowMode && app.packageName == defaultLauncherPkg
                    val isSelected = selectedPackages.contains(app.packageName) || isLauncher
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !isLauncher) {
                            if (!isLauncher) {
                                onSelectionChanged(
                                    if (isSelected) selectedPackages.filter { it != app.packageName }
                                    else selectedPackages + app.packageName
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isLauncher -> PrimaryTeal.copy(alpha = 0.05f)
                                isSelected -> PrimaryTeal.copy(alpha = 0.08f)
                                else -> Color.White
                            }
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            when {
                                isLauncher -> PrimaryTeal.copy(alpha = 0.5f)
                                isSelected -> PrimaryTeal
                                else -> Color(0xFFE5E7EB)
                            }
                        )
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                                    if (isLauncher) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(modifier = Modifier.background(PrimaryTeal.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) { 
                                            Text("Launcher", fontSize = 9.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold) 
                                        }
                                    }
                                }
                                Text(app.packageName, fontSize = 11.sp, color = GrayText, maxLines = 1)
                            }
                            if (isLauncher) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryTeal.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            } else {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        onSelectionChanged(
                                            if (isSelected) selectedPackages.filter { it != app.packageName }
                                            else selectedPackages + app.packageName
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryTeal)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6.5 WEBSITE PICKER
// ==========================================
private val popularWebsites = listOf(
    "Social Media" to listOf("facebook.com", "instagram.com", "twitter.com", "x.com", "tiktok.com", "snapchat.com", "pinterest.com", "reddit.com", "linkedin.com", "threads.net"),
    "Video" to listOf("youtube.com", "netflix.com", "primevideo.com", "hotstar.com", "chorki.com", "bioscope.net", "bongbd.com", "dailymotion.com", "twitch.tv", "vimeo.com"),
    "News" to listOf("prothomalo.com", "bdnews24.com", "thedailystar.net", "samakal.com", "jugantor.com", "ittefaq.com.bd", "bbc.com/bengali", "dw.com/bn", "anandabazar.com", "ntv.com.bd"),
    "Shopping" to listOf("daraz.com.bd", "shajgoj.com", "chaldal.com", "shohoz.com", "bikroy.com", "amazon.com", "alibaba.com", "aliexpress.com", "meesho.com", "ajkerdeal.com"),
    "Gaming" to listOf("roblox.com", "miniclip.com", "poki.com", "friv.com", "steam.com", "epicgames.com", "gameflare.com", "crazygames.com", "y8.com", "silvergames.com"),
    "Adult / বাজে" to listOf("xvideos.com", "pornhub.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com", "tube8.com", "brazzers.com", "onlyfans.com", "chaturbate.com"),
    "Messaging" to listOf("web.whatsapp.com", "web.telegram.org", "messenger.com", "discord.com", "slack.com", "zoom.us", "meet.google.com", "teams.microsoft.com"),
    "Other" to listOf("9gag.com", "buzzfeed.com", "quora.com", "wikipedia.org", "medium.com", "tumblr.com", "deviantart.com", "wattpad.com", "archive.org", "stumbleupon.com")
)

@Composable
fun FaviconImage(domain: String, modifier: Modifier = Modifier) {
    val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    androidx.compose.foundation.Image(
        painter = coil.compose.rememberAsyncImagePainter(faviconUrl),
        contentDescription = domain,
        modifier = modifier
    )
}

@Composable
fun WebsitePicker(selectedSites: List<String>, onSelectionChanged: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val allWebsites = remember { popularWebsites.flatMap { it.second }.distinct().sorted() }
    val listMatches = allWebsites.filter { it.contains(query.trim(), ignoreCase = true) }
    val trimmedQuery = query.trim().lowercase()
    val suggestedDomain: String? = remember(trimmedQuery) {
        if (trimmedQuery.length < 2) null
        else if (trimmedQuery.contains(".")) trimmedQuery 
        else "$trimmedQuery.com"
    }
    val showAutoSuggestion = trimmedQuery.length >= 2 && listMatches.none {
        it.equals(trimmedQuery, ignoreCase = true) || it.equals("$trimmedQuery.com", ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search or type website", color = GrayText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GrayText) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = GrayText, modifier = Modifier.size(18.dp)) } },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryTeal, unfocusedBorderColor = Color(0xFFE5E7EB), focusedLeadingIconColor = PrimaryTeal)
        )

        if (showAutoSuggestion && suggestedDomain != null && !selectedSites.contains(suggestedDomain)) {
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { if (!selectedSites.contains(suggestedDomain)) { onSelectionChanged(selectedSites + suggestedDomain) }; query = "" },
                shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)), border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { FaviconImage(domain = suggestedDomain, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))) }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) { Text(suggestedDomain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkText); Text("Tap to block", fontSize = 11.sp, color = GrayText) }
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = PrimaryTeal, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (selectedSites.isNotEmpty()) {
                item { Text("Selected (${selectedSites.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal); Spacer(Modifier.height(6.dp)) }
                items(selectedSites) { site ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = PrimaryTeal.copy(alpha = 0.08f)), border = BorderStroke(1.dp, PrimaryTeal)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).background(Color.White, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { FaviconImage(domain = site, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))) }
                            Spacer(Modifier.width(10.dp))
                            Text(site, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                            IconButton(onClick = { onSelectionChanged(selectedSites.filter { it != site }) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = RedIcon, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)) }
            }

            item { Text(if (query.isBlank()) "Popular Websites" else "Search Results", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GrayText); Spacer(Modifier.height(6.dp)) }

            items(listMatches) { site ->
                val isSelected = selectedSites.contains(site)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelectionChanged(if (isSelected) selectedSites.filter { it != site } else selectedSites + site) },
                    shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimaryTeal.copy(alpha = 0.08f) else Color.White), border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) PrimaryTeal else Color(0xFFE5E7EB))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(Color(0xFFF9FAFB), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { FaviconImage(domain = site, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))) }
                        Spacer(Modifier.width(10.dp))
                        Text(site, modifier = Modifier.weight(1f), fontSize = 14.sp, color = DarkText)
                        Checkbox(checked = isSelected, onCheckedChange = { onSelectionChanged(if (isSelected) selectedSites.filter { it != site } else selectedSites + site) }, colors = CheckboxDefaults.colors(checkedColor = PrimaryTeal))
                    }
                }
            }
            if (listMatches.isEmpty() && !showAutoSuggestion) { item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text("কোনো ফলাফল নেই", color = GrayText, fontSize = 13.sp) } } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ==========================================
// 7. ACTIVATION / UNLOCK DIALOGS
// ==========================================

@Composable
fun SelfControlActivateDialog(initialDays: Int, initialHours: Int, initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    var days    by remember { mutableStateOf(initialDays) }
    var hours   by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Self Control", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DarkText)
                Spacer(Modifier.height(20.dp))
                NumberPickerRow("Days", days, 0, 30) { days = it }
                Spacer(Modifier.height(12.dp))
                NumberPickerRow("Hours", hours, 0, 23) { hours = it }
                Spacer(Modifier.height(12.dp))
                NumberPickerRow("Minutes", minutes, 1, 59) { minutes = it }
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                    Button(onClick = { onConfirm(days, hours, minutes) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)) { Text("Start Block") }
                }
            }
        }
    }
}

@Composable
fun ParentsSetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Set Parent Password", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("এই প্রোফাইলটি বন্ধ করতে পরবর্তীতে এই পাসওয়ার্ডটি দিতে হবে।", fontSize = 12.sp, color = GrayText)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = enteredPin, onValueChange = { enteredPin = it }, 
                    label = { Text("Password (At least 4 chars)") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { if (enteredPin.length >= 4) onConfirm(enteredPin) }, enabled = enteredPin.length >= 4, modifier = Modifier.weight(1f)) { Text("Activate") }
                }
            }
        }
    }
}

@Composable
fun ParentsUnlockDialog(storedPin: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var error      by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Parents Control Unlock", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = enteredPin, onValueChange = { enteredPin = it; error = false }, 
                    label = { Text("Enter Password") }, 
                    isError = error, modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text("ভুল পাসওয়ার্ড!", color = RedIcon, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { if (enteredPin == storedPin) onConfirm() else error = true }, modifier = Modifier.weight(1f)) { Text("Unlock") }
                }
            }
        }
    }
}

@Composable
fun LongTextUnlockDialog(targetText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typedText  by remember { mutableStateOf("") }
    val matched = typedText.trim() == targetText.trim()
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Long Text Unlock", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("নিচের টেক্সটটি হুবহু টাইপ করে প্রোফাইল অফ করুন:", fontSize = 12.sp, color = GrayText)
                
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)), modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(targetText, color = Color.Blue, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
                
                OutlinedTextField(
                    value = typedText, onValueChange = { typedText = it }, 
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("এখানে টাইপ করুন...") }
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f), enabled = matched) { Text("Unlock") }
                }
            }
        }
    }
}

@Composable
fun DailyLimitActivateDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(initialMinutes) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Daily Usage Limit", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))
                NumberPickerRow("Minutes / day", minutes, 5, 480) { minutes = it }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onConfirm(minutes) }, modifier = Modifier.weight(1f)) { Text("Activate") }
                }
            }
        }
    }
}

@Composable
fun HourlyLimitActivateDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(initialMinutes) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Hourly Usage Limit", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))
                NumberPickerRow("Minutes / hour", minutes, 1, 55) { minutes = it }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onConfirm(minutes) }, modifier = Modifier.weight(1f)) { Text("Activate") }
                }
            }
        }
    }
}

// ==========================================
// 8. EDIT PROFILE SHEET
// ==========================================
@Composable
fun EditProfileSheet(profile: BlockingFocusProfile, onDismiss: () -> Unit, onSave: (BlockingFocusProfile) -> Unit) {
    var profileName        by remember { mutableStateOf(profile.profileName) }
    var blockedSites       by remember { mutableStateOf(profile.blockedSites) }
    var blockedApps        by remember { mutableStateOf(profile.blockedPackages) }
    var profileType        by remember { mutableStateOf(profile.profileType) }
    var selectedMode       by remember { mutableStateOf(profile.lockMode) }
    var selfDays           by remember { mutableStateOf(profile.selfDays) }
    var selfHours          by remember { mutableStateOf(profile.selfHours) }
    var selfMinutes        by remember { mutableStateOf(profile.selfMinutes) }
    var customLongText     by remember { mutableStateOf(profile.customLongText) }
    var dailyLimit         by remember { mutableStateOf(profile.dailyLimitMinutes) }
    var hourlyLimit        by remember { mutableStateOf(profile.hourlyLimitMinutes) }
    var blockPhoneSettings by remember { mutableStateOf(profile.blockPhoneSettings) }
    var uninstallProtection by remember { mutableStateOf(profile.uninstallProtection) }
    
    var selectedTabIndex   by remember { mutableStateOf(0) }
    var currentSection     by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = CardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText, modifier = Modifier.weight(1f))
                    }
                }
                Surface(color = CardBg, modifier = Modifier.fillMaxWidth()) {
                    ScrollableTabRow(selectedTabIndex = currentSection, containerColor = CardBg, contentColor = PrimaryTeal, edgePadding = 16.dp) {
                        listOf("General", "Apps & Sites", "Lock Mode", "Protections").forEachIndexed { idx, title ->
                            Tab(selected = currentSection == idx, onClick = { currentSection = idx }, text = { Text(title, fontWeight = if (currentSection == idx) FontWeight.Bold else FontWeight.Normal) })
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    when (currentSection) {
                        0 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Profile Name") }, placeholder = { Text("e.g. Deep Study Mode") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                            Text("Profile Type", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            SelectionCard("Block Apps and Websites", "Selected apps and websites will be restricted.", Icons.Default.Block, profileType == "Block") { profileType = "Block" }
                            SelectionCard("Allow Only Apps and Websites", "Everything is blocked EXCEPT the selected ones.", Icons.Default.CheckCircleOutline, profileType == "Allow") { profileType = "Allow" }
                        }
                        1 -> Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(Modifier.height(12.dp))
                            TabRow(selectedTabIndex = selectedTabIndex, containerColor = BackgroundColor, contentColor = PrimaryTeal) {
                                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Apps (${blockedApps.size})", fontWeight = FontWeight.Bold) })
                                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Websites (${blockedSites.size})", fontWeight = FontWeight.Bold) })
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (selectedTabIndex == 0) InstalledAppPicker(selectedPackages = blockedApps, onSelectionChanged = { blockedApps = it }, profileType = profileType)
                                else WebsitePicker(selectedSites = blockedSites, onSelectionChanged = { blockedSites = it })
                            }
                        }
                        2 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Blocking Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            lockModeOptions.forEach { mode ->
                                val isSelected = selectedMode == mode.index
                                Card(modifier = Modifier.fillMaxWidth().clickable { selectedMode = mode.index }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) mode.tint.copy(alpha = 0.09f) else Color.White), border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) mode.tint else Color(0xFFE5E7EB))) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(mode.icon, contentDescription = null, tint = mode.tint, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) { Text(mode.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText); Text(mode.subtitle, fontSize = 12.sp, color = GrayText) }
                                        RadioButton(selected = isSelected, onClick = { selectedMode = mode.index })
                                    }
                                }
                            }
                            if (selectedMode == 0) {
                                Spacer(Modifier.height(4.dp))
                                NumberPickerRow("Days", selfDays, 0, 30) { selfDays = it }
                                Spacer(Modifier.height(8.dp))
                                NumberPickerRow("Hours", selfHours, 0, 23) { selfHours = it }
                                Spacer(Modifier.height(8.dp))
                                NumberPickerRow("Minutes", selfMinutes, 1, 59) { selfMinutes = it }
                            } else if (selectedMode == 1) {
                                Text("প্রোফাইলটি অন করার সময় আপনাকে পাসওয়ার্ড সেট করতে হবে।", color = GrayText, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            } else if (selectedMode == 2) {
                                OutlinedTextField(
                                    value = customLongText, onValueChange = { customLongText = it },
                                    label = { Text("Text to type for Unlock") }, modifier = Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(12.dp)
                                )
                            } else if (selectedMode == 3) {
                                NumberPickerRow("Minutes / day", dailyLimit, 5, 480) { dailyLimit = it }
                            } else if (selectedMode == 4) {
                                NumberPickerRow("Minutes / hour", hourlyLimit, 1, 55) { hourlyLimit = it }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                        3 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Extra Protections", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) { Text("Block Phone Settings", fontWeight = FontWeight.Bold, color = DarkText); Text("Prevents bypassing restrictions", fontSize = 12.sp, color = GrayText) }
                                        Switch(checked = blockPhoneSettings, onCheckedChange = { blockPhoneSettings = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryTeal))
                                    }
                                    HorizontalDivider(color = Color(0xFFF3F4F6))
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) { Text("Uninstall Protection", fontWeight = FontWeight.Bold, color = DarkText); Text("Prevents app uninstallation", fontSize = 12.sp, color = GrayText) }
                                        Switch(checked = uninstallProtection, onCheckedChange = { uninstallProtection = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryTeal))
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(color = CardBg, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    val modeOk = selectedMode >= 0 && (selectedMode != 2 || customLongText.isNotBlank())
                    val canSave = profileName.isNotBlank() && modeOk
                    Button(
                        onClick = {
                            onSave(
                                profile.copy(
                                    profileName = profileName, profileType = profileType, blockedSites = blockedSites, blockedPackages = blockedApps,
                                    webCount = blockedSites.size, appCount = blockedApps.size, lockMode = selectedMode, mode = lockModeOptions.getOrNull(selectedMode)?.title ?: "",
                                    selfDays = selfDays, selfHours = selfHours, selfMinutes = selfMinutes, customLongText = customLongText,
                                    dailyLimitMinutes = dailyLimit, hourlyLimitMinutes = hourlyLimit, blockPhoneSettings = blockPhoneSettings, uninstallProtection = uninstallProtection
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(12.dp),
                        enabled = canSave, colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) { Text("✓ Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ==========================================
// 9. BLOCKING MANAGER
// ==========================================
object BlockingManager {
    private const val PREFS_NAME   = "blocking_profiles"
    private const val KEY_PROFILES = "profiles_json_v2"
    private val gson = Gson()

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }
    
    fun hasOverlayPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    
    fun openUsageAccessSettings(context: Context) = context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
    
    fun saveProfiles(context: Context, profiles: List<BlockingFocusProfile>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
    }
    
    fun loadProfiles(context: Context): List<BlockingFocusProfile> {
        val json  = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PROFILES, null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<BlockingFocusProfile>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
    
    fun getActiveBlockedSites(context: Context): Set<String> {
        val profileSites = loadProfiles(context)
            .filter { it.isActive }
            .flatMap { it.blockedSites }
            .map { WebsiteBlockingAccessibilityService.cleanDomain(it) }
            .filter { it.isNotBlank() }
            .toSet()
        // singel_website.kt এর individual blocked sites ও include করো
        val individualSites = IndividualSiteManager.getActiveBlockedDomains(context)
        return profileSites + individualSites
    }
    
    fun isSelfControlExpired(context: Context, profileId: Int, profile: BlockingFocusProfile): Boolean {
        if (profile.lockMode != 0) return true // Only applies to self control
        val activatedAt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong("activated_at_$profileId", -1L)
        if (activatedAt == -1L) return true
        val durationMs = (profile.selfDays * 24L * 60 * 60 * 1000 + profile.selfHours * 60L * 60 * 1000 + profile.selfMinutes * 60L * 1000)
        return System.currentTimeMillis() - activatedAt >= durationMs
    }
    
    // ── Permission helpers ──────────────────────────────────────────────
    fun isWebsiteBlockingServiceEnabled(context: Context): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // UnifiedBlockerService handles website block — check that
        val unifiedTarget = "${context.packageName}/com.rasel.RasFocus.selfcontrol.UnifiedBlockerService"
        // Also accept the old WebsiteBlockingAccessibilityService if somehow still enabled
        val websiteTarget = "${context.packageName}/${WebsiteBlockingAccessibilityService::class.java.name}"
        return enabledServices.split(":").any {
            it.equals(unifiedTarget, ignoreCase = true) || it.equals(websiteTarget, ignoreCase = true)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    fun shouldBlockPackage(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val defaultLauncher = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""
        if (packageName == defaultLauncher) return false

        val profiles = loadProfiles(context).filter { it.isActive }
        for (profile in profiles) {
            val matchesList = profile.blockedPackages.contains(packageName)
            val isTargeted = if (profile.profileType == "Allow") !matchesList else matchesList

            if (!isTargeted) continue

            when (profile.lockMode) {
                0 -> {
                    if (isSelfControlExpired(context, profile.id, profile)) {
                        saveProfiles(context, loadProfiles(context).map { if (it.id == profile.id) it.copy(isActive = false) else it })
                        continue
                    }
                    return true
                }
                1, 2, 3, 4 -> return true
            }
        }

        // Individual App Lock চেক (singel_apps.kt থেকে)
        if (IndividualLockManager.shouldBlockSingleApp(context, packageName)) return true

        return false
    }
}

// ==========================================
// 10. BLOCK OVERLAY VIEW 
// ==========================================
class BlockOverlayView(context: Context, private val blockedPkgName: String) : FrameLayout(context) {
    init {
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(AndroidColor.parseColor("#FF0F172A"), AndroidColor.parseColor("#FF1E293B"))
        )

        val pm = context.packageManager
        val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(blockedPkgName, 0)).toString() } catch (e: Exception) { blockedPkgName }
        val appIcon = try { pm.getApplicationIcon(blockedPkgName) } catch (e: Exception) { null }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val outerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val iconContainer = FrameLayout(context).apply {
            val size = dp(100)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(AndroidColor.parseColor("#2210B981")) 
            }
        }
        val iconView = ImageView(context).apply {
            setImageDrawable(appIcon)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        iconContainer.addView(iconView)

        val appPill = TextView(context).apply {
            text = appName
            textSize = 14f
            setTextColor(AndroidColor.parseColor("#FF10B981"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#1510B981"))
                setStroke(dp(1), AndroidColor.parseColor("#4410B981"))
                cornerRadius = 100f * density
            }
            setPadding(dp(18), dp(7), dp(18), dp(7))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(20)
            }
        }

        val title = TextView(context).apply {
            text = "App Blocked"
            textSize = 30f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
            }
        }

        val subtitleCard = FrameLayout(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#14FFFFFF"))
                setStroke(dp(1), AndroidColor.parseColor("#1FFFFFFF"))
                cornerRadius = 16f * density
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
        }
        val subtitleText = TextView(context).apply {
            text = "RasFocus Session Active\nকাজে মনোযোগ দিন, এই অ্যাপটি এখন বন্ধ।"
            textSize = 15f
            setTextColor(AndroidColor.parseColor("#D1D5DB"))
            gravity = Gravity.CENTER
            lineHeight = dp(24)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        subtitleCard.addView(subtitleText)

        val homeBtn = TextView(context).apply {
            text = "হোমে ফিরে যান"
            textSize = 16f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#FF10B981")) 
                cornerRadius = 14f * density
            }
            val h = dp(56)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h).apply {
                topMargin = dp(32)
            }
            setOnClickListener {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }

        val footer = TextView(context).apply {
            text = "🎯 Focus makes you better"
            textSize = 13f
            setTextColor(AndroidColor.parseColor("#64748B"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            }
        }

        outerLayout.addView(iconContainer)
        outerLayout.addView(appPill)
        outerLayout.addView(title)
        outerLayout.addView(subtitleCard)
        outerLayout.addView(homeBtn)
        outerLayout.addView(footer)
        addView(outerLayout)
    }
}

// ==========================================
// 11. APP BLOCKER SERVICE
// ==========================================
class AppBlockerService : Service() {
    companion object {
        private const val CHANNEL_ID   = "app_blocker_channel"
        private const val NOTIF_ID     = 8901
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(Intent(context, AppBlockerService::class.java))
            else context.startService(Intent(context, AppBlockerService::class.java))
        }
        fun stop(context: Context) = context.stopService(Intent(context, AppBlockerService::class.java))
    }
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lastCheckedPkg = ""
    private var isOverlayShowing = false
    private val checkRunnable = object : Runnable { override fun run() { checkForegroundApp(); handler.postDelayed(this, 500L) } }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App Blocker", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("RasFocus সক্রিয়").setContentText("Focus Profile চলছে").setSmallIcon(android.R.drawable.ic_lock_lock).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build())
        handler.post(checkRunnable)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(checkRunnable); removeOverlay() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForegroundApp() {
        val foregroundPkg = try { val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager; val now = System.currentTimeMillis(); usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000, now)?.maxByOrNull { it.lastTimeUsed }?.packageName } catch (e: Exception) { null } ?: return
        if (foregroundPkg == packageName) { if (isOverlayShowing) removeOverlay(); lastCheckedPkg = foregroundPkg; return }
        if (BlockingManager.shouldBlockPackage(this, foregroundPkg)) {
            if (foregroundPkg != lastCheckedPkg || !isOverlayShowing) {
                removeOverlay()
                if (BlockingManager.hasOverlayPermission(this) && !isOverlayShowing) {
                    overlayView = BlockOverlayView(this, foregroundPkg)
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                    val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; windowAnimations = android.R.style.Animation_Toast }
                    try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) { }
                }
                startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        } else { if (isOverlayShowing) removeOverlay() }
        lastCheckedPkg = foregroundPkg
    }
    private fun removeOverlay() { try { overlayView?.let { windowManager?.removeView(it) } } catch (e: Exception) {}; overlayView = null; isOverlayShowing = false }
}

// ==========================================
// 12. WEBSITE BLOCKING ACCESSIBILITY SERVICE
// ==========================================
class WebsiteBlockingAccessibilityService : AccessibilityService() {

    companion object {
        val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx",
            "com.opera.browser", "com.opera.mini.native", "com.brave.browser",
            "com.UCMobile.intl", "com.uc.browser.en", "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser", "com.rasel.RasFocus.selfcontrol"
        )

        fun cleanDomain(raw: String): String =
            raw.lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')
    }

    private var lastCheckedUrl = ""
    private var isBlocking = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                              AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                              AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50
            info.packageNames = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!BROWSER_PACKAGES.contains(pkg)) return

        val root = rootInActiveWindow ?: return
        val url = extractUrlFromWindow(root)
        if (url.isNullOrBlank()) return

        val cleanUrl = cleanDomain(url)

        if (cleanUrl == lastCheckedUrl && !isBlocking) return
        lastCheckedUrl = cleanUrl

        val blockedSites = BlockingManager.getActiveBlockedSites(this)
        if (blockedSites.isEmpty()) {
            isBlocking = false
            return
        }

        val shouldBlock = blockedSites.any { site ->
            val cleanSite = cleanDomain(site)
            cleanUrl == cleanSite ||
            cleanUrl.startsWith("$cleanSite/") ||
            cleanUrl.endsWith(".$cleanSite") ||
            cleanUrl.contains(".$cleanSite/")
        }

        if (shouldBlock) {
            isBlocking = true
            blockSite(url)
        } else {
            isBlocking = false
        }
    }

    private fun blockSite(originalUrl: String) {
        val domain = cleanDomain(originalUrl)
        BlockPage.show(this, BlockPage.Type.WEBSITE, "Website Blocked", "$domain is on your block list.")
        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val currentUrl = extractUrlFromWindow(root) ?: return@postDelayed
            val currentClean = cleanDomain(currentUrl)
            val blockedSites = BlockingManager.getActiveBlockedSites(this)
            val stillBlocked = blockedSites.any { site ->
                val cleanSite = cleanDomain(site)
                currentClean == cleanSite || currentClean.startsWith("$cleanSite/")
            }
            if (stillBlocked) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    isBlocking = false
                    lastCheckedUrl = ""
                }, 300)
            } else {
                isBlocking = false
                lastCheckedUrl = ""
            }
        }, 400)
    }

    private fun extractUrlFromWindow(root: AccessibilityNodeInfo): String? {
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.kiwibrowser.browser:id/url_bar",
            "com.UCMobile.intl:id/url_bar",
            "com.uc.browser.en:id/url_bar"
        )
        for (id in urlBarIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        return findUrlInNode(root)
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 8) return null  
        val text = node.text?.toString() ?: ""
        if (text.isNotBlank() && text.length > 4 &&
            (text.startsWith("http") || (text.contains('.') && !text.contains(' ')))
        ) return text
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findUrlInNode(it, depth + 1) }
            if (found != null) return found
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }
}
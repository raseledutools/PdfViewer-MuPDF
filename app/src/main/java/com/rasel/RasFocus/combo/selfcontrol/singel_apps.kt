package com.rasel.pdfviewer.combo.selfcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
// নতুন ইম্পোর্ট যোগ করা হয়েছে যাতে কম্পাইল এরর না হয়
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle

// ==========================================
// 1. COLORS & THEME (Same as BlockingPlan)
// ==========================================
private val PrimaryTeal = Color(0xFF0096B4)
private val DarkText      = Color(0xFF1A1A1A)
private val GrayText      = Color(0xFF6B7280)
private val CardBg        = Color(0xFFFFFFFF)
private val BackgroundColor = Color(0xFFF3F4F6)

private const val DEFAULT_LONG_TEXT = "Focus is the key to success. I choose to block distractions and work with full dedication..."

// ==========================================
// 2. DATA CLASSES 
// ==========================================
data class IndividualAppConfig(
    val packageName: String,
    val appName: String,
    val isActive: Boolean,
    val lockMode: Int = 0,
    val selfDays: Int = 0,
    val selfHours: Int = 0,
    val selfMinutes: Int = 25,
    val dailyLimitMinutes: Int = 60,
    val hourlyLimitMinutes: Int = 10,
    val parentPin: String = "",
    val customLongText: String = DEFAULT_LONG_TEXT
)

data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean
)

// ==========================================
// 3. MAIN INDIVIDUAL APP LOCK SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualAppLockScreen(navController: NavController) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Load locked configs
    var lockedConfigs by remember { mutableStateOf(IndividualLockManager.loadConfigs(context)) }
    
    // Selected app for configuring lock mode
    var selectedAppForConfig by remember { mutableStateOf<InstalledAppItem?>(null) }

    // Permission states
    var hasUsagePerm   by remember { mutableStateOf(BlockingManager.hasUsageStatsPermission(context)) }
    var hasOverlayPerm by remember { mutableStateOf(BlockingManager.hasOverlayPermission(context)) }

    // Settings থেকে ফিরলে recheck
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasUsagePerm   = BlockingManager.hasUsageStatsPermission(context)
                hasOverlayPerm = BlockingManager.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Parents Control PIN dialog
    var pinDialogApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pinActionIsUnlock by remember { mutableStateOf(true) } // true = unlock, false = lock

    // Self Control — time শেষ হয়নি warning dialog
    var selfControlBlockedApp by remember { mutableStateOf<InstalledAppItem?>(null) }

    // Long Text unlock dialog
    var longTextDialogApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var longTextInput by remember { mutableStateOf("") }
    var longTextError by remember { mutableStateOf(false) }
    var longTextActionIsUnlock by remember { mutableStateOf(true) }

    // Daily/Hourly Limit PIN dialog
    var limitPinDialogApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var limitPinInput by remember { mutableStateOf("") }
    var limitPinError by remember { mutableStateOf(false) }
    var limitPinActionIsUnlock by remember { mutableStateOf(true) }

    // Fetch Apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val launcherPkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != launcherPkg } // Hide default launcher
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // System apps hide
                .map { 
                    InstalledAppItem(
                        name = pm.getApplicationLabel(it).toString(),
                        packageName = it.packageName,
                        icon = pm.getApplicationIcon(it),
                        isSystemApp = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
            
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoading = false
            }
        }
    }

    // Sort apps: Blocked apps at the top, then alphabetically
    val filteredAndSortedApps = installedApps
        .filter { it.name.contains(searchQuery, ignoreCase = true) }
        .sortedWith(
            compareByDescending<InstalledAppItem> { lockedConfigs.containsKey(it.packageName) && lockedConfigs[it.packageName]!!.isActive }
                .thenBy { it.name }
        )

    if (selectedAppForConfig != null) {
        val app = selectedAppForConfig!!
        val currentConfig = lockedConfigs[app.packageName] ?: IndividualAppConfig(app.packageName, app.name, false)
        
        AppLockModeSheet(
            appInfo = app,
            initialConfig = currentConfig,
            onDismiss = { selectedAppForConfig = null },
            onSave = { newConfig ->
                // App enable করার সময় permission check
                if (newConfig.isActive) {
                    if (!hasUsagePerm) {
                        Toast.makeText(context, "App block করতে Usage Access Permission দিন", Toast.LENGTH_LONG).show()
                        BlockingManager.openUsageAccessSettings(context)
                        selectedAppForConfig = null
                        return@AppLockModeSheet
                    }
                    if (!hasOverlayPerm) {
                        Toast.makeText(context, "App block করতে Overlay Permission দিন", Toast.LENGTH_LONG).show()
                        BlockingManager.openOverlaySettings(context)
                        selectedAppForConfig = null
                        return@AppLockModeSheet
                    }
                }
                val newMap = lockedConfigs.toMutableMap()
                newMap[app.packageName] = newConfig
                lockedConfigs = newMap
                IndividualLockManager.saveConfigs(context, newMap)

                // Self Control mode হলে activation time save করো
                if (newConfig.isActive && newConfig.lockMode == 0) {
                    IndividualLockManager.saveActivationTime(context, app.packageName)
                }

                // Start overlay service if any app is locked
                if (newMap.values.any { it.isActive }) {
                    AppBlockerService.start(context)
                } else {
                    AppBlockerService.stop(context)
                }

                Toast.makeText(context, "${app.name} Lock Updated!", Toast.LENGTH_SHORT).show()
                selectedAppForConfig = null
            }
        )
    }

    // ===== Parents Control PIN Dialog =====
    if (pinDialogApp != null) {
        val app = pinDialogApp!!
        val config = lockedConfigs[app.packageName]
        AlertDialog(
            onDismissRequest = { pinDialogApp = null; pinInput = ""; pinError = false },
            title = { Text(if (pinActionIsUnlock) "Unlock করতে PIN দাও" else "Lock চালু করতে PIN দাও", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.name, fontSize = 13.sp, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = false },
                        label = { Text("PIN / Password") },
                        isError = pinError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (pinError) Text("ভুল PIN! আবার চেষ্টা করো।", color = Color.Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (config != null && pinInput == config.parentPin) {
                            val newMap = lockedConfigs.toMutableMap()
                            newMap[app.packageName] = config.copy(isActive = !pinActionIsUnlock)
                            lockedConfigs = newMap
                            IndividualLockManager.saveConfigs(context, newMap)
                            if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                            pinDialogApp = null; pinInput = ""; pinError = false
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pinDialogApp = null; pinInput = ""; pinError = false }) { Text("Cancel") }
            }
        )
    }

    // ===== Self Control — time শেষ হয়নি Warning Dialog =====
    if (selfControlBlockedApp != null) {
        val app = selfControlBlockedApp!!
        val config = lockedConfigs[app.packageName]
        val remainingText = if (config != null) {
            val prefs = context.getSharedPreferences("individual_app_locks", Context.MODE_PRIVATE)
            val activatedAt = prefs.getLong("individual_activated_at_${app.packageName}", -1L)
            val durationMs = (config.selfDays * 24L * 60 * 60 * 1000
                            + config.selfHours * 60L * 60 * 1000
                            + config.selfMinutes * 60L * 1000)
            val remainMs = durationMs - (System.currentTimeMillis() - activatedAt)
            if (remainMs > 0) {
                val h = remainMs / 3600000
                val m = (remainMs % 3600000) / 60000
                val s = (remainMs % 60000) / 1000
                "${h}h ${m}m ${s}s বাকি আছে"
            } else "Time শেষ হয়ে গেছে"
        } else ""

        AlertDialog(
            onDismissRequest = { selfControlBlockedApp = null },
            title = { Text("Self Control Active! 🔒", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.name, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
                    Text("এখনো lock period শেষ হয়নি।\n$remainingText\n\nTime শেষ হলে automatically unlock হবে।")
                }
            },
            confirmButton = {
                Button(
                    onClick = { selfControlBlockedApp = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) { Text("বুঝলাম") }
            }
        )
    }

    // ===== Long Text Unlock/Lock Dialog =====
    if (longTextDialogApp != null) {
        val app = longTextDialogApp!!
        val config = lockedConfigs[app.packageName]
        val targetText = config?.customLongText ?: DEFAULT_LONG_TEXT
        AlertDialog(
            onDismissRequest = { longTextDialogApp = null; longTextInput = ""; longTextError = false },
            title = {
                Text(
                    if (longTextActionIsUnlock) "Unlock করতে নিচের text টাইপ করো" else "Lock চালু করতে text টাইপ করো",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(app.name, color = Color(0xFF3B82F6), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    // Target text দেখাও
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = targetText,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                    OutlinedTextField(
                        value = longTextInput,
                        onValueChange = { longTextInput = it; longTextError = false },
                        label = { Text("এখানে type করো...") },
                        isError = longTextError,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (longTextError) Text("Text মিলছে না! হুবহু type করো।", color = Color.Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (longTextInput.trim() == targetText.trim()) {
                            val newMap = lockedConfigs.toMutableMap()
                            if (config != null) {
                                newMap[app.packageName] = config.copy(isActive = !longTextActionIsUnlock)
                                lockedConfigs = newMap
                                IndividualLockManager.saveConfigs(context, newMap)
                                if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                            }
                            longTextDialogApp = null; longTextInput = ""; longTextError = false
                        } else {
                            longTextError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { longTextDialogApp = null; longTextInput = ""; longTextError = false }) { Text("Cancel") }
            }
        )
    }

    // ===== Daily / Hourly Limit PIN Dialog =====
    if (limitPinDialogApp != null) {
        val app = limitPinDialogApp!!
        val config = lockedConfigs[app.packageName]
        val modeName = if (config?.lockMode == 3) "Daily Limit" else "Hourly Limit"
        AlertDialog(
            onDismissRequest = { limitPinDialogApp = null; limitPinInput = ""; limitPinError = false },
            title = {
                Text(
                    if (limitPinActionIsUnlock) "$modeName বন্ধ করতে PIN দাও" else "$modeName চালু করতে PIN দাও",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.name, fontSize = 13.sp,
                        color = if (config?.lockMode == 3) Color(0xFF8B5CF6) else Color(0xFFF97316),
                        fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = limitPinInput,
                        onValueChange = { limitPinInput = it; limitPinError = false },
                        label = { Text("PIN / Password") },
                        isError = limitPinError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (limitPinError) Text("ভুল PIN! আবার চেষ্টা করো।", color = Color.Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (config != null && limitPinInput == config.parentPin) {
                            val newMap = lockedConfigs.toMutableMap()
                            newMap[app.packageName] = config.copy(isActive = !limitPinActionIsUnlock)
                            lockedConfigs = newMap
                            IndividualLockManager.saveConfigs(context, newMap)
                            if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                            limitPinDialogApp = null; limitPinInput = ""; limitPinError = false
                        } else {
                            limitPinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (config?.lockMode == 3) Color(0xFF8B5CF6) else Color(0xFFF97316)
                    )
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { limitPinDialogApp = null; limitPinInput = ""; limitPinError = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Locker", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkText)
                        Text("প্রতিটি অ্যাপ আলাদাভাবে লক করো", fontSize = 12.sp, color = GrayText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back") 
                    }
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

            // Permission banners — শুধু তখনই দেখাবে যখন কোনো app active আছে
            val anyAppActive = lockedConfigs.values.any { it.isActive }
            if (anyAppActive && !hasUsagePerm) {
                SaPermissionBanner(
                    icon = Icons.Default.QueryStats,
                    title = "Usage Access দাও",
                    message = "কোন app foreground-এ আছে সেটা জানতে দরকার।",
                    color = Color(0xFFF97316)
                ) { BlockingManager.openUsageAccessSettings(context) }
                Spacer(Modifier.height(8.dp))
            }
            if (anyAppActive && !hasOverlayPerm) {
                SaPermissionBanner(
                    icon = Icons.Default.Layers,
                    title = "Overlay Permission দাও",
                    message = "Block screen দেখাতে এই permission দরকার।",
                    color = Color(0xFF3B82F6)
                ) { BlockingManager.openOverlaySettings(context) }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = searchQuery, 
                onValueChange = { searchQuery = it },
                placeholder = { Text("App search করো...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp), 
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryTeal,
                    unfocusedBorderColor = Color(0xFFE5E7EB)
                )
            )
            
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = PrimaryTeal) 
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredAndSortedApps, key = { it.packageName }) { app ->
                        val config = lockedConfigs[app.packageName]
                        val isLocked = config?.isActive == true
                        
                        AppLockRowItem(
                            app = app,
                            isLocked = isLocked,
                            lockModeTitle = if (isLocked) getLockModeTitle(config!!.lockMode) else "Unlocked",
                            onClickSettings = { selectedAppForConfig = app },
                            onToggleLock = {
                                if (config != null && config.isActive) {
                                    // === চালু আছে, বন্ধ করতে চাইছে ===
                                    when (config.lockMode) {
                                        1 -> {
                                            // Parents Control — বন্ধ করতে PIN লাগবে
                                            pinActionIsUnlock = true
                                            pinDialogApp = app
                                            pinInput = ""
                                            pinError = false
                                        }
                                        0 -> {
                                            // Self Control — time শেষ না হলে off করা যাবে না
                                            val prefs = context.getSharedPreferences("individual_app_locks", Context.MODE_PRIVATE)
                                            val activatedAt = prefs.getLong("individual_activated_at_${app.packageName}", -1L)
                                            val durationMs = (config.selfDays * 24L * 60 * 60 * 1000
                                                            + config.selfHours * 60L * 60 * 1000
                                                            + config.selfMinutes * 60L * 1000)
                                            val elapsed = System.currentTimeMillis() - activatedAt
                                            if (activatedAt == -1L || elapsed < durationMs) {
                                                // এখনো time শেষ হয়নি — off করা যাবে না
                                                selfControlBlockedApp = app
                                            } else {
                                                // Time শেষ — off করা যাবে
                                                val newMap = lockedConfigs.toMutableMap()
                                                newMap[app.packageName] = config.copy(isActive = false)
                                                lockedConfigs = newMap
                                                IndividualLockManager.saveConfigs(context, newMap)
                                                if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                                            }
                                        }
                                        2 -> {
                                            // Long Text — বন্ধ করতে text type করতে হবে
                                            longTextActionIsUnlock = true
                                            longTextDialogApp = app
                                            longTextInput = ""
                                            longTextError = false
                                        }
                                        3, 4 -> {
                                            // Daily/Hourly Limit — বন্ধ করতে PIN লাগবে
                                            limitPinActionIsUnlock = true
                                            limitPinDialogApp = app
                                            limitPinInput = ""
                                            limitPinError = false
                                        }
                                        else -> {
                                            // অন্য mode — সরাসরি off
                                            val newMap = lockedConfigs.toMutableMap()
                                            newMap[app.packageName] = config.copy(isActive = false)
                                            lockedConfigs = newMap
                                            IndividualLockManager.saveConfigs(context, newMap)
                                            if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                                        }
                                    }
                                } else if (config != null && !config.isActive) {
                                    // === বন্ধ আছে, চালু করতে চাইছে ===
                                    when (config.lockMode) {
                                        1 -> {
                                            // Parents Control — চালু করতেও PIN লাগবে
                                            pinActionIsUnlock = false
                                            pinDialogApp = app
                                            pinInput = ""
                                            pinError = false
                                        }
                                        2 -> {
                                            // Long Text — চালু করতেও text type করতে হবে
                                            longTextActionIsUnlock = false
                                            longTextDialogApp = app
                                            longTextInput = ""
                                            longTextError = false
                                        }
                                        3, 4 -> {
                                            // Daily/Hourly Limit — চালু করতেও PIN লাগবে
                                            limitPinActionIsUnlock = false
                                            limitPinDialogApp = app
                                            limitPinInput = ""
                                            limitPinError = false
                                        }
                                        else -> {
                                            val newMap = lockedConfigs.toMutableMap()
                                            newMap[app.packageName] = config.copy(isActive = true)
                                            lockedConfigs = newMap
                                            IndividualLockManager.saveConfigs(context, newMap)
                                            if (config.lockMode == 0) IndividualLockManager.saveActivationTime(context, app.packageName)
                                            if (newMap.values.any { it.isActive }) AppBlockerService.start(context) else AppBlockerService.stop(context)
                                        }
                                    }
                                } else {
                                    // Config নেই — settings sheet খোলো
                                    selectedAppForConfig = app
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

// ==========================================
// 4. APP LOCK ROW ITEM
// ==========================================
@Composable
fun AppLockRowItem(
    app: InstalledAppItem,
    isLocked: Boolean,
    lockModeTitle: String,
    onClickSettings: () -> Unit,
    onToggleLock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) PrimaryTeal.copy(alpha = 0.08f) else Color.White
        ),
        border = BorderStroke(
            1.dp, 
            if (isLocked) PrimaryTeal.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Text(
                    text = lockModeTitle, 
                    fontSize = 12.sp, 
                    color = if (isLocked) PrimaryTeal else GrayText,
                    fontWeight = if (isLocked) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            // Settings Icon (Right Side)
            IconButton(
                onClick = onClickSettings,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = GrayText, modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(8.dp))
            
            // Lock/Unlock Switch
            Switch(
                checked = isLocked,
                onCheckedChange = { onToggleLock() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, 
                    checkedTrackColor = PrimaryTeal,
                    uncheckedThumbColor = Color.White, 
                    uncheckedTrackColor = Color(0xFFD1D5DB)
                ),
                modifier = Modifier.scale(0.75f)
            )
        }
    }
}

// ==========================================
// 5. LOCK MODE SELECTION SHEET
// ==========================================
@Composable
fun AppLockModeSheet(
    appInfo: InstalledAppItem,
    initialConfig: IndividualAppConfig,
    onDismiss: () -> Unit,
    onSave: (IndividualAppConfig) -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialConfig.lockMode) }
    var selfDays by remember { mutableStateOf(initialConfig.selfDays) }
    var selfHours by remember { mutableStateOf(initialConfig.selfHours) }
    var selfMinutes by remember { mutableStateOf(initialConfig.selfMinutes) }
    var customLongText by remember { mutableStateOf(initialConfig.customLongText) }
    var dailyLimit by remember { mutableStateOf(initialConfig.dailyLimitMinutes) }
    var hourlyLimit by remember { mutableStateOf(initialConfig.hourlyLimitMinutes) }
    var parentPin by remember { mutableStateOf(initialConfig.parentPin) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = CardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Lock Mode", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text(appInfo.name, fontSize = 13.sp, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp), 
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    lockModeOptionsForIndividualApp.forEach { mode ->
                        val isSelected = selectedMode == mode.index
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedMode = mode.index }, 
                            shape = RoundedCornerShape(14.dp), 
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) mode.tint.copy(alpha = 0.09f) else Color.White), 
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) mode.tint else Color(0xFFE5E7EB))
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(mode.icon, contentDescription = null, tint = mode.tint, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) { 
                                    Text(mode.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text(mode.subtitle, fontSize = 12.sp, color = GrayText) 
                                }
                                RadioButton(selected = isSelected, onClick = { selectedMode = mode.index })
                            }
                        }
                    }

                    // Configuration fields based on selected mode
                    if (selectedMode == 0) {
                        NumberPickerRowIndividual("Days", selfDays, 0, 30) { selfDays = it }
                        NumberPickerRowIndividual("Hours", selfHours, 0, 23) { selfHours = it }
                        NumberPickerRowIndividual("Minutes", selfMinutes, 1, 59) { selfMinutes = it }
                    } else if (selectedMode == 1) {
                        OutlinedTextField(
                            value = parentPin,
                            onValueChange = { parentPin = it },
                            label = { Text("Set Password (Optional if already set)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedMode == 2) {
                        OutlinedTextField(
                            value = customLongText,
                            onValueChange = { customLongText = it },
                            label = { Text("Text to type for Unlock") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedMode == 3) {
                        NumberPickerRowIndividual("Minutes / day", dailyLimit, 5, 480) { dailyLimit = it }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = parentPin,
                            onValueChange = { parentPin = it },
                            label = { Text("Lock/Unlock করার PIN সেট করো") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (selectedMode == 4) {
                        NumberPickerRowIndividual("Minutes / hour", hourlyLimit, 1, 55) { hourlyLimit = it }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = parentPin,
                            onValueChange = { parentPin = it },
                            label = { Text("Lock/Unlock করার PIN সেট করো") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(30.dp))
                }

                Surface(color = CardBg, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onSave(
                                IndividualAppConfig(
                                    packageName = appInfo.packageName,
                                    appName = appInfo.name,
                                    isActive = true, // By default activating when saving
                                    lockMode = selectedMode,
                                    selfDays = selfDays,
                                    selfHours = selfHours,
                                    selfMinutes = selfMinutes,
                                    dailyLimitMinutes = dailyLimit,
                                    hourlyLimitMinutes = hourlyLimit,
                                    parentPin = parentPin,
                                    customLongText = customLongText
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), 
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) { 
                        Text("Save & Lock App", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}

// Helper components for UI
@Composable
fun NumberPickerRowIndividual(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DarkText, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onValueChange(value - 1) }, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp)) }
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryTeal, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { if (value < max) onValueChange(value + 1) }, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp)) }
        }
    }
}

// Data List for Lock Modes
data class SingleAppLockMode(val index: Int, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)
private val lockModeOptionsForIndividualApp = listOf(
    SingleAppLockMode(0, "Self Control", "Day / Hour / Minute timer দিয়ে ব্লক", Icons.Default.Timer, Color(0xFF0096B4)),
    SingleAppLockMode(1, "Parents Control", "আনলক করতে Password লাগবে", Icons.Default.Lock, Color(0xFFEAB308)), // LockPerson পরিবর্তে Lock ব্যবহার করা হয়েছে যদি ভার্সনে না থাকে
    SingleAppLockMode(2, "Long Text", "আনলক করতে paragraph টাইপ করতে হবে", Icons.Default.Subject, Color(0xFF3B82F6)),
    SingleAppLockMode(3, "Daily Limit", "সারাদিনে মোট কতক্ষণ ব্যবহার করা যাবে", Icons.Default.CalendarToday, Color(0xFF8B5CF6)),
    SingleAppLockMode(4, "Hourly Limit", "প্রতি ঘণ্টায় কতক্ষণ ব্যবহার করা যাবে", Icons.Default.HourglassEmpty, Color(0xFFF97316))
)

fun getLockModeTitle(index: Int): String {
    return lockModeOptionsForIndividualApp.find { it.index == index }?.title ?: "Unknown"
}

// ==========================================
// 6. INDIVIDUAL APP LOCK MANAGER
// ==========================================
object IndividualLockManager {
    private const val PREFS_NAME = "individual_app_locks"
    private const val KEY_CONFIGS = "app_configs_json"
    private val gson = Gson()

    fun saveConfigs(context: Context, configs: Map<String, IndividualAppConfig>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIGS, gson.toJson(configs))
            .apply()
    }

    fun loadConfigs(context: Context): Map<String, IndividualAppConfig> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CONFIGS, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, IndividualAppConfig>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    // Self Control timer শেষ হয়েছে কিনা চেক করো
    private fun isSelfControlExpired(context: Context, packageName: String, config: IndividualAppConfig): Boolean {
        if (config.lockMode != 0) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activatedAt = prefs.getLong("individual_activated_at_$packageName", -1L)
        if (activatedAt == -1L) return true
        val durationMs = (config.selfDays * 24L * 60 * 60 * 1000
                        + config.selfHours * 60L * 60 * 1000
                        + config.selfMinutes * 60L * 1000)
        return System.currentTimeMillis() - activatedAt >= durationMs
    }

    // Activation time save করো (Self Control mode এ)
    fun saveActivationTime(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong("individual_activated_at_$packageName", System.currentTimeMillis()).apply()
    }

    // Check if a specific app should be blocked based on individual lock
    fun shouldBlockSingleApp(context: Context, packageName: String): Boolean {
        val configs = loadConfigs(context)
        val appConfig = configs[packageName] ?: return false

        if (!appConfig.isActive) return false

        return when (appConfig.lockMode) {
            0 -> { // Self Control — timer শেষ হলে auto-unlock
                if (isSelfControlExpired(context, packageName, appConfig)) {
                    // Timer শেষ — config inactive করো
                    val newMap = configs.toMutableMap()
                    newMap[packageName] = appConfig.copy(isActive = false)
                    saveConfigs(context, newMap)
                    false
                } else {
                    true
                }
            }
            1, 2, 3, 4 -> true // Password, Long Text, Daily/Hourly — সবসময় block
            else -> false
        }
    }
}

// ==========================================
// 7. BLOCK METHOD ENUM
// ==========================================
enum class BlockMethod {
    TIME_RANGE,   // নির্দিষ্ট সময়ে block
    PASSWORD,     // password দিয়ে unlock
    LONG_TEXT     // long text টাইপ করে unlock
}

// ==========================================
// 8. BLOCK CONFIG DATA CLASS
// ==========================================
data class BlockConfig(
    val method: BlockMethod = BlockMethod.TIME_RANGE,
    val timeFrom: String = "00:00",   // "HH:mm" format
    val timeTo: String = "23:59",     // "HH:mm" format
    val password: String = "",
    val longText: String = "Focus is the key to success. I choose to block distractions and work with full dedication..."
)

// ==========================================
// 9. BLOCKED DATA — SharedPreferences store
//    Accessibility Service এই data পড়ে block করে
// ==========================================
object BlockedData {

    private const val PREFS_APPS  = "blocked_apps_store"
    private const val PREFS_SITES = "blocked_sites_store"
    private const val KEY_LIST    = "blocked_list_json"
    private val gson = Gson()

    // ── Apps ──────────────────────────────────────────────────────────────────

    fun blockApp(context: Context, packageName: String, config: BlockConfig) {
        val map = getBlockedAppsMap(context).toMutableMap()
        map[packageName] = config
        saveAppsMap(context, map)
    }

    fun unblockApp(context: Context, packageName: String) {
        val map = getBlockedAppsMap(context).toMutableMap()
        map.remove(packageName)
        saveAppsMap(context, map)
    }

    fun getBlockedApps(context: Context): Map<String, BlockConfig> =
        getBlockedAppsMap(context)

    fun isAppBlocked(context: Context, packageName: String): Boolean =
        getBlockedAppsMap(context).containsKey(packageName)

    private fun getBlockedAppsMap(context: Context): Map<String, BlockConfig> {
        val json = context
            .getSharedPreferences(PREFS_APPS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, BlockConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    private fun saveAppsMap(context: Context, map: Map<String, BlockConfig>) {
        context.getSharedPreferences(PREFS_APPS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, gson.toJson(map)).apply()
    }

    // ── Sites ─────────────────────────────────────────────────────────────────

    fun blockSite(context: Context, domain: String, config: BlockConfig) {
        val map = getBlockedSitesMap(context).toMutableMap()
        map[domain] = config
        saveSitesMap(context, map)
    }

    fun unblockSite(context: Context, domain: String) {
        val map = getBlockedSitesMap(context).toMutableMap()
        map.remove(domain)
        saveSitesMap(context, map)
    }

    fun getBlockedSites(context: Context): Map<String, BlockConfig> =
        getBlockedSitesMap(context)

    fun isSiteBlocked(context: Context, domain: String): Boolean =
        getBlockedSitesMap(context).containsKey(domain)

    private fun getBlockedSitesMap(context: Context): Map<String, BlockConfig> {
        val json = context
            .getSharedPreferences(PREFS_SITES, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, BlockConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    private fun saveSitesMap(context: Context, map: Map<String, BlockConfig>) {
        context.getSharedPreferences(PREFS_SITES, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, gson.toJson(map)).apply()
    }
}

// ==========================================
// 10. APP BLOCKER SERVICE (Removed Duplicate)
//     এই অংশটি মুছে ফেলা হয়েছে কারণ এটি BlockingPlan.kt এ আছে।
//     ডুপ্লিকেশন এড়াতে এখানে আর কোনো AppBlockerService অবজেক্ট নেই।
// ==========================================

// ==========================================
// 11. BLOCKER ROOT — MainActivity এ call হয়
//     NavController ছাড়া standalone wrapper
// ==========================================
@Composable
fun BlockerRoot() {
    // BlockerRoot এ নিজস্ব NavController নেই,
    // তাই IndividualAppLockScreen এর জন্য একটা
    // internal NavHost দিয়ে wrap করছি।
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "individual_lock_main"
    ) {
        composable("individual_lock_main") {
            IndividualAppLockScreen(navController = navController)
        }
    }
}

// ==========================================
// SaPermissionBanner — singel_apps screen এর জন্য
// ==========================================
@Composable
private fun SaPermissionBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    color: Color,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("দাও", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

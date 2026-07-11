package com.rasel.RasFocus.combo.parental

// ══════════════════════════════════════════════════════════════
//  RASFOCUS+ — CHILD PHONE CONTROL SCREEN
//  Companion to ParentControlScreen.kt (which handles PCs). This
//  file is the equivalent control surface for a child's MOBILE
//  device: app blocking, uninstall/tamper status, content filters,
//  screen-time + per-app time limits, and device lock.
//
//  Data flows through the real Device model + MainViewModel that
//  already exist in MainActivity.kt (users/<uid>/devices/<id> in
//  Firebase Realtime Database) — this is the same source of truth
//  ParentalDetailScreen reads, just with a dedicated, full screen.
// ══════════════════════════════════════════════════════════════

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rasel.pdfviewer.Device
import com.rasel.RasFocus.MainViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

// ══════════════════════════════════════════════════════════════
//  THEME — matches ParentControlScreen.kt's palette so PC control
//  and Phone control feel like one product.
// ══════════════════════════════════════════════════════════════
private val BgDark = Color(0xFF0F172A)
private val SurfaceCard = Color(0xFF1E293B)
private val SurfaceDeep = Color(0xFF334155)
private val Border = Color(0xFF1E293B)
private val BorderMid = Color(0xFF334155)
private val TealMain = Color(0xFF0096B4)
private val TealLight = Color(0xFF0096B4)
private val TealDim     = Color(0x2014B8A6)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSub     = Color(0xFF94A3B8)
private val TextMuted   = Color(0xFF64748B)
private val Rose        = Color(0xFFEF4444)
private val RoseDim     = Color(0x1AEF4444)
private val Amber       = Color(0xFFF59E0B)
private val AmberDim    = Color(0x1AF59E0B)

// ══════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ══════════════════════════════════════════════════════════════
@Composable
fun ChildPhoneControlScreen(
    viewModel: MainViewModel,
    deviceId: String,
    onBack: () -> Unit,
    // FIX: যখন কোনো child phone এখনো paired না (deviceId খালি), এই screen
    // এখন নিজেই PIN pairing UI দেখায় (আগে এই flow ParentalConnectScreen নামের
    // আলাদা generic screen এ ছিল)। Pairing সফল হলে child app নিজে থেকেই
    // users/<parentUid>/devices/<newId> এ write করে, যেটা viewModel.devices
    // এ realtime চলে আসে — সেই মুহূর্তে onDevicePaired(newId) কল করে parent
    // module কে জানিয়ে দেয়, যাতে সে navigation state আসল deviceId দিয়ে
    // replace করে দিতে পারে (ঠিক PC control এর onPaired এর মতোই প্যাটার্ন)।
    onDevicePaired: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val device = devices.find { it.id == deviceId }
    val isPairingMode = deviceId.isEmpty()

    var toastMessage by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    val toastScope = rememberCoroutineScope()
    fun showToast(msg: String) {
        toastMessage = msg
        toastVisible = true
        toastScope.launch { delay(2200); toastVisible = false }
    }

    val scrollState = rememberScrollState()

    Scaffold(containerColor = BgDark) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isPairingMode) {
                ChildPairingSection(
                    viewModel = viewModel,
                    existingDeviceIds = remember(devices) { devices.map { it.id }.toSet() },
                    onBack = onBack,
                    onPaired = onDevicePaired
                )
            } else if (device == null) {
                DeviceNotFound(onBack)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    PhoneHeader(device = device, onBack = onBack)
                    QuickActionsRow(device = device, viewModel = viewModel) { showToast(it) }
                    UninstallProtectionSection(device)
                    AppBlockingSection(device = device, viewModel = viewModel) { showToast(it) }
                    SpecificAppsSection(device = device, viewModel = viewModel, onToast = { showToast(it) })
                    SelfControlFeaturesSection(device = device, viewModel = viewModel, onToast = { showToast(it) })
                    NewlyInstalledAppsSection(device = device, viewModel = viewModel)
                    ContentFilterSection(device = device, viewModel = viewModel) { showToast(it) }
                    ScreenTimeSection(device = device, viewModel = viewModel) { showToast(it) }
                    BedtimeScheduleSection(device = device, viewModel = viewModel) { showToast(it) }
                    AppUsageSection(childUid = device.id)
                    WeeklyReportSection(childUid = device.id)
                    LocationSection(childUid = device.id)
                    YouTubeSafeModeSection(device = device, viewModel = viewModel) { showToast(it) }
                    AppTimeLimitSection(device = device, viewModel = viewModel) { showToast(it) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(
                visible = toastVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceCard, border = BorderStroke(1.dp, TealMain), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = TealMain, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(toastMessage, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  PAIRING — PIN generation UI for connecting a new child phone.
//  Mirrors ParentControlScreen.kt's PC pairing flow, but the
//  "device just paired" signal is different: the PC app flips
//  pairing_codes/<pin>.used = true, while the child MOBILE app
//  (ChildPairing.pairWithPin in ChildControlModule.kt) instead
//  writes itself directly to users/<parentUid>/devices/<id> — so
//  here we just watch viewModel.devices for a device id that
//  wasn't in the list when this screen opened.
// ══════════════════════════════════════════════════════════════
@Composable
private fun ChildPairingSection(
    viewModel: MainViewModel,
    existingDeviceIds: Set<String>,
    onBack: () -> Unit,
    onPaired: (String) -> Unit
) {
    val connectionPin by viewModel.connectionPin.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val context = LocalContext.current

    // Screen খোলার সাথে সাথে নতুন PIN generate করে Firebase এ save করো,
    // ঠিক PC pairing flow এর মতোই।
    LaunchedEffect(Unit) {
        viewModel.openPairDialog()
    }
    // Screen বন্ধ হলে unused PIN মুছে ফেলো, যাতে পুরনো PIN দিয়ে পরে কেউ
    // পেয়ার করতে না পারে।
    DisposableEffect(Unit) {
        onDispose { viewModel.closePairDialog() }
    }
    // নতুন device কখন list এ আসলো সেটাই এখানে "paired" হওয়ার সংকেত —
    // child app pairing_codes/<pin> থেকে parent_uid পড়ে সরাসরি নিজেকে
    // users/<parentUid>/devices/<newId> এ বসিয়ে দেয় (ChildControlModule.kt
    // এর ChildPairing.pairWithPin), কোনো "used" flag সেট করে না।
    LaunchedEffect(devices) {
        val newDeviceId = devices.map { it.id }.firstOrNull { it !in existingDeviceIds }
        if (newDeviceId != null) {
            onPaired(newDeviceId)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
                }
                Text("Pair child's mobile", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        containerColor = BgDark
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = TealDim,
                border = BorderStroke(1.dp, TealMain.copy(alpha = 0.25f))
            ) {
                Box(modifier = Modifier.size(88.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = TealMain, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Pairing code", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Install RasFocus+ on the child's phone, then enter this code there.",
                fontSize = 14.sp, color = TextSub, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))

            val displayPin = connectionPin.padStart(6, '0').takeLast(6)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                displayPin.forEach { digit ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Box(modifier = Modifier.size(width = 42.dp, height = 54.dp), contentAlignment = Alignment.Center) {
                            Text(
                                digit.toString(),
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TealLight
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.refreshPin() },
                    border = BorderStroke(1.dp, BorderMid)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = TealLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New code", color = TealLight)
                }
                Button(
                    onClick = {
                        val text = "RasFocus pairing code: $connectionPin\nInstall RasFocus+ on the phone and enter this code."
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share pairing code"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealMain)
                ) {
                    Icon(Icons.Default.Share, null, tint = BgDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", color = BgDark, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TealMain)
                Spacer(Modifier.width(8.dp))
                Text("Waiting for child's phone…", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun DeviceNotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PhonelinkErase, null, tint = TextMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Device not found", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, BorderMid)) {
            Text("Go back", color = TextSub)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  HEADER
// ══════════════════════════════════════════════════════════════
@Composable
private fun PhoneHeader(device: Device, onBack: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(SurfaceDeep)
                        .border(BorderStroke(1.dp, BorderMid), CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.size(40.dp).background(TealMain.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Smartphone, null, tint = TealMain, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(device.ownerName.ifBlank { "Child phone" }, fontSize = 11.sp, color = TextMuted)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (device.isOnline) TealMain.copy(alpha = 0.12f) else BorderMid.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, if (device.isOnline) TealMain.copy(alpha = 0.25f) else BorderMid)
                ) {
                    Text(
                        if (device.isOnline) "● Online" else "○ Offline",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (device.isOnline) TealLight else TextSub
                    )
                }
            }
            if (device.isOnline) {
                HorizontalDivider(color = Border)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.BatteryStd, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${device.batteryLevel}% battery", fontSize = 12.sp, color = TextSub)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  QUICK ACTIONS — Lock / Unlock
// ══════════════════════════════════════════════════════════════
@Composable
private fun QuickActionsRow(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    viewModel.toggleDeviceLock(device.id)
                    onToast(if (device.isLocked) "Unlock command sent" else "Lock command sent")
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (device.isLocked) RoseDim else TealDim)
            ) {
                Icon(if (device.isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock, null,
                    tint = if (device.isLocked) Rose else TealLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (device.isLocked) "Unlock Device" else "Lock Device",
                    color = if (device.isLocked) Rose else TealLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  UNINSTALL / TAMPER PROTECTION STATUS (read-only — reported by
//  the child device itself; real protection lives at the OS level
//  via Device Admin, so the parent can see status but the "action"
//  happens on the child's phone).
// ══════════════════════════════════════════════════════════════
@Composable
private fun UninstallProtectionSection(device: Device) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🛡️", "Uninstall Protection", "Prevents removing RasFocus+ from this phone", TealMain)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProtectionStatusRow("Device Admin", device.isDeviceAdminActive)
                ProtectionStatusRow("Accessibility Service", device.isAccessibilityActive)
                if (!device.isDeviceAdminActive || !device.isAccessibilityActive) {
                    Surface(shape = RoundedCornerShape(10.dp), color = AmberDim, border = BorderStroke(1.dp, Amber.copy(alpha = 0.25f))) {
                        Text(
                            "Some protections aren't active yet. Ask your child to open RasFocus+ and finish the permission setup.",
                            modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = Amber, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionStatusRow(label: String, active: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (active) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            null, tint = if (active) TealMain else Rose, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(if (active) "Active" else "Inactive", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (active) TealLight else Rose)
    }
}

// ══════════════════════════════════════════════════════════════
//  APP BLOCKING
// ══════════════════════════════════════════════════════════════
@Composable
private fun AppBlockingSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    var packageInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("📱", "App Blocking", "Block specific apps on this phone", TealMain)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("App name", color = TextMuted) },
                    placeholder = { Text("e.g. TikTok", color = TextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealMain, unfocusedBorderColor = BorderMid,
                        focusedContainerColor = BgDark, unfocusedContainerColor = BgDark,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = packageInput, onValueChange = { packageInput = it },
                    label = { Text("Package name", color = TextMuted) },
                    placeholder = { Text("e.g. com.zhiliaoapp.musically", color = TextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealMain, unfocusedBorderColor = BorderMid,
                        focusedContainerColor = BgDark, unfocusedContainerColor = BgDark
                    )
                )
                Button(
                    onClick = {
                        if (packageInput.isBlank()) { onToast("Enter a package name first"); return@Button }
                        viewModel.addBlockedApp(device.id, packageInput.trim(), nameInput.trim())
                        onToast("${nameInput.ifBlank { packageInput }} blocked")
                        packageInput = ""; nameInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealMain)
                ) {
                    Icon(Icons.Filled.Block, null, tint = BgDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Block App", fontWeight = FontWeight.Bold, color = BgDark)
                }

                if (device.blockedAppsList.isNotEmpty()) {
                    HorizontalDivider(color = Border)
                    Text("BLOCKED (${device.blockedAppsList.size})", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        device.blockedAppsList.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(BgDark, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.icon, fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(app.packageName, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                }
                                Icon(
                                    Icons.Filled.Close, contentDescription = "Remove",
                                    tint = TextMuted, modifier = Modifier.size(18.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            viewModel.removeBlockedApp(device.id, app.id)
                                            onToast("${app.displayName} unblocked")
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  CONTENT FILTERS (Adult content, Shorts, Reels, Incognito)
// ══════════════════════════════════════════════════════════════
@Composable
private fun ContentFilterSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🚫", "Content Filters", "Adult content, Shorts, Reels & Incognito", Rose)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                PhoneToggleRow("Adult Content Block (Halal Guard)", "Filters explicit content across apps & browser",
                    device.isHalalGuardOn, onChecked = { viewModel.toggleHalalGuard(device.id); onToast("Setting updated") })
                PhoneToggleRow("Block YouTube Shorts", "Hides the Shorts shelf and player",
                    device.blockYoutubeShorts, onChecked = { viewModel.toggleYoutubeShorts(device.id); onToast("Setting updated") })
                PhoneToggleRow("Block Reels", "Hides Instagram/Facebook Reels",
                    device.blockReels, onChecked = { viewModel.toggleReels(device.id); onToast("Setting updated") })
                PhoneToggleRow("Block Incognito Browsing", "Disables private/incognito tabs",
                    device.blockIncognito, onChecked = { viewModel.toggleIncognito(device.id); onToast("Setting updated") }, isLast = true)
            }
        }
    }
}

@Composable
private fun PhoneToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, isLast: Boolean = false) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable { onChecked(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp)
            }
            Switch(
                checked = checked, onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = TealMain,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = BorderMid
                )
            )
        }
        if (!isLast) HorizontalDivider(color = Border.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
    }
}

// ══════════════════════════════════════════════════════════════
//  SCREEN TIME (total daily limit)
// ══════════════════════════════════════════════════════════════
@Composable
private fun ScreenTimeSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    var hours by remember(device.id, device.screenTimeLimitMinutes) { mutableStateOf((device.screenTimeLimitMinutes / 60).toString()) }
    var minutes by remember(device.id, device.screenTimeLimitMinutes) { mutableStateOf((device.screenTimeLimitMinutes % 60).toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("⏱️", "Screen Time Limit", "Total daily usage allowed on this phone", Amber)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (device.screenTimeLimitMinutes > 0) {
                    val progress = (device.screenTimeUsedMinutes.toFloat() / device.screenTimeLimitMinutes).coerceIn(0f, 1f)
                    Text("${device.screenTimeUsedMinutes}m used today of ${device.screenTimeLimitMinutes}m", fontSize = 13.sp, color = TextSub)
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                        color = if (progress > 0.9f) Rose else TealMain, trackColor = Border
                    )
                } else {
                    Text("No daily limit set. 0h 0m disables the limit.", fontSize = 13.sp, color = TextSub, lineHeight = 18.dp.value.sp)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneTimeInput("Hours", hours) { hours = it }
                    Text(":", fontSize = 24.sp, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                    PhoneTimeInput("Minutes", minutes) { minutes = it }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val h = hours.toIntOrNull() ?: 0
                            val m = minutes.toIntOrNull() ?: 0
                            viewModel.setScreenTimeLimit(device.id, h * 60 + m)
                            onToast(if (h == 0 && m == 0) "Screen time limit disabled" else "Limit set: ${h}h ${m}m")
                        },
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = TealMain)
                    ) { Text("Set Limit", fontWeight = FontWeight.Bold, color = BgDark) }
                }
            }
        }
    }
}

@Composable
private fun PhoneTimeInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = { if (it.length <= 3) onValueChange(it.filter(Char::isDigit)) },
            modifier = Modifier.width(70.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealMain, unfocusedBorderColor = BorderMid,
                unfocusedContainerColor = BgDark, focusedContainerColor = BgDark
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════
//  PER-APP TIME LIMIT
// ══════════════════════════════════════════════════════════════
@Composable
private fun AppTimeLimitSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    var pkgInput by remember { mutableStateOf("") }
    var minInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("⏳", "Per-App Time Limit", "Give a specific app its own daily budget", TealMain)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pkgInput, onValueChange = { pkgInput = it },
                        label = { Text("Package name", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealMain, unfocusedBorderColor = BorderMid,
                            focusedContainerColor = BgDark, unfocusedContainerColor = BgDark
                        )
                    )
                    OutlinedTextField(
                        value = minInput, onValueChange = { if (it.length <= 4) minInput = it.filter(Char::isDigit) },
                        label = { Text("Minutes", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true, modifier = Modifier.width(90.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealMain, unfocusedBorderColor = BorderMid,
                            focusedContainerColor = BgDark, unfocusedContainerColor = BgDark
                        )
                    )
                }
                Button(
                    onClick = {
                        val minutes = minInput.toIntOrNull() ?: 0
                        if (pkgInput.isBlank() || minutes <= 0) { onToast("Enter package name and minutes"); return@Button }
                        viewModel.setAppTimeLimit(device.id, pkgInput.trim(), minutes)
                        onToast("Limit set: ${minutes}m/day for ${pkgInput.trim()}")
                        pkgInput = ""; minInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealDim)
                ) { Text("Set App Limit", fontWeight = FontWeight.Bold, color = TealLight) }

                if (device.appTimeLimitsMinutes.isNotEmpty()) {
                    HorizontalDivider(color = Border)
                    device.appTimeLimitsMinutes.forEach { (pkg, minutes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(BgDark, RoundedCornerShape(10.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                Text("$minutes minutes / day", fontSize = 11.sp, color = TextSub)
                            }
                            Icon(
                                Icons.Filled.Close, contentDescription = "Remove limit", tint = TextMuted,
                                modifier = Modifier.size(18.dp).clip(CircleShape).clickable {
                                    viewModel.setAppTimeLimit(device.id, pkg, 0)
                                    onToast("Limit removed for $pkg")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  SHARED SECTION HEADER (visually matches ParentControlScreen.kt)
// ══════════════════════════════════════════════════════════════
@Composable
private fun SectionHeader(emoji: String, title: String, subtitle: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = accentColor.copy(alpha = 0.1f), border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))) {
            Text(emoji, modifier = Modifier.padding(10.dp), fontSize = 20.sp)
        }
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSub)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  APP USAGE SECTION
//  Firebase: children/<childUid>/usageStats/today এ যা AppUsageSyncService
//  push করে সেটা real-time এ পড়ে দেখায়।
// ══════════════════════════════════════════════════════════════
data class AppUsageStat(
    val appName: String,
    val packageName: String,
    val totalMinutes: Int
)

@Composable
fun AppUsageSection(childUid: String) {
    var stats    by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var totalMin by remember { mutableStateOf(0) }
    var loading  by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    // Firebase real-time listener
    DisposableEffect(childUid) {
        val ref = Firebase.database.reference.child("children/$childUid/usageStats")

        val summaryListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                totalMin = (snap.child("summary/totalMinutesToday")
                    .getValue(Long::class.java) ?: 0L).toInt()
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        val todayListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = mutableListOf<AppUsageStat>()
                snap.child("today").children.forEach { child ->
                    val name  = child.child("appName").getValue(String::class.java) ?: return@forEach
                    val pkg   = child.child("packageName").getValue(String::class.java) ?: ""
                    val mins  = (child.child("totalMinutes").getValue(Long::class.java) ?: 0L).toInt()
                    if (mins > 0) list.add(AppUsageStat(name, pkg, mins))
                }
                stats   = list.sortedByDescending { it.totalMinutes }
                loading = false
            }
            override fun onCancelled(e: DatabaseError) { loading = false }
        }

        ref.addValueEventListener(summaryListener)
        ref.addValueEventListener(todayListener)

        onDispose {
            ref.removeEventListener(summaryListener)
            ref.removeEventListener(todayListener)
        }
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.BarChart, null, tint = TealMain, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("App Usage Today", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, modifier = Modifier.weight(1f))
                if (totalMin > 0) {
                    val h = totalMin / 60; val m = totalMin % 60
                    Text(if (h > 0) "${h}h ${m}m" else "${m}m",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealLight)
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TealMain, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Loading usage data...", fontSize = 13.sp, color = TextSub)
                    }
                }
                stats.isEmpty() -> {
                    Text("No usage data yet. Child phone sends data every 15 minutes.",
                        fontSize = 13.sp, color = TextSub, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                else -> {
                    val displayStats = if (expanded) stats else stats.take(5)
                    val maxMin = stats.first().totalMinutes.toFloat()

                    displayStats.forEach { stat ->
                        AppUsageRow(stat = stat, maxMin = maxMin)
                        Spacer(Modifier.height(8.dp))
                    }

                    if (stats.size > 5) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (expanded) "Show less" else "Show all ${stats.size} apps",
                                color = TealLight, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(stat: AppUsageStat, maxMin: Float) {
    val progress = (stat.totalMinutes / maxMin).coerceIn(0f, 1f)
    val h = stat.totalMinutes / 60; val m = stat.totalMinutes % 60
    val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App initial circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TealDim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stat.appName.take(1).uppercase(),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealLight
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(stat.appName, fontSize = 13.sp, color = TextPrimary,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                maxLines = 1)
            Text(timeStr, fontSize = 13.sp, color = TealLight, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        // Usage bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(TealMain)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FEATURE 2: BEDTIME SCHEDULE
// ══════════════════════════════════════════════════════════════
@Composable
fun BedtimeScheduleSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    var enabled    by remember { mutableStateOf(false) }
    var startHour  by remember { mutableStateOf(22) }
    var startMin   by remember { mutableStateOf(0) }
    var endHour    by remember { mutableStateOf(7) }
    var endMin     by remember { mutableStateOf(0) }
    var expanded   by remember { mutableStateOf(false) }
    val scope      = rememberCoroutineScope()
    val db         = Firebase.database.reference

    // Load existing schedule from Firebase
    LaunchedEffect(device.id) {
        db.child("children/${device.id}/commands/bedtime").get().addOnSuccessListener { snap ->
            enabled    = snap.child("enabled").getValue(Boolean::class.java) ?: false
            startHour  = snap.child("startHour").getValue(Long::class.java)?.toInt() ?: 22
            startMin   = snap.child("startMin").getValue(Long::class.java)?.toInt()  ?: 0
            endHour    = snap.child("endHour").getValue(Long::class.java)?.toInt()   ?: 7
            endMin     = snap.child("endMin").getValue(Long::class.java)?.toInt()    ?: 0
        }
    }

    fun save() {
        scope.launch {
            db.child("children/${device.id}/commands/bedtime").setValue(mapOf(
                "enabled"   to enabled,
                "startHour" to startHour, "startMin" to startMin,
                "endHour"   to endHour,   "endMin"   to endMin
            ))
            onToast(if (enabled) "Bedtime schedule saved ✓" else "Bedtime disabled")
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Bedtime, null, tint = TealMain, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bedtime Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; save() },
                    colors = SwitchDefaults.colors(checkedThumbColor = TealMain, checkedTrackColor = TealDim)
                )
            }

            if (enabled) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bedtime", fontSize = 12.sp, color = TextSub)
                        Spacer(Modifier.height(4.dp))
                        TimePickerButton(
                            hour = startHour, min = startMin,
                            onSet = { h, m -> startHour = h; startMin = m; save() }
                        )
                    }
                    Icon(Icons.Filled.ArrowForward, null, tint = TextSub,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Wake up", fontSize = 12.sp, color = TextSub)
                        Spacer(Modifier.height(4.dp))
                        TimePickerButton(
                            hour = endHour, min = endMin,
                            onSet = { h, m -> endHour = h; endMin = m; save() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePickerButton(hour: Int, min: Int, onSet: (Int, Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TealMain)
    ) {
        Text(
            "%02d:%02d".format(hour, min),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TealLight,
            fontFamily = FontFamily.Monospace
        )
    }
    if (showPicker) {
        TimePickerDialog(
            initialHour = hour, initialMin = min,
            onConfirm = { h, m -> onSet(h, m); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialHour: Int, initialMin: Int,
    onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit
) {
    var h by remember { mutableStateOf(initialHour.toString().padStart(2,'0')) }
    var m by remember { mutableStateOf(initialMin.toString().padStart(2,'0')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set time") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = h, onValueChange = { if (it.length <= 2) h = it },
                    modifier = Modifier.width(64.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, label = { Text("HH") }
                )
                Text("  :  ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = m, onValueChange = { if (it.length <= 2) m = it },
                    modifier = Modifier.width(64.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, label = { Text("MM") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hh = h.toIntOrNull()?.coerceIn(0, 23) ?: initialHour
                val mm = m.toIntOrNull()?.coerceIn(0, 59) ?: initialMin
                onConfirm(hh, mm)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ══════════════════════════════════════════════════════════════
// FEATURE 3: LOCATION SECTION
// ══════════════════════════════════════════════════════════════
@Composable
fun LocationSection(childUid: String) {
    var lat      by remember { mutableStateOf<Double?>(null) }
    var lng      by remember { mutableStateOf<Double?>(null) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var updatedAt by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(childUid) {
        val ref = Firebase.database.reference.child("children/$childUid/location")
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                lat       = snap.child("latitude").getValue(Double::class.java)
                lng       = snap.child("longitude").getValue(Double::class.java)
                accuracy  = snap.child("accuracy").getValue(Double::class.java)?.toFloat()
                updatedAt = snap.child("timestamp").getValue(Long::class.java)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = TealMain, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Last Known Location", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))

            if (lat != null && lng != null) {
                val timeAgo = updatedAt?.let {
                    val diff = System.currentTimeMillis() - it
                    when {
                        diff < 60_000 -> "Just now"
                        diff < 3_600_000 -> "${diff/60_000}m ago"
                        diff < 86_400_000 -> "${diff/3_600_000}h ago"
                        else -> "${diff/86_400_000}d ago"
                    }
                } ?: "Unknown"

                Text("📍 %.5f, %.5f".format(lat, lng),
                    fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Updated: $timeAgo", fontSize = 12.sp, color = TextSub)
                    accuracy?.let {
                        Text("  •  ±${it.toInt()}m", fontSize = 12.sp, color = TextSub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(Child+Location)")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        if (mapIntent.resolveActivity(context.packageManager) != null)
                            context.startActivity(mapIntent)
                        else
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://maps.google.com/?q=$lat,$lng")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealMain),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Maps", fontWeight = FontWeight.Bold)
                }
            } else {
                Text("No location data yet. Location updates every 5 minutes.",
                    fontSize = 13.sp, color = TextSub)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FEATURE 4: YOUTUBE SAFE MODE
// ══════════════════════════════════════════════════════════════
@Composable
fun YouTubeSafeModeSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    val db = Firebase.database.reference
    val scope = rememberCoroutineScope()

    LaunchedEffect(device.id) {
        db.child("children/${device.id}/commands/youtubeSafeMode").get()
            .addOnSuccessListener { snap ->
                enabled = snap.getValue(Boolean::class.java) ?: false
            }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayCircle, null, tint = if (enabled) Rose else TextSub,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("YouTube Safe Mode", fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    if (enabled) "Adult content blocked on YouTube"
                    else "YouTube unrestricted",
                    fontSize = 12.sp, color = TextSub
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { v ->
                    enabled = v
                    scope.launch {
                        db.child("children/${device.id}/commands/youtubeSafeMode").setValue(v)
                        onToast(if (v) "YouTube Safe Mode enabled ✓" else "YouTube Safe Mode disabled")
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = TealMain, checkedTrackColor = TealDim)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FEATURE 5: WEEKLY USAGE REPORT
// ══════════════════════════════════════════════════════════════
data class DailyUsage(val day: String, val minutes: Int)

@Composable
fun WeeklyReportSection(childUid: String) {
    var weekData by remember { mutableStateOf<List<DailyUsage>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }

    LaunchedEffect(childUid) {
        // Firebase থেকে শেষ 7 দিনের summary পড়ো
        // AppUsageSyncService প্রতিদিনের data children/<uid>/usageStats/history/<date> এ রাখে
        val ref = Firebase.database.reference.child("children/$childUid/usageStats/history")
        ref.limitToLast(7).get().addOnSuccessListener { snap ->
            val list = mutableListOf<DailyUsage>()
            snap.children.forEach { child ->
                val date = child.key ?: return@forEach
                val mins = child.child("totalMinutes").getValue(Long::class.java)?.toInt() ?: 0
                // date format: 2026-07-04 → show "Jul 4"
                val label = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val d = sdf.parse(date)
                    java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(d!!)
                } catch (e: Exception) { date.takeLast(5) }
                list.add(DailyUsage(label, mins))
            }
            weekData = list
            loading  = false
        }.addOnFailureListener { loading = false }
    }

    if (loading || weekData.isEmpty()) return

    val maxMin = weekData.maxOf { it.minutes }.coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, null, tint = TealMain, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Weekly Screen Time", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(14.dp))

            // Bar chart
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weekData.forEach { day ->
                    val frac = day.minutes.toFloat() / maxMin
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(frac.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(TealMain.copy(alpha = 0.7f + 0.3f * frac))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(day.day, fontSize = 9.sp, color = TextSub, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val totalWeek = weekData.sumOf { it.minutes }
            val avgMin = if (weekData.isNotEmpty()) totalWeek / weekData.size else 0
            Text(
                "Avg: ${avgMin/60}h ${avgMin%60}m/day  •  Total: ${totalWeek/60}h ${totalWeek%60}m",
                fontSize = 12.sp, color = TextSub, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FEATURE 6: SPECIFIC APPS SCHEDULE
// ══════════════════════════════════════════════════════════════
@Composable
private fun SpecificAppsSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("🎯", "Specific App Control", "Block popular apps with schedules", TealMain)
            Spacer(modifier = Modifier.height(16.dp))
            AppScheduleRow(
                appName = "Facebook",
                enabled = device.fbEnabled,
                onEnabledChange = { viewModel.updateDeviceField(device.id, "fbEnabled", it); onToast("Facebook schedule updated") },
                startTime = device.fbStartTime,
                onStartTimeChange = { viewModel.updateDeviceField(device.id, "fbStartTime", it) },
                endTime = device.fbEndTime,
                onEndTimeChange = { viewModel.updateDeviceField(device.id, "fbEndTime", it) }
            )
            HorizontalDivider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            AppScheduleRow(
                appName = "Facebook Lite",
                enabled = device.fbLiteEnabled,
                onEnabledChange = { viewModel.updateDeviceField(device.id, "fbLiteEnabled", it); onToast("FB Lite schedule updated") },
                startTime = device.fbLiteStartTime,
                onStartTimeChange = { viewModel.updateDeviceField(device.id, "fbLiteStartTime", it) },
                endTime = device.fbLiteEndTime,
                onEndTimeChange = { viewModel.updateDeviceField(device.id, "fbLiteEndTime", it) }
            )
            HorizontalDivider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            AppScheduleRow(
                appName = "YouTube",
                enabled = device.ytEnabled,
                onEnabledChange = { viewModel.updateDeviceField(device.id, "ytEnabled", it); onToast("YouTube schedule updated") },
                startTime = device.ytStartTime,
                onStartTimeChange = { viewModel.updateDeviceField(device.id, "ytStartTime", it) },
                endTime = device.ytEndTime,
                onEndTimeChange = { viewModel.updateDeviceField(device.id, "ytEndTime", it) }
            )
            HorizontalDivider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            AppScheduleRow(
                appName = "Chrome",
                enabled = device.chromeEnabled,
                onEnabledChange = { viewModel.updateDeviceField(device.id, "chromeEnabled", it); onToast("Chrome schedule updated") },
                startTime = device.chromeStartTime,
                onStartTimeChange = { viewModel.updateDeviceField(device.id, "chromeStartTime", it) },
                endTime = device.chromeEndTime,
                onEndTimeChange = { viewModel.updateDeviceField(device.id, "chromeEndTime", it) }
            )
        }
    }
}

@Composable
private fun AppScheduleRow(
    appName: String,
    enabled: Boolean, onEnabledChange: (Boolean) -> Unit,
    startTime: String, onStartTimeChange: (String) -> Unit,
    endTime: String, onEndTimeChange: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onEnabledChange(!enabled) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(appName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealMain)
            )
        }
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TimeInputField(label = "Start (HH:MM)", value = startTime, onValueChange = onStartTimeChange)
                }
                Box(modifier = Modifier.weight(1f)) {
                    TimeInputField(label = "End (HH:MM)", value = endTime, onValueChange = onEndTimeChange)
                }
            }
        }
    }
}

@Composable
private fun TimeInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, color = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealMain,
            unfocusedBorderColor = BorderMid,
            focusedContainerColor = BgDark,
            unfocusedContainerColor = BgDark,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

// ══════════════════════════════════════════════════════════════
// FEATURE 7: SELF CONTROL FEATURES
// ══════════════════════════════════════════════════════════════
@Composable
private fun SelfControlFeaturesSection(device: Device, viewModel: MainViewModel, onToast: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🧘", "Self Control Profiles", "Advanced blocking modes from Self Control module", TealMain)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                PhoneToggleRow("Deep Study Mode", "Blocks everything except study apps",
                    device.deepStudyEnabled, onChecked = { viewModel.updateDeviceField(device.id, "deepStudyEnabled", it); onToast("Deep Study updated") })
                PhoneToggleRow("Button Phone Mode", "Simulates a dumb phone",
                    device.buttonPhoneEnabled, onChecked = { viewModel.updateDeviceField(device.id, "buttonPhoneEnabled", it); onToast("Button Phone updated") })
                PhoneToggleRow("Single Apps Block", "Strictly block a specific app",
                    device.singleAppsBlockEnabled, onChecked = { viewModel.updateDeviceField(device.id, "singleAppsBlockEnabled", it); onToast("Single Apps Block updated") })
                PhoneToggleRow("Extreme Block", "Cannot be disabled easily",
                    device.extremeBlockEnabled, onChecked = { viewModel.updateDeviceField(device.id, "extremeBlockEnabled", it); onToast("Extreme Block updated") })
                PhoneToggleRow("Single Website Block", "Block only one specific website",
                    device.singleWebsiteBlockEnabled, onChecked = { viewModel.updateDeviceField(device.id, "singleWebsiteBlockEnabled", it); onToast("Single Website Block updated") })
                PhoneToggleRow("Family Browser", "Enforce safe browsing",
                    device.familyBrowserEnabled, onChecked = { viewModel.updateDeviceField(device.id, "familyBrowserEnabled", it); onToast("Family Browser updated") }, isLast = true)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// FEATURE 8: NEWLY INSTALLED APPS
// ══════════════════════════════════════════════════════════════
@Composable
private fun NewlyInstalledAppsSection(device: Device, viewModel: MainViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader("🆕", "Newly Installed Apps", "Apps recently installed by child", TealMain)
            Spacer(modifier = Modifier.height(16.dp))
            if (device.newInstalledAppsCsv.isBlank()) {
                Text("No newly installed apps detected.", color = TextMuted, fontSize = 13.sp)
            } else {
                val apps = device.newInstalledAppsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                apps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Android, null, tint = TealMain, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(app, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

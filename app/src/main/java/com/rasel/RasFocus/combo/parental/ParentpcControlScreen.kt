package com.rasel.RasFocus.combo.parental

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect

// ══════════════════════════════════════════════════════════════
//  THEME COLORS
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
private val Purple      = Color(0xFFA855F7)
private val PurpleDim   = Color(0x1AA855F7)
private val Orange      = Color(0xFFF97316)
private val OrangeDim   = Color(0x1AF97316)
private val Cyan        = Color(0xFF22D3EE)
private val CyanDim     = Color(0x1A22D3EE)

// ══════════════════════════════════════════════════════════════
//  DATA CLASSES
// ══════════════════════════════════════════════════════════════
data class ParentControls(
    val lockAllTabs: Boolean = false,
    val forceAdultBlock: Boolean = false,
    val forceReelsBlock: Boolean = false,
    val forceShortsBlock: Boolean = false,
    val appControlEnabled: Boolean = false,
    val appMode: String = "BLOCK",
    val allowedAppsCsv: String = "",
    val blockedAppsCsv: String = "",
    val webBlockEnabled: Boolean = false,
    val blockedWebsCsv: String = "",
    val blockTaskManager: Boolean = false,
    val blockSettings: Boolean = false,
    val blockFileManager: Boolean = false,
    val blockedFoldersCsv: String = "",
    val internetFasting: Boolean = false,
    val timeLimitMinutes: Int = 0,
    val powerAction: Int = 0,
    val lockUntilEpoch: Long = 0L,
    val lockType: String = "",
    val newInstalledAppsCsv: String = "",
    // Specific Apps
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
    val chromeEndTime: String = "00:00",
    // Self Control Settings
    val deepStudyEnabled: Boolean = false,
    val buttonPhoneEnabled: Boolean = false,
    val singleAppsBlockEnabled: Boolean = false,
    val extremeBlockEnabled: Boolean = false,
    val singleWebsiteBlockEnabled: Boolean = false,
    val familyBrowserEnabled: Boolean = false
)

// ══════════════════════════════════════════════════════════════
//  MAIN SCREEN  —  Header scrolls away with content (not fixed).
//  Footer is a single, clean action bar (no duplicated controls).
// ══════════════════════════════════════════════════════════════
@Composable
fun ParentControlScreen(
    onBack: () -> Unit = {},
    deviceId: String? = null,
    viewModel: com.rasel.pdfviewer.MainViewModel? = null,
    pin: String = "------",
    devices: List<Pair<String, Boolean>> = emptyList(),
    selectedDevice: String? = null,
    activeRulesCount: Int = 0,
    controls: ParentControls = ParentControls(),
    onRefreshPin: () -> Unit = {},
    onSelectDevice: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onControlChange: (ParentControls) -> Unit = {},
    onSendPower: (Int) -> Unit = {},
    onScheduleLock: (Long, String) -> Unit = { _, _ -> },
    onCancelSchedule: () -> Unit = {},
    onPaired: (String) -> Unit = {}
) {
    val isFirebaseMode = viewModel != null && !deviceId.isNullOrEmpty()

    if (isFirebaseMode) {
        DisposableEffect(deviceId) {
            viewModel!!.startPcControlSync(deviceId!!)
            onDispose { viewModel.stopPcControlSync(deviceId) }
        }
    } else {
        // No PC paired yet — auto-generate a pin AND persist it to Firebase
        // (pairing_codes/<pin>) the moment this screen opens, instead of only
        // ever showing the static default until the user manually taps "New PIN".
        LaunchedEffect(Unit) {
            onRefreshPin()
        }

        // Watch for the PC to actually finish pairing with this PIN
        // (tab_family_link.cpp flips pairing_codes/<pin>.used = true once
        // it verifies + registers itself). Previously nothing listened for
        // this, so the phone kept showing the PIN forever even after the PC
        // said "Connected" on its side.
        LaunchedEffect(pin) {
            if (pin.length == 6 && pin != "------") {
                viewModel?.listenForPcPairing(pin)
            }
        }
        val pairedId = viewModel?.pairedPcDeviceId?.collectAsState(null)?.value
        LaunchedEffect(pairedId) {
            pairedId?.let { hwId ->
                viewModel?.stopPcPairingListener()
                viewModel?.clearPairedPcDeviceId()
                onPaired(hwId)
            }
        }
        DisposableEffect(Unit) {
            onDispose { viewModel?.stopPcPairingListener() }
        }
    }

    val firebaseControls = (viewModel?.pcControls
        ?: kotlinx.coroutines.flow.MutableStateFlow(ParentControls())).collectAsState(ParentControls()).value
    val activeControls = if (isFirebaseMode) firebaseControls else controls

    val activeRules = if (isFirebaseMode) listOf(
        activeControls.lockAllTabs, activeControls.forceAdultBlock, activeControls.forceReelsBlock,
        activeControls.forceShortsBlock, activeControls.appControlEnabled, activeControls.webBlockEnabled,
        activeControls.blockTaskManager, activeControls.blockSettings, activeControls.blockFileManager,
        activeControls.internetFasting
    ).count { it } else activeRulesCount

    val onControlChangeActive: (ParentControls) -> Unit = if (isFirebaseMode)
        { c -> viewModel!!.updatePcControls(deviceId!!, c) } else onControlChange

    val onSendPowerActive: (Int) -> Unit = if (isFirebaseMode)
        { a -> viewModel!!.sendPcPowerCommand(deviceId!!, a) } else onSendPower

    val onScheduleLockActive: (Long, String) -> Unit = if (isFirebaseMode)
        { ms, t -> viewModel!!.schedulePcLock(deviceId!!, ms, t) } else onScheduleLock

    val onCancelScheduleActive: () -> Unit = if (isFirebaseMode)
        { -> viewModel!!.cancelPcSchedule(deviceId!!) } else onCancelSchedule

    // ── Toast state ──────────────────────────────────────────
    var toastMessage by remember { mutableStateOf("") }
    var toastType    by remember { mutableStateOf("ok") }
    var toastVisible by remember { mutableStateOf(false) }
    val toastScope   = rememberCoroutineScope()

    fun showToast(msg: String, type: String = "ok") {
        toastMessage = msg
        toastType    = type
        toastVisible = true
        toastScope.launch {
            delay(3000)
            toastVisible = false
        }
    }

    val scrollState = rememberScrollState()

    // Selected device online status (passed in via devices list)
    val selectedDeviceOnline = devices.find { it.first == selectedDevice }?.second

    // Device picker sheet state
    var showDevicePicker by remember { mutableStateOf(false) }

    Scaffold(
        // NOTE: intentionally no `topBar` here — the header lives inside the
        // scrollable content below so it moves with the page instead of
        // staying pinned. Only ONE bottom action bar is used for footer
        // controls (PIN + Devices), avoiding any duplicated footer buttons.
        bottomBar = {
            ParentFooterBar(
                pin            = pin,
                selectedDevice = selectedDevice,
                onRefreshPin   = onRefreshPin,
                onOpenDevices  = { showDevicePicker = true }
            )
        },
        containerColor = BgDark
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            // ── Subtle grid background ───────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSpacing = 40.dp.toPx()
                val lineColor   = Color(0x06FFFFFF)
                var x = 0f
                while (x < size.width) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f),
                        androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                    x += gridSpacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y),
                        androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                    y += gridSpacing
                }
            }

            // ── Teal glow top-left ───────────────────────────
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset((-50).dp, (-50).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(TealMain.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                    .blur(60.dp)
            )

            // ── Single-column scrollable content (header included) ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header now scrolls away with the rest of the page.
                ParentHeader(
                    activeRulesCount     = activeRules,
                    selectedDeviceOnline = selectedDeviceOnline,
                    selectedDevice       = selectedDevice,
                    onBack               = onBack,
                    onLogout             = onLogout,
                    onDevicePick         = { showDevicePicker = true }
                )

                if (selectedDevice == null) {
                    NoDeviceWarning(pin = pin)
                } else {
                    SummaryStrip(activeCount = activeRules)
                    InterfaceLockSection(activeControls, onControlChangeActive) { showToast(it) }
                    ContentBlockSection(activeControls, onControlChangeActive) { showToast(it) }
                    AppControlSection(activeControls, onControlChangeActive) { showToast(it) }
                    SpecificAppsSection(activeControls, onControlChangeActive) { showToast(it) }
                    SelfControlFeaturesSection(activeControls, onControlChangeActive) { showToast(it) }
                    NewlyInstalledAppsSection(activeControls)
                    WebsiteBlockSection(activeControls, onControlChangeActive) { showToast(it) }
                    SystemLockSection(activeControls, onControlChangeActive) { showToast(it) }
                    InternetFastingSection(activeControls, onControlChangeActive) { showToast(it) }
                    ScreenTimeLimitSection(activeControls, onControlChangeActive) { showToast(it) }
                    PowerControlSection(
                        controls         = activeControls,
                        onSendPower      = onSendPowerActive,
                        onScheduleLock   = onScheduleLockActive,
                        onCancelSchedule = onCancelScheduleActive,
                        onToast          = { showToast(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Toast — bottom center ────────────────────────
            AnimatedVisibility(
                visible  = toastVisible,
                enter    = fadeIn() + slideInVertically { it / 2 },
                exit     = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape           = RoundedCornerShape(12.dp),
                    color           = SurfaceCard,
                    border          = BorderStroke(1.dp, if (toastType == "ok") TealMain else Rose),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val (icon, tint) = if (toastType == "ok")
                            Pair(Icons.Default.CheckCircle, TealMain)
                        else Pair(Icons.Default.Error, Rose)
                        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                        Text(toastMessage, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                }
            }

            // ── Device picker sheet ──────────────────────────
            if (showDevicePicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showDevicePicker = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false, onClick = {}),
                        shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color  = SurfaceCard,
                        border = BorderStroke(1.dp, BorderMid)
                    ) {
                        Column(modifier = Modifier.padding(20.dp).navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.width(36.dp).height(4.dp)
                                    .background(BorderMid, RoundedCornerShape(2.dp))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Text("Select device", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (devices.isEmpty()) {
                                Text("কোনো device link নেই।", fontSize = 13.sp, color = TextMuted)
                            } else {
                                devices.forEach { (name, isOnline) ->
                                    DeviceChip(
                                        name       = name,
                                        isOnline   = isOnline,
                                        isSelected = name == selectedDevice,
                                        onClick    = { onSelectDevice(name); showDevicePicker = false }
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  HEADER  —  scrolls with content (NOT pinned). Professional card
//  with back button, brand, live status pill, and a tap-through
//  device row that opens the picker.
// ══════════════════════════════════════════════════════════════
@Composable
private fun ParentHeader(
    activeRulesCount: Int,
    selectedDeviceOnline: Boolean?,
    selectedDevice: String?,
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    onDevicePick: () -> Unit = {}
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "alpha"
    )

    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = SurfaceCard,
        border = BorderStroke(1.dp, Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Main toolbar row ───────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Professional back button
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceDeep)
                        .border(BorderStroke(1.dp, BorderMid), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                        tint = TextSub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ras",   color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Focus", color = TealMain,    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Parental Control · PC", fontSize = 11.sp, color = TextMuted,
                        fontWeight = FontWeight.Medium)
                }

                // Active rules badge
                if (activeRulesCount > 0) {
                    Surface(
                        shape  = RoundedCornerShape(50),
                        color  = TealDim,
                        border = BorderStroke(1.dp, TealMain.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape)
                                .background(TealMain.copy(alpha = pulse)))
                            Text("$activeRulesCount active",
                                fontSize = 11.sp, color = TealLight, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Sign out icon button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onLogout),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = "Sign out",
                        tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Divider(color = Border)

            // ── Device sub-row (tappable → opens picker) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TealMain.copy(alpha = 0.04f))
                    .clickable(onClick = onDevicePick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Computer, null, tint = TealLight.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp))
                Text(if (selectedDevice != null) "Controlling:" else "No device selected",
                    fontSize = 12.sp, color = TextMuted)
                if (selectedDevice != null) {
                    Text(selectedDevice, fontSize = 12.sp, color = TealLight,
                        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    if (selectedDeviceOnline != null) {
                        Surface(
                            shape  = RoundedCornerShape(50),
                            color  = if (selectedDeviceOnline) TealMain.copy(alpha = 0.12f)
                                     else BorderMid.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp,
                                if (selectedDeviceOnline) TealMain.copy(alpha = 0.25f) else BorderMid)
                        ) {
                            Text(if (selectedDeviceOnline) "● Online" else "○ Offline",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = if (selectedDeviceOnline) TealLight else TextSub)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.UnfoldMore, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  FOOTER  —  ONE single action bar. PIN on the left, two clearly
//  distinct icon actions on the right (refresh vs. devices), so
//  nothing reads as a duplicated control.
// ══════════════════════════════════════════════════════════════
@Composable
private fun ParentFooterBar(
    pin: String,
    selectedDevice: String?,
    onRefreshPin: () -> Unit,
    onOpenDevices: () -> Unit
) {
    Surface(
        color           = SurfaceCard,
        shadowElevation = 12.dp,
        modifier        = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Border), shape = RectangleShape)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PAIRING PIN", fontSize = 9.sp, color = TealLight,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(3.dp))
                Text(pin, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = TextPrimary)
            }

            // Single, unambiguous refresh action — outlined ghost icon button.
            FooterIconAction(
                icon        = Icons.Default.Refresh,
                contentDesc = "New PIN",
                tint        = TealLight,
                filled      = false,
                onClick     = onRefreshPin
            )

            // Single, unambiguous "manage devices" action — filled teal icon button,
            // visually distinct from the refresh button above so the two never
            // look like the same control repeated twice.
            FooterIconAction(
                icon        = Icons.Outlined.Devices,
                contentDesc = if (selectedDevice != null) "Switch device" else "Link a device",
                tint        = BgDark,
                filled      = true,
                onClick     = onOpenDevices
            )
        }
    }
}

@Composable
private fun FooterIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tint: Color,
    filled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = if (filled) TealMain else Color.Transparent,
        border = BorderStroke(1.dp, if (filled) TealMain else BorderMid),
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  DEVICE CHIP  (used in bottom-sheet device picker)
// ══════════════════════════════════════════════════════════════
@Composable
private fun DeviceChip(
    name: String, isOnline: Boolean, isSelected: Boolean, onClick: () -> Unit
) {
    val bgColor     = if (isSelected) TealMain.copy(alpha = 0.05f) else SurfaceCard
    val borderColor = if (isSelected) TealMain else Border

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .alpha(if (isOnline) 1f else 0.6f)
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💻", fontSize = 18.sp)
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dotColor = if (isOnline) TealMain else Rose
                    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
                        initialValue = 0.5f, targetValue = 1f,
                        animationSpec = if (isOnline)
                            infiniteRepeatable(tween(1000), RepeatMode.Reverse)
                        else
                            infiniteRepeatable(tween(500), RepeatMode.Restart),
                        label = "p"
                    )
                    Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(dotColor.copy(alpha = if (isOnline) pulse else 1f)))
                    Text(
                        if (isOnline) "Online" else "Offline",
                        fontSize   = 11.sp,
                        color      = if (isOnline) TealLight else TextSub,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  NO DEVICE WARNING
// ══════════════════════════════════════════════════════════════
@Composable
private fun NoDeviceWarning(pin: String) {
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = AmberDim,
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier              = Modifier.padding(32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text("📱", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("কোনো device link নেই", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = Amber)
            Spacer(Modifier.height(8.dp))
            Text("Windows app খুলে Family Link tab-এ এই PIN দাও:",
                fontSize = 13.sp, color = TextSub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = SurfaceCard,
                border = BorderStroke(1.dp, BorderMid)
            ) {
                Text(pin,
                    modifier   = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    fontSize   = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                    color      = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            Text("Device link হলে এখান থেকে control করতে পারবে।",
                fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  SUMMARY STRIP
// ══════════════════════════════════════════════════════════════
@Composable
private fun SummaryStrip(activeCount: Int) {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceCard,
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$activeCount", fontSize = 44.sp,
                    fontWeight = FontWeight.Bold, color = TealLight, lineHeight = 48.sp)
                Text("Active Controls", fontSize = 11.sp,
                    color = TextMuted, fontWeight = FontWeight.Medium)
            }
            Divider(modifier = Modifier.height(48.dp).width(1.dp), color = Border)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(TealMain))
                    Text("Real-time Synchronization", fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text("Firebase-এর মাধ্যমে ৫ সেকেন্ডের মধ্যে child device-এ apply হয়।",
                    fontSize = 12.sp, color = TextSub, lineHeight = 18.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  REUSABLE: SECTION HEADER & LABEL
// ══════════════════════════════════════════════════════════════
@Composable
private fun SectionHeader(emoji: String, title: String, subtitle: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = accentColor.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
        ) {
            Text(emoji, modifier = Modifier.padding(10.dp), fontSize = 20.sp)
        }
        Column {
            Text(title,    fontSize = 16.sp, fontWeight = FontWeight.Bold,  color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSub)
        }
    }
}


// ── Toggle Row ───────────────────────────────────────────────
@Composable
private fun ToggleRow(
    title: String, subtitle: String,
    checked: Boolean, onChecked: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChecked(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title,    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp)
            }
            Switch(
                checked         = checked,
                onCheckedChange = onChecked,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = TealMain,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BorderMid
                )
            )
        }
        if (!isLast) Divider(color = Border.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp))
    }
}

// ══════════════════════════════════════════════════════════════
//  1. INTERFACE LOCK
// ══════════════════════════════════════════════════════════════
@Composable
private fun InterfaceLockSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🔒", "Interface Lock", "Child-কে RasFocus settings দেখতে দেবে না", Rose)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            ToggleRow("Lock All Tabs",
                "Child কোনো RasFocus settings দেখতে বা পরিবর্তন করতে পারবে না",
                c.lockAllTabs,
                onChecked = { onChange(c.copy(lockAllTabs = it)); onToast("Setting updated") },
                isLast = true)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  2. CONTENT BLOCK
// ══════════════════════════════════════════════════════════════
@Composable
private fun ContentBlockSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🚫", "Content Block", "Adult content, Reels ও Shorts জোর করে block", Rose)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                ToggleRow("Force Adult Block",
                    "Child-এর adult content setting override করে — সবসময় ON",
                    c.forceAdultBlock,
                    onChecked = { onChange(c.copy(forceAdultBlock = it)); onToast("Setting updated") })
                ToggleRow("Block Facebook Reels",
                    "Facebook/Reels matching window বন্ধ করে দেয়",
                    c.forceReelsBlock,
                    onChecked = { onChange(c.copy(forceReelsBlock = it)); onToast("Setting updated") })
                ToggleRow("Block YouTube Shorts",
                    "YouTube Shorts matching window বন্ধ করে দেয়",
                    c.forceShortsBlock,
                    onChecked = { onChange(c.copy(forceShortsBlock = it)); onToast("Setting updated") },
                    isLast = true)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  3. APP CONTROL
// ══════════════════════════════════════════════════════════════
@Composable
private fun AppControlSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    var allowedText by remember(c.allowedAppsCsv) { mutableStateOf(c.allowedAppsCsv) }
    var blockedText by remember(c.blockedAppsCsv) { mutableStateOf(c.blockedAppsCsv) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("📱", "App Control", "নির্দিষ্ট app allow বা block করো", TealMain)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                ToggleRow("Enable App Control",
                    "Child device-এ app allowlist/blocklist enforce করবে",
                    c.appControlEnabled,
                    onChecked = { onChange(c.copy(appControlEnabled = it)); onToast("Setting updated") })

                if (c.appControlEnabled) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Text("Operating Mode", fontSize = 11.sp, color = TextMuted,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppModeButton("Allow Mode", Icons.Default.Check,
                                isSelected = c.appMode == "ALLOW", selectedColor = TealMain,
                                modifier = Modifier.weight(1f)
                            ) { onChange(c.copy(appMode = "ALLOW")); onToast("Operating mode changed") }
                            AppModeButton("Block Mode", Icons.Default.Block,
                                isSelected = c.appMode == "BLOCK", selectedColor = Rose,
                                modifier = Modifier.weight(1f)
                            ) { onChange(c.copy(appMode = "BLOCK")); onToast("Operating mode changed") }
                        }

                        Surface(shape = RoundedCornerShape(10.dp), color = BgDark,
                            border = BorderStroke(1.dp, Border)) {
                            Column(modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Allow Mode — listed apps ছাড়া সব বন্ধ।",
                                    fontSize = 11.sp, color = TealLight)
                                Text("Block Mode — listed apps বন্ধ থাকবে।",
                                    fontSize = 11.sp, color = Rose.copy(alpha = 0.8f))
                            }
                        }

                        // ── Show ONLY allowed list in ALLOW mode (HTML: csv_section_allowed) ──
                        if (c.appMode == "ALLOW") {
                            CsvInputField(
                                label         = "Allowed Apps",
                                labelColor    = TealLight,
                                value         = allowedText,
                                placeholder   = "chrome.exe, vlc.exe, notepad.exe",
                                hint          = "Comma দিয়ে .exe নাম লেখো। Browser সবসময় allowed।",
                                onValueChange = { allowedText = it },
                                onSave        = {
                                    onChange(c.copy(allowedAppsCsv = allowedText))
                                    onToast("List saved successfully")
                                }
                            )
                        }

                        // ── Show ONLY blocked list in BLOCK mode (HTML: csv_section_blocked) ──
                        if (c.appMode == "BLOCK") {
                            CsvInputField(
                                label         = "Blocked Apps",
                                labelColor    = Rose,
                                value         = blockedText,
                                placeholder   = "discord.exe, steam.exe, roblox.exe",
                                hint          = "Comma দিয়ে .exe নাম লেখো।",
                                onValueChange = { blockedText = it },
                                onSave        = {
                                    onChange(c.copy(blockedAppsCsv = blockedText))
                                    onToast("List saved successfully")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppModeButton(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean, selectedColor: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val bg        = if (isSelected) selectedColor.copy(alpha = 0.15f) else SurfaceDeep
    val border    = if (isSelected) selectedColor else BorderMid
    val textColor = if (isSelected) selectedColor else TextSub

    Surface(
        shape    = RoundedCornerShape(12.dp), color = bg,
        border   = BorderStroke(1.dp, border),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  4. WEBSITE BLOCK
// ══════════════════════════════════════════════════════════════
@Composable
private fun WebsiteBlockSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    var webText by remember(c.blockedWebsCsv) { mutableStateOf(c.blockedWebsCsv) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🌐", "Website Block", "Domain দিয়ে browser tab বন্ধ করো", Amber)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                ToggleRow("Enable Website Block",
                    "Browser-এর foreground window title দেখে matching tab বন্ধ করে",
                    c.webBlockEnabled,
                    onChecked = { onChange(c.copy(webBlockEnabled = it)); onToast("Setting updated") })
                if (c.webBlockEnabled) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CsvInputField(
                            label         = "Blocked Domains",
                            labelColor    = TextSub,
                            value         = webText,
                            placeholder   = "youtube.com, facebook.com, tiktok.com",
                            hint          = "Comma দিয়ে domain লেখো।",
                            onValueChange = { webText = it },
                            onSave        = {
                                onChange(c.copy(blockedWebsCsv = webText))
                                onToast("List saved successfully")
                            }
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  5. SYSTEM LOCK
// ══════════════════════════════════════════════════════════════
@Composable
private fun SystemLockSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    var foldersText by remember(c.blockedFoldersCsv) { mutableStateOf(c.blockedFoldersCsv) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("🛡️", "System Lock", "Windows system tools ও folders block করো", Purple)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column {
                ToggleRow("Block Task Manager",
                    "taskmgr.exe খুললেই সাথে সাথে বন্ধ করে দেয়",
                    c.blockTaskManager,
                    onChecked = { onChange(c.copy(blockTaskManager = it)); onToast("Setting updated") })
                ToggleRow("Block Windows Settings",
                    "SystemSettings.exe বন্ধ করে, Registry দিয়েও block করে",
                    c.blockSettings,
                    onChecked = { onChange(c.copy(blockSettings = it)); onToast("Setting updated") })
                ToggleRow("Block File Explorer",
                    "সব Explorer folder window বন্ধ করে (desktop থাকে)",
                    c.blockFileManager,
                    onChecked = { onChange(c.copy(blockFileManager = it)); onToast("Setting updated") })
                Column(modifier = Modifier.padding(16.dp)) {
                    CsvInputField(
                        label         = "Block Specific Folders",
                        labelColor    = TextSub,
                        value         = foldersText,
                        placeholder   = "Downloads, Documents, Games",
                        hint          = "Comma দিয়ে folder নাম লেখো। Window title দিয়ে match করে।",
                        onValueChange = { foldersText = it },
                        onSave        = {
                            onChange(c.copy(blockedFoldersCsv = foldersText))
                            onToast("List saved successfully")
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  6. INTERNET FASTING
// ══════════════════════════════════════════════════════════════
@Composable
private fun InternetFastingSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("📡", "Internet Fasting", "সম্পূর্ণ internet বন্ধ করে দাও", Cyan)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            ToggleRow("Kill Network Adapters",
                "ipconfig /release চালিয়ে সব network বন্ধ। Restore করলে আবার চালু।",
                c.internetFasting,
                onChecked = { onChange(c.copy(internetFasting = it)); onToast("Setting updated") },
                isLast = true)
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  7. SCREEN TIME LIMIT
// ══════════════════════════════════════════════════════════════
@Composable
private fun ScreenTimeLimitSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    var hours   by remember { mutableStateOf((c.timeLimitMinutes / 60).toString()) }
    var minutes by remember { mutableStateOf((c.timeLimitMinutes % 60).toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("⏱️", "Screen Time Limit", "নির্দিষ্ট সময় পর PC lock হবে", Orange)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("সময় শেষ হলে PC lock হবে। 0h 0m দিলে disable হয়।",
                    fontSize = 13.sp, color = TextSub, lineHeight = 18.sp)
                Row(verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeInputField("Hours", hours)   { hours = it }
                    Text(":", fontSize = 24.sp, color = TextMuted,
                        modifier = Modifier.padding(bottom = 8.dp))
                    TimeInputField("Minutes", minutes) { minutes = it }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val h = hours.toIntOrNull() ?: 0
                            val m = minutes.toIntOrNull() ?: 0
                            onChange(c.copy(timeLimitMinutes = h * 60 + m))
                            onToast(if (h == 0 && m == 0) "Screen time disabled" else "Time limit set: ${h}h ${m}m")
                        },
                        shape  = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealMain)
                    ) {
                        Text("Set Limit", fontWeight = FontWeight.Bold, color = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.background)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 9.sp, color = TextMuted,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.width(70.dp),
            textStyle     = androidx.compose.ui.text.TextStyle(
                fontSize   = 20.sp, fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center, color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor     = TealMain,
                unfocusedBorderColor   = BorderMid,
                unfocusedContainerColor = BgDark,
                focusedContainerColor   = BgDark
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════
//  8. POWER CONTROL
// ══════════════════════════════════════════════════════════════
@Composable
private fun PowerControlSection(
    controls: ParentControls,
    onSendPower: (Int) -> Unit,
    onScheduleLock: (Long, String) -> Unit,
    onCancelSchedule: () -> Unit,
    onToast: (String) -> Unit
) {
    var tlH    by remember { mutableStateOf("0") }
    var tlM    by remember { mutableStateOf("0") }
    var tlS    by remember { mutableStateOf("0") }
    var tlType by remember { mutableStateOf("lock") }

    val hasSchedule = controls.lockUntilEpoch > System.currentTimeMillis()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("⚡", "Power Control", "Instant action বা সময় দিয়ে lock করো", Rose)
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceCard, border = BorderStroke(1.dp, Border)) {
            Column(modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // ── Instant Actions ──────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DividerLabel("Instant Action")
                    Text("সাথে সাথে child device-এ execute হয়। ৩ সেকেন্ড পর auto-reset।",
                        fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InstantPowerBtn("🔒", "Lock PC",   TealMain, Modifier.weight(1f)) {
                            onSendPower(1); onToast("Lock Command Sent")
                        }
                        InstantPowerBtn("😴", "Sleep",     Amber,    Modifier.weight(1f)) {
                            onSendPower(2); onToast("Sleep Command Sent")
                        }
                        InstantPowerBtn("⛔", "Shutdown",  Rose,     Modifier.weight(1f)) {
                            onSendPower(3); onToast("Shutdown Command Sent")
                        }
                    }
                }

                // ── Timed Scheduled ──────────────────────────
                Surface(shape = RoundedCornerShape(14.dp), color = BgDark,
                    border = BorderStroke(1.dp, Border)) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏳ Timed Scheduled Actions", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.weight(1f))

                            // ── ACTIVE badge (HTML: #tlActiveBadge) ──────
                            if (hasSchedule) {
                                Surface(
                                    shape  = RoundedCornerShape(6.dp),
                                    color  = RoseDim,
                                    border = BorderStroke(1.dp, Rose.copy(alpha = 0.3f))
                                ) {
                                    val badgePulse by rememberInfiniteTransition(label = "badge")
                                        .animateFloat(
                                            initialValue = 0.6f, targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                tween(800), RepeatMode.Reverse),
                                            label = "bp"
                                        )
                                    Text("ACTIVE",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 9.sp,
                                        color    = Rose.copy(alpha = badgePulse),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp)
                                }
                            }
                        }

                        // Active banner
                        if (hasSchedule) {
                            Surface(shape = RoundedCornerShape(12.dp), color = RoseDim,
                                border = BorderStroke(1.dp, Rose.copy(alpha = 0.2f))) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Surface(shape = CircleShape, color = Rose.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Rose.copy(alpha = 0.3f))) {
                                        Text(
                                            when (controls.lockType) {
                                                "sleep" -> "😴"; "shutdown" -> "⛔"; else -> "🔒"
                                            },
                                            modifier = Modifier.padding(14.dp), fontSize = 22.sp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            when (controls.lockType) {
                                                "sleep"    -> "SLEEP SCHEDULED"
                                                "shutdown" -> "SHUTDOWN SCHEDULED"
                                                else       -> "PC LOCK SCHEDULED"
                                            },
                                            fontSize = 11.sp, color = Rose,
                                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        TimerCountdown(controls.lockUntilEpoch)
                                    }
                                    OutlinedButton(
                                        onClick = { onCancelSchedule(); onToast("Action cancelled & Unlocked!") },
                                        shape   = RoundedCornerShape(10.dp),
                                        border  = BorderStroke(1.dp, BorderMid),
                                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSub)
                                    ) {
                                        Text("🔓 Cancel", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Select Action Type:", fontSize = 11.sp, color = TextSub)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TlTypeBtn("🔒", "Lock",     "lock",     tlType, TealMain, Modifier.weight(1f)) { tlType = it }
                                    TlTypeBtn("😴", "Sleep",    "sleep",    tlType, Amber,    Modifier.weight(1f)) { tlType = it }
                                    TlTypeBtn("⛔", "Shutdown", "shutdown", tlType, Rose,     Modifier.weight(1f)) { tlType = it }
                                }

                                Text("Set countdown:", fontSize = 11.sp, color = TextSub)
                                Row(verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TimeInputField("Hr",  tlH) { tlH = it }
                                    TimeInputField("Min", tlM) { tlM = it }
                                    TimeInputField("Sec", tlS) { tlS = it }
                                }

                                val accentColor = when (tlType) {
                                    "sleep" -> Amber; "shutdown" -> Rose; else -> TealMain
                                }
                                Button(
                                    onClick = {
                                        val h  = tlH.toIntOrNull() ?: 0
                                        val m  = tlM.toIntOrNull() ?: 0
                                        val s  = tlS.toIntOrNull() ?: 0
                                        val ms = (h * 3600L + m * 60L + s) * 1000L
                                        if (ms > 0) {
                                            onScheduleLock(System.currentTimeMillis() + ms, tlType)
                                            val label = when (tlType) { "sleep" -> "Sleep"; "shutdown" -> "Shutdown"; else -> "Lock" }
                                            onToast("$label scheduled in: ${h}h ${m}m ${s}s")
                                        } else {
                                            onToast("সময় দিন — কমপক্ষে ১ সেকেন্ড", )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text(
                                        when (tlType) {
                                            "sleep"    -> "😴 Schedule Sleep"
                                            "shutdown" -> "⛔ Schedule Shutdown"
                                            else       -> "🔒 Schedule Lock"
                                        },
                                        fontWeight = FontWeight.Bold, color = BgDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstantPowerBtn(
    emoji: String, label: String, hoverColor: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = if (pressed) hoverColor.copy(alpha = 0.12f) else SurfaceDeep,
        border = BorderStroke(1.dp, if (pressed) hoverColor.copy(alpha = 0.5f) else BorderMid),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { pressed = true; onClick() }
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 26.sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (pressed) hoverColor else TextSub)
        }
    }
}

@Composable
private fun TlTypeBtn(
    emoji: String, label: String, type: String,
    selected: String, color: Color, modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val isSelected = type == selected
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = if (isSelected) color.copy(alpha = 0.1f) else SurfaceDeep,
        border = BorderStroke(1.dp, if (isSelected) color else BorderMid),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(type) }
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 20.sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (isSelected) color else TextSub)
        }
    }
}

@Composable
private fun TimerCountdown(epochMs: Long) {
    var remaining by remember { mutableLongStateOf(epochMs - System.currentTimeMillis()) }

    LaunchedEffect(epochMs) {
        while (remaining > 0) {
            delay(1000)
            remaining = epochMs - System.currentTimeMillis()
        }
    }

    val totalSeconds = (remaining / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60

    Text(
        "%02d:%02d:%02d".format(h, m, s),
        fontSize   = 28.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color      = TextPrimary
    )
}

// ══════════════════════════════════════════════════════════════
//  SHARED: CSV INPUT
// ══════════════════════════════════════════════════════════════
@Composable
private fun CsvInputField(
    label: String, labelColor: Color, value: String, placeholder: String,
    hint: String, onValueChange: (String) -> Unit, onSave: () -> Unit
) {
    var saved  by remember { mutableStateOf(false) }
    val scope  = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), fontSize = 10.sp, color = labelColor,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            placeholder   = { Text(placeholder, fontSize = 12.sp, color = TextMuted,
                fontFamily = FontFamily.Monospace) },
            textStyle     = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary),
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = TealMain,
                unfocusedBorderColor    = BorderMid,
                unfocusedContainerColor = BgDark,
                focusedContainerColor   = BgDark
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hint, fontSize = 11.sp, color = TextMuted,
                modifier = Modifier.weight(1f), lineHeight = 15.sp)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = if (saved) TealDim else TealDim.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, TealMain.copy(alpha = if (saved) 0.6f else 0.3f)),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    onSave()
                    saved = true
                    scope.launch { delay(1500); saved = false }
                }
            ) {
                Text(
                    if (saved) "✓ Saved!" else "Save List",
                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (saved) TealLight else TealLight.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  SHARED: DIVIDER LABEL
// ══════════════════════════════════════════════════════════════
@Composable
private fun DividerLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider(Modifier.weight(1f), color = Border)
        Text(text.uppercase(), fontSize = 9.sp, color = TextMuted,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Divider(Modifier.weight(1f), color = Border)
    }
}

// ============================================================================
// NEW SECTIONS: Specific Apps, Newly Installed Apps, Self Control Features
// ============================================================================

@Composable
private fun SpecificAppsSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                emoji = "🎯",
                title = "Specific App Control",
                subtitle = "Block popular apps with schedules",
                accentColor = TealMain
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            AppScheduleRow(
                appName = "Facebook",
                enabled = c.fbEnabled,
                onEnabledChange = { onChange(c.copy(fbEnabled = it)) },
                startTime = c.fbStartTime,
                onStartTimeChange = { onChange(c.copy(fbStartTime = it)) },
                endTime = c.fbEndTime,
                onEndTimeChange = { onChange(c.copy(fbEndTime = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            AppScheduleRow(
                appName = "Facebook Lite",
                enabled = c.fbLiteEnabled,
                onEnabledChange = { onChange(c.copy(fbLiteEnabled = it)) },
                startTime = c.fbLiteStartTime,
                onStartTimeChange = { onChange(c.copy(fbLiteStartTime = it)) },
                endTime = c.fbLiteEndTime,
                onEndTimeChange = { onChange(c.copy(fbLiteEndTime = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            AppScheduleRow(
                appName = "YouTube",
                enabled = c.ytEnabled,
                onEnabledChange = { onChange(c.copy(ytEnabled = it)) },
                startTime = c.ytStartTime,
                onStartTimeChange = { onChange(c.copy(ytStartTime = it)) },
                endTime = c.ytEndTime,
                onEndTimeChange = { onChange(c.copy(ytEndTime = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            AppScheduleRow(
                appName = "Chrome",
                enabled = c.chromeEnabled,
                onEnabledChange = { onChange(c.copy(chromeEnabled = it)) },
                startTime = c.chromeStartTime,
                onStartTimeChange = { onChange(c.copy(chromeStartTime = it)) },
                endTime = c.chromeEndTime,
                onEndTimeChange = { onChange(c.copy(chromeEndTime = it)) }
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
            modifier = Modifier.fillMaxWidth(),
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
                    TimeInputField(label = "Start Time (HH:MM)", value = startTime, onValueChange = onStartTimeChange)
                }
                Box(modifier = Modifier.weight(1f)) {
                    TimeInputField(label = "End Time (HH:MM)", value = endTime, onValueChange = onEndTimeChange)
                }
            }
        }
    }
}

@Composable
private fun NewlyInstalledAppsSection(
    c: ParentControls
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                emoji = "🆕",
                title = "Newly Installed Apps",
                subtitle = "Apps recently installed by child",
                accentColor = TealMain
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (c.newInstalledAppsCsv.isEmpty()) {
                Text("No new apps installed recently.", fontSize = 14.sp, color = TextMuted)
            } else {
                val apps = c.newInstalledAppsCsv.split(",").filter { it.isNotBlank() }
                apps.forEach { app ->
                    Text("• $app", fontSize = 14.sp, color = TextSub, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SelfControlFeaturesSection(
    c: ParentControls, onChange: (ParentControls) -> Unit, onToast: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                emoji = "🛡️",
                title = "Self Control Features",
                subtitle = "Remote control of advanced blocking modes",
                accentColor = TealMain
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            ToggleRow(
                title = "Deep Study Mode", subtitle = "Blocks all non-essential apps",
                checked = c.deepStudyEnabled, onChecked = { onChange(c.copy(deepStudyEnabled = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            ToggleRow(
                title = "Button Phone Mode", subtitle = "Only calls and SMS allowed",
                checked = c.buttonPhoneEnabled, onChecked = { onChange(c.copy(buttonPhoneEnabled = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            ToggleRow(
                title = "Extreme Block", subtitle = "Maximum restriction mode",
                checked = c.extremeBlockEnabled, onChecked = { onChange(c.copy(extremeBlockEnabled = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            ToggleRow(
                title = "Single Apps Block", subtitle = "Enforce specific app blocks",
                checked = c.singleAppsBlockEnabled, onChecked = { onChange(c.copy(singleAppsBlockEnabled = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            ToggleRow(
                title = "Single Website Block", subtitle = "Enforce specific website blocks",
                checked = c.singleWebsiteBlockEnabled, onChecked = { onChange(c.copy(singleWebsiteBlockEnabled = it)) }
            )
            Divider(color = BorderMid, modifier = Modifier.padding(vertical = 12.dp))
            
            ToggleRow(
                title = "Family Browser", subtitle = "Enable/Disable Family Browser",
                checked = c.familyBrowserEnabled, onChecked = { onChange(c.copy(familyBrowserEnabled = it)) }
            )
        }
    }
}

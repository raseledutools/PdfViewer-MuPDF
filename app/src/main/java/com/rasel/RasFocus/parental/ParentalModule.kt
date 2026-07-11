package com.rasel.RasFocus.parental

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.rasel.RasFocus.*

// ─────────────────────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────────────────────
private val PBg        = Color(0xFF0F172A)
private val PSurface   = Color(0xFF1E293B)
private val PSurface2  = Color(0xFF273448)
private val PBorder    = Color(0xFF334155)
private val PTeal      = Color(0xFF0096B4)
private val PTealLight = Color(0xFF00BCD4)
private val PTealDim   = Color(0x1400BCD4)
private val PText      = Color(0xFFF8FAFC)
private val PSub       = Color(0xFF94A3B8)
private val PMuted     = Color(0xFF64748B)
private val PGreen     = Color(0xFF22C55E)
private val PRed       = Color(0xFFEF4444)
private val PPurple    = Color(0xFFA855F7)
private val PPurpleDim = Color(0x14A855F7)
private val PAmber     = Color(0xFFF59E0B)

// ─────────────────────────────────────────────────────────────
// NAV STATE
// ─────────────────────────────────────────────────────────────
private sealed class ParentalNav {
    object PICKER                          : ParentalNav()
    data class PC(val deviceId: String)    : ParentalNav()
    data class MOBILE(val deviceId: String): ParentalNav()
}

// ─────────────────────────────────────────────────────────────
// ROOT
// ─────────────────────────────────────────────────────────────
@Composable
fun ParentalRootScreen(
    viewModel: MainViewModel,
    isComboMode: Boolean = false,
    hideOwnFooter: Boolean = false
) {
    var nav by remember { mutableStateOf<ParentalNav>(ParentalNav.PICKER) }

    when (val state = nav) {
        ParentalNav.PICKER -> {
            ParentalPickerScreen(
                viewModel    = viewModel,
                onPickPc     = { id -> nav = ParentalNav.PC(id) },
                onPickMobile = { id -> nav = ParentalNav.MOBILE(id) }
            )
        }
        is ParentalNav.PC -> {
            val pcPin by viewModel.connectionPin.collectAsState()
            ParentControlScreen(
                onBack         = { nav = ParentalNav.PICKER },
                deviceId       = state.deviceId,
                viewModel      = viewModel,
                pin            = pcPin,
                devices        = if (state.deviceId.isNotEmpty()) listOf(state.deviceId to true) else emptyList(),
                selectedDevice = state.deviceId.ifEmpty { null },
                onRefreshPin   = { viewModel.refreshPin() },
            )
        }
        is ParentalNav.MOBILE -> {
            ChildPhoneControlScreen(
                viewModel = viewModel,
                deviceId  = state.deviceId,
                onBack    = { nav = ParentalNav.PICKER }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PICKER — full page, dynamic header, scroll-aware
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentalPickerScreen(
    viewModel: MainViewModel,
    onPickPc: (String) -> Unit,
    onPickMobile: (String) -> Unit
) {
    val devices       by viewModel.devices.collectAsState()
    val pcDevices     = devices.filter { it.type == DeviceType.PC }
    val mobileDevices = devices.filter { it.type == DeviceType.MOBILE }
    val totalDevices  = devices.size
    val onlineCount   = devices.count { it.isOnline }

    val scrollState   = rememberScrollState()

    // Header collapses when scrolled down
    val isScrolled    = scrollState.value > 60

    // Pulse for online dot
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "dot"
    )

    // Confirm-remove dialog state
    var confirmRemoveDevice by remember { mutableStateOf<Device?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(PBg)) {

        // Subtle grid
        Canvas(Modifier.fillMaxSize()) {
            val step = 44.dp.toPx(); val lc = Color(0x07FFFFFF)
            var x = 0f; while (x < size.width)  { drawLine(lc, Offset(x,0f), Offset(x,size.height), 1f); x += step }
            var y = 0f; while (y < size.height) { drawLine(lc, Offset(0f,y), Offset(size.width,y), 1f); y += step }
        }
        // Teal ambient glow
        Box(Modifier.size(300.dp).offset((-60).dp, (-60).dp)
            .background(Brush.radialGradient(listOf(PTeal.copy(.08f), Color.Transparent)))
            .blur(80.dp))

        Column(modifier = Modifier.fillMaxSize()) {

            // ══ DYNAMIC HEADER ════════════════════════════════
            AnimatedContent(
                targetState = isScrolled,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "header"
            ) { collapsed ->
                Surface(
                    color           = if (collapsed) PSurface.copy(.97f) else Color.Transparent,
                    shadowElevation = if (collapsed) 8.dp else 0.dp,
                    modifier        = Modifier.fillMaxWidth()
                        .then(if (collapsed) Modifier.border(BorderStroke(1.dp, PBorder), RectangleShape) else Modifier)
                ) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier          = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = if (collapsed) 10.dp else 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon
                            Box(
                                modifier = Modifier.size(if (collapsed) 34.dp else 44.dp)
                                    .background(PTealDim, RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, PTeal.copy(.25f)), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.FamilyRestroom, null,
                                    tint = PTealLight,
                                    modifier = Modifier.size(if (collapsed) 18.dp else 24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ras",     color = PText,     fontSize = if (collapsed) 15.sp else 19.sp, fontWeight = FontWeight.Bold)
                                    Text("Focus",   color = PTeal,     fontSize = if (collapsed) 15.sp else 19.sp, fontWeight = FontWeight.Bold)
                                    Text(" Parental", color = PSub,    fontSize = if (collapsed) 12.sp else 14.sp)
                                }
                                if (!collapsed)
                                    Text("Manage your family devices", fontSize = 12.sp, color = PMuted)
                            }
                            // Device count chip — dynamic
                            if (totalDevices > 0) {
                                Surface(
                                    shape  = RoundedCornerShape(50),
                                    color  = PTealDim,
                                    border = BorderStroke(1.dp, PTeal.copy(.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(Modifier.size(6.dp).background(PTeal.copy(pulse), CircleShape))
                                        Text("$totalDevices device${if(totalDevices!=1)"s" else ""}",
                                            fontSize = 11.sp, color = PTealLight, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Sub-row (online summary) — visible when NOT collapsed, AND devices exist
                        if (!collapsed && totalDevices > 0) {
                            HorizontalDivider(color = PBorder.copy(.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(PTeal.copy(.04f))
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MiniChip(Icons.Filled.Computer,    "${pcDevices.size} PC${if(pcDevices.size!=1)"s" else ""}",       PTealLight)
                                MiniChip(Icons.Filled.Smartphone,  "${mobileDevices.size} Phone${if(mobileDevices.size!=1)"s" else ""}", PPurple)
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.size(7.dp).background(if(onlineCount>0) PGreen.copy(pulse) else PMuted.copy(.4f), CircleShape))
                                Text(
                                    if (onlineCount > 0) "$onlineCount online" else "All offline",
                                    fontSize = 11.sp,
                                    color    = if (onlineCount > 0) PGreen else PMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ══ SCROLLABLE CONTENT ════════════════════════════
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp)
                    .padding(top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Empty state
                if (totalDevices == 0) {
                    Spacer(Modifier.height(40.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(88.dp)
                                .background(PTealDim, RoundedCornerShape(28.dp))
                                .border(BorderStroke(1.dp, PTeal.copy(.2f)), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.DevicesOther, null, tint = PTealLight, modifier = Modifier.size(44.dp)) }
                        Text("No devices linked", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PText)
                        Text("Use the buttons below to pair a PC or child phone",
                            fontSize = 13.sp, color = PMuted, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }

                // PC GROUP
                if (pcDevices.isNotEmpty()) {
                    DeviceGroupHeader("PCs", Icons.Filled.Computer, PTealLight, pcDevices.size)
                    pcDevices.forEach { device ->
                        DeviceCard(
                            device   = device,
                            accent   = PTeal,
                            icon     = Icons.Filled.Computer,
                            pulse    = pulse,
                            onClick  = { onPickPc(device.id) },
                            onRemove = { confirmRemoveDevice = device }
                        )
                    }
                }

                // MOBILE GROUP
                if (mobileDevices.isNotEmpty()) {
                    if (pcDevices.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    DeviceGroupHeader("Child Phones", Icons.Filled.Smartphone, PPurple, mobileDevices.size)
                    mobileDevices.forEach { device ->
                        DeviceCard(
                            device   = device,
                            accent   = PPurple,
                            icon     = Icons.Filled.Smartphone,
                            pulse    = pulse,
                            onClick  = { onPickMobile(device.id) },
                            onRemove = { confirmRemoveDevice = device }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // ══ FOOTER — always visible ════════════════════════
            Surface(
                color           = PSurface,
                shadowElevation = 20.dp,
                modifier        = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, PBorder), RectangleShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick  = { onPickPc("") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PTeal, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pair PC", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick  = { onPickMobile("") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.5.dp, PPurple.copy(.7f)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PPurple)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pair Phone", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // ══ REMOVE CONFIRM DIALOG ═════════════════════════════
        confirmRemoveDevice?.let { dev ->
            AlertDialog(
                onDismissRequest = { confirmRemoveDevice = null },
                containerColor   = PSurface,
                shape            = RoundedCornerShape(20.dp),
                icon = {
                    Box(
                        modifier = Modifier.size(48.dp).background(PRed.copy(.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.DeleteForever, null, tint = PRed, modifier = Modifier.size(26.dp)) }
                },
                title = {
                    Text("Remove Device?", color = PText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                },
                text = {
                    Text(
                        "\"${dev.name.ifBlank { dev.id.take(8) }}\" will be unlinked from your account. You can re-pair it later.",
                        color = PSub, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.removeDevice(dev.id); confirmRemoveDevice = null },
                        colors  = ButtonDefaults.buttonColors(containerColor = PRed),
                        shape   = RoundedCornerShape(10.dp)
                    ) { Text("Remove", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { confirmRemoveDevice = null },
                        border  = BorderStroke(1.dp, PBorder),
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = PSub)
                    ) { Text("Cancel") }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DEVICE CARD — shows connection status, online status, active blocks
// ─────────────────────────────────────────────────────────────
@Composable
private fun DeviceCard(
    device: Device,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    pulse: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    // Summarise what's blocked on this device
    val activeBlocks = buildList {
        if (device.isLocked)          add("🔒 Locked")
        if (device.isHalalGuardOn)    add("🛡 Halal Guard")
        if (device.blockYoutubeShorts)add("📵 Shorts")
        if (device.blockReels)        add("📵 Reels")
        if (device.blockIncognito)    add("🕵 Incognito")
        if (device.blockedAppsList.isNotEmpty()) add("${device.blockedAppsList.size} Apps blocked")
    }

    Surface(
        modifier        = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape           = RoundedCornerShape(18.dp),
        color           = PSurface,
        border          = BorderStroke(1.dp, if (device.isOnline) accent.copy(.28f) else PBorder),
        shadowElevation = if (device.isOnline) 4.dp else 1.dp
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Main row ────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon box
                Box(
                    modifier = Modifier.size(46.dp)
                        .background(accent.copy(.12f), RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, accent.copy(.2f)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)) }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        device.name.ifBlank { device.id.take(10) },
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // Online / offline status
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(
                            if (device.isOnline) PGreen.copy(pulse) else PMuted.copy(.5f), CircleShape))
                        Text(
                            if (device.isOnline) "Online · Connected" else "Offline",
                            fontSize = 12.sp,
                            color    = if (device.isOnline) PGreen else PMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Battery (if available)
                    if (device.batteryLevel in 1..100) {
                        val batColor = when {
                            device.batteryLevel <= 20 -> PRed
                            device.batteryLevel <= 50 -> PAmber
                            else -> PGreen
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.BatteryStd, null, tint = batColor.copy(.7f), modifier = Modifier.size(12.dp))
                            Text("${device.batteryLevel}%", fontSize = 11.sp, color = batColor.copy(.8f))
                        }
                    }
                }

                // Remove button
                Box(
                    modifier = Modifier.size(34.dp)
                        .background(PRed.copy(.08f), RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, PRed.copy(.2f)), RoundedCornerShape(10.dp))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, null, tint = PRed.copy(.8f), modifier = Modifier.size(16.dp)) }

                // Chevron
                Icon(Icons.Filled.ChevronRight, null, tint = PMuted, modifier = Modifier.size(20.dp))
            }

            // ── Active blocks row (only if any) ─────────────
            if (activeBlocks.isNotEmpty()) {
                HorizontalDivider(color = PBorder.copy(.5f))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(accent.copy(.03f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Shield, null, tint = accent.copy(.6f), modifier = Modifier.size(13.dp))
                    Text(
                        activeBlocks.take(4).joinToString("  ·  "),
                        fontSize = 11.sp, color = PSub,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────
@Composable
private fun DeviceGroupHeader(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = tint.copy(.7f), modifier = Modifier.size(14.dp))
        Text(label.uppercase(), color = tint.copy(.8f), fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, letterSpacing = 1.2.sp)
        Box(
            modifier = Modifier.background(tint.copy(.15f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp)
        ) { Text("$count", fontSize = 10.sp, color = tint, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun MiniChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = tint.copy(.7f), modifier = Modifier.size(12.dp))
        Text(label, fontSize = 11.sp, color = tint.copy(.85f), fontWeight = FontWeight.SemiBold)
    }
}

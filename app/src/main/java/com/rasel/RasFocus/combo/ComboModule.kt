package com.rasel.RasFocus.combo

// ============================================================
//  RasFocus+ — Pro Combo Module
//  Design: Fixed RasFocus brand Premium Teal header + white
//  NavigationBar footer — matches SelfControlModule.kt exactly.
// ============================================================

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rasel.RasFocus.MainViewModel
import com.rasel.RasFocus.RasFocusColors

enum class ComboMode { SELF, PARENTAL }

// ─────────────────────────────────────────────────────────────
// RASFOCUS BRAND PALETTE — mirrors SelfControlModule.kt exactly,
// used for the shared header/footer components below so every
// module renders the identical Premium Teal header + white
// NavigationBar footer, independent of the file it lives in.
// ─────────────────────────────────────────────────────────────
private val PrimaryBlue       = Color(0xFF4A6FE3)
private val SoftBlue          = Color(0xFFDDE6FF)
private val TextGrayBrand     = Color(0xFF8A8A9A)
private val WhiteBrand        = Color(0xFFFFFFFF)
private val PremiumTealDark   = Color(0xFF032220)
private val PremiumTealMid    = Color(0xFF08504B)
private val PremiumTealAccent = Color(0xFF14C3B2)

@Composable
fun ComboDashboardScreen(
    viewModel: MainViewModel,
    navController: NavController,
    hideOwnFooter: Boolean = false
) {
    var activeMode by remember { mutableStateOf(ComboMode.SELF) }
    // Bottom nav selection state (Self Control / Family Control / Reports / Settings).
    // Tabs 0 and 1 stay wired to activeMode so the SELF/PARENTAL Crossfade below keeps
    // working exactly as before; Reports and Settings route out via navController like
    // any other independent screen — Combo stays a standalone module, not tied to
    // SelfControlModule's own internal tabs.
    var selectedNavTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.BackgroundWhite)
    ) {
        // ── 1. PREMIUM HEADER (RasFocus brand style — matches SelfControlModule) ──
        ComboPremiumHeader(activeMode = activeMode)

        // ── 2. MAIN CONTENT AREA ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = activeMode, label = "combo_fade_animation") { mode ->
                when (mode) {
                    ComboMode.SELF -> {
                        // ✅ Pass isComboMode = true to hide its internal header/footer
                        com.rasel.RasFocus.selfcontrol.StayFocusedApp(
                            navController = navController,
                            isComboMode = true
                        )
                    }
                    ComboMode.PARENTAL -> {
                        // ✅ Pass isComboMode = true to hide its internal header/footer
                        com.rasel.RasFocus.parental.ParentalRootScreen(
                            viewModel = viewModel,
                            isComboMode = true
                        )
                    }
                }
            }
        }

        // ── 3. FOOTER — RasFocus brand NavigationBar (matches SelfControlModule) ──
        // Hidden when MainActivity's PersonaScaffold already renders a bottom bar
        // around this screen (hideOwnFooter), so the two navigation bars never stack.
        if (!hideOwnFooter) {
            ComboBottomNav(
                selected = selectedNavTab,
                onSelect = { index ->
                    selectedNavTab = index
                    when (index) {
                        0 -> activeMode = ComboMode.SELF
                        1 -> activeMode = ComboMode.PARENTAL
                        2 -> navController.navigate("statistics")
                        3 -> navController.navigate("settings")
                    }
                }
            )
        }
    }
}

// ============================================================
// HEADER DESIGN — fixed Premium Teal gradient (matches SelfControlModule.TopHeader)
// ============================================================
@Composable
fun ComboPremiumHeader(activeMode: ComboMode) {
    val isSelf = activeMode == ComboMode.SELF
    val title = if (isSelf) "Self Control" else "Family Control"
    val subtitle = if (isSelf) "Stay focused on your goals" else "Monitor and protect family"
    val icon = if (isSelf) Icons.Filled.SelfImprovement else Icons.Filled.ChildCare

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
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Box(
                    Modifier.background(PremiumTealAccent.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                        .border(1.dp, PremiumTealAccent.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Pro Combo", color = PremiumTealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                Text("Welcome to", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("RasFocus+ $title", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
// FOOTER NAV — Self Control / Family Control / Reports / Settings
// Mirrors SelfControlModule's SelfControlBottomNav exactly:
// White NavigationBar, SoftBlue pill on selected item, PrimaryBlue tint.
// ============================================================
@Composable
private fun ComboBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Self Control",   Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement),
        Triple("Family Control", Icons.Filled.ChildCare,       Icons.Outlined.ChildCare),
        Triple("Reports",        Icons.Filled.BarChart,        Icons.Outlined.BarChart),
        Triple("Settings",       Icons.Filled.Settings,        Icons.Outlined.Settings)
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

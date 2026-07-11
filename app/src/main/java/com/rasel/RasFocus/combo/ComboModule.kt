package com.rasel.RasFocus.combo

// ============================================================
//  RasFocus+ — Pro Combo Module
//  Self Control tab  → com.rasel.RasFocus.combo.selfcontrol
//  Family Control tab → com.rasel.RasFocus.combo.parental
//  Each is a full independent copy with its own package;
//  Combo owns the brand header + NavigationBar footer.
// ============================================================

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rasel.RasFocus.MainViewModel
import com.rasel.RasFocus.RasFocusColors

// ─────────────────────────────────────────────────────────────
// RASFOCUS BRAND PALETTE — mirrors SelfControlModule.kt exactly
// ─────────────────────────────────────────────────────────────
private val PrimaryBlue       = Color(0xFF4A6FE3)
private val SoftBlue          = Color(0xFFDDE6FF)
private val TextGrayBrand     = Color(0xFF8A8A9A)
private val WhiteBrand        = Color(0xFFFFFFFF)
private val PremiumTealDark   = Color(0xFF032220)
private val PremiumTealMid    = Color(0xFF08504B)
private val PremiumTealAccent = Color(0xFF14C3B2)

enum class ComboMode { SELF, PARENTAL }

// ============================================================
//  ROOT SCREEN
// ============================================================
@Composable
fun ComboDashboardScreen(
    viewModel: MainViewModel,
    navController: NavController,
    hideOwnFooter: Boolean = false
) {
    var activeMode   by remember { mutableStateOf(ComboMode.SELF) }
    var selectedTab  by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite)) {

        // ── 1. BRAND HEADER ──────────────────────────────────
        ComboPremiumHeader(activeMode = activeMode)

        // ── 2. CONTENT — switched by tab 0 / 1 ──────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = activeMode, label = "combo_mode") { mode ->
                when (mode) {
                    // Uses the COPIED selfcontrol package inside combo/
                    ComboMode.SELF ->
                        com.rasel.RasFocus.combo.selfcontrol.StayFocusedApp(
                            navController = navController,
                            isComboMode   = true          // hides its own header/footer
                        )
                    // Uses the COPIED parental package inside combo/
                    ComboMode.PARENTAL ->
                        com.rasel.RasFocus.combo.parental.ParentalRootScreen(
                            viewModel     = viewModel,
                            isComboMode   = true          // hides its own header/footer
                        )
                }
            }
        }

        // ── 3. BRAND FOOTER (hidden when outer scaffold provides nav) ──
        if (!hideOwnFooter) {
            ComboBottomNav(
                selected = selectedTab,
                onSelect = { index ->
                    selectedTab = index
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
//  HEADER — fixed Premium Teal gradient (brand standard)
// ============================================================
@Composable
fun ComboPremiumHeader(activeMode: ComboMode) {
    val isSelf   = activeMode == ComboMode.SELF
    val title    = if (isSelf) "Self Control"              else "Family Control"
    val subtitle = if (isSelf) "Stay focused on your goals" else "Monitor and protect family"
    val icon     = if (isSelf) Icons.Filled.SelfImprovement else Icons.Filled.ChildCare

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(PremiumTealMid, PremiumTealDark)),
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)
    ) {
        Column {
            // Top row — icon | badge | notification
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Box(
                    Modifier
                        .background(PremiumTealAccent.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
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

            // Title block
            Column(Modifier.fillMaxWidth()) {
                Text("Welcome to",              color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("RasFocus+ $title",        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle,                  color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
//  FOOTER — Self Control / Family Control / Reports / Settings
//  Mirrors SelfControlModule's SelfControlBottomNav exactly.
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
        items.forEachIndexed { index, (label, filled, outlined) ->
            NavigationBarItem(
                selected = selected == index,
                onClick  = { onSelect(index) },
                icon = {
                    if (selected == index) {
                        Box(
                            Modifier
                                .background(SoftBlue, RoundedCornerShape(50.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(filled, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Icon(outlined, contentDescription = label, tint = TextGrayBrand, modifier = Modifier.size(22.dp))
                    }
                },
                label  = {
                    Text(
                        label,
                        fontSize   = 11.sp,
                        color      = if (selected == index) PrimaryBlue else TextGrayBrand,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

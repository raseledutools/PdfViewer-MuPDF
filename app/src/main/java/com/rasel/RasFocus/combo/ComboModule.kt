package com.rasel.RasFocus.combo

// ============================================================
//  RasFocus+ — Pro Combo Module  (combo_home route)
//
//  Navigation flow:
//    combo_home     → this screen (brand header + footer)
//    combo_self     → full-screen Self Control  (own header/footer, no outer scaffold)
//    combo_parental → full-screen Family Control (own header/footer, no outer scaffold)
//
//  Footer tab click replaces the entire screen — no nested headers/footers.
//  Settings tab inside selfcontrol/parental navigates back to combo_home.
// ============================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private val CardBg            = Color(0xFFF4F6FA)

// ============================================================
//  ROOT SCREEN  (combo_home)
// ============================================================
@Composable
fun ComboDashboardScreen(
    viewModel: MainViewModel,
    navController: NavController,
    hideOwnFooter: Boolean = false   // true when wrapped in PersonaScaffold
) {
    Column(Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite)) {

        // ── 1. BRAND HEADER ──────────────────────────────────
        ComboPremiumHeader()

        // ── 2. LANDING CONTENT ───────────────────────────────
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Choose your control mode",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF1A1A2E)
            )
            Text(
                "Tap a card to enter full-screen mode. Use Settings inside each module to return here.",
                fontSize = 13.sp,
                color    = TextGrayBrand,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(4.dp))

            // Self Control card
            ComboModeCard(
                icon     = Icons.Filled.SelfImprovement,
                title    = "Self Control",
                subtitle = "Focus mode, app blocking,\nstudy tools & analytics",
                accent   = PremiumTealMid,
                onClick  = {
                    navController.navigate("combo_self") {
                        launchSingleTop = true
                    }
                }
            )

            // Family Control card
            ComboModeCard(
                icon     = Icons.Filled.FamilyRestroom,
                title    = "Family Control",
                subtitle = "Pair & monitor child devices,\nset rules & screen time",
                accent   = Color(0xFF5B3FA6),
                onClick  = {
                    navController.navigate("combo_parental") {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── 3. FOOTER (hidden when outer PersonaScaffold provides nav) ──
        if (!hideOwnFooter) {
            ComboBottomNav(navController = navController)
        }
    }
}

// ============================================================
//  HEADER — fixed Premium Teal gradient (brand standard)
// ============================================================
@Composable
fun ComboPremiumHeader() {
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
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
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(Modifier.fillMaxWidth()) {
                Text("Welcome to",       color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("RasFocus+ Combo",  color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text("Full power — self focus & family protection", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
//  MODE CARD — tappable card that navigates to full screen
// ============================================================
@Composable
private fun ComboModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        onClick    = onClick,
        modifier   = Modifier.fillMaxWidth(),
        shape      = RoundedCornerShape(20.dp),
        colors     = CardDefaults.cardColors(containerColor = WhiteBrand),
        elevation  = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(56.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title,    fontWeight = FontWeight.Bold,  fontSize = 16.sp, color = Color(0xFF1A1A2E))
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = TextGrayBrand, lineHeight = 19.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

// ============================================================
//  FOOTER NAV — Self Control / Family Control / Reports / Settings
//  Tab click = full-screen route swap (no nested header/footer).
// ============================================================
@Composable
private fun ComboBottomNav(navController: NavController) {
    val items = listOf(
        Triple("Self Control",   Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement),
        Triple("Family Control", Icons.Filled.FamilyRestroom,  Icons.Outlined.FamilyRestroom),
        Triple("Reports",        Icons.Filled.BarChart,        Icons.Outlined.BarChart),
        Triple("Settings",       Icons.Filled.Settings,        Icons.Outlined.Settings)
    )
    // Determine which tab looks "selected" based on current back-stack route
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    val selectedIndex = when (currentRoute) {
        "combo_self"     -> 0
        "combo_parental" -> 1
        "statistics"     -> 2
        "settings_screen"-> 3
        else             -> -1   // combo_home itself: nothing highlighted
    }

    NavigationBar(containerColor = WhiteBrand, tonalElevation = 8.dp) {
        items.forEachIndexed { index, (label, filled, outlined) ->
            val isSelected = selectedIndex == index
            NavigationBarItem(
                selected = isSelected,
                onClick  = {
                    when (index) {
                        0 -> navController.navigate("combo_self")     { launchSingleTop = true }
                        1 -> navController.navigate("combo_parental") { launchSingleTop = true }
                        2 -> navController.navigate("statistics")     { launchSingleTop = true }
                        3 -> navController.navigate("settings_screen"){ launchSingleTop = true }
                    }
                },
                icon = {
                    if (isSelected) {
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
                        color      = if (isSelected) PrimaryBlue else TextGrayBrand,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

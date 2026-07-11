package com.rasel.RasFocus.combo

// ============================================================
//  RasFocus+ — Pro Combo Module (DYNAMIC COLLAPSIBLE UI)
//  Design: Premium Header collapses smoothly on scroll down
//  Logic: Uses NestedScrollConnection to track vertical scrolls
// ============================================================

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rasel.RasFocus.MainViewModel
import com.rasel.RasFocus.RasFocusColors

enum class ComboMode { SELF, PARENTAL }

@Composable
fun ComboDashboardScreen(viewModel: MainViewModel, navController: NavController) {
    var activeMode by remember { mutableStateOf(ComboMode.SELF) }
    
    // ─────────────────────────────────────────────────────────────
    // ⚡ DYNAMIC SCROLL ENGINE (Hides header on scroll down)
    // ─────────────────────────────────────────────────────────────
    val localDensity = LocalDensity.current
    val headerMaxHeightDp = 130.dp
    val headerMaxHeightPx = with(localDensity) { headerMaxHeightDp.toPx() }
    
    // ট্র্যাক করবে হেডার কত পিক্সেল স্ক্রোল ডাউন হয়েছে
    var headerOffsetPx by remember { mutableStateOf(0f) }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = headerOffsetPx + delta
                // হেডার হাইট সীমা ০ থেকে মাইনাস ম্যাক্স হাইট পর্যন্ত লক থাকবে
                headerOffsetPx = newOffset.coerceIn(-headerMaxHeightPx, 0f)
                return Offset.Zero
            }
        }
    }
    
    // পিক্সেল থেকে ডাইনামিক ডিপি ক্যালকুলেশন
    val currentHeaderHeightDp = headerMaxHeightDp + with(localDensity) { headerOffsetPx.toDp() }
    // ─────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.BackgroundWhite)
            .nestedScroll(nestedScrollConnection) // পুরো স্ক্রিনের স্ক্রোল ইভেন্ট হুক করা হলো
    ) {
        // ── 1. DYNAMIC COLLAPSIBLE HEADER ──
        Box(
            modifier = Modifier
                .height(currentHeaderHeightDp)
                .fillMaxWidth()
                .graphicsLayer {
                    // স্ক্রোল করার সাথে সাথে হেডার ফেড-আউট (fade out) হবে
                    alpha = (headerMaxHeightPx + headerOffsetPx) / headerMaxHeightPx
                }
        ) {
            ComboPremiumHeader(activeMode = activeMode)
        }
        
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
        
        // ── 3. FIXED FOOTER TOGGLE ──
        ComboFooterToggle(
            activeMode = activeMode,
            onModeSelect = { activeMode = it }
        )
    }
}

// ============================================================
// HEADER DESIGN
// ============================================================
@Composable
fun ComboPremiumHeader(activeMode: ComboMode) {
    val isSelf = activeMode == ComboMode.SELF
    
    val bgColor1 = if (isSelf) Color(0xFFFF6D00) else RasFocusColors.PrimaryTeal
    val bgColor2 = if (isSelf) Color(0xFFFF8F00) else RasFocusColors.PrimaryTealLight
    val title = if (isSelf) "Self Control" else "Parental Control"
    val subtitle = if (isSelf) "Stay focused on your goals" else "Monitor and protect family"
    val icon = if (isSelf) Icons.Filled.SelfImprovement else Icons.Filled.ChildCare

    val animColor1 by animateColorAsState(targetValue = bgColor1, animationSpec = tween(500), label = "color1")
    val animColor2 by animateColorAsState(targetValue = bgColor2, animationSpec = tween(500), label = "color2")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = listOf(animColor1, animColor2)),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Pro Combo Mode", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(subtitle, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

// ============================================================
// FOOTER DESIGN (Fixed Buttons)
// ============================================================
@Composable
fun ComboFooterToggle(activeMode: ComboMode, onModeSelect: (ComboMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .shadow(elevation = 16.dp, spotColor = RasFocusColors.CardShadow)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ComboToggleButton(
            title = "Self Control",
            isSelected = activeMode == ComboMode.SELF,
            activeColor = Color(0xFFFF6D00),
            icon = Icons.Filled.SelfImprovement,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelect(ComboMode.SELF) }
        )
        
        ComboToggleButton(
            title = "Family Control",
            isSelected = activeMode == ComboMode.PARENTAL,
            activeColor = RasFocusColors.PrimaryTeal,
            icon = Icons.Filled.ChildCare,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelect(ComboMode.PARENTAL) }
        )
    }
}

@Composable
fun ComboToggleButton(
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(targetValue = if (isSelected) activeColor else RasFocusColors.SurfaceOffWhite, label = "bg")
    val contentColor by animateColorAsState(targetValue = if (isSelected) Color.White else RasFocusColors.SubtleText, label = "content")
    val elevation = if (isSelected) 8.dp else 0.dp

    Card(
        modifier = modifier
            .height(64.dp)
            .shadow(elevation, RoundedCornerShape(20.dp), spotColor = activeColor)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (!isSelected) BorderStroke(1.dp, RasFocusColors.DividerColor) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

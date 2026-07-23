package com.rasel.RasFocus.selfcontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.rasel.RasFocus.ui.theme.SoftWhite

private val DrawerBg = Color(0xFF1A1A2E)
private val TextWhite = SoftWhite
private val AccentTeal = Color(0xFF14C3B2)

@Composable
fun DrawerContent(
    onNavigate: (String) -> Unit,
    closeDrawer: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = DrawerBg,
        drawerContentColor = TextWhite,
        modifier = Modifier.width(280.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Profile Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF08504B), DrawerBg)
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("R", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DrawerBg)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "RasFocus User",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        "Super Professional Mode",
                        fontSize = 12.sp,
                        color = AccentTeal
                    )
                }
            }

            HorizontalDivider(color = SoftWhite.copy(alpha = 0.1f))

            // Menu Items
            Spacer(modifier = Modifier.height(16.dp))
            DrawerMenuItem(Icons.Default.Home, "Home") {
                onNavigate("home")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.Analytics, "Statistics") {
                onNavigate("statistics")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.Block, "Block List") {
                onNavigate("extreme_block")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.MobileOff, "Block Apps") {
                onNavigate("single_apps")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.DesktopWindows, "Block Websites") {
                onNavigate("single_website")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.PlaylistAddCheck, "Blocking Plan") {
                onNavigate("blocking_plan")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.Shield, "Adult Block") {
                onNavigate("adult_block")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.MenuBook, "Deep Study") {
                onNavigate("deep_study")
                closeDrawer()
            }
            DrawerMenuItem(Icons.Default.Settings, "Settings") {
                onNavigate("settings")
                closeDrawer()
            }

            // Master Password — Professional lock system
            DrawerMenuItem(Icons.Default.Lock, "Master Password", tint = AccentTeal) {
                onNavigate("master_password")
                closeDrawer()
            }

            var showUpdateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current

            DrawerMenuItem(Icons.Default.SystemUpdateAlt, "Check for Updates") {
                showUpdateDialog = true
            }

            if (showUpdateDialog) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("Close", color = AccentTeal) }
                    },
                    containerColor = DrawerBg,
                    text = {
                        UpdateCenterSection(context)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            HorizontalDivider(color = SoftWhite.copy(alpha = 0.1f))
            DrawerMenuItem(Icons.Default.Logout, "Logout", AccentTeal) {
                closeDrawer()
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = TextWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

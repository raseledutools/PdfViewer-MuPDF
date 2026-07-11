package com.rasel.RasFocus.selfcontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rasel.RasFocus.DataManager

private val BgDark = Color(0xFF1E293B)
private val CardBg = Color(0xFF1E293B)
private val AccentGreen = Color(0xFF0096B4)
private val AccentBlue = Color(0xFF3B82F6)
private val SoftWhiteStat = Color(0xFFF8FAFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController) {
    val totalTimeMillis = DataManager.totalFocusTimeMillis
    val totalSessions = DataManager.totalSessions
    
    val hours = totalTimeMillis / (1000 * 60 * 60)
    val minutes = (totalTimeMillis / (1000 * 60)) % 60
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Statistics", color = SoftWhiteStat) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SoftWhiteStat)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Focus Time", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(timeStr, color = SoftWhiteStat, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(icon = Icons.Default.TrendingUp, label = "Productivity", value = "Active", color = AccentGreen)
                        StatItem(icon = Icons.Default.Timer, label = "Sessions", value = totalSessions.toString(), color = AccentBlue)
                    }
                }
            }

            // Detailed Stats Placeholder
            Text("Weekly Progress", color = SoftWhiteStat, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            
            // Bar Chart Placeholder
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Charts will populate as you complete more sessions this week.", color = Color.Gray, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = SoftWhiteStat, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

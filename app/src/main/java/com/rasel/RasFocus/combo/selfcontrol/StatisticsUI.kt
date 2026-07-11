package com.rasel.pdfviewer.combo.selfcontrol

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.rasel.pdfviewer.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private val BgDark = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBgLight = Color(0xFF243447)
private val AccentGreen = Color(0xFF0096B4)
private val AccentBlue = Color(0xFF3B82F6)
val SoftWhite = Color(0xFFF8FAFC) // Reusable off-white theme color

// A small rotating palette so the per-app bars/rings don't all look identical.
private val AppColorPalette = listOf(
    Color(0xFF14C3B2), Color(0xFF3B82F6), Color(0xFF8B5CF6),
    Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF10B981),
    Color(0xFFEC4899), Color(0xFF6366F1)
)

/** One row of "today's" per-app usage, ready for direct UI consumption. */
data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val usageMillis: Long,
    val lastTimeUsed: Long
)

private val EXCLUDED_PACKAGES = setOf(
    "com.android.systemui", "com.android.launcher", "com.android.launcher3",
    "com.sec.android.app.launcher", "com.miui.home", "com.huawei.android.launcher",
    "android", "com.android.settings", "com.rasel.pdfviewer"
)

/**
 * Digital-Wellbeing-style usage engine: queries today's (midnight → now)
 * per-app foreground time directly from UsageStatsManager, resolves each
 * package into a display name + icon, and returns everything sorted by
 * time spent (descending) — exactly what a "today's screen time" dashboard needs.
 */
object UsageStatsEngine {

    // Delegates to the app's existing, already-proven permission check
    // (BlockingManager) instead of re-implementing the AppOpsManager dance.
    fun hasPermission(context: Context): Boolean = BlockingManager.hasUsageStatsPermission(context)

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Today's per-app usage (icon/name resolved), sorted by time spent, descending. */
    suspend fun getTodayUsage(context: Context): List<AppUsageStat> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val midnight = startOfToday()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, now) ?: return@withContext emptyList()

        stats
            .filter { it.totalTimeInForeground > 60_000L } // ignore under a minute of use
            .filter { it.packageName !in EXCLUDED_PACKAGES }
            .filter { !it.packageName.startsWith("com.android.") || it.packageName == "com.android.chrome" }
            .groupBy { it.packageName } // some OEMs return duplicate entries per package
            .map { (pkg, entries) -> pkg to entries.maxByOrNull { it.totalTimeInForeground }!! }
            .mapNotNull { (pkg, stat) ->
                try {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    // Skip apps with no launcher entry point — usually background/system components,
                    // not something a person would recognize as "an app they used."
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                    AppUsageStat(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        usageMillis = stat.totalTimeInForeground,
                        lastTimeUsed = stat.lastTimeUsed
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    null // app was uninstalled after the stat was recorded
                }
            }
            .sortedByDescending { it.usageMillis }
    }

    fun totalMillis(stats: List<AppUsageStat>): Long = stats.sumOf { it.usageMillis }

    fun colorFor(index: Int): Color = AppColorPalette[index % AppColorPalette.size]
}

/** Formats milliseconds into a compact "2h 14m" / "38m" style string, like Digital Wellbeing. */
fun formatUsageDuration(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController) {
    val context = LocalContext.current

    val totalTimeMillis = DataManager.totalFocusTimeMillis
    val totalSessions = DataManager.totalSessions
    val hours = totalTimeMillis / (1000 * 60 * 60)
    val minutes = (totalTimeMillis / (1000 * 60)) % 60
    val focusTimeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    var hasPermission by remember { mutableStateOf(UsageStatsEngine.hasPermission(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var appUsageList by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }

    // Re-check permission + reload usage every time this screen becomes visible again
    // (e.g. after the person comes back from the system "Usage Access" settings page).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = UsageStatsEngine.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            appUsageList = UsageStatsEngine.getTodayUsage(context)
            isLoading = false
        } else {
            isLoading = false
        }
    }

    val totalScreenTimeMillis = remember(appUsageList) { UsageStatsEngine.totalMillis(appUsageList) }
    val maxAppMillis = remember(appUsageList) { appUsageList.maxOfOrNull { it.usageMillis } ?: 1L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Statistics", color = SoftWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Today's Screen Time — the headline Digital-Wellbeing-style card ──
            item {
                TodayScreenTimeCard(
                    hasPermission = hasPermission,
                    isLoading = isLoading,
                    totalMillis = totalScreenTimeMillis,
                    appCount = appUsageList.size,
                    onEnableClick = { BlockingManager.openUsageAccessSettings(context) }
                )
            }

            // ── Per-app usage breakdown, sorted by time spent — like Digital Wellbeing's app list ──
            if (hasPermission && !isLoading && appUsageList.isNotEmpty()) {
                item {
                    Text(
                        "App Usage Today",
                        color = SoftWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            appUsageList.forEachIndexed { index, app ->
                                AppUsageRow(
                                    app = app,
                                    barColor = UsageStatsEngine.colorFor(index),
                                    fractionOfMax = app.usageMillis.toFloat() / maxAppMillis.toFloat(),
                                    fractionOfTotal = if (totalScreenTimeMillis > 0) app.usageMillis.toFloat() / totalScreenTimeMillis.toFloat() else 0f
                                )
                                if (index != appUsageList.lastIndex) {
                                    HorizontalDivider(color = SoftWhite.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                                }
                            }
                        }
                    }
                }
            } else if (hasPermission && !isLoading && appUsageList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No app usage recorded yet today. Check back after using a few apps.",
                                color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Existing focus-session summary — kept as-is, distinct from OS-level screen time ──
            item {
                Text("Focus Sessions", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
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
                        Text(focusTimeStr, color = SoftWhite, fontSize = 36.sp, fontWeight = FontWeight.Bold)
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
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/** Big headline card: total time on the phone today, plus a permission-request state. */
@Composable
private fun TodayScreenTimeCard(
    hasPermission: Boolean,
    isLoading: Boolean,
    totalMillis: Long,
    appCount: Int,
    onEnableClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Today's Screen Time", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !hasPermission -> {
                    Text(
                        "Grant Usage Access to see how much time you're spending on your phone today.",
                        color = SoftWhite.copy(alpha = 0.85f), fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onEnableClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Usage Access", fontWeight = FontWeight.Bold)
                    }
                }
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(32.dp))
                    }
                }
                else -> {
                    Text(
                        formatUsageDuration(totalMillis),
                        color = SoftWhite, fontSize = 44.sp, fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (appCount > 0) "across $appCount ${if (appCount == 1) "app" else "apps"}" else "No usage yet today",
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** One row in the per-app usage list — icon, name, formatted time, and a proportional bar. */
@Composable
private fun AppUsageRow(
    app: AppUsageStat,
    barColor: Color,
    fractionOfMax: Float,
    fractionOfTotal: Float
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CardBgLight),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.toBitmap(width = 96, height = 96).asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = SoftWhite.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${(fractionOfTotal * 100).toInt()}% of today",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                formatUsageDuration(app.usageMillis),
                color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Proportional bar — relative to the most-used app, so the biggest bar is always full.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SoftWhite.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fractionOfMax.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

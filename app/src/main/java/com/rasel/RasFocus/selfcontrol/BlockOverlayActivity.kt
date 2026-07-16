package com.rasel.RasFocus.selfcontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson

// ==========================================
// BLOCK OVERLAY ACTIVITY
//
// Launched by AppBlockerService when a blocked app is detected.
// Shows full-screen overlay.  User must satisfy the lock condition
// to either:
//   A) Go back (home)  — always available
//   B) Temporarily unlock for this session (mode-dependent)
//
// Lock modes:
//   0 = Self Control   → cannot unlock, only go home
//   1 = Parents Control → enter PIN to unlock
//   2 = Long Text      → type paragraph to unlock
//   3 = Daily Limit    → shows remaining minutes (no unlock)
//   4 = Hourly Limit   → shows remaining minutes (no unlock)
// ==========================================

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PACKAGE = "blocked_package"
        private const val EXTRA_PROFILE = "profile_json"

        // Paragraph the user must type for Long Text mode
        const val UNLOCK_PARAGRAPH =
            "আমি এখন মনোযোগ দিয়ে পড়াশোনা করব। বিক্ষেপ থেকে দূরে থেকে আমার লক্ষ্য অর্জন করব।"

        fun createIntent(context: Context, blockedPackage: String, profile: BlockingFocusProfile): Intent =
            Intent(context, BlockOverlayActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, blockedPackage)
                putExtra(EXTRA_PROFILE, Gson().toJson(profile))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity appear over lock screen / other apps
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val profileJson    = intent.getStringExtra(EXTRA_PROFILE) ?: ""
        val profile = try {
            Gson().fromJson(profileJson, BlockingFocusProfile::class.java)
        } catch (e: Exception) {
            finish(); return
        }

        setContent {
            BlockOverlayScreen(
                blockedPackage = blockedPackage,
                profile        = profile,
                onGoHome       = {
                    // Send user to home screen
                    startActivity(
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    finish()
                },
                onUnlocked = {
                    // Mark this session as temporarily unlocked (10 min grace)
                    getSharedPreferences("rasfocus_break_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putLong("temp_unlock_${blockedPackage}", System.currentTimeMillis())
                        .apply()
                    finish()
                }
            )
        }
    }

    override fun onBackPressed() {
        // Prevent back press from dismissing overlay — send home instead
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }
}

// ==========================================
// OVERLAY UI
// ==========================================

@Composable
fun BlockOverlayScreen(
    blockedPackage: String,
    profile: BlockingFocusProfile,
    onGoHome: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current

    // Get app name
    val appName = remember(blockedPackage) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(blockedPackage, 0)).toString()
        } catch (e: Exception) {
            blockedPackage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface, com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Blocked icon ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "blocked!",
                fontSize = 14.sp,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = appName,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "\"${profile.profileName}\" profile দ্বারা block করা হয়েছে",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // ── Mode-specific unlock UI ────────────────────────────────────────
            when (profile.lockMode) {
                1 -> PinUnlockSection(profile.parentPin, onUnlocked)
                2 -> LongTextUnlockSection(onUnlocked)
                3 -> DailyLimitInfoSection(blockedPackage, profile.dailyLimitMinutes, context)
                4 -> HourlyLimitInfoSection(profile.hourlyLimitMinutes)
                else -> SelfControlInfoSection(profile)   // 0 = self control
            }

            Spacer(Modifier.height(36.dp))

            // ── Go Home button — always visible ───────────────────────────────
            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Home এ যাও", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── PIN unlock ─────────────────────────────────────────────────────────────────
@Composable
fun PinUnlockSection(storedPin: String, onUnlocked: () -> Unit) {
    var pin     by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PIN দিয়ে unlock করো", fontSize = 16.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; isError = false } },
            placeholder = { Text("PIN", color = Color(0xFF64748B)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color(0xFFEAB308),
                unfocusedBorderColor = Color(0xFF475569),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                errorBorderColor     = Color(0xFFEF4444),
                cursorColor          = Color(0xFFEAB308)
            )
        )

        if (isError) {
            Spacer(Modifier.height(6.dp))
            Text("❌ ভুল PIN! আবার চেষ্টা করো", fontSize = 13.sp, color = Color(0xFFEF4444))
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (pin == storedPin) onUnlocked()
                else { isError = true; pin = "" }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308))
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("Unlock করো", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

// ── Long Text unlock ───────────────────────────────────────────────────────────
@Composable
fun LongTextUnlockSection(onUnlocked: () -> Unit) {
    var typedText by remember { mutableStateOf("") }
    val target = BlockOverlayActivity.UNLOCK_PARAGRAPH
    val progress = if (target.isEmpty()) 1f else (typedText.length.coerceAtMost(target.length)).toFloat() / target.length
    val isMatch  = typedText == target

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Paragraph টাইপ করে unlock করো", fontSize = 16.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Target text card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F))
        ) {
            Text(
                text = target,
                modifier = Modifier.padding(14.dp),
                fontSize = 13.sp,
                color = Color(0xFFBAE6FD),
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF3B82F6),
            trackColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface
        )

        Spacer(Modifier.height(4.dp))
        Text("${(progress * 100).toInt()}% টাইপ হয়েছে", fontSize = 12.sp, color = Color(0xFF64748B))

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            placeholder = { Text("এখানে টাইপ করো...", color = Color(0xFF475569)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color(0xFF3B82F6),
                unfocusedBorderColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surfaceVariant,
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = Color(0xFF3B82F6)
            )
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { if (isMatch) onUnlocked() },
            enabled = isMatch,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Unlock করো", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Self Control info (no unlock) ──────────────────────────────────────────────
@Composable
fun SelfControlInfoSection(profile: BlockingFocusProfile) {
    val context = LocalContext.current

    // FIX: আগে profile.selfDays/selfHours/selfMinutes দেখাত — এগুলো session
    // setup-এর সময়ের value, actual remaining time না। তাই "wrong day" দেখাত।
    // এখন SharedPreferences থেকে KEY_BREAK_END নিয়ে actual remaining calculate করছি।
    val remainingLabel = remember {
        val endTime = context.getSharedPreferences("take_rest_prefs", Context.MODE_PRIVATE)
            .getLong("break_end_time", 0L)
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0L) return@remember "শেষ হয়ে গেছে"
        val totalSec  = remaining / 1000L
        val d = totalSec / 86400L
        val h = (totalSec % 86400L) / 3600L
        val m = (totalSec % 3600L) / 60L
        buildString {
            if (d > 0) append("${d}d ")
            if (h > 0) append("${h}h ")
            append("${m}m")
        }.trim().ifBlank { "< 1m" }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14532D).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("Self Control Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "বাকি সময়: $remainingLabel\nTimer শেষ হলে block উঠবে। এখন unlock করা যাবে না।",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

// ── Daily Limit info ───────────────────────────────────────────────────────────
@Composable
fun DailyLimitInfoSection(packageName: String, limitMinutes: Int, context: Context) {
    val usedMinutes = remember {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val startOfDay = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfDay, now
            )
            val stat = stats?.find { it.packageName == packageName }
            ((stat?.totalTimeInForeground ?: 0L) / 60_000L).toInt()
        } catch (_: Exception) { 0 }
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4C1D95).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("Daily Limit পূর্ণ হয়েছে", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
            Spacer(Modifier.height(8.dp))
            Text(
                text = "আজকে ${usedMinutes} মিনিট ব্যবহার হয়েছে।\nLimit: ${limitMinutes} মিনিট।\nকাল সকালে আবার ব্যবহার করতে পারবে।",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

// ── Hourly Limit info ──────────────────────────────────────────────────────────
@Composable
fun HourlyLimitInfoSection(limitMinutes: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7C2D12).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("Hourly Limit পূর্ণ হয়েছে", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
            Spacer(Modifier.height(8.dp))
            Text(
                text = "এই ঘণ্টায় ${limitMinutes} মিনিট limit শেষ হয়ে গেছে।\nপরের ঘণ্টায় আবার ব্যবহার করতে পারবে।",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

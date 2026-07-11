package com.rasel.RasFocus.combo.selfcontrol

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────
internal val BpTealMid    = Color(0xFF00897B)
internal val BpTealDark   = Color(0xFF00695C)
internal val BpTealAccent = Color(0xFF1DE9B6)
internal val BpGrayBg     = Color(0xFFF8F9FA)
internal val BpTextDark   = Color(0xFF212121)
internal val BpTextGray   = Color(0xFF757575)
internal val BpRedAccent  = Color(0xFFE53935)

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────
enum class BpScreen       { SETUP, RUNNING, UNLOCK, LOCK_DETAIL, ALLOW_LIST }
enum class BpLockMode     { SELF_CONTROL, PARENTS_CONTROL, LONG_TEXT }
enum class BpPhoneOption  { COMPLETE, CUSTOMIZE }
enum class BpAllowTab     { APPS, WEBSITES }

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────
data class BpAppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

// ─────────────────────────────────────────────────────────────────────────────
// CONSTANTS (From take_rest.kt)
// ─────────────────────────────────────────────────────────────────────────────
object BpC {
    const val PREFS            = "take_rest_prefs"
    const val KEY_ALLOWED      = "allowed_pkgs"
    const val KEY_BREAK_END    = "break_end_time"
    const val CHANNEL_ID       = "take_rest_channel"
    const val NOTIF_ID         = 9999

    // ── Lock system keys ──
    const val KEY_LOCK_MODE    = "lock_mode"
    const val KEY_PARENT_PASS  = "parent_password"
    const val LOCK_SELF        = "self"
    const val LOCK_PARENTS     = "parents"
    const val LOCK_LONGTEXT    = "longtext"

    const val LONG_UNLOCK_TEXT =
        "I acknowledge that I set this focus session to improve my productivity. " +
        "Unlocking early means I am choosing distraction over my goals. " +
        "I understand that consistent focus builds habits and leads to long-term success. " +
        "I am committed to completing my session and will return to my work with full attention."

    val DEFAULT_ALLOWED = setOf(
        "com.android.dialer",            
        "com.google.android.dialer",    
        "com.samsung.android.dialer",   
        "com.android.phone",            
        "com.android.mms",              
        "com.google.android.apps.messaging", 
        "com.samsung.android.messaging",     
        "com.android.messaging"         
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PomPrefs (For Pomodoro Alarms)
// ─────────────────────────────────────────────────────────────────────────────
object PomPrefs {
    private const val PREFS       = "pom_prefs_v1"
    private const val KEY_COUNT   = "pom_count"
    private const val KEY_SESSION = "pom_session_ms"
    private const val KEY_BREAK   = "pom_break_ms"
    private const val KEY_END     = "pom_end_ms"

    fun save(context: Context, count: Int, sessionMs: Long, breakMs: Long, endMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COUNT, count).putLong(KEY_SESSION, sessionMs)
            .putLong(KEY_BREAK, breakMs).putLong(KEY_END, endMs).apply()
    }
    fun isActive(context: Context): Boolean {
        val end = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END, 0L)
        return end > System.currentTimeMillis()
    }
    fun sessions(context: Context): Triple<Int, Long, Long> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(p.getInt(KEY_COUNT, 0), p.getLong(KEY_SESSION, 0L), p.getLong(KEY_BREAK, 0L))
    }
    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper functions
// ─────────────────────────────────────────────────────────────────────────────
private fun bpHasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

private fun promptInternetPanel(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (_: Exception) {}
}

private fun schedulePomodoroAlarms(context: Context, count: Int, sessionMs: Long, breakMs: Long) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    // FIX: Android 12+ (S, API 31) requires SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM to call
    // setExact/setExactAndAllowWhileIdle — without checking canScheduleExactAlarms(), this
    // throws SecurityException and crashes the app the moment a Pomodoro session starts,
    // on every device running Android 12-16 where the user hasn't granted "Alarms & reminders".
    val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    var t = System.currentTimeMillis()
    for (i in 0 until count) {
        t += sessionMs
        val pi = PendingIntent.getBroadcast(
            context, 1000 + i,
            Intent("com.rasel.pdfviewer.POMODORO_ALARM").putExtra("index", i),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                canExact -> am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
                else -> am.set(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, t, pi)
        }
        if (i < count - 1) t += breakMs
    }
}

private fun cancelPomodoroAlarms(context: Context, sessions: Triple<Int, Long, Long>) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    for (i in 0 until sessions.first) {
        am.cancel(PendingIntent.getBroadcast(
            context, 1000 + i,
            Intent("com.rasel.pdfviewer.POMODORO_ALARM"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }
}

@Composable
private fun BpAppIconImage(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: FocusLauncherCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FocusLauncherCard(onSessionStart: () -> Unit) {
    var showSetup by remember { mutableStateOf(false) }
    com.rasel.RasFocus.ui.theme.PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = { showSetup = true },
        colors = CardDefaults.cardColors(containerColor = BpTealMid),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneLocked, contentDescription = null, tint = BpTealDark, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Button Phone Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text("Lock yourself to minimal apps only", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
    if (showSetup) {
        BpSetupDialog(onDismiss = { showSetup = false }, onSessionStart = { showSetup = false; onSessionStart() })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: TakeABreakCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TakeABreakCard(onSessionStart: () -> Unit = {}) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val endMs = context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).getLong(BpC.KEY_BREAK_END, 0L)
    var isActive by remember { mutableStateOf(endMs > System.currentTimeMillis()) }

    com.rasel.RasFocus.ui.theme.PremiumCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = if (isActive) BpTealMid else Color(0xFFE8D5F5)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).background(if (isActive) Color.White.copy(0.15f) else BpTealMid.copy(0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("☕", fontSize = 24.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isActive) "Session Active" else "Take a Break", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isActive) Color.White else BpTextDark)
                Text(if (isActive) "চলছে — tap করে বন্ধ বা পরিবর্তন করো" else "Custom break / Pomodoro timer", fontSize = 12.sp, color = if (isActive) Color.White.copy(0.75f) else BpTextDark.copy(0.65f))
            }
            Icon(if (isActive) Icons.Default.Timer else Icons.Default.ChevronRight, contentDescription = null, tint = if (isActive) BpTealAccent else BpTextGray)
        }
    }
    if (showDialog) {
        BpBreakDialog(
            onDismiss = { showDialog = false },
            onStarted = { isActive = true; showDialog = false; onSessionStart() },
            onStopped = { isActive = false; showDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpSectionLabel helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BpSectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color(0xFF2DD4AA)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpSetupDialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BpSetupDialog(onDismiss: () -> Unit, onSessionStart: () -> Unit) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(BpScreen.SETUP) }
    var lockMode by remember { mutableStateOf(BpLockMode.SELF_CONTROL) }
    var phoneOption by remember { mutableStateOf(BpPhoneOption.COMPLETE) }
    var lockModeExpanded by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf("0") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }
    var parentPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var blockInternet by remember { mutableStateOf(false) }
    var allowedPkgs by remember { mutableStateOf(BpC.DEFAULT_ALLOWED.toSet()) }
    var allowedWebs by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = { if (screen == BpScreen.SETUP) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = screen == BpScreen.SETUP)) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {

                // ── SETUP: সব এক পেজে, no scroll ─────────────────────────────
                BpScreen.SETUP -> Column(
                    Modifier.fillMaxSize().background(Color(0xFF060B14))
                ) {
                    // Header
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF00897B), Color(0xFF00695C))),
                                RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss, Modifier.size(40.dp)) {
                                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Button Phone Mode", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                                Text("একবারে সব সেট করো", fontSize = 11.sp, color = Color.White.copy(0.7f))
                            }
                        }
                    }

                    // Body — single scroll only if needed
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ① Phone Mode
                        BpSectionLabel("📱 Phone Mode")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                BpPhoneOption.COMPLETE to ("🔒 Complete" to "Essential apps only"),
                                BpPhoneOption.CUSTOMIZE to ("⚙️ Customize" to "নিজে apps বেছে নাও")
                            ).forEach { (opt, texts) ->
                                val (title, sub) = texts; val sel = phoneOption == opt
                                Card(
                                    Modifier.weight(1f).clickable { phoneOption = opt },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (sel) BpTealMid else Color(0xFF0E1E35)
                                    ),
                                    border = if (sel) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3A5C))
                                ) {
                                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(4.dp))
                                        Text(sub, fontSize = 10.sp, color = if (sel) Color.White.copy(0.8f) else Color(0xFF5A7A9A), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }

                        // ② Lock Mode — 3 chips instead of dropdown
                        BpSectionLabel("🔐 Unlock Type")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                BpLockMode.SELF_CONTROL  to ("Self\nControl" to "নিজেই unlock"),
                                BpLockMode.PARENTS_CONTROL to ("Parents\nControl" to "Password lock"),
                                BpLockMode.LONG_TEXT     to ("Long\nText" to "200 words")
                            ).forEach { (mode, texts) ->
                                val (title, sub) = texts; val sel = lockMode == mode
                                Card(
                                    Modifier.weight(1f).clickable { lockMode = mode; lockModeExpanded = false },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (sel) Color(0xFF1DE9B6).copy(0.15f) else Color(0xFF0E1E35)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, if (sel) BpTealAccent else Color(0xFF1A3A5C)
                                    )
                                ) {
                                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                            color = if (sel) BpTealAccent else Color.White,
                                            textAlign = TextAlign.Center, lineHeight = 16.sp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(sub, fontSize = 9.sp,
                                            color = if (sel) BpTealAccent.copy(0.8f) else Color(0xFF5A7A9A),
                                            textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }

                        // ③ Duration / Password (inline — no new screen)
                        when (lockMode) {
                            BpLockMode.SELF_CONTROL -> {
                                BpSectionLabel("⏱ Duration")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Days" to days, "Hours" to hours, "Mins" to minutes).forEachIndexed { i, (lbl, v) ->
                                        OutlinedTextField(
                                            v, { if (i==0) days=it else if (i==1) hours=it else minutes=it },
                                            label = { Text(lbl, fontSize = 11.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = BpTealAccent,
                                                unfocusedBorderColor = Color(0xFF1A3A5C),
                                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                                focusedLabelColor = BpTealAccent, unfocusedLabelColor = Color(0xFF5A7A9A),
                                                focusedContainerColor = Color(0xFF0E1E35), unfocusedContainerColor = Color(0xFF0E1E35)
                                            )
                                        )
                                    }
                                }
                            }
                            BpLockMode.PARENTS_CONTROL -> {
                                BpSectionLabel("🔑 Password")
                                OutlinedTextField(
                                    parentPass, { parentPass = it }, label = { Text("Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BpTealAccent, unfocusedBorderColor = Color(0xFF1A3A5C),
                                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedLabelColor = BpTealAccent, unfocusedLabelColor = Color(0xFF5A7A9A),
                                        focusedContainerColor = Color(0xFF0E1E35), unfocusedContainerColor = Color(0xFF0E1E35)
                                    )
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    confirmPass, { confirmPass = it }, label = { Text("Confirm Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BpTealAccent, unfocusedBorderColor = Color(0xFF1A3A5C),
                                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedLabelColor = BpTealAccent, unfocusedLabelColor = Color(0xFF5A7A9A),
                                        focusedContainerColor = Color(0xFF0E1E35), unfocusedContainerColor = Color(0xFF0E1E35)
                                    )
                                )
                            }
                            BpLockMode.LONG_TEXT -> {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E35)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BpTealAccent.copy(0.3f))
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("✍️", fontSize = 22.sp); Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Long Text Unlock", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                            Text("~200 words টাইপ করলে unlock হবে", fontSize = 11.sp, color = Color(0xFF5A7A9A))
                                        }
                                    }
                                }
                            }
                        }

                        // ④ Extra: Block Internet
                        BpSectionLabel("⚙️ Extra")
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E35)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3A5C))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { blockInternet = !blockInternet }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WifiOff, null, tint = if (blockInternet) BpTealAccent else Color(0xFF5A7A9A), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Block Internet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                    Text("Session চলাকালীন internet বন্ধ", fontSize = 10.sp, color = Color(0xFF5A7A9A))
                                }
                                Switch(checked = blockInternet, onCheckedChange = { blockInternet = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = BpTealAccent, uncheckedTrackColor = Color(0xFF1A3A5C)))
                            }
                        }
                    }

                    // Bottom CTA
                    Box(Modifier.fillMaxWidth().background(Color(0xFF060B14)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button(
                            onClick = { screen = BpScreen.ALLOW_LIST },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (phoneOption == BpPhoneOption.COMPLETE) "START SESSION" else "Next: Choose Apps →",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White
                            )
                        }
                    }
                }

                // ── LOCK_DETAIL: merged into SETUP above, kept as stub ──────
                BpScreen.LOCK_DETAIL -> { screen = BpScreen.ALLOW_LIST }

                BpScreen.ALLOW_LIST -> BpAllowListScreen(
                    selectedPkgs = allowedPkgs, selectedWebs = allowedWebs,
                    isComplete = phoneOption == BpPhoneOption.COMPLETE,
                    onPkgsChanged = { allowedPkgs = it }, onWebsChanged = { allowedWebs = it },
                    onBack = { screen = BpScreen.SETUP },
                    onStart = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            Toast.makeText(context, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
                            return@BpAllowListScreen
                        }
                        if (!bpHasUsageStatsPermission(context)) {
                            Toast.makeText(context, "Please enable Usage Access", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            return@BpAllowListScreen
                        }

                        val totalMs = if (lockMode != BpLockMode.SELF_CONTROL) 100L * 365 * 24 * 3600 * 1000 // Indefinite
                            else (days.toLongOrNull() ?: 0L) * 86_400_000L + (hours.toLongOrNull() ?: 0L) * 3_600_000L + (minutes.toLongOrNull() ?: 30L) * 60_000L
                        val endTime = System.currentTimeMillis() + totalMs
                        
                        val finalPkgs = (if (phoneOption == BpPhoneOption.COMPLETE) BpC.DEFAULT_ALLOWED else allowedPkgs + BpC.DEFAULT_ALLOWED).toSet()
                        
                        val lockModeString = when(lockMode) {
                            BpLockMode.SELF_CONTROL -> BpC.LOCK_SELF
                            BpLockMode.PARENTS_CONTROL -> BpC.LOCK_PARENTS
                            BpLockMode.LONG_TEXT -> BpC.LOCK_LONGTEXT
                        }

                        context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                            .putLong(BpC.KEY_BREAK_END, endTime)
                            .putString(BpC.KEY_ALLOWED, finalPkgs.joinToString(","))
                            .putString(BpC.KEY_LOCK_MODE, lockModeString)
                            .putString(BpC.KEY_PARENT_PASS, parentPass)
                            .apply()
                            
                        if (blockInternet) promptInternetPanel(context)

                        val svcIntent = Intent(context, BpBlockingService::class.java)
                            .putExtra(BpBlockingService.EXTRA_SHOW_NOW, true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                        else context.startService(svcIntent)
                        
                        Toast.makeText(context, "Button Phone Session Started!", Toast.LENGTH_LONG).show()
                        onSessionStart()
                    }
                )
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpAllowListScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BpAllowListScreen(selectedPkgs: Set<String>, selectedWebs: Set<String>, isComplete: Boolean = false, onPkgsChanged: (Set<String>) -> Unit, onWebsChanged: (Set<String>) -> Unit, onBack: () -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(BpAllowTab.APPS) }
    var webInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    var installedApps by remember { mutableStateOf<List<BpAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Async Loading - Fixed performance issues
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val dialerPkg = telecomManager?.defaultDialerPackage
            val smsPkg = Telephony.Sms.getDefaultSmsPackage(context)
            
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 || it.packageName == dialerPkg || it.packageName == smsPkg }
                .map { BpAppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
                .sortedBy { it.appName }
            
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoadingApps = false
            }
        }
    }
    
    val filteredApps = if (searchQuery.isEmpty()) installedApps else installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(BpGrayBg)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BpTealMid, BpTealDark))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Spacer(Modifier.width(8.dp))
                Column { Text("Allow List", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White); Text(if (isComplete) "Default apps fixed — শুধু এরাই কাজ করবে" else "Apps/sites বেছে নাও", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpTabBtn("📱 Apps (${selectedPkgs.size})", activeTab == BpAllowTab.APPS, Modifier.weight(1f)) { activeTab = BpAllowTab.APPS }
            BpTabBtn("🌐 Websites (${selectedWebs.size})", activeTab == BpAllowTab.WEBSITES, Modifier.weight(1f)) { activeTab = BpAllowTab.WEBSITES }
        }
        when (activeTab) {
            BpAllowTab.APPS -> {
                // Search Bar added
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = BpTextGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = BpTealMid) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BpTealMid,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))

                if (isLoadingApps) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BpTealMid, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Apps লোড হচ্ছে...", color = BpTextGray, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        items(filteredApps) { app ->
                            val selected = app.packageName in selectedPkgs; val isDefaultApp = app.packageName in BpC.DEFAULT_ALLOWED; val isLocked = isComplete || isDefaultApp
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (!isLocked) Modifier.clickable { onPkgsChanged(if (selected) selectedPkgs - app.packageName else selectedPkgs + app.packageName) } else Modifier), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected) BpTealMid.copy(alpha = 0.1f) else Color.White), elevation = CardDefaults.cardElevation(if (selected) 2.dp else 0.dp), border = if(selected) BorderStroke(1.dp, BpTealMid.copy(alpha=0.5f)) else BorderStroke(1.dp, Color.LightGray.copy(alpha=0.5f))) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BpAppIconImage(drawable = app.icon, modifier = Modifier.size(42.dp)); Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) { Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (isLocked && !selected) BpTextGray else BpTealDark); Text(app.packageName, fontSize = 11.sp, color = BpTextGray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    Icon(if (isLocked) (if (selected) Icons.Default.Lock else Icons.Default.LockOpen) else (if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked), null, tint = if (isLocked) (if (selected) BpTealMid else BpTextGray.copy(alpha = 0.4f)) else (if (selected) BpTealMid else BpTextGray), modifier = Modifier.size(if (isLocked) 20.dp else 24.dp))
                                }
                            }
                        }
                    }
                }
            }
            BpAllowTab.WEBSITES -> Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = webInput, onValueChange = { webInput = it }, label = { Text("Website (e.g. google.com)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealMid, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                    Button(onClick = { val site = webInput.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www."); if (site.isNotEmpty()) { onWebsChanged(selectedWebs + site); webInput = "" } }, modifier = Modifier.height(56.dp).align(Alignment.CenterVertically), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
                if (selectedWebs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌐", fontSize = 40.sp); Spacer(Modifier.height(8.dp)); Text("কোনো website add করা হয়নি", color = BpTextGray); Text("উপরে লিখে + চাপুন", fontSize = 12.sp, color = BpTextGray) } }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        items(selectedWebs.toList()) { site ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BpTealMid.copy(alpha = 0.1f)), border = BorderStroke(1.dp, BpTealMid.copy(0.3f))) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, null, tint = BpTealMid, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Text(site, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = BpTealDark)
                                    IconButton(onClick = { onWebsChanged(selectedWebs - site) }) { Icon(Icons.Default.Close, null, tint = BpRedAccent, modifier = Modifier.size(20.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFF060B14)).padding(16.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Icon(Icons.Default.Lock, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Text("START FOCUS SESSION", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpBreakDialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BpBreakDialog(onDismiss: () -> Unit, onStarted: () -> Unit, onStopped: () -> Unit) {
    val context = LocalContext.current; var tab by remember { mutableStateOf(0) }
    
    val endMs = context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).getLong(BpC.KEY_BREAK_END, 0L)
    val isBreakActive = endMs > System.currentTimeMillis()
    val isPomActive = PomPrefs.isActive(context)
    
    var tbDays by remember { mutableStateOf(0) }; var tbHours by remember { mutableStateOf(0) }; var tbMins by remember { mutableStateOf(25) }
    var pomSession by remember { mutableStateOf(25) }; var pomBreak by remember { mutableStateOf(5) }; var pomCount by remember { mutableStateOf(4) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.90f).padding(horizontal = 14.dp).background(Color(0xFF0F1724), RoundedCornerShape(28.dp))) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BpTealMid, BpTealDark)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onDismiss, Modifier.size(36.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }; Spacer(Modifier.width(10.dp)); Column { Text("Take a Break Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White); Text("Session শুরু করো", fontSize = 12.sp, color = Color.White.copy(0.7f)) } }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("☕  Break", "🍅  Pomodoro").forEachIndexed { i, label ->
                        Button(onClick = { tab = i }, Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (tab == i) BpTealMid else Color.White.copy(0.08f), contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(if (tab == i) 4.dp else 0.dp)) { Text(label, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    AnimatedContent(tab, label = "tab") { t -> when (t) { 0 -> BpBreakContent(tbDays, { tbDays = it }, tbHours, { tbHours = it }, tbMins, { tbMins = it }); 1 -> BpPomodoroContent(pomSession, { pomSession = it }, pomBreak, { pomBreak = it }, pomCount, { pomCount = it }) } }
                }
                Column(Modifier.padding(16.dp)) {
                    if (isBreakActive || isPomActive) {
                        OutlinedButton(
                            onClick = {
                                if (isPomActive) cancelPomodoroAlarms(context, PomPrefs.sessions(context))
                                PomPrefs.clear(context)
                                context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                    .putLong(BpC.KEY_BREAK_END, 0L).apply()
                                context.stopService(Intent(context, BpBlockingService::class.java))
                                onStopped()
                            },
                            Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(2.dp, Color(0xFFE53935))
                        ) { Icon(Icons.Default.Stop, null, tint = Color(0xFFE53935)); Spacer(Modifier.width(8.dp)); Text("Session বন্ধ করো", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                Toast.makeText(context, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (!bpHasUsageStatsPermission(context)) {
                                Toast.makeText(context, "Please enable Usage Access", Toast.LENGTH_SHORT).show()
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                return@Button
                            }
                            when (tab) {
                                0 -> {
                                    if (tbDays == 0 && tbHours == 0 && tbMins == 0) { Toast.makeText(context, "Duration সেট করুন!", Toast.LENGTH_SHORT).show(); return@Button }
                                    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, tbDays); add(java.util.Calendar.HOUR_OF_DAY, tbHours); add(java.util.Calendar.MINUTE, tbMins) }
                                    
                                    context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                        .putLong(BpC.KEY_BREAK_END, cal.timeInMillis)
                                        .putString(BpC.KEY_ALLOWED, BpC.DEFAULT_ALLOWED.joinToString(","))
                                        .putString(BpC.KEY_LOCK_MODE, BpC.LOCK_SELF)
                                        .putString(BpC.KEY_PARENT_PASS, "")
                                        .apply()

                                    val svcIntent = Intent(context, BpBlockingService::class.java)
                                        .putExtra(BpBlockingService.EXTRA_SHOW_NOW, true)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                                    else context.startService(svcIntent)
                                    onStarted()
                                }
                                1 -> {
                                    if (pomSession == 0 || pomBreak == 0 || pomCount == 0) { Toast.makeText(context, "Pomodoro parameters সেট করুন!", Toast.LENGTH_SHORT).show(); return@Button }
                                    val sessionMs = pomSession * 60_000L; val breakMs = pomBreak * 60_000L
                                    val totalMs = (pomCount.toLong() * sessionMs) + ((pomCount - 1).toLong() * breakMs)
                                    val endMs = System.currentTimeMillis() + totalMs
                                    
                                    context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                        .putLong(BpC.KEY_BREAK_END, endMs)
                                        .putString(BpC.KEY_ALLOWED, BpC.DEFAULT_ALLOWED.joinToString(","))
                                        .putString(BpC.KEY_LOCK_MODE, BpC.LOCK_SELF)
                                        .putString(BpC.KEY_PARENT_PASS, "")
                                        .apply()
                                        
                                    PomPrefs.save(context, pomCount, sessionMs, breakMs, endMs)
                                    schedulePomodoroAlarms(context, pomCount, sessionMs, breakMs)
                                    
                                    val svcIntent = Intent(context, BpBlockingService::class.java)
                                        .putExtra(BpBlockingService.EXTRA_SHOW_NOW, true)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                                    else context.startService(svcIntent)
                                    onStarted()
                                }
                            }
                        },
                        Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (tab == 1) Color(0xFFE65100) else BpTealMid)
                    ) { Icon(if (tab == 1) Icons.Default.Timer else Icons.Default.Coffee, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(if (tab == 1) "Pomodoro শুরু করো" else "Break শুরু করো", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun BpBreakContent(days: Int, onDays: (Int) -> Unit, hours: Int, onHours: (Int) -> Unit, mins: Int, onMins: (Int) -> Unit) {
    val display = buildString { if (days > 0) append("${days}d "); if (hours > 0) append("${hours}h "); if (mins > 0) append("${mins}m"); if (isEmpty()) append("0m") }.trim()
    Column {
        Box(Modifier.fillMaxWidth().background(BpTealMid.copy(0.15f), RoundedCornerShape(16.dp)).border(1.dp, BpTealAccent.copy(0.4f), RoundedCornerShape(16.dp)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) { Text(display, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = BpTealAccent, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(16.dp)); Text("Quick Select", fontSize = 12.sp, color = BpTextGray, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(0,0,5,"5m"), listOf(0,0,15,"15m"), listOf(0,0,25,"25m"), listOf(0,0,30,"30m"), listOf(0,1,0,"1h")).forEach { preset ->
                val d = preset[0] as Int; val h = preset[1] as Int; val m = preset[2] as Int; val lbl = preset[3] as String; val sel = days==d && hours==h && mins==m
                Box(Modifier.weight(1f).background(if (sel) BpTealMid else Color.White.copy(0.07f), RoundedCornerShape(10.dp)).border(1.dp, if (sel) BpTealAccent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onDays(d); onHours(h); onMins(m) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else BpTextGray) }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Custom Duration", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        BpSlider("Days", days, 0, 7) { onDays(it) }; BpSlider("Hours", hours, 0, 23) { onHours(it) }; BpSlider("Minutes", mins, 0, 59) { onMins(it) }
    }
}

@Composable
private fun BpPomodoroContent(sessionMins: Int, onSession: (Int) -> Unit, breakMins: Int, onBreak: (Int) -> Unit, sessions: Int, onSessions: (Int) -> Unit) {
    val total = (sessionMins * sessions) + (breakMins * (sessions - 1))
    Column {
        Box(Modifier.fillMaxWidth().background(Color(0xFFE65100).copy(0.15f), RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFE65100).copy(0.4f), RoundedCornerShape(16.dp)).padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("🍅" to "${sessions}x Sessions", "⏱" to "${sessionMins}m Focus", "☕" to "${breakMins}m Break", "🕐" to "${total}m Total").forEach { (emoji, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(emoji, fontSize = 20.sp); Text(label, fontSize = 12.sp, color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Preset", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(25,5,4,"Classic"), listOf(50,10,3,"Deep"), listOf(15,3,6,"Short")).forEach { preset ->
                val s = preset[0] as Int; val b = preset[1] as Int; val n = preset[2] as Int; val lbl = preset[3] as String; val sel = sessionMins==s && breakMins==b && sessions==n
                Box(Modifier.weight(1f).background(if (sel) Color(0xFFE65100) else Color.White.copy(0.07f), RoundedCornerShape(10.dp)).clickable { onSession(s); onBreak(b); onSessions(n) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White); Text("${s}m/${b}m", fontSize = 10.sp, color = Color.White.copy(0.7f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Custom", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        BpSlider("Focus(m)", sessionMins, 5, 90) { onSession(it) }; BpSlider("Break(m)", breakMins, 1, 30) { onBreak(it) }; BpSlider("Sessions", sessions, 1, 12) { onSessions(it) }
    }
}

@Composable
private fun BpSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = BpTextGray, modifier = Modifier.width(76.dp))
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(), steps = (max - min - 1).coerceAtLeast(0), modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = BpTealMid, activeTrackColor = BpTealAccent))
        Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun BpTabBtn(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) BpTealMid else Color.White, contentColor = if (selected) Color.White else BpTextDark), elevation = ButtonDefaults.buttonElevation(if (selected) 4.dp else 0.dp), border = if(!selected) BorderStroke(1.dp, Color.LightGray.copy(0.5f)) else null) { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
}

// ═════════════════════════════════════════════════════════════════════════════
//  POMODORO ALARM RECEIVER
//  FIX: manifest declared this receiver but the class didn't exist anywhere in
//  the codebase, so the OS would throw ClassNotFoundException and crash the app
//  the instant a scheduled Pomodoro alarm fired on ANY Android version — the
//  receiver simply posts a "break time" notification.
// ═════════════════════════════════════════════════════════════════════════════
class PomodoroAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "pomodoro_alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Pomodoro Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Pomodoro Session শেষ 🍅")
            .setContentText("বিরতি নিন বা পরের সেশন শুরু করুন")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(4000 + intent.getIntExtra("index", 0), notif)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  BLOCKING SERVICE (From take_rest.kt)
// ═════════════════════════════════════════════════════════════════════════════
class BpBlockingService : Service() {

    companion object {
        const val EXTRA_SHOW_NOW = "show_overlay_immediately"
    }

    private var thread: Thread? = null
    @Volatile private var running = false
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    @Volatile private var isOverlayShowing = false

    private var remainingTimeTv: TextView? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateRemainingTimeDisplay()
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(BpC.NOTIF_ID, buildNotification())

        // FIX: Session start করার সাথে সাথেই overlay দেখানো হচ্ছে।
        // আগে শুধু trackLoop() একটা blocked app detect করলে overlay আসতো,
        // যেটার জন্য কয়েকশো ms (CONFIRM_THRESHOLD * 200ms) সময় লাগে।
        // ওই gap-টাতেই নিচের Activity-র সাদা স্ক্রিন দেখা যেত।
        if (intent?.getBooleanExtra(EXTRA_SHOW_NOW, false) == true && !isOverlayShowing) {
            Handler(Looper.getMainLooper()).post { showOverlay() }
        }

        if (!running) {
            running = true
            thread = Thread(::trackLoop).also { it.start() }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(BpC.CHANNEL_ID, "Break Blocker", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, BpC.CHANNEL_ID)
            .setContentTitle("Focus Session চলছে 📵")
            .setContentText("Apps blocked — কাজে মন দাও")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private fun isBreakActive(): Boolean {
        val endTime = getSharedPreferences(BpC.PREFS, MODE_PRIVATE).getLong(BpC.KEY_BREAK_END, 0L)
        return System.currentTimeMillis() < endTime
    }

    private fun getRemainingTimeString(): String {
        val endTime = getSharedPreferences(BpC.PREFS, MODE_PRIVATE).getLong(BpC.KEY_BREAK_END, 0L)
        val remaining = maxOf(0L, endTime - System.currentTimeMillis())
        val totalSecs = remaining / 1000
        val days = totalSecs / 86400
        val hours = (totalSecs % 86400) / 3600
        val mins  = (totalSecs % 3600) / 60
        val secs  = totalSecs % 60
        return when {
            days > 0 -> "%02dd %02dh".format(days, hours)
            hours > 0 -> "%02d:%02d:%02d".format(hours, mins, secs)
            else -> "%02d:%02d".format(mins, secs)
        }
    }

    private fun updateRemainingTimeDisplay() {
        remainingTimeTv?.text = getRemainingTimeString()
    }

    private fun isAllowed(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in BpC.DEFAULT_ALLOWED) return true
        val raw = getSharedPreferences(BpC.PREFS, MODE_PRIVATE).getString(BpC.KEY_ALLOWED, "") ?: ""
        val allowed = raw.split(",").filter { it.isNotEmpty() }.toSet()
        return pkg in allowed
    }

    private fun getForegroundPackage(usm: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000, now)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    private var blockedConfirmCount = 0
    private var allowedConfirmCount = 0
    private val CONFIRM_THRESHOLD = 2 

    private fun trackLoop() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        while (running) {
            try {
                if (!isBreakActive()) {
                    getSharedPreferences(BpC.PREFS, MODE_PRIVATE).edit()
                        .putLong(BpC.KEY_BREAK_END, 0L)
                        .apply()

                    Handler(Looper.getMainLooper()).post {
                        timerHandler.removeCallbacks(timerRunnable)
                        removeOverlay()
                        stopForeground(true)
                        stopSelf()
                    }
                    break
                }

                val pkg = getForegroundPackage(usm)
                if (pkg != null) {
                    val blocked = !isAllowed(pkg)

                    if (blocked) {
                        blockedConfirmCount++
                        allowedConfirmCount = 0
                        
                        if (blockedConfirmCount >= CONFIRM_THRESHOLD && !isOverlayShowing) {
                            Handler(Looper.getMainLooper()).post { showOverlay() }
                        }
                    } else {
                        allowedConfirmCount++
                        blockedConfirmCount = 0
                        
                        if (allowedConfirmCount >= 1 && isOverlayShowing) {
                            Handler(Looper.getMainLooper()).post { removeOverlay() }
                        }
                    }
                }
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {
                Thread.sleep(300)
            }
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing || !Settings.canDrawOverlays(this)) return

        val view = buildOverlayUI()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // FIX BUG 1: OPAQUE থেকে TRANSLUCENT করা হয়েছে এবং Hardware Acceleration দেয়া হয়েছে
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags, PixelFormat.TRANSLUCENT // TRANSLUCENT = No White Flash
        )
        params.windowAnimations = android.R.style.Animation_Toast // Smooth fade animation

        windowManager.addView(view, params)
        overlayView = view
        isOverlayShowing = true

        timerHandler.post(timerRunnable)
    }

    private fun removeOverlay() {
        if (!isOverlayShowing) return
        timerHandler.removeCallbacks(timerRunnable)
        remainingTimeTv = null
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        isOverlayShowing = false
    }

    private fun buildOverlayUI(): View {
        // FIX BUG 1: Theme_Black_NoTitleBar_Fullscreen ব্যবহার করায় সাদা স্ক্রিন আসবে না
        val context = ContextThemeWrapper(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val scroll = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.parseColor("#060B14"))
            isVerticalScrollBarEnabled = false
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(52), dp(20), dp(48))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(root)

        val statusPill = object : LinearLayout(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#0E1E35") }
            private val r = RectF()
            override fun dispatchDraw(c: Canvas) {
                r.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(r, height / 2f, height / 2f, p)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(28) }
        }
        
        val dotView = object : View(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#2DD4AA") }
            override fun onDraw(c: Canvas) { c.drawCircle(width/2f, height/2f, width/2f, p) }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                .also { it.marginEnd = dp(8); it.gravity = Gravity.CENTER_VERTICAL }
            setWillNotDraw(false)
        }
        statusPill.addView(dotView)
        statusPill.addView(blockTv(context, "FOCUS MODE  ACTIVE", 11f, Typeface.BOLD, android.graphics.Color.parseColor("#2DD4AA"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        root.addView(statusPill)

        val clockTv = blockTv(context, java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            72f, Typeface.BOLD, android.graphics.Color.WHITE, bot = dp(4), grav = Gravity.CENTER).apply {
            letterSpacing = -0.02f
        }
        val dateTv = blockTv(context,
            java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date()),
            13f, color = android.graphics.Color.parseColor("#3D5068"), bot = dp(36), grav = Gravity.CENTER)

        timerHandler.post(object : Runnable {
            override fun run() {
                clockTv.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                dateTv.text  = java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date())
                timerHandler.postDelayed(this, 60_000)
            }
        })
        root.addView(clockTv)
        root.addView(dateTv)

        val timerCard = object : FrameLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#1A3A5C"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(24).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            setWillNotDraw(false)
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(24) }
        }

        val timerInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        labelRow.addView(blockTv(context, "⏳", 14f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = dp(6) }
        })
        labelRow.addView(blockTv(context, "REMAINING TIME", 11f, Typeface.BOLD, android.graphics.Color.parseColor("#2D4A6B"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        timerInner.addView(labelRow)

        val timeTv = TextView(context).apply {
            text = getRemainingTimeString()
            textSize = 52f
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL), Typeface.NORMAL)
            setTextColor(android.graphics.Color.parseColor("#E8F4FF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(6) }
        }
        remainingTimeTv = timeTv
        timerInner.addView(timeTv)

        timerInner.addView(object : View(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(0f, 0f, 500f, 0f,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#1A4A7A"), android.graphics.Color.TRANSPARENT),
                    null, android.graphics.Shader.TileMode.CLAMP)
            }
            override fun onDraw(c: Canvas) { c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p) }
        }.apply {
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                .also { it.setMargins(dp(24), dp(4), dp(24), dp(10)) }
        })

        timerInner.addView(blockTv(context, "session শেষ হওয়ার আগ পর্যন্ত apps blocked", 11f,
            color = android.graphics.Color.parseColor("#2D4A6B"), grav = Gravity.CENTER))
        timerCard.addView(timerInner)
        root.addView(timerCard)

        root.addView(blockTv(context, "ALLOWED APPS", 10f, Typeface.BOLD,
            android.graphics.Color.parseColor("#2D4A6B"), bot = dp(12), grav = Gravity.CENTER).apply {
            letterSpacing = 0.15f
        })

        val appsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(appsContainer)
        loadAllowedAppsToView(context, appsContainer)

        addUnlockButtonToOverlay(context, root)

        return scroll
    }

    private fun addUnlockButtonToOverlay(context: Context, root: LinearLayout) {
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
        })

        val prefs    = getSharedPreferences(BpC.PREFS, MODE_PRIVATE)
        val lockMode = prefs.getString(BpC.KEY_LOCK_MODE, BpC.LOCK_SELF) ?: BpC.LOCK_SELF
        val lockHint = when (lockMode) {
            BpC.LOCK_PARENTS  -> "🔐  Parents password দিয়ে unlock"
            BpC.LOCK_LONGTEXT -> "✍️  Long text টাইপ করে unlock"
            else            -> "🛡️  Strict Self Control Mode Active"
        }
        root.addView(blockTv(context, lockHint, 11f, color = android.graphics.Color.parseColor("#243550"), bot = dp(10), grav = Gravity.CENTER))

        val unlockBtn = object : LinearLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#1A3A5C"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(14).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val lockIcon = blockTv(context, if(lockMode == BpC.LOCK_SELF) "🚫" else "🔓", 18f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = dp(10) }
        }
        val unlockLabel = blockTv(context, if(lockMode == BpC.LOCK_SELF) "No Early Unlock" else "Unlock", 14f, Typeface.NORMAL, android.graphics.Color.parseColor("#3D6080"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        unlockBtn.addView(lockIcon)
        unlockBtn.addView(unlockLabel)
        unlockBtn.setOnClickListener { showUnlockDialog(context) }
        root.addView(unlockBtn)
    }

    private fun showUnlockDialog(context: Context) {
        val prefs    = getSharedPreferences(BpC.PREFS, MODE_PRIVATE)
        val lockMode = prefs.getString(BpC.KEY_LOCK_MODE, BpC.LOCK_SELF) ?: BpC.LOCK_SELF

        when (lockMode) {
            BpC.LOCK_PARENTS -> {
                showParentsUnlockDialog(context)
            }
            BpC.LOCK_LONGTEXT -> {
                showLongTextUnlockOverlay(context)
            }
            else -> {
                // FIX BUG 2: Self Control mode এ early unlock পুরোপুরি ব্লক করা হয়েছে
                showSelfControlUnlockOverlay(context)
            }
        }
    }

    // FIX BUG 2: Strict Self Control Overlay - No early unlock button at all
    private fun showSelfControlUnlockOverlay(context: Context) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#E60D1117"))
            setPadding(dp(28), dp(80), dp(28), dp(40))
            gravity = Gravity.CENTER_VERTICAL
        }

        var selfUnlockOverlay: android.view.View? = root

        fun dismiss() {
            selfUnlockOverlay?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            selfUnlockOverlay = null
        }

        root.addView(blockTv(context, "🚫", 60f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        })

        root.addView(blockTv(context, "Strict Self Control Active!", 22f, Typeface.BOLD,
            android.graphics.Color.WHITE, bot = dp(8), grav = Gravity.CENTER))

        val remaining = getRemainingTimeString()
        root.addView(blockTv(context, "সময় শেষ না হওয়া পর্যন্ত কোনোভাবেই আনলক করা যাবে না।\n\nএখনো $remaining বাকি আছে।",
            15f, color = android.graphics.Color.parseColor("#8B949E"), bot = dp(40), grav = Gravity.CENTER).apply {
            setLineSpacing(0f, 1.4f)
        })

        val backBtn = blockTv(context, "← ফিরে যাও এবং কাজে মন দাও", 16f, Typeface.BOLD,
            color = android.graphics.Color.parseColor("#2DD4AA"), bot = 0, grav = Gravity.CENTER)
        backBtn.setPadding(dp(20), dp(16), dp(20), dp(16))
        backBtn.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#161B22"))
            cornerRadius = dp(14).toFloat()
        }
        backBtn.setOnClickListener { dismiss() }
        root.addView(backBtn)

        windowManager.addView(root, params)
    }

    private fun showParentsUnlockDialog(context: Context) {
        val savedPass = getSharedPreferences(BpC.PREFS, MODE_PRIVATE).getString(BpC.KEY_PARENT_PASS, "") ?: ""

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val overlayRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#E60D1117"))
            setPadding(dp(28), dp(80), dp(28), dp(40))
            gravity = Gravity.CENTER_VERTICAL
        }

        var overlayView: android.view.View? = overlayRoot

        fun dismiss() {
            overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            overlayView = null
        }

        overlayRoot.addView(blockTv(context, "🔐", 52f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        })

        overlayRoot.addView(blockTv(context, "Parents Unlock", 22f, Typeface.BOLD, android.graphics.Color.WHITE, bot = dp(8), grav = Gravity.CENTER))
        overlayRoot.addView(blockTv(context, "Parents এর password দিয়ে unlock করো", 13f, color = android.graphics.Color.parseColor("#6E7681"), bot = dp(32), grav = Gravity.CENTER))

        val inputEt = android.widget.EditText(context).apply {
            hint = "Password লিখুন"
            setHintTextColor(android.graphics.Color.parseColor("#484F58"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundColor(android.graphics.Color.parseColor("#161B22"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }
        overlayRoot.addView(inputEt)

        val errorTv = blockTv(context, "❌  ভুল পাসওয়ার্ড! আবার চেষ্টা করো।", 13f,
            color = android.graphics.Color.parseColor("#F85149"), bot = dp(16)).apply {
            visibility = android.view.View.GONE
        }
        overlayRoot.addView(errorTv)

        val unlockBtn = object : TextView(context) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "🔓  Unlock করো"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }
        unlockBtn.setOnClickListener {
            val entered = inputEt.text.toString()
            if (entered == savedPass) {
                dismiss()
                doUnlock()
            } else {
                errorTv.visibility = android.view.View.VISIBLE
                inputEt.text.clear()
            }
        }
        overlayRoot.addView(unlockBtn)

        val backBtn = blockTv(context, "← ফিরে যাও", 14f, color = android.graphics.Color.parseColor("#484F58"), bot = 0, grav = Gravity.CENTER)
        backBtn.setOnClickListener { dismiss() }
        overlayRoot.addView(backBtn)

        windowManager.addView(overlayRoot, params)
    }

    private fun showLongTextUnlockOverlay(context: Context) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val unlockRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#E60D1117"))
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }

        unlockRoot.addView(blockTv(context, "Unlock করতে নিচের text হুবহু টাইপ করো:", 14f, Typeface.BOLD, android.graphics.Color.parseColor("#4DD0E1"), bot = dp(12)))

        val targetScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120))
                .also { it.bottomMargin = dp(16) }
            setBackgroundColor(android.graphics.Color.parseColor("#161B22"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        targetScroll.addView(blockTv(context, BpC.LONG_UNLOCK_TEXT, 11f, color = android.graphics.Color.parseColor("#8B949E")))
        unlockRoot.addView(targetScroll)

        val inputEt = android.widget.EditText(context).apply {
            hint = "এখানে টাইপ করুন..."
            setHintTextColor(android.graphics.Color.parseColor("#546E7A"))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#161B22"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 4
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(16) }
        }
        unlockRoot.addView(inputEt)

        var unlockOverlayView: android.view.View? = unlockRoot
        windowManager.addView(unlockRoot, params)

        val verifyBtn = object : TextView(context) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "Verify & Unlock"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(10) }
        }
        verifyBtn.setOnClickListener {
            if (inputEt.text.toString().trim() == BpC.LONG_UNLOCK_TEXT.trim()) {
                unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
                unlockOverlayView = null
                doUnlock()
            } else {
                Toast.makeText(context, "লেখা মিলছে না! হুবহু টাইপ করুন।", Toast.LENGTH_SHORT).show()
            }
        }
        unlockRoot.addView(verifyBtn)

        val backBtn = blockTv(context, "← ফিরে যাও", 14f, color = android.graphics.Color.parseColor("#546E7A"), bot = 0, grav = Gravity.CENTER)
        backBtn.setOnClickListener {
            unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            unlockOverlayView = null
        }
        unlockRoot.addView(backBtn)
    }

    private fun doUnlock() {
        getSharedPreferences(BpC.PREFS, MODE_PRIVATE).edit()
            .putLong(BpC.KEY_BREAK_END, 0L)
            .putString(BpC.KEY_LOCK_MODE, BpC.LOCK_SELF)
            .putString(BpC.KEY_PARENT_PASS, "")
            .apply()
        removeOverlay()
        stopForeground(true)
        stopSelf()
    }

    private fun loadAllowedAppsToView(context: Context, container: LinearLayout) {
        val raw = getSharedPreferences(BpC.PREFS, MODE_PRIVATE).getString(BpC.KEY_ALLOWED, "") ?: ""
        val pkgs = (raw.split(",").filter { it.isNotEmpty() } + BpC.DEFAULT_ALLOWED).distinct()
        val pm = packageManager

        val allPkgs = pkgs.filter { pkg ->
            try { pm.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }
        }

        if (allPkgs.isEmpty()) {
            container.addView(blockTv(context, "কোনো app allow করা হয়নি", 13f, color = android.graphics.Color.parseColor("#5C6BC0"), bot = 0, grav = Gravity.CENTER))
            return
        }

        var row: LinearLayout? = null
        allPkgs.forEachIndexed { idx, pkg ->
            if (idx % 2 == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .also { it.setMargins(0, 0, 0, dp(10)) }
                }
                container.addView(row)
            }

            val chip = buildAppChip(context, pkg, pm)
            chip.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(if (idx % 2 == 0) 0 else dp(8), 0, 0, 0)
            }
            chip.setOnClickListener {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    removeOverlay()
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "App খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            }
            row?.addView(chip)
        }

        if (allPkgs.size % 2 != 0) {
            row?.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(dp(8), 0, 0, 0) }
            })
        }
    }

    private fun buildAppChip(context: Context, pkg: String, pm: PackageManager): LinearLayout {
        val chip = object : LinearLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#152240"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(16).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(16), dp(12))
            setWillNotDraw(false)
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginEnd = dp(10) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try { iconView.setImageDrawable(pm.getApplicationIcon(pkg)) }
        catch (_: Exception) { iconView.setImageDrawable(pm.defaultActivityIcon) }

        val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    catch (_: Exception) { pkg.substringAfterLast(".") }

        val nameView = TextView(context).apply {
            text = label
            textSize = 12f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(android.graphics.Color.parseColor("#8BAFD4"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        chip.addView(iconView)
        chip.addView(nameView)
        return chip
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun blockTv(
        context: Context, text: String, size: Float,
        style: Int = Typeface.NORMAL, color: Int = android.graphics.Color.WHITE,
        bot: Int = 0, grav: Int = Gravity.NO_GRAVITY
    ) = TextView(context).apply {
        this.text = text; textSize = size
        setTypeface(null, style); setTextColor(color)
        setPadding(0, 0, 0, bot)
        if (grav != Gravity.NO_GRAVITY) gravity = grav
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        timerHandler.removeCallbacks(timerRunnable)
        Handler(Looper.getMainLooper()).post { removeOverlay() }
    }
}
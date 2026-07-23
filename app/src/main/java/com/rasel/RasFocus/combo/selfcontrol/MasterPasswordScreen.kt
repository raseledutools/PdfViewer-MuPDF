package com.rasel.RasFocus.combo.selfcontrol

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rasel.RasFocus.ui.theme.SoftWhite

// ─── Colors ────────────────────────────────────────────────────────────────
private val MpBg          = Color(0xFF0D0D1A)
private val MpCard        = Color(0xFF14142B)
private val MpBorder      = Color(0xFF2A2A45)
private val MpTeal        = Color(0xFF14C3B2)
private val MpBlue        = Color(0xFF4A6FE3)
private val MpPurple      = Color(0xFF9B5DE5)
private val MpOrange      = Color(0xFFFF8C42)
private val MpRed         = Color(0xFFFF3B30)
private val MpGreen       = Color(0xFF30D158)
private val MpTextDim     = SoftWhite.copy(alpha = 0.5f)

// ─── SharedPreferences keys ─────────────────────────────────────────────────
private const val PREFS_MASTER = "master_password_prefs"
private const val KEY_MASTER_HASH = "master_pass_hash"
private const val KEY_SELF_PASS   = "section_self_pass"
private const val KEY_PARENT_PASS = "section_parent_pass"
private const val KEY_LONG_TEXT   = "section_long_text"
private const val KEY_SELF_EN     = "section_self_enabled"
private const val KEY_PARENT_EN   = "section_parent_enabled"
private const val KEY_TEXT_EN     = "section_text_enabled"

// ─── Simple hash (avoids storing plain text) ────────────────────────────────
private fun simpleHash(input: String): String =
    input.hashCode().toUInt().toString(16)

// ─── Data model ─────────────────────────────────────────────────────────────
data class MasterPasswordState(
    val masterSet: Boolean = false,
    val selfEnabled: Boolean = false,
    val parentEnabled: Boolean = false,
    val textEnabled: Boolean = false
)

fun loadMasterState(context: Context): MasterPasswordState {
    val p = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    return MasterPasswordState(
        masterSet     = p.getString(KEY_MASTER_HASH, "").orEmpty().isNotEmpty(),
        selfEnabled   = p.getBoolean(KEY_SELF_EN, false),
        parentEnabled = p.getBoolean(KEY_PARENT_EN, false),
        textEnabled   = p.getBoolean(KEY_TEXT_EN, false)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MASTER UNLOCK GATE — app open হলে এই screen দেখাবে (যদি set থাকে)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MasterPasswordGate(
    context: Context = LocalContext.current,
    onUnlocked: () -> Unit
) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    val masterHash = prefs.getString(KEY_MASTER_HASH, "").orEmpty()

    // Master password set না থাকলে সরাসরি unlock
    if (masterHash.isEmpty()) {
        LaunchedEffect(Unit) { onUnlocked() }
        return
    }

    var input by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF08504B), MpBg))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        Brush.radialGradient(listOf(MpTeal.copy(alpha = 0.3f), Color.Transparent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MpTeal, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("RasFocus", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SoftWhite)
            Text("Enter Master Password to continue", fontSize = 14.sp, color = MpTextDim)

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; showError = false },
                label = { Text("Master Password", color = MpTextDim) },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null, tint = MpTeal
                        )
                    }
                },
                isError = showError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite,
                    focusedBorderColor = MpTeal,
                    unfocusedBorderColor = MpBorder,
                    errorBorderColor = MpRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (showError) {
                Spacer(Modifier.height(8.dp))
                Text("❌ Incorrect password", color = MpRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (simpleHash(input) == masterHash) {
                        onUnlocked()
                    } else {
                        showError = true
                        input = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MpTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF032220))
                Spacer(Modifier.width(10.dp))
                Text("Unlock App", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF032220))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN — Master Password Settings (3 sections)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MasterPasswordScreen() {
    val context = LocalContext.current
    val prefs   = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)

    var state by remember { mutableStateOf(loadMasterState(context)) }

    // Dialog states
    var showSetMasterDialog   by remember { mutableStateOf(false) }
    var showChangeMasterDialog by remember { mutableStateOf(false) }
    var showSelfDialog        by remember { mutableStateOf(false) }
    var showParentDialog      by remember { mutableStateOf(false) }
    var showTextDialog        by remember { mutableStateOf(false) }
    var showVerifyForAction   by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Verify master before sensitive actions
    fun requireMaster(action: () -> Unit) {
        val hash = prefs.getString(KEY_MASTER_HASH, "").orEmpty()
        if (hash.isEmpty()) action() else showVerifyForAction = action
    }

    if (showVerifyForAction != null) {
        MasterVerifyDialog(
            context = context,
            onVerified = {
                showVerifyForAction?.invoke()
                showVerifyForAction = null
            },
            onCancel = { showVerifyForAction = null }
        )
    }
    if (showSetMasterDialog)    SetMasterPasswordDialog(context, onDone = { state = loadMasterState(context); showSetMasterDialog = false })
    if (showChangeMasterDialog) ChangeMasterPasswordDialog(context, onDone = { state = loadMasterState(context); showChangeMasterDialog = false })
    if (showSelfDialog)         SectionPasswordDialog(context, "Self Control", KEY_SELF_PASS, KEY_SELF_EN, onDone = { state = loadMasterState(context); showSelfDialog = false })
    if (showParentDialog)       SectionPasswordDialog(context, "Parental Control", KEY_PARENT_PASS, KEY_PARENT_EN, onDone = { state = loadMasterState(context); showParentDialog = false })
    if (showTextDialog)         LongTextSectionDialog(context, onDone = { state = loadMasterState(context); showTextDialog = false })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MpBg)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top header ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF08504B), MpBg)),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(SoftWhite.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MpTeal, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Master Password", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = SoftWhite)
                        Text("Professional security system", fontSize = 13.sp, color = MpTextDim)
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Master status card
                MasterStatusBanner(
                    isSet = state.masterSet,
                    onSetClick = { showSetMasterDialog = true },
                    onChangeClick = { requireMaster { showChangeMasterDialog = true } }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Section label ────────────────────────────────────────────────
        Text(
            "Security Sections",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = MpTextDim,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))

        // ── SECTION 1: Self Control ──────────────────────────────────────
        SectionCard(
            icon       = Icons.Default.SelfImprovement,
            iconColor  = MpTeal,
            gradient   = listOf(Color(0xFF032220), Color(0xFF0A3D38)),
            title      = "Self Control",
            subtitle   = "নিজের জন্য lock — শুধু তুমি নিজেই unlock করতে পারবে",
            badge      = if (state.selfEnabled) "ACTIVE" else "OFF",
            badgeColor = if (state.selfEnabled) MpGreen else MpTextDim,
            enabled    = state.selfEnabled,
            onConfigure = {
                if (state.masterSet) requireMaster { showSelfDialog = true }
                else showSelfDialog = true
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── SECTION 2: Parental Control ──────────────────────────────────
        SectionCard(
            icon       = Icons.Default.FamilyRestroom,
            iconColor  = MpBlue,
            gradient   = listOf(Color(0xFF0A1628), Color(0xFF122040)),
            title      = "Parental Control",
            subtitle   = "Parents এর জন্য — PIN দিয়ে lock, শুধু parent unlock করতে পারবে",
            badge      = if (state.parentEnabled) "ACTIVE" else "OFF",
            badgeColor = if (state.parentEnabled) MpGreen else MpTextDim,
            enabled    = state.parentEnabled,
            onConfigure = {
                if (state.masterSet) requireMaster { showParentDialog = true }
                else showParentDialog = true
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── SECTION 3: Long Text ──────────────────────────────────────────
        SectionCard(
            icon       = Icons.Default.EditNote,
            iconColor  = MpPurple,
            gradient   = listOf(Color(0xFF1A0A2E), Color(0xFF2A1048)),
            title      = "Long Text Lock",
            subtitle   = "নির্দিষ্ট text টাইপ করলেই unlock হবে — distraction resistance",
            badge      = if (state.textEnabled) "ACTIVE" else "OFF",
            badgeColor = if (state.textEnabled) MpGreen else MpTextDim,
            enabled    = state.textEnabled,
            onConfigure = {
                if (state.masterSet) requireMaster { showTextDialog = true }
                else showTextDialog = true
            }
        )

        Spacer(Modifier.height(24.dp))

        // ── Info card ─────────────────────────────────────────────────────
        InfoCard()

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MasterStatusBanner(isSet: Boolean, onSetClick: () -> Unit, onChangeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSet) MpTeal.copy(alpha = 0.12f) else MpRed.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, if (isSet) MpTeal.copy(alpha = 0.3f) else MpRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isSet) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isSet) MpTeal else MpRed,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isSet) "Master Password Set ✓" else "Master Password Not Set",
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (isSet) MpTeal else MpRed
            )
            Text(
                if (isSet) "App খুলতে password লাগবে" else "Set করলে app এ ঢুকতে password লাগবে",
                fontSize = 12.sp, color = MpTextDim
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = if (isSet) onChangeClick else onSetClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSet) MpTeal.copy(alpha = 0.2f) else MpTeal
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                if (isSet) "Change" else "Set Now",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (isSet) MpTeal else Color(0xFF032220)
            )
        }
    }
}

@Composable
fun SectionCard(
    icon: ImageVector,
    iconColor: Color,
    gradient: List<Color>,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    enabled: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { onConfigure() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (enabled) iconColor.copy(alpha = 0.4f) else MpBorder),
        colors = CardDefaults.cardColors(containerColor = MpCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradient))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(13.dp))
                            .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SoftWhite)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
                                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(badge, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                    color = badgeColor, letterSpacing = 0.8.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, fontSize = 12.sp, color = MpTextDim, lineHeight = 16.sp)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = iconColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Active indicator bar
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(listOf(iconColor, iconColor.copy(alpha = 0f))),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MpOrange.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MpOrange.copy(alpha = 0.08f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text("⚠️", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Important", fontWeight = FontWeight.Bold, color = MpOrange, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Master Password ভুলে গেলে recover করা possible না। " +
                    "কোনো safe জায়গায় note করে রাখো। " +
                    "Section passwords আলাদাভাবে set করা যাবে master password ছাড়াও।",
                    fontSize = 12.sp, color = MpTextDim, lineHeight = 18.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MasterVerifyDialog(context: Context, onVerified: () -> Unit, onCancel: () -> Unit) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    val masterHash = prefs.getString(KEY_MASTER_HASH, "").orEmpty()
    var input by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MpCard),
            border = BorderStroke(1.dp, MpBorder)
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = MpTeal, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Master Password Required", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SoftWhite)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = false },
                    label = { Text("Enter master password", color = MpTextDim) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MpTeal)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = MpTeal, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) { Spacer(Modifier.height(6.dp)); Text("Incorrect password", color = MpRed, fontSize = 12.sp) }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MpBorder)
                    ) { Text("Cancel", color = MpTextDim) }
                    Button(
                        onClick = {
                            if (simpleHash(input) == masterHash) onVerified()
                            else { error = true; input = "" }
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MpTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Verify", fontWeight = FontWeight.Bold, color = Color(0xFF032220)) }
                }
            }
        }
    }
}

@Composable
fun SetMasterPasswordDialog(context: Context, onDone: () -> Unit) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MpCard),
                border = BorderStroke(1.dp, MpTeal.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = MpTeal, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Set Master Password", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SoftWhite)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("এই password দিয়ে app খুলতে হবে", fontSize = 13.sp, color = MpTextDim)
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; error = "" },
                        label = { Text("New Master Password", color = MpTextDim) },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MpTeal)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                            focusedBorderColor = MpTeal, unfocusedBorderColor = MpBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = "" },
                        label = { Text("Confirm Password", color = MpTextDim) },
                        singleLine = true,
                        isError = error.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                            focusedBorderColor = MpTeal, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = MpRed, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            when {
                                pass.length < 4  -> error = "Password must be at least 4 characters"
                                pass != confirm  -> error = "Passwords don't match"
                                else -> {
                                    prefs.edit().putString(KEY_MASTER_HASH, simpleHash(pass)).apply()
                                    Toast.makeText(context, "Master password set! ✓", Toast.LENGTH_SHORT).show()
                                    onDone()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MpTeal),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF032220))
                        Spacer(Modifier.width(8.dp))
                        Text("Set Master Password", fontWeight = FontWeight.Bold, color = Color(0xFF032220))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MpBorder)
                    ) { Text("Cancel", color = MpTextDim) }
                }
            }
        }
    }
}

@Composable
fun ChangeMasterPasswordDialog(context: Context, onDone: () -> Unit) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MpCard),
                border = BorderStroke(1.dp, MpBlue.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockReset, null, tint = MpBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Change Master Password", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SoftWhite)
                    }
                    Spacer(Modifier.height(20.dp))

                    listOf(
                        Triple("Current Password", oldPass) { v: String -> oldPass = v },
                        Triple("New Password", newPass) { v: String -> newPass = v },
                        Triple("Confirm New Password", confirm) { v: String -> confirm = v }
                    ).forEach { (label, value, onChange) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onChange(it); error = "" },
                            label = { Text(label, color = MpTextDim) },
                            singleLine = true,
                            isError = error.isNotEmpty(),
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                                focusedBorderColor = MpBlue, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showPass = !showPass }) {
                        Checkbox(checked = showPass, onCheckedChange = { showPass = it },
                            colors = CheckboxDefaults.colors(checkedColor = MpBlue))
                        Text("Show passwords", color = MpTextDim, fontSize = 13.sp)
                    }

                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MpRed, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val currentHash = prefs.getString(KEY_MASTER_HASH, "").orEmpty()
                            when {
                                simpleHash(oldPass) != currentHash -> error = "Current password incorrect"
                                newPass.length < 4                 -> error = "New password too short (min 4)"
                                newPass != confirm                 -> error = "Passwords don't match"
                                else -> {
                                    prefs.edit().putString(KEY_MASTER_HASH, simpleHash(newPass)).apply()
                                    Toast.makeText(context, "Master password changed! ✓", Toast.LENGTH_SHORT).show()
                                    onDone()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MpBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Change Password", fontWeight = FontWeight.Bold, color = SoftWhite) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MpBorder)) {
                        Text("Cancel", color = MpTextDim)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionPasswordDialog(
    context: Context,
    sectionName: String,
    passKey: String,
    enabledKey: String,
    onDone: () -> Unit
) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(prefs.getBoolean(enabledKey, false)) }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val accent = if (sectionName.contains("Self")) MpTeal else MpBlue

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MpCard),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text("$sectionName Password", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SoftWhite)
                    Text("এই section এ ঢুকতে এই password লাগবে", fontSize = 13.sp, color = MpTextDim)
                    Spacer(Modifier.height(20.dp))

                    // Enable toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable $sectionName Lock", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = SoftWhite)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(visible = enabled) {
                        Column {
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it; error = "" },
                                label = { Text("Set Password / PIN", color = MpTextDim) },
                                singleLine = true,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = accent)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                                    focusedBorderColor = accent, unfocusedBorderColor = MpBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = confirm,
                                onValueChange = { confirm = it; error = "" },
                                label = { Text("Confirm Password", color = MpTextDim) },
                                singleLine = true,
                                isError = error.isNotEmpty(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                                    focusedBorderColor = accent, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = MpRed, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (enabled) {
                                when {
                                    pass.length < 4 -> { error = "Password must be at least 4 characters"; return@Button }
                                    pass != confirm  -> { error = "Passwords don't match"; return@Button }
                                }
                            }
                            prefs.edit()
                                .putBoolean(enabledKey, enabled)
                                .putString(passKey, if (enabled) simpleHash(pass) else "")
                                .apply()
                            Toast.makeText(context,
                                if (enabled) "$sectionName lock enabled ✓" else "$sectionName lock disabled",
                                Toast.LENGTH_SHORT).show()
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color(0xFF032220))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Settings", fontWeight = FontWeight.Bold, color = Color(0xFF032220))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MpBorder)) {
                        Text("Cancel", color = MpTextDim)
                    }
                }
            }
        }
    }
}

@Composable
fun LongTextSectionDialog(context: Context, onDone: () -> Unit) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    var text by remember { mutableStateOf(prefs.getString(KEY_LONG_TEXT, "").orEmpty()) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_TEXT_EN, false)) }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MpCard),
                border = BorderStroke(1.dp, MpPurple.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, null, tint = MpPurple, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Long Text Lock", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SoftWhite)
                    }
                    Text("এই text টাইপ করলেই feature unlock হবে", fontSize = 13.sp, color = MpTextDim)
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MpPurple.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Long Text Lock", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it; error = "" },
                            colors = SwitchDefaults.colors(checkedTrackColor = MpPurple, checkedThumbColor = SoftWhite)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(visible = enabled) {
                        Column {
                            Text("Unlock করতে এই text টাইপ করতে হবে:", fontSize = 13.sp, color = MpTextDim)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it; error = "" },
                                label = { Text("Enter long text phrase", color = MpTextDim) },
                                minLines = 3, maxLines = 6,
                                isError = error.isNotEmpty(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                                    focusedBorderColor = MpPurple, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("${text.length} characters", fontSize = 11.sp, color = MpPurple)
                        }
                    }

                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = MpRed, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (enabled && text.trim().length < 10) {
                                error = "Text must be at least 10 characters long"
                                return@Button
                            }
                            prefs.edit()
                                .putBoolean(KEY_TEXT_EN, enabled)
                                .putString(KEY_LONG_TEXT, text.trim())
                                .apply()
                            Toast.makeText(context,
                                if (enabled) "Long text lock enabled ✓" else "Long text lock disabled",
                                Toast.LENGTH_SHORT).show()
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MpPurple),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, null, tint = SoftWhite)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Settings", fontWeight = FontWeight.Bold, color = SoftWhite)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MpBorder)) {
                        Text("Cancel", color = MpTextDim)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION VERIFICATION — অন্য screen থেকে call করার জন্য
// Usage: SectionVerifyDialog("Self Control", KEY_SELF_PASS, KEY_SELF_EN, onSuccess, onCancel)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionVerifyDialog(
    context: Context,
    sectionName: String,
    passKey: String,
    enabledKey: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val prefs = context.getSharedPreferences(PREFS_MASTER, Context.MODE_PRIVATE)
    val isEnabled = prefs.getBoolean(enabledKey, false)
    val savedHash = prefs.getString(passKey, "").orEmpty()

    // Lock set না থাকলে সরাসরি unlock
    if (!isEnabled || savedHash.isEmpty()) {
        LaunchedEffect(Unit) { onSuccess() }
        return
    }

    var input by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val accent = if (sectionName.contains("Self")) MpTeal else MpBlue

    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MpCard),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, tint = accent, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text("$sectionName Locked", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SoftWhite)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = false },
                    label = { Text("Enter password", color = MpTextDim) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = accent)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                        focusedBorderColor = accent, unfocusedBorderColor = MpBorder, errorBorderColor = MpRed
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) { Spacer(Modifier.height(6.dp)); Text("Incorrect password", color = MpRed, fontSize = 12.sp) }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MpBorder)) {
                        Text("Cancel", color = MpTextDim)
                    }
                    Button(
                        onClick = {
                            if (simpleHash(input) == savedHash) onSuccess()
                            else { error = true; input = "" }
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Unlock", fontWeight = FontWeight.Bold, color = Color(0xFF032220)) }
                }
            }
        }
    }
}

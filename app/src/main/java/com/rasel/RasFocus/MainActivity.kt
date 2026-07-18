package com.rasel.RasFocus

import android.app.Application
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
// Legacy GoogleSignIn imports (Android 10 compatible)
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.rasel.RasFocus.drivebackup.DriveBackupManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.rasel.RasFocus.parental.ParentControls

// ============================================================
// SECTION 1 — DESIGN SYSTEM TOKENS
// ============================================================
object RasFocusColors {
    val PrimaryTeal      = Color(0xFF0096B4)
    val PrimaryTealLight = Color(0xFF4FC3D6)
    val PrimaryTealDark  = Color(0xFF006E85)
    val BackgroundWhite  = Color(0xFFFFFFFF)
    val SurfaceOffWhite  = Color(0xFFF5FFFE)
    val SurfaceCard      = Color(0xFFECFAFD)
    val OnPrimary        = Color(0xFFFFFFFF)
    val OnBackground     = Color(0xFF0A1628)
    val OnSurface        = Color(0xFF1A2B3C)
    val SubtleText       = Color(0xFF6B7F8E)
    val DividerColor     = Color(0xFFD6EEF4)
    val ErrorRed         = Color(0xFFE53935)
    val SuccessGreen     = Color(0xFF43A047)
    val WarningAmber     = Color(0xFFFB8C00)
    val CardShadow       = Color(0xFF0096B4).copy(alpha = 0.12f)
    val LockRed          = Color(0xFFFF3B30)
    val StudentPurple    = Color(0xFF7C4DFF)
    val SelfOrange       = Color(0xFFFF6D00)
    val ComboGold        = Color(0xFFFFAB00)
    val TimerRingBg      = Color(0xFFE0F7FA)
    val BarChartColor    = Color(0xFF0096B4).copy(alpha = 0.75f)
}

object RasFocusShapes {
    val BottomSheet   = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Card          = RoundedCornerShape(24.dp)
    val Button        = RoundedCornerShape(16.dp)
    val Pill          = RoundedCornerShape(50.dp)
    val ChipRadius    = 12.dp
    val CardRadius    = 24.dp
}

// ============================================================
// SECTION 2 — DATA MODELS
// ============================================================
enum class UserPersona(
    val displayName: String, val subtitle: String, val icon: String,
    val accentColor: Color, val description: String
) {
    SELF_CONTROL("Self Control", "Only control yourself", "🧘", RasFocusColors.SelfOrange, "Focus on your own productivity."),
    PARENTAL("Parental Control", "Only control your child", "👨‍👧", RasFocusColors.PrimaryTeal, "Monitor and manage child devices."),
    COMBO("Pro Combo", "Self + Parental", "⚡", RasFocusColors.ComboGold, "Full power package for family."),
    STUDENT("Student Mode", "Self + Linked Child", "🎓", RasFocusColors.StudentPurple, "Designed for students with focus timers.")
}

enum class DeviceType { MOBILE, PC }

data class Device(
    val id: String, val name: String, val ownerName: String, val type: DeviceType,
    val isLocked: Boolean = false, val isHalalGuardOn: Boolean = false,
    val screenTimeUsedMinutes: Int = 0, val screenTimeLimitMinutes: Int = 120,
    val blockedAppsList: List<BlockedApp> = emptyList(), val isOnline: Boolean = true,
    val isAccessibilityActive: Boolean = false,
    val appTimeLimitsMinutes: Map<String, Int> = emptyMap(),
    val batteryLevel: Int = 75,
    val blockYoutubeShorts: Boolean = true,
    val blockReels: Boolean = true,
    val isDeviceAdminActive: Boolean = false,
    val fbEnabled: Boolean = false,
    val fbStartTime: String = "00:00",
    val fbEndTime: String = "00:00",
    val fbLiteEnabled: Boolean = false,
    val fbLiteStartTime: String = "00:00",
    val fbLiteEndTime: String = "00:00",
    val ytEnabled: Boolean = false,
    val ytStartTime: String = "00:00",
    val ytEndTime: String = "00:00",
    val chromeEnabled: Boolean = false,
    val chromeStartTime: String = "00:00",
    val chromeEndTime: String = "00:00",
    val deepStudyEnabled: Boolean = false,
    val buttonPhoneEnabled: Boolean = false,
    val singleAppsBlockEnabled: Boolean = false,
    val extremeBlockEnabled: Boolean = false,
    val singleWebsiteBlockEnabled: Boolean = false,
    val familyBrowserEnabled: Boolean = false,
    val newInstalledAppsCsv: String = "",
    val blockIncognito: Boolean = true
)
data class BlockedApp(val id: String, val packageName: String, val displayName: String, val category: String, val icon: String)
typealias AppInfo = BlockedApp
data class RunningProcess(val pid: Int, val name: String, val cpuUsage: Float, val memoryMB: Int, val isSuspicious: Boolean = false)
data class PermissionItem(val id: String, val title: String, val description: String, val icon: ImageVector, val isRequired: Boolean, var isGranted: Boolean = false)
data class DailyStats(val day: String, val focusMinutes: Int, val distractionBlocks: Int)
data class FocusSession(val durationMinutes: Int, val breakMinutes: Int, val currentRound: Int, val totalRounds: Int)

data class PomodoroState(
    val isRunning: Boolean = false, val isPaused: Boolean = false,
    val currentPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingSeconds: Int = 25 * 60, val completedRounds: Int = 0,
    val totalRounds: Int = 4, val focusDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5, val longBreakDurationMinutes: Int = 15
)
enum class PomodoroPhase(val label: String, val color: Color) {
    FOCUS("Focus Time", RasFocusColors.PrimaryTeal),
    BREAK("Short Break", RasFocusColors.SuccessGreen),
    LONG_BREAK("Long Break", RasFocusColors.StudentPurple)
}

// ============================================================
// SECTION 3 — NAVIGATION ROUTES
// ============================================================
object Routes {
    const val SPLASH            = "splash"
    const val LOGIN             = "login"
    const val ROLE_SELECTION    = "role_selection"
    const val CHILD_DASHBOARD   = "child_dashboard"

    const val PERMISSION_SETUP        = "permission_setup"
    const val SELF_CONTROL_PERMISSION = "self_control_permission"
    const val SELF_CONTROL_DASH = "self_control_dashboard"
    const val PARENTAL_DASH     = "parental_dashboard"
    const val COMBO_DASH        = "combo_dashboard"
    const val COMBO_HOME        = "combo_home"
    const val COMBO_SELF        = "combo_self"
    const val COMBO_PARENTAL    = "combo_parental"

    const val PARENTAL_FAMILY   = "parental_family"
    const val COMBO_FAMILY      = "combo_family"
    const val SETTINGS          = "settings_screen"
}

sealed class BottomNavTab(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object MyFocus : BottomNavTab("my_focus", "My Focus", Icons.Filled.Person, Icons.Outlined.Person)
    object Family : BottomNavTab("family", "Family", Icons.Filled.Home, Icons.Outlined.Home)
    object Settings : BottomNavTab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    companion object { val all = listOf(MyFocus, Family, Settings) }
}

// ============================================================
// SECTION 4 — FIREBASE REPOSITORY
// ============================================================
object FirebaseRepository {
    private val db = FirebaseDatabase.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid

    fun pushDeviceLock(deviceId: String, isLocked: Boolean) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices/$deviceId/isLocked").setValue(isLocked)
    }

    fun pushHalalGuard(deviceId: String, isOn: Boolean) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices/$deviceId/isHalalGuardOn").setValue(isOn)
    }

    fun pushScreenTimeLimit(deviceId: String, limitMinutes: Int) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices/$deviceId/screenTimeLimit").setValue(limitMinutes)
    }

    fun pushBlockedApp(deviceId: String, app: BlockedApp) {
        val uid = uid() ?: return
        val appMap = mapOf(
            "id" to app.id, "packageName" to app.packageName,
            "displayName" to app.displayName, "category" to app.category, "icon" to app.icon
        )
        db.getReference("users/$uid/devices/$deviceId/blockedApps/${app.id}").setValue(appMap)
    }

    fun removeBlockedApp(deviceId: String, appId: String) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices/$deviceId/blockedApps/$appId").removeValue()
    }

    fun setAppTimeLimit(deviceId: String, packageName: String, minutesPerDay: Int) {
        val uid = uid() ?: return
        if (minutesPerDay <= 0) {
            db.getReference("users/$uid/devices/$deviceId/appTimeLimits/$packageName").removeValue()
        } else {
            db.getReference("users/$uid/devices/$deviceId/appTimeLimits/$packageName").setValue(minutesPerDay)
        }
    }

    fun registerDevice(deviceId: String, deviceName: String, ownerName: String, type: DeviceType) {
        val uid = uid() ?: return
        val deviceMap = mapOf(
            "id" to deviceId,
            "name" to deviceName,
            "ownerName" to ownerName,
            "type" to type.name,
            "isLocked" to false,
            "isHalalGuardOn" to false,
            "screenTimeLimit" to 120,
            "isOnline" to true,
            "batteryLevel" to 100
        )
        db.getReference("users/$uid/devices/$deviceId").setValue(deviceMap)
    }

    fun listenDevices(onUpdate: (List<Device>) -> Unit): ValueEventListener {
        val uid = uid() ?: run { onUpdate(emptyList()); return object : ValueEventListener { override fun onDataChange(s: DataSnapshot) {} override fun onCancelled(e: DatabaseError) {} } }
        val ref = db.getReference("users/$uid/devices")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val devices = snapshot.children.mapNotNull { child ->
                    try {
                        Device(
                            id = child.child("id").getValue(String::class.java) ?: child.key ?: return@mapNotNull null,
                            name = child.child("name").getValue(String::class.java) ?: "Unknown",
                            ownerName = child.child("ownerName").getValue(String::class.java) ?: "",
                            type = runCatching { DeviceType.valueOf(child.child("type").getValue(String::class.java) ?: "") }.getOrDefault(DeviceType.MOBILE),
                            isLocked = child.child("isLocked").getValue(Boolean::class.java) ?: false,
                            isHalalGuardOn = child.child("isHalalGuardOn").getValue(Boolean::class.java) ?: false,
                            screenTimeLimitMinutes = (child.child("screenTimeLimit").getValue(Long::class.java) ?: 120L).toInt(),
                            isOnline = child.child("isOnline").getValue(Boolean::class.java) ?: false,
                            batteryLevel = (child.child("batteryLevel").getValue(Long::class.java) ?: 75L).toInt(),
                            blockedAppsList = child.child("blockedApps").children.mapNotNull { appSnap ->
                                BlockedApp(
                                    id = appSnap.child("id").getValue(String::class.java) ?: return@mapNotNull null,
                                    packageName = appSnap.child("packageName").getValue(String::class.java) ?: "",
                                    displayName = appSnap.child("displayName").getValue(String::class.java) ?: "",
                                    category = appSnap.child("category").getValue(String::class.java) ?: "",
                                    icon = appSnap.child("icon").getValue(String::class.java) ?: "📱"
                                )
                            },
                            blockYoutubeShorts = child.child("filters/blockShorts").getValue(Boolean::class.java) ?: true,
                            blockReels = child.child("filters/blockReels").getValue(Boolean::class.java) ?: true,
                            blockIncognito = child.child("filters/blockIncognito").getValue(Boolean::class.java) ?: true,
                            isDeviceAdminActive = child.child("isDeviceAdminActive").getValue(Boolean::class.java) ?: false,
                            isAccessibilityActive = child.child("isAccessibilityActive").getValue(Boolean::class.java) ?: false,
                            appTimeLimitsMinutes = child.child("appTimeLimits").children.mapNotNull { limitSnap ->
                                val pkg = limitSnap.key ?: return@mapNotNull null
                                val minutes = limitSnap.getValue(Long::class.java)?.toInt() ?: return@mapNotNull null
                                pkg to minutes
                            }.toMap(),
                            fbEnabled = child.child("fbEnabled").getValue(Boolean::class.java) ?: false,
                            fbStartTime = child.child("fbStartTime").getValue(String::class.java) ?: "00:00",
                            fbEndTime = child.child("fbEndTime").getValue(String::class.java) ?: "00:00",
                            fbLiteEnabled = child.child("fbLiteEnabled").getValue(Boolean::class.java) ?: false,
                            fbLiteStartTime = child.child("fbLiteStartTime").getValue(String::class.java) ?: "00:00",
                            fbLiteEndTime = child.child("fbLiteEndTime").getValue(String::class.java) ?: "00:00",
                            ytEnabled = child.child("ytEnabled").getValue(Boolean::class.java) ?: false,
                            ytStartTime = child.child("ytStartTime").getValue(String::class.java) ?: "00:00",
                            ytEndTime = child.child("ytEndTime").getValue(String::class.java) ?: "00:00",
                            chromeEnabled = child.child("chromeEnabled").getValue(Boolean::class.java) ?: false,
                            chromeStartTime = child.child("chromeStartTime").getValue(String::class.java) ?: "00:00",
                            chromeEndTime = child.child("chromeEndTime").getValue(String::class.java) ?: "00:00",
                            deepStudyEnabled = child.child("deepStudyEnabled").getValue(Boolean::class.java) ?: false,
                            buttonPhoneEnabled = child.child("buttonPhoneEnabled").getValue(Boolean::class.java) ?: false,
                            singleAppsBlockEnabled = child.child("singleAppsBlockEnabled").getValue(Boolean::class.java) ?: false,
                            extremeBlockEnabled = child.child("extremeBlockEnabled").getValue(Boolean::class.java) ?: false,
                            singleWebsiteBlockEnabled = child.child("singleWebsiteBlockEnabled").getValue(Boolean::class.java) ?: false,
                            familyBrowserEnabled = child.child("familyBrowserEnabled").getValue(Boolean::class.java) ?: false,
                            newInstalledAppsCsv = child.child("newInstalledAppsCsv").getValue(String::class.java) ?: ""
                        )
                    } catch (e: Exception) { null }
                }
                onUpdate(devices)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun detachListener(listener: ValueEventListener) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices").removeEventListener(listener)
    }

    fun savePersona(persona: UserPersona) {
        val uid = uid() ?: return
        db.getReference("users/$uid/persona").setValue(persona.name)
    }

    fun loadPersona(onResult: (UserPersona?) -> Unit) {
        val uid = uid() ?: run { onResult(null); return }
        db.getReference("users/$uid/persona").get()
            .addOnSuccessListener { snap ->
                val name = snap.getValue(String::class.java)
                onResult(name?.let { runCatching { UserPersona.valueOf(it) }.getOrNull() })
            }
            .addOnFailureListener { onResult(null) }
    }

    fun updateSelfDeviceStatus(deviceId: String, batteryLevel: Int) {
        val uid = uid() ?: return
        db.getReference("users/$uid/devices/$deviceId").updateChildren(
            mapOf("isOnline" to true, "batteryLevel" to batteryLevel)
        )
    }

    fun listenMyDeviceCommands(deviceId: String, onLockChanged: (Boolean) -> Unit, onHalalChanged: (Boolean) -> Unit): ValueEventListener {
        val uid = uid() ?: return object : ValueEventListener { override fun onDataChange(s: DataSnapshot) {} override fun onCancelled(e: DatabaseError) {} }
        val ref = db.getReference("users/$uid/devices/$deviceId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isLocked = snapshot.child("isLocked").getValue(Boolean::class.java) ?: false
                val isHalal = snapshot.child("isHalalGuardOn").getValue(Boolean::class.java) ?: false
                onLockChanged(isLocked)
                onHalalChanged(isHalal)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }
}

// ============================================================
// SECTION 5 — MAIN VIEW MODEL
// ============================================================
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("rasfocus_prefs", Context.MODE_PRIVATE)
    private var deviceListener: ValueEventListener? = null

    // ── NEW: Terms and Conditions Flag ──
    private val _hasAcceptedTerms = MutableStateFlow(prefs.getBoolean("has_accepted_terms", false))
    val hasAcceptedTerms: StateFlow<Boolean> = _hasAcceptedTerms.asStateFlow()

    fun acceptTerms() {
        prefs.edit().putBoolean("has_accepted_terms", true).apply()
        _hasAcceptedTerms.value = true
    }

    fun isUserLoggedInOrSkipped(): Boolean {
        return prefs.getBoolean("is_logged_in", false) || prefs.getBoolean("has_skipped_login", false)
    }

    fun setLoginStatus(isLoggedIn: Boolean, hasSkipped: Boolean) {
        prefs.edit()
            .putBoolean("is_logged_in", isLoggedIn)
            .putBoolean("has_skipped_login", hasSkipped)
            .apply()
    }

    fun getSavedPersona(): UserPersona? = prefs.getString("selected_persona", null)?.let { name -> runCatching { UserPersona.valueOf(name) }.getOrNull() }
    private fun savePersonaLocal(persona: UserPersona) { prefs.edit().putString("selected_persona", persona.name).apply() }

    private val _selectedPersona = MutableStateFlow<UserPersona?>(null)
    val selectedPersona: StateFlow<UserPersona?> = _selectedPersona.asStateFlow()

    private val _pendingPersona = MutableStateFlow<UserPersona?>(null)
    val pendingPersona: StateFlow<UserPersona?> = _pendingPersona.asStateFlow()

    private val _showPermissionSheet = MutableStateFlow(false)
    val showPermissionSheet: StateFlow<Boolean> = _showPermissionSheet.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _isFirebaseConnected = MutableStateFlow(FirebaseAuth.getInstance().currentUser != null)
    val isFirebaseConnected: StateFlow<Boolean> = _isFirebaseConnected.asStateFlow()

    private val _selectedDeviceTab = MutableStateFlow(DeviceType.MOBILE)
    val selectedDeviceTab: StateFlow<DeviceType> = _selectedDeviceTab.asStateFlow()

    private val _showPairDialog = MutableStateFlow(false)
    val showPairDialog: StateFlow<Boolean> = _showPairDialog.asStateFlow()

    private val _connectionPin = MutableStateFlow(generatePin())
    val connectionPin: StateFlow<String> = _connectionPin.asStateFlow()

    private val _pomodoroState = MutableStateFlow(PomodoroState())
    val pomodoroState: StateFlow<PomodoroState> = _pomodoroState.asStateFlow()

    private val _dailyStats = MutableStateFlow(generateMockStats())
    val dailyStats: StateFlow<List<DailyStats>> = _dailyStats.asStateFlow()

    private val _studentOtp = MutableStateFlow(List(6) { "" })
    val studentOtp: StateFlow<List<String>> = _studentOtp.asStateFlow()

    private val _otpVerified = MutableStateFlow(false)
    val otpVerified: StateFlow<Boolean> = _otpVerified.asStateFlow()

    fun startFirebaseDeviceSync() {
        _isFirebaseConnected.value = FirebaseAuth.getInstance().currentUser != null
        if (_isFirebaseConnected.value) {
            deviceListener = FirebaseRepository.listenDevices { firebaseDevices ->
                if (firebaseDevices.isNotEmpty()) {
                    _devices.value = firebaseDevices
                }
            }
        }
    }

    fun stopFirebaseDeviceSync() {
        deviceListener?.let { FirebaseRepository.detachListener(it) }
        deviceListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopFirebaseDeviceSync()
    }

    fun selectPendingPersona(persona: UserPersona) { _pendingPersona.value = persona }
    fun confirmPersona() {
        val persona = _pendingPersona.value ?: return
        savePersonaLocal(persona)
        FirebaseRepository.savePersona(persona)
        _selectedPersona.value = persona
        _showPermissionSheet.value = false
    }

    fun restorePersona(persona: UserPersona) {
        _selectedPersona.value = persona
        _pendingPersona.value = persona
    }

    fun selectDeviceTab(type: DeviceType) { _selectedDeviceTab.value = type }
    fun openPairDialog() {
        val pin = generatePin()
        _connectionPin.value = pin
        _showPairDialog.value = true
        savePinToFirebase(pin)
    }
    fun closePairDialog() {
        val pin = _connectionPin.value
        if (pin.isNotEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("pairing_codes").document(pin).delete()
        }
        _showPairDialog.value = false
    }
    fun refreshPin() {
        val oldPin = _connectionPin.value
        if (oldPin.isNotEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("pairing_codes").document(oldPin).delete()
        }
        val newPin = generatePin()
        _connectionPin.value = newPin
        savePinToFirebase(newPin)
    }
    // Writes to Firestore pairing_codes/<pin> — this is the path + field name
    // (parent_uid) the Windows C++ app already reads via SendFirestoreRequest
    // in tab_family_link.cpp. Previously this wrote to Realtime Database
    // instead, a completely different product/URL, so the Windows app could
    // never find the PIN and pairing always failed with "Invalid or expired PIN".
    private val _pinSaveError = MutableStateFlow<String?>(null)
    val pinSaveError: StateFlow<String?> = _pinSaveError.asStateFlow()

    private fun savePinToFirebase(pin: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            _pinSaveError.value = "Not logged in — please sign in first so pairing codes can be saved."
            return
        }
        _pinSaveError.value = null
        FirebaseFirestore.getInstance()
            .collection("pairing_codes").document(pin)
            .set(mapOf("parent_uid" to uid))
            .addOnFailureListener { e ->
                _pinSaveError.value = "Failed to save code: ${e.localizedMessage}"
            }
    }

    fun toggleDeviceLock(deviceId: String) {
        val current = _devices.value.find { it.id == deviceId }?.isLocked ?: false
        val newState = !current
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(isLocked = newState) else it }
        FirebaseRepository.pushDeviceLock(deviceId, newState)
    }

    fun removeDevice(deviceId: String) {
        _devices.value = _devices.value.filter { it.id != deviceId }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users/$uid/devices/$deviceId").removeValue()
    }

    fun toggleHalalGuard(deviceId: String) {
        val current = _devices.value.find { it.id == deviceId }?.isHalalGuardOn ?: false
        val newState = !current
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(isHalalGuardOn = newState) else it }
        FirebaseRepository.pushHalalGuard(deviceId, newState)
    }

    fun removeBlockedApp(deviceId: String, appId: String) {
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(blockedAppsList = it.blockedAppsList.filter { app -> app.id != appId }) else it }
        FirebaseRepository.removeBlockedApp(deviceId, appId)
    }

    /** Adds a package to the child device's block list — used by Child Phone Control UI. */
    fun addBlockedApp(deviceId: String, packageName: String, displayName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        val app = BlockedApp(
            id = cleanPkg,
            packageName = cleanPkg,
            displayName = displayName.ifBlank { cleanPkg },
            category = "Other",
            icon = "📱"
        )
        _devices.value = _devices.value.map {
            if (it.id == deviceId && it.blockedAppsList.none { existing -> existing.packageName == cleanPkg })
                it.copy(blockedAppsList = it.blockedAppsList + app)
            else it
        }
        FirebaseRepository.pushBlockedApp(deviceId, app)
    }

    /** Sets the total daily screen-time budget for a child device (0 = no limit). */
    fun setScreenTimeLimit(deviceId: String, minutes: Int) {
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(screenTimeLimitMinutes = minutes) else it }
        FirebaseRepository.pushScreenTimeLimit(deviceId, minutes)
    }

    /** Sets (or clears, with minutes<=0) a per-app daily time budget for a child device. */
    fun setAppTimeLimit(deviceId: String, packageName: String, minutes: Int) {
        _devices.value = _devices.value.map {
            if (it.id == deviceId) {
                val updated = it.appTimeLimitsMinutes.toMutableMap()
                if (minutes <= 0) updated.remove(packageName) else updated[packageName] = minutes
                it.copy(appTimeLimitsMinutes = updated)
            } else it
        }
        FirebaseRepository.setAppTimeLimit(deviceId, packageName, minutes)
    }

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notif_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()
    private val _darkModeEnabled = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val darkModeEnabled: StateFlow<Boolean> = _darkModeEnabled.asStateFlow()
    private val _strictModeEnabled = MutableStateFlow(prefs.getBoolean("strict_mode", false))
    val strictModeEnabled: StateFlow<Boolean> = _strictModeEnabled.asStateFlow()

    fun toggleNotifications() {
        val new = !_notificationsEnabled.value
        _notificationsEnabled.value = new
        prefs.edit().putBoolean("notif_enabled", new).apply()
    }
    fun toggleDarkMode() {
        val new = !_darkModeEnabled.value
        _darkModeEnabled.value = new
        prefs.edit().putBoolean("dark_mode", new).apply()
    }
    fun toggleStrictMode() {
        val new = !_strictModeEnabled.value
        _strictModeEnabled.value = new
        prefs.edit().putBoolean("strict_mode", new).apply()
    }

    private val _firebaseDb = com.google.firebase.database.FirebaseDatabase.getInstance().reference

    fun toggleYoutubeShorts(deviceId: String) {
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(blockYoutubeShorts = !it.blockYoutubeShorts) else it }
        val device = _devices.value.find { it.id == deviceId } ?: return
        _firebaseDb.child("children/$deviceId/filters/blockShorts").setValue(device.blockYoutubeShorts)
    }
    fun toggleReels(deviceId: String) {
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(blockReels = !it.blockReels) else it }
        val device = _devices.value.find { it.id == deviceId } ?: return
        _firebaseDb.child("children/$deviceId/filters/blockReels").setValue(device.blockReels)
    }
    fun toggleIncognito(deviceId: String) {
        _devices.value = _devices.value.map { if (it.id == deviceId) it.copy(blockIncognito = !it.blockIncognito) else it }
        val device = _devices.value.find { it.id == deviceId } ?: return
        _firebaseDb.child("children/$deviceId/filters/blockIncognito").setValue(device.blockIncognito)
    }

    fun updateDeviceField(deviceId: String, field: String, value: Any) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        _firebaseDb.child("users/$uid/devices/$deviceId/$field").setValue(value)
        // Also update local StateFlow optimistically if needed, though listenDevices will catch it.
    }
    fun requestScreenshot(deviceId: String) {
        _firebaseDb.child("children/$deviceId/commands/takeScreenshot").setValue(true)
    }

    // ── PC Control Methods ────────────────────────────────────────
    // NOTE: deviceId here must be the Windows hardware ID (hwId), matching
    // parent_commands/<hwId> as written by the C++ app in tab_family_link.cpp
    // (InitParentCommandsDocument / PollParentCommands) — not an Android-side
    // device UID. That hwId doc is created during pairing (Step 3 of the
    // Windows connect flow), so it must already exist by the time a parent
    // opens PC Control for that device.
    private val _pcControls = MutableStateFlow(ParentControls())
    val pcControls: StateFlow<ParentControls> = _pcControls.asStateFlow()
    private var pcControlsListener: ListenerRegistration? = null

    private fun pcCommandsDoc(deviceId: String) =
        FirebaseFirestore.getInstance().collection("parent_commands").document(deviceId)

    // ── PC Pairing Detection ──────────────────────────────────────
    // tab_family_link.cpp (untouched) does 4 things when the user types the
    // PIN on the PC:
    //   1. GET   pairing_codes/<pin>                  (verify PIN)
    //   2. PATCH devices/<hwId>  { parent_uid, linked_at, ... }
    //   3. PATCH parent_commands/<hwId>                (init controls doc)
    //   4. PATCH pairing_codes/<pin> { used: true }    (consume PIN)
    //
    // Nothing on the phone was ever listening for any of this, so even
    // though the PC considered itself "connected", the phone had no way to
    // find out — it just kept showing the PIN screen forever. This listens
    // for step 4 (`used` flips true) and then resolves which devices/<hwId>
    // doc was just stamped with our parent_uid (step 2), so the phone can
    // switch to the real hwId and open live PC Control.
    private val _pairedPcDeviceId = MutableStateFlow<String?>(null)
    val pairedPcDeviceId: StateFlow<String?> = _pairedPcDeviceId.asStateFlow()
    private var pcPairingListener: ListenerRegistration? = null

    fun listenForPcPairing(pin: String) {
        if (pin.isBlank() || pin == "------") return
        pcPairingListener?.remove()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        pcPairingListener = FirebaseFirestore.getInstance()
            .collection("pairing_codes").document(pin)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                val used = snap.getBoolean("used") ?: false
                if (!used) return@addSnapshotListener

                // PIN just got consumed by the PC (Step 4) — find its hwId.
                // No orderBy on purpose: whereEqualTo + orderBy on a
                // different field needs a Firestore composite index; a
                // parent has very few PC devices, so we just pick the
                // newest linked_at client-side to skip that setup step.
                FirebaseFirestore.getInstance()
                    .collection("devices")
                    .whereEqualTo("parent_uid", uid)
                    .get()
                    .addOnSuccessListener { docs ->
                        val newest = docs.documents.maxByOrNull {
                            it.getString("linked_at")?.toLongOrNull() ?: 0L
                        }
                        val hwId = newest?.id ?: return@addOnSuccessListener
                        _pairedPcDeviceId.value = hwId
                    }
            }
    }

    fun stopPcPairingListener() {
        pcPairingListener?.remove()
        pcPairingListener = null
    }

    fun clearPairedPcDeviceId() {
        _pairedPcDeviceId.value = null
    }

    fun startPcControlSync(deviceId: String) {
        pcControlsListener?.remove()
        pcControlsListener = pcCommandsDoc(deviceId).addSnapshotListener { snap, error ->
            if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
            _pcControls.value = ParentControls(
                lockAllTabs = snap.getBoolean("lock_all_tabs") ?: false,
                forceAdultBlock = snap.getBoolean("force_adult_block") ?: false,
                forceReelsBlock = snap.getBoolean("force_reels_block") ?: false,
                forceShortsBlock = snap.getBoolean("force_shorts_block") ?: false,
                appControlEnabled = snap.getBoolean("app_control_enabled") ?: false,
                appMode = snap.getString("app_mode") ?: "BLOCK",
                allowedAppsCsv = snap.getString("allowed_apps_csv") ?: "",
                blockedAppsCsv = snap.getString("blocked_apps_csv") ?: "",
                webBlockEnabled = snap.getBoolean("web_block_enabled") ?: false,
                blockedWebsCsv = snap.getString("blocked_webs_csv") ?: "",
                blockTaskManager = snap.getBoolean("block_task_manager") ?: false,
                blockSettings = snap.getBoolean("block_settings") ?: false,
                blockFileManager = snap.getBoolean("block_file_manager") ?: false,
                blockedFoldersCsv = snap.getString("blocked_folders_csv") ?: "",
                internetFasting = snap.getBoolean("internet_fasting") ?: false,
                timeLimitMinutes = (snap.getLong("time_limit_minutes") ?: 0L).toInt(),
                powerAction = (snap.getLong("power_action") ?: 0L).toInt(),
                lockUntilEpoch = snap.getLong("lock_until_epoch") ?: 0L,
                lockType = snap.getString("lock_type") ?: "",
                // Specific Apps
                fbEnabled = snap.getBoolean("fb_enabled") ?: false,
                fbStartTime = snap.getString("fb_start") ?: "00:00",
                fbEndTime = snap.getString("fb_end") ?: "00:00",
                fbLiteEnabled = snap.getBoolean("fblite_enabled") ?: false,
                fbLiteStartTime = snap.getString("fblite_start") ?: "00:00",
                fbLiteEndTime = snap.getString("fblite_end") ?: "00:00",
                ytEnabled = snap.getBoolean("yt_enabled") ?: false,
                ytStartTime = snap.getString("yt_start") ?: "00:00",
                ytEndTime = snap.getString("yt_end") ?: "00:00",
                chromeEnabled = snap.getBoolean("chrome_enabled") ?: false,
                chromeStartTime = snap.getString("chrome_start") ?: "00:00",
                chromeEndTime = snap.getString("chrome_end") ?: "00:00",
                // Self Control
                deepStudyEnabled = snap.getBoolean("deep_study_enabled") ?: false,
                buttonPhoneEnabled = snap.getBoolean("button_phone_enabled") ?: false,
                singleAppsBlockEnabled = snap.getBoolean("single_apps_block_enabled") ?: false,
                extremeBlockEnabled = snap.getBoolean("extreme_block_enabled") ?: false,
                singleWebsiteBlockEnabled = snap.getBoolean("single_web_block_enabled") ?: false,
                familyBrowserEnabled = snap.getBoolean("family_browser_enabled") ?: false,
                // Status from child
                newInstalledAppsCsv = snap.getString("new_installed_apps_csv") ?: ""
            )
        }
    }

    fun stopPcControlSync(deviceId: String) {
        pcControlsListener?.remove()
        pcControlsListener = null
    }

    fun updatePcControls(deviceId: String, controls: ParentControls) {
        _pcControls.value = controls
        pcCommandsDoc(deviceId).set(
            mapOf(
                "lock_all_tabs" to controls.lockAllTabs,
                "force_adult_block" to controls.forceAdultBlock,
                "force_reels_block" to controls.forceReelsBlock,
                "force_shorts_block" to controls.forceShortsBlock,
                "app_control_enabled" to controls.appControlEnabled,
                "app_mode" to controls.appMode,
                "allowed_apps_csv" to controls.allowedAppsCsv,
                "blocked_apps_csv" to controls.blockedAppsCsv,
                "web_block_enabled" to controls.webBlockEnabled,
                "blocked_webs_csv" to controls.blockedWebsCsv,
                "block_task_manager" to controls.blockTaskManager,
                "block_settings" to controls.blockSettings,
                "block_file_manager" to controls.blockFileManager,
                "blocked_folders_csv" to controls.blockedFoldersCsv,
                "internet_fasting" to controls.internetFasting,
                "time_limit_minutes" to controls.timeLimitMinutes,
                // Specific Apps
                "fb_enabled" to controls.fbEnabled,
                "fb_start" to controls.fbStartTime,
                "fb_end" to controls.fbEndTime,
                "fblite_enabled" to controls.fbLiteEnabled,
                "fblite_start" to controls.fbLiteStartTime,
                "fblite_end" to controls.fbLiteEndTime,
                "yt_enabled" to controls.ytEnabled,
                "yt_start" to controls.ytStartTime,
                "yt_end" to controls.ytEndTime,
                "chrome_enabled" to controls.chromeEnabled,
                "chrome_start" to controls.chromeStartTime,
                "chrome_end" to controls.chromeEndTime,
                // Self Control
                "deep_study_enabled" to controls.deepStudyEnabled,
                "button_phone_enabled" to controls.buttonPhoneEnabled,
                "single_apps_block_enabled" to controls.singleAppsBlockEnabled,
                "extreme_block_enabled" to controls.extremeBlockEnabled,
                "single_web_block_enabled" to controls.singleWebsiteBlockEnabled,
                "family_browser_enabled" to controls.familyBrowserEnabled,
                // Status from child
                "new_installed_apps_csv" to controls.newInstalledAppsCsv
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    fun sendPcPowerCommand(deviceId: String, action: Int) {
        pcCommandsDoc(deviceId).set(
            mapOf("power_action" to action),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    fun schedulePcLock(deviceId: String, ms: Long, t: String) {
        pcCommandsDoc(deviceId).set(
            mapOf("lock_until_epoch" to ms, "lock_type" to t),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    fun cancelPcSchedule(deviceId: String) {
        pcCommandsDoc(deviceId).set(
            mapOf("lock_until_epoch" to 0L, "lock_type" to ""),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _isFirebaseConnected.value = false
        stopFirebaseDeviceSync()
        _devices.value = emptyList()
        prefs.edit().clear().apply()
        _selectedPersona.value = null
        _pendingPersona.value = null
    }
}

private fun generatePin(): String {
    // (100000..999999) সবসময় 6 digit দেয়, কিন্তু extra safety হিসেবে
    // padStart দিয়ে নিশ্চিত করা হলো — কখনো 5 digit যাবে না।
    return (100000..999999).random().toString().padStart(6, '0')
}
private fun generateMockStats(): List<DailyStats> {
    // FIX: Returns empty list — real data is populated by UsageStats at runtime.
    // Previously this returned hardcoded fake data that would show to production users.
    return emptyList()
}

// ============================================================
// SECTION 6 — MAIN ACTIVITY
// ============================================================
class MainActivity : ComponentActivity() {

    // ✅ Modern ActivityResultLauncher — onActivityResult deprecated, তাই এটা ব্যবহার করছি
    var googleSignInCallback: ((GoogleSignInAccount?) -> Unit)? = null

    // ✅ FIX: Google Sign-In (বা অন্য যেকোনো internal activity launch) চলাকালীন
    // onUserLeaveHint()-এর "restart during break" logic যাতে ট্রিগার না হয়, তার জন্য flag।
    // এটা না থাকলে sign-in flow চলাকালীন MainActivity restart হয়ে যেত এবং
    // googleSignInLauncher/googleSignInCallback সহ পুরনো Activity instance নষ্ট হয়ে
    // ব্যবহারকারী app থেকে বের হয়ে যেত।
    var isLaunchingInternalActivity: Boolean = false

    val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLaunchingInternalActivity = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            googleSignInCallback?.invoke(account)
        } catch (e: ApiException) {
            googleSignInCallback?.invoke(null)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLaunchingInternalActivity) return
        if (this.getSharedPreferences("take_rest_prefs", android.content.Context.MODE_PRIVATE).getLong("break_end_time", 0L) > System.currentTimeMillis()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Initialize DataManager so all features have access to persisted state
        com.rasel.RasFocus.DataManager.init(this)
        
        // Check for app updates via GitHub API (Immediate check + Background periodic check)
        AutoUpdater.checkForUpdates(this)
        AutoUpdater.setupBackgroundAutoUpdate(this)
        
        // Ensure Notification Service is running if terms are accepted
        val prefs = getSharedPreferences("rasfocus_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("has_accepted_terms", false)) {
            try {
                UsageNotificationService.start(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        setContent {
            com.rasel.RasFocus.ui.theme.RasFocusAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: MainViewModel = viewModel(
                        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)
                    )
                    
                    val hasAcceptedTerms by viewModel.hasAcceptedTerms.collectAsState()
                    
                    if (!hasAcceptedTerms) {
                        TermsAndConditionsScreen(onAccept = { viewModel.acceptTerms() })
                    } else {
                        RasFocusApp(viewModel)
                    }
                }
            }
        }
    }
}

// ── NEW: Terms and Conditions Screen ──
@Composable
fun TermsAndConditionsScreen(onAccept: () -> Unit) {
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(RasFocusColors.SurfaceOffWhite).systemBarsPadding().padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.size(64.dp).background(RasFocusColors.PrimaryTeal.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Gavel, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Terms & Privacy Policy", fontSize = 28.sp, fontWeight = FontWeight.Black, color = RasFocusColors.OnBackground)
        Text("Please read and agree to proceed.", fontSize = 15.sp, color = RasFocusColors.SubtleText)
        
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, RasFocusColors.DividerColor)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("1. Accessibility Service Usage\nRasFocus uses Accessibility services to detect when restricted apps are launched and dynamically block them to enforce focus rules. We do not transmit or store your personal typing or screen reading data on our servers.\n\n2. Device Admin\nTo prevent unauthorized removal by children or during strict focus blocks, this app may require Device Admin privileges.\n\n3. Data Sync\nDevice controls and usage times are synced securely via Firebase to allow remote parental management.\n\nBy continuing, you agree to these core functional requirements.", fontSize = 14.sp, color = RasFocusColors.SubtleText, lineHeight = 20.sp)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isChecked = !isChecked }) {
            Checkbox(checked = isChecked, onCheckedChange = { isChecked = it }, colors = CheckboxDefaults.colors(checkedColor = RasFocusColors.PrimaryTeal))
            Text("I understand and agree to the Terms.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RasFocusColors.OnBackground)
        }
        
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAccept,
            enabled = isChecked,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal, disabledContainerColor = RasFocusColors.DividerColor)
        ) {
            Text("CONTINUE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RasFocusApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // FIX: previously this called startFirebaseDeviceSync() here AND again inside the
    // SPLASH LaunchedEffect below — every app resume/process-restart fired the device
    // sync network call twice. Removed here; the SPLASH block below is the single
    // source of truth for the initial sync.

    // RasFocus+ Drive backup: on every app open, quietly re-sync the "RasFocus+"
    // Drive folder + settings for returning users. Runs in parallel with the
    // splash navigation logic below so it never delays getting into the app.
    LaunchedEffect(Unit) {
        // Drive sync: run on IO thread with full error isolation.
        // google-api-client-android can throw on main thread during
        // credential init — explicit IO dispatch prevents that.
        if (FirebaseAuth.getInstance().currentUser != null &&
            DriveBackupManager.isAvailable(context)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { DriveBackupManager.syncNow(context) } catch (_: Exception) {}
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            LaunchedEffect(Unit) {
                // FIX: this used to be a hardcoded delay(1500) — a flat 1.5s black
                // screen on EVERY launch and every process-restart (which happens a
                // lot here because several always-on foreground services keep memory
                // pressure high, so Android frequently kills+recreates this process
                // when the user multitasks away and back). All the auth/persona
                // checks below are just local SharedPreferences reads plus a cheap
                // Firebase currentUser check — no artificial wait needed. A tiny
                // 60ms yield avoids a splash-frame flash/tear on very fast devices.
                delay(60)

                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    viewModel.setLoginStatus(true, false)
                    viewModel.startFirebaseDeviceSync()
                }

                val isAuthDone = viewModel.isUserLoggedInOrSkipped()
                val savedPersona = viewModel.getSavedPersona()

                if (!isAuthDone) {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } }
                } else if (savedPersona != null) {
                    viewModel.restorePersona(savedPersona)
                    val route = when (savedPersona) {
                        UserPersona.PARENTAL     -> Routes.PARENTAL_DASH
                        UserPersona.COMBO        -> Routes.COMBO_HOME
                        UserPersona.STUDENT      -> Routes.CHILD_DASHBOARD
                        UserPersona.SELF_CONTROL -> Routes.SELF_CONTROL_DASH
                    }
                    navController.navigate(route) { popUpTo(Routes.SPLASH) { inclusive = true } }
                } else {
                    navController.navigate(Routes.ROLE_SELECTION) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            }
            SplashScreen()
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    viewModel.setLoginStatus(true, false)
                    viewModel.startFirebaseDeviceSync()
                    navController.navigate(Routes.ROLE_SELECTION) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onSkip = {
                    viewModel.setLoginStatus(false, true)
                    navController.navigate(Routes.ROLE_SELECTION) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onPersonaSelected = { persona ->
                    if (persona == UserPersona.STUDENT) {
                        navController.navigate(Routes.CHILD_DASHBOARD) { popUpTo(Routes.ROLE_SELECTION) { inclusive = true } }
                    } else {
                        navController.navigate(Routes.PERMISSION_SETUP) { popUpTo(Routes.ROLE_SELECTION) { inclusive = true } }
                    }
                },
                viewModel = viewModel
            )
        }

        composable(Routes.PERMISSION_SETUP) {
            val persona by viewModel.selectedPersona.collectAsState()
            val dashRoute = when (persona) {
                UserPersona.PARENTAL -> Routes.PARENTAL_DASH
                UserPersona.COMBO    -> Routes.COMBO_HOME
                UserPersona.STUDENT  -> Routes.CHILD_DASHBOARD
                else                 -> Routes.SELF_CONTROL_DASH
            }
            PermissionSetupScreen(
                persona = persona,
                onAllGranted = {
                    try {
                        navController.navigate(dashRoute) {
                            popUpTo(Routes.PERMISSION_SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    } catch (e: Exception) {
                        // Anti-Crash protection for navigation jumps
                        e.printStackTrace()
                    }
                }
            )
        }

        composable(Routes.SELF_CONTROL_DASH) {
            com.rasel.RasFocus.selfcontrol.StayFocusedApp(
                navController   = navController,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }

        composable("schedule_blocks") {
            // ScheduleBlocksScreen is handled inside StayFocusedApp tabs — redirect to dash
            com.rasel.RasFocus.selfcontrol.StayFocusedApp(
                navController   = navController,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }

        composable("blocking_plan") {
            com.rasel.RasFocus.selfcontrol.BlockingPlanScreen(navController = navController)
        }

        composable("social_media") { com.rasel.RasFocus.selfcontrol.SocialMediaScreen(navController = navController) }
        composable("reels_shorts") { com.rasel.RasFocus.selfcontrol.ReelsShortsScreen(navController = navController) }
        composable("adult_block") { com.rasel.RasFocus.selfcontrol.Adult_block() }
        composable("deep_study") { com.rasel.RasFocus.selfcontrol.Deep_study() }
        composable("extreme_block") { com.rasel.RasFocus.selfcontrol.ExtremeBlockScreen() }
        composable("single_apps") { com.rasel.RasFocus.selfcontrol.BlockerRoot() }
        composable("single_website") {
            com.rasel.RasFocus.selfcontrol.IndividualWebsiteLockScreen(navController = navController)
        }
        composable("statistics") {
            com.rasel.RasFocus.selfcontrol.StatisticsScreen(navController = navController)
        }

        composable(Routes.PARENTAL_DASH) {
            PersonaScaffold(tabs = BottomNavTab.all, currentRoute = Routes.PARENTAL_DASH, onTabSelect = { tab ->
                when (tab) {
                    is BottomNavTab.MyFocus  -> { }
                    is BottomNavTab.Family   -> navController.navigate(Routes.PARENTAL_FAMILY) { launchSingleTop = true; restoreState = true }
                    is BottomNavTab.Settings -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true; restoreState = true }
                }
            }) { padding -> Box(Modifier.padding(padding)) { com.rasel.RasFocus.parental.ParentalRootScreen(viewModel = viewModel, hideOwnFooter = true) } }
        }

        composable(Routes.PARENTAL_FAMILY) {
            PersonaScaffold(tabs = BottomNavTab.all, currentRoute = Routes.PARENTAL_FAMILY, onTabSelect = { tab ->
                when (tab) {
                    is BottomNavTab.MyFocus  -> navController.navigate(Routes.PARENTAL_DASH)  { launchSingleTop = true; popUpTo(Routes.PARENTAL_DASH) { inclusive = false } }
                    is BottomNavTab.Family   -> { }
                    is BottomNavTab.Settings -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            }) { padding -> Box(Modifier.padding(padding)) { com.rasel.RasFocus.parental.ParentalRootScreen(viewModel = viewModel, hideOwnFooter = true) } }
        }

        composable(Routes.COMBO_DASH) {
            PersonaScaffold(tabs = BottomNavTab.all, currentRoute = Routes.COMBO_DASH, onTabSelect = { tab ->
                when (tab) {
                    is BottomNavTab.MyFocus  -> { }
                    is BottomNavTab.Family   -> navController.navigate(Routes.COMBO_FAMILY) { launchSingleTop = true }
                    is BottomNavTab.Settings -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            }) { padding -> Box(Modifier.padding(padding)) { com.rasel.RasFocus.combo.ComboDashboardScreen(viewModel = viewModel, navController = navController, hideOwnFooter = true) } }
        }

        composable(Routes.COMBO_FAMILY) {
            PersonaScaffold(tabs = BottomNavTab.all, currentRoute = Routes.COMBO_FAMILY, onTabSelect = { tab ->
                when (tab) {
                    is BottomNavTab.MyFocus  -> navController.navigate(Routes.COMBO_DASH) { launchSingleTop = true; popUpTo(Routes.COMBO_DASH) { inclusive = false } }
                    is BottomNavTab.Family   -> { }
                    is BottomNavTab.Settings -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            }) { padding -> Box(Modifier.padding(padding)) { com.rasel.RasFocus.combo.ComboDashboardScreen(viewModel = viewModel, navController = navController, hideOwnFooter = true) } }
        }


        // ── COMBO NEW ROUTES ────────────────────────────────────────────────
        // combo_home  : Combo landing screen (brand header + footer visible)
        // combo_self  : Full-screen Self Control (own header/footer, no outer scaffold)
        // combo_parental: Full-screen Parental   (own header/footer, no outer scaffold)
        composable(Routes.COMBO_HOME) {
            com.rasel.RasFocus.combo.ComboDashboardScreen(
                viewModel      = viewModel,
                navController  = navController,
                hideOwnFooter  = false
            )
        }

        composable(Routes.COMBO_SELF) {
            com.rasel.RasFocus.combo.selfcontrol.StayFocusedApp(
                navController   = navController,
                onSettingsClick = {
                    navController.navigate(Routes.COMBO_HOME) {
                        popUpTo(Routes.COMBO_HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.COMBO_PARENTAL) {
            com.rasel.RasFocus.combo.parental.ParentalRootScreen(
                viewModel     = viewModel,
                hideOwnFooter = false,
                onBackToCombo = {
                    navController.navigate(Routes.COMBO_HOME) {
                        popUpTo(Routes.COMBO_HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.CHILD_DASHBOARD) {
            val context = LocalContext.current
            com.rasel.RasFocus.child.ChildRootScreen(context = context)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSignOut = {
                    viewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
                onChangePersona = { navController.navigate(Routes.ROLE_SELECTION) { popUpTo(0) { inclusive = true } } }
            )
        }
    }
}

// ============================================================
// SPLASH SCREEN
// ============================================================
@Composable
fun SplashScreen() {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "splash_scale")
    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.PrimaryTeal), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).scale(scale).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("RasFocus+", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Focus. Control. Grow.", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

// ============================================================
// SECTION 7 — LOGIN SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSignUpMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var exactException by remember { mutableStateOf<String?>(null) }

    fun processEmailAuth() {
        if (isSignUpMode && name.isBlank()) { errorMsg = "Please enter your name."; return }
        if (email.isBlank() || password.isBlank()) { errorMsg = "Please fill in both email and password."; return }
        coroutineScope.launch {
            isLoading = true; errorMsg = null; exactException = null
            try {
                val auth = FirebaseAuth.getInstance()
                if (isSignUpMode) {
                    auth.createUserWithEmailAndPassword(email.trim(), password).await()
                    // Save name to Firebase Realtime Database after successful signup
                    auth.currentUser?.let { user ->
                        FirebaseDatabase.getInstance().getReference("users/${user.uid}/profile/name").setValue(name.trim())
                    }
                } else auth.signInWithEmailAndPassword(email.trim(), password).await()
                isLoading = false; onLoginSuccess()
            } catch (e: Exception) {
                isLoading = false; errorMsg = if (isSignUpMode) "Sign Up Failed" else "Login Failed"; exactException = e.localizedMessage ?: e.toString()
            }
        }
    }

    fun startGoogleSignIn() {
        val activity = context as? MainActivity
        if (activity == null) {
            errorMsg = "Activity not found. Please restart the app."
            return
        }
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            errorMsg = "Google Sign-In is not configured."
            exactException = "GOOGLE_WEB_CLIENT_ID is missing from build config"
            return
        }
        isLoading = true; errorMsg = null; exactException = null
        val gso = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        ).requestIdToken(webClientId).requestEmail()
            // RasFocus+ Drive backup: drive.file only lets the app see/manage
            // files IT creates (the "RasFocus+" folder) — never the user's
            // other Drive files.
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(activity, gso)

        // FIX: signOut().addOnCompleteListener এ launch করলে callback আসার আগেই
        // onUserLeaveHint() fire হয়ে MainActivity restart হয়ে যেত — activity
        // recreate হওয়ায় googleSignInLauncher ও googleSignInCallback হারিয়ে যেত,
        // ফলে user app থেকে বের হয়ে যেত।
        // এখন isLaunchingInternalActivity আগে set করে, তারপর signOut() চালানো হচ্ছে।
        // signOut task fire হওয়ার আগেই flag set থাকায় onUserLeaveHint() block করা যাবে।
        activity.isLaunchingInternalActivity = true
        activity.googleSignInCallback = { account ->
            if (account == null || account.idToken == null) {
                isLoading = false
                errorMsg = "Google Sign-In বাতিল হয়েছে"
                exactException = "No account or ID token received"
            } else {
                coroutineScope.launch {
                    try {
                        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                        FirebaseAuth.getInstance().signInWithCredential(credential).await()
                        isLoading = false
                        onLoginSuccess()
                        // RasFocus+ Drive backup: fire-and-forget — creates/finds the
                        // "RasFocus+" Drive folder and syncs settings. Doesn't block login.
                        coroutineScope.launch { DriveBackupManager.syncNow(activity) }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMsg = "Google Sign-In Failed"
                        exactException = e.localizedMessage ?: e.toString()
                    }
                }
            }
        }
        // signOut() করে fresh account picker দেখানো হচ্ছে — কিন্তু এখন launch
        // callback এর বাইরে, flag আগেই set, তাই restart হওয়ার ভয় নেই।
        googleSignInClient.signOut().addOnCompleteListener {
            try {
                activity.googleSignInLauncher.launch(googleSignInClient.signInIntent)
            } catch (e: Exception) {
                // launcher failed to start — reset state so user isn't stuck
                isLoading = false
                activity.isLaunchingInternalActivity = false
                errorMsg = "Google Sign-In could not be started"
                exactException = e.localizedMessage ?: e.toString()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.SurfaceOffWhite).systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Box(modifier = Modifier.size(72.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.1f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("RasFocus+", fontSize = 32.sp, fontWeight = FontWeight.Black, color = RasFocusColors.PrimaryTeal)
            Text(if (isSignUpMode) "Create a new account" else "Welcome back", fontSize = 16.sp, color = RasFocusColors.SubtleText)
            Spacer(Modifier.height(32.dp))

            if (errorMsg != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RasFocusColors.ErrorRed.copy(alpha = 0.08f)), border = BorderStroke(1.dp, RasFocusColors.ErrorRed.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) { 
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, tint = RasFocusColors.ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = errorMsg!!, color = RasFocusColors.ErrorRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (exactException != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = exactException!!, color = RasFocusColors.ErrorRed.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (isSignUpMode) {
                OutlinedTextField(value = name, onValueChange = { name = it; errorMsg = null }, label = { Text("Full Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RasFocusColors.PrimaryTeal, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = RasFocusColors.PrimaryTeal, focusedLabelColor = RasFocusColors.PrimaryTeal, unfocusedLabelColor = Color.Gray, focusedLeadingIconColor = RasFocusColors.PrimaryTeal, unfocusedLeadingIconColor = Color.Gray))
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(value = email, onValueChange = { email = it; errorMsg = null }, label = { Text("Email Address please") }, leadingIcon = { Icon(Icons.Outlined.Email, null) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RasFocusColors.PrimaryTeal, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = RasFocusColors.PrimaryTeal, focusedLabelColor = RasFocusColors.PrimaryTeal, unfocusedLabelColor = Color.Gray, focusedLeadingIconColor = RasFocusColors.PrimaryTeal, unfocusedLeadingIconColor = Color.Gray))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it; errorMsg = null }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, trailingIcon = {
                val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(icon, null) }
            }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RasFocusColors.PrimaryTeal, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = RasFocusColors.PrimaryTeal, focusedLabelColor = RasFocusColors.PrimaryTeal, unfocusedLabelColor = Color.Gray, focusedLeadingIconColor = RasFocusColors.PrimaryTeal, unfocusedLeadingIconColor = Color.Gray, focusedTrailingIconColor = Color.Gray, unfocusedTrailingIconColor = Color.Gray))

            Spacer(Modifier.height(24.dp))
            Button(onClick = { processEmailAuth() }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RasFocusShapes.Button, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal, disabledContainerColor = Color.LightGray)) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Text(if (isSignUpMode) "Sign Up" else "Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isSignUpMode) "Already have an account?" else "Don't have an account?", fontSize = 14.sp, color = RasFocusColors.SubtleText)
                TextButton(onClick = { isSignUpMode = !isSignUpMode; errorMsg = null }) { Text(if (isSignUpMode) "Log In" else "Sign Up", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.PrimaryTeal) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = RasFocusColors.DividerColor)
                Text(" OR ", modifier = Modifier.padding(horizontal = 8.dp), color = RasFocusColors.SubtleText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.weight(1f), color = RasFocusColors.DividerColor)
            }
            Spacer(Modifier.height(16.dp))
            // Premium Google Sign-In Button
            val googleInteraction = remember { MutableInteractionSource() }
            val isGooglePressed by googleInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(if (isGooglePressed) 1.dp else 3.dp, RoundedCornerShape(16.dp))
                    .background(
                        color = if (!isLoading) Color.White else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(BorderStroke(1.5.dp, Color(0xFFDADCE0)), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = googleInteraction,
                        indication = null,
                        enabled = !isLoading
                    ) { startGoogleSignIn() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    // Google "G" logo using colored squares
                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val s = size.minDimension
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val r = s / 2f
                            // Blue arc (top-right)
                            drawArc(color = Color(0xFF4285F4), startAngle = -45f, sweepAngle = 135f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(s, s))
                            // Red arc (top-left)
                            drawArc(color = Color(0xFFEA4335), startAngle = -180f, sweepAngle = 135f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(s, s))
                            // Yellow arc (bottom-left)
                            drawArc(color = Color(0xFFFBBC04), startAngle = 135f, sweepAngle = 90f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(s, s))
                            // Green arc (bottom-right)
                            drawArc(color = Color(0xFF34A853), startAngle = -90f + 270f, sweepAngle = 90f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(s, s))
                            // Horizontal bar for "G"
                            drawRect(color = Color(0xFF4285F4), topLeft = androidx.compose.ui.geometry.Offset(cx, cy - s * 0.09f), size = androidx.compose.ui.geometry.Size(r, s * 0.18f))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF3C4043),
                        letterSpacing = 0.25.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now", fontSize = 15.sp, color = RasFocusColors.SubtleText, fontWeight = FontWeight.SemiBold) }
            Text("You can sign in anytime from Settings", fontSize = 12.sp, color = RasFocusColors.SubtleText.copy(alpha = 0.6f))
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================
// SECTION 8 — ROLE SELECTION SCREEN
// ============================================================
@Composable
fun RoleSelectionScreen(onPersonaSelected: (UserPersona) -> Unit, viewModel: MainViewModel) {
    val pendingPersona by viewModel.pendingPersona.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).systemBarsPadding()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(text = "Welcome to\nRasFocus+", fontSize = 34.sp, fontWeight = FontWeight.Black, color = RasFocusColors.OnBackground)
                Spacer(Modifier.height(8.dp))
                Text(text = "How do you want to use RasFocus+?", fontSize = 15.sp, color = RasFocusColors.SubtleText)
                Spacer(Modifier.height(16.dp))
            }
            itemsIndexed(UserPersona.values()) { _, persona ->
                PersonaCard(persona = persona, isSelected = pendingPersona == persona, onClick = { viewModel.selectPendingPersona(persona) })
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        if (pendingPersona != null) {
            Button(onClick = { viewModel.confirmPersona(); pendingPersona?.let { onPersonaSelected(it) } }, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth().height(56.dp), shape = RasFocusShapes.Button, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) {
                Text("Continue with ${pendingPersona?.displayName}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PersonaCard(persona: UserPersona, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(if (isSelected) persona.accentColor else RasFocusColors.DividerColor, label = "")
    val bgColor by animateColorAsState(if (isSelected) persona.accentColor.copy(alpha = 0.06f) else RasFocusColors.SurfaceOffWhite, label = "")
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bgColor), border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(persona.icon, fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                Text(persona.subtitle, fontSize = 13.sp, color = RasFocusColors.SubtleText)
                Spacer(Modifier.height(4.dp))
                Text(persona.description, fontSize = 12.sp, color = RasFocusColors.SubtleText.copy(alpha = 0.8f))
            }
            if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = persona.accentColor, modifier = Modifier.size(26.dp))
        }
    }
}

// ============================================================
// SECTION 9 — PERSONA SCAFFOLD + BOTTOM BAR
// ============================================================
@Composable
fun PersonaScaffold(tabs: List<BottomNavTab>, currentRoute: String, onTabSelect: (BottomNavTab) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(bottomBar = { RasFocusBottomBar(tabs = tabs, currentRoute = currentRoute, onTabSelect = onTabSelect) }) { innerPadding -> content(innerPadding) }
}

@Composable
fun RasFocusBottomBar(tabs: List<BottomNavTab>, currentRoute: String, onTabSelect: (BottomNavTab) -> Unit) {
    NavigationBar(
        containerColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.surface,
        contentColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.textPrimary,
        tonalElevation = 4.dp,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(RoundedCornerShape(24.dp))
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route ||
                (tab is BottomNavTab.MyFocus && (currentRoute == Routes.SELF_CONTROL_DASH || currentRoute == Routes.PARENTAL_DASH || currentRoute == Routes.COMBO_DASH)) ||
                (tab is BottomNavTab.Family  && (currentRoute == Routes.PARENTAL_FAMILY  || currentRoute == Routes.COMBO_FAMILY))
            val iconScale by animateFloatAsState(targetValue = if (selected) 1.25f else 1f, animationSpec = spring(dampingRatio = 0.6f), label = "")
            NavigationBarItem(
                selected = selected, onClick = { onTabSelect(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = tab.label, modifier = Modifier.scale(iconScale)) },
                label = { Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary, 
                    selectedTextColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary, 
                    unselectedIconColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.textSecondary, 
                    unselectedTextColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.textSecondary, 
                    indicatorColor = com.rasel.RasFocus.ui.theme.RasFocusTheme.colors.primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}

// ============================================================
// SECTION 10 — SETTINGS SCREEN
// ============================================================
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onSignOut: () -> Unit, onChangePersona: () -> Unit) {
    val persona by viewModel.selectedPersona.collectAsState()
    val notifEnabled by viewModel.notificationsEnabled.collectAsState()
    val darkMode by viewModel.darkModeEnabled.collectAsState()
    val strictMode by viewModel.strictModeEnabled.collectAsState()
    val isFirebase by viewModel.isFirebaseConnected.collectAsState()
    val firebaseUser = FirebaseAuth.getInstance().currentUser

    Column(modifier = Modifier.fillMaxSize().background(RasFocusColors.SurfaceOffWhite).systemBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().background(RasFocusColors.BackgroundWhite).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = RasFocusColors.OnBackground) }
                Spacer(Modifier.width(8.dp))
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingsCard(title = "Account") {
                    if (isFirebase && firebaseUser != null) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(26.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(firebaseUser.displayName ?: "User", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                                Text(firebaseUser.email ?: "", fontSize = 12.sp, color = RasFocusColors.SubtleText)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(RasFocusColors.SuccessGreen, CircleShape))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Synced with Firebase", fontSize = 11.sp, color = RasFocusColors.SuccessGreen)
                                }
                            }
                        }
                        HorizontalDivider(color = RasFocusColors.DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(icon = Icons.AutoMirrored.Filled.Logout, label = "Sign Out", isDestructive = true, onClick = onSignOut)
                    } else {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PersonOff, null, tint = RasFocusColors.SubtleText)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text("Not signed in", fontWeight = FontWeight.SemiBold, color = RasFocusColors.OnBackground); Text("Sign in to sync devices & control remotely", fontSize = 12.sp, color = RasFocusColors.SubtleText) }
                        }
                        Padding16 { Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Sign in with Email or Google") } }
                    }
                }
            }
            item {
                SettingsCard(title = "Current Mode") {
                    persona?.let { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.icon, fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(p.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground); Text(p.description, fontSize = 12.sp, color = RasFocusColors.SubtleText) }
                            TextButton(onClick = onChangePersona) { Text("Change", fontSize = 13.sp, color = RasFocusColors.PrimaryTeal) }
                        }
                    } ?: run { SettingsRow(icon = Icons.Filled.SwapHoriz, label = "Select a Mode", onClick = onChangePersona) }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), letterSpacing = 1.sp)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = RasFocusColors.BackgroundWhite), elevation = CardDefaults.cardElevation(0.dp), border = BorderStroke(1.dp, RasFocusColors.DividerColor)) { content() }
    }
}
@Composable
fun SettingsRow(icon: ImageVector, label: String, trailing: String? = null, isDestructive: Boolean = false, onClick: (() -> Unit)? = null) {
    val color = if (isDestructive) RasFocusColors.ErrorRed else RasFocusColors.OnBackground
    Row(modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isDestructive) RasFocusColors.ErrorRed else RasFocusColors.PrimaryTeal, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = color, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 14.sp, color = RasFocusColors.SubtleText) else if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = RasFocusColors.SubtleText, modifier = Modifier.size(20.dp))
    }
}
@Composable
private fun Padding16(content: @Composable () -> Unit) { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { content() } }

// ============================================================
// SECTION 11 — PERMISSION SETUP SCREEN (PREMIUM & SKIPPABLE ACCESSIBILITY)
// ============================================================

// Helper: open accessibility settings with deep-link to the exact service on all Android versions
private fun openAccessibilitySettings(context: Context, packageName: String, serviceClass: String) {
    val componentName = "$packageName/$serviceClass"

    // 1. Try Samsung / MIUI / OEM deep-link first (works on many vendor ROMs)
    val deepIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Standard fragment-args deep-link used by AOSP Settings ≥ Android 9
        putExtra(":settings:fragment_args_key", componentName)
        putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
            putString(":settings:fragment_args_key", componentName)
        })
    }
    try {
        context.startActivity(deepIntent)
        return
    } catch (_: Exception) {}

    // 2. Fallback — plain accessibility settings page
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (_: Exception) {}
}

// FIX: MIUI/ColorOS/FuntouchOS/EMUI/Realme UI kill background apps using their OWN
// "Auto-start" / "Background app management" switch — separate from, and often
// ignoring, the stock Android battery-optimization exemption. This is why RasFocus
// (and its blocking services) can still get killed on these phones even after the
// standard "Ignore battery optimizations" permission is granted, which shows up as
// "app fire ashle abar slow lage" after multitasking. No existing service/feature
// logic is touched here — this only opens a settings screen for the user to flip
// manually; each vendor intent is tried in turn and silently skipped if unsupported.
private fun openOemAutoStartSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val candidates = mutableListOf<Intent>()

    when {
        manufacturer.contains("xiaomi") -> candidates.add(
            Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
        )
        manufacturer.contains("oppo") -> {
            candidates.add(Intent().setComponent(android.content.ComponentName(
                "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
            candidates.add(Intent().setComponent(android.content.ComponentName(
                "com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
            candidates.add(Intent().setComponent(android.content.ComponentName(
                "com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
        }
        manufacturer.contains("vivo") -> candidates.add(
            Intent().setComponent(android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ))
        )
        manufacturer.contains("huawei") -> {
            candidates.add(Intent().setComponent(android.content.ComponentName(
                "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            candidates.add(Intent().setComponent(android.content.ComponentName(
                "com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
        }
        manufacturer.contains("oneplus") -> candidates.add(
            Intent().setComponent(android.content.ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ))
        )
        manufacturer.contains("realme") -> candidates.add(
            Intent().setComponent(android.content.ComponentName(
                "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ))
        )
        manufacturer.contains("samsung") -> candidates.add(
            Intent().setComponent(android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ))
        )
    }

    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) { /* this vendor screen doesn't exist on this device/ROM version — try next */ }
    }

    // Fallback for every other manufacturer (or if none of the vendor screens exist):
    // app's own detail settings page, where "Battery" → "Unrestricted" lives on stock Android.
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (_: Exception) {}
}

@Composable
fun PermissionSetupScreen(persona: UserPersona?, onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val needsAccessibility = persona != UserPersona.PARENTAL

    // Correct fully-qualified service class names for each persona
    // FIX: was pointing at RasFocusAccessibilityService / RasAccessibilityService —
    // pre-refactor class names that are no longer declared as an
    // <accessibilityservice> in AndroidManifest.xml (only UnifiedBlockerService
    // is). Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES can therefore never
    // contain those old names, so hasAccessibility() below always returned
    // false — even with the real service turned on in system Settings.
    val accessibilityServiceClass = "com.rasel.RasFocus.selfcontrol.UnifiedBlockerService"

    fun hasAccessibility(): Boolean {
        if (!needsAccessibility) return true
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
                return true
            }
        }
        return false
    }

    fun hasUsageStats(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }
    }

    fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    fun hasNotification(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

    fun hasBatteryOpt(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    var accessOk  by remember { mutableStateOf(hasAccessibility()) }
    var accessSkipped by remember { mutableStateOf(false) }
    var usageOk   by remember { mutableStateOf(hasUsageStats()) }
    var usageSkipped by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(hasOverlay()) }
    var notifOk   by remember { mutableStateOf(hasNotification()) }
    var batteryOk by remember { mutableStateOf(hasBatteryOpt()) }
    var batterySkipped by remember { mutableStateOf(false) }

    // FIX: Use LocalLifecycleOwner directly which is standard and safe in Compose
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessOk  = hasAccessibility()
                usageOk   = hasUsageStats()
                overlayOk = hasOverlay()
                notifOk   = hasNotification()
                batteryOk = hasBatteryOpt()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessEffectivelyOk = accessOk || accessSkipped
    val usageEffectivelyOk = usageOk || usageSkipped
    val batteryEffectivelyOk = batteryOk || batterySkipped

    val requiredPermissionsMet = (if (needsAccessibility) accessEffectivelyOk else true) && usageEffectivelyOk && overlayOk && notifOk && batteryEffectivelyOk
    val totalReq   = if (needsAccessibility) 5 else 4
    val grantedReq = listOfNotNull(
        if (needsAccessibility) accessEffectivelyOk else null,
        usageEffectivelyOk, overlayOk, notifOk, batteryEffectivelyOk
    ).count { it }
    val progress   = grantedReq.toFloat() / totalReq.toFloat()

    val accessibilitySettingsHint = when (persona) {
        UserPersona.PARENTAL -> ""
        UserPersona.STUDENT  -> "RasFocus+ Focus Service"
        else                 -> "RasFocus+ App Blocker"
    }

    // Sticky-bottom layout: button always visible, permission rows scroll above it
    Scaffold(
        containerColor = RasFocusColors.BackgroundWhite,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RasFocusColors.BackgroundWhite)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (requiredPermissionsMet) {
                    Button(
                        onClick = {
                            try { UsageNotificationService.start(context) } catch (_: Exception) {}
                            onAllGranted()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.SuccessGreen)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("ALL SET! ENTER APP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Grant required permissions above, then tap Continue.",
                        fontSize = 13.sp,
                        color = RasFocusColors.SubtleText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try { UsageNotificationService.start(context) } catch (_: Exception) {}
                            onAllGranted()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, RasFocusColors.PrimaryTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RasFocusColors.PrimaryTeal)
                    ) {
                        Text("Continue anyway \u2192", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.BackgroundWhite)
            .systemBarsPadding()
            .padding(innerPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Header row — icon + title + badge compact
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Security, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("System Permissions", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RasFocusColors.OnBackground)
                if (persona != null) {
                    Text("${persona.icon} ${persona.displayName} mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = persona.accentColor)
                }
            }
            Spacer(Modifier.weight(1f))
            // Progress chip
            Box(
                modifier = Modifier.background(
                    if (requiredPermissionsMet) RasFocusColors.SuccessGreen.copy(0.12f) else RasFocusColors.DividerColor,
                    RoundedCornerShape(50.dp)
                ).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "$grantedReq/$totalReq",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (requiredPermissionsMet) RasFocusColors.SuccessGreen else RasFocusColors.SubtleText
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = RasFocusColors.SuccessGreen,
            trackColor = RasFocusColors.DividerColor
        )
        Spacer(Modifier.height(14.dp))

        // Permission rows — compact version to fit on one screen
        if (needsAccessibility) {
            CompactPermissionRow(
                icon = Icons.Filled.Accessibility,
                title = "Accessibility Service",
                desc = "App blocking ($accessibilitySettingsHint)",
                isGranted = accessEffectivelyOk,
                isRequired = false,
                onClick = { openAccessibilitySettings(context, context.packageName, accessibilityServiceClass) },
                onSkip = { accessSkipped = true }
            )
            Spacer(Modifier.height(8.dp))
        }

        CompactPermissionRow(
            icon = Icons.Filled.BarChart,
            title = "Usage Access",
            desc = "Track screen time & app launches",
            isGranted = usageEffectivelyOk,
            isRequired = false,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        return@CompactPermissionRow
                    } catch (_: Exception) {}
                }
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            },
            onSkip = { usageSkipped = true }
        )
        Spacer(Modifier.height(8.dp))

        CompactPermissionRow(
            icon = Icons.Filled.Layers,
            title = "Display Over Apps",
            desc = "Show block screen on top of apps",
            isGranted = overlayOk,
            isRequired = true,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        )
        Spacer(Modifier.height(8.dp))

        CompactPermissionRow(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            desc = "Show usage stats in notification bar",
            isGranted = notifOk,
            isRequired = true,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            }
        )
        Spacer(Modifier.height(8.dp))

        CompactPermissionRow(
            icon = Icons.Filled.BatteryFull,
            title = "Battery Optimization",
            desc = "Keep RasFocus running 24 hours",
            isGranted = batteryEffectivelyOk,
            isRequired = false,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            },
            onSkip = { batterySkipped = true }
        )
        Spacer(Modifier.height(8.dp))

        CompactPermissionRow(
            icon = Icons.Filled.RocketLaunch,
            title = "Auto-Start (MIUI/Oppo/Vivo)",
            desc = "Background এ app বন্ধ হবে না",
            isGranted = true,
            isRequired = false,
            onClick = { openOemAutoStartSettings(context) }
        )

        Spacer(Modifier.height(20.dp))
    } // Column
    } // Scaffold
}

// Compact one-screen permission row (smaller than PremiumPermissionRow)
@Composable
fun CompactPermissionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isGranted) RasFocusColors.SuccessGreen.copy(0.06f) else Color.White),
        border = BorderStroke(1.dp, if (isGranted) RasFocusColors.SuccessGreen.copy(0.35f) else RasFocusColors.DividerColor),
        elevation = CardDefaults.cardElevation(if (isGranted) 0.dp else 1.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(
                    if (isGranted) RasFocusColors.SuccessGreen.copy(0.15f) else RasFocusColors.PrimaryTeal.copy(0.1f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (isGranted) RasFocusColors.SuccessGreen else RasFocusColors.PrimaryTeal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RasFocusColors.OnBackground)
                    if (isRequired && !isGranted) {
                        Spacer(Modifier.width(5.dp))
                        Box(modifier = Modifier.background(RasFocusColors.ErrorRed.copy(0.1f), RoundedCornerShape(50.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("Required", fontSize = 8.sp, color = RasFocusColors.ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(desc, fontSize = 11.sp, color = RasFocusColors.SubtleText)
            }
            Spacer(Modifier.width(8.dp))
            if (isGranted) {
                Icon(Icons.Filled.CheckCircle, null, tint = RasFocusColors.SuccessGreen, modifier = Modifier.size(26.dp))
            } else if (!isRequired && onSkip != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) { Text("Skip", fontSize = 11.sp, color = RasFocusColors.SubtleText) }
                }
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun PremiumPermissionRow(icon: ImageVector, title: String, desc: String, isGranted: Boolean, isRequired: Boolean, onClick: () -> Unit, onSkip: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isGranted) RasFocusColors.SuccessGreen.copy(0.06f) else Color.White),
        border = BorderStroke(1.dp, if (isGranted) RasFocusColors.SuccessGreen.copy(0.4f) else RasFocusColors.DividerColor),
        elevation = CardDefaults.cardElevation(if (isGranted) 0.dp else 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(if (isGranted) RasFocusColors.SuccessGreen.copy(0.15f) else RasFocusColors.PrimaryTeal.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (isGranted) RasFocusColors.SuccessGreen else RasFocusColors.PrimaryTeal, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RasFocusColors.OnBackground)
                    if (isRequired && !isGranted) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.background(RasFocusColors.ErrorRed.copy(0.1f), RoundedCornerShape(50.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Required", fontSize = 9.sp, color = RasFocusColors.ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isRequired && !isGranted) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.background(Color.Gray.copy(0.1f), RoundedCornerShape(50.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Optional", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (isGranted) {
                Icon(Icons.Filled.CheckCircle, null, tint = RasFocusColors.SuccessGreen, modifier = Modifier.size(32.dp))
            } else if (!isRequired && onSkip != null) {
                // Optional permission: show both Enable and Skip buttons
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Skip", fontSize = 12.sp, color = RasFocusColors.SubtleText)
                    }
                }
            } else {
                Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                    Text("Enable", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// SECTION 12 — 24HR USAGE NOTIFICATION SERVICE (DYNAMIC)
// ============================================================
class UsageNotificationService : Service() {
    companion object {
        const val CHANNEL_ID = "rasfocus_usage_channel"
        const val NOTIF_ID   = 9001
        fun start(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { updateNotification(); handler.postDelayed(this, 10_000) } // Update faster to catch acc status
    }
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("RasFocus Active", "Tracking usage..."))
        handler.post(updateRunnable)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(updateRunnable); super.onDestroy() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "RasFocus Usage Stats", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows your daily screen time"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(packageName)
    }

    private fun isProtectionActuallyWorking(): Boolean {
        return com.rasel.RasFocus.selfcontrol.UnifiedBlockerService.instance != null
    }

    private fun getUsageStats(): Pair<String, Int> {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - (24 * 60 * 60 * 1000L), now)
            val totalMs = stats?.sumOf { it.totalTimeInForeground } ?: 0L
            val hours = (totalMs / 3_600_000).toInt()
            val mins  = ((totalMs % 3_600_000) / 60_000).toInt()
            Pair(if (hours > 0) "${hours}h ${mins}m" else "${mins}m", mins + hours * 60)
        } catch (e: Exception) { Pair("--", 0) }
    }

    private fun updateNotification() {
        // ── FIX: Dynamic Notification Logic ──
        if (!isAccessibilityServiceEnabled()) {
            // যদি অ্যাক্সেসিবিলিটি অফ থাকে, তাহলে সার্ভিসটি স্টপ করে দেওয়া হবে (নোটিফিকেশন গায়েব হয়ে যাবে)
            stopForeground(true)
            stopSelf()
            return
        }

        val (timeText, totalMins) = getUsageStats()
        val isWorking = isProtectionActuallyWorking()
        val lockStatus = if (isWorking) "Locked" else "Unlocked"
        val status = when { 
            totalMins > 300 -> "High usage today | $lockStatus" 
            totalMins > 120 -> "Moderate usage | $lockStatus" 
            else -> "Good focus today | $lockStatus" 
        }
        val iconRes = if (isWorking) R.drawable.ic_notif_lock_locked else R.drawable.ic_notif_lock_unlocked
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Screen Time: $timeText", status, iconRes))
        FirebaseRepository.updateSelfDeviceStatus("self_device", 75)
    }

    private fun buildNotification(title: String, content: String, iconRes: Int = android.R.drawable.ic_secure): android.app.Notification {
        val pi = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setContentIntent(pi)
            .setSilent(true)
            .build()
    }
}

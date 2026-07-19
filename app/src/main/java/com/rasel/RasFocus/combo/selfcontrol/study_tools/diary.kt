package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.net.Uri
import com.rasel.RasFocus.drivebackup.DriveBackupManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Brand colors (WriteDiary inspired — magenta accent, clean white) ─────────
private val WDPink     = Color(0xFFE91E8C)
private val WDPinkDark = Color(0xFFC2185B)
private val WDPinkBg   = Color(0xFFFCE4EC)
private val WDGreen    = Color(0xFF4CAF50)
private val WDBg       = Color(0xFFFFFFFF)
private val WDText     = Color(0xFF1A1A1A)
private val WDSub      = Color(0xFF888888)
private val WDLine     = Color(0xFFE8E8E8)
private val WDDateBg   = Color(0xFF4CAF50)

// ── Biometric ─────────────────────────────────────────────────────────────────
fun launchBiometric(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val bm = BiometricManager.from(activity)
    val canAuth = bm.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) { onError("Biometric not available"); return }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); onSuccess() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { super.onAuthenticationError(errorCode, errString); onError(errString.toString()) }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Diary Entry").setSubtitle("Use fingerprint or device credential")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

// ── Lock Screen ───────────────────────────────────────────────────────────────
@Composable
fun DiaryLockScreen(entry: DiaryEntry, onUnlock: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    val vm: DiaryViewModel = viewModel()
    Box(
        modifier = Modifier.fillMaxSize().background(WDBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(80.dp).background(WDPinkBg, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Lock, null, tint = WDPink, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("Locked Entry", color = WDText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(entry.title.ifBlank { "Untitled" }, color = WDSub, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { if (it.length <= 6) pinInput = it; pinError = false },
                label = { Text("Enter PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, isError = pinError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WDPink, unfocusedBorderColor = WDLine,
                    focusedLabelColor = WDPink
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (pinError) Text("Wrong PIN. Try again.", color = Color.Red, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (vm.verifyPin(pinInput)) onUnlock() else pinError = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WDPink)
            ) { Text("Unlock with PIN", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    (context as? FragmentActivity)?.let { activity ->
                        launchBiometric(activity,
                            onSuccess = { vm.unlockWithBiometric(); onUnlock() },
                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, WDPink)
            ) {
                Icon(Icons.Default.Fingerprint, null, tint = WDPink)
                Spacer(Modifier.width(8.dp))
                Text("Use Biometric", color = WDPink, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("Cancel", color = WDSub) }
        }
    }
}

// ── Set PIN Dialog ────────────────────────────────────────────────────────────
@Composable
fun SetPinDialog(currentEntry: DiaryEntry, onDismiss: () -> Unit, onPinSet: (String) -> Unit, onRemovePin: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentEntry.isLocked) "Change/Remove PIN" else "Set PIN Lock", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (currentEntry.isLocked) {
                    OutlinedButton(onClick = onRemovePin, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.Red)) {
                        Icon(Icons.Default.LockOpen, null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove PIN Lock", color = Color.Red)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) pin = it; error = "" },
                    label = { Text("New PIN (4-6 digits)") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WDPink, focusedLabelColor = WDPink)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirmPin, onValueChange = { if (it.length <= 6) confirmPin = it; error = "" },
                    label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WDPink, focusedLabelColor = WDPink)
                )
                if (error.isNotBlank()) Text(error, color = Color.Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                when { pin.length < 4 -> error = "Minimum 4 digits"; pin != confirmPin -> error = "PINs don't match"; else -> onPinSet(pin) }
            }, colors = ButtonDefaults.buttonColors(containerColor = WDPink)) { Text("Set PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Calendar Screen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryCalendarScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onNewEntryForDay: (String) -> Unit = {},   // ★ NEW: create entry for selected date
    onBack: () -> Unit
) {
    val today = Calendar.getInstance()
    var displayedMonth by remember { mutableStateOf(today.get(Calendar.MONTH)) }
    var displayedYear  by remember { mutableStateOf(today.get(Calendar.YEAR)) }
    val entryDays = remember(entries, displayedMonth, displayedYear) {
        entries.mapNotNull { entry ->
            runCatching {
                val cal = Calendar.getInstance()
                cal.time = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).parse(entry.date) ?: return@mapNotNull null
                if (cal.get(Calendar.MONTH) == displayedMonth && cal.get(Calendar.YEAR) == displayedYear)
                    cal.get(Calendar.DAY_OF_MONTH) else null
            }.getOrNull()
        }.toSet()
    }
    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    val selectedEntries = remember(entries, selectedDay, displayedMonth, displayedYear) {
        entries.filter { entry ->
            runCatching {
                val cal = Calendar.getInstance()
                cal.time = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).parse(entry.date) ?: return@filter false
                cal.get(Calendar.DAY_OF_MONTH) == selectedDay && cal.get(Calendar.MONTH) == displayedMonth && cal.get(Calendar.YEAR) == displayedYear
            }.getOrElse { false }
        }
    }
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
        Calendar.getInstance().also { it.set(displayedYear, displayedMonth, 1) }.time
    )
    val daysInMonth   = Calendar.getInstance().also { it.set(displayedYear, displayedMonth, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = Calendar.getInstance().also { it.set(displayedYear, displayedMonth, 1) }.get(Calendar.DAY_OF_WEEK) - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WDPink)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(WDBg)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(WDPinkBg).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (displayedMonth == 0) { displayedMonth = 11; displayedYear-- } else displayedMonth-- }) {
                    Icon(Icons.Default.ChevronLeft, null, tint = WDPink)
                }
                Text(monthName, color = WDPink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (displayedMonth == 11) { displayedMonth = 0; displayedYear++ } else displayedMonth++ }) {
                    Icon(Icons.Default.ChevronRight, null, tint = WDPink)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat").forEach { day ->
                    Text(day, fontSize = 11.sp, color = WDSub, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            val rows = (firstDayOfWeek + daysInMonth + 6) / 7
            LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height((rows * 48).dp).padding(horizontal = 8.dp)) {
                items(rows * 7) { index ->
                    val day = index - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) { Box(Modifier.size(40.dp)) } else {
                        val isToday = day == today.get(Calendar.DAY_OF_MONTH) && displayedMonth == today.get(Calendar.MONTH) && displayedYear == today.get(Calendar.YEAR)
                        val hasEntry = day in entryDays
                        val isSelected = day == selectedDay
                        Box(
                            modifier = Modifier.size(40.dp).padding(2.dp).clip(CircleShape)
                                .background(if (isSelected) WDPink else if (isToday) WDPinkBg else Color.Transparent)
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", fontSize = 14.sp, color = if (isSelected) Color.White else WDText,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (hasEntry) Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else WDPink))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = WDLine)
            if (selectedEntries.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No entries on this day", color = WDSub)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Format the selected day as diary date string and open editor
                            val cal = Calendar.getInstance().also {
                                it.set(displayedYear, displayedMonth, selectedDay)
                            }
                            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(cal.time)
                            onNewEntryForDay(dateStr)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WDPink),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New entry for this day", color = Color.White)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(selectedEntries) { entry ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEntryClick(entry) },
                            colors = CardDefaults.cardColors(containerColor = WDPinkBg), shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (entry.isLocked) { Icon(Icons.Default.Lock, null, tint = WDPink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
                                Column { Text(entry.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold, color = WDText); Text(entry.folder, fontSize = 12.sp, color = WDSub) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Reminder Dialog ───────────────────────────────────────────────────────────
@Composable
fun ReminderDialog(currentEntry: DiaryEntry, onDismiss: () -> Unit, onSetReminder: (Long, String) -> Unit, onClearReminder: () -> Unit) {
    val context = LocalContext.current
    var reminderLabel by remember { mutableStateOf(currentEntry.reminderLabel.ifBlank { "Write in your diary" }) }
    var selectedDateTimeMs by remember { mutableStateOf(if (currentEntry.reminderTimeMillis > 0) currentEntry.reminderTimeMillis else System.currentTimeMillis() + 3_600_000) }
    val cal = Calendar.getInstance().also { it.timeInMillis = selectedDateTimeMs }
    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ Set Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = reminderLabel, onValueChange = { reminderLabel = it },
                    label = { Text("Reminder message") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WDPink, focusedLabelColor = WDPink))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); selectedDateTimeMs = cal.timeInMillis },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, WDPink)) {
                    Icon(Icons.Default.DateRange, null, tint = WDPink); Spacer(Modifier.width(8.dp))
                    Text("Date: $dateStr", color = WDPink)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); selectedDateTimeMs = cal.timeInMillis },
                        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, WDPink)) {
                    Icon(Icons.Default.Schedule, null, tint = WDPink); Spacer(Modifier.width(8.dp))
                    Text("Time: $timeStr", color = WDPink)
                }
                if (currentEntry.reminderTimeMillis > 0) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onClearReminder, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NotificationsOff, null, tint = Color.Red); Spacer(Modifier.width(8.dp))
                        Text("Clear Reminder", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSetReminder(selectedDateTimeMs, reminderLabel) }, colors = ButtonDefaults.buttonColors(containerColor = WDPink)) { Text("Set Reminder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// ENTRY LIST SCREEN (WriteDiary style — date badge + title + snippet)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onNewEntry: () -> Unit,
    onMenuClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WriteDiary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewEntry,
                containerColor = WDPink,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(26.dp))
            }
        },
        containerColor = WDBg
    ) { padding ->
        if (entries.isEmpty()) {
            // Empty state — WriteDiary style illustration
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(280.dp).background(WDPink),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📔", fontSize = 64.sp)
                            Text("No entries yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Tap + to write your first entry", color = Color.White.copy(.7f), fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(entries) { entry ->
                    DiaryListItem(entry = entry, onClick = { onEntryClick(entry) })
                    HorizontalDivider(color = WDLine, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun DiaryListItem(entry: DiaryEntry, onClick: () -> Unit) {
    // Parse day/month/year from date string
    val (day, month, year) = remember(entry.date) {
        runCatching {
            val cal = Calendar.getInstance()
            cal.time = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).parse(entry.date)!!
            Triple(
                cal.get(Calendar.DAY_OF_MONTH).toString(),
                SimpleDateFormat("MMM", Locale.ENGLISH).format(cal.time).uppercase(),
                cal.get(Calendar.YEAR).toString()
            )
        }.getOrElse { Triple("--", "---", "----") }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(WDBg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date badge (green, WriteDiary style)
        Box(
            modifier = Modifier.size(width = 56.dp, height = 68.dp)
                .background(WDDateBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(month, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(day, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
                Text(year, color = Color.White.copy(.75f), fontSize = 9.sp)
            }
        }

        // Content
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isLocked) Icon(Icons.Default.Lock, null, tint = WDPink, modifier = Modifier.size(13.dp))
                Text(
                    entry.title.ifBlank { "Untitled" },
                    color = WDPink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.body.isNotBlank()) {
                Text(
                    entry.body.take(60).replace("\n", " "),
                    color = WDPink.copy(.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.mood.isNotBlank()) {
                Text(entry.mood, color = WDSub, fontSize = 11.sp)
            }
        }

        // Mood emoji or lock
        if (entry.mood.isNotBlank()) {
            Text(entry.mood.take(2), fontSize = 18.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EDITOR SCREEN (WriteDiary "Write note" style — lined paper + attachment bar)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorScreen(
    entry: DiaryEntry,
    saveStatus: SaveStatus,
    onEntryChange: (DiaryEntry) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSetPin: () -> Unit,
    onSetReminder: () -> Unit
) {
    // ★ Photo state — list of (uri, x_offset, y_offset, scale)
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    // Image picker launcher
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { photos = photos + PhotoItem(it) }
    }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showMoodMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write note", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    // Mood
                    Box {
                        IconButton(onClick = { showMoodMenu = true }) {
                            Icon(
                                if (entry.mood.isNotBlank()) Icons.Default.Face else Icons.Outlined.SentimentSatisfied,
                                null, tint = Color.White
                            )
                        }
                        DropdownMenu(expanded = showMoodMenu, onDismissRequest = { showMoodMenu = false }) {
                            listOf("😊 Happy","😢 Sad","😠 Angry","🎉 Excited","😐 Neutral","😰 Anxious","😴 Tired","🤔 Thoughtful").forEach { mood ->
                                DropdownMenuItem(
                                    text = { Text(mood) },
                                    onClick = { onEntryChange(entry.copy(mood = mood)); showMoodMenu = false }
                                )
                            }
                        }
                    }
                    // Save status
                    IconButton(onClick = {}) {
                        Icon(
                            if (saveStatus == SaveStatus.SAVING) Icons.Default.Sync else Icons.Default.Check,
                            null, tint = Color.White
                        )
                    }
                    // More menu
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text(if (entry.isLocked) "Change PIN" else "Set PIN Lock") },
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = WDPink) },
                                onClick = { showMoreMenu = false; onSetPin() })
                            DropdownMenuItem(text = { Text("Set Reminder") },
                                leadingIcon = { Icon(Icons.Default.Alarm, null, tint = WDPink) },
                                onClick = { showMoreMenu = false; onSetReminder() })
                            Box { // Folder submenu
                                DropdownMenuItem(text = { Text("Move to folder: ${entry.folder}") },
                                    leadingIcon = { Icon(Icons.Default.Folder, null, tint = WDPink) },
                                    onClick = { showFolderMenu = true; showMoreMenu = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete", color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                onClick = { showMoreMenu = false; onDelete() })
                        }
                        DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
                            listOf("General","Work","Personal","Secret").forEach { folder ->
                                DropdownMenuItem(text = { Text(folder) }, onClick = { onEntryChange(entry.copy(folder = folder)); showFolderMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WDPink)
            )
        },
        containerColor = WDBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Attachment toolbar — Photo only ─────────────────────────
            Surface(color = WDPinkBg, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AttachBtn(Icons.Default.Image, "Add Photo") { photoLauncher.launch("image/*") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${entry.body.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }} words",
                        color = WDSub, fontSize = 11.sp
                    )
                }
            }

            // ── Date banner ───────────────────────────────────────────────
            Surface(color = WDPinkBg) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, null, tint = WDPink, modifier = Modifier.size(18.dp))
                    val dateDisplay = entry.date.ifBlank {
                        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(Date())
                    }
                    Text(dateDisplay, color = WDPink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (entry.isLocked) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, null, tint = WDPink, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // ── Lined paper editor ────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Title input
                OutlinedTextField(
                    value = entry.title,
                    onValueChange = { onEntryChange(entry.copy(title = it)) },
                    placeholder = { Text("Add title", color = WDSub.copy(.7f), fontSize = 18.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = WDText),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        cursorColor = WDPink
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = WDSub.copy(.5f), modifier = Modifier.size(18.dp)) }
                )

                HorizontalDivider(color = WDPink.copy(.3f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                // ── Realistic notebook canvas ─────────────────────────────
                // Paper colors
                val paperBg     = Color(0xFFFFFDE7)   // warm cream — real notebook paper
                val lineBlue    = Color(0xFFBBDEFB)   // college-ruled blue lines
                val marginRed   = Color(0xFFEF9A9A)   // red margin line (classic notebook)
                val shadowColor = Color(0x22000000)
                val lineSpacingDp = 32.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 600.dp)
                        .padding(horizontal = 4.dp)
                        .shadow(4.dp, RoundedCornerShape(2.dp))
                        .background(paperBg, RoundedCornerShape(2.dp))
                ) {
                    val density = LocalDensity.current

                    // ── Canvas: draw lines + margin ──────────────────────
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 600.dp)
                    ) {
                        val spacing   = lineSpacingDp.toPx()
                        val marginX   = 56.dp.toPx()

                        // Subtle top header band (like spiral notebook header)
                        drawRect(
                            color = Color(0xFFF8BBD0).copy(alpha = 0.35f),
                            size  = androidx.compose.ui.geometry.Size(size.width, spacing * 1.5f)
                        )

                        // Horizontal ruled lines — start after header
                        var y = spacing * 2f
                        while (y < size.height) {
                            drawLine(
                                color       = lineBlue,
                                start       = Offset(0f, y),
                                end         = Offset(size.width, y),
                                strokeWidth = 1.2f
                            )
                            y += spacing
                        }

                        // Vertical red margin line
                        drawLine(
                            color       = marginRed,
                            start       = Offset(marginX, 0f),
                            end         = Offset(marginX, size.height),
                            strokeWidth = 1.8f
                        )

                        // Left spiral holes (3 circles evenly spaced)
                        val holeX = 14.dp.toPx()
                        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                            val holeY = size.height * fraction
                            drawCircle(
                                color  = Color(0xFFD7CCC8),
                                radius = 7.dp.toPx(),
                                center = Offset(holeX, holeY)
                            )
                            drawCircle(
                                color  = paperBg,
                                radius = 5.dp.toPx(),
                                center = Offset(holeX, holeY)
                            )
                        }

                        // Subtle right-edge shadow (paper depth)
                        val shadowBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, shadowColor),
                            startX = size.width - 8.dp.toPx(),
                            endX   = size.width
                        )
                        drawRect(brush = shadowBrush)
                    }

                    // ── Text input — aligns with ruled lines ─────────────
                    OutlinedTextField(
                        value = entry.body,
                        onValueChange = { onEntryChange(entry.copy(body = it)) },
                        placeholder = {
                            Text(
                                "Start writing here...",
                                color = Color(0xFFBCAAA4),
                                fontSize = 15.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 600.dp)
                            .padding(
                                start  = 66.dp,   // right of margin line + gap
                                end    = 12.dp,
                                top    = (lineSpacingDp * 1.6f)  // below header band
                            ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize   = 15.sp,
                            color      = Color(0xFF37474F),
                            lineHeight = 32.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor          = WDPink,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        minLines = 18
                    )

                    // ── Photos — draggable + pinch-to-resize ─────────────
                    photos.forEachIndexed { idx, photo ->
                        DraggablePhoto(
                            photo    = photo,
                            onUpdate = { updated -> photos = photos.toMutableList().also { it[idx] = updated } },
                            onDelete = { photos = photos.toMutableList().also { it.removeAt(idx) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Icon(icon, null, tint = WDPink, modifier = Modifier.size(22.dp))
        Text(label, color = WDPink, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Photo item state ───────────────────────────────────────────────────────────
private data class PhotoItem(
    val uri: Uri,
    val offsetX: Float = 60f,
    val offsetY: Float = 60f,
    val scale: Float   = 1f
)

// ── Draggable + pinch-to-resize photo overlay ─────────────────────────────────
@Composable
private fun DraggablePhoto(
    photo: PhotoItem,
    onUpdate: (PhotoItem) -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset { IntOffset(photo.offsetX.toInt(), photo.offsetY.toInt()) }
            .size((120 * photo.scale).dp, (120 * photo.scale).dp)
            .pointerInput(Unit) {
                // Two-finger pinch to scale
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (photo.scale * zoom).coerceIn(0.4f, 4f)
                    val newX = photo.offsetX + pan.x
                    val newY = photo.offsetY + pan.y
                    onUpdate(photo.copy(offsetX = newX, offsetY = newY, scale = newScale))
                }
            }
            .pointerInput(Unit) {
                // Single-finger drag
                detectDragGestures(
                    onDragStart = { showDelete = true },
                    onDragEnd   = {},
                    onDrag = { _, drag ->
                        onUpdate(photo.copy(
                            offsetX = photo.offsetX + drag.x,
                            offsetY = photo.offsetY + drag.y
                        ))
                    }
                )
            }
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                .border(2.dp, WDPink.copy(.5f), RoundedCornerShape(8.dp))
        )
        // Delete button — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(WDPink, CircleShape)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN — orchestrates list ↔ editor ↔ calendar
// ══════════════════════════════════════════════════════════════════════════════
private sealed class DiaryNav {
    object LIST : DiaryNav()
    object EDITOR : DiaryNav()
    object CALENDAR : DiaryNav()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDiaryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DiaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val currentEntry  by viewModel.currentEntry.collectAsState()
    val allEntries    by viewModel.allEntries.collectAsState()
    val selectedFilter by viewModel.selectedFolderFilter.collectAsState()
    val saveStatus    by viewModel.saveStatus.collectAsState()
    val isUnlocked    by viewModel.isUnlocked.collectAsState()
    val cloudStatus   by viewModel.cloudStatus.collectAsState()

    var nav by remember { mutableStateOf<DiaryNav>(DiaryNav.LIST) }
    var showSetPinDialog   by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showExportMenu     by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.forceSaveOnExit() } }
    LaunchedEffect(Unit) { if (DiaryCloudSync.isLoggedIn()) viewModel.syncFromCloud() }

    // Lock screen
    if (nav == DiaryNav.EDITOR && currentEntry.isLocked && !isUnlocked) {
        DiaryLockScreen(
            entry = currentEntry,
            onUnlock = { viewModel.unlockWithBiometric() },
            onCancel = { nav = DiaryNav.LIST }
        )
        return
    }

    // Calendar screen
    if (nav == DiaryNav.CALENDAR) {
        DiaryCalendarScreen(
            entries = allEntries,
            onEntryClick = { entry -> viewModel.loadEntry(entry); nav = DiaryNav.EDITOR },
            onNewEntryForDay = { dateStr ->
                // Create a new blank entry pre-filled with the selected date
                viewModel.startNewEntry()
                viewModel.updateEntry(viewModel.currentEntry.value.copy(date = dateStr))
                nav = DiaryNav.EDITOR
            },
            onBack = { nav = DiaryNav.LIST }
        )
        return
    }

    // Editor screen
    if (nav == DiaryNav.EDITOR) {
        DiaryEditorScreen(
            entry = currentEntry,
            saveStatus = saveStatus,
            onEntryChange = { viewModel.updateEntry(it) },
            onBack = { nav = DiaryNav.LIST },
            onDelete = {
                viewModel.deleteEntry(currentEntry)
                Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                nav = DiaryNav.LIST
            },
            onSetPin   = { showSetPinDialog = true },
            onSetReminder = { showReminderDialog = true }
        )
        // Dialogs on top of editor
        if (showSetPinDialog) {
            SetPinDialog(
                currentEntry = currentEntry,
                onDismiss = { showSetPinDialog = false },
                onPinSet = { pin -> viewModel.setPin(pin); showSetPinDialog = false; Toast.makeText(context, "PIN set", Toast.LENGTH_SHORT).show() },
                onRemovePin = { viewModel.removePin(); showSetPinDialog = false; Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show() }
            )
        }
        if (showReminderDialog) {
            ReminderDialog(
                currentEntry = currentEntry,
                onDismiss = { showReminderDialog = false },
                onSetReminder = { ms, label -> viewModel.setReminder(context, ms, label); showReminderDialog = false; Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show() },
                onClearReminder = { viewModel.clearReminder(context); showReminderDialog = false }
            )
        }
        return
    }

    // List screen with sidebar drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = Color(0xFF1A1A1A)) {
                DiaryDrawer(
                    selectedFilter = selectedFilter,
                    cloudStatus = cloudStatus,
                    isLoggedIn = DiaryCloudSync.isLoggedIn(),
                    allEntries = allEntries,
                    onFilterSelect = { viewModel.setFolderFilter(it); scope.launch { drawerState.close() } },
                    onNewEntry = { viewModel.startNewEntry(); nav = DiaryNav.EDITOR; scope.launch { drawerState.close() } },
                    onCalendar = { nav = DiaryNav.CALENDAR; scope.launch { drawerState.close() } },
                    onSync = { viewModel.syncToCloud() },
                    onExport = { showExportMenu = true; scope.launch { drawerState.close() } },
                    onEntryClick = { entry -> viewModel.loadEntry(entry); nav = DiaryNav.EDITOR; scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        DiaryListScreen(
            entries = allEntries,
            onEntryClick = { entry -> viewModel.loadEntry(entry); nav = DiaryNav.EDITOR },
            onNewEntry   = { viewModel.startNewEntry(); nav = DiaryNav.EDITOR },
            onMenuClick  = { scope.launch { drawerState.open() } }
        )
    }

    if (showExportMenu) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("📄 Export as PDF") },
            text = { Text("Choose what to export.") },
            confirmButton = {
                Column {
                    Button(onClick = {
                        val file = DiaryPdfExporter.exportSingleEntry(context, currentEntry)
                        if (file != null) context.startActivity(Intent.createChooser(DiaryPdfExporter.getShareIntent(context, file), "Share PDF"))
                        else Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                        showExportMenu = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = WDPink)) { Text("Current Entry") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val file = DiaryPdfExporter.exportAllEntries(context, allEntries)
                        if (file != null) context.startActivity(Intent.createChooser(DiaryPdfExporter.getShareIntent(context, file), "Share PDF"))
                        else Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                        showExportMenu = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = WDPink)) { Text("All Entries") }
                }
            },
            dismissButton = { TextButton(onClick = { showExportMenu = false }) { Text("Cancel") } }
        )
    }
}

// ── Sidebar Drawer ────────────────────────────────────────────────────────────
@Composable
private fun DiaryDrawer(
    selectedFilter: String, cloudStatus: CloudStatus, isLoggedIn: Boolean,
    allEntries: List<DiaryEntry>,
    onFilterSelect: (String) -> Unit, onNewEntry: () -> Unit, onCalendar: () -> Unit,
    onSync: () -> Unit, onExport: () -> Unit, onEntryClick: (DiaryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().background(WDPink).padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📔 My Diary", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (isLoggedIn) "☁ Cloud Sync Active" else "Login to enable sync", color = Color.White.copy(.7f), fontSize = 11.sp)
            }
        }
        Button(onClick = onNewEntry, modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WDPink), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New Entry", fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = Color(0xFF2A2A2A))
        listOf("All Entries" to Icons.Default.List, "Work" to Icons.Default.Work, "Personal" to Icons.Default.Person, "Secret" to Icons.Default.Lock).forEach { (label, icon) ->
            DrawerItem(label, icon, selectedFilter == label, WDPink) { onFilterSelect(label) }
        }
        HorizontalDivider(color = Color(0xFF2A2A2A))
        DrawerItem("Calendar", Icons.Default.CalendarMonth, false, Color(0xFF64B5F6)) { onCalendar() }
        DrawerItem(
            when(cloudStatus) { CloudStatus.SYNCING -> "Syncing..."; CloudStatus.SUCCESS -> "Synced ✓"; CloudStatus.ERROR -> "Sync Failed"; else -> "Sync to Cloud" },
            Icons.Default.CloudUpload, false,
            when(cloudStatus) { CloudStatus.SUCCESS -> WDGreen; CloudStatus.ERROR -> Color.Red; else -> Color(0xFF64B5F6) }
        ) { onSync() }
        DrawerItem("PDF Export", Icons.Default.PictureAsPdf, false, Color(0xFFEF5350)) { onExport() }
        HorizontalDivider(color = Color(0xFF2A2A2A))
        Text("  Recent", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(allEntries.take(8)) { entry ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onEntryClick(entry) }.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isLocked) { Icon(Icons.Default.Lock, null, tint = WDPink, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(entry.title.ifBlank { "Untitled" }, color = Color(0xFFE0E0E0), fontSize = 13.sp, maxLines = 1)
                        Text(entry.date.take(11).ifBlank { "No date" }, color = Color(0xFF888888), fontSize = 10.sp)
                    }
                    if (entry.mood.isNotBlank()) Text(entry.mood.take(2), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, iconTint: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .background(if (isSelected) Color(0xFF2A2A2A) else Color.Transparent)
        .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = if (isSelected) Color.White else Color(0xFFD0D0D0), fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Chip ─────────────────────────────────────────────────────────────────────
@Composable
fun Chip(label: String, onClose: () -> Unit) {
    Surface(modifier = Modifier.height(26.dp), shape = RoundedCornerShape(13.dp), color = WDPinkBg, contentColor = WDPink) {
        Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp).clickable { onClose() }, tint = WDPink)
        }
    }
}

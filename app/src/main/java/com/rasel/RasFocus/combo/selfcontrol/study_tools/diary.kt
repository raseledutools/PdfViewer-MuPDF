package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.rasel.RasFocus.drivebackup.DriveBackupManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// BIOMETRIC HELPER
// ============================================================
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
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometric not available")
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Diary Entry")
        .setSubtitle("Use fingerprint or device credential")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(info)
}

// ============================================================
// LOCK SCREEN
// ============================================================
@Composable
fun DiaryLockScreen(
    entry: DiaryEntry,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    val vm: DiaryViewModel = viewModel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D24), Color(0xFF2D323E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock, contentDescription = null,
                tint = Color(0xFF9B59B6), modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "🔒 Locked Entry",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                entry.title.ifBlank { "Untitled" },
                color = Color.Gray, fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            // PIN input
            OutlinedTextField(
                value = pinInput,
                onValueChange = { if (it.length <= 6) pinInput = it; pinError = false },
                label = { Text("Enter PIN", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = pinError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF9B59B6),
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    errorBorderColor = Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (pinError) {
                Text("Wrong PIN. Try again.", color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (vm.verifyPin(pinInput)) onUnlock()
                    else pinError = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B59B6))
            ) {
                Text("Unlock with PIN")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Biometric button
            OutlinedButton(
                onClick = {
                    (context as? FragmentActivity)?.let { activity ->
                        launchBiometric(
                            activity,
                            onSuccess = { vm.unlockWithBiometric(); onUnlock() },
                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9B59B6))
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF9B59B6))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use Biometric", color = Color(0xFF9B59B6))
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.Gray)
            }
        }
    }
}

// ============================================================
// SET PIN DIALOG
// ============================================================
@Composable
fun SetPinDialog(
    currentEntry: DiaryEntry,
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit,
    onRemovePin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentEntry.isLocked) "Change / Remove PIN" else "Set PIN Lock") },
        text = {
            Column {
                if (currentEntry.isLocked) {
                    Text("Entry is currently locked.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRemovePin,
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove PIN Lock", color = Color.Red)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Or set a new PIN:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it; error = "" },
                    label = { Text("New PIN (4-6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) confirmPin = it; error = "" },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Text(error, color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length < 4 -> error = "PIN must be at least 4 digits"
                    pin != confirmPin -> error = "PINs do not match"
                    else -> onPinSet(pin)
                }
            }) { Text("Set PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ============================================================
// CALENDAR SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryCalendarScreen(
    entries: List<DiaryEntry>,
    onEntryClick: (DiaryEntry) -> Unit,
    onBack: () -> Unit
) {
    val today = Calendar.getInstance()
    var displayedMonth by remember { mutableStateOf(today.get(Calendar.MONTH)) }
    var displayedYear by remember { mutableStateOf(today.get(Calendar.YEAR)) }

    // Build set of days that have entries this month
    val entryDays = remember(entries, displayedMonth, displayedYear) {
        entries.mapNotNull { entry ->
            runCatching {
                val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(entry.date) ?: return@mapNotNull null
                if (cal.get(Calendar.MONTH) == displayedMonth &&
                    cal.get(Calendar.YEAR) == displayedYear
                ) cal.get(Calendar.DAY_OF_MONTH)
                else null
            }.getOrNull()
        }.toSet()
    }

    var selectedDay by remember { mutableStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    val selectedEntries = remember(entries, selectedDay, displayedMonth, displayedYear) {
        entries.filter { entry ->
            runCatching {
                val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(entry.date) ?: return@filter false
                cal.get(Calendar.DAY_OF_MONTH) == selectedDay &&
                cal.get(Calendar.MONTH) == displayedMonth &&
                cal.get(Calendar.YEAR) == displayedYear
            }.getOrElse { false }
        }
    }

    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
        Calendar.getInstance().also { it.set(displayedYear, displayedMonth, 1) }.time
    )

    // Days in month
    val daysInMonth = Calendar.getInstance().also {
        it.set(displayedYear, displayedMonth, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    val firstDayOfWeek = Calendar.getInstance().also {
        it.set(displayedYear, displayedMonth, 1)
    }.get(Calendar.DAY_OF_WEEK) - 1  // 0=Sun

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFD32F2F))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2D323E)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (displayedMonth == 0) { displayedMonth = 11; displayedYear-- }
                    else displayedMonth--
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White)
                }
                Text(monthName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    if (displayedMonth == 11) { displayedMonth = 0; displayedYear++ }
                    else displayedMonth++
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }

            // Day headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat").forEach { day ->
                    Text(
                        day, fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                }
            }

            // Calendar grid
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height((rows * 48).dp).padding(horizontal = 8.dp)
            ) {
                items(rows * 7) { index ->
                    val day = index - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.size(40.dp))
                    } else {
                        val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                            displayedMonth == today.get(Calendar.MONTH) &&
                            displayedYear == today.get(Calendar.YEAR)
                        val hasEntry = day in entryDays
                        val isSelected = day == selectedDay

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> Color(0xFFD32F2F)
                                        isToday -> Color(0xFF9B59B6).copy(alpha = 0.4f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day", fontSize = 14.sp,
                                    color = if (isSelected) Color.White else Color(0xFF1A237E),
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasEntry) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(
                                        if (isSelected) Color.White else Color(0xFF2389D7)
                                    ))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Entries for selected day
            val selDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(
                Calendar.getInstance().also { it.set(displayedYear, displayedMonth, selectedDay) }.time
            )
            Text(
                selDate, fontSize = 14.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (selectedEntries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No entries on this day", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(selectedEntries) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { onEntryClick(entry) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (entry.isLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = null,
                                        tint = Color(0xFF9B59B6), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column {
                                    Text(entry.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold)
                                    Text(entry.mood.ifBlank { entry.folder }, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// REMINDER DIALOG
// ============================================================
@Composable
fun ReminderDialog(
    currentEntry: DiaryEntry,
    onDismiss: () -> Unit,
    onSetReminder: (Long, String) -> Unit,
    onClearReminder: () -> Unit
) {
    val context = LocalContext.current
    var reminderLabel by remember { mutableStateOf(currentEntry.reminderLabel.ifBlank { "Write in your diary" }) }
    var selectedDateTimeMs by remember { mutableStateOf(
        if (currentEntry.reminderTimeMillis > 0) currentEntry.reminderTimeMillis
        else System.currentTimeMillis() + 60 * 60 * 1000
    )}

    val cal = Calendar.getInstance().also { it.timeInMillis = selectedDateTimeMs }
    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
    val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ Set Reminder") },
        text = {
            Column {
                OutlinedTextField(
                    value = reminderLabel,
                    onValueChange = { reminderLabel = it },
                    label = { Text("Reminder message") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Date picker
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                selectedDateTimeMs = cal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: $dateStr")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time picker
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, min ->
                                cal.set(Calendar.HOUR_OF_DAY, h)
                                cal.set(Calendar.MINUTE, min)
                                selectedDateTimeMs = cal.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Time: $timeStr")
                }

                if (currentEntry.reminderTimeMillis > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onClearReminder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Reminder", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSetReminder(selectedDateTimeMs, reminderLabel) }) {
                Text("Set Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ============================================================
// MAIN SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDiaryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DiaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentEntry by viewModel.currentEntry.collectAsState()
    val allEntries by viewModel.allEntries.collectAsState()
    val selectedFilter by viewModel.selectedFolderFilter.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()

    var showMoodDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(false) }

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFE6CFA3)
    val paperColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF1A237E)

    DisposableEffect(Unit) {
        onDispose { viewModel.forceSaveOnExit() }
    }

    // Auto sync on login
    LaunchedEffect(Unit) {
        if (DiaryCloudSync.isLoggedIn()) viewModel.syncFromCloud()
    }

    // Show calendar
    if (showCalendar) {
        DiaryCalendarScreen(
            entries = allEntries,
            onEntryClick = { entry ->
                viewModel.loadEntry(entry)
                showCalendar = false
            },
            onBack = { showCalendar = false }
        )
        return
    }

    // Show lock screen if entry is locked and not yet unlocked
    if (currentEntry.isLocked && !isUnlocked) {
        DiaryLockScreen(
            entry = currentEntry,
            onUnlock = { viewModel.unlockWithBiometric() },
            onCancel = { viewModel.startNewEntry() }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF2D323E)
            ) {
                DiarySidebar(
                    selectedFilter = selectedFilter,
                    isDarkMode = isDarkMode,
                    cloudStatus = cloudStatus,
                    isLoggedIn = DiaryCloudSync.isLoggedIn(),
                    onFilterSelect = {
                        viewModel.setFolderFilter(it)
                        scope.launch { drawerState.close() }
                    },
                    onNewEntry = {
                        viewModel.startNewEntry()
                        scope.launch { drawerState.close() }
                    },
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onExportClick = {
                        scope.launch { drawerState.close() }
                        showExportMenu = true
                    },
                    onCalendarClick = {
                        scope.launch { drawerState.close() }
                        showCalendar = true
                    },
                    onSyncClick = { viewModel.syncToCloud() },
                    allEntries = allEntries,
                    onEntryClick = { entry ->
                        viewModel.loadEntry(entry)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Journal", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        // Cloud status icon
                        when (cloudStatus) {
                            CloudStatus.SYNCING -> CircularProgressIndicator(
                                color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                            )
                            CloudStatus.SUCCESS -> Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = Color.Green)
                            CloudStatus.ERROR -> Icon(Icons.Default.CloudOff, contentDescription = "Error", tint = Color.Red)
                            CloudStatus.NOT_LOGGED_IN -> Icon(Icons.Default.CloudOff, contentDescription = "Not logged in", tint = Color.Gray)
                            else -> {
                                Text(
                                    if (saveStatus == SaveStatus.SAVING) "Saving..." else "Saved",
                                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        // Reminder button
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(
                                if (currentEntry.reminderTimeMillis > 0) Icons.Default.Notifications
                                else Icons.Default.NotificationsNone,
                                contentDescription = "Reminder",
                                tint = if (currentEntry.reminderTimeMillis > 0) Color.Yellow else Color.White
                            )
                        }

                        // Lock button
                        IconButton(onClick = { showSetPinDialog = true }) {
                            Icon(
                                if (currentEntry.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = if (currentEntry.isLocked) Color(0xFFFFD700) else Color.White
                            )
                        }

                        IconButton(onClick = {
                            if (currentEntry.title.isNotBlank() || currentEntry.body.isNotBlank()) {
                                viewModel.deleteEntry(currentEntry)
                                Toast.makeText(context, "Entry Deleted", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }

                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFD32F2F))
                )
            },
            containerColor = bgColor
        ) { paddingValues ->
            DiaryEditorArea(
                modifier = Modifier.padding(paddingValues),
                entry = currentEntry,
                paperColor = paperColor,
                textColor = textColor,
                onEntryChange = { viewModel.updateEntry(it) },
                onMoodClick = { showMoodDialog = true },
                onTagClick = { showTagDialog = true },
                onAddTag = { tag -> if (tag.isNotBlank()) viewModel.addTag(tag) },
                onRemoveTag = { tag -> viewModel.removeTag(tag) },
                onFolderClick = { showFolderMenu = true },
                showFolderMenu = showFolderMenu,
                onDismissFolderMenu = { showFolderMenu = false }
            )
        }
    }

    // ---- Dialogs ----

    if (showMoodDialog) {
        val moods = listOf("😊 Happy", "😢 Sad", "😠 Angry", "🎉 Excited", "😐 Neutral", "😰 Anxious")
        AlertDialog(
            onDismissRequest = { showMoodDialog = false },
            title = { Text("Select Mood") },
            text = {
                Column {
                    moods.forEach { mood ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.updateMood(mood)
                                showMoodDialog = false
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentEntry.mood == mood, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mood)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMoodDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tag Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTag(tagInput); tagInput = ""; showTagDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportMenu) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("📄 Export as PDF") },
            text = { Text("Choose what you want to export.") },
            confirmButton = {
                Column {
                    Button(onClick = {
                        val file = DiaryPdfExporter.exportSingleEntry(context, currentEntry)
                        if (file != null) {
                            val intent = DiaryPdfExporter.getShareIntent(context, file)
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            // RasFocus+ Drive backup: quietly push this export into the
                            // "RasFocus+" Drive folder, updating the same file each time.
                            if (DriveBackupManager.isAvailable(context)) {
                                scope.launch {
                                    val ok = DriveBackupManager.uploadDiarySingleEntryPdf(context, file)
                                    if (ok) Toast.makeText(context, "Drive-এ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                        showExportMenu = false
                    }) { Text("Export Current Entry") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val file = DiaryPdfExporter.exportAllEntries(context, allEntries)
                        if (file != null) {
                            val intent = DiaryPdfExporter.getShareIntent(context, file)
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            // RasFocus+ Drive backup: quietly push this export into the
                            // "RasFocus+" Drive folder, updating the same file each time.
                            if (DriveBackupManager.isAvailable(context)) {
                                scope.launch {
                                    val ok = DriveBackupManager.uploadDiaryAllEntriesPdf(context, file)
                                    if (ok) Toast.makeText(context, "Drive-এ আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                        showExportMenu = false
                    }) { Text("Export All Entries") }
                }
            },
            dismissButton = { TextButton(onClick = { showExportMenu = false }) { Text("Cancel") } }
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            currentEntry = currentEntry,
            onDismiss = { showSetPinDialog = false },
            onPinSet = { pin ->
                viewModel.setPin(pin)
                showSetPinDialog = false
                Toast.makeText(context, "PIN set successfully", Toast.LENGTH_SHORT).show()
            },
            onRemovePin = {
                viewModel.removePin()
                showSetPinDialog = false
                Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showReminderDialog) {
        ReminderDialog(
            currentEntry = currentEntry,
            onDismiss = { showReminderDialog = false },
            onSetReminder = { timeMs, label ->
                viewModel.setReminder(context, timeMs, label)
                showReminderDialog = false
                Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show()
            },
            onClearReminder = {
                viewModel.clearReminder(context)
                showReminderDialog = false
                Toast.makeText(context, "Reminder cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ============================================================
// SIDEBAR  (updated with Calendar + Sync + entry list)
// ============================================================
@Composable
fun DiarySidebar(
    selectedFilter: String,
    isDarkMode: Boolean,
    cloudStatus: CloudStatus,
    isLoggedIn: Boolean,
    allEntries: List<DiaryEntry>,
    onFilterSelect: (String) -> Unit,
    onNewEntry: () -> Unit,
    onToggleTheme: () -> Unit,
    onExportClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onSyncClick: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1D24)).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📔 My Diary", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (isLoggedIn) {
                    Text("☁ Cloud Sync Active", color = Color(0xFF4CAF50), fontSize = 11.sp)
                } else {
                    Text("Log in to enable Cloud Sync", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = onNewEntry,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2389D7)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Entry", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = Color(0xFF3A4150))

        SidebarItem("All Entries", Icons.Default.List, selectedFilter == "All Entries", Color.LightGray) { onFilterSelect("All Entries") }
        SidebarItem("Work", Icons.Default.Build, selectedFilter == "Work", Color(0xFFF39C12)) { onFilterSelect("Work") }
        SidebarItem("Personal", Icons.Default.Person, selectedFilter == "Personal", Color(0xFF2ECC71)) { onFilterSelect("Personal") }
        SidebarItem("Secret", Icons.Default.Lock, selectedFilter == "Secret", Color(0xFF9B59B6)) { onFilterSelect("Secret") }

        HorizontalDivider(color = Color(0xFF3A4150), modifier = Modifier.padding(vertical = 4.dp))

        SidebarItem("Calendar", Icons.Default.CalendarMonth, false, Color(0xFF2389D7)) { onCalendarClick() }

        SidebarItem(
            title = when (cloudStatus) {
                CloudStatus.SYNCING -> "Syncing..."
                CloudStatus.SUCCESS -> "Sync: Done ✓"
                CloudStatus.ERROR -> "Sync Failed"
                CloudStatus.NOT_LOGGED_IN -> "Login to Sync"
                else -> "Sync to Cloud"
            },
            icon = Icons.Default.CloudUpload,
            isSelected = false,
            iconTint = when (cloudStatus) {
                CloudStatus.SUCCESS -> Color(0xFF4CAF50)
                CloudStatus.ERROR -> Color.Red
                else -> Color(0xFF5DADE2)
            }
        ) { onSyncClick() }

        SidebarItem("PDF Export", Icons.Default.PictureAsPdf, false, Color(0xFFE74C3C)) { onExportClick() }

        HorizontalDivider(color = Color(0xFF3A4150), modifier = Modifier.padding(vertical = 4.dp))

        // Recent entries
        Text(
            "  Recent Entries",
            color = Color(0xFF8899AA), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(allEntries.take(10)) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEntryClick(entry) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.isLocked) {
                        Icon(Icons.Default.Lock, contentDescription = null,
                            tint = Color(0xFF9B59B6), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title.ifBlank { "Untitled" },
                            color = Color(0xFFD1D2D4), fontSize = 13.sp,
                            maxLines = 1
                        )
                        Text(
                            entry.date.take(12).ifBlank { "No date" },
                            color = Color(0xFF8899AA), fontSize = 10.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF3A4150))
        SidebarItem(
            title = if (isDarkMode) "Light Mode" else "Dark Mode",
            icon = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.Nightlight,
            isSelected = false, iconTint = Color.LightGray
        ) { onToggleTheme() }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SidebarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (isSelected) Color(0xFF3A4150) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            title, color = if (isSelected) Color.White else Color(0xFFD1D2D4),
            fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorArea(
    modifier: Modifier = Modifier,
    entry: DiaryEntry,
    paperColor: Color,
    textColor: Color,
    onEntryChange: (DiaryEntry) -> Unit,
    onMoodClick: () -> Unit,
    onTagClick: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onFolderClick: () -> Unit,
    showFolderMenu: Boolean,
    onDismissFolderMenu: () -> Unit
) {
    val wordCount = entry.body.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize().background(paperColor)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {

            // Reminder badge
            if (entry.reminderTimeMillis > 0) {
                val remStr = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                    .format(Date(entry.reminderTimeMillis))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFF9C4))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null,
                        tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reminder: $remStr", fontSize = 11.sp, color = Color(0xFFE65100))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Date Banner
            val currentDate = if (entry.date.isNotBlank()) entry.date
                else SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(Date())
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2))))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(currentDate.split(", ")[0].uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, letterSpacing = 1.sp)
                        Text(currentDate.substringAfter(", ").trim(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (entry.isLocked) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.DateRange, contentDescription = "Date", tint = Color.White)
                    }
                }
            }

            // Title Input
            OutlinedTextField(
                value = entry.title,
                onValueChange = { onEntryChange(entry.copy(title = it)) },
                placeholder = { Text("Entry Title...", color = Color.Gray.copy(alpha = 0.7f)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Color(0xFFD32F2F)
                ),
                singleLine = true
            )

            // Meta row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(modifier = Modifier.clickable { onFolderClick() }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(entry.folder, color = Color.Gray, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showFolderMenu, onDismissRequest = onDismissFolderMenu) {
                        listOf("General", "Work", "Personal", "Secret").forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder) },
                                onClick = { onEntryChange(entry.copy(folder = folder)); onDismissFolderMenu() }
                            )
                        }
                    }
                }
                Text("$wordCount words", color = Color.Gray, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMoodClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Face, contentDescription = "Mood",
                        tint = if (entry.mood.isNotBlank()) Color(0xFF2389D7) else Color.Gray)
                }
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = "Audio", tint = Color.Gray)
                }
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Brush, contentDescription = "Draw", tint = Color.Gray)
                }
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Image, contentDescription = "Image", tint = Color.Gray)
                }
                IconButton(onClick = onTagClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Label, contentDescription = "Tag",
                        tint = if (entry.tags.isNotEmpty()) Color(0xFF2389D7) else Color.Gray)
                }
                VerticalDivider(modifier = Modifier.height(24.dp), color = Color.LightGray)
                Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.Gray, modifier = Modifier.clickable { }.padding(4.dp))
                Text("I", fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Color.Gray, modifier = Modifier.clickable { }.padding(4.dp))
                Text("U", textDecoration = TextDecoration.Underline, fontSize = 18.sp, color = Color.Gray, modifier = Modifier.clickable { }.padding(4.dp))
            }

            if (entry.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entry.tags.forEach { tag -> Chip(label = tag, onClose = { onRemoveTag(tag) }) }
                }
            }

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

            // Body
            OutlinedTextField(
                value = entry.body,
                onValueChange = { onEntryChange(entry.copy(body = it)) },
                placeholder = { Text("Start writing your thoughts here...", color = Color.Gray.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, color = textColor, lineHeight = 27.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Color(0xFFD32F2F)
                )
            )
        }
    }
}

@Composable
fun Chip(label: String, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.height(26.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFE3F2FD),
        contentColor = Color(0xFF1565C0)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove Tag",
                modifier = Modifier.size(14.dp).clickable { onClose() },
                tint = Color(0xFF1565C0)
            )
        }
    }
}
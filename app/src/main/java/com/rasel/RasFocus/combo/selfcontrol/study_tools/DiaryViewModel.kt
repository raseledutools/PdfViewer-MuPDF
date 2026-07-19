package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// PIN UTILITY
// ============================================================
object PinUtil {
    fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, hash: String) = hash(pin) == hash
}

// ============================================================
// REMINDER BROADCAST RECEIVER
// ============================================================
class DiaryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "Diary Reminder"
        val entryId = intent.getLongExtra("entry_id", 0L)
        showReminderNotification(context, label, entryId)
    }

    private fun showReminderNotification(context: Context, label: String, entryId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "diary_reminder"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                channelId, "Diary Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }
        val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📔 Diary Reminder")
            .setContentText(label)
            .setAutoCancel(true)
            .build()
        nm.notify(entryId.toInt(), notif)
    }
}

// ============================================================
// CLOUD SYNC HELPER (Firestore)
// ============================================================
object DiaryCloudSync {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userCollection() = auth.currentUser?.uid?.let {
        db.collection("diary_users").document(it).collection("entries")
    }

    suspend fun uploadEntry(entry: DiaryEntry) {
        val col = userCollection() ?: return
        val map = mapOf(
            "id" to entry.id,
            "title" to entry.title,
            "body" to entry.body,
            "folder" to entry.folder,
            "mood" to entry.mood,
            "tags" to entry.tags,
            "date" to entry.date,
            "timestamp" to entry.timestamp,
            "isLocked" to entry.isLocked,
            "pinHash" to entry.pinHash,
            "reminderTimeMillis" to entry.reminderTimeMillis,
            "reminderLabel" to entry.reminderLabel
        )
        col.document(entry.id.toString()).set(map, SetOptions.merge()).await()
    }

    suspend fun deleteEntry(entryId: Long) {
        val col = userCollection() ?: return
        col.document(entryId.toString()).delete().await()
    }

    suspend fun fetchAllEntries(): List<DiaryEntry> {
        val col = userCollection() ?: return emptyList()
        val snapshot = col.get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching {
                DiaryEntry(
                    id = doc.getLong("id") ?: return@mapNotNull null,
                    title = doc.getString("title") ?: "",
                    body = doc.getString("body") ?: "",
                    folder = doc.getString("folder") ?: "General",
                    mood = doc.getString("mood") ?: "",
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    date = doc.getString("date") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    isLocked = doc.getBoolean("isLocked") ?: false,
                    pinHash = doc.getString("pinHash") ?: "",
                    reminderTimeMillis = doc.getLong("reminderTimeMillis") ?: 0L,
                    reminderLabel = doc.getString("reminderLabel") ?: ""
                )
            }.getOrNull()
        }
    }

    fun isLoggedIn() = auth.currentUser != null
}

// ============================================================
// REPOSITORY
// ============================================================
class DiaryRepository(private val dao: DiaryDao) {
    fun getAllEntries() = dao.getAllEntries()
    fun getEntriesByFolder(folder: String) = dao.getEntriesByFolder(folder)
    fun getEntriesWithReminder() = dao.getEntriesWithReminder()
    suspend fun saveEntry(entry: DiaryEntry) = dao.upsertEntry(entry)
    suspend fun deleteEntry(entry: DiaryEntry) = dao.deleteEntry(entry)
    suspend fun getEntryById(id: Long) = dao.getEntryById(id)
}

// ============================================================
// VIEWMODEL
// ============================================================
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DiaryRepository
    private var autoSaveJob: Job? = null

    private val _currentEntry = MutableStateFlow(DiaryEntry())
    val currentEntry: StateFlow<DiaryEntry> = _currentEntry.asStateFlow()

    private val _allEntries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val allEntries: StateFlow<List<DiaryEntry>> = _allEntries.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow("All Entries")
    val selectedFolderFilter: StateFlow<String> = _selectedFolderFilter.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.SYNCED)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    // Lock state
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Cloud sync status
    private val _cloudStatus = MutableStateFlow(CloudStatus.IDLE)
    val cloudStatus: StateFlow<CloudStatus> = _cloudStatus.asStateFlow()

    init {
        val dao = DiaryDatabase.getDatabase(application).diaryDao()
        repository = DiaryRepository(dao)
        observeEntries()
    }

    private fun observeEntries() {
        viewModelScope.launch {
            _selectedFolderFilter.collectLatest { filter ->
                val flow = if (filter == "All Entries") repository.getAllEntries()
                           else repository.getEntriesByFolder(filter)
                flow.collect { entries -> _allEntries.value = entries }
            }
        }
    }

    fun setFolderFilter(folder: String) { _selectedFolderFilter.value = folder }

    fun startNewEntry() {
        val todayDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(Date())
        _currentEntry.value = DiaryEntry(date = todayDate)
        _saveStatus.value = SaveStatus.SYNCED
        _isUnlocked.value = true  // new entry is always unlocked
    }

    fun loadEntry(entry: DiaryEntry) {
        _currentEntry.value = entry
        _saveStatus.value = SaveStatus.SYNCED
        // If entry is locked, require unlock
        _isUnlocked.value = !entry.isLocked
    }

    fun updateEntry(updated: DiaryEntry) {
        _currentEntry.value = updated
        triggerAutoSave()
    }

    fun updateMood(mood: String) = updateEntry(_currentEntry.value.copy(mood = mood))

    fun addTag(tag: String) {
        if (tag.isBlank()) return
        val current = _currentEntry.value
        if (tag !in current.tags) updateEntry(current.copy(tags = current.tags + tag))
    }

    fun removeTag(tag: String) {
        updateEntry(_currentEntry.value.copy(tags = _currentEntry.value.tags - tag))
    }

    // ---- LOCK ----
    fun setPin(pin: String) {
        val hash = PinUtil.hash(pin)
        updateEntry(_currentEntry.value.copy(isLocked = true, pinHash = hash))
    }

    fun removePin() {
        updateEntry(_currentEntry.value.copy(isLocked = false, pinHash = ""))
        _isUnlocked.value = true
    }

    fun verifyPin(pin: String): Boolean {
        val entry = _currentEntry.value
        return if (entry.pinHash.isBlank()) {
            _isUnlocked.value = true; true
        } else {
            val ok = PinUtil.verify(pin, entry.pinHash)
            if (ok) _isUnlocked.value = true
            ok
        }
    }

    fun unlockWithBiometric() { _isUnlocked.value = true }

    // ---- REMINDER ----
    fun setReminder(context: Context, timeMillis: Long, label: String) {
        updateEntry(_currentEntry.value.copy(
            reminderTimeMillis = timeMillis,
            reminderLabel = label
        ))
        scheduleAlarm(context, _currentEntry.value.id, timeMillis, label)
    }

    fun clearReminder(context: Context) {
        cancelAlarm(context, _currentEntry.value.id)
        updateEntry(_currentEntry.value.copy(reminderTimeMillis = 0L, reminderLabel = ""))
    }

    private fun scheduleAlarm(context: Context, entryId: Long, timeMillis: Long, label: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DiaryReminderReceiver::class.java).apply {
            putExtra("entry_id", entryId)
            putExtra("label", label)
        }
        val pi = PendingIntent.getBroadcast(
            context, entryId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        }
    }

    private fun cancelAlarm(context: Context, entryId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DiaryReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, entryId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    // ---- CLOUD SYNC ----
    fun syncToCloud() {
        if (!DiaryCloudSync.isLoggedIn()) {
            _cloudStatus.value = CloudStatus.NOT_LOGGED_IN
            return
        }
        viewModelScope.launch {
            _cloudStatus.value = CloudStatus.SYNCING
            runCatching {
                _allEntries.value.forEach { DiaryCloudSync.uploadEntry(it) }
            }.onSuccess {
                _cloudStatus.value = CloudStatus.SUCCESS
            }.onFailure {
                _cloudStatus.value = CloudStatus.ERROR
            }
        }
    }

    fun syncFromCloud() {
        if (!DiaryCloudSync.isLoggedIn()) {
            _cloudStatus.value = CloudStatus.NOT_LOGGED_IN
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _cloudStatus.value = CloudStatus.SYNCING
            runCatching {
                val cloudEntries = DiaryCloudSync.fetchAllEntries()
                cloudEntries.forEach { repository.saveEntry(it) }
            }.onSuccess {
                kotlinx.coroutines.delay(300) // Let Room Flow settle before UI update
                _cloudStatus.value = CloudStatus.SUCCESS
            }.onFailure {
                _cloudStatus.value = CloudStatus.ERROR
            }
        }
    }

    private fun triggerAutoSave() {
        autoSaveJob?.cancel()
        _saveStatus.value = SaveStatus.SAVING
        autoSaveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(800)
            saveCurrentEntryNow()
        }
    }

    suspend fun saveCurrentEntryNow() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val entry = _currentEntry.value
        if (entry.title.isBlank() && entry.body.isBlank()) {
            _saveStatus.value = SaveStatus.SYNCED
            return@withContext
        }
        repository.saveEntry(entry)
        _saveStatus.value = SaveStatus.SYNCED
        // Auto upload to cloud on save (silent)
        if (DiaryCloudSync.isLoggedIn()) {
            runCatching { DiaryCloudSync.uploadEntry(entry) }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            if (DiaryCloudSync.isLoggedIn()) {
                runCatching { DiaryCloudSync.deleteEntry(entry.id) }
            }
            if (_currentEntry.value.id == entry.id) startNewEntry()
        }
    }

    fun forceSaveOnExit() {
        autoSaveJob?.cancel()
        viewModelScope.launch { saveCurrentEntryNow() }
    }
}

enum class SaveStatus { SAVING, SYNCED }
enum class CloudStatus { IDLE, SYNCING, SUCCESS, ERROR, NOT_LOGGED_IN }
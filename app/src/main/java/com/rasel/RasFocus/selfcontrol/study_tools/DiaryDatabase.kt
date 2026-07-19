package com.rasel.RasFocus.selfcontrol.study_tools

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================================
// ENTITY
// ============================================================
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = false)
    val id: Long = System.currentTimeMillis(),
    val title: String = "",
    val body: String = "",
    val folder: String = "General",
    val mood: String = "",
    val tags: List<String> = emptyList(),
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),

    // Lock
    val isLocked: Boolean = false,
    val pinHash: String = "",          // SHA-256 of PIN (empty = no PIN lock)

    // Reminder
    val reminderTimeMillis: Long = 0L, // 0 = no reminder
    val reminderLabel: String = "",
)

// ============================================================
// TYPE CONVERTER
// ============================================================
class Converters {
    @TypeConverter
    fun fromTagList(tags: List<String>): String = tags.joinToString("|||")

    @TypeConverter
    fun toTagList(data: String): List<String> =
        if (data.isEmpty()) emptyList() else data.split("|||")
}

// ============================================================
// DAO
// ============================================================
@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE folder = :folderName ORDER BY timestamp DESC")
    fun getEntriesByFolder(folderName: String): Flow<List<DiaryEntry>>

    // Calendar: entries for a specific date string (e.g. "Monday, January 1, 2025")
    @Query("SELECT * FROM diary_entries WHERE date LIKE '%' || :dateFragment || '%' ORDER BY timestamp DESC")
    fun getEntriesByDate(dateFragment: String): Flow<List<DiaryEntry>>

    // Entries with reminder set
    @Query("SELECT * FROM diary_entries WHERE reminderTimeMillis > 0 ORDER BY reminderTimeMillis ASC")
    fun getEntriesWithReminder(): Flow<List<DiaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DiaryEntry)

    @Delete
    suspend fun deleteEntry(entry: DiaryEntry)

    @Query("SELECT * FROM diary_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: Long): DiaryEntry?

    // One-shot query for WorkManager / backup (no Flow, no coroutine collect needed)
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesOnce(): List<DiaryEntry>

    // Upsert from Drive JSON import
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DiaryEntry>)
}

// ============================================================
// DATABASE  — version bumped to 2 for new columns
// ============================================================
@Database(
    entities = [DiaryEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun getDatabase(context: android.content.Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "diary_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

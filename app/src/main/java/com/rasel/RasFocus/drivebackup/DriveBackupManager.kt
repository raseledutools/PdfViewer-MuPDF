package com.rasel.RasFocus.drivebackup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File as JFile

object DriveBackupManager {

    private const val TAG              = "DriveBackupManager"
    private const val APP_FOLDER_NAME  = "RasFocus+"
    private const val FOLDER_PREF_KEY  = "drive_app_folder_id"
    private const val FOLDER_PREF_FILE = "drive_prefs"
    private const val DIARY_JSON_NAME  = "RasFocus_Diary_Backup.json"
    private const val DIARY_PDF_NAME   = "RasFocus_Diary_AllEntries.pdf"
    private const val SETTINGS_NAME    = "rasfocus_settings_backup.json"

    // SharedPreference files to include in settings backup
    private val SETTINGS_PREF_NAMES = listOf(
        "browser_settings", "focus_settings", "parental_settings",
        "app_settings", "blocking_settings", "user_prefs"
    )

    // ✅ DIAGNOSTIC: UI-এ আগে শুধু "Upload failed" দেখানো হতো, আসল কারণ কখনো
    // দেখানো হতো না (শুধু Log.e/Log.w তে যেত, যেটা user দেখতে পায় না)।
    // এখন প্রতিটা ব্যর্থতার real reason এখানে জমা থাকে, UI সেটা toast এ দেখাতে পারে।
    var lastError: String? = null
        private set

    // ✅ যদি failure এর কারণ হয় Drive scope/permission missing (সবচেয়ে common
    // কারণ — বিশেষত যারা এই Drive feature আসার আগে sign-in করেছিল), তাহলে
    // Android নিজেই একটা resolution Intent দেয় যেটা launch করলে user সরাসরি
    // permission grant করতে পারে, sign-out/sign-in করা লাগে না।
    var lastRecoveryIntent: Intent? = null
        private set

    private fun recordFailure(tag: String, e: Exception) {
        if (e is UserRecoverableAuthIOException) {
            lastError = "Drive permission দেওয়া হয়নি — নিচের 'Fix Drive Access' বাটনে ট্যাপ করুন।"
            lastRecoveryIntent = e.intent
            Log.w(TAG, "$tag: Drive permission not granted, recovery intent available", e)
        } else {
            lastError = e.message ?: e.javaClass.simpleName
            lastRecoveryIntent = null
            Log.e(TAG, "$tag failed: ${e.message}", e)
        }
    }

    // ── Public check ─────────────────────────────────────────────────────────
    // ✅ FIX: DRIVE_FILE scope না থাকলেও account আছে কিনা দেখো — Drive service
    //         build করার সময় scope automatically request হবে। শুধু account null
    //         হলে unavailable বলো।
    fun isAvailable(context: Context): Boolean {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
            // account আছে মানে sign-in হয়েছে। DRIVE_FILE scope check
            // করি কিন্তু না থাকলেও true return করি — buildDriveService এ
            // credential এ scope add করা হবে, তাই upload try করতে পারবে।
            val hasDriveScope = GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
            if (!hasDriveScope) {
                Log.w(TAG, "isAvailable: DRIVE_FILE scope missing — Drive buttons এ click করলে error toast দেখাবে")
            }
            true  // ✅ account থাকলেই Drive section দেখাও; actual failure upload সময় handle হবে
        } catch (e: Exception) {
            Log.w(TAG, "isAvailable check failed: ${e.message}")
            false
        }
    }

    // ── Build Drive service safely on IO thread ───────────────────────────────
    private fun buildDriveService(context: Context): Drive? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                lastError = "কোনো Google account সাইন-ইন করা নেই।"
                Log.w(TAG, "buildDriveService: no signed-in account")
                return null
            }
            val androidAccount = account.account
            if (androidAccount == null) {
                lastError = "Google account পাওয়া যায়নি — আবার sign-in করুন।"
                Log.w(TAG, "buildDriveService: account.account is null")
                return null
            }
            // ✅ FIX: DRIVE_FILE scope না থাকলেও credential এ scope দাও।
            // GoogleAccountCredential OAuth2 scope automatically token request করে।
            // পুরনো code DRIVE_FILE scope না থাকলে null return করত — সেটাই upload fail করাত।
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).also { it.selectedAccount = androidAccount }
            Drive.Builder(
                NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
            ).setApplicationName("RasFocus+").build()
        } catch (e: Exception) {
            recordFailure("buildDriveService", e)
            null
        }
    }

    // ── Get or create the "RasFocus+" folder in Drive ────────────────────────
    private fun getOrCreateFolderId(context: Context, drive: Drive): String? {
        val prefs = context.getSharedPreferences(FOLDER_PREF_FILE, Context.MODE_PRIVATE)
        val cached = prefs.getString(FOLDER_PREF_KEY, null)

        cached?.let { id ->
            try {
                val f = drive.files().get(id).setFields("id,trashed,mimeType").execute()
                if (f.trashed == true || f.mimeType != "application/vnd.google-apps.folder") {
                    Log.w(TAG, "getOrCreateFolderId: cached folder $id invalid (trashed=${f.trashed}, mimeType=${f.mimeType}), clearing cache")
                    prefs.edit().remove(FOLDER_PREF_KEY).apply()
                } else {
                    return id
                }
            } catch (e: Exception) {
                Log.w(TAG, "getOrCreateFolderId: cached folder $id is not accessible (${e.message}), clearing cache")
                prefs.edit().remove(FOLDER_PREF_KEY).apply()
            }
        }

        val existingId = try {
            val query = "name='$APP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "getOrCreateFolderId: folder query failed (${e.message}), trying folder create")
            null
        }

        val folderId = existingId ?: try {
            val meta = DriveFile().apply {
                name = APP_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(meta).setFields("id").execute().id
        } catch (e: Exception) {
            recordFailure("getOrCreateFolderId:createFolder", e)
            null
        }

        folderId?.also {
            Log.d(TAG, "getOrCreateFolderId: using folderId=$it")
            prefs.edit().putString(FOLDER_PREF_KEY, it).apply()
        }
        return folderId
    }

    // ── Generic upload (create or update named file in Drive folder) ──────────
    private suspend fun uploadNamedFile(
        context: Context, localFile: JFile, name: String, mime: String
    ): Boolean = withContext(Dispatchers.IO) {
        lastError = null
        lastRecoveryIntent = null
        try {
            if (!localFile.exists()) {
                lastError = "Upload failed: local file not found (${localFile.absolutePath})"
                Log.e(TAG, "uploadNamedFile($name): local file does not exist: ${localFile.absolutePath}")
                return@withContext false
            }
            val drive    = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateFolderId(context, drive) ?: return@withContext false
            val content  = FileContent(mime, localFile)
            val query    = "name='$name' and '$folderId' in parents and trashed=false"
            val existing = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
            if (existing != null) {
                drive.files().update(existing.id, DriveFile(), content).execute()
                Log.d(TAG, "uploadNamedFile($name): updated existing Drive file id=${existing.id}")
            } else {
                val meta = DriveFile().apply {
                    this.name    = name
                    parents      = listOf(folderId)
                }
                val createdId = drive.files().create(meta, content).setFields("id").execute().id
                Log.d(TAG, "uploadNamedFile($name): created new Drive file id=$createdId")
            }
            true
        } catch (e: Exception) {
            recordFailure("uploadNamedFile($name)", e)
            false
        }
    }

    // ── Settings backup ───────────────────────────────────────────────────────
    private suspend fun backupSettings(context: Context, drive: Drive, folderId: String) {
        try {
            val root = JSONObject()
            for (prefName in SETTINGS_PREF_NAMES) {
                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val obj   = JSONObject()
                prefs.all.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> obj.put(k, v)
                        is Int     -> obj.put(k, v)
                        is Long    -> obj.put(k, v)
                        is Float   -> obj.put(k, v)
                        is String  -> obj.put(k, v)
                        is Set<*>  -> obj.put(k, v.joinToString(","))
                        else       -> obj.put(k, v.toString())
                    }
                }
                root.put(prefName, obj)
            }
            val file = JFile(context.cacheDir, SETTINGS_NAME)
            file.writeText(root.toString(2))
            uploadNamedFile(context, file, SETTINGS_NAME, "application/json")
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "backupSettings failed", e)
        }
    }

    // ── Public: full sync (settings) ─────────────────────────────────────────
    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive    = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateFolderId(context, drive) ?: return@withContext false
            backupSettings(context, drive, folderId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncNow failed", e)
            false
        }
    }

    // ── Public: upload diary JSON ─────────────────────────────────────────────
    suspend fun uploadDiaryJson(context: Context, file: JFile): Boolean =
        uploadNamedFile(context, file, DIARY_JSON_NAME, "application/json")

    // ── Public: upload diary PDF ──────────────────────────────────────────────
    suspend fun uploadDiaryPdf(context: Context, file: JFile): Boolean =
        uploadNamedFile(context, file, DIARY_PDF_NAME, "application/pdf")

    // ── Public: import settings from Drive ───────────────────────────────────
    suspend fun importSettings(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive    = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateFolderId(context, drive) ?: return@withContext false
            val query    = "name='$SETTINGS_NAME' and '$folderId' in parents and trashed=false"
            val found    = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
                ?: return@withContext false
            val json = JSONObject(
                drive.files().get(found.id).executeMediaAsInputStream()
                    .bufferedReader().readText()
            )
            for (prefName in SETTINGS_PREF_NAMES) {
                if (!json.has(prefName)) continue
                val obj  = json.getJSONObject(prefName)
                val edit = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
                obj.keys().forEach { key ->
                    when (val v = obj.get(key)) {
                        is Boolean -> edit.putBoolean(key, v)
                        is Int     -> edit.putInt(key, v)
                        is Long    -> edit.putLong(key, v)
                        is Float   -> edit.putFloat(key, v)
                        else       -> edit.putString(key, v.toString())
                    }
                }
                edit.apply()
            }
            true
        } catch (e: Exception) {
            recordFailure("importSettings", e)
            false
        }
    }

    // ── Public: download diary JSON string from Drive ─────────────────────────
    suspend fun downloadDiaryJson(context: Context): String? = withContext(Dispatchers.IO) {
        lastError = null
        lastRecoveryIntent = null
        try {
            val drive    = buildDriveService(context) ?: return@withContext null
            val folderId = getOrCreateFolderId(context, drive) ?: return@withContext null
            val query    = "name='$DIARY_JSON_NAME' and '$folderId' in parents and trashed=false"
            val found    = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
                ?: return@withContext null
            drive.files().get(found.id).executeMediaAsInputStream()
                .bufferedReader().readText()
        } catch (e: Exception) {
            recordFailure("downloadDiaryJson", e)
            null
        }
    }
}
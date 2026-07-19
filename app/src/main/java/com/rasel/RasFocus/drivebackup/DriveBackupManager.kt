package com.rasel.RasFocus.drivebackup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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

    // ── Public check ─────────────────────────────────────────────────────────
    fun isAvailable(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    // ── Build Drive service safely on IO thread ───────────────────────────────
    private fun buildDriveService(context: Context): Drive? = try {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) return null
        val androidAccount = account.account ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).also { it.selectedAccount = androidAccount }
        Drive.Builder(
            NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
        ).setApplicationName("RasFocus+").build()
    } catch (e: Exception) {
        Log.e(TAG, "buildDriveService failed", e)
        null
    }

    // ── Get or create the "RasFocus+" folder in Drive ────────────────────────
    private fun getOrCreateFolderId(context: Context, drive: Drive): String? = try {
        val prefs = context.getSharedPreferences(FOLDER_PREF_FILE, Context.MODE_PRIVATE)
        val cached = prefs.getString(FOLDER_PREF_KEY, null)
        if (cached != null) {
            cached
        } else {
            val query = "name='$APP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            val existing = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
            val folderId = existing?.id ?: run {
                val meta = DriveFile().apply {
                    name     = APP_FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
                drive.files().create(meta).setFields("id").execute().id
            }
            folderId?.also { prefs.edit().putString(FOLDER_PREF_KEY, it).apply() }
        }
    } catch (e: Exception) {
        Log.e(TAG, "getOrCreateFolderId failed", e)
        null
    }

    // ── Generic upload (create or update named file in Drive folder) ──────────
    private suspend fun uploadNamedFile(
        context: Context, localFile: JFile, name: String, mime: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive    = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateFolderId(context, drive) ?: return@withContext false
            val content  = FileContent(mime, localFile)
            val query    = "name='$name' and '$folderId' in parents and trashed=false"
            val existing = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
            if (existing != null) {
                drive.files().update(existing.id, DriveFile(), content).execute()
            } else {
                val meta = DriveFile().apply {
                    this.name    = name
                    parents      = listOf(folderId)
                }
                drive.files().create(meta, content).setFields("id").execute()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadNamedFile($name) failed", e)
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
            Log.e(TAG, "importSettings failed", e)
            false
        }
    }

    // ── Public: download diary JSON string from Drive ─────────────────────────
    suspend fun downloadDiaryJson(context: Context): String? = withContext(Dispatchers.IO) {
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
            Log.e(TAG, "downloadDiaryJson failed", e)
            null
        }
    }
}

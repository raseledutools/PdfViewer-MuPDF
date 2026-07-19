package com.rasel.RasFocus.drivebackup

/**
 * DriveBackupManager.kt
 * ─────────────────────────────────────────────────────────────────────────────
 * Handles everything related to backing up app data to the signed-in user's
 * Google Drive, inside a single app-owned folder named "RasFocus+".
 *
 * What it does:
 *  1. On successful Google Sign-In, finds (or creates) a "RasFocus+" folder
 *     in the user's Drive — using the `drive.file` scope, so this app can
 *     only ever see/manage files IT created, never the user's other Drive
 *     files.
 *  2. Backs up known SharedPreferences ("settings") into a single JSON file
 *     inside that folder, and keeps updating (overwriting) the same file on
 *     every sync — never creates duplicates.
 *  3. Whenever a Diary PDF is exported, uploads it into the same folder,
 *     also updating (overwriting) the same Drive file each time so the
 *     user always has ONE up-to-date PDF in Drive rather than a pile of
 *     old exports.
 *
 * REQUIRED SETUP (one-time, in Google Cloud Console):
 *  - The OAuth consent screen + Android OAuth client must already exist
 *    (it does — see GoogleAccountManager.kt / BuildConfig.GOOGLE_WEB_CLIENT_ID).
 *  - No extra "sensitive scope" verification is needed because this uses
 *    `drive.file` (a restricted-but-not-sensitive scope): the app can only
 *    touch files/folders it itself created.
 *
 * REQUIRED BUILD.GRADLE DEPENDENCIES (added in app/build.gradle.kts):
 *   implementation("com.google.api-client:google-api-client-android:2.2.0")
 *   implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
 *   implementation("com.google.http-client:google-http-client-gson:1.44.2")
 *
 * REQUIRED SIGN-IN SCOPE (added in MainActivity's GoogleSignInOptions):
 *   .requestScopes(Scope(DriveScopes.DRIVE_FILE))
 * ─────────────────────────────────────────────────────────────────────────────
 */

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
import org.json.JSONObject
import java.io.File as JFile

object DriveBackupManager {

    private const val TAG = "DriveBackupManager"

    const val APP_FOLDER_NAME = "RasFocus+"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    private const val SETTINGS_FILE_NAME    = "rasfocus_settings_backup.json"
    private const val DIARY_ALL_FILE_NAME   = "RasFocus_Diary_AllEntries.pdf"
    private const val DIARY_SINGLE_FILE_NAME= "RasFocus_Diary_LatestEntry.pdf"
    private const val DIARY_JSON_FILE_NAME  = "RasFocus_Diary_Backup.json"

    private const val PREF_NAME = "drive_backup_prefs"
    private const val KEY_FOLDER_ID = "app_folder_id"

    // Names of all SharedPreferences files in the app that count as
    // "settings" and should be mirrored into the Drive backup JSON.
    // Add a name here any time a new prefs-backed settings screen is built.
    private val SETTINGS_PREF_NAMES = listOf(
        "google_accounts_prefs",
        "rasfocus_settings",
        "self_control_prefs",
        "parental_prefs",
        "diary_prefs",
        "focus_service_prefs"
    )

    // ── Public: is Drive backup available right now? ──────────────────────
    fun isAvailable(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    // ── Public: run a full sync (folder + settings) — call after sign-in ──
    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateAppFolderId(context, drive) ?: return@withContext false
            backupSettingsInternal(context, drive, folderId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncNow failed", e)
            false
        }
    }

    // ── Public: upload/update the "all entries" diary PDF ─────────────────
    suspend fun uploadDiaryAllEntriesPdf(context: Context, localFile: JFile): Boolean =
        uploadNamedFile(context, localFile, DIARY_ALL_FILE_NAME, "application/pdf")

    // ── Public: upload/update the "single entry" diary PDF ────────────────
    suspend fun uploadDiarySingleEntryPdf(context: Context, localFile: JFile): Boolean =
        uploadNamedFile(context, localFile, DIARY_SINGLE_FILE_NAME, "application/pdf")

    // ── Public: upload/update diary JSON backup ────────────────────────────
    suspend fun uploadDiaryJson(context: Context, localFile: JFile): Boolean =
        uploadNamedFile(context, localFile, DIARY_JSON_FILE_NAME, "application/json")

    // ── Public: restore settings JSON from Drive ───────────────────────────
    suspend fun importSettingsFromDrive(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive    = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateAppFolderId(context, drive) ?: return@withContext false
            val query    = "name='$SETTINGS_FILE_NAME' and '$folderId' in parents and trashed=false"
            val found    = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
                ?: return@withContext false

            val stream = drive.files().get(found.id).executeMediaAsInputStream()
            val json   = org.json.JSONObject(stream.bufferedReader().readText())

            for (prefName in SETTINGS_PREF_NAMES) {
                if (!json.has(prefName)) continue
                val obj   = json.getJSONObject(prefName)
                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val edit  = prefs.edit()
                obj.keys().forEach { key ->
                    when (val v = obj.get(key)) {
                        is Boolean -> edit.putBoolean(key, v)
                        is Int     -> edit.putInt(key, v)
                        is Long    -> edit.putLong(key, v)
                        is Float   -> edit.putFloat(key, v)
                        is String  -> edit.putString(key, v)
                        else       -> edit.putString(key, v.toString())
                    }
                }
                edit.apply()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "importSettingsFromDrive failed", e)
            false
        }
    }

    // ── Public: download diary JSON from Drive and return parsed entries ───
    suspend fun importDiaryJsonFromDrive(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val drive    = buildDriveService(context) ?: return@withContext null
            val folderId = getOrCreateAppFolderId(context, drive) ?: return@withContext null
            val query    = "name='$DIARY_JSON_FILE_NAME' and '$folderId' in parents and trashed=false"
            val found    = drive.files().list().setQ(query).setSpaces("drive")
                .setFields("files(id)").execute().files?.firstOrNull()
                ?: return@withContext null

            val stream = drive.files().get(found.id).executeMediaAsInputStream()
            stream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "importDiaryJsonFromDrive failed", e)
            null
        }
    }

    private suspend fun uploadNamedFile(
        context: Context,
        localFile: JFile,
        driveFileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = buildDriveService(context) ?: return@withContext false
            val folderId = getOrCreateAppFolderId(context, drive) ?: return@withContext false
            uploadOrUpdate(drive, folderId, driveFileName, mimeType, localFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadNamedFile($driveFileName) failed", e)
            false
        }
    }

    // ── Drive service builder (uses the already-signed-in Google account) ─
    // NOTE: GoogleAccountCredential internally calls AccountManager which
    // can block — always call this from a background thread (Dispatchers.IO).
    private fun buildDriveService(context: Context): Drive? {
        return try {
            val account: GoogleSignInAccount =
                GoogleSignIn.getLastSignedInAccount(context) ?: return null

            if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
                Log.w(TAG, "Drive scope not granted for \${account.email}")
                return null
            }

            // account.account can be null if the device account isn't yet
            // synced — guard against it to prevent NPE crash
            val androidAccount = account.account ?: run {
                Log.w(TAG, "account.account is null — skipping Drive init")
                return null
            }

            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = androidAccount

            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("RasFocus+").build()
        } catch (e: Exception) {
            Log.e(TAG, "buildDriveService failed", e)
            null
        }
    }

    // ── Find (or create) the "RasFocus+" app folder, cached by ID ─────────
    private fun getOrCreateAppFolderId(context: Context, drive: Drive): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cachedId = prefs.getString(KEY_FOLDER_ID, null)

        // Verify the cached folder still exists and isn't trashed.
        if (cachedId != null) {
            try {
                val existing = drive.files().get(cachedId)
                    .setFields("id, trashed")
                    .execute()
                if (existing != null && existing.trashed != true) {
                    return cachedId
                }
            } catch (_: Exception) {
                // Cached id is stale (deleted/inaccessible) — fall through to re-find/create.
            }
        }

        // Search for an existing "RasFocus+" folder owned by this app.
        val query = "mimeType='$FOLDER_MIME' and name='$APP_FOLDER_NAME' and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val foundId = result.files?.firstOrNull()?.id
        if (foundId != null) {
            prefs.edit().putString(KEY_FOLDER_ID, foundId).apply()
            return foundId
        }

        // Not found — create it.
        val folderMetadata = DriveFile().apply {
            name = APP_FOLDER_NAME
            mimeType = FOLDER_MIME
        }
        val created = drive.files().create(folderMetadata)
            .setFields("id")
            .execute()

        prefs.edit().putString(KEY_FOLDER_ID, created.id).apply()
        return created.id
    }

    // ── Create-or-update a single named file inside the app folder ────────
    private fun uploadOrUpdate(
        drive: Drive,
        folderId: String,
        fileName: String,
        mimeType: String,
        localFile: JFile
    ) {
        val content = FileContent(mimeType, localFile)

        val query = "name='$fileName' and '$folderId' in parents and trashed=false"
        val existing = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            ?.firstOrNull()

        if (existing != null) {
            // Update contents in place — same file id, same share link, no duplicates.
            drive.files().update(existing.id, null, content).execute()
        } else {
            val metadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
            }
            drive.files().create(metadata, content).setFields("id").execute()
        }
    }

    // ── Gather all known SharedPreferences into one JSON file, then upload ─
    private fun backupSettingsInternal(context: Context, drive: Drive, folderId: String) {
        val root = JSONObject()
        for (prefName in SETTINGS_PREF_NAMES) {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val all = prefs.all
            if (all.isEmpty()) continue
            val obj = JSONObject()
            for ((key, value) in all) {
                obj.put(key, value)
            }
            root.put(prefName, obj)
        }
        root.put("backed_up_at", System.currentTimeMillis())

        val tempFile = JFile(context.cacheDir, SETTINGS_FILE_NAME)
        tempFile.writeText(root.toString(2))

        uploadOrUpdate(drive, folderId, SETTINGS_FILE_NAME, "application/json", tempFile)
        tempFile.delete()
    }
}

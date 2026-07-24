package com.rasel.RasFocus

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val publishedAt: String,
    val downloadUrl: String
)

class AutoUpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val prefs = applicationContext.getSharedPreferences(AutoUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            val lastTag = prefs.getString(AutoUpdater.LAST_TAG_KEY, "") ?: ""
            val info = AutoUpdater.fetchLatestReleaseInfoSync(applicationContext)
            if (info != null && info.tagName != lastTag) {
                // Background worker শুধু check করে — actual download user-initiated
                AutoUpdater.showUpdateAvailableNotification(applicationContext, info)
            }
            Result.success()
        }
    }
}

object AutoUpdater {
    private const val GITHUB_OWNER = "raseledutools"
    private const val GITHUB_REPO  = "RasFocus-final"
    private const val TAG = "AutoUpdater"
    const val PREFS_NAME    = "AutoUpdaterPrefs"
    const val LAST_TAG_KEY  = "last_installed_tag"
    const val NOTIFICATION_ID       = 554433
    const val NOTIF_UPDATE_AVAIL    = 554434
    const val CHANNEL_ID = "update_channel"

    // ★ Full release APK — GitHub Actions এর actual asset name
    const val APK_UNIVERSAL   = "app-full-universal-release.apk"
    const val APK_ARMEABI     = "app-full-armeabi-v7a-release.apk"

    fun setupBackgroundAutoUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<AutoUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AutoUpdateCheck", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    // ── GitHub API থেকে latest release info fetch ──────────────────────────
    suspend fun fetchLatestReleaseInfoSync(context: Context): ReleaseInfo? {
        return try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tagName = json.getString("tag_name")

            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(json.getString("published_at"))
            val publishedAt = if (date != null)
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            else json.getString("published_at")

            // ★ Device architecture অনুযায়ী সঠিক APK বেছে নাও
            val preferredApk = getPreferredApkName(context)
            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            var fallbackUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name == preferredApk) downloadUrl = asset.getString("browser_download_url")
                if (name == APK_UNIVERSAL) fallbackUrl = asset.getString("browser_download_url")
            }
            if (downloadUrl.isEmpty()) downloadUrl = fallbackUrl
            if (downloadUrl.isEmpty()) return null

            ReleaseInfo(tagName, publishedAt, downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatestReleaseInfoSync failed", e)
            null
        }
    }

    fun fetchLatestReleaseInfo(context: Context, onResult: (ReleaseInfo?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = fetchLatestReleaseInfoSync(context)
            withContext(Dispatchers.Main) { onResult(info) }
        }
    }

    // ── App launch এ check — update থাকলে callback ────────────────────────
    fun checkForUpdates(context: Context, onUpdateAvailable: ((ReleaseInfo) -> Unit)? = null) {
        fetchLatestReleaseInfo(context) { info ->
            if (info == null) return@fetchLatestReleaseInfo
            val currentTag = "v" + BuildConfig.VERSION_NAME
            if (info.tagName != currentTag) {
                onUpdateAvailable?.invoke(info) ?: showUpdateAvailableNotification(context, info)
            }
        }
    }

    // ── Device ABI detect ─────────────────────────────────────────────────
    private fun getPreferredApkName(context: Context): String {
        val abis = Build.SUPPORTED_ABIS
        return if (abis.any { it.contains("armeabi-v7a") && !it.contains("arm64") })
            APK_ARMEABI else APK_UNIVERSAL
    }

    // ── DownloadManager দিয়ে download — progress notification auto ────────
    fun downloadWithProgress(context: Context, info: ReleaseInfo, onDownloadId: (Long) -> Unit) {
        ensureChannel(context)
        val apkName = "RasFocus_${info.tagName}.apk"
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
            setTitle("RasFocus Update ${info.tagName}")
            setDescription("Downloading...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, apkName)
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // পুরনো ডাউনলোড ফাইল মুছে দাও
        getDownloadedFile(context, info.tagName)?.delete()
        val downloadId = dm.enqueue(request)
        onDownloadId(downloadId)
    }

    // ── Download শেষ হলে file path পাও ──────────────────────────────────
    fun getDownloadedFile(context: Context, tag: String): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val f = File(dir, "RasFocus_$tag.apk")
        return if (f.exists() && f.length() > 0) f else null
    }

    // ── DownloadManager query — progress percentage ───────────────────────
    fun queryProgress(context: Context, downloadId: Long): Pair<Int, Int> {
        // Returns Pair(percent 0-100, status)
        // status: DownloadManager.STATUS_RUNNING / STATUS_SUCCESSFUL / STATUS_FAILED
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = dm.query(query)
        if (!cursor.moveToFirst()) { cursor.close(); return Pair(0, DownloadManager.STATUS_FAILED) }
        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        cursor.close()
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        return Pair(percent, status)
    }

    // ── Install ───────────────────────────────────────────────────────────
    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "installApk failed", e)
        }
    }

    // ── Notification: update available (background check এ) ───────────────
    fun showUpdateAvailableNotification(context: Context, info: ReleaseInfo) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("RasFocus Update Available")
            .setContentText("Version ${info.tagName} is ready. Open app to install.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_UPDATE_AVAIL, notif)
    }

    // ── Tag save ──────────────────────────────────────────────────────────
    fun saveTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(LAST_TAG_KEY, tag).apply()
    }

    // ── Alias helpers (called from MissingServices / SelfControlModule / InstallUpdateReceiver) ──

    /** Alias for installApk — installs a previously downloaded APK file. */
    fun installDownloadedUpdate(context: Context, file: java.io.File) =
        installApk(context, file)

    /**
     * Alias for downloadWithProgress — silently queues a background download.
     * [apkName] is ignored (kept for call-site compatibility); actual name is derived from [tag].
     */
    fun silentDownloadUpdate(context: Context, apkName: String, tag: String) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            val info = fetchLatestReleaseInfoSync(context) ?: return@launch
            downloadWithProgress(context, info) { /* download id ignored in silent mode */ }
        }
    }

    /** Alias for getDownloadedFile — returns the cached APK for the given tag, or null. */
    fun getDownloadedUpdateFile(context: Context, tag: String): java.io.File? =
        getDownloadedFile(context, tag)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "RasFocus update notifications"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}

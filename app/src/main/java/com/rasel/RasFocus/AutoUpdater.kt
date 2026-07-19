package com.rasel.RasFocus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val publishedAt: String
)

class AutoUpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val prefs = applicationContext.getSharedPreferences(AutoUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            val lastTag = prefs.getString(AutoUpdater.LAST_TAG_KEY, "") ?: ""
            
            val info = AutoUpdater.fetchLatestReleaseInfoSync()
            if (info != null && info.tagName != lastTag) {
                val success = AutoUpdater.downloadUpdateSync(applicationContext, AutoUpdater.getBestApkVariant(), info.tagName)
                if (success) {
                    AutoUpdater.saveTag(applicationContext, info.tagName)
                    return@withContext Result.success()
                } else {
                    return@withContext Result.retry()
                }
            }
            Result.success()
        }
    }
}

object AutoUpdater {
    private const val GITHUB_OWNER = "raseledutools"
    private const val GITHUB_REPO = "RasFocus-final"
    private const val TAG = "AutoUpdater"
    const val PREFS_NAME = "AutoUpdaterPrefs"
    const val LAST_TAG_KEY = "last_installed_tag"
    const val NOTIFICATION_ID = 554433
    const val CHANNEL_ID = "update_channel"

    // APK flavors (used as substrings for matching)
    const val APK_UNIVERSAL   = "universal"
    const val APK_LIGHT       = "light"
    const val APK_FULL_SPLIT  = "armeabi-v7a"   // 32-bit split APK filename fragment

    /**
     * Device ABI দেখে সঠিক APK বেছে নেয়:
     *  - armeabi-v7a (32-bit) → split APK (armeabi-v7a)
     *  - arm64-v8a / x86_64 / অন্য  → universal APK
     * Workflow এখন দুটোই release করে।
     */
    fun getBestApkVariant(): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return if (abi == "armeabi-v7a") APK_FULL_SPLIT else APK_UNIVERSAL
    }

    fun setupBackgroundAutoUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val updateRequest = PeriodicWorkRequestBuilder<AutoUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AutoUpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    suspend fun fetchLatestReleaseInfoSync(): ReleaseInfo? {
        return try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val publishedAtRaw = json.getString("published_at")
                
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(publishedAtRaw)
                
                val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val publishedAt = if (date != null) formatter.format(date) else publishedAtRaw

                ReleaseInfo(tagName, publishedAt)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch release info", e)
            null
        }
    }

    fun fetchLatestReleaseInfo(onResult: (ReleaseInfo?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = fetchLatestReleaseInfoSync()
            withContext(Dispatchers.Main) {
                onResult(info)
            }
        }
    }

    fun checkForUpdates(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTag = prefs.getString(LAST_TAG_KEY, "") ?: ""
        fetchLatestReleaseInfo { info ->
            if (info != null && info.tagName != lastTag) {
                silentDownloadUpdate(context, getBestApkVariant(), info.tagName)
            }
        }
    }

    // Manual Download — in-app, no browser needed
    // Download শেষে automatically install prompt দেখাবে
    fun downloadAndInstallUpdate(context: Context, targetApkName: String, newTag: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Already downloaded? সরাসরি install করো
            val existing = getDownloadedUpdateFile(context, newTag)
            if (existing != null) {
                withContext(Dispatchers.Main) { triggerInstall(context, existing) }
                return@launch
            }
            // In-app download with progress notification
            val success = downloadUpdateSync(context, targetApkName, newTag)
            if (success) {
                saveTag(context, newTag)
                val apkFile = getDownloadedUpdateFile(context, newTag)
                if (apkFile != null) {
                    withContext(Dispatchers.Main) { triggerInstall(context, apkFile) }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed. Check internet.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Install prompt — system installer, no browser
    fun triggerInstall(context: Context, apkFile: java.io.File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Background Silent Download
    fun silentDownloadUpdate(context: Context, targetApkName: String, newTag: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = downloadUpdateSync(context, targetApkName, newTag)
            if (success) {
                saveTag(context, newTag)
            }
        }
    }
    
    suspend fun downloadUpdateSync(context: Context, targetApkName: String, newTag: String): Boolean {
        return try {
            if (getDownloadedUpdateFile(context, newTag) != null) {
                Log.d(TAG, "Update already downloaded: $newTag")
                return true
            }

            val downloadUrl = fetchDownloadUrl(targetApkName) ?: return false

            val rasDir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "RasFocus"
            )
            rasDir.mkdirs()
            rasDir.listFiles()?.filter { it.name.startsWith("RasFocus_") && it.name.endsWith(".apk") }
                ?.forEach { it.delete() }

            val apkFile = java.io.File(rasDir, "RasFocus_$newTag.apk")

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}")
                return false
            }

            // ── Progress notification setup ────────────────────────────────────
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_LOW)
                notifManager.createNotificationChannel(ch)
            }
            val progressBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("RasFocus আপডেট ডাউনলোড হচ্ছে")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            val totalBytes = connection.contentLengthLong   // -1 if unknown
            val input  = connection.inputStream
            val output = FileOutputStream(apkFile)
            val buf    = ByteArray(8192)
            var downloaded = 0L
            var lastNotifPct = -1
            var count: Int

            while (input.read(buf).also { count = it } != -1) {
                output.write(buf, 0, count)
                downloaded += count

                if (totalBytes > 0) {
                    val pct = (downloaded * 100 / totalBytes).toInt()
                    // notification প্রতি 5% এ update করো — বেশি frequent হলে system slow হয়
                    if (pct >= lastNotifPct + 5) {
                        lastNotifPct = pct
                        val mb = "%.1f".format(downloaded / 1_048_576f)
                        val total = "%.1f".format(totalBytes / 1_048_576f)
                        progressBuilder
                            .setContentText("$mb MB / $total MB")
                            .setProgress(100, pct, false)
                        notifManager.notify(NOTIFICATION_ID, progressBuilder.build())
                    }
                } else {
                    // size অজানা হলে indeterminate bar দেখাও
                    val mb = "%.1f".format(downloaded / 1_048_576f)
                    progressBuilder
                        .setContentText("$mb MB ডাউনলোড হয়েছে...")
                        .setProgress(0, 0, true)
                    notifManager.notify(NOTIFICATION_ID, progressBuilder.build())
                }
            }

            output.flush(); output.close(); input.close()

            // Progress notification সরিয়ে install notification দেখাও
            notifManager.cancel(NOTIFICATION_ID)
            showInstallNotification(context, apkFile, newTag)
            Log.d(TAG, "Download complete: $newTag")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            false
        }
    }

    private suspend fun fetchDownloadUrl(targetApkName: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val assets = JSONObject(connection.inputStream.bufferedReader().readText())
                .getJSONArray("assets")

            // ── Split APK request (armeabi-v7a): prefer exact split, reject universal ──
            // Universal APK নামেও "armeabi" থাকে না, কিন্তু নিশ্চিত হতে
            // universal শব্দ থাকলে skip করছি।
            if (targetApkName == APK_FULL_SPLIT) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name  = asset.getString("name")
                    if (name.contains("armeabi-v7a", ignoreCase = true) &&
                        !name.contains("universal", ignoreCase = true)) {
                        return asset.getString("browser_download_url")
                    }
                }
            }

            // ── Universal / light request: prefer universal, avoid split ──
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name  = asset.getString("name")
                if (name.contains(targetApkName, ignoreCase = true)) {
                    return asset.getString("browser_download_url")
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun showInstallNotification(context: Context, apkFile: File, newTag: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new app updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Notification tap → সরাসরি system installer খোলে
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", apkFile
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✅ RasFocus আপডেট প্রস্তুত!")
            .setContentText("$newTag ডাউনলোড সম্পন্ন — install করতে tap করুন")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun getDownloadedUpdateFile(context: Context, tag: String): File? {
        val rasDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "RasFocus"
        )
        val file = File(rasDir, "RasFocus_$tag.apk")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun installDownloadedUpdate(context: Context, file: File) {
        try {
            // File exist করে এবং valid APK size (কমপক্ষে 1MB) কিনা check
            if (!file.exists() || file.length() < 1_000_000L) {
                Toast.makeText(context, "APK file is missing or corrupted. Please try again.", Toast.LENGTH_LONG).show()
                file.delete() // corrupt file মুছে দাও যাতে পরের check-এ re-download হয়
                return
            }
            val intent = getInstallIntent(context, file)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Installation failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    fun saveTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_TAG_KEY, tag)
            .apply()
    }
}

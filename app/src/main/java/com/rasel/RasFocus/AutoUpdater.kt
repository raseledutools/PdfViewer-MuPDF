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
                val success = AutoUpdater.downloadUpdateSync(applicationContext, AutoUpdater.APK_FULL_SPLIT, info.tagName)
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
    const val APK_UNIVERSAL = "universal"
    const val APK_LIGHT = "light"
    const val APK_FULL_SPLIT = "full-armeabi-v7a"

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
                silentDownloadUpdate(context, APK_FULL_SPLIT, info.tagName)
            }
        }
    }

    // Manual Download (opens browser)
    fun downloadAndInstallUpdate(context: Context, targetApkName: String, newTag: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val downloadUrl = fetchDownloadUrl(targetApkName)
            if (downloadUrl != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Opening update in browser...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        saveTag(context, newTag)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to open browser.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "APK not found in release assets.", Toast.LENGTH_SHORT).show()
                }
            }
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
            val updateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
            
            updateDir.listFiles()?.forEach { it.delete() }

            val apkFile = File(updateDir, "RasFocus_$newTag.apk")
            
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}")
                return false
            }

            val input = connection.inputStream
            val output = FileOutputStream(apkFile)

            val data = ByteArray(4096)
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            showInstallNotification(context, apkFile, newTag)
            Log.d(TAG, "Silent download complete: $newTag")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Silent download failed", e)
            false
        }
    }

    private suspend fun fetchDownloadUrl(targetApkName: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val assets = json.getJSONArray("assets")
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").contains(targetApkName, ignoreCase = true)) {
                        return asset.getString("browser_download_url")
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
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

        // Use BroadcastReceiver so we can check canRequestPackageInstalls()
        // before attempting install — prevents hang on notification tap
        val broadcastIntent = Intent(InstallUpdateReceiver.ACTION_INSTALL_UPDATE).apply {
            setClass(context, InstallUpdateReceiver::class.java)
            putExtra(InstallUpdateReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, NOTIFICATION_ID, broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("RasFocus Update Ready")
            .setContentText("Version $newTag downloaded. Tap to install.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun getDownloadedUpdateFile(context: Context, tag: String): File? {
        val updateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(updateDir, "RasFocus_$tag.apk")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun installDownloadedUpdate(context: Context, file: File) {
        try {
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

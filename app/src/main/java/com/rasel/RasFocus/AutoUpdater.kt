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
import androidx.work.OneTimeWorkRequestBuilder
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

// ─────────────────────────────────────────────────────────────────────────────
// Background WorkManager worker — runs every 15 min, downloads if new release
// ─────────────────────────────────────────────────────────────────────────────
class AutoUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val prefs = applicationContext.getSharedPreferences(
                AutoUpdater.PREFS_NAME, Context.MODE_PRIVATE
            )
            val lastTag = prefs.getString(AutoUpdater.LAST_TAG_KEY, "") ?: ""

            val info = AutoUpdater.fetchLatestReleaseInfoSync() ?: return@withContext Result.retry()

            if (info.tagName == lastTag) {
                Log.d("AutoUpdater", "Already on latest: $lastTag")
                return@withContext Result.success()
            }

            // Developer mode: download split (armeabi-v7a) — smaller, faster
            val success = AutoUpdater.downloadUpdateSync(
                applicationContext,
                AutoUpdater.APK_SPLIT,   // armeabi-v7a split for dev device
                info.tagName
            )
            if (success) {
                AutoUpdater.saveTag(applicationContext, info.tagName)
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AutoUpdater singleton
// ─────────────────────────────────────────────────────────────────────────────
object AutoUpdater {
    private const val GITHUB_OWNER = "raseledutools"
    private const val GITHUB_REPO  = "RasFocus-final"
    private const val TAG          = "AutoUpdater"

    const val PREFS_NAME    = "AutoUpdaterPrefs"
    const val LAST_TAG_KEY  = "last_installed_tag"
    const val CHANNEL_ID    = "update_channel"
    const val NOTIF_PROGRESS = 554433   // ongoing progress notification
    const val NOTIF_DONE     = 554434   // tap-to-install notification

    // APK asset substrings — matched against GitHub release asset names
    const val APK_SPLIT     = "armeabi-v7a"   // dev: small, 32-bit split
    const val APK_UNIVERSAL = "universal"      // distribution: all ABIs

    // back-compat aliases — existing callers in SelfControlModule still compile
    const val APK_FULL_SPLIT = APK_SPLIT
    const val APK_LIGHT      = "light"

    // ── Developer / Testing mode ──────────────────────────────────────────────
    // true  → app open/boot এ সাথে সাথে check+download (WorkManager bypass)
    //         same version হলেও notification দেখাবে — testing এ কাজে লাগে
    // false → production: tag compare, periodic 15min background worker
    private const val DEV_MODE = true

    // ── Setup ────────────────────────────────────────────────────────────────
    fun setupBackgroundAutoUpdate(context: Context) {
        if (DEV_MODE) {
            // WorkManager periodic minimum 15 min — Android enforce করে, কমানো যায় না
            // Dev mode এ সরাসরি coroutine এ run করি
            Log.d(TAG, "DEV_MODE: skipping periodic WorkManager — direct coroutine check")
            checkForUpdates(context)
            return
        }

        // Production: periodic 15 min background worker
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<AutoUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AutoUpdateCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        // App open হলে immediate একটা check
        val immediateRequest = OneTimeWorkRequestBuilder<AutoUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(immediateRequest)

        Log.d(TAG, "AutoUpdate scheduled (periodic 15min + immediate)")
    }

    // ── Check + trigger download if new version ──────────────────────────────
    fun checkForUpdates(context: Context) {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTag = prefs.getString(LAST_TAG_KEY, "") ?: ""

        fetchLatestReleaseInfo { info ->
            if (info == null) {
                Log.w(TAG, "checkForUpdates: could not fetch release info")
                return@fetchLatestReleaseInfo
            }

            if (DEV_MODE || info.tagName != lastTag) {
                // DEV_MODE: same tag হলেও download — testing এ notification দেখা যাবে
                Log.d(TAG, "checkForUpdates: new=${info.tagName} last=$lastTag DEV=$DEV_MODE → downloading")
                silentDownloadUpdate(context, APK_SPLIT, info.tagName)
            } else {
                Log.d(TAG, "checkForUpdates: already on latest $lastTag — skip")
            }
        }
    }

    // ── Fetch release metadata ───────────────────────────────────────────────
    suspend fun fetchLatestReleaseInfoSync(): ReleaseInfo? {
        return try {
            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val tagName     = json.getString("tag_name")
                val rawDate     = json.getString("published_at")
                val parser      = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                val date        = parser.parse(rawDate)
                val displayDate = if (date != null)
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(date)
                else rawDate
                ReleaseInfo(tagName, displayDate)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatestReleaseInfoSync failed", e)
            null
        }
    }

    fun fetchLatestReleaseInfo(onResult: (ReleaseInfo?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = fetchLatestReleaseInfoSync()
            withContext(Dispatchers.Main) { onResult(info) }
        }
    }

    // ── Silent background download with progress notification ────────────────
    fun silentDownloadUpdate(context: Context, apkFlavor: String, newTag: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = downloadUpdateSync(context, apkFlavor, newTag)
            if (success) saveTag(context, newTag)
        }
    }

    suspend fun downloadUpdateSync(context: Context, apkFlavor: String, newTag: String): Boolean {
        return try {
            // Already downloaded?
            if (getDownloadedUpdateFile(context, newTag) != null) {
                Log.d(TAG, "Already downloaded: $newTag")
                showDoneNotification(context, getDownloadedUpdateFile(context, newTag)!!, newTag)
                return true
            }

            val (downloadUrl, assetName) = fetchDownloadUrlAndName(apkFlavor)
                ?: return false

            // Downloads/RasFocus/
            val rasDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "RasFocus"
            )
            rasDir.mkdirs()

            // পুরনো APK মুছে দাও
            rasDir.listFiles()
                ?.filter { it.name.startsWith("RasFocus_") && it.name.endsWith(".apk") }
                ?.forEach { it.delete() }

            val apkFile = File(rasDir, assetName)

            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP ${conn.responseCode}")
                return false
            }

            val totalBytes = conn.contentLength.toLong()  // -1 if unknown
            val nm        = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            var downloadedBytes = 0L
            var lastNotifPercent = -1

            val input  = conn.inputStream
            val output = FileOutputStream(apkFile)
            val buf    = ByteArray(8192)
            var n: Int

            while (input.read(buf).also { n = it } != -1) {
                output.write(buf, 0, n)
                downloadedBytes += n

                // Progress notification — update every 5%
                if (totalBytes > 0) {
                    val pct = (downloadedBytes * 100 / totalBytes).toInt()
                    if (pct / 5 != lastNotifPercent / 5) {
                        lastNotifPercent = pct
                        showProgressNotification(context, nm, newTag, pct, apkFlavor)
                    }
                } else {
                    // Unknown size — show indeterminate every ~256 KB
                    if (downloadedBytes % (256 * 1024) < 8192) {
                        showProgressNotification(context, nm, newTag, -1, apkFlavor)
                    }
                }
            }

            output.flush()
            output.close()
            input.close()

            // Cancel progress notif, show tap-to-install
            nm.cancel(NOTIF_PROGRESS)
            showDoneNotification(context, apkFile, newTag)

            Log.d(TAG, "Download done: ${apkFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_PROGRESS)
            false
        }
    }

    // ── Progress notification (ongoing, with % bar) ──────────────────────────
    private fun showProgressNotification(
        context: Context,
        nm: NotificationManager,
        tag: String,
        percent: Int,       // -1 = indeterminate
        flavor: String
    ) {
        val flavorLabel = if (flavor == APK_SPLIT) "split" else "universal"
        val body = if (percent >= 0) "$percent% — RasFocus $tag ($flavorLabel)"
                   else "Downloading… RasFocus $tag ($flavorLabel)"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading RasFocus Update")
            .setContentText(body)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)           // can't be dismissed while downloading
            .setOnlyAlertOnce(true)     // no sound/vibrate on every update
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(NOTIF_PROGRESS, notif)
    }

    // ── Done notification (tap to install) ───────────────────────────────────
    private fun showDoneNotification(context: Context, apkFile: File, newTag: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val installIntent = getInstallIntent(context, apkFile)
        val pi = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✅ RasFocus $newTag — Ready to Install")
            .setContentText("Tap to install now")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_DONE, notif)
    }

    // ── Fetch asset download URL + filename ──────────────────────────────────
    private suspend fun fetchDownloadUrlAndName(apkFlavor: String): Pair<String, String>? {
        return try {
            val url  = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val json   = JSONObject(conn.inputStream.bufferedReader().readText())
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name  = asset.getString("name")
                    if (name.contains(apkFlavor, ignoreCase = true)) {
                        return Pair(asset.getString("browser_download_url"), name)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchDownloadUrlAndName failed", e)
            null
        }
    }

    // ── Manual browser download (fallback) ───────────────────────────────────
    fun downloadAndInstallUpdate(context: Context, targetApkName: String, newTag: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = fetchDownloadUrlAndName(targetApkName)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    Toast.makeText(context, "Opening update in browser…", Toast.LENGTH_SHORT).show()
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(result.first))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        saveTag(context, newTag)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to open browser.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "APK not found in release assets.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    fun getDownloadedUpdateFile(context: Context, tag: String): File? {
        val rasDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "RasFocus"
        )
        if (!rasDir.exists()) return null
        return rasDir.listFiles()
            ?.firstOrNull {
                it.name.contains(tag) &&
                it.name.endsWith(".apk") &&
                it.length() > 1_000_000L  // minimum 1 MB — corrupt file guard
            }
    }

    fun installDownloadedUpdate(context: Context, file: File) {
        try {
            if (!file.exists() || file.length() < 1_000_000L) {
                Toast.makeText(context, "APK missing or corrupted — re-downloading.", Toast.LENGTH_LONG).show()
                file.delete()
                return
            }
            context.startActivity(getInstallIntent(context, file))
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Installation failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_LOW)
                    .also { it.description = "RasFocus update download progress" }
            )
        }
    }

    fun saveTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(LAST_TAG_KEY, tag).apply()
    }
}

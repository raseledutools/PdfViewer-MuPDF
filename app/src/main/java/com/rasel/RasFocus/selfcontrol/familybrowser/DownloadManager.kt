package com.rasel.RasFocus.selfcontrol.familybrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * DownloadManager.kt
 * Handles file downloads with progress tracking, notifications, and resume support.
 *
 * Features:
 * - Download any file (PDF, image, video, etc.)
 * - Progress notification with percentage
 * - Pause / Resume / Cancel
 * - Download history
 * - Auto-open after download
 * - Organized save locations by file type
 */

enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    var fileName: String,
    var mimeType: String = "application/octet-stream",
    var totalBytes: Long = -1L,
    var downloadedBytes: Long = 0L,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var errorMessage: String = "",
    var localPath: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0L
) {
    val progress: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
    val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING
}

class BrowserDownloadManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "Downloads"
        const val MAX_CONCURRENT = 3
        private const val BUFFER_SIZE = 8192
    }

    val downloads = mutableStateListOf<DownloadItem>()
    private val activeJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        createNotificationChannel()
    }

    // ─── Start Download ───────────────────────────────────────────────────────

    fun startDownload(
        url: String,
        fileName: String? = null,
        mimeType: String = "application/octet-stream",
        userAgent: String = "FamilyBrowser/1.0",
        referer: String? = null,
        cookies: String? = null
    ): DownloadItem {
        val safeFileName = fileName ?: extractFileName(url)
        val item = DownloadItem(url = url, fileName = safeFileName, mimeType = mimeType)
        downloads.add(0, item)

        // ── সঙ্গে সঙ্গে "Starting..." notification দেখাও ───────────────────
        // এটা না থাকলে ছোট ফাইল শেষ হয়ে যাবে কিন্তু কিছু দেখাবে না
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val startNotif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(safeFileName)
                .setContentText("Starting download...")
                .setProgress(0, 0, true)  // indeterminate
                .setOngoing(true)
                .setSilent(true)
                .build()
            nm.notify(item.id.hashCode(), startNotif)
        } catch (e: Exception) { /* ignore */ }

        val job = scope.launch {
            executeDownload(item, userAgent, referer, cookies)
        }
        activeJobs[item.id] = job
        return item
    }

    private suspend fun executeDownload(
        item: DownloadItem,
        userAgent: String,
        referer: String?,
        cookies: String?
    ) = withContext(Dispatchers.IO) {
        try {
            updateItem(item.id) { it.copy(status = DownloadStatus.DOWNLOADING) }

            val connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", userAgent)
                referer?.let { setRequestProperty("Referer", it) }
                cookies?.let { setRequestProperty("Cookie", it) }
                // Resume support
                if (item.downloadedBytes > 0) {
                    setRequestProperty("Range", "bytes=${item.downloadedBytes}-")
                }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                updateItem(item.id) { it.copy(status = DownloadStatus.FAILED, errorMessage = "HTTP $responseCode") }
                return@withContext
            }

            val total = connection.contentLengthLong
            if (total > 0) updateItem(item.id) { it.copy(totalBytes = total) }

            val file = getOutputFile(item)
            updateItem(item.id) { it.copy(localPath = file.absolutePath) }

            // ── Download শুরু হওয়ার সাথে সাথে প্রথম notification দেখাও ──────
            showProgressNotification(downloads.find { it.id == item.id }!!)

            connection.inputStream.use { input ->
                FileOutputStream(file, item.downloadedBytes > 0).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = item.downloadedBytes
                    var bytes: Int
                    // Time-based throttle: প্রতি 500ms এ একবার notification update
                    var lastNotifTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytes = it } != -1) {
                        // Check for cancellation
                        val current = downloads.find { it.id == item.id }
                        if (current?.status == DownloadStatus.CANCELLED) {
                            file.delete()
                            return@withContext
                        }
                        if (current?.status == DownloadStatus.PAUSED) {
                            updateItem(item.id) { it.copy(downloadedBytes = downloaded) }
                            return@withContext
                        }

                        output.write(buffer, 0, bytes)
                        downloaded += bytes

                        // প্রতি 500ms এ progress notification — ছোট বড় সব ফাইলে কাজ করে
                        val now = System.currentTimeMillis()
                        if (now - lastNotifTime >= 500L) {
                            lastNotifTime = now
                            updateItem(item.id) { it.copy(downloadedBytes = downloaded) }
                            showProgressNotification(downloads.find { it.id == item.id }!!)
                        }
                    }

                    // Final bytes save করো
                    updateItem(item.id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = downloaded,
                            completedAt = System.currentTimeMillis()
                        )
                    }

                    // Android 10+ — cache থেকে public Downloads/Pictures/etc. এ move
                    val finalPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        moveToMediaStore(downloads.find { it.id == item.id }!!, file)
                    } else {
                        file.absolutePath
                    }
                    updateItem(item.id) { it.copy(localPath = finalPath) }

                    showCompletedNotification(downloads.find { it.id == item.id }!!, file)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) return@withContext
            updateItem(item.id) { it.copy(status = DownloadStatus.FAILED, errorMessage = e.message ?: "Unknown error") }
            showErrorNotification(item)
        } finally {
            activeJobs.remove(item.id)
        }
    }

    // ─── Controls ─────────────────────────────────────────────────────────────

    fun pauseDownload(downloadId: String) {
        updateItem(downloadId) { it.copy(status = DownloadStatus.PAUSED) }
    }

    fun resumeDownload(downloadId: String) {
        val item = downloads.find { it.id == downloadId } ?: return
        val updatedItem = item.copy(status = DownloadStatus.DOWNLOADING)
        val index = downloads.indexOfFirst { it.id == downloadId }
        if (index != -1) downloads[index] = updatedItem
        val job = scope.launch { executeDownload(updatedItem, "FamilyBrowser/1.0", null, null) }
        activeJobs[downloadId] = job
    }

    fun cancelDownload(downloadId: String) {
        updateItem(downloadId) { it.copy(status = DownloadStatus.CANCELLED) }
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        NotificationManagerCompat.from(context).cancel(downloadId.hashCode())
    }

    fun removeFromList(downloadId: String) {
        cancelDownload(downloadId)
        downloads.removeAll { it.id == downloadId }
    }

    fun retryDownload(downloadId: String) {
        val item = downloads.find { it.id == downloadId } ?: return
        val reset = item.copy(status = DownloadStatus.PENDING, downloadedBytes = 0, errorMessage = "")
        val index = downloads.indexOfFirst { it.id == downloadId }
        if (index != -1) downloads[index] = reset
        val job = scope.launch { executeDownload(reset, "FamilyBrowser/1.0", null, null) }
        activeJobs[downloadId] = job
    }

    fun openFile(downloadId: String) {
        val item = downloads.find { it.id == downloadId } ?: return

        // Android 10+ এ localPath content:// URI হতে পারে
        val uri: Uri = if (item.localPath.startsWith("content://")) {
            Uri.parse(item.localPath)
        } else {
            val file = File(item.localPath)
            if (!file.exists()) return
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getOutputFile(item: DownloadItem): File {
        // Android 9 এবং নিচে — সরাসরি External Storage
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = when {
                item.mimeType.startsWith("image/") ->
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                item.mimeType.startsWith("video/") ->
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                item.mimeType.startsWith("audio/") ->
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                else ->
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }.also { it.mkdirs() }

            var file = File(dir, item.fileName)
            var counter = 1
            while (file.exists() && item.downloadedBytes == 0L) {
                val dotIndex = item.fileName.lastIndexOf('.')
                val name = if (dotIndex != -1) item.fileName.substring(0, dotIndex) else item.fileName
                val ext = if (dotIndex != -1) item.fileName.substring(dotIndex) else ""
                file = File(dir, "${name}($counter)$ext")
                counter++
            }
            return file
        }

        // Android 10+ — app cache directory তে save করো, পরে MediaStore এ insert করো
        // (scoped storage — WRITE_EXTERNAL ছাড়াই Downloads এ যাবে)
        val cacheDir = File(context.cacheDir, "downloads").also { it.mkdirs() }
        var file = File(cacheDir, item.fileName)
        var counter = 1
        while (file.exists() && item.downloadedBytes == 0L) {
            val dotIndex = item.fileName.lastIndexOf('.')
            val name = if (dotIndex != -1) item.fileName.substring(0, dotIndex) else item.fileName
            val ext = if (dotIndex != -1) item.fileName.substring(dotIndex) else ""
            file = File(cacheDir, "${name}($counter)$ext")
            counter++
        }
        return file
    }

    // Android 10+ এ cache থেকে MediaStore (Downloads/Pictures/etc.) এ move করা
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun moveToMediaStore(item: DownloadItem, cacheFile: File): String {
        val collection = when {
            item.mimeType.startsWith("image/") ->
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            item.mimeType.startsWith("video/") ->
                android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            item.mimeType.startsWith("audio/") ->
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else ->
                android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val subDir = when {
            item.mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            item.mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            item.mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, subDir)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return cacheFile.absolutePath

        resolver.openOutputStream(uri)?.use { out ->
            cacheFile.inputStream().use { it.copyTo(out) }
        }

        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        cacheFile.delete() // cache থেকে মুছে ফেলা
        return uri.toString()
    }

    private fun extractFileName(url: String): String {
        return try {
            val path = URL(url).path
            val name = path.substringAfterLast('/')
            if (name.isNotEmpty() && name.contains('.')) name
            else "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        val index = downloads.indexOfFirst { it.id == id }
        if (index != -1) downloads[index] = transform(downloads[index])
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "File download progress"
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(item: DownloadItem) {
        try {
            val isIndeterminate = item.progress < 0
            val progressText = if (isIndeterminate) {
                formatBytes(item.downloadedBytes) + " — downloading..."
            } else {
                "${item.progress}% · ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(item.fileName)
                .setContentText(progressText)
                .setProgress(100, item.progress.coerceAtLeast(0), isIndeterminate)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)  // বারবার sound না করতে
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(item.id.hashCode(), notification)
        } catch (e: Exception) {
            // SecurityException বা অন্য — চুপ করে ignore করো, crash করো না
        }
    }

    private fun showCompletedNotification(item: DownloadItem, file: File) {
        try {
            // Progress notification সরিয়ে দাও
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(item.id.hashCode())

            // localPath — Android 10+ এ content:// URI, নিচে absolute path
            val latestItem = downloads.find { it.id == item.id } ?: item
            val uri: Uri = when {
                latestItem.localPath.startsWith("content://") ->
                    Uri.parse(latestItem.localPath)
                file.exists() ->
                    androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                else -> Uri.fromFile(file)
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, latestItem.mimeType.ifEmpty { "application/octet-stream" })
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, latestItem.id.hashCode(),
                Intent.createChooser(openIntent, "Open with"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete ✓")
                .setContentText(latestItem.fileName)
                .setSubText(formatBytes(latestItem.downloadedBytes))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

            nm.notify(latestItem.id.hashCode(), notification)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun showErrorNotification(item: DownloadItem) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed")
                .setContentText(item.fileName)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
        } catch (e: Exception) { }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "?"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024f / 1024f)} MB"
        else -> "${"%.2f".format(bytes / 1024f / 1024f / 1024f)} GB"
    }


    fun removeDownload(id: String) {
        downloads.removeAll { it.id == id }
    }

    fun clearCompleted() {
        downloads.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
    }

    fun cleanup() { scope.cancel() }
}

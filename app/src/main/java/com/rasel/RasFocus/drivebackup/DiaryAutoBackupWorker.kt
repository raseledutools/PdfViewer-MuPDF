package com.rasel.RasFocus.drivebackup

/**
 * DiaryAutoBackupWorker — runs every 3 hours via WorkManager.
 * Exports all diary entries as PDF + JSON and uploads to Drive.
 * Also backs up app settings JSON.
 */

import android.content.Context
import androidx.work.*
import com.rasel.RasFocus.selfcontrol.study_tools.DiaryDatabase
import com.rasel.RasFocus.selfcontrol.study_tools.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DiaryAutoBackupWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext

            // 1. Settings backup
            DriveBackupManager.syncNow(context)

            // 2. Export diary entries to JSON
            val dao     = DiaryDatabase.getDatabase(context).diaryDao()
            val entries = dao.getAllEntriesOnce()

            // JSON export
            val jsonFile = exportDiaryJson(context, entries)
            if (jsonFile != null) {
                DriveBackupManager.uploadDiaryJson(context, jsonFile)
                jsonFile.delete()
            }

            // PDF export (text-based, no external lib needed)
            val pdfFile = exportDiaryPdf(context, entries)
            if (pdfFile != null) {
                DriveBackupManager.uploadDiaryAllEntriesPdf(context, pdfFile)
                pdfFile.delete()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun exportDiaryJson(context: Context, entries: List<DiaryEntry>): File? {
        return try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id",      e.id)
                    put("title",   e.title)
                    put("body",    e.body)
                    put("date",    e.date)
                    put("mood",    e.mood)
                    put("folder",  e.folder)
                    put("tags",    e.tags.joinToString(","))
                    put("locked",  e.isLocked)
                })
            }
            val root = JSONObject().apply {
                put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()))
                put("entry_count", entries.size)
                put("entries", arr)
            }
            val file = File(context.cacheDir, "RasFocus_Diary_Backup.json")
            file.writeText(root.toString(2))
            file
        } catch (_: Exception) { null }
    }

    private fun exportDiaryPdf(context: Context, entries: List<DiaryEntry>): File? {
        return try {
            val doc  = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint().apply {
                textSize = 14f
                color    = android.graphics.Color.BLACK
                isAntiAlias = true
            }
            val titlePaint = android.graphics.Paint().apply {
                textSize    = 18f
                color       = 0xFFE91E8C.toInt()   // WDPink
                isFakeBoldText = true
                isAntiAlias = true
            }
            val subPaint = android.graphics.Paint().apply {
                textSize = 11f
                color    = android.graphics.Color.GRAY
                isAntiAlias = true
            }

            val pageW = 595; val pageH = 842   // A4 in points (72dpi)
            val marginL = 50f; val marginR = pageW - 50f
            val maxLineW = (marginR - marginL).toInt()

            entries.forEachIndexed { entryIdx, entry ->
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, entryIdx + 1).create()
                val page     = doc.startPage(pageInfo)
                val canvas   = page.canvas
                var y = 70f

                // Date + folder
                canvas.drawText("${entry.date}  •  ${entry.folder}", marginL, y, subPaint)
                y += 24f

                // Title
                val titleText = entry.title.ifBlank { "Untitled" }
                canvas.drawText(titleText, marginL, y, titlePaint)
                y += 28f

                // Mood chip
                if (entry.mood.isNotBlank()) {
                    canvas.drawText("Mood: ${entry.mood}", marginL, y, subPaint)
                    y += 20f
                }

                // Divider
                canvas.drawLine(marginL, y, marginR, y, subPaint)
                y += 16f

                // Body — wrap lines
                val words = entry.body.split("\n")
                for (line in words) {
                    if (y > pageH - 60f) break   // page full
                    // Wrap long lines
                    var start = 0
                    while (start < line.length) {
                        val measured = paint.breakText(line, start, line.length, true, maxLineW.toFloat(), null)
                        canvas.drawText(line.substring(start, start + measured), marginL, y, paint)
                        y += 20f
                        start += measured
                        if (y > pageH - 60f) break
                    }
                }

                doc.finishPage(page)
            }

            // If no entries, create one blank page
            if (entries.isEmpty()) {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                val page     = doc.startPage(pageInfo)
                page.canvas.drawText("No diary entries yet.", 50f, 100f, paint)
                doc.finishPage(page)
            }

            val file = File(context.cacheDir, "RasFocus_Diary_AllEntries.pdf")
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
            file
        } catch (_: Exception) { null }
    }

    companion object {
        private const val WORK_NAME = "diary_auto_backup"

        /**
         * Schedule recurring backup every 3 hours.
         * Call once from Application.onCreate or after sign-in.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DiaryAutoBackupWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(15, TimeUnit.MINUTES)   // don't run immediately on sign-in
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // don't restart if already scheduled
                request
            )
        }

        /** Cancel the recurring backup (call on sign-out). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

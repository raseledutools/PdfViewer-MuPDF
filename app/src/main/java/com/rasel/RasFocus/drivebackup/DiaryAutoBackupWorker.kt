package com.rasel.RasFocus.drivebackup

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.work.*
import com.rasel.RasFocus.selfcontrol.study_tools.DiaryDatabase
import com.rasel.RasFocus.selfcontrol.study_tools.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DiaryAutoBackupWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            if (!DriveBackupManager.isAvailable(ctx)) return@withContext Result.success()

            val dao     = DiaryDatabase.getDatabase(ctx).diaryDao()
            val entries = dao.getAllEntriesOnce()

            // 1. Settings backup
            DriveBackupManager.syncNow(ctx)

            // 2. Diary JSON export → Drive
            val jsonFile = buildJsonFile(ctx, entries)
            if (jsonFile != null) {
                DriveBackupManager.uploadDiaryJson(ctx, jsonFile)
                jsonFile.delete()
            }

            // 3. Diary PDF export → Drive
            val pdfFile = buildPdfFile(ctx, entries)
            if (pdfFile != null) {
                DriveBackupManager.uploadDiaryPdf(ctx, pdfFile)
                pdfFile.delete()
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // ── JSON builder ──────────────────────────────────────────────────────────
    private fun buildJsonFile(ctx: Context, entries: List<DiaryEntry>): File? = try {
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
                put("timestamp", e.timestamp)
            })
        }
        val root = JSONObject().apply {
            put("exported_at",  SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()))
            put("entry_count",  entries.size)
            put("entries",      arr)
        }
        File(ctx.cacheDir, "RasFocus_Diary_Backup.json")
            .also { it.writeText(root.toString(2)) }
    } catch (_: Exception) { null }

    // ── PDF builder (android.graphics.pdf — no extra lib) ────────────────────
    private fun buildPdfFile(ctx: Context, entries: List<DiaryEntry>): File? = try {
        val doc    = PdfDocument()
        val W = 595; val H = 842   // A4 72dpi
        val mL = 50f; val mR = (W - 50).toFloat()

        val bodyPaint = Paint().apply { textSize = 13f; color = Color.parseColor("#37474F"); isAntiAlias = true }
        val titlePaint = Paint().apply { textSize = 17f; color = Color.parseColor("#E91E8C"); isFakeBoldText = true; isAntiAlias = true }
        val subPaint  = Paint().apply { textSize = 10f; color = Color.GRAY; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.parseColor("#BBDEFB"); strokeWidth = 1f }

        if (entries.isEmpty()) {
            val pi = PdfDocument.PageInfo.Builder(W, H, 1).create()
            val pg = doc.startPage(pi)
            pg.canvas.drawText("No diary entries yet.", mL, 100f, bodyPaint)
            doc.finishPage(pg)
        }

        entries.forEachIndexed { idx, entry ->
            val pi = PdfDocument.PageInfo.Builder(W, H, idx + 1).create()
            val pg = doc.startPage(pi)
            val cv = pg.canvas
            var y  = 60f

            // Ruled lines
            var ly = 60f
            while (ly < H) { cv.drawLine(mL, ly, mR, ly, linePaint); ly += 24f }
            // Red margin
            val marginPaint = Paint().apply { color = Color.parseColor("#EF9A9A"); strokeWidth = 1.5f }
            cv.drawLine(56f, 0f, 56f, H.toFloat(), marginPaint)

            cv.drawText("${entry.date}  ·  ${entry.folder}", mL, y, subPaint); y += 22f
            cv.drawText(entry.title.ifBlank { "Untitled" }, mL, y, titlePaint); y += 26f
            if (entry.mood.isNotBlank()) { cv.drawText("Mood: ${entry.mood}", mL, y, subPaint); y += 18f }
            cv.drawLine(mL, y, mR, y, subPaint); y += 14f

            entry.body.split("\n").forEach { line ->
                if (y > H - 50f) return@forEach
                var s = 0
                while (s < line.length && y < H - 50f) {
                    val n = bodyPaint.breakText(line, s, line.length, true, (mR - mL - 56f), null)
                    cv.drawText(line.substring(s, s + n), 60f, y, bodyPaint)
                    y += 20f; s += n
                }
            }
            doc.finishPage(pg)
        }

        File(ctx.cacheDir, "RasFocus_Diary_AllEntries.pdf")
            .also { it.outputStream().use { os -> doc.writeTo(os) }; doc.close() }
    } catch (_: Exception) { null }

    companion object {
        private const val WORK_NAME = "rasfocus_diary_auto_backup"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DiaryAutoBackupWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        // One-shot manual trigger (for "Backup Now" button)
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<DiaryAutoBackupWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                ).build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

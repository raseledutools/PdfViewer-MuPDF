package com.rasel.pdfviewer.combo.selfcontrol.study_tools

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object DiaryPdfExporter {

    // A4 dimensions at 72 dpi (standard PDF)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    // ── COLORS ─────────────────────────────────────────────
    private val C_COVER_BG1  = Color.parseColor("#1A1D24")
    private val C_COVER_BG2  = Color.parseColor("#2D3250")
    private val C_ACCENT     = Color.parseColor("#D32F2F")
    private val C_GOLD       = Color.parseColor("#C9A84C")
    private val C_WHITE      = Color.WHITE
    private val C_LIGHT_GRAY = Color.parseColor("#EEEEEE")
    private val C_TEXT_DARK  = Color.parseColor("#1A237E")
    private val C_TEXT_BODY  = Color.parseColor("#212121")
    private val C_LINE       = Color.parseColor("#D32F2F")
    private val C_DATE_BG    = Color.parseColor("#EDE7F6")
    private val C_DATE_TEXT  = Color.parseColor("#4A148C")

    // ── PUBLIC API ──────────────────────────────────────────
    fun exportSingleEntry(context: Context, entry: DiaryEntry): File? =
        exportEntries(context, listOf(entry), "diary_entry")

    fun exportAllEntries(context: Context, entries: List<DiaryEntry>): File? =
        exportEntries(context, entries, "diary_export")

    // ── MAIN EXPORT ─────────────────────────────────────────
    private fun exportEntries(
        context: Context,
        entries: List<DiaryEntry>,
        fileNamePrefix: String
    ): File? {
        if (entries.isEmpty()) return null
        val document = PdfDocument()

        // Cover page
        drawCoverPage(document, entries)

        // Table of Contents (if multiple entries)
        if (entries.size > 1) drawTableOfContents(document, entries)

        // Entry pages
        for (entry in entries) drawEntryPages(document, entry)

        // Save
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DiaryExports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")
        return try {
            FileOutputStream(outputFile).use { fos -> document.writeTo(fos) }
            outputFile
        } catch (e: IOException) {
            e.printStackTrace(); null
        } finally {
            document.close()
        }
    }

    // ── COVER PAGE ──────────────────────────────────────────
    private fun drawCoverPage(document: PdfDocument, entries: List<DiaryEntry>) {
        val page = document.startPage(newPageInfo(document))
        val canvas = page.canvas

        // Background gradient (manual)
        val bgPaint = Paint()
        val shader = LinearGradient(
            0f, 0f, 0f, PAGE_HEIGHT.toFloat(),
            intArrayOf(C_COVER_BG1, C_COVER_BG2, Color.parseColor("#4A0E0E")),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        // Top decorative bar
        val barPaint = Paint().apply { color = C_ACCENT; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, barPaint)
        canvas.drawRect(0f, PAGE_HEIGHT - 8f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), barPaint)

        // Side gold lines
        val goldPaint = Paint().apply { color = C_GOLD; strokeWidth = 2f; style = Paint.Style.STROKE }
        canvas.drawLine(30f, 30f, 30f, PAGE_HEIGHT - 30f, goldPaint)
        canvas.drawLine(PAGE_WIDTH - 30f, 30f, PAGE_WIDTH - 30f, PAGE_HEIGHT - 30f, goldPaint)
        canvas.drawRect(30f, 30f, PAGE_WIDTH - 30f, PAGE_HEIGHT - 30f, goldPaint)

        // Decorative corner ornaments
        drawCornerOrnament(canvas, 30f, 30f, goldPaint)
        drawCornerOrnament(canvas, PAGE_WIDTH - 30f, 30f, goldPaint)
        drawCornerOrnament(canvas, 30f, PAGE_HEIGHT - 30f, goldPaint)
        drawCornerOrnament(canvas, PAGE_WIDTH - 30f, PAGE_HEIGHT - 30f, goldPaint)

        // Book icon area (rectangle representing a book)
        val bookPaint = Paint().apply { color = Color.parseColor("#C62828"); style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(200f, 200f, 395f, 310f), 8f, 8f, bookPaint)
        val spinePaint = Paint().apply { color = Color.parseColor("#B71C1C"); style = Paint.Style.FILL }
        canvas.drawRect(200f, 200f, 220f, 310f, spinePaint)
        val linesPaint = Paint().apply { color = Color.WHITE; strokeWidth = 1.5f; alpha = 120 }
        for (i in 0 until 5) canvas.drawLine(232f, 225f + i * 16f, 385f, 225f + i * 16f, linesPaint)
        val bookmarkPaint = Paint().apply { color = C_GOLD; style = Paint.Style.FILL }
        val bookmark = Path().apply {
            moveTo(350f, 200f); lineTo(375f, 200f); lineTo(375f, 245f)
            lineTo(362f, 235f); lineTo(350f, 245f); close()
        }
        canvas.drawPath(bookmark, bookmarkPaint)

        // "MY DIARY" title
        val titlePaint = Paint().apply {
            color = C_WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            letterSpacing = 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MY DIARY", PAGE_WIDTH / 2f, 370f, titlePaint)

        // Subtitle line
        val subLinePaint = Paint().apply { color = C_GOLD; strokeWidth = 1.5f }
        canvas.drawLine(150f, 385f, 445f, 385f, subLinePaint)

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Personal Journal & Reflections", PAGE_WIDTH / 2f, 408f, subtitlePaint)

        // Year
        val yearPaint = Paint().apply {
            color = C_GOLD; textSize = 22f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        canvas.drawText(year, PAGE_WIDTH / 2f, 450f, yearPaint)

        // Info box
        val infoBoxPaint = Paint().apply {
            color = Color.parseColor("#22FFFFFF"); style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(70f, 580f, PAGE_WIDTH - 70f, 730f), 12f, 12f, infoBoxPaint)
        val infoBorderPaint = Paint().apply {
            color = C_GOLD; strokeWidth = 1f; style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(RectF(70f, 580f, PAGE_WIDTH - 70f, 730f), 12f, 12f, infoBorderPaint)

        val infoLabelPaint = Paint().apply {
            color = Color.parseColor("#AAAAAA"); textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val infoValuePaint = Paint().apply {
            color = C_WHITE; textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TOTAL ENTRIES", PAGE_WIDTH / 2f, 610f, infoLabelPaint)
        canvas.drawText("${entries.size}", PAGE_WIDTH / 2f, 635f, infoValuePaint)

        // Date range
        val dates = entries.mapNotNull { e ->
            runCatching {
                SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).parse(e.date)
            }.getOrNull()
        }
        if (dates.isNotEmpty()) {
            val earliest = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(dates.minOrNull()!!)
            val latest = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(dates.maxOrNull()!!)
            canvas.drawText("DATE RANGE", PAGE_WIDTH / 2f, 665f, infoLabelPaint)
            canvas.drawText("$earliest  –  $latest", PAGE_WIDTH / 2f, 688f, infoValuePaint)
        }

        // Exported on
        val exportDate = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("EXPORTED ON", PAGE_WIDTH / 2f, 712f, infoLabelPaint)
        canvas.drawText(exportDate, PAGE_WIDTH / 2f, 728f, infoValuePaint)

        // Bottom text
        val footPaint = Paint().apply {
            color = Color.parseColor("#888888"); textSize = 10f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated by RasFocus · Private & Confidential", PAGE_WIDTH / 2f, PAGE_HEIGHT - 45f, footPaint)

        document.finishPage(page)
    }

    private fun drawCornerOrnament(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val size = 12f
        val signX = if (x < PAGE_WIDTH / 2) 1f else -1f
        val signY = if (y < PAGE_HEIGHT / 2) 1f else -1f
        canvas.drawLine(x, y, x + signX * size, y, paint)
        canvas.drawLine(x, y, x, y + signY * size, paint)
    }

    // ── TABLE OF CONTENTS ───────────────────────────────────
    private fun drawTableOfContents(document: PdfDocument, entries: List<DiaryEntry>) {
        val page = document.startPage(newPageInfo(document))
        val canvas = page.canvas
        drawPageBackground(canvas)
        drawPageHeader(canvas, "Table of Contents", null)

        val titlePaint = Paint().apply {
            color = C_TEXT_DARK; textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val datePaint = Paint().apply { color = Color.GRAY; textSize = 10f }
        val dotPaint = Paint().apply { color = Color.parseColor("#CCCCCC"); strokeWidth = 1f }

        var y = 130f
        val lineHeight = 28f

        entries.forEachIndexed { idx, entry ->
            if (y > PAGE_HEIGHT - MARGIN - 20) return@forEachIndexed
            val num = "${idx + 1}."
            val entryTitle = entry.title.ifBlank { "Untitled Entry" }
            val entryDate = entry.date.take(20).ifBlank { "No date" }

            // Number
            canvas.drawText(num, MARGIN, y, datePaint)
            // Title
            canvas.drawText(entryTitle.take(55), MARGIN + 28f, y, titlePaint)
            // Dots
            val textEnd = MARGIN + 28f + titlePaint.measureText(entryTitle.take(55))
            var dotX = textEnd + 6f
            while (dotX < PAGE_WIDTH - MARGIN - 50f) {
                canvas.drawPoint(dotX, y - 3f, dotPaint)
                dotX += 5f
            }
            // Date (right-aligned)
            val datePaintR = Paint().apply { color = Color.GRAY; textSize = 10f; textAlign = Paint.Align.RIGHT }
            canvas.drawText(entryDate, PAGE_WIDTH - MARGIN, y, datePaintR)

            y += lineHeight
        }

        drawPageFooter(canvas, "Contents")
        document.finishPage(page)
    }

    // ── ENTRY PAGES ─────────────────────────────────────────
    private fun drawEntryPages(document: PdfDocument, entry: DiaryEntry) {
        val wrappedLines = wrapText(entry.body.ifBlank { "(No content)" }, buildBodyPaint(), CONTENT_WIDTH)

        var lineIndex = 0
        var isFirst = true

        while (lineIndex < wrappedLines.size || isFirst) {
            val page = document.startPage(newPageInfo(document))
            val canvas = page.canvas
            drawPageBackground(canvas)

            var y = MARGIN + 10f

            if (isFirst) {
                // Date badge
                val dateBgPaint = Paint().apply { color = C_DATE_BG; style = Paint.Style.FILL }
                canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 200f, y + 24f), 12f, 12f, dateBgPaint)
                val dateTxtPaint = Paint().apply {
                    color = C_DATE_TEXT; textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                canvas.drawText("📅  ${entry.date.ifBlank { "No date" }}", MARGIN + 10f, y + 16f, dateTxtPaint)
                y += 36f

                // Title
                val titlePaint = Paint().apply {
                    color = C_TEXT_DARK; textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                canvas.drawText(entry.title.ifBlank { "Untitled Entry" }, MARGIN, y + 26f, titlePaint)
                y += 40f

                // Red accent line under title
                val accentPaint = Paint().apply { color = C_LINE; strokeWidth = 3f }
                canvas.drawLine(MARGIN, y, MARGIN + 120f, y, accentPaint)
                y += 14f

                // Meta row: folder | mood | tags
                val metaPaint = Paint().apply { color = Color.GRAY; textSize = 10f }
                var metaX = MARGIN
                if (entry.folder.isNotBlank()) {
                    drawMetaBadge(canvas, "📁 ${entry.folder}", metaX, y + 10f)
                    metaX += 80f
                }
                if (entry.mood.isNotBlank()) {
                    drawMetaBadge(canvas, entry.mood, metaX, y + 10f)
                    metaX += 90f
                }
                entry.tags.take(3).forEach { tag ->
                    drawMetaBadge(canvas, "#$tag", metaX, y + 10f)
                    metaX += metaPaint.measureText("#$tag") + 36f
                }
                y += 30f

                // Separator line
                val sepPaint = Paint().apply {
                    color = C_LIGHT_GRAY; strokeWidth = 1f; style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                }
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, sepPaint)
                y += 18f

                isFirst = false
            } else {
                y = MARGIN + 10f
                // Continuation header
                val contPaint = Paint().apply { color = Color.parseColor("#AAAAAA"); textSize = 9f }
                canvas.drawText("${entry.title.ifBlank { "Entry" }}  (continued)", MARGIN, y + 10f, contPaint)
                y += 24f
            }

            val lineHeight = 20f
            val maxY = PAGE_HEIGHT - MARGIN - 30f

            while (lineIndex < wrappedLines.size && y + lineHeight <= maxY) {
                val line = wrappedLines[lineIndex]
                // Horizontal ruled lines (faint)
                val ruledPaint = Paint().apply { color = Color.parseColor("#F0F0F0"); strokeWidth = 0.5f }
                canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, ruledPaint)
                canvas.drawText(line, MARGIN, y, buildBodyPaint())
                y += lineHeight
                lineIndex++
            }

            drawPageFooter(canvas, entry.date.take(15))
            document.finishPage(page)
        }
    }

    // ── HELPER DRAW FUNCTIONS ───────────────────────────────

    private fun drawPageBackground(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        // Subtle left margin line (like a real diary)
        val marginLinePaint = Paint().apply { color = Color.parseColor("#FFD0D0"); strokeWidth = 1f }
        canvas.drawLine(MARGIN - 15f, 0f, MARGIN - 15f, PAGE_HEIGHT.toFloat(), marginLinePaint)
    }

    private fun drawPageHeader(canvas: Canvas, title: String, entryDate: String?) {
        val headerBgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, PAGE_WIDTH.toFloat(), 0f,
                intArrayOf(C_ACCENT, Color.parseColor("#B71C1C")),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 60f, headerBgPaint)

        val hTitlePaint = Paint().apply {
            color = C_WHITE; textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(title, MARGIN, 38f, hTitlePaint)

        if (entryDate != null) {
            val hDatePaint = Paint().apply {
                color = Color.WHITE; textSize = 10f; textAlign = Paint.Align.RIGHT
                alpha = 200
            }
            canvas.drawText(entryDate, PAGE_WIDTH - MARGIN, 38f, hDatePaint)
        }
    }

    private fun drawPageFooter(canvas: Canvas, leftText: String) {
        val footLinePaint = Paint().apply { color = C_LIGHT_GRAY; strokeWidth = 1f }
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 30f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 30f, footLinePaint)

        val footPaint = Paint().apply { color = Color.GRAY; textSize = 9f }
        canvas.drawText(leftText, MARGIN, PAGE_HEIGHT - 14f, footPaint)

        val footRightPaint = Paint().apply { color = Color.GRAY; textSize = 9f; textAlign = Paint.Align.RIGHT }
        canvas.drawText("RasFocus · My Diary", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 14f, footRightPaint)
    }

    private fun drawMetaBadge(canvas: Canvas, text: String, x: Float, y: Float) {
        val paint = Paint().apply { textSize = 9f; color = Color.parseColor("#5C6BC0") }
        val w = paint.measureText(text) + 16f
        val bgPaint = Paint().apply { color = Color.parseColor("#E8EAF6"); style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(x, y - 12f, x + w, y + 4f), 8f, 8f, bgPaint)
        canvas.drawText(text, x + 8f, y, paint)
    }

    private fun buildBodyPaint() = Paint().apply {
        color = C_TEXT_BODY; textSize = 12.5f
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }

    private fun newPageInfo(document: PdfDocument) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()

    // ── TEXT WRAP ───────────────────────────────────────────
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) { result.add(""); continue }
            val words = paragraph.split(" ")
            var line = StringBuilder()
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) <= maxWidth) {
                    line = StringBuilder(test)
                } else {
                    if (line.isNotEmpty()) result.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            if (line.isNotEmpty()) result.add(line.toString())
        }
        return result
    }

    fun getShareIntent(context: Context, file: File): android.content.Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

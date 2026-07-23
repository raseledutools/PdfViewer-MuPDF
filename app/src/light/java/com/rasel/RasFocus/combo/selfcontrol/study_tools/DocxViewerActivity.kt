package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

// ─────────────────────────────────────────────────────────────────────────────
// DocxViewerActivity — converts .docx → PDF in-memory → opens PdfViewerActivity
// Zero external dependencies: ZIP + XmlPullParser + android.graphics.pdf.PdfDocument
// ─────────────────────────────────────────────────────────────────────────────

class DocxViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null ->
                intent.data
            intent?.hasExtra("docx_uri") == true ->
                Uri.parse(intent.getStringExtra("docx_uri"))
            else -> null
        }

        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }

        val fileName = uri?.let { getFileName(it) } ?: "Document"

        setContent {
            val converting = remember { mutableStateOf(true) }
            val error      = remember { mutableStateOf("") }

            LaunchedEffect(uri) {
                if (uri == null) {
                    error.value = "ফাইল পাওয়া যায়নি"
                    converting.value = false
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    try {
                        val paragraphs = parseDocx(uri)
                        val pdfFile    = writePdf(fileName, paragraphs)
                        val pdfUri = FileProvider.getUriForFile(
                            this@DocxViewerActivity,
                            "${packageName}.fileprovider",
                            pdfFile
                        )
                        withContext(Dispatchers.Main) {
                            val intent = android.content.Intent(
                                this@DocxViewerActivity,
                                com.rasel.RasFocus.combo.selfcontrol.study_tools.PdfViewerActivity::class.java
                            ).apply {
                                action = android.content.Intent.ACTION_VIEW
                                setDataAndType(pdfUri, "application/pdf")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                putExtra("source_name", fileName)
                            }
                            startActivity(intent)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            error.value    = "Convert ব্যর্থ: ${e.message}"
                            converting.value = false
                        }
                    }
                }
            }

            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().background(ComposeColor(0xFF111111)),
                    contentAlignment = Alignment.Center
                ) {
                    if (converting.value) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ComposeColor(0xFF4FACFE))
                            Spacer(Modifier.height(12.dp))
                            Text("Word → PDF convert করছি…", color = ComposeColor(0xFFF5F5F5), fontSize = 14.sp)
                            Text(fileName, color = ComposeColor(0xFF777777), fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    } else if (error.value.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)) {
                            Text("⚠️", fontSize = 36.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(error.value, color = ComposeColor(0xFFFF5C5C), fontSize = 13.sp)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { finish() }) { Text("← ফিরে যান") }
                        }
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    }

    // ── DOCX Parser ────────────────────────────────────────────────────────────

    private data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val fontSize: Int = 24,   // half-points
        val colorHex: String = ""
    )
    private data class Para(val runs: List<Run>, val style: String = "Normal")

    private fun parseDocx(uri: Uri): List<Para> {
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return emptyList()
        var docXml: ByteArray? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") { docXml = zip.readBytes(); break }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        return docXml?.let { parseXml(it) } ?: emptyList()
    }

    private fun parseXml(xml: ByteArray): List<Para> {
        val paragraphs = mutableListOf<Para>()
        val xpp = newParser(xml.inputStream())
        var event = xpp.eventType
        var paraStyle = "Normal"
        val runs = mutableListOf<Run>()
        var bold = false; var italic = false; var sz = 24; var color = ""
        val runText = StringBuilder()
        var inRun = false

        fun flushRun() {
            val t = runText.toString(); if (t.isNotEmpty()) runs.add(Run(t, bold, italic, sz, color))
            runText.clear()
        }
        fun flushPara() {
            flushRun()
            if (runs.isNotEmpty()) paragraphs.add(Para(runs.toList(), paraStyle))
            runs.clear(); paraStyle = "Normal"
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (xpp.name) {
                    "w:pStyle" -> paraStyle = xpp.getAttributeValue(null, "w:val") ?: "Normal"
                    "w:r"  -> { inRun = true; bold = false; italic = false; sz = 24; color = "" }
                    "w:b"  -> if (inRun) bold   = true
                    "w:i"  -> if (inRun) italic  = true
                    "w:sz" -> if (inRun) sz = xpp.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 24
                    "w:color" -> if (inRun) color = xpp.getAttributeValue(null, "w:val") ?: ""
                    "w:tab"   -> if (inRun) runText.append("    ")
                    "w:br"    -> if (inRun) runText.append("\n")
                }
                XmlPullParser.TEXT -> if (inRun) runText.append(xpp.text ?: "")
                XmlPullParser.END_TAG -> when (xpp.name) {
                    "w:r" -> { flushRun(); inRun = false }
                    "w:p" -> flushPara()
                }
            }
            event = xpp.next()
        }
        return paragraphs
    }

    // ── PDF Writer ─────────────────────────────────────────────────────────────

    private fun writePdf(name: String, paragraphs: List<Para>): File {
        val pageW = 595; val pageH = 842   // A4 points
        val marginL = 56f; val marginR = 56f; val marginT = 56f; val marginB = 56f
        val contentW = pageW - marginL - marginR

        val pdf  = PdfDocument()
        var pageNum  = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page     = pdf.startPage(pageInfo)
        var canvas   = page.canvas
        var y        = marginT

        fun newPage() {
            pdf.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            page     = pdf.startPage(pageInfo)
            canvas   = page.canvas
            y        = marginT
        }

        fun drawText(text: String, paint: Paint): Float {
            // Word-wrap and return height consumed
            val words  = text.split(" ")
            val line   = StringBuilder()
            var height = 0f
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > contentW) {
                    if (line.isNotEmpty()) {
                        canvas.drawText(line.toString(), marginL, y + height + paint.textSize, paint)
                        height += paint.textSize * 1.4f
                    }
                    line.clear(); line.append(word)
                } else {
                    line.clear(); line.append(test)
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line.toString(), marginL, y + height + paint.textSize, paint)
                height += paint.textSize * 1.4f
            }
            return height
        }

        // White background
        val bgPaint = Paint().apply { color = Color.WHITE }

        for (para in paragraphs) {
            val headingLevel = when {
                para.style.startsWith("Heading1") || para.style == "Title"    -> 1
                para.style.startsWith("Heading2") || para.style == "Subtitle" -> 2
                para.style.startsWith("Heading3") -> 3
                para.style.startsWith("Heading4") -> 4
                else -> 0
            }

            val paraFontSizePt = when (headingLevel) {
                1 -> 22f; 2 -> 18f; 3 -> 15f; 4 -> 13f; else -> 11f
            }
            val paraTopSpace  = when (headingLevel) { 1 -> 14f; 2 -> 10f; else -> 4f }

            // Ensure enough space for at least one line
            val lineH = paraFontSizePt * 1.5f
            if (y + paraTopSpace + lineH > pageH - marginB) newPage()
            y += paraTopSpace

            for (run in para.runs) {
                val rSizePt = if (run.fontSize > 0) (run.fontSize / 2f).coerceIn(7f, 48f) else paraFontSizePt
                val finalSize = if (headingLevel > 0) paraFontSizePt else rSizePt
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = finalSize
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        when {
                            run.bold && run.italic -> Typeface.BOLD_ITALIC
                            run.bold || headingLevel in 1..2 -> Typeface.BOLD
                            run.italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                    )
                    color = if (run.colorHex.length == 6) {
                        try { Color.parseColor("#${run.colorHex}") } catch (_: Exception) { Color.BLACK }
                    } else Color.BLACK
                }
                val text = run.text
                if (text.isBlank()) { y += finalSize * 0.5f; continue }

                // Check page overflow before each run
                if (y + finalSize * 1.5f > pageH - marginB) newPage()

                canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)
                val consumed = drawText(text, paint)
                y += consumed
                if (y > pageH - marginB) newPage()
            }

            if (para.runs.isEmpty()) y += paraFontSizePt * 0.8f
        }

        pdf.finishPage(page)

        val cacheDir = File(cacheDir, "converted_pdfs")
        cacheDir.mkdirs()
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".docx")
        val file = File(cacheDir, "${safe}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        val f = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        return f.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}

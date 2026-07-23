package com.rasel.RasFocus.selfcontrol.study_tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// PptxViewerActivity — converts .pptx → PDF (one page per slide) → PdfViewerActivity
// Zero external dependencies: ZIP + XmlPullParser + android.graphics.pdf.PdfDocument
// ─────────────────────────────────────────────────────────────────────────────

class PptxViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null ->
                intent.data
            intent?.hasExtra("pptx_uri") == true ->
                Uri.parse(intent.getStringExtra("pptx_uri"))
            else -> null
        }

        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }

        val fileName = uri?.let { getFileName(it) } ?: "Presentation"

        setContent {
            val converting = remember { mutableStateOf(true) }
            val error      = remember { mutableStateOf("") }

            LaunchedEffect(uri) {
                if (uri == null) {
                    error.value = "ফাইল পাওয়া যায়নি"; converting.value = false; return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    try {
                        val slides  = parsePptx(uri)
                        val pdfFile = renderToPdf(fileName, slides)
                        val pdfUri  = FileProvider.getUriForFile(
                            this@PptxViewerActivity,
                            "${packageName}.fileprovider",
                            pdfFile
                        )
                        withContext(Dispatchers.Main) {
                            val intent = android.content.Intent(
                                this@PptxViewerActivity,
                                PdfViewerActivity::class.java
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
                            Text("Presentation → PDF convert করছি…",
                                color = ComposeColor(0xFFF5F5F5), fontSize = 14.sp)
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
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Presentation"
    }

    // ── PPTX Parser ────────────────────────────────────────────────────────────

    private data class TextShape(
        val text: String, val bold: Boolean, val italic: Boolean,
        val fontSize: Float, val color: Int,
        val x: Float, val y: Float, val w: Float, val h: Float
    )
    private data class Slide(
        val shapes: List<TextShape>,
        val bgColor: Int = Color.WHITE
    )

    private fun parsePptx(uri: Uri): List<Slide> {
        val zipBytes = contentResolver.openInputStream(uri)?.readBytes() ?: return emptyList()
        val entries  = mutableMapOf<String, ByteArray>()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        // Collect slides by numbered name (slide1.xml, slide2.xml …)
        val slideKeys = entries.keys
            .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedWith(compareBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 })

        return slideKeys.map { parseSlide(entries[it]!!) }
    }

    private fun parseSlide(xml: ByteArray): Slide {
        val shapes = mutableListOf<TextShape>()
        var bgColor = Color.WHITE
        val xpp = newParser(xml.inputStream())
        var event = xpp.eventType
        var curX = 0f; var curY = 0f; var curW = 1f; var curH = 1f
        val curText = StringBuilder()
        var bold = false; var italic = false; var size = 24f; var color = Color.BLACK
        var inBody = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (xpp.name) {
                    "a:srgbClr" -> {
                        val hex = xpp.getAttributeValue(null, "val") ?: ""
                        if (hex.length == 6) try { bgColor = Color.parseColor("#$hex") } catch (_: Exception) {}
                    }
                    "a:off" -> {
                        curX = (xpp.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L).toFloat() / 9_144_000f
                        curY = (xpp.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L).toFloat() / 6_858_000f
                    }
                    "a:ext" -> {
                        curW = (xpp.getAttributeValue(null, "cx")?.toLongOrNull() ?: 9_144_000L).toFloat() / 9_144_000f
                        curH = (xpp.getAttributeValue(null, "cy")?.toLongOrNull() ?: 6_858_000L).toFloat() / 6_858_000f
                    }
                    "p:txBody", "a:txBody" -> { inBody = true; curText.clear() }
                    "a:rPr" -> {
                        bold   = xpp.getAttributeValue(null, "b") == "1"
                        italic = xpp.getAttributeValue(null, "i") == "1"
                        val sz = xpp.getAttributeValue(null, "sz")?.toIntOrNull()
                        if (sz != null) size = sz / 100f
                    }
                    "a:p" -> if (inBody) curText.append("")
                }
                XmlPullParser.TEXT -> if (inBody) curText.append(xpp.text ?: "")
                XmlPullParser.END_TAG -> when (xpp.name) {
                    "a:p"  -> if (inBody) curText.append("\n")
                    "p:sp" -> {
                        val txt = curText.toString().trim()
                        if (txt.isNotEmpty()) {
                            shapes.add(TextShape(txt, bold, italic, size.coerceIn(8f, 96f),
                                color, curX, curY, curW, curH))
                        }
                        curText.clear(); inBody = false; size = 24f; bold = false; italic = false; color = Color.BLACK
                    }
                }
            }
            event = xpp.next()
        }
        return Slide(shapes, bgColor)
    }

    // ── PDF Renderer ───────────────────────────────────────────────────────────
    // Each slide → one PDF page (landscape 842×595 = A4 landscape)

    private fun renderToPdf(name: String, slides: List<Slide>): File {
        val pageW = 842; val pageH = 595   // A4 landscape
        val pdf   = PdfDocument()

        slides.forEachIndexed { i, slide ->
            val info   = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
            val page   = pdf.startPage(info)
            val canvas = page.canvas

            // Background
            canvas.drawColor(slide.bgColor)

            slide.shapes.forEach { shape ->
                val px = shape.x * pageW
                val py = shape.y * pageH
                val pw = shape.w * pageW
                val ph = shape.h * pageH

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize  = shape.fontSize * (pageW / 960f)
                    color     = shape.color
                    typeface  = Typeface.create(Typeface.DEFAULT,
                        when {
                            shape.bold && shape.italic -> Typeface.BOLD_ITALIC
                            shape.bold   -> Typeface.BOLD
                            shape.italic -> Typeface.ITALIC
                            else         -> Typeface.NORMAL
                        })
                }

                val lines = wrapText(shape.text, paint, pw)
                val lineH = paint.textSize * 1.3f
                lines.forEachIndexed { li, line ->
                    val lx = px + 6f
                    val ly = py + 6f + (li + 1) * lineH
                    if (ly < py + ph) canvas.drawText(line, lx, ly, paint)
                }
            }

            pdf.finishPage(page)
        }

        val cacheDir = File(cacheDir, "converted_pdfs")
        cacheDir.mkdirs()
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".pptx")
        val file = File(cacheDir, "${safe}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            val words = paragraph.split(" ")
            var line  = StringBuilder()
            words.forEach { word ->
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

    private fun newParser(stream: InputStream): XmlPullParser {
        val f = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        return f.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}

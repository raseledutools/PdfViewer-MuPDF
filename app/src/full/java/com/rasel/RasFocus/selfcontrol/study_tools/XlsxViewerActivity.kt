package com.rasel.RasFocus.selfcontrol.study_tools

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
// XlsxViewerActivity — converts .xlsx → PDF → PdfViewerActivity
// Uses Android's built-in PdfDocument (no external libs).
// Parses the xlsx ZIP: xl/sharedStrings.xml + xl/worksheets/sheet*.xml
// ─────────────────────────────────────────────────────────────────────────────

class XlsxViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null ->
                intent.data
            intent?.hasExtra("xlsx_uri") == true ->
                Uri.parse(intent.getStringExtra("xlsx_uri"))
            else -> null
        }

        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }

        val fileName = uri?.let { getFileName(it) } ?: "Spreadsheet"

        setContent {
            val converting = remember { mutableStateOf(true) }
            val error      = remember { mutableStateOf("") }

            LaunchedEffect(uri) {
                if (uri == null) {
                    error.value = "ফাইল পাওয়া যায়নি"; converting.value = false; return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    try {
                        val sheets  = parseXlsx(uri)
                        val pdfFile = sheetsToPdf(fileName, sheets)
                        val pdfUri  = FileProvider.getUriForFile(
                            this@XlsxViewerActivity,
                            "${packageName}.fileprovider",
                            pdfFile
                        )
                        withContext(Dispatchers.Main) {
                            val intent = android.content.Intent(
                                this@XlsxViewerActivity,
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
                            CircularProgressIndicator(color = ComposeColor(0xFF3FD68F))
                            Spacer(Modifier.height(12.dp))
                            Text("Spreadsheet → PDF convert করছি…",
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
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Spreadsheet"
    }

    // ── Data ────────────────────────────────────────────────────────────────
    private data class Sheet(val name: String, val rows: List<List<String>>)

    // ── XLSX Parser ─────────────────────────────────────────────────────────
    // xlsx = ZIP containing:
    //   xl/sharedStrings.xml  — string table
    //   xl/worksheets/sheet1.xml (sheet2.xml …) — cell data
    //   xl/workbook.xml — sheet names & order

    private fun parseXlsx(uri: Uri): List<Sheet> {
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return emptyList()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
                zip.closeEntry(); e = zip.nextEntry
            }
        }

        // 1. Shared strings
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])

        // 2. Sheet names from workbook
        val sheetNames = parseWorkbookSheetNames(entries["xl/workbook.xml"])

        // 3. Find worksheet files in order
        val worksheetKeys = entries.keys
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedWith(compareBy { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 0 })

        return worksheetKeys.mapIndexed { idx, key ->
            val rows = parseWorksheet(entries[key]!!, sharedStrings)
            val name = sheetNames.getOrElse(idx) { "Sheet ${idx + 1}" }
            Sheet(name, rows)
        }
    }

    private fun parseSharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val strings = mutableListOf<String>()
        val xpp = newParser(xml.inputStream())
        var event = xpp.eventType
        val cur = StringBuilder()
        var inT = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (xpp.name == "si") cur.clear()
                    if (xpp.name == "t") inT = true
                }
                XmlPullParser.TEXT -> if (inT) cur.append(xpp.text ?: "")
                XmlPullParser.END_TAG -> {
                    if (xpp.name == "t") inT = false
                    if (xpp.name == "si") strings.add(cur.toString())
                }
            }
            event = xpp.next()
        }
        return strings
    }

    private fun parseWorkbookSheetNames(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val names = mutableListOf<String>()
        val xpp = newParser(xml.inputStream())
        var event = xpp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xpp.name == "sheet") {
                val n = xpp.getAttributeValue(null, "name") ?: ""
                if (n.isNotEmpty()) names.add(n)
            }
            event = xpp.next()
        }
        return names
    }

    private fun parseWorksheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        // rows keyed by 1-based row index
        val rowMap = mutableMapOf<Int, MutableMap<Int, String>>()
        val xpp = newParser(xml.inputStream())
        var event = xpp.eventType
        var curRow = 0; var curCol = 0; var cellType = ""
        var inV = false; var inIs = false
        val cellVal = StringBuilder()

        // Parse column letter(s) from cell ref like "B3" → col index 2
        fun colIndex(ref: String): Int {
            val letters = ref.takeWhile { it.isLetter() }
            return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (xpp.name) {
                    "row" -> curRow = xpp.getAttributeValue(null, "r")?.toIntOrNull() ?: (curRow + 1)
                    "c"   -> {
                        val ref = xpp.getAttributeValue(null, "r") ?: ""
                        curCol = if (ref.isNotEmpty()) colIndex(ref) else 0
                        cellType = xpp.getAttributeValue(null, "t") ?: ""
                        cellVal.clear()
                    }
                    "v"   -> { inV = true }
                    "is", "t" -> if (!inV) inIs = true
                }
                XmlPullParser.TEXT -> {
                    if (inV || inIs) cellVal.append(xpp.text ?: "")
                }
                XmlPullParser.END_TAG -> when (xpp.name) {
                    "v"  -> { inV = false }
                    "is", "t" -> inIs = false
                    "c"  -> {
                        val raw = cellVal.toString().trim()
                        val value = when (cellType) {
                            "s" -> sharedStrings.getOrElse(raw.toIntOrNull() ?: -1) { raw }
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "str", "inlineStr" -> raw
                            else -> raw  // number / date / formula result
                        }
                        if (value.isNotEmpty()) {
                            rowMap.getOrPut(curRow) { mutableMapOf() }[curCol] = value
                        }
                    }
                }
            }
            event = xpp.next()
        }

        if (rowMap.isEmpty()) return emptyList()
        val maxRow = rowMap.keys.max()
        val maxCol = rowMap.values.flatMap { it.keys }.maxOrNull() ?: 0
        return (1..maxRow).map { r ->
            val cols = rowMap[r] ?: emptyMap()
            (0..maxCol).map { c -> cols[c] ?: "" }
        }
    }

    // ── PDF Writer ─────────────────────────────────────────────────────────
    private fun sheetsToPdf(name: String, sheets: List<Sheet>): File {
        val pageW = 842; val pageH = 595   // A4 landscape for wide tables
        val marginL = 36f; val marginT = 48f; val marginB = 40f
        val contentW = pageW - marginL * 2

        val pdf = PdfDocument()
        var pageNum = 0

        var currentPage: PdfDocument.Page? = null
        fun newPage(): android.graphics.Canvas {
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            val p = pdf.startPage(info)
            currentPage = p
            p.canvas.drawColor(Color.WHITE)
            return p.canvas
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f; color = Color.BLACK
        }
        val gridPaint = Paint().apply {
            color = Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE
        }
        val sheetTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(30, 90, 200)
        }

        for (sheet in sheets) {
            if (sheet.rows.isEmpty()) continue

            val rows = sheet.rows
            val colCount = rows.maxOfOrNull { it.size } ?: 0
            if (colCount == 0) continue

            // Calculate column widths based on max content length
            val colWidths = FloatArray(colCount) { c ->
                val maxLen = rows.mapNotNull { row -> row.getOrNull(c)?.length }.maxOrNull() ?: 0
                (maxLen.coerceIn(4, 20) * 5.5f).coerceIn(30f, contentW / colCount.coerceAtLeast(1))
            }
            val totalW = colWidths.sum().coerceAtMost(contentW)
            val scaleFactor = if (colWidths.sum() > contentW) contentW / colWidths.sum() else 1f
            val scaledWidths = colWidths.map { it * scaleFactor }

            var canvas = newPage()
            var y = marginT

            // Sheet title
            canvas.drawText("📊 ${sheet.name}", marginL, y, sheetTitlePaint)
            y += 18f

            val rowH = 16f

            for ((rowIdx, row) in rows.withIndex()) {
                if (y + rowH > pageH - marginB) {
                    pdf.finishPage(currentPage)
                    canvas = newPage()
                    y = marginT
                    // Repeat header on new page
                    if (rowIdx > 0) {
                        drawSheetRow(canvas, rows[0], scaledWidths, marginL, y, rowH,
                            headerPaint, gridPaint, isHeader = true)
                        y += rowH
                    }
                }

                val isHeader = rowIdx == 0
                drawSheetRow(canvas, row, scaledWidths, marginL, y, rowH,
                    if (isHeader) headerPaint else cellPaint, gridPaint, isHeader)
                y += rowH
            }

            // Close last page of this sheet
            if (pageNum > 0) pdf.finishPage(currentPage)
        }

        if (pageNum == 0) {
            // Empty file — write one blank page
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            val p = pdf.startPage(info)
            p.canvas.drawColor(Color.WHITE)
            p.canvas.drawText("(Empty spreadsheet)", 40f, 60f, cellPaint)
            pdf.finishPage(p)
        }

        val cacheDir = File(cacheDir, "converted_pdfs")
        cacheDir.mkdirs()
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .removeSuffix(".xlsx").removeSuffix(".xls")
        val file = File(cacheDir, "${safe}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun drawSheetRow(
        canvas: android.graphics.Canvas, row: List<String>,
        colWidths: List<Float>, startX: Float, y: Float, rowH: Float,
        textPaint: Paint, gridPaint: Paint, isHeader: Boolean
    ) {
        var x = startX
        // Header row background
        if (isHeader) {
            val bgPaint = Paint().apply { color = Color.rgb(220, 230, 255) }
            canvas.drawRect(x, y, x + colWidths.sum(), y + rowH, bgPaint)
        }
        colWidths.forEachIndexed { i, w ->
            val cell = row.getOrElse(i) { "" }
            // Clip text to column width
            val maxChars = (w / (textPaint.textSize * 0.6f)).toInt().coerceAtLeast(1)
            val displayText = if (cell.length > maxChars) cell.take(maxChars - 1) + "…" else cell
            canvas.drawText(displayText, x + 2f, y + rowH - 4f, textPaint)
            // Cell border
            canvas.drawRect(x, y, x + w, y + rowH, gridPaint)
            x += w
        }
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        val f = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        return f.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}

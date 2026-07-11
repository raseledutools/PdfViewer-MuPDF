package com.rasel.RasFocus.selfcontrol.study_tools

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────
sealed class OfficeContent {
    data class DocxContent(val paragraphs: List<DocParagraph>)    : OfficeContent()
    data class XlsxContent(val sheets: List<SheetData>)           : OfficeContent()
    data class PptxContent(val slides: List<SlideData>)           : OfficeContent()
    object Unsupported                                             : OfficeContent()
}

data class DocParagraph(val text: String, val style: String, val isBold: Boolean, val fontSize: Int)
data class SheetData(val name: String, val rows: List<List<String>>)
data class SlideData(val index: Int, val title: String, val body: String)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class OfficeViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null -> intent.data
            else -> null
        }
        val fileName = uri?.let { getFileName(it) } ?: "Document"
        setContent { OfficeViewerScreen(uri = uri, fileName = fileName, onClose = { finish() }) }
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
}

// ─────────────────────────────────────────────────────────────────────────────
// COLORS
// ─────────────────────────────────────────────────────────────────────────────
private val OBg      = Color(0xFF0D1117)
private val OSurface = Color(0xFF161B22)
private val OBorder  = Color(0xFF30363D)
private val OText    = Color(0xFFE6EDF3)
private val OSub     = Color(0xFF8B949E)
private val OBlue    = Color(0xFF58A6FF)
private val OGreen   = Color(0xFF3FB950)
private val OAmber   = Color(0xFFF2CC60)
private val OPurple  = Color(0xFFD2A8FF)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OfficeViewerScreen(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ext     = fileName.substringAfterLast('.', "").lowercase()

    var content   by remember { mutableStateOf<OfficeContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf("") }

    // Parse on IO thread
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; error = "ফাইল পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val stream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("ফাইল পড়া যায়নি")
                val result = when (ext) {
                    "docx" -> parseDocx(stream)
                    "xlsx", "xls" -> parseXlsx(stream)
                    "pptx" -> parsePptx(stream)
                    else -> OfficeContent.Unsupported
                }
                stream.close()
                content   = result
                isLoading = false
            } catch (e: Exception) {
                error     = "পার্স করা যায়নি: ${e.message?.take(80)}"
                isLoading = false
            }
        }
    }

    // Icon + accent per type
    val (typeIcon, typeColor, typeLabel) = when (ext) {
        "docx", "doc" -> Triple(Icons.Default.Description, OBlue,   "Word Document")
        "xlsx", "xls" -> Triple(Icons.Default.TableChart,  OGreen,  "Excel Spreadsheet")
        "pptx", "ppt" -> Triple(Icons.Default.Slideshow,   OAmber,  "PowerPoint")
        else           -> Triple(Icons.Default.InsertDriveFile, OSub, ext.uppercase())
    }

    Box(Modifier.fillMaxSize().background(OBg)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────
            Surface(color = OSurface, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OText)
                    }
                    Box(
                        modifier = Modifier.size(32.dp)
                            .background(typeColor.copy(.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fileName, color = OText, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, maxLines = 1)
                        Text(typeLabel, color = typeColor, fontSize = 11.sp)
                    }
                }
            }

            // ── Content ──────────────────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = typeColor, strokeWidth = 2.5.dp)
                        Text("পড়া হচ্ছে…", color = OSub, fontSize = 12.sp)
                    }
                }
                error.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("❌", fontSize = 40.sp)
                        Text(error, color = OSub, fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
                else -> when (val c = content) {
                    is OfficeContent.DocxContent  -> DocxView(c)
                    is OfficeContent.XlsxContent  -> XlsxView(c)
                    is OfficeContent.PptxContent  -> PptxView(c)
                    is OfficeContent.Unsupported  -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("এই format টি সাপোর্ট করে না", color = OSub)
                    }
                    null -> {}
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOCX VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DocxView(content: OfficeContent.DocxContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content.paragraphs.forEach { para ->
            if (para.text.isBlank()) {
                Spacer(Modifier.height(6.dp))
                return@forEach
            }
            val (size, weight, color) = when {
                para.style.contains("Heading1", true) -> Triple(22.sp, FontWeight.ExtraBold, OBlue)
                para.style.contains("Heading2", true) -> Triple(18.sp, FontWeight.Bold, OBlue.copy(.85f))
                para.style.contains("Heading3", true) -> Triple(15.sp, FontWeight.Bold, OText)
                para.isBold                           -> Triple(14.sp, FontWeight.Bold, OText)
                else                                  -> Triple(14.sp, FontWeight.Normal, OText)
            }
            Text(para.text, fontSize = size, fontWeight = weight, color = color, lineHeight = (size.value * 1.55f).sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// XLSX VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun XlsxView(content: OfficeContent.XlsxContent) {
    var selectedSheet by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Sheet tabs
        if (content.sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheet,
                containerColor   = OSurface,
                contentColor     = OGreen
            ) {
                content.sheets.forEachIndexed { i, sheet ->
                    Tab(selected = i == selectedSheet, onClick = { selectedSheet = i }) {
                        Text(sheet.name, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 12.sp, fontWeight = if (i == selectedSheet) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        val sheet = content.sheets.getOrNull(selectedSheet) ?: return@Column
        val hScroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(hScroll)
                .padding(12.dp)
                .navigationBarsPadding()
        ) {
            sheet.rows.forEachIndexed { rowIdx, row ->
                Row {
                    row.forEachIndexed { colIdx, cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 80.dp, max = 200.dp)
                                .background(
                                    when {
                                        rowIdx == 0 -> OGreen.copy(.12f)
                                        rowIdx % 2 == 0 -> OSurface
                                        else -> OBg
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                cell,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (rowIdx == 0) OGreen else OText,
                                fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2
                            )
                        }
                    }
                }
                if (rowIdx == 0) HorizontalDivider(color = OGreen.copy(.3f), thickness = 1.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PPTX VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PptxView(content: OfficeContent.PptxContent) {
    var currentSlide by remember { mutableIntStateOf(0) }
    val slide = content.slides.getOrNull(currentSlide) ?: return

    Column(Modifier.fillMaxSize()) {
        // Slide content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Slide number
                Text("Slide ${slide.index + 1} / ${content.slides.size}",
                    fontSize = 11.sp, color = OAmber, fontWeight = FontWeight.Bold)

                // Title
                if (slide.title.isNotBlank()) {
                    Text(slide.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = OAmber, lineHeight = 28.sp)
                    HorizontalDivider(color = OAmber.copy(.3f))
                }

                // Body
                if (slide.body.isNotBlank()) {
                    Text(slide.body, fontSize = 14.sp, color = OText, lineHeight = 22.sp)
                }
            }
        }

        // Navigation
        Surface(color = OSurface, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick  = { if (currentSlide > 0) currentSlide-- },
                    enabled  = currentSlide > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, null,
                        tint = if (currentSlide > 0) OAmber else OSub,
                        modifier = Modifier.size(28.dp))
                }
                Text("${currentSlide + 1} / ${content.slides.size}",
                    color = OText, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick  = { if (currentSlide < content.slides.size - 1) currentSlide++ },
                    enabled  = currentSlide < content.slides.size - 1
                ) {
                    Icon(Icons.Default.ChevronRight, null,
                        tint = if (currentSlide < content.slides.size - 1) OAmber else OSub,
                        modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PARSERS (IO thread)
// ─────────────────────────────────────────────────────────────────────────────
private fun parseDocx(stream: InputStream): OfficeContent.DocxContent {
    val doc   = XWPFDocument(stream)
    val paras = doc.paragraphs.map { p ->
        val style  = p.style ?: ""
        val isBold = p.runs.firstOrNull()?.isBold ?: false
        val size   = p.runs.firstOrNull()?.fontSize?.takeIf { it > 0 } ?: 11
        DocParagraph(p.text, style, isBold, size)
    }
    doc.close()
    return OfficeContent.DocxContent(paras)
}

private fun parseXlsx(stream: InputStream): OfficeContent.XlsxContent {
    val wb     = WorkbookFactory.create(stream)
    val sheets = (0 until wb.numberOfSheets).map { si ->
        val sheet = wb.getSheetAt(si)
        val rows  = (sheet.firstRowNum..sheet.lastRowNum).map { ri ->
            val row = sheet.getRow(ri)
            if (row == null) {
                emptyList()
            } else {
                (row.firstCellNum until row.lastCellNum).map { ci ->
                    val cell = row.getCell(ci.toInt())
                    cell?.toString() ?: ""
                }
            }
        }.filter { it.isNotEmpty() }
        SheetData(sheet.sheetName, rows)
    }
    wb.close()
    return OfficeContent.XlsxContent(sheets)
}

private fun parsePptx(stream: InputStream): OfficeContent.PptxContent {
    val ppt    = XMLSlideShow(stream)
    val slides = ppt.slides.mapIndexed { i, slide ->
        val title = slide.title ?: ""
        val body  = slide.shapes
            .filterIsInstance<org.apache.poi.xslf.usermodel.XSLFTextShape>()
            .filter { it.shapeName?.contains("title", true) == false }
            .joinToString("\n") { shape ->
                shape.textParagraphs.joinToString("\n") { it.text }
            }
        SlideData(i, title, body)
    }
    ppt.close()
    return OfficeContent.PptxContent(slides)
}

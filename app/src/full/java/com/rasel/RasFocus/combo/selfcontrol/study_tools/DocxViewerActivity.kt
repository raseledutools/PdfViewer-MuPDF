package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val fontSize: Int = 24,          // half-points → divide by 2 for sp
    val colorHex: String = "",
    val highlight: Boolean = false
)

data class DocxParagraph(
    val runs: List<DocxRun>,
    val style: String = "Normal",    // "Heading1"…"Heading6", "Normal", "ListParagraph"
    val indent: Int = 0,             // list indent level
    val numFmt: String = "",         // "bullet", "decimal", ""
    val alignment: TextAlign = TextAlign.Start
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────

class DocxViewerActivity : ComponentActivity() {

    private val uriState  = mutableStateOf<Uri?>(null)
    private val nameState = mutableStateOf("Document")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            MaterialTheme {
                DocxViewer(
                    uri      = uriState.value,
                    fileName = nameState.value,
                    onClose  = { finish() }
                )
            }
        }
    }

    private fun loadFromIntent(intent: android.content.Intent?) {
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
        uriState.value  = uri
        nameState.value = uri?.let { getFileName(it) } ?: "Document"
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
// DOCX PARSER  (ZIP + XmlPullParser — zero external deps)
// ─────────────────────────────────────────────────────────────────────────────

object DocxParser {

    fun parse(context: android.content.Context, uri: Uri): List<DocxParagraph> {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: return emptyList()

        var documentXml: ByteArray? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readBytes()
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return documentXml?.let { parseDocumentXml(it) } ?: emptyList()
    }

    private fun parseDocumentXml(xml: ByteArray): List<DocxParagraph> {
        val paragraphs = mutableListOf<DocxParagraph>()
        val xpp        = newParser(xml.inputStream())
        var event      = xpp.eventType

        // Paragraph-level state
        var paraStyle  = "Normal"
        var paraIndent = 0
        var paraNumFmt = ""
        var paraAlign  = TextAlign.Start
        val runs       = mutableListOf<DocxRun>()

        // Run-level state
        var runBold      = false
        var runItalic    = false
        var runUnderline = false
        var runStrike    = false
        var runSize      = 24        // half-points
        var runColor     = ""
        var runHighlight = false
        val runText      = StringBuilder()
        var inRun        = false

        fun flushRun() {
            val t = runText.toString()
            if (t.isNotEmpty()) {
                runs.add(DocxRun(t, runBold, runItalic, runUnderline, runStrike,
                    runSize, runColor, runHighlight))
            }
            runText.clear()
        }

        fun flushPara() {
            flushRun()
            if (runs.isNotEmpty()) {
                paragraphs.add(DocxParagraph(
                    runs      = runs.toList(),
                    style     = paraStyle,
                    indent    = paraIndent,
                    numFmt    = paraNumFmt,
                    alignment = paraAlign
                ))
            }
            runs.clear()
            paraStyle = "Normal"; paraIndent = 0; paraNumFmt = ""; paraAlign = TextAlign.Start
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (xpp.name) {
                    // Paragraph style
                    "w:pStyle" -> {
                        paraStyle = xpp.getAttributeValue(null, "w:val") ?: "Normal"
                    }
                    // Paragraph alignment
                    "w:jc" -> {
                        paraAlign = when (xpp.getAttributeValue(null, "w:val")) {
                            "center"    -> TextAlign.Center
                            "right", "end" -> TextAlign.End
                            "both", "distribute" -> TextAlign.Justify
                            else        -> TextAlign.Start
                        }
                    }
                    // List numbering
                    "w:ilvl" -> {
                        paraIndent = xpp.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0
                    }
                    "w:numId" -> {
                        val id = xpp.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0
                        if (id > 0) paraNumFmt = "bullet"
                    }

                    // Run properties
                    "w:r" -> {
                        inRun = true
                        // reset run formatting
                        runBold = false; runItalic = false; runUnderline = false
                        runStrike = false; runSize = 24; runColor = ""; runHighlight = false
                    }
                    "w:b"  -> if (inRun) runBold      = true
                    "w:i"  -> if (inRun) runItalic     = true
                    "w:u"  -> if (inRun) runUnderline  = xpp.getAttributeValue(null, "w:val") != "none"
                    "w:strike" -> if (inRun) runStrike = true
                    "w:sz" -> if (inRun) runSize = xpp.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 24
                    "w:color" -> if (inRun) runColor   = xpp.getAttributeValue(null, "w:val") ?: ""
                    "w:highlight" -> if (inRun) runHighlight = true

                    // Tab → space
                    "w:tab" -> if (inRun) runText.append("    ")

                    // Line break
                    "w:br" -> if (inRun) runText.append("\n")
                }

                XmlPullParser.TEXT -> {
                    if (inRun) runText.append(xpp.text ?: "")
                }

                XmlPullParser.END_TAG -> when (xpp.name) {
                    "w:r"  -> { flushRun(); inRun = false }
                    "w:p"  -> flushPara()
                }
            }
            event = xpp.next()
        }

        return paragraphs
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSE HELPERS — paragraph → AnnotatedString
// ─────────────────────────────────────────────────────────────────────────────

private fun DocxParagraph.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        runs.forEach { run ->
            withStyle(
                SpanStyle(
                    fontWeight     = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle      = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when {
                        run.underline && run.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        run.underline -> TextDecoration.Underline
                        run.strike    -> TextDecoration.LineThrough
                        else          -> TextDecoration.None
                    },
                    fontSize = (run.fontSize / 2).coerceIn(8, 72).sp,
                    color    = if (run.colorHex.length == 6) {
                        try { Color(android.graphics.Color.parseColor("#${run.colorHex}")) }
                        catch (_: Exception) { Color.Unspecified }
                    } else Color.Unspecified,
                    background = if (run.highlight) Color(0xFFFFFF00) else Color.Unspecified
                )
            ) {
                append(run.text)
            }
        }
    }
}

private val DocxParagraph.headingLevel: Int
    get() = when {
        style.startsWith("Heading1") || style == "Title" -> 1
        style.startsWith("Heading2") || style == "Subtitle" -> 2
        style.startsWith("Heading3") -> 3
        style.startsWith("Heading4") -> 4
        style.startsWith("Heading5") -> 5
        style.startsWith("Heading6") -> 6
        else -> 0
    }

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DocxViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var paragraphs      by remember { mutableStateOf<List<DocxParagraph>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(true) }
    var errorMsg        by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    val listState       = rememberLazyListState()

    val BG       = Color(0xFF1C1C1E)
    val PAPER    = Color(0xFFFAFAFA)
    val VA_WHITE = Color(0xFFF5F5F5)
    val VA_BG    = Color(0xFF111111)

    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "ফাইল পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val result = DocxParser.parse(context, uri)
                withContext(Dispatchers.Main) {
                    paragraphs = result
                    isLoading  = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg  = "খুলতে পারিনি: ${e.message}"
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BG)
            .systemBarsPadding()
    ) {
        when {
            isLoading -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4FACFE))
                    Spacer(Modifier.height(12.dp))
                    Text("Document খুলছি…", color = VA_WHITE, fontSize = 14.sp)
                }
            }
            errorMsg.isNotEmpty() -> {
                Text(errorMsg, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            }
            paragraphs.isEmpty() -> {
                Text("কোনো content পাওয়া যায়নি", color = VA_WHITE, modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                LazyColumn(
                    state           = listState,
                    modifier        = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                        },
                    contentPadding  = PaddingValues(
                        top = 64.dp, bottom = 72.dp, start = 16.dp, end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Group paragraphs into "pages" (paper card style)
                    // Simple approach: one card per ~40 paragraphs
                    val chunks = paragraphs.chunked(40)
                    items(chunks) { chunk ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(8.dp),
                            color    = PAPER,
                            shadowElevation = 4.dp
                        ) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                                chunk.forEach { para ->
                                    ParagraphView(para)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Top bar
                androidx.compose.animation.AnimatedVisibility(
                    visible  = controlsVisible,
                    enter    = fadeIn(),
                    exit     = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(VA_BG.copy(alpha = 0.95f))
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.ArrowBack, null, tint = VA_WHITE, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            fileName, color = VA_WHITE, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphView(para: DocxParagraph) {
    if (para.runs.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        return
    }

    val headingLevel = para.headingLevel
    val annotated    = para.toAnnotatedString()

    // Prefix for list items
    val prefix = when {
        para.numFmt == "bullet" -> "${"  ".repeat(para.indent)}• "
        para.numFmt == "decimal" -> "${"  ".repeat(para.indent)}1. "
        else -> ""
    }

    val combinedText = if (prefix.isNotEmpty()) {
        buildAnnotatedString { append(prefix); append(annotated) }
    } else annotated

    val (fontSize, fontWeight, topPad, bottomPad) = when (headingLevel) {
        1 -> listOf(28, FontWeight.ExtraBold, 16, 8)
        2 -> listOf(24, FontWeight.Bold,      12, 6)
        3 -> listOf(20, FontWeight.Bold,       8, 4)
        4 -> listOf(18, FontWeight.SemiBold,   6, 4)
        5 -> listOf(16, FontWeight.SemiBold,   4, 2)
        6 -> listOf(14, FontWeight.Medium,     4, 2)
        else -> listOf(15, FontWeight.Normal,  2, 2)
    }

    @Suppress("UNCHECKED_CAST")
    val fw = fontWeight as FontWeight

    Box(Modifier.padding(top = (topPad as Int).dp, bottom = (bottomPad as Int).dp)) {
        Text(
            text      = combinedText,
            fontSize  = (fontSize as Int).sp,
            fontWeight = fw,
            textAlign = para.alignment,
            color     = Color(0xFF1A1A1A),
            modifier  = Modifier.fillMaxWidth()
        )
        // Heading underline for H1/H2
        if (headingLevel in 1..2) {
            Divider(
                color     = Color(0xFF3A86FF).copy(alpha = 0.5f),
                thickness = if (headingLevel == 1) 2.dp else 1.dp,
                modifier  = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

package com.rasel.RasFocus.selfcontrol.study_tools

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class TextViewerActivity : ComponentActivity() {

    private val uriState      = mutableStateOf<Uri?>(null)
    private val fileNameState = mutableStateOf("File")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadFromIntent(intent)
        setContent {
            TextViewerScreen(
                uri      = uriState.value,
                fileName = fileNameState.value,
                onClose  = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadFromIntent(intent)
    }

    private fun loadFromIntent(intent: android.content.Intent?) {
        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null -> intent.data
            else -> null
        }
        if (uri != null && uri.scheme == "content") {
            // Try read+write first (needed for save), fall back to read-only
            // — some file managers only grant read, and that's fine for viewing.
            // Both calls are wrapped separately so a write failure doesn't block read.
            //
            // FIX: takePersistableUriPermission can throw IllegalArgumentException
            // (not just SecurityException) when the source content provider doesn't
            // support persistable grants at all — this is common with many
            // third-party file managers. Catching only SecurityException let that
            // exception propagate uncaught, crashing the activity before it ever
            // rendered anything. Catching Exception broadly here is intentional:
            // this permission is a nice-to-have (enables Save), never something
            // that should be allowed to crash the viewer.
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { /* write not granted — view only, save disabled */ }
        }
        uriState.value      = uri
        fileNameState.value = uri?.let { getFileNameFromUri(it) } ?: "File"
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) { /* some providers reject query — fall back to path segment below */ }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLORS (dark code-editor / GitHub dark theme)
// ─────────────────────────────────────────────────────────────────────────────
private val BG         = Color(0xFF0D1117)
private val SURFACE    = Color(0xFF161B22)
private val TEXT_MAIN  = Color(0xFFE6EDF3)
private val TEXT_MUTED = Color(0xFF8B949E)
private val ACCENT     = Color(0xFF58A6FF)
private val GREEN      = Color(0xFF3FB950)

// Kotlin syntax
private val KT_KEYWORD = Color(0xFFFF7B72)
private val KT_STRING  = Color(0xFFA5D6FF)
private val KT_COMMENT = Color(0xFF8B949E)
private val KT_NUMBER  = Color(0xFFF2CC60)
private val KT_ANNOT   = Color(0xFFD2A8FF)
private val KT_TYPE    = Color(0xFFFFA657)

// Markdown
private val MD_H1      = Color(0xFFFFD700)
private val MD_H2      = Color(0xFFFFA657)
private val MD_H3      = Color(0xFF79C0FF)
private val MD_CODE_BG = Color(0xFF21262D)
private val MD_LINK    = Color(0xFF58A6FF)
private val MD_BULLET  = Color(0xFF79C0FF)
private val MD_ITALIC  = Color(0xFFC9D1D9)

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TextViewerScreen(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var rawText    by remember { mutableStateOf("") }
    var editText   by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf("") }
    var isEditMode by remember { mutableStateOf(false) }
    var isSaving   by remember { mutableStateOf(false) }
    var toast      by remember { mutableStateOf("") }   // "" = hidden

    val ext        = fileName.substringAfterLast('.', "").lowercase()
    val isKotlin   = ext == "kt"
    val isMarkdown = ext in listOf("md", "markdown")
    val isEditable = ext in listOf("kt", "txt", "md", "markdown", "py", "js", "ts",
                                   "html", "htm", "css", "xml", "json", "yaml", "yml",
                                   "toml", "ini", "sh", "bat", "log", "csv", "java",
                                   "cpp", "c", "h", "dart", "swift", "rb", "php", "rs")

    // Load file
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "ফাইল পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("ফাইল পড়া যায়নি")
                rawText  = text
                editText = text
                isLoading = false
            } catch (e: Exception) {
                errorMsg  = "খুলতে পারিনি: ${e.message}"
                isLoading = false
            }
        }
    }

    // Auto-hide toast
    LaunchedEffect(toast) {
        if (toast.isNotBlank()) { delay(2200); toast = "" }
    }

    // Save function
    suspend fun saveFile() {
        if (uri == null) return
        isSaving = true
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")
                    ?.bufferedWriter()?.use { it.write(editText) }
                rawText = editText
                withContext(Dispatchers.Main) {
                    toast = "✓  Saved!"
                    isEditMode = false
                    isSaving   = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast    = "❌  Save failed: ${e.message?.take(40)}"
                    isSaving = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top Bar ────────────────────────────────────────────────────
            Surface(color = SURFACE, shadowElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TEXT_MAIN)
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            text       = fileName,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TEXT_MAIN,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            text  = buildString {
                                append(when (ext) {
                                    "kt"              -> "Kotlin"
                                    "md", "markdown"  -> "Markdown"
                                    "txt"             -> "Plain Text"
                                    "py"              -> "Python"
                                    "js"              -> "JavaScript"
                                    "json"            -> "JSON"
                                    "xml"             -> "XML"
                                    "html", "htm"     -> "HTML"
                                    else              -> ext.uppercase().ifBlank { "Text" }
                                })
                                if (isEditMode) append(" · Editing")
                                if (editText != rawText && isEditMode) append(" ·  unsaved")
                            },
                            fontSize = 11.sp,
                            color    = if (editText != rawText && isEditMode) Color(0xFFF2CC60) else TEXT_MUTED
                        )
                    }

                    // Copy
                    if (!isEditMode) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(rawText))
                            toast = "✓  Copied!"
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = TEXT_MUTED)
                        }
                    }

                    // Save (edit mode only)
                    if (isEditMode && isEditable) {
                        if (isSaving) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    color       = GREEN,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                scope.launch { saveFile() }
                            }) {
                                Icon(Icons.Default.Save, "Save", tint = GREEN)
                            }
                        }
                    }

                    // Edit/View toggle
                    if (isEditable && !isLoading && errorMsg.isBlank()) {
                        IconButton(onClick = {
                            if (isEditMode && editText != rawText) {
                                // Discard changes — reset to last saved
                                editText   = rawText
                            }
                            isEditMode = !isEditMode
                        }) {
                            Icon(
                                imageVector        = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (isEditMode) "View" else "Edit",
                                tint               = if (isEditMode) ACCENT else TEXT_MUTED
                            )
                        }
                    }
                }
            }

            // ── Content ────────────────────────────────────────────────────
            // FIX: Loading screen সরানো হয়েছে — "পড়া হচ্ছে..." message আর আসবে না।
            // Top-এ subtle linear progress bar দেখাবে load হওয়ার সময়,
            // content area সাথে সাথে দেখা যাবে।
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = ACCENT,
                    trackColor = BG
                )
            }
            when {
                isLoading && rawText.isEmpty() && errorMsg.isBlank() -> {
                    // Content area খালি রাখি load হওয়া পর্যন্ত — no full-screen spinner
                    Box(Modifier.fillMaxSize().background(BG))
                }
                errorMsg.isNotBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❌", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(errorMsg, color = TEXT_MUTED, fontSize = 13.sp)
                        }
                    }
                }
                isEditMode -> {
                    // ── EDIT MODE — TextField (notepad style) ──────────────
                    TextField(
                        value         = editText,
                        onValueChange = { editText = it },
                        modifier      = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        textStyle = TextStyle(
                            fontSize   = 13.sp,
                            lineHeight  = 20.sp,
                            fontFamily = if (isKotlin) FontFamily.Monospace else FontFamily.Default,
                            color      = TEXT_MAIN
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = BG,
                            unfocusedContainerColor = BG,
                            focusedTextColor        = TEXT_MAIN,
                            unfocusedTextColor      = TEXT_MAIN,
                            cursorColor             = ACCENT,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        placeholder = { Text("এখানে লিখুন…", color = TEXT_MUTED, fontSize = 13.sp) }
                    )
                }
                else -> {
                    // ── VIEW MODE ──────────────────────────────────────────
                    SelectionContainer {
                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        val baseMod = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .navigationBarsPadding()

                        when {
                            isMarkdown ->
                                MarkdownContent(rawText, baseMod)
                            isKotlin ->
                                Text(
                                    text      = buildKotlinAnnotated(rawText),
                                    modifier  = baseMod.horizontalScroll(hScroll),
                                    softWrap  = false,
                                    style     = TextStyle(
                                        fontSize   = 13.sp,
                                        lineHeight = 20.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            else ->
                                Text(
                                    text     = rawText,
                                    modifier = baseMod,
                                    style    = TextStyle(
                                        fontSize   = 14.sp,
                                        lineHeight = 22.sp,
                                        color      = TEXT_MAIN
                                    )
                                )
                        }
                    }
                }
            }
        }

        // ── Toast ──────────────────────────────────────────────────────────
        if (toast.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                val isBad = toast.startsWith("❌")
                Surface(
                    shape           = RoundedCornerShape(20.dp),
                    color           = (if (isBad) Color(0xFFDA3633) else GREEN).copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text       = toast,
                        modifier   = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KOTLIN SYNTAX HIGHLIGHT
// ─────────────────────────────────────────────────────────────────────────────
private val KT_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed",
    "abstract", "open", "override", "private", "public", "protected", "internal",
    "companion", "return", "if", "else", "when", "for", "while", "do", "in", "is",
    "as", "by", "import", "package", "typealias", "true", "false", "null", "this",
    "super", "init", "constructor", "get", "set", "it", "suspend", "inline",
    "reified", "crossinline", "noinline", "lateinit", "const", "throw", "try",
    "catch", "finally", "continue", "break", "with", "let", "also", "apply",
    "run", "to", "and", "or", "not"
)

private fun buildKotlinAnnotated(code: String): AnnotatedString {
    return buildAnnotatedString {
        code.lines().forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//")) {
                withStyle(SpanStyle(color = KT_COMMENT)) { append(line) }
            } else {
                var i = 0
                while (i < line.length) {
                    when {
                        line[i] == '@' -> {
                            var end = i + 1
                            while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                            withStyle(SpanStyle(color = KT_ANNOT)) { append(line.substring(i, end)) }
                            i = end
                        }
                        line[i] == '"' -> {
                            val end = (line.indexOf('"', i + 1) + 1).let { if (it == 0) line.length else it }
                            withStyle(SpanStyle(color = KT_STRING)) { append(line.substring(i, end)) }
                            i = end
                        }
                        line[i] == '\'' -> {
                            val end = (line.indexOf('\'', i + 1) + 1).let { if (it == 0) line.length else it }
                            withStyle(SpanStyle(color = KT_STRING)) { append(line.substring(i, end)) }
                            i = end
                        }
                        line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                            var end = i
                            while (end < line.length && (line[end].isDigit() || line[end] == '.' || line[end] == 'f' || line[end] == 'L')) end++
                            withStyle(SpanStyle(color = KT_NUMBER)) { append(line.substring(i, end)) }
                            i = end
                        }
                        line[i].isLetter() || line[i] == '_' -> {
                            var end = i
                            while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                            val word = line.substring(i, end)
                            when {
                                word in KT_KEYWORDS -> withStyle(SpanStyle(color = KT_KEYWORD, fontWeight = FontWeight.Bold)) { append(word) }
                                word.firstOrNull()?.isUpperCase() == true -> withStyle(SpanStyle(color = KT_TYPE)) { append(word) }
                                else -> withStyle(SpanStyle(color = TEXT_MAIN)) { append(word) }
                            }
                            i = end
                        }
                        else -> { withStyle(SpanStyle(color = TEXT_MUTED)) { append(line[i]) }; i++ }
                    }
                }
            }
            if (idx < code.lines().lastIndex) append('\n')
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARKDOWN RENDERER (no external lib, line-by-line)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MarkdownContent(text: String, modifier: Modifier) {
    Column(modifier) {
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trimStart().startsWith("```") -> {
                    val lang = line.trim().removePrefix("```")
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i]); i++
                    }
                    MdCodeBlock(codeLines.joinToString("\n"), lang)
                }
                line.startsWith("# ")   -> MdHeading(line.removePrefix("# "),   MD_H1, 22.sp, 12.dp)
                line.startsWith("## ")  -> MdHeading(line.removePrefix("## "),  MD_H2, 18.sp, 10.dp)
                line.startsWith("### ") -> MdHeading(line.removePrefix("### "), MD_H3, 15.sp, 8.dp)
                line.trimStart().startsWith("---") || line.trimStart().startsWith("***") -> {
                    HorizontalDivider(
                        modifier  = Modifier.padding(vertical = 8.dp),
                        color     = TEXT_MUTED.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                }
                line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
                    val indent = (line.length - line.trimStart().length) * 4
                    Row(Modifier.padding(start = (indent + 2).dp, top = 2.dp, bottom = 2.dp)) {
                        Text("•  ", color = MD_BULLET, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(mdInline(line.trimStart().drop(2)), fontSize = 14.sp, lineHeight = 20.sp, color = TEXT_MAIN)
                    }
                }
                line.trimStart().matches(Regex("\\d+\\.\\s.*")) -> {
                    val m    = Regex("^(\\d+\\.\\s)(.*)").find(line.trimStart())
                    val num  = m?.groupValues?.get(1) ?: ""
                    val rest = m?.groupValues?.get(2) ?: line
                    Row(Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(num, color = MD_BULLET, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(mdInline(rest), fontSize = 14.sp, lineHeight = 20.sp, color = TEXT_MAIN)
                    }
                }
                line.startsWith("> ") -> {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.width(3.dp).height(20.dp).background(MD_LINK, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(mdInline(line.removePrefix("> ")), fontSize = 14.sp, lineHeight = 20.sp,
                            color = TEXT_MUTED, fontStyle = FontStyle.Italic)
                    }
                }
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(mdInline(line), fontSize = 14.sp, lineHeight = 22.sp,
                    color = TEXT_MAIN, modifier = Modifier.padding(vertical = 1.dp))
            }
            i++
        }
    }
}

@Composable
private fun MdHeading(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit, topPad: androidx.compose.ui.unit.Dp) {
    Text(text, fontSize = size, fontWeight = FontWeight.ExtraBold, color = color,
        modifier = Modifier.padding(top = topPad, bottom = 3.dp))
}

@Composable
private fun MdCodeBlock(code: String, lang: String) {
    Surface(
        modifier        = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color           = MD_CODE_BG,
        shape           = RoundedCornerShape(8.dp),
        tonalElevation  = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            if (lang.isNotBlank()) {
                Text(lang.uppercase(), fontSize = 10.sp, color = TEXT_MUTED,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 6.dp))
            }
            val hScroll = rememberScrollState()
            Text(
                text     = if (lang in listOf("kt", "kotlin")) buildKotlinAnnotated(code) else AnnotatedString(code),
                softWrap = false,
                style    = TextStyle(fontSize = 12.sp, lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace, color = TEXT_MAIN),
                modifier = Modifier.horizontalScroll(hScroll)
            )
        }
    }
}

private fun mdInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TEXT_MAIN)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                else { append(text[i]); i++ }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) { withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = MD_ITALIC)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = MD_CODE_BG, color = KT_STRING, fontSize = 12.sp)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                else { append(text[i]); i++ }
            }
            text.startsWith("[", i) -> {
                val cb = text.indexOf(']', i)
                val op = if (cb != -1) text.indexOf('(', cb) else -1
                val cp = if (op == cb + 1) text.indexOf(')', op) else -1
                if (cp != -1) { withStyle(SpanStyle(color = MD_LINK)) { append(text.substring(i + 1, cb)) }; i = cp + 1 }
                else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

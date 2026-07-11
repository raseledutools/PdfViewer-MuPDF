package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

// ─────────────────────────────────────────────────────────────────────────────
// RasFocus SIGNATURE COLORS  (WPS dark + indigo/amber accent)
// ─────────────────────────────────────────────────────────────────────────────
private val BG        = Color(0xFF0A0A0F)   // near-black
private val BG2       = Color(0xFF111118)
private val CARD      = Color(0xFF16161F)
private val CARD2     = Color(0xFF1C1C28)
private val BORDER    = Color(0xFF252535)
private val MUTED     = Color(0xFF55556A)
private val WHITE     = Color(0xFFF0EFFF)

// Primary accent — electric indigo
private val INDIGO    = Color(0xFF6C63FF)
private val INDIGO2   = Color(0xFF8B83FF)

// Secondary accent — amber/gold
private val AMBER     = Color(0xFFFFB347)
private val AMBER2    = Color(0xFFFFD580)

// Tool accent colors (WPS-like per-tool colors)
private val T_RED     = Color(0xFFFF5C5C)
private val T_ORANGE  = Color(0xFFFF8A47)
private val T_PINK    = Color(0xFFFF4D8B)
private val T_YELLOW  = Color(0xFFFFCC00)
private val T_BLUE    = Color(0xFF4DA6FF)
private val T_GREEN   = Color(0xFF3FD68F)
private val T_PURPLE  = Color(0xFFA87CFF)
private val T_TEAL    = Color(0xFF2DE0CC)

// Gradients
private val IndigoGrad = Brush.linearGradient(listOf(INDIGO, INDIGO2))
private val AmberGrad  = Brush.linearGradient(listOf(AMBER, AMBER2))
private val RedGrad    = Brush.linearGradient(listOf(T_RED, T_ORANGE))
private val GreenGrad  = Brush.linearGradient(listOf(T_GREEN, T_TEAL))
private val PinkGrad   = Brush.linearGradient(listOf(T_PINK, Color(0xFFFF8AC0)))
private val PurpleGrad = Brush.linearGradient(listOf(T_PURPLE, INDIGO2))
private val YellowGrad = Brush.linearGradient(listOf(T_YELLOW, AMBER))
private val BlueGrad   = Brush.linearGradient(listOf(T_BLUE, INDIGO2))

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────
private enum class PdfScreen { HOME, VIEWER, MERGE, SPLIT, COMPRESS, PDF_TO_IMG, IMG_TO_PDF, RESIZE, SCAN_TO_PDF, CALCULATOR }

// WPS bottom nav tabs
private enum class HomeTab { RECENT, FILES, TOOLS, TEMPLATES }

private data class RecentItem(
    val name:    String,
    val type:    String,         // "pdf" | "img"
    val size:    String,
    val time:    Long    = System.currentTimeMillis(),
    val uri:     android.net.Uri? = null,  // MediaStore URI
    val path:    String  = "",
    var starred: Boolean = false,
)

private data class QuickTool(
    val label:      String,
    val icon:       String,
    val screen:     PdfScreen,
    val color:      Color,
    val shortcutId: String = "",
)

// ── WPS top row tools (4 icons like "PDF Edit", "PDF DOC", "Picture to PDF", "Export images")
private val TOP_TOOLS = listOf(
    QuickTool("Scan\nto PDF",    "📷", PdfScreen.SCAN_TO_PDF, T_GREEN,  "scan_to_pdf"),
    QuickTool("PDF\nEdit",       "✏️", PdfScreen.VIEWER,      T_RED,    "pdf_viewer"),
    QuickTool("PDF\nDOC",        "📝", PdfScreen.MERGE,       T_ORANGE, "pdf_merge"),
    QuickTool("Picture\nto PDF", "🖼️", PdfScreen.IMG_TO_PDF,  T_PINK,   "img_to_pdf"),
)

// ── WPS second row tools
private val BOTTOM_TOOLS = listOf(
    QuickTool("PDF\nSplit",     "✂️", PdfScreen.SPLIT,     T_PINK,   "pdf_split"),
    QuickTool("Compress",       "🗜️", PdfScreen.COMPRESS,  T_YELLOW, "pdf_compress"),
    QuickTool("Image\nResize",  "📐", PdfScreen.RESIZE,    T_PURPLE, "img_resize"),
    QuickTool("PDF\nMerge",     "🔀", PdfScreen.MERGE,     T_GREEN,  ""),
    QuickTool("Calculator", "🔢", PdfScreen.CALCULATOR, T_BLUE,   "calculator"),
)

private val ALL_TOOLS = TOP_TOOLS + BOTTOM_TOOLS

// ─────────────────────────────────────────────────────────────────────────────
// ROOT SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PdfToolsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val controller  = remember { PdfEngineController() }
    val engineState by produceState(PdfEngineState()) {
        controller.onStateChange = { value = it }
    }

    var screen  by remember { mutableStateOf(PdfScreen.HOME) }
    var homeTab by remember { mutableStateOf(HomeTab.RECENT) }

    val recentItems = remember { mutableStateListOf<RecentItem>() }
    var isScanning by remember { mutableStateOf(false) }

    // MediaStore scan — device-এর সব PDF, last modified first
    suspend fun scanDevicePdfs(ctx: android.content.Context): List<RecentItem> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<RecentItem>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Files.getContentUri("external")

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA,
            )
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            val selArgs   = arrayOf("application/pdf")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            try {
                ctx.contentResolver.query(collection, projection, selection, selArgs, sortOrder)
                    ?.use { cursor ->
                        val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                        val timeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                        val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                        while (cursor.moveToNext()) {
                            val id   = cursor.getLong(idIdx)
                            val name = cursor.getString(nameIdx) ?: continue
                            val size = cursor.getLong(sizeIdx)
                            val time = cursor.getLong(timeIdx) * 1000L
                            val path = cursor.getString(pathIdx) ?: ""
                            val uri  = ContentUris.withAppendedId(collection, id)
                            val sizeStr = when {
                                size >= 1_048_576 -> "${"%.1f".format(size / 1_048_576.0)} MB"
                                size >= 1_024     -> "${size / 1024} KB"
                                else              -> "$size B"
                            }
                            list.add(RecentItem(
                                name = name, type = "pdf",
                                size = sizeStr, time = time,
                                uri = uri, path = path
                            ))
                        }
                    }
            } catch (_: Exception) {}
            list
        }

    // Auto-scan on first load
    LaunchedEffect(Unit) {
        isScanning = true
        val scanned = scanDevicePdfs(context)
        recentItems.clear()
        recentItems.addAll(scanned)
        isScanning = false
    }

    // Manual open থেকে top-এ add করা
    fun addRecent(name: String, type: String, size: String, uri: Uri? = null) {
        recentItems.removeAll { it.name == name }
        recentItems.add(0, RecentItem(name, type, size, uri = uri))
    }

    var pendingPdfCallback  by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var pendingImgsCallback by remember { mutableStateOf<((List<Pair<String,String>>) -> Unit)?>(null) }

    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val b64  = uriToBase64(context, uri) ?: return@launch
            val name = getFileName(context, uri)
            withContext(Dispatchers.Main) { pendingPdfCallback?.invoke(b64, name); pendingPdfCallback = null }
        }
    }
    val singleImgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val b64  = uriToBase64(context, uri) ?: return@launch
            val name = getFileName(context, uri)
            withContext(Dispatchers.Main) { pendingImgsCallback?.invoke(listOf(Pair(b64, name))); pendingImgsCallback = null }
        }
    }
    val multiDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch(Dispatchers.IO) {
            val results = uris.mapNotNull { uri ->
                val b64 = uriToBase64(context, uri) ?: return@mapNotNull null
                Pair(b64, getFileName(context, uri))
            }
            withContext(Dispatchers.Main) { pendingImgsCallback?.invoke(results); pendingImgsCallback = null }
        }
    }

    fun pickPdf(cb: (String, String) -> Unit)            { pendingPdfCallback = cb; singlePdfPicker.launch(arrayOf("application/pdf")) }
    fun pickImg(cb: (String, String) -> Unit)            { pendingImgsCallback = { list -> cb(list[0].first, list[0].second) }; singleImgPicker.launch(arrayOf("image/*")) }
    fun pickMultiPdf(cb: (List<Pair<String,String>>) -> Unit) { pendingImgsCallback = cb; multiDocPicker.launch(arrayOf("application/pdf")) }
    fun pickMultiImg(cb: (List<Pair<String,String>>) -> Unit) { pendingImgsCallback = cb; multiDocPicker.launch(arrayOf("image/*")) }

    BackHandler { if (screen != PdfScreen.HOME) screen = PdfScreen.HOME else onBack() }

    Box(Modifier.fillMaxSize().background(BG)) {
        PdfEngine(controller = controller)

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            label = "screen"
        ) { activeScreen ->
            when (activeScreen) {
                PdfScreen.HOME -> HomeScreen(
                    recentItems = recentItems,
                    isScanning  = isScanning,
                    activeTab   = homeTab,
                    onTabChange = { homeTab = it },
                    onToolClick = { screen = it },
                    onRefresh   = {
                        scope.launch {
                            isScanning = true
                            val scanned = scanDevicePdfs(context)
                            recentItems.clear()
                            recentItems.addAll(scanned)
                            isScanning = false
                        }
                    },
                    onBack      = onBack,
                )
                PdfScreen.VIEWER -> PdfViewerScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickPdf   = { pickPdf { b64, name ->
                        addRecent(name, "pdf", "${b64.length * 3 / 4 / 1024} KB")
                        controller.loadPdfInViewer(b64, name)
                    }},
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.MERGE -> MergeScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickMulti = { pickMultiPdf(it) },
                    onBack      = { screen = PdfScreen.HOME }
                )
                PdfScreen.SPLIT -> SplitScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickPdf   = { cb -> pickPdf { b64, name ->
                        addRecent(name, "pdf", "${b64.length * 3 / 4 / 1024} KB"); cb(b64, name)
                    }},
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.COMPRESS -> CompressScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickPdf   = { cb -> pickPdf { b64, name ->
                        addRecent(name, "pdf", "${b64.length * 3 / 4 / 1024} KB"); cb(b64, name)
                    }},
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.PDF_TO_IMG -> PdfToImgScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickPdf   = { cb -> pickPdf { b64, name ->
                        addRecent(name, "pdf", "${b64.length * 3 / 4 / 1024} KB"); cb(b64, name)
                    }},
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.IMG_TO_PDF -> ImgToPdfScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickMulti = { pickMultiImg(it) },
                    onBack      = { screen = PdfScreen.HOME }
                )
                PdfScreen.RESIZE -> ResizeScreen(
                    controller  = controller,
                    engineState = engineState,
                    onPickImg   = { cb -> pickImg { b64, name ->
                        addRecent(name, "img", "${b64.length * 3 / 4 / 1024} KB"); cb(b64, name)
                    }},
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.SCAN_TO_PDF -> ScanToPdfScreen(
                    onBack = { screen = PdfScreen.HOME }
                )
                PdfScreen.CALCULATOR -> ScientificCalculatorScreen(
                    onBack = { screen = PdfScreen.HOME }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN  — WPS exact layout
// Top bar  |  Tool grid (2 rows × 4)  |  Recent/Starred list  |  Bottom nav
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeScreen(
    recentItems: List<RecentItem>,
    isScanning:  Boolean,
    activeTab:   HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onToolClick: (PdfScreen) -> Unit,
    onRefresh:   () -> Unit,
    onBack:      () -> Unit,
) {
    val context = LocalContext.current
    var shortcutTarget by remember { mutableStateOf<QuickTool?>(null) }

    Box(Modifier.fillMaxSize().background(BG)) {

        Column(Modifier.fillMaxSize().padding(bottom = 60.dp)) {  // 60dp = bottom nav height

            // ── WPS-style top bar ─────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BG2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar circle (like WPS user icon)
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(INDIGO.copy(0.25f), CircleShape)
                            .border(1.5.dp, INDIGO.copy(0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("R", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = INDIGO2)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("RasFocus PDF", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WHITE)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Scanning spinner
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp).padding(end = 4.dp),
                            color       = INDIGO2,
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Search, "Search", tint = MUTED, modifier = Modifier.size(20.dp))
                    }
                    // Refresh button — re-scan device PDFs
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MUTED, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Scrollable content ────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

                // ── Tool grid section (WPS 2 rows × 4) ───────────────────────
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(BG2)
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    // Row 1
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TOP_TOOLS.forEach { tool ->
                            WpsToolCell(
                                tool        = tool,
                                onClick     = { onToolClick(tool.screen) },
                                onLongClick = { if (tool.shortcutId.isNotEmpty()) shortcutTarget = tool },
                                modifier    = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Row 2
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BOTTOM_TOOLS.forEach { tool ->
                            WpsToolCell(
                                tool        = tool,
                                onClick     = { onToolClick(tool.screen) },
                                onLongClick = { if (tool.shortcutId.isNotEmpty()) shortcutTarget = tool },
                                modifier    = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Thin separator ────────────────────────────────────────────
                Spacer(Modifier.height(8.dp))

                // ── Recent / Starred tab row (WPS style) ──────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WpsHomeTabItem("Recent", activeTab == HomeTab.RECENT, Modifier.weight(1f)) {
                        onTabChange(HomeTab.RECENT)
                    }
                    WpsHomeTabItem("Starred", activeTab == HomeTab.TOOLS, Modifier.weight(1f)) {
                        onTabChange(HomeTab.TOOLS)
                    }
                }

                // ── File list ─────────────────────────────────────────────────
                val today    = System.currentTimeMillis()
                val todayMs  = today - (today % 86_400_000)
                val starredMode = activeTab == HomeTab.TOOLS

                val displayed = if (starredMode)
                    recentItems.filter { it.starred }
                else
                    recentItems

                if (displayed.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(36.dp),
                                    color       = INDIGO2,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Device scan করা হচ্ছে...", fontSize = 12.sp, color = MUTED)
                            } else {
                                Text("📂", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (starredMode) "কোনো starred ফাইল নেই"
                                    else "কোনো PDF পাওয়া যায়নি\nRefresh বাটন চাপুন",
                                    fontSize  = 13.sp,
                                    color     = MUTED,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Date group header "Today"
                    if (!starredMode && displayed.any { it.time >= todayMs }) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Today", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MUTED)
                            Icon(Icons.Default.FilterList, "filter", tint = MUTED, modifier = Modifier.size(16.dp))
                        }
                    }

                    displayed.take(50).forEach { item ->
                        WpsRecentRow(
                            item        = item,
                            onStarToggle = { item.starred = !item.starred },
                            onOpen       = { onToolClick(PdfScreen.VIEWER) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        // ── WPS bottom navigation bar ─────────────────────────────────────────
        WpsBottomNav(
            active   = activeTab,
            onChange = { onTabChange(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── FAB (WPS red "+" button) ──────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 76.dp)
        ) {
            FloatingActionButton(
                onClick           = { onToolClick(PdfScreen.VIEWER) },
                containerColor    = T_RED,
                contentColor      = Color.White,
                modifier          = Modifier.size(52.dp),
                shape             = CircleShape
            ) {
                Icon(Icons.Default.Add, "Open PDF", modifier = Modifier.size(26.dp))
            }
        }
    }

    // ── Shortcut bottom sheet ─────────────────────────────────────────────────
    shortcutTarget?.let { tool ->
        ShortcutSheet(
            tool          = tool,
            onDismiss     = { shortcutTarget = null },
            onAddShortcut = { addToolShortcut(context, tool); shortcutTarget = null }
        )
    }
}

// ── WPS tool cell: icon box + label below ─────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpsToolCell(
    tool:        QuickTool,
    onClick:     () -> Unit,
    onLongClick: () -> Unit = {},
    modifier:    Modifier   = Modifier,
) {
    Column(
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Colored rounded square icon — WPS style
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tool.color.copy(alpha = 0.18f))
                .border(1.dp, tool.color.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(tool.icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            tool.label,
            fontSize   = 9.sp,
            fontWeight = FontWeight.Medium,
            color      = WHITE.copy(0.85f),
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            lineHeight = 12.sp
        )
    }
}

// ── WPS-style recent row  ─────────────────────────────────────────────────────
@Composable
private fun WpsRecentRow(
    item:         RecentItem,
    onStarToggle: () -> Unit,
    onOpen:       () -> Unit = {},
) {
    val context = LocalContext.current
    val isPdf = item.type == "pdf"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                // URI থেকে directly PdfViewerActivity open করো — super fast, no base64
                val uri = item.uri
                if (uri != null) {
                    val intent = android.content.Intent(context, PdfViewerActivity::class.java).apply {
                        action = android.content.Intent.ACTION_VIEW
                        setDataAndType(uri, "application/pdf")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    onOpen()
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon box
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isPdf) T_RED.copy(0.15f) else T_BLUE.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPdf) "📄" else "🖼️", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WHITE,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            val timeLabel = run {
                val diff = System.currentTimeMillis() - item.time
                when {
                    diff < 60_000L        -> "এইমাত্র"
                    diff < 3_600_000L     -> "${diff / 60_000} min ago"
                    diff < 86_400_000L    -> "${diff / 3_600_000} hr ago"
                    else                  -> {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.time }
                        "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH)+1}"
                    }
                }
            }
            Text(
                "${item.size}  ·  $timeLabel",
                fontSize = 10.sp,
                color    = MUTED
            )
        }
        // Star icon
        IconButton(onClick = onStarToggle, modifier = Modifier.size(30.dp)) {
            Icon(
                if (item.starred) Icons.Default.Star else Icons.Default.StarBorder,
                "Star",
                tint     = if (item.starred) T_YELLOW else MUTED,
                modifier = Modifier.size(18.dp)
            )
        }
        // Three-dot menu
        IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.MoreVert, "More", tint = MUTED, modifier = Modifier.size(16.dp))
        }
    }
}

// ── WPS home tab chip  ────────────────────────────────────────────────────────
@Composable
private fun WpsHomeTabItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) INDIGO.copy(0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color      = if (selected) INDIGO2 else MUTED
            )
            if (selected) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(INDIGO2, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

// ── WPS bottom navigation bar ─────────────────────────────────────────────────
@Composable
private fun WpsBottomNav(active: HomeTab, onChange: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple(HomeTab.RECENT,    Icons.Default.AccessTime,  "Recent"),
        Triple(HomeTab.FILES,     Icons.Default.FolderOpen,  "Files"),
        Triple(HomeTab.TOOLS,     Icons.Default.Apps,        "Tools"),
        Triple(HomeTab.TEMPLATES, Icons.Default.Article,     "Templates"),
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(BG2)
            .border(BorderStroke(0.5.dp, BORDER), RoundedCornerShape(0.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        items.forEach { (tab, icon, label) ->
            val isActive = active == tab
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onChange(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon, label,
                    tint     = if (isActive) INDIGO2 else MUTED,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize   = 9.sp,
                    color      = if (isActive) INDIGO2 else MUTED,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF VIEWER SCREEN  — WPS style
// Full page, tap = show/hide controls, scroll = auto-hide controls
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PdfViewerScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickPdf:   ((String, String) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls while scrolling
    var lastPage by remember { mutableStateOf(engineState.currentPage) }
    LaunchedEffect(engineState.currentPage) {
        if (engineState.currentPage != lastPage && engineState.totalPages > 0) {
            controlsVisible = false
            lastPage = engineState.currentPage
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {

        if (engineState.totalPages == 0) {
            // ── Empty state ───────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxSize()
                    .background(BG)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(80.dp)
                        .background(T_RED.copy(0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, T_RED.copy(0.25f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("📄", fontSize = 36.sp) }

                Spacer(Modifier.height(20.dp))
                Text("PDF ফাইল খুলুন", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WHITE)
                Spacer(Modifier.height(6.dp))
                Text("Pinch to zoom  ·  Swipe to scroll", fontSize = 11.sp, color = MUTED)
                Spacer(Modifier.height(28.dp))

                GradientButton(
                    text    = "📂  Select PDF",
                    brush   = RedGrad,
                    onClick = { onPickPdf { b64, name -> controller.loadPdfInViewer(b64, name) } }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, BORDER)
                ) {
                    Text("← Back", fontSize = 12.sp, color = MUTED)
                }
            }

        } else {
            // ── Full-screen page view ─────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { controlsVisible = !controlsVisible }
            ) {
                PdfViewerWebView(controller = controller, modifier = Modifier.fillMaxSize())
            }

            // ── Floating top bar (WPS: filename + page number + open icon) ────
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = slideInVertically { -it } + fadeIn(),
                exit     = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BG.copy(0.93f))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = WHITE, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 2.dp)) {
                        Text(
                            engineState.fileName.ifBlank { "PDF Viewer" },
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = WHITE,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = { onPickPdf { b64, name -> controller.loadPdfInViewer(b64, name) } },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, "Open", tint = INDIGO2, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Loading spinner
            if (engineState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(42.dp),
                        color       = INDIGO2,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED TOOL SCREEN CHROME
// Top bar with back arrow + title (WPS file name bar style)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ToolTopBar(title: String, subtitle: String, accent: Color = INDIGO, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BG2)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ArrowBack, "Back", tint = WHITE, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title,    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WHITE)
            Text(subtitle, fontSize = 9.sp,  color = MUTED)
        }
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MERGE SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MergeScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickMulti: ((List<Pair<String,String>>) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var files by remember { mutableStateOf(listOf<Pair<String,String>>()) }
    val canMerge = files.size > 1 && !engineState.isLoading

    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("PDF Merge", "Multiple → Single", T_GREEN, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("📂  PDFs নির্বাচন করুন (২+)") { onPickMulti { results -> files = files + results } }

            if (files.isNotEmpty()) {
                SectionLabel("📎  Selected (${files.size})")
                files.forEachIndexed { i, (_, name) ->
                    FileChip(name = name, onRemove = { files = files.toMutableList().also { it.removeAt(i) } })
                }
            }
            LoadingOverlay(engineState.isLoading)
            GradientButton(
                text    = when { files.size < 2 -> "২টি PDF select করুন"; engineState.isLoading -> "Merging..."; else -> "🔀  Merge" },
                brush   = GreenGrad,
                enabled = canMerge,
                onClick = {
                    val json = "[" + files.joinToString(",") { (b64, name) -> """{"base64":"$b64","name":"${name.replace("\"","'")}"}""" } + "]"
                    controller.mergePdfs(json)
                }
            )
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.saveMergePdfResult() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SPLIT SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SplitScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickPdf:   ((String, String) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var loaded   by remember { mutableStateOf(false) }
    var fromPage by remember { mutableStateOf("1") }
    var toPage   by remember { mutableStateOf("1") }

    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("PDF Split", "পাতার পরিসীমা select করুন", T_PINK, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("📂  PDF নির্বাচন করুন") {
                onPickPdf { b64, name ->
                    controller.loadPdfForSplit(b64, name)
                    loaded = true
                    toPage = engineState.totalPages.toString()
                }
            }
            if (loaded) {
                SectionLabel("📄  ${engineState.fileName}")
                Text("মোট পাতা: ${engineState.totalPages}", fontSize = 11.sp, color = MUTED, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromPage, onValueChange = { fromPage = it },
                        label = { Text("From", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = toolFieldColors(), singleLine = true
                    )
                    OutlinedTextField(
                        value = toPage, onValueChange = { toPage = it },
                        label = { Text("To", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = toolFieldColors(), singleLine = true
                    )
                }
            }
            LoadingOverlay(engineState.isLoading)
            GradientButton(
                text    = when { !loaded -> "PDF select করুন"; engineState.isLoading -> "Splitting..."; else -> "✂️  Split" },
                brush   = PinkGrad,
                enabled = loaded && fromPage.isNotEmpty() && toPage.isNotEmpty() && !engineState.isLoading,
                onClick = {
                    val from = fromPage.toIntOrNull() ?: 1
                    val to   = toPage.toIntOrNull()   ?: engineState.totalPages
                    if (from in 1..engineState.totalPages && to in from..engineState.totalPages)
                        controller.splitPdf(from, to)
                }
            )
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.saveSplitPdfResult() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPRESS SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CompressScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickPdf:   ((String, String) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var loaded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("Compress PDF", "সাইজ কমান", T_YELLOW, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("📂  PDF নির্বাচন করুন") {
                onPickPdf { b64, name -> controller.loadPdfForCompress(b64, name); loaded = true }
            }
            if (loaded) {
                SectionLabel("📄  ${engineState.fileName}")
                Text("সাইজ: ${engineState.fileSizeKb} KB", fontSize = 11.sp, color = WHITE, fontWeight = FontWeight.Bold)
            }
            LoadingOverlay(engineState.isLoading)
            GradientButton(
                text    = when { !loaded -> "PDF select করুন"; engineState.isLoading -> "Compressing..."; else -> "🗜️  Compress" },
                brush   = YellowGrad,
                enabled = loaded && !engineState.isLoading,
                onClick = { controller.compressPdf(75) }
            )
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.saveCompressPdfResult() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF → IMAGE SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PdfToImgScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickPdf:   ((String, String) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("PDF → Images", "সব পাতা extract করুন", T_BLUE, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("📂  PDF নির্বাচন করুন") {
                onPickPdf { b64, name -> controller.pdfToImages(b64, name) }
            }
            LoadingOverlay(engineState.isLoading)
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.savePdfToImagesResults() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMAGE → PDF SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ImgToPdfScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickMulti: ((List<Pair<String,String>>) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var files by remember { mutableStateOf(listOf<Pair<String,String>>()) }
    val canConvert = files.isNotEmpty() && !engineState.isLoading

    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("Image → PDF", "একাধিক ছবি → PDF", T_PINK, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("🖼️  ছবি select করুন") { onPickMulti { results -> files = files + results } }
            if (files.isNotEmpty()) {
                SectionLabel("🖼️  Selected (${files.size})")
                files.forEachIndexed { i, (_, name) ->
                    FileChip(name = name, onRemove = { files = files.toMutableList().also { it.removeAt(i) } })
                }
            }
            LoadingOverlay(engineState.isLoading)
            GradientButton(
                text    = when { files.isEmpty() -> "ছবি select করুন"; engineState.isLoading -> "Creating PDF..."; else -> "📄  Create PDF" },
                brush   = PinkGrad,
                enabled = canConvert,
                onClick = {
                    val json = "[" + files.joinToString(",") { (b64, name) -> """{"base64":"$b64","name":"${name.replace("\"","'")}"}""" } + "]"
                    controller.imagesToPdf(json)
                }
            )
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.saveImagesToPdfResult() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESIZE SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ResizeScreen(
    controller:  PdfEngineController,
    engineState: PdfEngineState,
    onPickImg:   ((String, String) -> Unit) -> Unit,
    onBack:      () -> Unit,
) {
    var fileName by remember { mutableStateOf("") }
    var loaded   by remember { mutableStateOf(false) }
    var imgB64   by remember { mutableStateOf("") }
    var width    by remember { mutableStateOf("300") }
    var height   by remember { mutableStateOf("300") }

    val presets = listOf(
        Triple("👤 Job Photo",  "300",  "300"),
        Triple("✍️ Signature", "300",  "80"),
        Triple("📷 800×600",   "800",  "600"),
        Triple("📱 FullHD",    "1080", "1920"),
    )

    Column(Modifier.fillMaxSize().background(BG)) {
        ToolTopBar("Resize Photo", "সাইজ পরিবর্তন করুন", T_PURPLE, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PickButton("🖼️  ছবি নির্বাচন করুন") {
                onPickImg { b64, name -> imgB64 = b64; fileName = name; loaded = true }
            }
            if (loaded) SectionLabel("🖼️  $fileName")

            SectionLabel("⚡  Presets")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (label, w, h) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(T_PURPLE.copy(0.12f))
                                    .border(1.dp, T_PURPLE.copy(0.25f), RoundedCornerShape(10.dp))
                                    .clickable { width = w; height = h }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = WHITE, textAlign = TextAlign.Center)
                            }
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionLabel("📏  Custom Size")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = width, onValueChange = { width = it },
                    label = { Text("Width px", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = toolFieldColors(), singleLine = true
                )
                OutlinedTextField(
                    value = height, onValueChange = { height = it },
                    label = { Text("Height px", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = toolFieldColors(), singleLine = true
                )
            }

            LoadingOverlay(engineState.isLoading)
            GradientButton(
                text    = when { !loaded -> "ছবি select করুন"; engineState.isLoading -> "Resizing..."; else -> "📐  Resize" },
                brush   = PurpleGrad,
                enabled = loaded && width.isNotEmpty() && height.isNotEmpty() && !engineState.isLoading,
                onClick = { controller.resizeImage(imgB64, fileName, width.toIntOrNull() ?: 300, height.toIntOrNull() ?: 300) }
            )
            if (engineState.operationResult.isNotEmpty() || engineState.errorMsg.isNotEmpty()) {
                PdfStatusCard(engineState = engineState, onSave = { controller.saveResizeResult() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHORTCUT BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShortcutSheet(tool: QuickTool, onDismiss: () -> Unit, onAddShortcut: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, onClick = onDismiss
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(CARD2, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, onClick = {}
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.width(40.dp).height(4.dp).background(MUTED.copy(0.4f), RoundedCornerShape(2.dp)))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .size(54.dp)
                        .background(tool.color.copy(0.18f), RoundedCornerShape(15.dp))
                        .border(1.dp, tool.color.copy(0.35f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(tool.icon, fontSize = 26.sp) }
                Column {
                    Text(tool.label.replace("\n", " "), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WHITE)
                    Text("Home screen এ shortcut add করুন", fontSize = 11.sp, color = MUTED)
                }
            }

            HorizontalDivider(color = BORDER, thickness = 0.5.dp)

            Button(
                onClick  = onAddShortcut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = tool.color.copy(0.9f))
            ) {
                Icon(Icons.Default.AddCircle, "Add", modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Home Screen এ Add করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("বাতিল", fontSize = 12.sp, color = MUTED)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHORTCUT HELPER
// ─────────────────────────────────────────────────────────────────────────────
private fun addToolShortcut(context: android.content.Context, tool: QuickTool) {
    val intent = Intent(context, try { Class.forName("com.rasel.RasFocus.MainActivity") } catch (_: Exception) { context.javaClass }).apply {
        action = "com.rasel.rasfocus.PDF_TOOL"
        putExtra("screen", tool.shortcutId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val bmp = buildShortcutIcon(context, tool)
    val shortcut = ShortcutInfoCompat.Builder(context, tool.shortcutId)
        .setShortLabel(tool.label.replace("\n", " "))
        .setLongLabel("PDF Tools — ${tool.label.replace("\n", " ")}")
        .setIcon(IconCompat.createWithBitmap(bmp))
        .setIntent(intent)
        .build()
    if (!ShortcutManagerCompat.requestPinShortcut(context, shortcut, null))
        Toast.makeText(context, "এই launcher shortcut support করে না", Toast.LENGTH_SHORT).show()
}

private fun buildShortcutIcon(context: android.content.Context, tool: QuickTool): Bitmap {
    val size   = (context.resources.displayMetrics.density * 108).toInt().coerceAtLeast(108)
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220,
            (tool.color.red * 255).toInt(), (tool.color.green * 255).toInt(), (tool.color.blue * 255).toInt())
    }
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = Paint.Align.CENTER }
    canvas.drawText(tool.icon, size / 2f, size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    return bmp
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED UI COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MUTED,
        modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun PickButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CARD)
            .border(1.5.dp, INDIGO.copy(0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = INDIGO2)
    }
}

@Composable
private fun FileChip(name: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(8.dp))
            .border(1.dp, BORDER, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 11.sp, color = WHITE, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, "Remove", tint = T_RED, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GradientButton(text: String, brush: Brush, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (enabled) brush else Brush.linearGradient(listOf(MUTED, MUTED)), RoundedCornerShape(12.dp)),
        shape  = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor         = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else MUTED)
    }
}

@Composable
private fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(CARD.copy(0.9f), RoundedCornerShape(10.dp))
                .border(1.dp, INDIGO.copy(0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = INDIGO2, strokeWidth = 2.dp)
                Text("প্রসেসিং...", fontSize = 12.sp, color = WHITE, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PdfStatusCard(engineState: PdfEngineState, onSave: () -> Unit) {
    val isError = engineState.errorMsg.isNotEmpty()
    val accent  = if (isError) T_RED else T_GREEN
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(0.10f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (isError) "✗ Error" else "✓ Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(
                if (isError) engineState.errorMsg else engineState.operationResult,
                fontSize = 10.sp, color = MUTED, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (!isError) {
            Button(
                onClick  = onSave,
                modifier = Modifier.height(34.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = T_GREEN)
            ) {
                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BG)
            }
        }
    }
}

@Composable
private fun toolFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = INDIGO,
    unfocusedBorderColor    = BORDER,
    focusedTextColor        = WHITE,
    unfocusedTextColor      = WHITE,
    focusedLabelColor       = INDIGO2,
    unfocusedLabelColor     = MUTED,
    focusedContainerColor   = BG2,
    unfocusedContainerColor = BG2,
)

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS — defined in FileUtils.kt (same package)
// ─────────────────────────────────────────────────────────────────────────────
// uriToBase64(context, uri) → String?
// getFileName(context, uri) → String

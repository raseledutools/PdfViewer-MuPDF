package com.rasel.pdfviewer

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.artifex.mupdf.viewer.MuPDFCore
import kotlinx.coroutines.*
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// COLORS — deep dark theme, premium feel
// ─────────────────────────────────────────────────────────────────────────────
private val BG_DEEP     = Color(0xFF0A0A0F)
private val BG_SURFACE  = Color(0xFF111118)
private val BG_CARD     = Color(0xFF1C1C28)
private val BORDER_CLR  = Color(0xFF252535)
private val TEXT_MUTED  = Color(0xFF55556A)
private val TEXT_WHITE  = Color(0xFFF0EFFF)
private val ACCENT      = Color(0xFF6C63FF)
private val ACCENT2     = Color(0xFF8B83FF)
private val ERR_CLR     = Color(0xFFFF5C5C)

// Highlight colors
private val HL_YELLOW = Color(0xAAFFE066)
private val HL_GREEN  = Color(0xAA4AE08A)
private val HL_BLUE   = Color(0xAA4DA6FF)
private val HL_PINK   = Color(0xAAFF6B9D)

// Max bitmap dimension — prevents OOM on extreme zoom + large page
private const val MAX_RENDER_DIM = 4096

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────
data class PageBitmap(
    val bitmap:          Bitmap,
    val pageIndex:       Int,
    val widthPx:         Int,
    val heightPx:        Int,
    val renderedAtScale: Float = 1f,
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class PdfViewerActivity : ComponentActivity() {

    private val uriState      = mutableStateOf<Uri?>(null)
    private val fileNameState = mutableStateOf("PDF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while reading
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadFromIntent(intent)

        setContent {
            MuPdfViewer(
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
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null ->
                intent.data
            intent?.hasExtra("pdf_uri") == true ->
                Uri.parse(intent.getStringExtra("pdf_uri"))
            else -> null
        }

        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
        }

        uriState.value      = uri
        fileNameState.value = uri?.let { getDisplayName(it) } ?: "PDF"
    }

    private fun getDisplayName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE — MuPDF vector rendering
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MuPdfViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val density  = LocalDensity.current

    val pages        = remember { mutableStateListOf<PageBitmap?>() }
    var totalPages   by remember { mutableIntStateOf(0) }
    var currentPage  by remember { mutableIntStateOf(1) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMsg     by remember { mutableStateOf("") }

    // Controls auto-hide (WPS/Adobe style — clean reading)
    var controlsVisible by remember { mutableStateOf(false) }
    var autoHideJob     by remember { mutableStateOf<Job?>(null) }

    // Document-level zoom — shared across ALL pages
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // MuPDF core instance — created once, kept alive for the whole session
    var muCore by remember { mutableStateOf<MuPDFCore?>(null) }

    val listState = rememberLazyListState()
    val screenW   = context.resources.displayMetrics.widthPixels

    // ── Auto-hide controls ─────────────────────────────────────────────────
    fun scheduleAutoHide() {
        autoHideJob?.cancel()
        controlsVisible = true
        autoHideJob = scope.launch {
            delay(3_500)
            controlsVisible = false
        }
    }
    fun toggleControls() {
        if (controlsVisible) { autoHideJob?.cancel(); controlsVisible = false }
        else scheduleAutoHide()
    }

    // ── Open PDF with MuPDF ────────────────────────────────────────────────
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "PDF পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true

        withContext(Dispatchers.IO) {
            try {
                // MuPDFCore accepts: file path OR content URI
                val core = if (uri.scheme == "content") {
                    // Copy content URI to a temp file for MuPDF
                    val tmpFile = java.io.File(context.cacheDir, "mupdf_tmp.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    MuPDFCore(tmpFile.absolutePath)
                } else {
                    MuPDFCore(uri.path ?: "")
                }

                muCore = core
                val count = core.countPages()

                withContext(Dispatchers.Main) {
                    pages.clear()
                    repeat(count) { pages.add(null) }
                    totalPages  = count
                    currentPage = 1
                    isLoading   = false
                }

                // Render pages — MuPDF renders true vectors (NOT bitmap-stretched)
                for (i in 0 until count) {
                    val pageSize = core.getPageSize(i)
                    val origW    = pageSize.x.coerceAtLeast(1f)
                    val origH    = pageSize.y.coerceAtLeast(1f)
                    val pdfScale = screenW.toFloat() / origW
                    val bmpW     = screenW
                    val bmpH     = (origH * pdfScale).roundToInt().coerceAtLeast(1)

                    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)

                    // MuPDF renders at exact pixel resolution — vectors stay sharp
                    core.drawPage(bmp, i, bmpW, bmpH, 0, 0, bmpW, bmpH, null)

                    withContext(Dispatchers.Main) {
                        if (i < pages.size)
                            pages[i] = PageBitmap(bmp, i, bmpW, bmpH)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg  = "PDF খোলা যায়নি: ${e.message}"
                }
            }
        }
    }

    // ── Re-render at higher resolution on zoom (sharp zoom) ───────────────
    suspend fun reRenderSharper(pageIndex: Int, targetScale: Float) {
        val core = muCore ?: return
        withContext(Dispatchers.IO) {
            try {
                val pageSize = core.getPageSize(pageIndex)
                val origW    = pageSize.x.coerceAtLeast(1f)
                val origH    = pageSize.y.coerceAtLeast(1f)
                val baseScl  = screenW.toFloat() / origW

                var bmpW = (screenW * targetScale).roundToInt()
                var bmpH = (origH * baseScl * targetScale).roundToInt().coerceAtLeast(1)

                // Safety cap
                if (bmpW > MAX_RENDER_DIM || bmpH > MAX_RENDER_DIM) {
                    val shrink = MAX_RENDER_DIM.toFloat() / maxOf(bmpW, bmpH)
                    bmpW = (bmpW * shrink).roundToInt().coerceAtLeast(1)
                    bmpH = (bmpH * shrink).roundToInt().coerceAtLeast(1)
                }

                val sharpBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                sharpBmp.eraseColor(AColor.WHITE)
                // MuPDF re-renders from vectors at the new resolution — perfectly sharp
                core.drawPage(sharpBmp, pageIndex, bmpW, bmpH, 0, 0, bmpW, bmpH, null)

                withContext(Dispatchers.Main) {
                    val old = pages.getOrNull(pageIndex)
                    if (old != null) {
                        val oldBmp = old.bitmap
                        pages[pageIndex] = old.copy(
                            bitmap          = sharpBmp,
                            widthPx         = bmpW,
                            heightPx        = bmpH,
                            renderedAtScale = targetScale
                        )
                        if (!oldBmp.isRecycled) oldBmp.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // Track current page
    val visibleIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(visibleIdx) {
        if (totalPages > 0) currentPage = visibleIdx + 1
    }

    // Debounced re-render on zoom settle
    LaunchedEffect(scale, visibleIdx) {
        delay(300)
        val cur = pages.getOrNull(visibleIdx) ?: return@LaunchedEffect
        if (scale > 1.05f && scale > cur.renderedAtScale * 1.4f) {
            reRenderSharper(visibleIdx, scale)
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            muCore?.onDestroy()
            pages.forEach { it?.bitmap?.let { b -> if (!b.isRecycled) b.recycle() } }
        }
    }

    // ── System bar control (immersive reading) ────────────────────────────
    val activityWindow = (context as? Activity)?.window
    val view           = LocalView.current
    DisposableEffect(activityWindow) {
        val win = activityWindow
        if (win != null) WindowCompat.setDecorFitsSystemWindows(win, false)
        onDispose {
            if (win != null)
                WindowInsetsControllerCompat(win, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(controlsVisible, activityWindow) {
        val win = activityWindow ?: return@LaunchedEffect
        val ctrl = WindowInsetsControllerCompat(win, view)
        if (controlsVisible) {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        } else {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(BG_DEEP)) {

        when {
            isLoading          -> LoadingView(fileName)
            errorMsg.isNotEmpty() -> ErrorView(errorMsg, onClose)
            else -> {
                // Document-level zoom container
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange  = event.calculatePan()
                                        val newScale = (scale * zoomChange).coerceIn(1f, 10f)
                                        offsetX = if (newScale > 1f) offsetX + panChange.x else 0f
                                        offsetY = if (newScale > 1f) offsetY + panChange.y else 0f
                                        scale   = newScale
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { toggleControls() },
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.2f) {
                                        scale = 1f; offsetX = 0f; offsetY = 0f
                                    } else {
                                        val ns = 2.5f
                                        offsetX = (size.width / 2f - tapOffset.x) * (ns - 1f)
                                        offsetY = (size.height / 2f - tapOffset.y) * (ns - 1f)
                                        scale = ns
                                    }
                                }
                            )
                        }
                        .graphicsLayer(
                            scaleX       = scale,
                            scaleY       = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                            clip         = false
                        )
                ) {
                    LazyColumn(
                        state               = listState,
                        modifier            = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding      = PaddingValues(top = 0.dp, bottom = 72.dp)
                    ) {
                        itemsIndexed(pages) { _, pageData ->
                            if (pageData == null) {
                                PageSkeleton()
                            } else {
                                PdfPage(pageData)
                            }
                        }
                    }
                }

                // ── Top bar ──────────────────────────────────────────────
                AnimatedVisibility(
                    visible  = controlsVisible,
                    enter    = slideInVertically { -it } + fadeIn(),
                    exit     = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopAppBar(
                        fileName    = fileName,
                        currentPage = currentPage,
                        totalPages  = totalPages,
                        onBack      = onClose
                    )
                }

                // ── Page indicator ───────────────────────────────────────
                AnimatedVisibility(
                    visible  = controlsVisible,
                    enter    = slideInVertically { it } + fadeIn(),
                    exit     = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PageIndicator(currentPage, totalPages)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF PAGE ITEM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PdfPage(data: PageBitmap) {
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(0.dp))
            .background(Color.White)
    ) {
        Image(
            bitmap             = data.bitmap.asImageBitmap(),
            contentDescription = "Page ${data.pageIndex + 1}",
            contentScale       = ContentScale.FillWidth,
            modifier           = Modifier.fillMaxWidth().wrapContentHeight()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SKELETON PLACEHOLDER — shows while page is rendering
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageSkeleton() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(BG_CARD),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                color        = ACCENT,
                modifier     = Modifier.size(28.dp),
                strokeWidth  = 2.5.dp
            )
            Text("Rendering...", color = TEXT_MUTED, fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOADING VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoadingView(fileName: String) {
    Box(Modifier.fillMaxSize().background(BG_DEEP), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            // Animated icon
            Box(
                Modifier
                    .size(80.dp)
                    .background(BG_CARD, CircleShape)
                    .border(1.dp, BORDER_CLR, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint     = ACCENT,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text       = fileName,
                color      = TEXT_WHITE,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Center
            )

            CircularProgressIndicator(
                color       = ACCENT,
                strokeWidth = 3.dp,
                modifier    = Modifier.size(40.dp)
            )

            Text("MuPDF — Vector Rendering", color = TEXT_MUTED, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ErrorView(message: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG_DEEP), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = ERR_CLR, modifier = Modifier.size(48.dp))
            Text(message, color = TEXT_WHITE, fontSize = 14.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onClose,
                colors  = ButtonDefaults.buttonColors(containerColor = ACCENT)
            ) {
                Text("বন্ধ করুন", color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP APP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopAppBar(fileName: String, currentPage: Int, totalPages: Int, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BG_DEEP.copy(alpha = 0.95f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Back", tint = TEXT_WHITE)
        }

        Column(Modifier.weight(1f)) {
            Text(
                text       = fileName,
                color      = TEXT_WHITE,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = "$currentPage / $totalPages",
                color    = TEXT_MUTED,
                fontSize = 11.sp
            )
        }

        Box(
            Modifier
                .background(ACCENT.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("MuPDF", color = ACCENT2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAGE INDICATOR (bottom pill)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageIndicator(currentPage: Int, totalPages: Int) {
    Box(
        Modifier
            .padding(bottom = 24.dp)
            .background(BG_DEEP.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, BORDER_CLR, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text     = "পৃষ্ঠা $currentPage / $totalPages",
            color    = TEXT_WHITE,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

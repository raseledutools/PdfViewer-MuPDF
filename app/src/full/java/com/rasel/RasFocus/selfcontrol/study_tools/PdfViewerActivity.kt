package com.rasel.RasFocus.selfcontrol.study_tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.*
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// COLORS
// ─────────────────────────────────────────────────────────────────────────────
private val VA_BG      = Color(0xFF0A0A0F)
private val VA_BG2     = Color(0xFF111118)
private val VA_CARD2   = Color(0xFF1C1C28)
private val VA_BORDER  = Color(0xFF252535)
private val VA_MUTED   = Color(0xFF55556A)
private val VA_WHITE   = Color(0xFFF0EFFF)
private val VA_INDIGO  = Color(0xFF6C63FF)
private val VA_INDIGO2 = Color(0xFF8B83FF)
private val VA_RED     = Color(0xFFFF5C5C)

private val HL_YELLOW  = Color(0xAAFFE066)
private val HL_GREEN   = Color(0xAA4AE08A)
private val HL_BLUE    = Color(0xAA4DA6FF)
private val HL_PINK    = Color(0xAAFF6B9D)

private const val MAX_RENDER_DIM = 4096

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────
data class PageData(
    val textPage:        Any?   = null,
    val pageIndex:       Int,
    val widthPx:         Int,
    val heightPx:        Int,
    val renderedAtScale: Float  = 1f,
    val bitmap:          Bitmap? = null
)

data class Highlight(
    val pageIndex: Int,
    val rects:     List<RectF>,
    val color:     Color,
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class PdfViewerActivity : ComponentActivity() {

    private val uriState      = mutableStateOf<Uri?>(null)
    private val fileNameState = mutableStateOf("PDF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadFromIntent(intent)
        setContent {
            NativePdfViewer(
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
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }
        uriState.value      = uri
        fileNameState.value = uri?.let { getFileNameFromUri(it) } ?: "PDF"
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NativePdfViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current

    // pdfium core — one instance per composable lifecycle
    val pdfCore = remember { PdfiumCore(context) }

    val pages       = remember { mutableStateListOf<PageData?>() }
    val bitmapCache = remember {
        object : android.util.LruCache<Int, Bitmap>(4) {
            override fun entryRemoved(evicted: Boolean, key: Int, old: Bitmap, new: Bitmap?) {
                if (evicted) scope.launch(Dispatchers.Main) {
                    val cur = pages.getOrNull(key)
                    if (cur?.bitmap === old) pages[key] = cur.copy(bitmap = null, renderedAtScale = 1f)
                    delay(200)
                    if (!old.isRecycled) old.recycle()
                }
            }
        }
    }

    var totalPages      by remember { mutableIntStateOf(0) }
    var currentPage     by remember { mutableIntStateOf(1) }
    var isLoading       by remember { mutableStateOf(true) }
    var errorMsg        by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }

    // zoom state — shared across all pages (not per-page)
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    // highlights
    val highlights    = remember { mutableStateListOf<Highlight>() }
    var showToolbar   by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(HL_YELLOW) }

    // pdfium document handle — kept alive until dispose
    var pdfDoc by remember { mutableStateOf<com.shockwave.pdfium.PdfDocument?>(null) }
    var pdfPfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    val listState = rememberLazyListState()
    val screenW   = context.resources.displayMetrics.widthPixels

    // ── Render one page to bitmap using pdfium ───────────────────────────────
    fun renderPage(doc: com.shockwave.pdfium.PdfDocument, pageIndex: Int, bmpW: Int, bmpH: Int): Bitmap {
        pdfCore.openPage(doc, pageIndex)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AColor.WHITE)
        pdfCore.renderPageBitmap(doc, bmp, pageIndex, 0, 0, bmpW, bmpH, true)
        return bmp
    }

    // ── Load PDF ─────────────────────────────────────────────────────────────
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "PDF পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try { pdfDoc?.let { pdfCore.closeDocument(it) } } catch (_: Exception) {}
            try { pdfPfd?.close() } catch (_: Exception) {}
        }
        pdfDoc = null; pdfPfd = null

        withContext(Dispatchers.IO) {
            try {
                val pfd: ParcelFileDescriptor
                val doc: com.shockwave.pdfium.PdfDocument

                when (uri.scheme) {
                    "file" -> {
                        val file = java.io.File(uri.path ?: throw IllegalStateException("Bad file URI"))
                        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    else -> {
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw IllegalStateException("Cannot open: $uri")
                    }
                }
                doc = pdfCore.newDocument(pfd)

                withContext(Dispatchers.Main) { pdfDoc = doc; pdfPfd = pfd }

                val count = pdfCore.getPageCount(doc)
                withContext(Dispatchers.Main) {
                    pages.clear()
                    repeat(count) { pages.add(null) }
                    totalPages  = count
                    currentPage = 1
                }

                for (i in 0 until count) {
                    pdfCore.openPage(doc, i)
                    val origW = pdfCore.getPageWidthPoint(doc, i).toFloat().coerceAtLeast(1f)
                    val origH = pdfCore.getPageHeightPoint(doc, i).toFloat().coerceAtLeast(1f)
                    val baseS = screenW.toFloat() / origW
                    val bmpW  = screenW
                    val bmpH  = (origH * baseS).roundToInt().coerceAtLeast(1)

                    if (i == 0) {
                        val bmp = renderPage(doc, 0, bmpW, bmpH)
                        bitmapCache.put(0, bmp)
                        withContext(Dispatchers.Main) {
                            pages[0]  = PageData(pageIndex = 0, widthPx = bmpW, heightPx = bmpH,
                                renderedAtScale = 1f, bitmap = bmp)
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (i < pages.size)
                                pages[i] = PageData(pageIndex = i, widthPx = bmpW, heightPx = bmpH)
                        }
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

    // ── Re-render at higher res for sharp zoom ───────────────────────────────
    suspend fun reRenderPageSharper(pageIndex: Int, targetScale: Float) {
        val doc = pdfDoc ?: return
        withContext(Dispatchers.IO) {
            try {
                pdfCore.openPage(doc, pageIndex)
                val origW = pdfCore.getPageWidthPoint(doc, pageIndex).toFloat().coerceAtLeast(1f)
                val origH = pdfCore.getPageHeightPoint(doc, pageIndex).toFloat().coerceAtLeast(1f)
                val baseS = screenW.toFloat() / origW
                var bmpW  = (screenW * targetScale).roundToInt()
                var bmpH  = (origH * baseS * targetScale).roundToInt().coerceAtLeast(1)
                if (bmpW > MAX_RENDER_DIM || bmpH > MAX_RENDER_DIM) {
                    val shrink = MAX_RENDER_DIM.toFloat() / maxOf(bmpW, bmpH)
                    bmpW = (bmpW * shrink).roundToInt().coerceAtLeast(1)
                    bmpH = (bmpH * shrink).roundToInt().coerceAtLeast(1)
                }
                val bmp = renderPage(doc, pageIndex, bmpW, bmpH)
                withContext(Dispatchers.Main) {
                    bitmapCache.put(pageIndex, bmp)
                    val old = pages.getOrNull(pageIndex)
                    if (old != null) pages[pageIndex] = old.copy(
                        bitmap = bmp, widthPx = bmpW, heightPx = bmpH, renderedAtScale = targetScale
                    )
                }
            } catch (_: Exception) {}
        }
    }

    val visibleIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(visibleIdx) { if (totalPages > 0) currentPage = visibleIdx + 1 }

    LaunchedEffect(scale, visibleIdx) {
        delay(300)
        if (scale <= 1.05f) return@LaunchedEffect
        listOf(visibleIdx, visibleIdx + 1, visibleIdx - 1)
            .filter { it in 0 until totalPages }
            .forEach { idx ->
                val cur = pages.getOrNull(idx) ?: return@forEach
                if (scale > cur.renderedAtScale * 1.3f) reRenderPageSharper(idx, scale)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            bitmapCache.evictAll()
            try { pdfDoc?.let { pdfCore.closeDocument(it) } } catch (_: Exception) {}
            try { pdfPfd?.close() } catch (_: Exception) {}
        }
    }

    // ── Immersive bars ───────────────────────────────────────────────────────
    val activityWindow = (context as? Activity)?.window
    val view = LocalView.current
    DisposableEffect(activityWindow) {
        activityWindow?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        onDispose {
            activityWindow?.let {
                WindowInsetsControllerCompat(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(controlsVisible, activityWindow) {
        val w = activityWindow ?: return@LaunchedEffect
        val c = WindowInsetsControllerCompat(w, view)
        if (controlsVisible) c.show(WindowInsetsCompat.Type.systemBars())
        else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        when {
            isLoading           -> LoadingView(fileName)
            errorMsg.isNotEmpty() -> ErrorView(errorMsg, onClose)
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val firstDown  = awaitFirstDown(requireUnconsumed = false)
                                var lastPos    = firstDown.position
                                var lastDist   = 0f
                                var lastMidX   = 0f
                                var twoFinger  = false
                                var lastMoveMs = firstDown.uptimeMillis
                                var lastVelY   = 0f

                                do {
                                    val event   = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        twoFinger = true
                                        val p0   = pressed[0].position
                                        val p1   = pressed[1].position
                                        val dist = kotlin.math.sqrt(
                                            ((p0.x-p1.x)*(p0.x-p1.x) + (p0.y-p1.y)*(p0.y-p1.y)).toDouble()
                                        ).toFloat()
                                        val midX = (p0.x + p1.x) / 2f
                                        if (lastDist > 0f) {
                                            val ns = (scale * (dist / lastDist)).coerceIn(1f, 10f)
                                            val mx = (size.width * (ns - 1f) / 2f).coerceAtLeast(0f)
                                            val px = (midX - lastMidX) + (size.width / 2f - midX) * (ns - scale)
                                            offsetX = if (ns > 1f) (offsetX + px).coerceIn(-mx, mx) else 0f
                                            scale   = ns
                                        }
                                        lastDist = dist; lastMidX = midX
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }

                                    } else if (pressed.size == 1 && !twoFinger) {
                                        val pos   = pressed[0].position
                                        val delta = pos - lastPos
                                        val dtMs  = (pressed[0].uptimeMillis - lastMoveMs).coerceAtLeast(1L)
                                        if (delta.y != 0f) lastVelY = delta.y / dtMs.toFloat()
                                        lastMoveMs = pressed[0].uptimeMillis
                                        if (delta.y != 0f) listState.dispatchRawDelta(-delta.y)
                                        if (scale > 1.05f && delta.x != 0f) {
                                            val mx = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            offsetX = (offsetX + delta.x).coerceIn(-mx, mx)
                                        }
                                        lastPos = pos
                                        pressed[0].consume()
                                    }
                                } while (event.changes.any { it.pressed })

                                // fling
                                if (!twoFinger) {
                                    val velPx = -lastVelY * 1000f
                                    if (kotlin.math.abs(velPx) > 200f) {
                                        scope.launch {
                                            var v = velPx
                                            while (kotlin.math.abs(v) > 30f) {
                                                listState.dispatchRawDelta(v * (16f / 1000f))
                                                v *= 0.95f
                                                delay(16)
                                            }
                                        }
                                    }
                                }
                                twoFinger = false
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    if (showToolbar) { showToolbar = false }
                                },
                                onDoubleTap = { tap ->
                                    if (scale > 1.2f) { scale = 1f; offsetX = 0f }
                                    else {
                                        val ns = 2.5f
                                        val mx = (size.width * (ns - 1f) / 2f).coerceAtLeast(0f)
                                        offsetX = ((size.width / 2f - tap.x) * (ns - 1f)).coerceIn(-mx, mx)
                                        scale   = ns
                                        scope.launch { listState.scrollBy((tap.y - size.height / 2f) * (ns - 1f) / ns) }
                                    }
                                }
                            )
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, clip = false)
                ) {
                    LazyColumn(
                        state             = listState,
                        userScrollEnabled = false,
                        modifier          = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding    = PaddingValues(bottom = 72.dp)
                    ) {
                        items(pages.size) { i ->
                            PdfPageItem(
                                pageData    = pages.getOrNull(i),
                                pageIndex   = i,
                                density     = density,
                                onLoadBitmap = { _ ->
                                    scope.launch(Dispatchers.IO) {
                                        val doc = pdfDoc ?: return@launch
                                        val pd  = pages.getOrNull(i) ?: return@launch
                                        val bmp = renderPage(doc, i, pd.widthPx, pd.heightPx)
                                        bitmapCache.put(i, bmp)
                                        withContext(Dispatchers.Main) {
                                            if (i < pages.size)
                                                pages[i] = pd.copy(bitmap = bmp, renderedAtScale = 1f)
                                        }
                                    }
                                },
                                highlights  = highlights
                            )
                        }
                    }
                }

                // ── Controls overlay ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter   = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit    = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopBar(fileName = fileName, onBack = onClose)
                }

                // Page indicator
                AnimatedVisibility(
                    visible  = controlsVisible,
                    enter    = fadeIn(),
                    exit     = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    if (totalPages > 0) {
                        Box(
                            Modifier.background(VA_BG2.copy(0.85f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("$currentPage / $totalPages", fontSize = 12.sp, color = VA_WHITE)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAGE ITEM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PdfPageItem(
    pageData:    PageData?,
    pageIndex:   Int,
    density:     androidx.compose.ui.unit.Density,
    onLoadBitmap: (Float) -> Unit,
    highlights:  List<Highlight>,
) {
    val imgHeightPx = pageData?.heightPx ?: 0

    Box(Modifier.fillMaxWidth().wrapContentHeight()) {
        if (pageData == null || pageData.bitmap == null) {
            // Trigger load
            LaunchedEffect(pageIndex) { onLoadBitmap(1f) }
            Box(
                Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = VA_INDIGO2, strokeWidth = 2.dp)
            }
        } else {
            Image(
                bitmap             = pageData.bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale       = ContentScale.FillWidth,
                modifier           = Modifier.fillMaxWidth().wrapContentHeight()
            )
        }

        // Highlight overlay
        val pageHighlights = highlights.filter { it.pageIndex == pageIndex }
        if (pageHighlights.isNotEmpty() && imgHeightPx > 0) {
            Canvas(
                Modifier.fillMaxWidth().height(with(density) { imgHeightPx.toDp() })
            ) {
                pageHighlights.forEach { hl ->
                    hl.rects.forEach { r ->
                        drawRect(hl.color, Offset(r.left, r.top),
                            androidx.compose.ui.geometry.Size(r.width(), r.height()))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(fileName: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(VA_BG.copy(0.93f)).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.ArrowBack, "Back", tint = VA_WHITE, modifier = Modifier.size(22.dp))
        }
        Text(fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VA_WHITE,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOADING / ERROR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoadingView(fileName: String) {
    Box(Modifier.fillMaxSize().background(VA_BG), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = VA_INDIGO2, strokeWidth = 2.5.dp, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("PDF লোড হচ্ছে...", fontSize = 13.sp, color = VA_MUTED)
            Text(fileName, fontSize = 11.sp, color = VA_MUTED.copy(0.6f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp))
        }
    }
}

@Composable
private fun ErrorView(msg: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(VA_BG), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(msg, fontSize = 13.sp, color = VA_RED, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(20.dp))
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = VA_INDIGO)) {
                Text("← ফিরে যান", color = Color.White)
            }
        }
    }
}

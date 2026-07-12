package com.rasel.RasFocus.selfcontrol.study_tools

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import java.io.File
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
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
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
private val VA_AMBER   = Color(0xFFFFB347)

// Highlight colors
private val HL_YELLOW  = Color(0xAAFFE066)
private val HL_GREEN   = Color(0xAA4AE08A)
private val HL_BLUE    = Color(0xAA4DA6FF)
private val HL_PINK    = Color(0xAAFF6B9D)

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────
// Hard ceiling on any single rendered page bitmap's width/height, regardless
// of zoom level — protects against OOM on an extreme zoom + a huge PDF page
// (e.g. a poster-sized page) combination. 4096px is the same safe ceiling
// most Android GPUs/bitmap handling comfortably supports.
private const val MAX_RENDER_DIM = 4096

data class PageData(
    val textPage:  Any?, // unused in MuPDF
    val pageIndex: Int,
    val widthPx:   Int,
    val heightPx:  Int,
    val renderedAtScale: Float = 1f,
    val bitmap:    Bitmap? = null
)

data class TextSelection(
    val pageIndex: Int,
    val charStart: Int,
    val charEnd:   Int,
    val text:      String,
    val rects:     List<RectF>,       // in bitmap coords
)

data class Highlight(
    val pageIndex: Int,
    val charStart: Int,
    val charEnd:   Int,
    val rects:     List<RectF>,
    val color:     Color,
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class PdfViewerActivity : ComponentActivity() {

    // FIX: uri/fileName held as Compose state (not local onCreate vals) so onNewIntent()
    // can push a new PDF into this same instance — singleTask means Android reuses this
    // Activity (instead of creating a new one) whenever another "open PDF" intent arrives
    // while it's already on top, e.g. tapping a 2nd PDF from Drive/Files.
    private val uriState = mutableStateOf<Uri?>(null)
    private val fileNameState = mutableStateOf("PDF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while reading
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadFromIntent(intent)

        setContent {
            NativePdfViewer(uri = uriState.value, fileName = fileNameState.value, onClose = { finish() })
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

        // FIX: a content:// grant delivered with this Intent only lives as long as this
        // process does. If the OS kills the process later (common on low-RAM devices)
        // and the user reopens this task from Recents, Android replays this same Intent
        // but the read grant is gone — contentResolver calls below then throw
        // SecurityException, which shows up as "PDF loads forever, then the app dies"
        // specifically for PDFs opened from another app (Drive/Files/etc).
        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Source app didn't offer a persistable grant — nothing more we can do,
                // but this must never crash the activity.
            }
        }

        uriState.value = uri
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
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val density       = LocalDensity.current

    // Pages
    val pages         = remember { mutableStateListOf<PageData?>() }
    val bitmapCache   = remember {
        object : android.util.LruCache<Int, Bitmap>(4) { // 4 pages max in memory
            override fun entryRemoved(evicted: Boolean, key: Int, oldBitmap: Bitmap, newBitmap: Bitmap?) {
                if (evicted) {
                    scope.launch(Dispatchers.Main) {
                        val current = pages.getOrNull(key)
                        if (current?.bitmap === oldBitmap) {
                            pages[key] = current.copy(bitmap = null, renderedAtScale = 1f)
                        }
                        kotlinx.coroutines.delay(200) // Wait for Compose to drop the reference
                        if (!oldBitmap.isRecycled) oldBitmap.recycle()
                    }
                }
            }
        }
    }
    var totalPages    by remember { mutableIntStateOf(0) }
    var currentPage   by remember { mutableIntStateOf(1) }
    var isLoading     by remember { mutableStateOf(true) }
    var errorMsg      by remember { mutableStateOf("") }

    // Controls visibility — starts VISIBLE (normal reading mode with header +
    // footer shown), matching WPS/Adobe's default. A single tap toggles into
    // fullscreen (hides both bars + system bars); tapping again brings them
    // back. This is a deliberate sticky toggle, not a timed auto-hide — the
    // user asked for "normal header/footer, but single tap → fullscreen",
    // which means the visible state should persist until the user taps again.
    var controlsVisible by remember { mutableStateOf(true) }

    fun toggleControls() {
        controlsVisible = !controlsVisible
    }

    // FIX: shared, document-level zoom state — previously scale/offsetX/offsetY
    // lived INSIDE PdfPageItem (one remember{} per page item), so every page had
    // its own independent zoom that reset back to 1x the moment you scrolled to
    // a different page. Every real PDF viewer treats zoom as one state shared
    // across the whole document; pinching in on page 3 and scrolling to page 4
    // should show page 4 at the same zoom level, not reset it.
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Selection & highlight
    var selection       by remember { mutableStateOf<TextSelection?>(null) }
    val highlights      = remember { mutableStateListOf<Highlight>() }
    var showToolbar     by remember { mutableStateOf(false) }
    var selectedColor   by remember { mutableStateOf(HL_YELLOW) }

    // MuPDF Document
    var pdfDoc   by remember { mutableStateOf<Document?>(null) }
    var tempFilePath by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val screenW   = context.resources.displayMetrics.widthPixels

    // ── Load PDF ────────────────────────────────────────────────────────────
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "PDF পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                // FIX: MuPDF's Document.openDocument(path) needs a real filesystem
                // path it can reopen/seek freely. "/proc/self/fd/N" looked like it
                // worked but is unreliable across devices/SELinux policies and content
                // providers — MuPDF's native layer can silently SIGSEGV on it instead
                // of throwing a catchable Java exception, which is exactly the
                // "app just closes, no error" symptom. Copying to a real cache file
                // first is slightly slower to open but crash-safe on every device.
                val tempFile = File(context.cacheDir, "viewer_temp_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("File খুলতে পারিনি")

                val doc = Document.openDocument(tempFile.absolutePath)
                tempFilePath = tempFile.absolutePath
                pdfDoc  = doc
                val count = doc.countPages()

                withContext(Dispatchers.Main) {
                    pages.clear()
                    repeat(count) { pages.add(null) }
                    totalPages  = count
                    currentPage = 1
                    // isLoading stays true here — flipped to false after
                    // the first page bitmap is rendered (see loop below).
                }

                // ── FAST OPEN: render first page bitmap immediately so the
                // reading view appears at once (no "loading" screen for the
                // user). All remaining pages render in the background — visible
                // pages get their bitmaps as PdfPageItem's LaunchedEffect fires,
                // invisible pages are skipped until scrolled into view.
                for (i in 0 until count) {
                    val page   = doc.loadPage(i)
                    val bounds = page.bounds
                    val origW  = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                    val origH  = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                    val baseS  = screenW.toFloat() / origW.toFloat()
                    val bmpW   = screenW
                    val bmpH   = (origH * baseS).roundToInt().coerceAtLeast(1)

                    if (i == 0) {
                        // Render page 0 NOW — synchronous on IO thread — so we
                        // can flip isLoading = false with a real bitmap already
                        // in place.  The user sees page 1 the instant it opens.
                        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AColor.WHITE)
                        val ctm = Matrix(bmpW.toFloat() / origW, bmpH.toFloat() / origH)
                        val dev = AndroidDrawDevice(bmp, 0, 0)
                        page.run(dev, ctm, null)
                        dev.close()
                        bitmapCache.put(0, bmp)
                        withContext(Dispatchers.Main) {
                            pages[0] = PageData(null, 0, bmpW, bmpH,
                                renderedAtScale = 1f, bitmap = bmp)
                            isLoading = false   // ← show viewer immediately
                        }
                    } else {
                        // Remaining pages: just register dimensions so the
                        // LazyColumn can lay them out.  Bitmaps arrive later
                        // via PdfPageItem's LaunchedEffect → onLoadBitmap().
                        withContext(Dispatchers.Main) {
                            if (i < pages.size)
                                pages[i] = PageData(null, i, bmpW, bmpH)
                        }
                    }
                    page.destroy()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg  = "PDF খোলা যায়নি: ${e.message}"
                }
            }
        }
    }

    // ── Re-render a page at higher resolution for sharp zoom ────────────────
    suspend fun reRenderPageSharper(pageIndex: Int, targetScale: Float) {
        val doc = pdfDoc ?: return
        withContext(Dispatchers.IO) {
            try {
                val page   = doc.loadPage(pageIndex)
                val bounds = page.bounds
                val origW  = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val origH  = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                val baseScale = screenW.toFloat() / origW.toFloat()

                var bmpW = (screenW * targetScale).roundToInt()
                var bmpH = (origH * baseScale * targetScale).roundToInt().coerceAtLeast(1)

                if (bmpW > MAX_RENDER_DIM || bmpH > MAX_RENDER_DIM) {
                    val shrink = MAX_RENDER_DIM.toFloat() / maxOf(bmpW, bmpH)
                    bmpW = (bmpW * shrink).roundToInt().coerceAtLeast(1)
                    bmpH = (bmpH * shrink).roundToInt().coerceAtLeast(1)
                }

                val sharperBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                sharperBmp.eraseColor(AColor.WHITE)
                // MuPDF fitz — vector-accurate rendering
                val ctm = Matrix(bmpW.toFloat() / origW.toFloat(), bmpH.toFloat() / origH.toFloat())
                val dev = AndroidDrawDevice(sharperBmp, 0, 0)
                page.run(dev, ctm, null)
                dev.close()
                page.destroy()

                withContext(Dispatchers.Main) {
                    bitmapCache.put(pageIndex, sharperBmp)
                    val old = pages.getOrNull(pageIndex)
                    if (old != null) {
                        val oldBitmap = old.bitmap
                        pages[pageIndex] = old.copy(
                            bitmap          = sharperBmp,
                            widthPx         = bmpW,
                            heightPx        = bmpH,
                            renderedAtScale = targetScale
                        )
                        // LruCache eviction will handle old bitmaps eventually, but we can explicitly free if we want.
                        // Actually, if we just let LruCache evict it when we `put` new ones, it's safer.
                    }
                }
            } catch (_: Exception) {
                // Zoomed page just stays at its current (lower) resolution —
                // not worth surfacing an error for a sharpness upgrade that
                // didn't happen.
            }
        }
    }

    // Track current page from scroll
    val visibleIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(visibleIdx) {
        if (totalPages > 0) currentPage = visibleIdx + 1
    }

    // Debounced: waits for pinch/zoom to settle before re-rendering at vector
    // resolution. Re-renders the visible page AND the next two pages so scrolling
    // forward while zoomed in never shows a blurry page first.
    LaunchedEffect(scale, visibleIdx) {
        delay(300)   // slightly faster settle than before
        if (scale <= 1.05f) return@LaunchedEffect   // no work needed at 1x
        val pagesToUpgrade = listOf(visibleIdx, visibleIdx + 1, visibleIdx - 1)
            .filter { it in 0 until totalPages }
        for (idx in pagesToUpgrade) {
            val current = pages.getOrNull(idx) ?: continue
            // Re-render if we haven't rendered this page at this zoom yet,
            // or if the zoom has grown significantly since we last rendered it.
            if (scale > current.renderedAtScale * 1.3f) {
                reRenderPageSharper(idx, scale)
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            bitmapCache.evictAll()
            // textPage cleanup not needed with MuPDF fitz
            try { pdfDoc?.destroy() } catch (_: Exception) {}
            try { tempFilePath?.let { File(it).delete() } } catch (_: Exception) {}
        }
    }

    // ── Immersive system bars ──────────────────────────────────────────────
    // FIX: "clean screen like WPS" — the app toolbar hiding on its own wasn't
    // enough because Android's own status/navigation bars stayed on screen.
    // Hide them together with the in-app controls so the reading view is a
    // true edge-to-edge page, and bring both back together on tap.
    val activityWindow = (context as? Activity)?.window
    val view = LocalView.current
    DisposableEffect(activityWindow) {
        val window = activityWindow
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {
            if (window != null) {
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(controlsVisible, activityWindow) {
        val window = activityWindow ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {

        // FIX: "PDF লোড হচ্ছে..." full-screen message সরানো হয়েছে।
        // Top-এ subtle progress bar — content area সাথে সাথে দেখা যাবে।
        if (isLoading) {
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color      = VA_INDIGO2,
                trackColor = Color.Transparent
            )
        }
        when {
            isLoading && pages.all { it == null } && errorMsg.isEmpty() -> {
                // First page আসা পর্যন্ত dark background — no spinner
                Box(Modifier.fillMaxSize().background(Color(0xFF111111)))
            }
            errorMsg.isNotEmpty() -> ErrorView(errorMsg, onClose)
            else -> {
                // ── WPS-style gesture engine ────────────────────────────────
                // Architecture:
                //   - ONE Box wraps the whole LazyColumn with graphicsLayer
                //     (scaleX/Y + translationX) so ALL pages scale together.
                //   - A single pointerInput block handles every touch gesture:
                //       • 1-finger drag  → always vertical-scroll the LazyColumn
                //                          AND horizontal-pan when zoomed in
                //       • 2-finger pinch → zoom (scale) + horizontal pan
                //       • tap            → toggle header/footer
                //       • double-tap     → zoom-in at tapped point / zoom-out
                //   - LazyColumn's own userScrollEnabled = false so our custom
                //     1-finger handler exclusively owns vertical scrolling —
                //     no gesture ownership fights between Compose's internal
                //     scroll system and our pointerInput.

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // ── tap + double-tap ────────────────────────────
                            detectTapGestures(
                                onTap = {
                                    toggleControls()
                                    if (showToolbar) { selection = null; showToolbar = false }
                                },
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.2f) {
                                        scale   = 1f
                                        offsetX = 0f
                                    } else {
                                        val newScale   = 2.5f
                                        val maxOffsetX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                        offsetX = ((size.width / 2f - tapOffset.x) * (newScale - 1f))
                                            .coerceIn(-maxOffsetX, maxOffsetX)
                                        scale = newScale
                                        val distFromCenterY = tapOffset.y - size.height / 2f
                                        val scrollDelta = distFromCenterY * (newScale - 1f) / newScale
                                        scope.launch { listState.scrollBy(scrollDelta) }
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // ── pinch-zoom + single-finger pan ──────────────
                            // awaitEachGesture restarts for every new touch sequence.
                            awaitEachGesture {
                                // Wait for any first touch
                                val firstDown = awaitFirstDown(requireUnconsumed = false)

                                var lastPos    = firstDown.position
                                var lastDist   = 0f   // distance between two fingers
                                var lastMidX   = 0f
                                var twoFinger  = false

                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        // ── TWO-FINGER: pinch zoom ──────────
                                        twoFinger = true
                                        val p0 = pressed[0].position
                                        val p1 = pressed[1].position
                                        val dist = kotlin.math.sqrt(
                                            ((p0.x - p1.x) * (p0.x - p1.x) +
                                             (p0.y - p1.y) * (p0.y - p1.y)).toDouble()
                                        ).toFloat()
                                        val midX = (p0.x + p1.x) / 2f

                                        if (lastDist > 0f) {
                                            val zoomChange = dist / lastDist
                                            val newScale   = (scale * zoomChange).coerceIn(1f, 10f)
                                            val maxOffsetX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                            // Pan: midpoint movement + zoom pivot at midpoint
                                            val panX = (midX - lastMidX) + (size.width / 2f - midX) * (newScale - scale)
                                            offsetX = if (newScale > 1f)
                                                (offsetX + panX).coerceIn(-maxOffsetX, maxOffsetX)
                                            else 0f
                                            scale = newScale
                                        }
                                        lastDist = dist
                                        lastMidX = midX
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }

                                    } else if (pressed.size == 1 && !twoFinger) {
                                        // ── ONE-FINGER: vertical scroll + horizontal pan ──
                                        val pos   = pressed[0].position
                                        val delta = pos - lastPos

                                        // Vertical: always scroll the LazyColumn
                                        if (delta.y != 0f) {
                                            scope.launch { listState.scrollBy(-delta.y) }
                                        }

                                        // Horizontal: only when zoomed in
                                        if (scale > 1.05f && delta.x != 0f) {
                                            val maxOffsetX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            offsetX = (offsetX + delta.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        }

                                        lastPos = pos
                                        pressed[0].consume()
                                    }

                                } while (event.changes.any { it.pressed })

                                // Reset inter-finger distance for next gesture
                                lastDist  = 0f
                                twoFinger = false
                            }
                        }
                        .graphicsLayer(
                            scaleX       = scale,
                            scaleY       = scale,
                            translationX = offsetX,
                            clip         = false
                        )
                ) {
                    LazyColumn(
                        state               = listState,
                        // Disabled — our custom pointerInput above handles ALL
                        // scrolling so there are no gesture ownership conflicts.
                        userScrollEnabled   = false,
                        modifier            = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding      = PaddingValues(top = 0.dp, bottom = 72.dp)
                    ) {
                        itemsIndexed(pages) { idx, pageData ->
                            if (pageData == null) {
                                PagePlaceholder()
                            } else {
                                PdfPageItem(
                                    pageData       = pageData,
                                    pdfDoc         = pdfDoc,
                                    highlights     = highlights.filter { it.pageIndex == idx },
                                    onTextSelected = { sel ->
                                        selection    = sel
                                        showToolbar  = sel != null
                                    },
                                    onLoadBitmap   = { _ ->
                                        // Always render at current document zoom so pages
                                        // scrolled into view while zoomed appear sharp at once.
                                        scope.launch { reRenderPageSharper(idx, scale) }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Selection toolbar ────────────────────────────────────────
                AnimatedVisibility(
                    visible  = showToolbar && selection != null,
                    enter    = fadeIn() + slideInVertically { it / 2 },
                    exit     = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SelectionToolbar(
                        selectedColor = selectedColor,
                        onColorChange = { selectedColor = it },
                        onHighlight   = {
                            selection?.let { sel ->
                                highlights.add(
                                    Highlight(sel.pageIndex, sel.charStart, sel.charEnd, sel.rects, selectedColor)
                                )
                            }
                            selection   = null
                            showToolbar = false
                        },
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("PDF Text", selection?.text ?: ""))
                            selection   = null
                            showToolbar = false
                        },
                        onDismiss = { selection = null; showToolbar = false }
                    )
                }

                // ── Floating top bar ─────────────────────────────────────────
                AnimatedVisibility(
                    visible  = controlsVisible && !showToolbar,
                    enter    = slideInVertically { -it } + fadeIn(),
                    exit     = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopBar(
                        fileName = fileName,
                        onBack   = onClose
                    )
                }

                // ── Floating bottom bar (footer) ─────────────────────────────
                // Mirrors TopBar's visibility exactly, so header + footer always
                // show/hide together on tap — one "fullscreen" toggle, not two.
                AnimatedVisibility(
                    visible  = controlsVisible && !showToolbar,
                    enter    = slideInVertically { it } + fadeIn(),
                    exit     = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomBar(
                        currentPage = currentPage,
                        totalPages  = totalPages
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF PAGE ITEM
// Handles: text selection, highlight render. Zoom/pan now lives one level up,
// on the container wrapping the whole page list (see NativePdfViewer) — the
// item itself always renders at 1x internally.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PdfPageItem(
    pageData:       PageData,
    pdfDoc:         Document?,
    highlights:     List<Highlight>,
    onTextSelected: (TextSelection?) -> Unit,
    onLoadBitmap:   (Float) -> Unit,
) {
    val density = LocalDensity.current

    // Text selection state
    var selStart   by remember { mutableStateOf<Offset?>(null) }
    var selEnd     by remember { mutableStateOf<Offset?>(null) }
    var isSelecting by remember { mutableStateOf(false) }

    // Image dimensions on screen
    var imgWidthPx  by remember { mutableIntStateOf(pageData.widthPx) }
    var imgHeightPx by remember { mutableIntStateOf(pageData.heightPx) }

    // Convert screen tap position → PDF page coords for text extraction.
    // FIX: no longer compensates for scale/offset — the item is always laid
    // out and hit-tested at 1x now (Compose inverse-transforms pointer input
    // through the container's graphicsLayer automatically), so the raw local
    // tap position already lines up with the item's own untransformed size.
    fun screenToPdfCoords(screenX: Float, screenY: Float): PointF {
        val pdfX = (screenX / imgWidthPx.toFloat()) * pageData.widthPx
        val pdfY = (screenY / imgHeightPx.toFloat()) * pageData.heightPx
        return PointF(pdfX, pdfY)
    }

    // Extract text between two screen positions using MuPDF's StructuredText.
    // FIX: this was a stub that always returned null — the long-press+drag UI
    // ran, but no text was ever actually extracted or highlighted/copied.
    fun extractSelectedText(start: Offset, end: Offset): TextSelection? {
        val doc = pdfDoc ?: return null
        return try {
            val p1 = screenToPdfCoords(start.x, start.y)
            val p2 = screenToPdfCoords(end.x, end.y)
            val left   = minOf(p1.x, p2.x)
            val top    = minOf(p1.y, p2.y)
            val right  = maxOf(p1.x, p2.x)
            val bottom = maxOf(p1.y, p2.y)
            if (right - left < 2f && bottom - top < 2f) return null // no real drag yet

            val page = doc.loadPage(pageData.pageIndex)
            val text = try {
                page.toStructuredText()
            } finally {
                // Page itself can be released once StructuredText is built —
                // the StructuredText object holds its own data independently.
            }

            val sb = StringBuilder()
            val rects = mutableListOf<RectF>()
            var charIndex = 0
            var startIdx = -1
            var endIdx   = -1

            // StructuredText exposes blocks → lines → chars, each with a
            // quad (4-point box) in PDF page coordinates.
            for (block in text.blocks) {
                for (line in block.lines) {
                    for (ch in line.chars) {
                        val q = ch.quad
                        // Char center point, used to test against the
                        // selection rectangle the user dragged out.
                        val cx = (q.ul_x + q.ur_x + q.ll_x + q.lr_x) / 4f
                        val cy = (q.ul_y + q.ur_y + q.ll_y + q.lr_y) / 4f
                        val inSelection = cx in left..right && cy in top..bottom
                        if (inSelection) {
                            if (startIdx == -1) startIdx = charIndex
                            endIdx = charIndex
                            sb.appendCodePoint(ch.c)
                            // Convert this char's PDF-space quad to bitmap
                            // coords for the highlight/selection overlay.
                            val bx1 = (minOf(q.ul_x, q.ll_x) / pageData.widthPx)  * imgWidthPx
                            val by1 = (minOf(q.ul_y, q.ur_y) / pageData.heightPx) * imgHeightPx
                            val bx2 = (maxOf(q.ur_x, q.lr_x) / pageData.widthPx)  * imgWidthPx
                            val by2 = (maxOf(q.ll_y, q.lr_y) / pageData.heightPx) * imgHeightPx
                            rects.add(RectF(bx1, by1, bx2, by2))
                        }
                        charIndex++
                    }
                    // Newline between lines within a block so copied text
                    // reads naturally instead of running every line together.
                    if (startIdx != -1 && endIdx != -1) sb.append(' ')
                }
            }
            page.destroy()

            if (startIdx == -1 || sb.isBlank()) return null
            TextSelection(pageData.pageIndex, startIdx, endIdx, sb.toString().trim(), rects)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFF111111))
            .onGloballyPositioned { coords ->
                imgWidthPx  = coords.size.width
                imgHeightPx = coords.size.height
            }
            .pointerInput(pageData) {
                detectTapGestures(
                    onLongPress = { pressOffset ->
                        // Long press = start text selection
                        isSelecting = true
                        selStart    = pressOffset
                        selEnd      = pressOffset
                    }
                )
            }
            .pointerInput(pageData, isSelecting) {
                // Drag to extend selection after long press
                if (!isSelecting) return@pointerInput
                detectDragGestures(
                    onDrag = { change, _ ->
                        selEnd = change.position
                        val s = selStart
                        val e = selEnd
                        if (s != null && e != null) {
                            val sel = extractSelectedText(s, e)
                            onTextSelected(sel)
                        }
                    },
                    onDragEnd = {
                        val s = selStart
                        val e = selEnd
                        if (s != null && e != null) {
                            val sel = extractSelectedText(s, e)
                            onTextSelected(sel)
                        }
                        isSelecting = false
                    }
                )
            }
    ) {
        // ── Render page bitmap ─────────────────────────────────────────────
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(with(density) { imgHeightPx.toDp() }).background(Color.White)
        ) {
            LaunchedEffect(pageData.pageIndex) {
                if (pageData.bitmap == null) {
                    onLoadBitmap(1f)
                }
            }

            if (pageData.bitmap != null) {
                Image(
                    bitmap             = pageData.bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageData.pageIndex + 1}",
                    contentScale       = ContentScale.FillWidth,
                    modifier           = Modifier.fillMaxWidth().wrapContentHeight()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

        // ── Render highlights + selection overlay ─────────────────────────
        val allHighlights = highlights
        val currentSel    = if (isSelecting) {
            val s = selStart; val e = selEnd
            if (s != null && e != null) extractSelectedText(s, e) else null
        } else null

        if (allHighlights.isNotEmpty() || currentSel != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { imgHeightPx.toDp() })
            ) {
                // Draw saved highlights
                allHighlights.forEach { hl ->
                    hl.rects.forEach { r ->
                        drawRect(
                            color   = hl.color,
                            topLeft = Offset(r.left, r.top),
                            size    = androidx.compose.ui.geometry.Size(r.width(), r.height())
                        )
                    }
                }
                // Draw live selection
                currentSel?.rects?.forEach { r ->
                    drawRect(
                        color   = HL_BLUE,
                        topLeft = Offset(r.left, r.top),
                        size    = androidx.compose.ui.geometry.Size(r.width(), r.height())
                    )
                }
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// SELECTION TOOLBAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SelectionToolbar(
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    onHighlight:   () -> Unit,
    onCopy:        () -> Unit,
    onDismiss:     () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VA_BG2)
            .border(BorderStroke(0.5.dp, VA_BORDER), RoundedCornerShape(0.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Color picker row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Highlight:", fontSize = 11.sp, color = VA_MUTED)
            listOf(HL_YELLOW, HL_GREEN, HL_BLUE, HL_PINK).forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width  = if (c == selectedColor) 2.5.dp else 0.dp,
                            color  = VA_WHITE,
                            shape  = CircleShape
                        )
                        .clickable { onColorChange(c) }
                )
            }
        }

        // Action buttons
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick  = onHighlight,
                modifier = Modifier.weight(1f).height(38.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = selectedColor.copy(alpha = 0.85f))
            ) {
                Icon(Icons.Default.FormatColorFill, "Highlight", modifier = Modifier.size(16.dp), tint = VA_BG)
                Spacer(Modifier.width(4.dp))
                Text("Highlight", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VA_BG)
            }

            Button(
                onClick  = onCopy,
                modifier = Modifier.weight(1f).height(38.dp),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = VA_INDIGO)
            ) {
                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp), tint = VA_WHITE)
                Spacer(Modifier.width(4.dp))
                Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VA_WHITE)
            }

            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .size(38.dp)
                    .background(VA_CARD2, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, "Dismiss", tint = VA_MUTED, modifier = Modifier.size(18.dp))
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
        Modifier
            .fillMaxWidth()
            .background(VA_BG.copy(0.93f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.ArrowBack, "Back", tint = VA_WHITE, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(
                fileName,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = VA_WHITE,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM BAR (footer) — page indicator, mirrors TopBar's look & visibility
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomBar(currentPage: Int, totalPages: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(VA_BG.copy(0.93f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = "$currentPage / $totalPages",
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium,
            color      = VA_WHITE.copy(alpha = 0.85f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOADING / ERROR / PLACEHOLDER
// ─────────────────────────────────────────────────────────────────────────────
// LoadingView removed — instant open, no loading screen

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

@Composable
private fun PagePlaceholder() {
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = VA_INDIGO2, strokeWidth = 2.dp)
    }
}

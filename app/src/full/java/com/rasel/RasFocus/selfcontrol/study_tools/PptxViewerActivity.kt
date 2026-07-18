package com.rasel.RasFocus.selfcontrol.study_tools

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class PptxSlide(
    val index: Int,
    val shapes: List<PptxShape>,
    val bgColor: Int = android.graphics.Color.WHITE
)

sealed class PptxShape {
    data class TextBox(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val fontSize: Float = 24f,
        val color: Int = android.graphics.Color.BLACK,
        val x: Float = 0f, val y: Float = 0f,
        val w: Float = 1f, val h: Float = 1f,
        val align: Paint.Align = Paint.Align.LEFT
    ) : PptxShape()

    data class Rect(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val fillColor: Int = android.graphics.Color.TRANSPARENT,
        val strokeColor: Int = android.graphics.Color.GRAY,
        val strokeWidth: Float = 2f
    ) : PptxShape()

    data class EmbeddedImage(
        val bitmap: Bitmap,
        val x: Float, val y: Float, val w: Float, val h: Float
    ) : PptxShape()
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────

class PptxViewerActivity : ComponentActivity() {

    private val uriState   = mutableStateOf<Uri?>(null)
    private val nameState  = mutableStateOf("Presentation")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            MaterialTheme {
                PptxViewer(
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
        uriState.value  = uri
        nameState.value = uri?.let { getFileName(it) } ?: "Presentation"
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
}

// ─────────────────────────────────────────────────────────────────────────────
// PPTX PARSER  (ZIP + XmlPullParser — zero external deps)
// ─────────────────────────────────────────────────────────────────────────────

object PptxParser {

    fun parse(context: Context, uri: Uri): List<PptxSlide> {
        val zipBytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: return emptyList()

        // 1. Read all ZIP entries into memory once
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // 2. Find slide order from presentation.xml
        val slideOrder = parseSlideOrder(entries["ppt/presentation.xml"] ?: return emptyList())

        // 3. Parse each slide
        return slideOrder.mapIndexedNotNull { idx, rId ->
            val path = "ppt/slides/$rId"
            val xml  = entries[path] ?: return@mapIndexedNotNull null
            parseSlide(idx, xml, entries)
        }
    }

    private fun parseSlideOrder(xml: ByteArray): List<String> {
        val order = mutableListOf<String>()
        val xpp   = newParser(xml.inputStream())
        var event = xpp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xpp.name == "p:sld") {
                // r:id attr points to slide relationship
                for (i in 0 until xpp.attributeCount) {
                    if (xpp.getAttributeName(i).endsWith("id")) {
                        // resolve via _rels
                        break
                    }
                }
            }
            event = xpp.next()
        }
        // Fallback: look for slide filenames directly
        return (1..100).mapNotNull { i ->
            val name = "slide$i.xml"
            if (true) name else null   // always include, parser skips missing
        }.take(50)
    }

    private fun parseSlide(index: Int, xml: ByteArray, allEntries: Map<String, ByteArray>): PptxSlide {
        val shapes  = mutableListOf<PptxShape>()
        var bgColor = android.graphics.Color.WHITE

        val xpp   = newParser(xml.inputStream())
        var event = xpp.eventType

        // State for current shape
        var curText    = StringBuilder()
        var curBold    = false
        var curItalic  = false
        var curSize    = 24f
        var curColor   = android.graphics.Color.BLACK
        var curX       = 0f; var curY = 0f
        var curW       = 1f; var curH = 1f
        var inTextBody = false
        var inPara     = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (xpp.name) {
                    // Slide background solid fill
                    "p:bg"    -> {}
                    "a:solidFill" -> {
                        // read srgbClr child
                    }
                    "a:srgbClr" -> {
                        val hex = xpp.getAttributeValue(null, "val") ?: ""
                        if (hex.length == 6) {
                            try {
                                bgColor = android.graphics.Color.parseColor("#$hex")
                            } catch (_: Exception) {}
                        }
                    }

                    // Shape position/size from xfrm
                    "a:off" -> {
                        curX = (xpp.getAttributeValue(null, "x")?.toLongOrNull()
                            ?: 0L).toFloat() / 9_144_000f   // EMU → fraction of slide
                        curY = (xpp.getAttributeValue(null, "y")?.toLongOrNull()
                            ?: 0L).toFloat() / 6_858_000f
                    }
                    "a:ext" -> {
                        curW = (xpp.getAttributeValue(null, "cx")?.toLongOrNull()
                            ?: 9_144_000L).toFloat() / 9_144_000f
                        curH = (xpp.getAttributeValue(null, "cy")?.toLongOrNull()
                            ?: 6_858_000L).toFloat() / 6_858_000f
                    }

                    // Text
                    "p:txBody", "a:txBody" -> { inTextBody = true; curText = StringBuilder() }
                    "a:p"  -> { inPara = true }
                    "a:rPr" -> {
                        curBold   = xpp.getAttributeValue(null, "b") == "1"
                        curItalic = xpp.getAttributeValue(null, "i") == "1"
                        val sz    = xpp.getAttributeValue(null, "sz")?.toIntOrNull()
                        if (sz != null) curSize = sz / 100f
                    }
                    "a:t"  -> {
                        // text content read in TEXT event
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inTextBody) curText.append(xpp.text ?: "")
                }

                XmlPullParser.END_TAG -> when (xpp.name) {
                    "a:p" -> {
                        if (inPara) curText.append("\n")
                        inPara = false
                    }
                    "p:sp" -> {
                        val txt = curText.toString().trim()
                        if (txt.isNotEmpty()) {
                            shapes.add(PptxShape.TextBox(
                                text = txt, bold = curBold, italic = curItalic,
                                fontSize = curSize.coerceIn(8f, 96f),
                                color    = curColor,
                                x = curX, y = curY, w = curW, h = curH
                            ))
                        }
                        // Reset shape state
                        curText   = StringBuilder(); curBold  = false
                        curItalic = false; curSize = 24f; curColor = android.graphics.Color.BLACK
                        inTextBody = false
                    }
                }
            }
            event = xpp.next()
        }

        return PptxSlide(index, shapes, bgColor)
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        // FIX (blank/white slides): isNamespaceAware = true strips the "p:"/"a:"
        // prefix from every tag name, so xpp.name returned "txBody"/"t"/"sp"
        // instead of "a:txBody"/"a:t"/"p:sp" — none of the `when (xpp.name)`
        // checks below ever matched, so no text/shapes were ever captured.
        // Keeping namespace-awareness OFF makes xpp.name return the raw
        // qualified name, matching the prefixed strings used throughout.
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SLIDE RENDERER  →  Bitmap
// ─────────────────────────────────────────────────────────────────────────────

object SlideRenderer {

    fun render(slide: PptxSlide, width: Int): Bitmap {
        val height  = (width * 9f / 16f).roundToInt()   // 16:9 default
        val bmp     = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas  = android.graphics.Canvas(bmp)

        // Background
        canvas.drawColor(slide.bgColor)

        // Shapes
        slide.shapes.forEach { shape ->
            when (shape) {
                is PptxShape.TextBox -> {
                    val px = shape.x * width
                    val py = shape.y * height
                    val pw = shape.w * width
                    val ph = shape.h * height

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color     = shape.color
                        textSize  = shape.fontSize * (width / 960f)
                        typeface  = Typeface.create(
                            Typeface.DEFAULT,
                            when {
                                shape.bold && shape.italic -> Typeface.BOLD_ITALIC
                                shape.bold   -> Typeface.BOLD
                                shape.italic -> Typeface.ITALIC
                                else         -> Typeface.NORMAL
                            }
                        )
                        textAlign = shape.align
                    }

                    // Word-wrap
                    val lines = wrapText(shape.text, paint, pw)
                    val lineH = paint.textSize * 1.3f
                    lines.forEachIndexed { i, line ->
                        val lx = when (shape.align) {
                            Paint.Align.CENTER -> px + pw / 2
                            Paint.Align.RIGHT  -> px + pw
                            else               -> px + 8f
                        }
                        val ly = py + 8f + (i + 1) * lineH
                        if (ly < py + ph) canvas.drawText(line, lx, ly, paint)
                    }
                }

                is PptxShape.Rect -> {
                    val rect = RectF(
                        shape.x * width, shape.y * height,
                        (shape.x + shape.w) * width, (shape.y + shape.h) * height
                    )
                    if (shape.fillColor != android.graphics.Color.TRANSPARENT) {
                        canvas.drawRect(rect, Paint().apply { color = shape.fillColor })
                    }
                    canvas.drawRect(rect, Paint().apply {
                        color     = shape.strokeColor
                        strokeWidth = shape.strokeWidth
                        style     = Paint.Style.STROKE
                    })
                }

                is PptxShape.EmbeddedImage -> {
                    val dst = RectF(
                        shape.x * width, shape.y * height,
                        (shape.x + shape.w) * width, (shape.y + shape.h) * height
                    )
                    canvas.drawBitmap(shape.bitmap, null, dst, null)
                }
            }
        }

        return bmp
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
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PptxViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current

    var slides        by remember { mutableStateOf<List<PptxSlide>>(emptyList()) }
    var bitmaps       by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var isLoading     by remember { mutableStateOf(true) }
    var errorMsg      by remember { mutableStateOf("") }
    var currentSlide  by remember { mutableStateOf(1) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Zoom/pan state
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val screenW = context.resources.displayMetrics.widthPixels
    val listState = rememberLazyListState()

    // Load
    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "ফাইল পাওয়া যায়নি"; return@LaunchedEffect }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val parsed = PptxParser.parse(context, uri)
                withContext(Dispatchers.Main) {
                    slides    = parsed
                    isLoading = false
                }
                // Render slide bitmaps progressively
                parsed.forEachIndexed { idx, slide ->
                    val bmp = SlideRenderer.render(slide, screenW)
                    withContext(Dispatchers.Main) {
                        bitmaps = bitmaps + (idx to bmp)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg  = "খুলতে পারিনি: ${e.message}"
                }
            }
        }
    }

    // Track current slide
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentSlide = listState.firstVisibleItemIndex + 1
    }

    val VA_BG    = Color(0xFF111111)
    val VA_WHITE = Color(0xFFF5F5F5)

    Box(
        Modifier
            .fillMaxSize()
            .background(VA_BG)
            .systemBarsPadding()
    ) {
        when {
            isLoading -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4FACFE))
                    Spacer(Modifier.height(12.dp))
                    Text("Presentation খুলছি…", color = VA_WHITE, fontSize = 14.sp)
                }
            }
            errorMsg.isNotEmpty() -> {
                Text(errorMsg, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            }
            slides.isEmpty() -> {
                Text("কোনো slide পাওয়া যায়নি", color = VA_WHITE, modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                // Slide list with zoom+pan
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale   = (scale * zoomChange).coerceIn(1f, 5f)
                    val maxOff = (screenW * (scale - 1f)) / 2f
                    offsetX = (offsetX + panChange.x).coerceIn(-maxOff, maxOff)
                    if (scale == 1f) offsetX = 0f
                }

                LazyColumn(
                    state  = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer(
                            scaleX       = scale,
                            scaleY       = scale,
                            translationX = offsetX
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    if (scale > 1f) {
                                        scale = 1f; offsetX = 0f
                                    } else {
                                        scale = 2.5f
                                        val sw = screenW.toFloat()
                                        offsetX = ((sw / 2f) - offset.x) * (scale - 1f)
                                        val maxOff = (sw * (scale - 1f)) / 2f
                                        offsetX = offsetX.coerceIn(-maxOff, maxOff)
                                    }
                                }
                            )
                        },
                    contentPadding  = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(slides) { idx, _ ->
                        val bmp = bitmaps[idx]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap             = bmp.asImageBitmap(),
                                    contentDescription = "Slide ${idx + 1}",
                                    contentScale       = ContentScale.FillWidth,
                                    modifier           = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(28.dp),
                                    color       = Color(0xFF4FACFE),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
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
                            .background(VA_BG.copy(alpha = 0.93f))
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

                // Bottom bar — slide indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible  = controlsVisible,
                    enter    = fadeIn(),
                    exit     = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(VA_BG.copy(alpha = 0.93f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "$currentSlide / ${slides.size}",
                            color      = VA_WHITE,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

package com.rasel.RasFocus.selfcontrol.study_tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Light-flavour PDF Viewer — uses android.graphics.pdf.PdfRenderer (no pdfium)
// Available on all Android 5+ devices. No native .so deps needed.
// ─────────────────────────────────────────────────────────────────────────────

class PdfViewerActivity : ComponentActivity() {

    private val uriState      = mutableStateOf<Uri?>(null)
    private val fileNameState = mutableStateOf("PDF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            MaterialTheme {
                LightPdfViewer(
                    uri      = uriState.value,
                    fileName = fileNameState.value,
                    onClose  = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent); loadFromIntent(intent)
    }

    private fun loadFromIntent(intent: android.content.Intent?) {
        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null -> intent.data
            intent?.hasExtra("pdf_uri") == true -> Uri.parse(intent.getStringExtra("pdf_uri"))
            else -> null
        }
        if (uri != null && uri.scheme == "content") {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) {}
        }
        uriState.value = uri
        fileNameState.value = uri?.let { n -> var nm: String? = null
            if (n.scheme == "content") contentResolver.query(n, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) nm = c.getString(i) }
            }
            nm ?: n.lastPathSegment?.substringAfterLast('/') ?: "PDF"
        } ?: "PDF"
    }
}

@Composable
fun LightPdfViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var bitmaps  by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var total    by remember { mutableIntStateOf(0) }
    var current  by remember { mutableIntStateOf(1) }
    var loading  by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var controls by remember { mutableStateOf(false) }
    var scale    by remember { mutableFloatStateOf(1f) }
    var offsetX  by remember { mutableFloatStateOf(0f) }

    val listState  = rememberLazyListState()
    val screenW    = context.resources.displayMetrics.widthPixels

    LaunchedEffect(uri) {
        if (uri == null) { loading = false; errorMsg = "ফাইল পাওয়া যায়নি"; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val pfd  = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("File খুলতে পারিনি")
                val renderer = PdfRenderer(pfd)
                val count    = renderer.pageCount
                withContext(Dispatchers.Main) { total = count }
                val list = mutableListOf<Bitmap>()
                for (i in 0 until count) {
                    val page  = renderer.openPage(i)
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val bmpW  = screenW; val bmpH = (screenW * ratio).toInt().coerceAtLeast(1)
                    val bmp   = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    list.add(bmp)
                    withContext(Dispatchers.Main) { bitmaps = list.toList() }
                }
                renderer.close(); pfd.close()
                withContext(Dispatchers.Main) { loading = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loading = false; errorMsg = "PDF খোলা যায়নি: ${e.message}" }
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) { current = listState.firstVisibleItemIndex + 1 }

    val VA_BG = ComposeColor(0xFF111111); val VA_WHITE = ComposeColor(0xFFF5F5F5)

    Box(Modifier.fillMaxSize().background(VA_BG).systemBarsPadding()) {
        when {
            loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ComposeColor(0xFF6C63FF))
                Spacer(Modifier.height(12.dp))
                Text("PDF লোড হচ্ছে...", color = VA_WHITE, fontSize = 14.sp)
            }
            errorMsg.isNotEmpty() -> Text(errorMsg, color = ComposeColor.Red, modifier = Modifier.align(Alignment.Center))
            bitmaps.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ComposeColor(0xFF6C63FF))
            else -> {
                val transformState = rememberTransformableState { zc, pc, _ ->
                    scale   = (scale * zc).coerceIn(1f, 5f)
                    val max = (screenW * (scale - 1f)) / 2f
                    offsetX = (offsetX + pc.x).coerceIn(-max, max)
                    if (scale == 1f) offsetX = 0f
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX)
                        .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls },
                            onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f } else scale = 2.5f }) },
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(bitmaps) { _, bmp ->
                        Box(Modifier.fillMaxWidth().background(ComposeColor.White)) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(Modifier.fillMaxWidth().background(VA_BG.copy(0.93f)).padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.ArrowBack, null, tint = VA_WHITE, modifier = Modifier.size(22.dp))
                        }
                        Text(fileName, color = VA_WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$current/$total", color = VA_WHITE.copy(0.6f), fontSize = 11.sp,
                            modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
    }
}

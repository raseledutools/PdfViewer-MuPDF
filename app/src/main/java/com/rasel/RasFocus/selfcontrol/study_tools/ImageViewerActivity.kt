package com.rasel.RasFocus.selfcontrol.study_tools

// ─────────────────────────────────────────────────────────────────────────────
//  ImageViewerActivity — .jpg .jpeg .png .gif .webp opened from any file manager
//  Pinch-zoom, pan, rotate, share
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageViewerActivity : ComponentActivity() {

    private val uriState  = mutableStateOf<Uri?>(null)
    private val nameState = mutableStateOf("Image")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        loadFromIntent(intent)

        setContent {
            ImageViewerScreen(
                uri      = uriState.value,
                fileName = nameState.value,
                onClose  = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadFromIntent(intent)
    }

    private fun loadFromIntent(intent: Intent?) {
        val uri: Uri? = when {
            intent?.action == Intent.ACTION_VIEW && intent.data != null -> intent.data
            else -> null
        }
        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* not persistable — fine, this session still works */ }
        }
        uriState.value  = uri
        nameState.value = uri?.let { getFileName(it) } ?: "Image"
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
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Image"
    }
}

@Composable
private fun ImageViewerScreen(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var bitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var isLoading  by remember { mutableStateOf(true) }
    var scale      by remember { mutableStateOf(1f) }
    var offsetX    by remember { mutableStateOf(0f) }
    var offsetY    by remember { mutableStateOf(0f) }
    var rotation   by remember { mutableStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        if (uri == null) { isLoading = false; errorMsg = "ছবি পাওয়া যায়নি"; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                        ?: throw IllegalStateException("ছবি decode করা যায়নি")
                    withContext(Dispatchers.Main) {
                        bitmap    = bmp
                        isLoading = false
                    }
                } ?: throw IllegalStateException("ফাইল খোলা যায়নি")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg  = "ছবি খোলা যায়নি: ${e.message}"
                }
            }
        }
    }

    fun shareImage() {
        val bmp = bitmap ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "shared_image.png")
                cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share image"))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            errorMsg != null -> {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BrokenImage, null, Modifier.size(56.dp), tint = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Text(errorMsg ?: "", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClose) { Text("Close") }
                }
            }
            bitmap != null -> {
                Image(
                    bitmap             = bitmap!!.asImageBitmap(),
                    contentDescription = fileName,
                    contentScale       = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX          = scale,
                            scaleY          = scale,
                            translationX    = offsetX,
                            translationY    = offsetY,
                            rotationZ       = rotation
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 6f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offsetX = 0f; offsetY = 0f
                                }
                            )
                        }
                )
            }
        }

        // Top bar
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter   = androidx.compose.animation.fadeIn(),
            exit    = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .6f))
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    fileName, color = Color.White, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                IconButton(onClick = { rotation += 90f }) {
                    Icon(Icons.Default.RotateRight, "Rotate", tint = Color.White)
                }
                IconButton(onClick = { shareImage() }) {
                    Icon(Icons.Default.Share, "Share", tint = Color.White)
                }
            }
        }

        // Bottom zoom reset
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible && scale != 1f,
            enter   = androidx.compose.animation.fadeIn(),
            exit    = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        ) {
            FilledTonalButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                Icon(Icons.Default.ZoomOutMap, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset zoom")
            }
        }
    }
}

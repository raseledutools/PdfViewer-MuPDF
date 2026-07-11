package com.rasel.RasFocus.selfcontrol.study_tools

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = when {
            intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null -> intent.data
            else -> null
        }
        val fileName = uri?.let { getFileName(it) } ?: "Image"
        setContent { ImageViewerScreen(uri = uri, fileName = fileName, onClose = { finish() }) }
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
fun ImageViewerScreen(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = LocalContext.current

    // Zoom + pan state
    var scale      by remember { mutableFloatStateOf(1f) }
    var offset     by remember { mutableStateOf(Offset.Zero) }
    var showBars   by remember { mutableStateOf(true) }

    val animScale  by animateFloatAsState(scale, label = "scale")

    val ext = fileName.substringAfterLast('.', "").lowercase()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Image with pinch-zoom + pan ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap     = { showBars = !showBars },
                        onDoubleTap = {
                            scale  = if (scale > 1.5f) 1f else 2.5f
                            offset = Offset.Zero
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale  = (scale * zoom).coerceIn(0.5f, 6f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX        = animScale,
                            scaleY        = animScale,
                            translationX  = offset.x,
                            translationY  = offset.y
                        )
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌", fontSize = 48.sp)
                    Text("ছবি খোলা যায়নি", color = Color.White.copy(.7f))
                }
            }
        }

        // ── Top bar ──────────────────────────────────────────────
        if (showBars) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(.7f), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(fileName, color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ext.uppercase(), color = Color.White.copy(.6f), fontSize = 11.sp)
                    }
                }
            }

            // ── Bottom bar: zoom controls ─────────────────────────
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(.55f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom out
                Box(
                    modifier = Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(.15f))
                        .pointerInput(Unit) { detectTapGestures { scale = (scale - 0.5f).coerceAtLeast(0.5f) } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ZoomOut, null, tint = Color.White, modifier = Modifier.size(18.dp)) }

                // Reset
                Box(
                    modifier = Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(.15f))
                        .pointerInput(Unit) { detectTapGestures { scale = 1f; offset = Offset.Zero } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.CenterFocusWeak, null, tint = Color.White, modifier = Modifier.size(18.dp)) }

                Text("${(animScale * 100).toInt()}%", color = Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Zoom in
                Box(
                    modifier = Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(.15f))
                        .pointerInput(Unit) { detectTapGestures { scale = (scale + 0.5f).coerceAtMost(6f) } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import java.io.File

class PptxViewerActivity : ComponentActivity() {

    private val uriState  = mutableStateOf<Uri?>(null)
    private val nameState = mutableStateOf("Presentation")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            MaterialTheme {
                OfficeWebViewer(
                    uri      = uriState.value,
                    fileName = nameState.value,
                    onClose  = { finish() }
                )
            }
        }
    }

    private fun loadFromIntent(intent: Intent?) {
        val uri: Uri? = when {
            intent?.action == Intent.ACTION_VIEW && intent.data != null -> intent.data
            intent?.hasExtra("pptx_uri") == true ->
                Uri.parse(intent.getStringExtra("pptx_uri"))
            else -> null
        }
        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }
        uriState.value  = uri
        nameState.value = uri?.let { getFileName(it) } ?: "Presentation"
    }

    private fun getFileName(uri: Uri): String {
        // Try display name from content resolver
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Presentation.pptx"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OfficeWebViewer — copies the office file to cache, then loads it via
// Google Docs Viewer (docs.google.com/viewer?url=...) in a WebView.
// Supports PPTX and DOCX equally well; the Activity class name determines
// which file type the system routes here, not this composable.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OfficeWebViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf("") }
    var viewerUrl by remember { mutableStateOf("") }

    // Copy file to public cache dir → get a publicly-accessible URL
    LaunchedEffect(uri) {
        if (uri == null) { errorMsg = "File not found"; isLoading = false; return@LaunchedEffect }
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                // Use file:// URL directly — much faster than encoding the
                // entire file as base64, especially for large DOCX/PPTX.
                // WebView can access cacheDir files when allowFileAccess=true.
                val fileUrl = "file://${cacheFile.absolutePath}"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    viewerUrl = fileUrl
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            errorMsg  = "Failed to load: ${e.message}"
            isLoading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .systemBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF16213E))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                fileName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        // Content
        Box(Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = Color(0xFF60A5FA)
                    )
                }
                errorMsg.isNotEmpty() -> {
                    Text(
                        errorMsg,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                viewerUrl.isNotEmpty() -> {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled    = true
                                    domStorageEnabled    = true
                                    loadWithOverviewMode = true
                                    useWideViewPort      = true
                                    builtInZoomControls  = true
                                    displayZoomControls  = false
                                    setSupportZoom(true)
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                    allowFileAccess = true
                                    allowContentAccess = true
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient   = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView, request: WebResourceRequest
                                    ) = false
                                }
                            }
                        },
                        update = { wv ->
                            if (viewerUrl.startsWith("file://")) {
                                wv.loadUrl(viewerUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

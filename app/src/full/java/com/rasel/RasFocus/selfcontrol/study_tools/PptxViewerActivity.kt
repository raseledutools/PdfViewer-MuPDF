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

    private var currentUri:  Uri?   = null
    private var currentName: String = "Presentation"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            MaterialTheme {
                val uriState  = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(currentUri)
                }
                val nameState = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(currentName)
                }
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
        currentUri  = uri
        currentName = uri?.let { getFileName(it) } ?: "Presentation"
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
// OfficeWebViewer — copies the office file to a cache dir, shares it via
// FileProvider, then loads Google Docs Viewer with the public URL.
// Works for both PPTX and DOCX. Google Docs Viewer renders the file
// faithfully (fonts, images, layout) without any additional library.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OfficeWebViewer(uri: Uri?, fileName: String, onClose: () -> Unit) {
    val context   = androidx.compose.ui.platform.LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf("") }
    var viewerUrl by remember { mutableStateOf("") }

    LaunchedEffect(uri) {
        if (uri == null) { errorMsg = "File not found"; isLoading = false; return@LaunchedEffect }
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Copy to cache so FileProvider can share it
                val cacheFile = java.io.File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                // FileProvider → content:// URI (publicly accessible)
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                // Google Docs Viewer — renders PPTX/DOCX perfectly on-device
                // via the internet. Encodes the content URI as the "url" param.
                val encoded = android.net.Uri.encode(contentUri.toString())
                val url     = "https://docs.google.com/viewer?url=$encoded&embedded=true"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    viewerUrl = url
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
                            if (viewerUrl.isNotEmpty()) {
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

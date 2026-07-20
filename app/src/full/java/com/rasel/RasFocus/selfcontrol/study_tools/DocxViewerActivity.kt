package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat

class DocxViewerActivity : ComponentActivity() {

    // Plain vars — updated before setContent recomposition
    private var currentUri:  Uri?   = null
    private var currentName: String = "Document"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        loadFromIntent(intent)
        setContent {
            // Compose reads from State; wrap vars in remember+mutableStateOf here
            val uriState  = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(currentUri)
            }
            val nameState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(currentName)
            }
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
            intent?.hasExtra("docx_uri") == true ->
                Uri.parse(intent.getStringExtra("docx_uri"))
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
        currentName = uri?.let { getFileName(it) } ?: "Document.docx"
    }

    private fun getFileName(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document.docx"
    }
}

package com.rasel.pdfviewer.combo.selfcontrol.study_tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * URI → Base64 string (IO thread safe)
 * Returns null if stream cannot be read.
 */
suspend fun uriToBase64(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            }
        } catch (_: Exception) { null }
    }

/**
 * Returns display filename from URI.
 * Falls back to last path segment if cursor returns nothing.
 */
fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) result = cursor.getString(idx)
            }
        }
    }
    if (result == null) {
        result = uri.lastPathSegment?.substringAfterLast('/')
    }
    return result?.ifBlank { "file_${System.currentTimeMillis()}" }
        ?: "file_${System.currentTimeMillis()}"
}

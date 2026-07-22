package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Multi-format file scanner — scans PDF, PPTX, DOCX, images (JPG, PNG, GIF, WebP)
 * Returns RecentItem with correct type detection
 */
object MultiFormatFileScanner {

    data class FileItem(
        val name: String,
        val type: String,        // "pdf", "pptx", "docx", "image", etc.
        val size: String,
        val time: Long,
        val uri: android.net.Uri?,
        val path: String,
        val mimeType: String
    )

    suspend fun scanAllFiles(ctx: Context): List<FileItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<FileItem>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Files.getContentUri("external")

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
            )

            // Multi-format MIME types
            val mimeTypes = listOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",  // PPTX
                "application/vnd.ms-powerpoint",  // PPT
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // DOCX
                "application/msword",  // DOC
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
            )

            val selectionArgs = mimeTypes.toTypedArray()
            val selection = selectionArgs.joinToString(" OR ") { 
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" 
            }
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            try {
                ctx.contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val timeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = cursor.getLong(sizeIdx)
                        val time = cursor.getLong(timeIdx) * 1000L
                        val path = cursor.getString(pathIdx) ?: ""
                        val mimeType = cursor.getString(mimeIdx) ?: ""
                        val uri = ContentUris.withAppendedId(collection, id)

                        val sizeStr = when {
                            size >= 1_048_576 -> "${"%.1f".format(size / 1_048_576.0)} MB"
                            size >= 1_024 -> "${size / 1024} KB"
                            else -> "$size B"
                        }

                        val type = detectFileType(name, mimeType)

                        list.add(FileItem(
                            name = name,
                            type = type,
                            size = sizeStr,
                            time = time,
                            uri = uri,
                            path = path,
                            mimeType = mimeType
                        ))
                    }
                }
            } catch (_: Exception) {}
            list
        }

    private fun detectFileType(fileName: String, mimeType: String): String {
        val lowerName = fileName.lowercase()
        return when {
            lowerName.endsWith(".pdf") -> "pdf"
            lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> "pptx"
            lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> "docx"
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || mimeType == "image/jpeg" -> "image"
            lowerName.endsWith(".png") || mimeType == "image/png" -> "image"
            lowerName.endsWith(".gif") || mimeType == "image/gif" -> "image"
            lowerName.endsWith(".webp") || mimeType == "image/webp" -> "image"
            mimeType.startsWith("image/") -> "image"
            else -> "file"
        }
    }

    fun getFileIcon(type: String): String = when (type) {
        "pdf" -> "📄"
        "pptx" -> "🎯"
        "docx" -> "📝"
        "image" -> "🖼️"
        else -> "📦"
    }

    fun getFileBgColor(type: String): androidx.compose.ui.graphics.Color = when (type) {
        "pdf" -> androidx.compose.ui.graphics.Color(0xFFFF5C5C)  // T_RED
        "pptx" -> androidx.compose.ui.graphics.Color(0xFFFFB347)  // AMBER
        "docx" -> androidx.compose.ui.graphics.Color(0xFF4DA6FF)  // T_BLUE
        "image" -> androidx.compose.ui.graphics.Color(0xFF3FD68F)  // T_GREEN
        else -> androidx.compose.ui.graphics.Color(0xFF55556A)    // MUTED
    }
}

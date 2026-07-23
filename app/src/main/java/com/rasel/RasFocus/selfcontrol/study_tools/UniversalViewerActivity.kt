package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// UniversalViewerActivity
//
// Single "Open with RasFocus" entry point for ALL file types:
//   PDF, DOCX, PPTX, XLSX, XLS, JPG, PNG, WEBP, GIF, TXT, MD, and more.
//
// Strategy:
//   • PDF             → PdfViewerActivity (direct)
//   • DOCX / DOC      → DocxViewerActivity (converts to PDF internally)
//   • PPTX / PPT      → PptxViewerActivity (converts to PDF internally)
//   • XLSX / XLS      → XlsxViewerActivity (converts to PDF internally)
//   • Images          → wrap in a 1-page PDF → PdfViewerActivity
//   • TXT / MD / code → TextViewerActivity
//   • Unknown         → try PdfViewerActivity, fall back to TextViewerActivity
//
// Having ONE activity declared in the manifest means Android shows
// "RasFocus" exactly once in the "Open with" picker regardless of file type.
// ─────────────────────────────────────────────────────────────────────────────

class UniversalViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val uri: Uri? = intent?.data
            ?: intent?.getParcelableExtra(Intent.EXTRA_STREAM)

        if (uri != null && uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }

        val fileName = getFileName(uri)
        val mimeType = intent?.type
            ?: uri?.let { contentResolver.getType(it) }
            ?: ""

        // Detect file type from extension + MIME
        val fileType = detectType(fileName, mimeType)

        setContent {
            val status = remember { mutableStateOf("") }

            LaunchedEffect(uri) {
                if (uri == null) {
                    status.value = "ফাইল পাওয়া যায়নি"
                    return@LaunchedEffect
                }
                when (fileType) {
                    FileType.PDF -> {
                        openDirect(PdfViewerActivity::class.java, uri, "application/pdf")
                    }
                    FileType.DOCX -> {
                        openDirect(DocxViewerActivity::class.java, uri, mimeType.ifEmpty {
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        })
                    }
                    FileType.PPTX -> {
                        openDirect(PptxViewerActivity::class.java, uri, mimeType.ifEmpty {
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        })
                    }
                    FileType.XLSX -> {
                        openDirect(XlsxViewerActivity::class.java, uri, mimeType.ifEmpty {
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        })
                    }
                    FileType.IMAGE -> {
                        withContext(Dispatchers.IO) {
                            try {
                                val pdfFile = imageToPdf(uri, fileName)
                                val pdfUri  = FileProvider.getUriForFile(
                                    this@UniversalViewerActivity,
                                    "${packageName}.fileprovider",
                                    pdfFile
                                )
                                withContext(Dispatchers.Main) {
                                    openDirect(PdfViewerActivity::class.java, pdfUri, "application/pdf")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    status.value = "Image খুলতে ব্যর্থ: ${e.message}"
                                }
                            }
                        }
                        return@LaunchedEffect
                    }
                    FileType.TEXT -> {
                        openDirect(TextViewerActivity::class.java, uri, mimeType.ifEmpty { "text/plain" })
                    }
                    FileType.UNKNOWN -> {
                        // Last resort: try PdfViewerActivity
                        openDirect(PdfViewerActivity::class.java, uri, "application/pdf")
                    }
                }
            }

            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().background(ComposeColor(0xFF111111)),
                    contentAlignment = Alignment.Center
                ) {
                    if (status.value.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)) {
                            Text("⚠️", fontSize = 36.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(status.value, color = ComposeColor(0xFFFF5C5C), fontSize = 13.sp)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { finish() }) { Text("← ফিরে যান") }
                        }
                    } else {
                        // Brief loading indicator while we dispatch to the right viewer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ComposeColor(0xFF6C63FF))
                            Spacer(Modifier.height(12.dp))
                            Text("ফাইল খুলছি…",
                                color = ComposeColor(0xFFF5F5F5), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    private fun openDirect(cls: Class<*>, uri: Uri, mimeType: String) {
        startActivity(Intent(this, cls).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }

    private fun getFileName(uri: Uri?): String {
        if (uri == null) return ""
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }

    // ── File type detection ────────────────────────────────────────────────
    private enum class FileType { PDF, DOCX, PPTX, XLSX, IMAGE, TEXT, UNKNOWN }

    private fun detectType(fileName: String, mimeType: String): FileType {
        val ext  = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeType.lowercase()
        return when {
            ext == "pdf"  || mime == "application/pdf" -> FileType.PDF
            ext in setOf("docx", "doc") ||
                mime.contains("wordprocessingml") ||
                mime == "application/msword" -> FileType.DOCX
            ext in setOf("pptx", "ppt") ||
                mime.contains("presentationml") ||
                mime == "application/vnd.ms-powerpoint" -> FileType.PPTX
            ext in setOf("xlsx", "xls") ||
                mime.contains("spreadsheetml") ||
                mime == "application/vnd.ms-excel" -> FileType.XLSX
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif") ||
                mime.startsWith("image/") -> FileType.IMAGE
            ext in setOf("txt", "md", "markdown", "kt", "java", "py", "js",
                "ts", "html", "css", "xml", "json", "yaml", "yml", "csv",
                "sh", "bat", "c", "cpp", "h", "rs", "go", "rb") ||
                mime.startsWith("text/") -> FileType.TEXT
            else -> FileType.UNKNOWN
        }
    }

    // ── Image → single-page PDF ────────────────────────────────────────────
    private fun imageToPdf(uri: Uri, fileName: String): File {
        val bytes = contentResolver.openInputStream(uri)?.readBytes()
            ?: throw IllegalStateException("Cannot read image")
        val orig = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Cannot decode image")

        // Fit within A4 portrait keeping aspect ratio
        val pageW = 595; val pageH = 842
        val margin = 36f
        val maxW = pageW - margin * 2
        val maxH = pageH - margin * 2
        val scaleX = maxW / orig.width
        val scaleY = maxH / orig.height
        val scale  = minOf(scaleX, scaleY, 1f)   // never upscale
        val dstW = (orig.width * scale).toInt()
        val dstH = (orig.height * scale).toInt()
        val left = margin + (maxW - dstW) / 2f
        val top  = margin + (maxH - dstH) / 2f

        val pdf    = PdfDocument()
        val info   = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page   = pdf.startPage(info)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(orig, null,
            android.graphics.RectF(left, top, left + dstW, top + dstH), null)
        pdf.finishPage(page)
        orig.recycle()

        val cacheDir = File(cacheDir, "converted_pdfs")
        cacheDir.mkdirs()
        val safe = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(cacheDir, "${safe}.pdf")
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}

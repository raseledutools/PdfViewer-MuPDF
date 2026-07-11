package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.artifex.mupdf.viewer.Document
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.cos.COSName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════════════
// STATE
// ═════════════════════════════════════════════════════════════════════════════
data class PdfEngineState(
    val isReady:         Boolean = true,
    val isLoading:       Boolean = false,
    val errorMsg:        String  = "",
    val operationResult: String  = "",

    // viewer
    val fileName:    String = "",
    val totalPages:  Int    = 0,
    val currentPage: Int    = 1,
    val zoomPct:     Int    = 100,
    val fileSizeKb:  Int    = 0,
)

private class EngineDocs {
    var splitDoc:   PDDocument? = null
    var splitName:  String      = ""
    var splitBytes: ByteArray?  = null

    var compressDoc:          PDDocument? = null
    var compressName:         String      = ""
    var compressBytes:        ByteArray?  = null
    var compressOriginalSize: Int         = 0

    var mergeOutputBytes: ByteArray? = null
    var mergeOutputName:  String     = ""

    var splitOutputBytes: ByteArray? = null
    var splitOutputName:  String     = ""

    var compressOutputBytes: ByteArray? = null
    var compressOutputName:  String     = ""

    var imagesToPdfOutputBytes: ByteArray? = null
    var imagesToPdfOutputName:  String     = ""

    var resizeOutputBytes: ByteArray? = null
    var resizeOutputName:  String     = ""

    var pdfToImagesOutputs: List<Pair<String, ByteArray>> = emptyList()

    fun closeAll() {
        splitDoc?.runCatching { close() }
        compressDoc?.runCatching { close() }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// CONTROLLER
// ═════════════════════════════════════════════════════════════════════════════
class PdfEngineController {

    var onStateChange: (PdfEngineState) -> Unit = {}
    private var state = PdfEngineState()
        set(value) { field = value; onStateChange(value) }

    internal lateinit var appContext: Context
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    private val docs = EngineDocs()

    // ── MuPDF viewer state ───────────────────────────────────────────────────
    // MuPDF Document — vector rendering engine (sharp at any zoom level)
    // Unlike android.graphics.pdf.PdfRenderer which pre-rasterizes to a fixed
    // bitmap, MuPDF re-renders from the original vector data at the requested
    // resolution — pages stay crisp whether you zoom to 50% or 500%.
    private var muPdfDoc:  Document?  = null
    private var viewerFile: File?     = null

    // All rendered pages stored here for LazyColumn display
    internal val viewerPages = mutableStateListOf<Bitmap?>()

    // One-shot scroll request for the viewer's LazyColumn. gotoPage() previously
    // only updated state.currentPage (the page NUMBER shown in the top bar) but
    // never told the LazyColumn to actually scroll — so Prev/Next changed the
    // page indicator text while the visible page stayed put. This holds a
    // (targetPageIndex, requestId) pair; requestId always increments so the
    // viewer's LaunchedEffect fires even if the same page is requested twice in
    // a row (a plain state-equality check wouldn't re-trigger for a repeat value).
    internal var scrollRequest by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    private var scrollRequestId = 0

    // screen width in px (set from Composable)
    internal var screenWidthPx: Int = 1080

    private fun setLoading(loading: Boolean) { state = state.copy(isLoading = loading) }
    private fun setError(msg: String)  { state = state.copy(isLoading = false, errorMsg = msg, operationResult = "error:$msg") }
    private fun setResult(result: String) { state = state.copy(isLoading = false, errorMsg = "", operationResult = result) }

    private fun b64ToBytes(b64: String): ByteArray = Base64.decode(b64, Base64.DEFAULT)
    private fun cacheFile(prefix: String): File = File.createTempFile(prefix, ".pdf", appContext.cacheDir)

    // ───────────────────────────────────────────────────────────────────────
    // PDF VIEWER — MuPDF vector rendering (sharp at any zoom)
    // ───────────────────────────────────────────────────────────────────────
    fun loadPdfInViewer(b64: String, name: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = b64ToBytes(b64)
                closeViewer()

                // Write bytes to a temp file — MuPDF Document opens by file path
                val file = cacheFile("viewer_")
                FileOutputStream(file).use { it.write(bytes) }
                viewerFile = file

                // Open document with MuPDF — supports PDF, XPS, EPUB, CBZ, MOBI
                val doc = Document.openDocument(file.absolutePath)
                    ?: throw IllegalStateException("MuPDF: ফাইল খোলা যায়নি")
                muPdfDoc = doc

                val pageCount = doc.countPages()

                // Pre-fill list with nulls so LazyColumn has correct count immediately
                withContext(Dispatchers.Main) {
                    viewerPages.clear()
                    repeat(pageCount) { viewerPages.add(null) }
                    state = state.copy(
                        isLoading       = false,
                        errorMsg        = "",
                        fileName        = name,
                        totalPages      = pageCount,
                        currentPage     = 1,
                        zoomPct         = 100,
                        fileSizeKb      = bytes.size / 1024,
                        operationResult = ""
                    )
                }

                // Render pages progressively (first page first, then rest async)
                for (i in 0 until pageCount) {
                    val bmp = renderMuPdfPage(doc, i, screenWidthPx)
                    withContext(Dispatchers.Main) {
                        if (i < viewerPages.size) viewerPages[i] = bmp
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("PDF খোলা যায়নি: ${e.message}") }
            }
        }
    }

    /**
     * MuPDF vector rendering — renders a single page at [targetWidth] pixels wide.
     * MuPDF re-renders from the original vector data each time, so the bitmap is
     * always sharp at the requested resolution (unlike PdfRenderer which just
     * scales a pre-rasterized bitmap).
     */
    private fun renderMuPdfPage(doc: Document, pageIndex: Int, targetWidth: Int): Bitmap {
        // Get the page size in points (1pt = 1/72 inch)
        val pageSize = doc.getPageSize(pageIndex)
        val origWidth  = pageSize.x.coerceAtLeast(1f)
        val origHeight = pageSize.y.coerceAtLeast(1f)

        // Scale to fit targetWidth while maintaining aspect ratio
        val scale     = targetWidth.toFloat() / origWidth
        val bmpWidth  = targetWidth
        val bmpHeight = (origHeight * scale).roundToInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)

        // MuPDF drawPage() takes direct pixel coordinates — no ctm matrix needed.
        // It internally renders from vector data at exactly the requested resolution.
        doc.drawPage(bmp, pageIndex, bmpWidth, bmpHeight, 0, 0, bmpWidth, bmpHeight, null)
        return bmp
    }

    fun gotoPage(pageIndex: Int) {
        if (pageIndex in 0 until state.totalPages) {
            state = state.copy(currentPage = pageIndex + 1)
            scrollRequestId += 1
            scrollRequest = pageIndex to scrollRequestId
        }
    }

    fun zoomViewer(delta: Int) {
        state = state.copy(zoomPct = (state.zoomPct + delta).coerceIn(50, 400))
    }

    fun updateCurrentPage(pageIndex: Int) {
        if (pageIndex + 1 != state.currentPage) {
            state = state.copy(currentPage = pageIndex + 1)
        }
    }

    fun closeViewer() {
        muPdfDoc?.runCatching { destroy() }
        viewerFile?.runCatching { delete() }
        viewerPages.forEach { it?.recycle() }
        viewerPages.clear()
        muPdfDoc   = null
        viewerFile = null
    }

    // ───────────────────────────────────────────────────────────────────────
    // PDF MERGE
    // ───────────────────────────────────────────────────────────────────────
    fun mergePdfs(jsonStr: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val files = parseFileJsonArray(jsonStr)
                if (files.isEmpty()) throw IllegalStateException("কোনো PDF নির্বাচন করা হয়নি")

                val merged    = PDDocument()
                var totalPages = 0

                files.forEach { (b64, _) ->
                    try {
                        val doc = PDDocument.load(b64ToBytes(b64))
                        doc.pages.forEach { page -> merged.addPage(page) }
                        totalPages += doc.numberOfPages
                        doc.close()
                    } catch (_: Exception) { /* skip corrupted */ }
                }

                if (totalPages == 0) throw IllegalStateException("কোনো valid PDF পাওয়া যায়নি")

                val out  = ByteArrayOutputStream()
                merged.save(out)
                merged.close()

                val outBytes = out.toByteArray()
                val outName  = "merged_${System.currentTimeMillis() / 1000}.pdf"
                docs.mergeOutputBytes = outBytes
                docs.mergeOutputName  = outName

                withContext(Dispatchers.Main) { setResult("merged:$outName:$totalPages") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("Merge ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun saveMergePdfResult() = saveResult(docs.mergeOutputBytes, docs.mergeOutputName)

    // ───────────────────────────────────────────────────────────────────────
    // PDF SPLIT
    // ───────────────────────────────────────────────────────────────────────
    fun loadPdfForSplit(b64: String, name: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = b64ToBytes(b64)
                docs.splitDoc?.runCatching { close() }
                docs.splitDoc   = PDDocument.load(bytes)
                docs.splitName  = name
                docs.splitBytes = bytes
                withContext(Dispatchers.Main) {
                    state = state.copy(
                        isLoading  = false, errorMsg = "",
                        fileName   = name,
                        totalPages = docs.splitDoc?.numberOfPages ?: 0
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("PDF লোড ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun splitPdf(fromPage: Int, toPage: Int) {
        val doc = docs.splitDoc
        if (doc == null) { setError("কোনো PDF লোড করা হয়নি"); return }
        if (fromPage < 1 || toPage < fromPage || toPage > doc.numberOfPages) {
            setError("পাতার পরিসীমা অবৈধ (১-${doc.numberOfPages})"); return
        }
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val freshDoc = PDDocument.load(docs.splitBytes ?: throw IllegalStateException("Source not found"))
                val split    = PDDocument()
                for (i in fromPage - 1 until toPage) {
                    if (i < freshDoc.numberOfPages) split.addPage(freshDoc.getPage(i))
                }
                val out  = ByteArrayOutputStream()
                split.save(out); split.close(); freshDoc.close()

                val outBytes = out.toByteArray()
                val baseName = docs.splitName.substringBeforeLast('.').ifBlank { "split" }
                val outName  = "${baseName}_${fromPage}-${toPage}.pdf"
                docs.splitOutputBytes = outBytes
                docs.splitOutputName  = outName

                withContext(Dispatchers.Main) { setResult("split:$outName:${toPage - fromPage + 1}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("Split ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun saveSplitPdfResult() = saveResult(docs.splitOutputBytes, docs.splitOutputName)

    // ───────────────────────────────────────────────────────────────────────
    // PDF COMPRESS
    // ───────────────────────────────────────────────────────────────────────
    fun loadPdfForCompress(b64: String, name: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = b64ToBytes(b64)
                docs.compressDoc?.runCatching { close() }
                docs.compressDoc          = PDDocument.load(bytes)
                docs.compressName         = name
                docs.compressBytes        = bytes
                docs.compressOriginalSize = bytes.size / 1024
                withContext(Dispatchers.Main) {
                    state = state.copy(
                        isLoading  = false, errorMsg = "",
                        fileName   = name,
                        totalPages = docs.compressDoc?.numberOfPages ?: 0,
                        fileSizeKb = bytes.size / 1024
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("PDF লোড ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun compressPdf(quality: Int) {
        val bytes = docs.compressBytes
        if (bytes == null) { setError("কোনো PDF লোড করা হয়নি"); return }
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val doc = PDDocument.load(bytes)
                val jpegQuality = quality.coerceIn(10, 95) / 100f

                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames.toList()) {
                        try {
                            val xobj = resources.getXObject(name)
                            if (xobj !is PDImageXObject) continue
                            val bmp = xobj.image ?: continue

                            // Re-encode this image at the requested JPEG quality —
                            // this is what actually shrinks the file; skipping this
                            // step (as the old code did) meant "Compress" only
                            // re-serialized the PDF with no real size change.
                            val recompressed = JPEGFactory.createFromImage(doc, bmp, jpegQuality)
                            resources.put(name as COSName, recompressed)
                        } catch (_: Exception) {
                            // Leave this one image as-is rather than aborting the
                            // whole compress operation over a single bad image.
                        }
                    }
                }

                val out = ByteArrayOutputStream()
                doc.save(out); doc.close()

                val outBytes     = out.toByteArray()
                val baseName     = docs.compressName.substringBeforeLast('.').ifBlank { "compressed" }
                val outName      = "${baseName}_compressed.pdf"
                val originalSize = docs.compressOriginalSize
                val newSize      = outBytes.size / 1024
                val ratio        = (100 - (newSize * 100 / originalSize.coerceAtLeast(1))).coerceAtLeast(0)

                docs.compressOutputBytes = outBytes
                docs.compressOutputName  = outName

                withContext(Dispatchers.Main) {
                    setResult("compressed:$outName:$originalSize KB→$newSize KB (-$ratio%)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("Compress ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun saveCompressPdfResult() = saveResult(docs.compressOutputBytes, docs.compressOutputName)

    // ───────────────────────────────────────────────────────────────────────
    // PDF → IMAGES  (MuPDF rendering — sharp output images)
    // ───────────────────────────────────────────────────────────────────────
    fun pdfToImages(b64: String, name: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes   = b64ToBytes(b64)
                val pdfFile = cacheFile("pdf_to_img_")
                FileOutputStream(pdfFile).use { it.write(bytes) }

                // Use MuPDF for vector-quality image extraction
                val doc     = Document.openDocument(pdfFile.absolutePath)
                    ?: throw IllegalStateException("MuPDF: ফাইল খোলা যায়নি")
                val results = mutableListOf<Pair<String, ByteArray>>()

                for (i in 0 until doc.countPages()) {
                    val bmp = renderMuPdfPage(doc, i, 1080)
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    bmp.recycle()
                    val baseName = name.substringBeforeLast('.').ifBlank { "page" }
                    results.add("${baseName}_page${i + 1}.jpg" to out.toByteArray())
                }

                doc.destroy()
                pdfFile.delete()
                docs.pdfToImagesOutputs = results

                withContext(Dispatchers.Main) { setResult("pdfToImages:${results.size} images extracted") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("PDF→Image ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun savePdfToImagesResults() {
        scope.launch(Dispatchers.IO) {
            try {
                docs.pdfToImagesOutputs.forEach { (n, b) -> saveBytesToDownloads(b, n) }
                withContext(Dispatchers.Main) { setResult("saved:${docs.pdfToImagesOutputs.size} images") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("সংরক্ষণ ব্যর্থ: ${e.message}") }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // IMAGES → PDF
    // ───────────────────────────────────────────────────────────────────────
    fun imagesToPdf(jsonStr: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val files = parseFileJsonArray(jsonStr)
                if (files.isEmpty()) throw IllegalStateException("কোনো ছবি নির্বাচন করা হয়নি")

                val doc = PDDocument()
                files.forEach { (b64, name) ->
                    try {
                        // FIX: b64ToBytes(b64) ছিল দুইবার call হচ্ছিল — একবার decodeByteArray
                        // এর data argument এ, আরেকবার শুধু .size এর জন্য। মানে প্রতিটা ছবির
                        // জন্য base64 decode ডাবল হচ্ছিল (CPU + temp memory দুইগুণ খরচ,
                        // অনেকগুলো/বড় ছবি একসাথে দিলে slow বা OOM হওয়ার ঝুঁকি বাড়ায়)।
                        // এখন একবার decode করে variable এ রেখে দুই জায়গায় reuse করা হচ্ছে।
                        val imgBytes = b64ToBytes(b64)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(
                            imgBytes, 0, imgBytes.size
                        ) ?: throw IllegalStateException("ছবি: $name ডিকোড ব্যর্থ")

                        val pdfWidth  = bmp.width.toFloat()
                        val pdfHeight = bmp.height.toFloat()
                        val page      = PDPage(PDRectangle(pdfWidth, pdfHeight))
                        val stream    = PDPageContentStream(doc, page)
                        val xImage    = if (name.endsWith(".png", true))
                            LosslessFactory.createFromImage(doc, bmp)
                        else
                            JPEGFactory.createFromImage(doc, bmp)
                        stream.drawImage(xImage, 0f, 0f, pdfWidth, pdfHeight)
                        stream.close()
                        doc.addPage(page)
                        bmp.recycle()
                    } catch (_: Exception) { /* skip corrupted */ }
                }

                val out  = ByteArrayOutputStream()
                doc.save(out); doc.close()

                val outBytes = out.toByteArray()
                val outName  = "images_to_pdf_${System.currentTimeMillis() / 1000}.pdf"
                docs.imagesToPdfOutputBytes = outBytes
                docs.imagesToPdfOutputName  = outName

                withContext(Dispatchers.Main) {
                    setResult("imagesToPdf:$outName created with ${files.size} images")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("Image→PDF ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun saveImagesToPdfResult() = saveResult(docs.imagesToPdfOutputBytes, docs.imagesToPdfOutputName)

    // ───────────────────────────────────────────────────────────────────────
    // IMAGE RESIZE
    // ───────────────────────────────────────────────────────────────────────
    fun resizeImage(imgB64: String, fileName: String, width: Int, height: Int) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes  = b64ToBytes(imgB64)
                val bmp    = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("ছবি ডিকোড ব্যর্থ")
                val resized = Bitmap.createScaledBitmap(bmp, max(1, width), max(1, height), true)
                bmp.recycle()

                val ext    = fileName.substringAfterLast('.', "jpg").lowercase()
                val format = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val out    = ByteArrayOutputStream()
                resized.compress(format, 92, out)
                resized.recycle()

                val outBytes = out.toByteArray()
                val baseName = fileName.substringBeforeLast('.').ifBlank { "resized" }
                val outExt   = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
                val outName  = "${baseName}_${width}x${height}.$outExt"

                docs.resizeOutputBytes = outBytes
                docs.resizeOutputName  = outName

                withContext(Dispatchers.Main) { setResult("resized:$outName resized to ${width}x${height}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("Resize ব্যর্থ: ${e.message}") }
            }
        }
    }

    fun saveResizeResult() = saveResult(docs.resizeOutputBytes, docs.resizeOutputName)

    // ───────────────────────────────────────────────────────────────────────
    // HELPERS
    // ───────────────────────────────────────────────────────────────────────
    private fun saveResult(bytes: ByteArray?, fileName: String) {
        if (bytes == null) { setError("সংরক্ষণের জন্য কোনো ফলাফল নেই"); return }
        scope.launch(Dispatchers.IO) {
            try {
                val saved = saveBytesToDownloads(bytes, fileName)
                withContext(Dispatchers.Main) { setResult("saved:$saved") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setError("সংরক্ষণ ব্যর্থ: ${e.message}") }
            }
        }
    }

    private fun parseFileJsonArray(json: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < json.length) {
            val objStart = json.indexOf('{', i); if (objStart == -1) break
            val objEnd   = json.indexOf('}', objStart); if (objEnd == -1) break
            val obj      = json.substring(objStart + 1, objEnd)
            val base64   = extractJsonField(obj, "base64")
            val name     = extractJsonField(obj, "name")
            if (base64 != null) results.add(base64 to (name ?: ""))
            i = objEnd + 1
        }
        return results
    }

    private fun extractJsonField(obj: String, field: String): String? {
        val key        = "\"$field\":\""
        val start      = obj.indexOf(key); if (start == -1) return null
        val valueStart = start + key.length
        val valueEnd   = obj.indexOf('"', valueStart); if (valueEnd == -1) return null
        return obj.substring(valueStart, valueEnd)
    }

    private fun saveBytesToDownloads(bytes: ByteArray, fileName: String): String {
        val resolver = appContext.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val mime = when {
                fileName.endsWith(".pdf", true) -> "application/pdf"
                fileName.endsWith(".png", true) -> "image/png"
                else                            -> "image/jpeg"
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert ব্যর্থ")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Output stream খোলা ব্যর্থ")
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
        }
        return fileName
    }

    fun release() {
        closeViewer()
        docs.closeAll()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PdfEngine(controller: PdfEngineController) {
    val context = LocalContext.current
    val density = LocalDensity.current
    DisposableEffect(controller) {
        controller.appContext = context.applicationContext
        PDFBoxResourceLoader.init(context.applicationContext)
        onDispose { controller.release() }
    }
    // Pass real screen width to controller for correct bitmap sizing
    BoxWithConstraints(Modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(720)
        LaunchedEffect(widthPx) { controller.screenWidthPx = widthPx }
    }
}

/**
 * WPS-style viewer:
 * - Continuous vertical scroll through all pages (LazyColumn)
 * - Pinch-to-zoom on individual page
 * - Pages render progressively (null = placeholder)
 * - Correct aspect ratio per page
 */
@Composable
fun PdfViewerWebView(controller: PdfEngineController, modifier: Modifier = Modifier) {
    val pages     = controller.viewerPages
    val listState = rememberLazyListState()

    // FIX: gotoPage() (called by the Prev/Next buttons) previously only updated
    // the page-NUMBER shown in the top bar (engineState.currentPage) — nothing
    // ever told this LazyColumn to actually scroll, so pressing Prev/Next moved
    // the displayed number while the visible page stayed exactly where it was.
    // This reacts to controller.scrollRequest (a (pageIndex, requestId) pair)
    // and performs the real scroll. Keyed on the full pair, not just pageIndex,
    // so a second tap that requests the same page again still re-triggers —
    // requestId always increments even for a repeat page value.
    LaunchedEffect(controller.scrollRequest) {
        controller.scrollRequest?.let { (pageIndex, _) ->
            listState.animateScrollToItem(pageIndex)
        }
    }

    // Update current page indicator as user scrolls
    val visibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(visibleIndex) {
        controller.updateCurrentPage(visibleIndex)
    }

    LazyColumn(
        state   = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding      = PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(pages) { _, bmp ->
            if (bmp == null) {
                // Placeholder while page renders
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f) // A4 ratio fallback
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier    = Modifier.size(28.dp),
                        color       = Color(0xFF4FACFE),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                // Per-page pinch zoom
                var scale   by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                // FIX: swiping up/down previously did nothing because
                // detectTransformGestures ran on every page unconditionally and
                // claims pointer input the moment it starts — a plain
                // single-finger vertical drag was being consumed here as a pan
                // gesture instead of ever reaching the parent LazyColumn's scroll.
                // Checking pointer count *inside* detectTransformGestures's
                // callback doesn't help either, since arbitration already
                // happened by the time the callback runs.
                // Fix: a custom detector using PointerEventPass.Initial, which
                // runs BEFORE normal gesture arbitration. With fewer than 2
                // pointers down, it does nothing and never calls consume() — the
                // touch passes straight through untouched, so the LazyColumn is
                // free to claim it as a scroll. Only once a second finger is down
                // (an actual pinch) does this start tracking centroid pan and
                // inter-finger distance for zoom. Works from any zoom level,
                // including 100% — no need to zoom via button first.
                val zoomModifier = Modifier.pointerInput(bmp) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.filter { it.pressed }
                            if (down.size < 2) continue  // let the LazyColumn handle it

                            val p1 = down[0]
                            val p2 = down[1]
                            val prevDist = (p1.previousPosition - p2.previousPosition).getDistance()
                            val currDist = (p1.position - p2.position).getDistance()
                            val prevCentroid = (p1.previousPosition + p2.previousPosition) / 2f
                            val currCentroid = (p1.position + p2.position) / 2f

                            if (prevDist > 0f) {
                                val zoomFactor = currDist / prevDist
                                scale = (scale * zoomFactor).coerceIn(1f, 4f)
                            }
                            offsetX += currCentroid.x - prevCentroid.x
                            offsetY += currCentroid.y - prevCentroid.y
                            if (scale == 1f) { offsetX = 0f; offsetY = 0f }

                            event.changes.forEach { it.consume() }
                        }
                    }
                }

                Image(
                    bitmap              = bmp.asImageBitmap(),
                    contentDescription  = null,
                    contentScale        = ContentScale.FillWidth,
                    modifier            = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .then(zoomModifier)
                        .graphicsLayer(
                            scaleX       = scale,
                            scaleY       = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
        }
    }
}
package com.rasel.RasFocus.selfcontrol.study_tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
// FIX: the in-app PDF-viewing responsibility (page bitmaps, scroll position,
// zoom) has moved to PdfViewerActivity.kt, which uses MuPDF fitz for accurate
// rendering, working pinch-zoom pan, and a foundation for real text selection.
// fileName/totalPages/fileSizeKb stay here — they're shared "loaded file"
// info that Split and Compress also populate (via loadPdfForSplit /
// loadPdfForCompress) to show the user what they just picked. currentPage
// and zoomPct were viewer-only scroll/zoom state and are gone.
// ═════════════════════════════════════════════════════════════════════════════
data class PdfEngineState(
    val isReady:         Boolean = true,
    val isLoading:       Boolean = false,
    val errorMsg:        String  = "",
    val operationResult: String  = "",

    val fileName:    String = "",
    val totalPages:  Int    = 0,
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

    private fun setLoading(loading: Boolean) { state = state.copy(isLoading = loading) }
    private fun setError(msg: String)  { state = state.copy(isLoading = false, errorMsg = msg, operationResult = "error:$msg") }
    private fun setResult(result: String) { state = state.copy(isLoading = false, errorMsg = "", operationResult = result) }

    private fun b64ToBytes(b64: String): ByteArray = Base64.decode(b64, Base64.DEFAULT)
    private fun cacheFile(prefix: String): File = File.createTempFile(prefix, ".pdf", appContext.cacheDir)

    // Used by pdfToImages() below to rasterize each page of a PDF into a
    // bitmap for JPG export. (Previously also used by the in-app PDF viewer,
    // which now lives in PdfViewerActivity.kt using MuPDF instead.)
    private fun renderSinglePage(renderer: PdfRenderer, pageIndex: Int, targetWidth: Int): Bitmap {
        val page       = renderer.openPage(pageIndex)
        val origWidth  = page.width.coerceAtLeast(1)
        val origHeight = page.height.coerceAtLeast(1)
        val scale      = targetWidth.toFloat() / origWidth
        val bmpWidth   = targetWidth
        val bmpHeight  = (origHeight * scale).roundToInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bmp
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
    // PDF → IMAGES
    // ───────────────────────────────────────────────────────────────────────
    fun pdfToImages(b64: String, name: String) {
        setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val bytes   = b64ToBytes(b64)
                val pdfFile = cacheFile("pdf_to_img_")
                FileOutputStream(pdfFile).use { it.write(bytes) }

                val fd       = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val results  = mutableListOf<Pair<String, ByteArray>>()

                for (i in 0 until renderer.pageCount) {
                    val bmp = renderSinglePage(renderer, i, 1080)
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    bmp.recycle()
                    val baseName = name.substringBeforeLast('.').ifBlank { "page" }
                    results.add("${baseName}_page${i + 1}.jpg" to out.toByteArray())
                }

                renderer.close(); fd.close(); pdfFile.delete()
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
        docs.closeAll()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PdfEngine(controller: PdfEngineController) {
    val context = LocalContext.current
    DisposableEffect(controller) {
        controller.appContext = context.applicationContext
        PDFBoxResourceLoader.init(context.applicationContext)
        onDispose { controller.release() }
    }
}
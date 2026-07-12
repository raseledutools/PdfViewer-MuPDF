package com.rasel.RasFocus.combo.selfcontrol.study_tools

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// ═════════════════════════════════════════════════════════════════════════════
// LIGHT STUB — pdfbox-android নেই এই flavor-এ।
// Full flavor-এ আসল implementation আছে (com.tom_roush.pdfbox দিয়ে)।
// Public API surface PdfToolsScreen.kt compile করার জন্য মিলিয়ে রাখা হয়েছে।
// ═════════════════════════════════════════════════════════════════════════════

data class PdfEngineState(
    val isReady:         Boolean = true,
    val isLoading:       Boolean = false,
    val errorMsg:        String  = "এই ফিচার light build-এ উপলব্ধ নয়",
    val operationResult: String  = "",

    val fileName:    String = "",
    val totalPages:  Int    = 0,
    val currentPage: Int    = 1,
    val zoomPct:     Int    = 100,
    val fileSizeKb:  Int    = 0,
)

class PdfEngineController {

    var onStateChange: (PdfEngineState) -> Unit = {}
    private var state = PdfEngineState()
        set(value) { field = value; onStateChange(value) }

    internal lateinit var appContext: Context
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    internal var screenWidthPx: Int = 1080
    internal val viewerPages = mutableStateListOf<Bitmap?>()

    private fun notAvailable() {
        state = state.copy(errorMsg = "PDF Tools light build-এ উপলব্ধ নয়। Full version ব্যবহার করুন।")
    }

    fun loadPdfInViewer(b64: String, name: String) = notAvailable()
    fun gotoPage(pageIndex: Int) = notAvailable()
    fun zoomViewer(delta: Int) = notAvailable()
    fun updateCurrentPage(pageIndex: Int) = notAvailable()
    fun closeViewer() = notAvailable()

    fun mergePdfs(jsonStr: String) = notAvailable()
    fun saveMergePdfResult() = notAvailable()

    fun loadPdfForSplit(b64: String, name: String) = notAvailable()
    fun splitPdf(fromPage: Int, toPage: Int) = notAvailable()
    fun saveSplitPdfResult() = notAvailable()

    fun loadPdfForCompress(b64: String, name: String) = notAvailable()
    fun compressPdf(quality: Int) = notAvailable()
    fun saveCompressPdfResult() = notAvailable()

    fun pdfToImages(b64: String, name: String) = notAvailable()
    fun savePdfToImagesResults() = notAvailable()

    fun imagesToPdf(jsonStr: String) = notAvailable()
    fun saveImagesToPdfResult() = notAvailable()

    fun resizeImage(imgB64: String, fileName: String, width: Int, height: Int) = notAvailable()
    fun saveResizeResult() = notAvailable()

    fun release() {
        viewerPages.clear()
    }
}

@Composable
fun PdfEngine(controller: PdfEngineController) {
    // Light build — কিছুই init করার নেই, pdfbox loader নেই।
}

@Composable
fun PdfViewerWebView(controller: PdfEngineController, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("PDF Viewer light build-এ উপলব্ধ নয়")
    }
}

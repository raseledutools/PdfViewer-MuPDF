package com.rasel.RasFocus.selfcontrol.study_tools

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ─── Colors ───────────────────────────────────────────────────────────────────
private val ScanBg         = Color(0xFF0A0A0F)
private val ScanSurface    = Color(0xFF141420)
private val ScanCard       = Color(0xFF1C1C2E)
private val ScanAccent     = Color(0xFF6C63FF)
private val ScanAccent2    = Color(0xFF00D4AA)
private val ScanText       = Color(0xFFF0F0FF)
private val ScanSubText    = Color(0xFF8888AA)
private val ScanDivider    = Color(0xFF2A2A40)

// ─── Data Model ───────────────────────────────────────────────────────────────
data class ScannedDoc(val name: String, val path: String, val date: String, val size: String)

// ─── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var docs          by remember { mutableStateOf(loadDocs(context)) }
    var imagesArray   by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var autoCrop      by remember { mutableStateOf(true) }
    var currentFilter by remember { mutableStateOf("magic-pro") }
    var pageSize      by remember { mutableStateOf("fit") }
    var isGenerating  by remember { mutableStateOf(false) }
    var genProgress   by remember { mutableStateOf(0f) }
    var genText       by remember { mutableStateOf("0 / 0") }
    var showPdfImportModal  by remember { mutableStateOf<Uri?>(null) }
    var showPickerSheet     by remember { mutableStateOf(false) }
    var showFilterSheet     by remember { mutableStateOf(false) }

    // ── Permissions ───────────────────────────────────────────────────────────
    val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    else emptyArray()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { v -> v }) docs = loadDocs(context)
    }
    LaunchedEffect(Unit) {
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED })
            permLauncher.launch(permissions)
    }

    // ── Launchers ─────────────────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK)
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.let { pages ->
                imagesArray = imagesArray + pages.map { it.imageUri }
            }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) imagesArray = imagesArray + uris
    }
    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) showPdfImportModal = uri
    }

    // ── Generate Action ───────────────────────────────────────────────────────
    val scope = rememberCoroutineScope()
    val onGenerate = {
        if (imagesArray.isNotEmpty()) {
            isGenerating = true
            scope.launch(Dispatchers.IO) {
                try {
                    buildPdfFromImages(context, imagesArray, currentFilter, autoCrop, pageSize) { done, total ->
                        genProgress = done.toFloat() / total
                        genText = "$done / $total"
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF Saved!", Toast.LENGTH_SHORT).show()
                        imagesArray = emptyList()
                        docs = loadDocs(context)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) { isGenerating = false }
                }
            }
        }
        Unit
    }

    // ── fun to launch camera scan ─────────────────────────────────────────────
    val launchCameraScan = {
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { cameraLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { Toast.makeText(context, "Scanner error: ${it.message}", Toast.LENGTH_SHORT).show() }
        Unit
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(ScanBg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), ScanBg)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp).background(ScanSurface, CircleShape)
                    ) { Icon(Icons.Default.ArrowBack, null, tint = ScanText, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "RasScanner",
                            color = ScanText, fontSize = 22.sp, fontWeight = FontWeight.Black
                        )
                        Text("300 DPI • Premium Quality", color = ScanAccent2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (imagesArray.isNotEmpty()) {
                        TextButton(
                            onClick = { imagesArray = emptyList() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B))
                        ) { Text("Clear", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Quick Action Row ──────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Scan
                    ScanActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Scan",
                        sub = "Camera",
                        gradient = Brush.verticalGradient(listOf(Color(0xFF6C63FF), Color(0xFF4F46E5))),
                        modifier = Modifier.weight(1f)
                    ) { launchCameraScan() }
                    // Gallery
                    ScanActionButton(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Gallery",
                        sub = "Multi-select",
                        gradient = Brush.verticalGradient(listOf(Color(0xFF00D4AA), Color(0xFF00A888))),
                        modifier = Modifier.weight(1f)
                    ) {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                    // Import
                    ScanActionButton(
                        icon = Icons.Default.AddPhotoAlternate,
                        label = "Import",
                        sub = "PDF / Image",
                        gradient = Brush.verticalGradient(listOf(Color(0xFFFF8C42), Color(0xFFFF6B1A))),
                        modifier = Modifier.weight(1f)
                    ) { showPickerSheet = true }
                }

                Spacer(Modifier.height(16.dp))

                // ── Settings Row ──────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filter chip
                    val filterLabels = mapOf(
                        "none" to "Original", "magic-pro" to "✨ Magic", "print-pro" to "🖨 Print",
                        "clear-pro" to "💎 Clear", "super-bw" to "◐ B&W"
                    )
                    ScanChip(
                        icon = Icons.Default.AutoFixHigh,
                        label = filterLabels[currentFilter] ?: "Filter",
                        modifier = Modifier.weight(1f)
                    ) { showFilterSheet = true }
                    // Page size chip
                    ScanChip(
                        icon = Icons.Default.Article,
                        label = if (pageSize == "a4") "A4 (300dpi)" else "Fit Image",
                        modifier = Modifier.weight(1f)
                    ) { pageSize = if (pageSize == "a4") "fit" else "a4" }
                    // Auto-crop chip
                    ScanChip(
                        icon = if (autoCrop) Icons.Default.Crop else Icons.Default.CropFree,
                        label = if (autoCrop) "Auto Crop" else "No Crop",
                        active = autoCrop,
                        modifier = Modifier.weight(1f)
                    ) { autoCrop = !autoCrop }
                }

                Spacer(Modifier.height(20.dp))

                // ── Image Grid ────────────────────────────────────────────────
                if (imagesArray.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(ScanSurface)
                            .border(1.dp, ScanDivider, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(72.dp)
                                    .background(Brush.radialGradient(listOf(ScanAccent.copy(.2f), Color.Transparent)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, tint = ScanAccent, modifier = Modifier.size(36.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("No pages yet", color = ScanText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Tap Scan, Gallery or Import above", color = ScanSubText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    // Page count badge
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.background(ScanAccent.copy(.15f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${imagesArray.size} page${if (imagesArray.size > 1) "s" else ""}",
                                color = ScanAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { showPickerSheet = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = ScanAccent2)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add more", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(imagesArray.size) { index ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ScanCard)
                            ) {
                                FilteredPreviewImage(context, imagesArray[index], currentFilter)
                                // Delete
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                        .size(22.dp).background(Color(0xFFFF4444), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(onClick = {
                                        imagesArray = imagesArray.toMutableList().also { it.removeAt(index) }
                                    }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                // Page number
                                Box(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                        .background(ScanAccent, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Generate Button ───────────────────────────────────────────
                AnimatedVisibility(
                    visible = imagesArray.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = { onGenerate() },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(ScanAccent, Color(0xFF00D4AA))),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Generate PDF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // ── Saved Docs ────────────────────────────────────────────────
                if (docs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Scans", color = ScanText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${docs.size} files", color = ScanSubText, fontSize = 13.sp)
                    }
                    docs.forEach { doc ->
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ScanCard)
                                .clickable {
                                    context.startActivity(Intent(context, PdfViewerActivity::class.java).apply {
                                        putExtra("pdf_path", doc.path)
                                        putExtra("pdf_name", doc.name)
                                    })
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp)
                                        .background(ScanAccent.copy(.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, tint = ScanAccent, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.name, color = ScanText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${doc.date} • ${doc.size}", color = ScanSubText, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                                }
                                IconButton(onClick = { sharePdf(context, doc) }) {
                                    Icon(Icons.Default.Share, null, tint = ScanAccent2, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        // ── Generating Overlay ────────────────────────────────────────────────
        if (isGenerating) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    CircularProgressIndicator(
                        progress = { genProgress },
                        color = ScanAccent,
                        trackColor = ScanSurface,
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Creating PDF...", color = ScanText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(genText, color = ScanAccent, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(ScanSurface)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(genProgress)
                                .background(Brush.horizontalGradient(listOf(ScanAccent, ScanAccent2)))
                        )
                    }
                }
            }
        }

        // ── Image Picker Bottom Sheet (Telegram-style) ────────────────────────
        if (showPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPickerSheet = false },
                containerColor = ScanCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                    Box(modifier = Modifier.width(36.dp).height(4.dp).background(ScanDivider, CircleShape).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(20.dp))
                    Text("Add Pages", color = ScanText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    PickerRow(
                        icon = Icons.Default.CameraAlt,
                        label = "Scan with Camera",
                        sub = "Auto edge detection",
                        color = ScanAccent
                    ) { showPickerSheet = false; launchCameraScan() }
                    HorizontalDivider(color = ScanDivider, modifier = Modifier.padding(vertical = 4.dp))
                    PickerRow(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Choose from Gallery",
                        sub = "Multiple selection supported",
                        color = ScanAccent2
                    ) {
                        showPickerSheet = false
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                    HorizontalDivider(color = ScanDivider, modifier = Modifier.padding(vertical = 4.dp))
                    PickerRow(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Import PDF",
                        sub = "Merge pages into this document",
                        color = Color(0xFFFF8C42)
                    ) { showPickerSheet = false; pdfPickerLauncher.launch("application/pdf") }
                }
            }
        }

        // ── Filter Bottom Sheet ───────────────────────────────────────────────
        if (showFilterSheet) {
            val filters = listOf(
                Triple("none",      "Original",  "No processing"),
                Triple("magic-pro", "✨ Magic Pro", "Best for documents"),
                Triple("print-pro", "🖨 Print Pro", "High contrast print"),
                Triple("clear-pro", "💎 Clear Pro",  "Enhanced clarity"),
                Triple("super-bw",  "◐ Super B&W",  "Black & white sharp")
            )
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = ScanCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                    Box(modifier = Modifier.width(36.dp).height(4.dp).background(ScanDivider, CircleShape).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(20.dp))
                    Text("Image Filter", color = ScanText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    filters.forEach { (key, name, desc) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (currentFilter == key) ScanAccent.copy(.12f) else Color.Transparent)
                                .clickable { currentFilter = key; showFilterSheet = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = ScanText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(desc, color = ScanSubText, fontSize = 12.sp)
                            }
                            if (currentFilter == key)
                                Icon(Icons.Default.CheckCircle, null, tint = ScanAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // ── PDF Import Modal ──────────────────────────────────────────────────
        if (showPdfImportModal != null) {
            Dialog(onDismissRequest = { showPdfImportModal = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = ScanCard)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Import PDF", color = ScanText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Add pages from this PDF into current scan?", color = ScanSubText, fontSize = 14.sp)
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { showPdfImportModal = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ScanDivider),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScanSubText)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val uri = showPdfImportModal
                                    showPdfImportModal = null
                                    if (uri != null) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                                                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                                val tempUris = mutableListOf<Uri>()
                                                for (i in 0 until renderer.pageCount) {
                                                    val page = renderer.openPage(i)
                                                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                                                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                    page.close()
                                                    val tmpFile = File.createTempFile("pdf_pg_$i", ".jpg", context.cacheDir)
                                                    FileOutputStream(tmpFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                                                    tempUris.add(Uri.fromFile(tmpFile))
                                                }
                                                renderer.close()
                                                withContext(Dispatchers.Main) { imagesArray = imagesArray + tempUris }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ScanAccent)
                            ) { Text("Import", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Reusable UI components ────────────────────────────────────────────────────

@Composable
private fun ScanActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, sub: String,
    gradient: Brush, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.height(88.dp).clip(RoundedCornerShape(16.dp))
            .background(gradient).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.White.copy(.75f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ScanChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, active: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active) ScanAccent.copy(.15f) else ScanSurface)
            .border(1.dp, if (active) ScanAccent.copy(.4f) else ScanDivider, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (active) ScanAccent else ScanSubText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (active) ScanAccent else ScanSubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, sub: String, color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(color.copy(.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, color = ScanText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(sub, color = ScanSubText, fontSize = 12.sp)
        }
    }
}

// ─── Image Preview ─────────────────────────────────────────────────────────────
@Composable
fun FilteredPreviewImage(context: Context, uri: Uri, filterType: String) {
    val bitmap by produceState<Bitmap?>(null, uri, filterType) {
        value = withContext(Dispatchers.IO) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                else MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                bmp.copy(Bitmap.Config.ARGB_8888, true) ?: bmp
            } catch (e: Exception) { null }
        }
    }
    if (bitmap != null) {
        val mat = getFilterMatrix(filterType)
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(mat.array)
            )
        )
    }
}

// ─── Filter Matrix ─────────────────────────────────────────────────────────────
fun getFilterMatrix(filter: String): ColorMatrix {
    val cm = ColorMatrix()
    when (filter) {
        "magic-pro" -> {
            cm.setSaturation(0f)
            val contrast = ColorMatrix(floatArrayOf(
                1.6f,0f,0f,0f,-40f, 0f,1.6f,0f,0f,-40f, 0f,0f,1.6f,0f,-40f, 0f,0f,0f,1f,0f
            ))
            cm.postConcat(contrast)
        }
        "print-pro" -> {
            cm.setSaturation(0f)
            val contrast = ColorMatrix(floatArrayOf(
                2.2f,0f,0f,0f,-80f, 0f,2.2f,0f,0f,-80f, 0f,0f,2.2f,0f,-80f, 0f,0f,0f,1f,0f
            ))
            cm.postConcat(contrast)
        }
        "clear-pro" -> {
            val contrast = ColorMatrix(floatArrayOf(
                1.3f,0f,0f,0f,-20f, 0f,1.3f,0f,0f,-20f, 0f,0f,1.3f,0f,-20f, 0f,0f,0f,1f,0f
            ))
            cm.postConcat(contrast)
        }
        "super-bw" -> {
            cm.setSaturation(0f)
            val contrast = ColorMatrix(floatArrayOf(
                3.0f,0f,0f,0f,-120f, 0f,3.0f,0f,0f,-120f, 0f,0f,3.0f,0f,-120f, 0f,0f,0f,1f,0f
            ))
            cm.postConcat(contrast)
        }
    }
    return cm
}

// ─── PDF Builder ───────────────────────────────────────────────────────────────
fun buildPdfFromImages(
    context: Context, uris: List<Uri>, filterType: String,
    autoCrop: Boolean, pageSize: String,
    onProgress: (Int, Int) -> Unit
): File {
    val dir  = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ?: context.filesDir
    val name = "RasScanner_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    val file = File(dir, "$name.pdf")
    val doc  = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.colorFilter = ColorMatrixColorFilter(getFilterMatrix(filterType))

    for (i in uris.indices) {
        onProgress(i + 1, uris.size)
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uris[i]))
        else MediaStore.Images.Media.getBitmap(context.contentResolver, uris[i])
        val bmp = raw.copy(Bitmap.Config.ARGB_8888, true) ?: raw

        val pageW: Int
        val pageH: Int
        if (pageSize == "a4") {
            // A4 at 300 DPI — CamScanner quality
            pageW = 2480
            pageH = 3508
        } else {
            pageW = bmp.width
            pageH = bmp.height
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, doc.pages.size + 1).create()
        val page     = doc.startPage(pageInfo)
        val scale    = minOf(pageW.toFloat() / bmp.width, pageH.toFloat() / bmp.height)
        val w        = bmp.width * scale
        val h        = bmp.height * scale
        val left     = (pageW - w) / 2f
        val top      = (pageH - h) / 2f
        page.canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + w, top + h), paint)
        doc.finishPage(page)
        bmp.recycle()
    }

    file.outputStream().use { doc.writeTo(it) }
    doc.close()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RasFocus")
            }
            val contentUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (contentUri != null) {
                context.contentResolver.openOutputStream(contentUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            }
        } catch (_: Exception) {}
    }
    return file
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
fun loadDocs(context: Context): List<ScannedDoc> {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return emptyList()
    return dir.listFiles { f -> f.name.startsWith("RasScanner_") && f.name.endsWith(".pdf") }
        ?.sortedByDescending { it.lastModified() }
        ?.map { f ->
            val kb = f.length() / 1024
            val size = if (kb > 1024) "${"%.1f".format(kb / 1024f)} MB" else "$kb KB"
            val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(f.lastModified()))
            ScannedDoc(f.nameWithoutExtension, f.absolutePath, date, size)
        } ?: emptyList()
}

fun sharePdf(context: Context, doc: ScannedDoc) {
    try {
        val file = File(doc.path)
        val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"
        ))
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

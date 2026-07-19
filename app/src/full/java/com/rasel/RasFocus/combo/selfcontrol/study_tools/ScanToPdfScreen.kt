package com.rasel.RasFocus.combo.selfcontrol.study_tools

import com.rasel.RasFocus.selfcontrol.study_tools.PdfViewerActivity

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

// ─── Data Models ──────────────────────────────────────────────────────────────

data class ScannedDoc(
    val name : String,
    val path : String,
    val date : String,
    val size : String
)

// ─── Entry Point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var docs by remember { mutableStateOf(loadDocs(context)) }
    var imagesArray by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    // Settings state
    var autoCrop by remember { mutableStateOf(true) }
    var currentFilter by remember { mutableStateOf("magic-pro") }
    var pageSize by remember { mutableStateOf("fit") }
    
    var isGenerating by remember { mutableStateOf(false) }
    var generatingProgress by remember { mutableStateOf(0f) }
    var generatingText by remember { mutableStateOf("0 / 0") }
    
    var showPdfImportModal by remember { mutableStateOf<Uri?>(null) }

    // --- Permissions ---
    val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        emptyArray() 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) docs = loadDocs(context)
    }

    LaunchedEffect(Unit) {
        if (permissions.isNotEmpty()) {
            if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
                permissionLauncher.launch(permissions)
            }
        }
    }

    // --- Launchers ---
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            res?.pages?.let { pages ->
                imagesArray = imagesArray + pages.map { it.imageUri }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            imagesArray = imagesArray + uris
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            showPdfImportModal = uri
        }
    }

    // --- PDF Generate Action ---
    val scope = rememberCoroutineScope()
    val generatePdfAction = {
        if (imagesArray.isNotEmpty()) {
            isGenerating = true
            scope.launch(Dispatchers.IO) {
                try {
                    val file = buildPdfFromImages(
                        context = context,
                        uris = imagesArray,
                        filterType = currentFilter,
                        autoCrop = autoCrop,
                        pageSize = pageSize,
                        onProgress = { current, total ->
                            generatingProgress = current.toFloat() / total
                            generatingText = "$current / $total"
                        }
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF Saved Successfully!", Toast.LENGTH_SHORT).show()
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
    }

    // --- Layout ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color(0xFFE0E7FF), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ras", fontWeight = FontWeight.Black, color = Color.White, fontSize = 24.sp)
                        Text("Scanner", fontWeight = FontWeight.Black, color = Color(0xFFC7D2FE), fontSize = 24.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (imagesArray.isNotEmpty()) {
                        TextButton(
                            onClick = { imagesArray = emptyList() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4F46E5)),
                            modifier = Modifier.padding(end = 8.dp).background(Color.White, RoundedCornerShape(50))
                        ) {
                            Text("Clear Grid", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4F46E5))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)).padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                
                // --- Top Actions ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Camera
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp).clickable {
                            val options = GmsDocumentScannerOptions.Builder()
                                .setGalleryImportAllowed(false)
                                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                                .build()
                            GmsDocumentScanning.getClient(options)
                                .getStartScanIntent(context as Activity)
                                .addOnSuccessListener { intentSender ->
                                    cameraLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Scanner failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    // Gallery
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp).clickable {
                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Gallery", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    // Import PDF
                    Card(
                        modifier = Modifier.weight(1f).height(90.dp).clickable {
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Import PDF", color = Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // --- Settings Card ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Auto Crop Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF2FF), RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Smart Auto Crop", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                            }
                            Switch(
                                checked = autoCrop,
                                onCheckedChange = { autoCrop = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4F46E5))
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Filter Selection
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pro Image Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                                var filterExpanded by remember { mutableStateOf(false) }
                                val filters = mapOf(
                                    "none" to "Original",
                                    "magic-pro" to "Magic Pro",
                                    "print-pro" to "Print Pro",
                                    "clear-pro" to "Clear Pro",
                                    "super-bw" to "Super B&W"
                                )
                                Box {
                                    OutlinedButton(
                                        onClick = { filterExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF9FAFB))
                                    ) {
                                        Text(filters[currentFilter] ?: "Select", color = Color(0xFF111827), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                                        filters.forEach { (key, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name, fontWeight = if (key == currentFilter) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { currentFilter = key; filterExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            // Page Size Selection
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page Size", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                                var sizeExpanded by remember { mutableStateOf(false) }
                                val sizes = mapOf("fit" to "Fit to Image", "a4" to "A4 Size")
                                Box {
                                    OutlinedButton(
                                        onClick = { sizeExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF9FAFB))
                                    ) {
                                        Text(sizes[pageSize] ?: "Select", color = Color(0xFF111827), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                                        sizes.forEach { (key, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name, fontWeight = if (key == pageSize) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { pageSize = key; sizeExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // --- Image Grid ---
                if (imagesArray.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.White.copy(alpha=0.7f), RoundedCornerShape(24.dp)).border(2.dp, Color(0xFFD1D5DB), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(80.dp).background(Color(0xFFF3F4F6), CircleShape).border(1.dp, Color(0xFFE5E7EB), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("No Images Selected", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1F2937))
                            Text("Capture, upload or import PDF.", fontSize = 12.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(imagesArray.size) { index ->
                            Box(
                                modifier = Modifier.aspectRatio(0.75f).background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(16.dp)).shadow(4.dp, RoundedCornerShape(16.dp))
                            ) {
                                FilteredPreviewImage(context, imagesArray[index], currentFilter)
                                IconButton(
                                    onClick = {
                                        val newArr = imagesArray.toMutableList()
                                        newArr.removeAt(index)
                                        imagesArray = newArr
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Red, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Box(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(24.dp).background(Color(0xFF4F46E5), CircleShape).border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // --- Generate Button ---
                AnimatedVisibility(visible = imagesArray.isNotEmpty()) {
                    Button(
                        onClick = generatePdfAction,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(24.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("SCAN & SAVE PDF", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                Divider(color = Color(0xFFD1D5DB))
                Spacer(Modifier.height(32.dp))
                
                // --- History Section ---
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Saved Documents", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF1F2937))
                }
                
                if (docs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.White.copy(alpha=0.7f), RoundedCornerShape(24.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(64.dp).padding(bottom = 16.dp))
                            Text("No PDFs generated yet.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                        }
                    }
                } else {
                    docs.forEach { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable {
                                val intent = Intent(context, PdfViewerActivity::class.java).apply {
                                    putExtra("pdf_path", doc.path)
                                    putExtra("pdf_name", doc.name)
                                }
                                context.startActivity(intent)
                            },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            border = BorderStroke(1.dp, Color(0xFFF3F4F6))
                        ) {
                            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(64.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(32.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(doc.date, fontSize = 12.sp, color = Color(0xFF6B7280))
                                        Text(" • ", fontSize = 12.sp, color = Color(0xFF6B7280))
                                        Text(doc.size, fontSize = 12.sp, color = Color(0xFF6B7280))
                                    }
                                }
                                IconButton(onClick = { sharePdf(context, doc) }, modifier = Modifier.background(Color(0xFFF3F4F6), CircleShape)) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF4F46E5), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(100.dp))
            }
            
            // --- Loading Overlay ---
            if (isGenerating) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha=0.95f)).clickable(enabled=false){}, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(64.dp).padding(bottom = 24.dp), strokeWidth = 6.dp)
                        Text("Creating Magic...", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                        Text("Please wait, enhancing resolution and saving safely.", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 32.dp))
                        
                        Box(modifier = Modifier.width(300.dp).height(16.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(50))) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(generatingProgress).background(Color(0xFF4F46E5), RoundedCornerShape(50)))
                        }
                        Row(modifier = Modifier.width(300.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(generatingText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
                            Text("${(generatingProgress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }
                }
            }
            
            // --- PDF Import Modal ---
            if (showPdfImportModal != null) {
                Dialog(onDismissRequest = { showPdfImportModal = null }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Import PDF Options", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1F2937), modifier = Modifier.padding(bottom = 8.dp))
                            Text("How do you want to process this PDF file?", fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 24.dp))
                            
                            Button(
                                onClick = { 
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val pfd = context.contentResolver.openFileDescriptor(showPdfImportModal!!, "r")
                                            if (pfd != null) {
                                                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                                val uris = mutableListOf<Uri>()
                                                for (i in 0 until renderer.pageCount) {
                                                    val page = renderer.openPage(i)
                                                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                    page.close()
                                                    
                                                    val tempFile = File(context.cacheDir, "pdf_ext_${System.currentTimeMillis()}_$i.jpg")
                                                    FileOutputStream(tempFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                                    uris.add(Uri.fromFile(tempFile))
                                                }
                                                renderer.close()
                                                pfd.close()
                                                withContext(Dispatchers.Main) {
                                                    imagesArray = imagesArray + uris
                                                    showPdfImportModal = null
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to extract PDF", Toast.LENGTH_SHORT).show() }
                                        }
                                    }
                                    showPdfImportModal = null
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Scan & Apply Filters", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val name = "Imported_${System.currentTimeMillis()}.pdf"
                                            saveDirectPdf(context, showPdfImportModal!!, name)
                                            withContext(Dispatchers.Main) {
                                                docs = loadDocs(context)
                                                Toast.makeText(context, "Saved directly to Reader", Toast.LENGTH_SHORT).show()
                                                showPdfImportModal = null
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show() }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                            ) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color(0xFF059669))
                                Spacer(Modifier.width(8.dp))
                                Text("Save Directly as Reader", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { showPdfImportModal = null }, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancel", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Utility Components ────────────────────────────────────────────────────────

@Composable
fun FilteredPreviewImage(context: Context, uri: Uri, filter: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true) ?: bmp
        }
    }

    bitmap?.let { bmp ->
        val colorMatrix = getFilterMatrix(filter)
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(14.dp)),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(colorMatrix.array))
        )
    } ?: Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(24.dp)) }
}

fun getFilterMatrix(filter: String): ColorMatrix {
    val cm = ColorMatrix()
    when (filter) {
        "magic-pro" -> {
            cm.set(floatArrayOf(
                1.4f, 0f, 0f, 0f, 10f,
                0f, 1.4f, 0f, 0f, 10f,
                0f, 0f, 1.4f, 0f, 10f,
                0f, 0f, 0f, 1.2f, 0f
            ))
        }
        "print-pro" -> {
            cm.setSaturation(0f)
            val contrast = ColorMatrix(floatArrayOf(
                1.6f, 0f, 0f, 0f, -40f,
                0f, 1.6f, 0f, 0f, -40f,
                0f, 0f, 1.6f, 0f, -40f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrast)
        }
        "clear-pro" -> {
            cm.set(floatArrayOf(
                1.2f, 0f, 0f, 0f, 30f,
                0f, 1.2f, 0f, 0f, 30f,
                0f, 0f, 1.2f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        "super-bw" -> {
            cm.setSaturation(0f)
            val contrast = ColorMatrix(floatArrayOf(
                2.5f, 0f, 0f, 0f, -150f,
                0f, 2.5f, 0f, 0f, -150f,
                0f, 0f, 2.5f, 0f, -150f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrast)
        }
    }
    return cm
}

fun getDocDir(context: Context): File {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RasFocus/ScannedPDFs")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun loadDocs(context: Context): List<ScannedDoc> {
    val dir = getDocDir(context)
    val files = dir.listFiles { f -> f.extension.equals("pdf", true) } ?: return emptyList()
    val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return files.sortedByDescending { it.lastModified() }.map {
        ScannedDoc(
            name = it.name,
            path = it.absolutePath,
            date = format.format(Date(it.lastModified())),
            size = "${it.length() / 1024} KB"
        )
    }
}

private fun sharePdf(context: Context, doc: ScannedDoc) {
    val file = File(doc.path)
    if (!file.exists()) { Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show(); return }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share PDF"))
}

private suspend fun buildPdfFromImages(
    context: Context,
    uris: List<Uri>,
    filterType: String,
    autoCrop: Boolean,
    pageSize: String,
    onProgress: suspend (Int, Int) -> Unit
): File {
    val dir = getDocDir(context)
    val name = "RasScanner_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    val file = File(dir, "$name.pdf")
    val doc = PdfDocument()
    
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(getFilterMatrix(filterType))

    for (i in uris.indices) {
        onProgress(i + 1, uris.size)
        val uri = uris[i]
        
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap
        
        val croppedBitmap = if (autoCrop) {
            softwareBitmap 
        } else softwareBitmap
        
        val pageW: Int
        val pageH: Int
        
        if (pageSize == "a4") {
            pageW = 595
            pageH = 842
        } else {
            pageW = (croppedBitmap.width * 0.5f).toInt()
            pageH = (croppedBitmap.height * 0.5f).toInt()
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, doc.pages.size + 1).create()
        val page = doc.startPage(pageInfo)
        
        val scale = minOf(pageW.toFloat() / croppedBitmap.width, pageH.toFloat() / croppedBitmap.height)
        val w = croppedBitmap.width * scale
        val h = croppedBitmap.height * scale
        val left = (pageW - w) / 2f
        val top = (pageH - h) / 2f
        
        val destRect = android.graphics.RectF(left, top, left + w, top + h)
        page.canvas.drawBitmap(croppedBitmap, null, destRect, paint)
        doc.finishPage(page)
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
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
        } catch (e: Exception) { }
    }
    
    return file
}

private fun saveDirectPdf(context: Context, sourceUri: Uri, outName: String) {
    val dir = getDocDir(context)
    val file = File(dir, outName)
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RasFocus")
            }
            val contentUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (contentUri != null) {
                context.contentResolver.openOutputStream(contentUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
        } catch (e: Exception) { }
    }
}

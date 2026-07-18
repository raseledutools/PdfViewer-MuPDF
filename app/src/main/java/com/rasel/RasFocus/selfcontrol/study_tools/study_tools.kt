package com.rasel.RasFocus.selfcontrol.study_tools

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// ─────────────────────────────────────────────────────────────────────────────
// Color tokens
// ─────────────────────────────────────────────────────────────────────────────
private val BgDeep       = Color(0xFF0D0D1A)
private val BgCard       = Color(0xFF1A1A2E)
private val BgCard2      = Color(0xFF16213E)
private val AccentBlue   = Color(0xFF4FACFE)
private val AccentCyan   = Color(0xFF00F2FE)
private val AccentRed    = Color(0xFFFF6B6B)
private val AccentOrange = Color(0xFFFF8E53)
private val AccentPurple = Color(0xFFA18CD1)
private val AccentPink   = Color(0xFFFBC2EB)
private val AccentGreen  = Color(0xFF43E97B)
private val AccentTeal   = Color(0xFF38F9D7)
private val AccentYellow = Color(0xFFFFD93D)
private val AccentLime   = Color(0xFFA8E063)
private val TextWhite    = Color(0xFFFFFFFF)
private val TextMuted    = Color(0xFF8888AA)

// ─────────────────────────────────────────────────────────────────────────────
// Nav state
// ─────────────────────────────────────────────────────────────────────────────
private sealed class StudyNav {
    object Home       : StudyNav()
    data class Web(val url: String, val title: String) : StudyNav()
    object PdfMerge   : StudyNav()
    object PdfTools   : StudyNav()
    object Calculator : StudyNav()
    object UnitConv   : StudyNav()
    object Pomodoro   : StudyNav()
    object QuickNotes : StudyNav()
    object GraphCalculator : StudyNav()
    object DocScanner : StudyNav()  // 📷 CamScanner-style document scanner
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry composable
// ─────────────────────────────────────────────────────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudyToolsScreen(onBack: () -> Unit = {}, onOpenDiary: () -> Unit) {
    var nav by remember { mutableStateOf<StudyNav>(StudyNav.Home) }

    BackHandler {
        if (nav != StudyNav.Home) nav = StudyNav.Home else onBack()
    }

    AnimatedContent(
        targetState = nav,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
            slideOutHorizontally { -it } + fadeOut()
        },
        label = "study_nav"
    ) { current ->
        when (current) {
            is StudyNav.Home       -> StudyToolsMain(
                onOpenUrl    = { u, t -> nav = StudyNav.Web(u, t) },
                onPdfMerge   = { nav = StudyNav.PdfMerge },
                onPdfTools   = { nav = StudyNav.PdfTools },
                onCalculator = { nav = StudyNav.Calculator },
                onUnitConv   = { nav = StudyNav.UnitConv },
                onPomodoro   = { nav = StudyNav.Pomodoro },
                onQuickNotes = { nav = StudyNav.QuickNotes },
                onGraphCalculator = { nav = StudyNav.GraphCalculator },
                onDocScanner = { nav = StudyNav.DocScanner },
                onOpenDiary  = onOpenDiary
            )
            is StudyNav.Web        -> StudyWebView(url = current.url, title = current.title, onBack = { nav = StudyNav.Home })
            is StudyNav.PdfMerge   -> NativePdfMergeScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.PdfTools   -> PdfToolsScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.Calculator -> ScientificCalculatorScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.UnitConv   -> UnitConverterScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.Pomodoro   -> PomodoroScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.QuickNotes -> QuickNotesScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.GraphCalculator -> GraphicCalculatorScreen(onBack = { nav = StudyNav.Home })
            is StudyNav.DocScanner -> ScanToPdfScreen(onBack = { nav = StudyNav.Home })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main scrollable screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StudyToolsMain(
    onOpenUrl:    (String, String) -> Unit,
    onPdfMerge:   () -> Unit,
    onPdfTools:   () -> Unit,
    onCalculator: () -> Unit,
    onUnitConv:   () -> Unit,
    onPomodoro:   () -> Unit,
    onQuickNotes: () -> Unit,
    onGraphCalculator: () -> Unit,
    onDocScanner: () -> Unit,
    onOpenDiary:  () -> Unit
) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(scroll)
            .padding(bottom = 40.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF16213E), BgDeep)))
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp)
        ) {
            Column {
                Text("📚 Study Tools", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                Spacer(Modifier.height(6.dp))
                Text("সব কিছু এক জায়গায়", fontSize = 14.sp, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                Text(
                    SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 12.sp, color = AccentBlue
                )
            }
        }

        // ── Personal Diary (সবার আগে) ──────────────────────────────
        SectionTitle("📓 Personal Diary", AccentPurple, AccentPink)
        PersonalDiaryCard(onClick = onOpenDiary)

        // ── Doc Scanner (CamScanner-style) ──────────────────────────────
        SectionTitle("📷 Doc Scanner", AccentCyan, AccentBlue)
        DocScannerCard(onClick = onDocScanner)

        // ── PDF Tools (Merge বাদ — শুধু PDF & Image Tools) ──────────────
        SectionTitle("📄 PDF Tools", AccentRed, AccentOrange)
        NativePdfToolsCard(onClick = onPdfTools)

        // ── Native Tools ────────────────────────────────────────────────
        SectionTitle("⚡ Native Tools (Offline)", AccentYellow, AccentOrange)
        NativeToolsGrid(
            onCalculator = onCalculator,
            onUnitConv   = onUnitConv,
            onPomodoro   = onPomodoro,
            onQuickNotes = onQuickNotes,
            onGraphCalculator = onGraphCalculator
        )

        // ── Math & Reference ────────────────────────────────────────────
        SectionTitle("📐 Math & Reference", AccentGreen, AccentTeal)
        ToolGrid(
            items = listOf(
                ToolItem("∞",  "Wolfram Alpha", AccentGreen,        "https://www.wolframalpha.com"),
                ToolItem("📈", "Desmos Graph",  AccentTeal,         "https://www.desmos.com/calculator"),
                ToolItem("📊", "GeoGebra",      Color(0xFF56AB2F),  "https://www.geogebra.org/calculator"),
                ToolItem("🔢", "Matrix Calc",   Color(0xFF11998E),  "https://matrix.reshish.com")
            ),
            onOpenUrl = onOpenUrl
        )

        // ── Dictionary & Translation ────────────────────────────────────
        SectionTitle("📖 Dictionary & Translation", AccentBlue, AccentCyan)
        ToolGrid(
            items = listOf(
                ToolItem("🇧🇩", "Bangla Dict",    AccentBlue,          "https://www.bdword.com"),
                ToolItem("🌐", "Google Translate", Color(0xFF4285F4),   "https://translate.google.com"),
                ToolItem("📚", "Oxford Dict",      Color(0xFF0078D7),   "https://www.oxfordlearnersdictionaries.com"),
                ToolItem("🗣️", "Cambridge Dict",   Color(0xFF003087),   "https://dictionary.cambridge.org")
            ),
            onOpenUrl = onOpenUrl
        )

        // ── AI Section ──────────────────────────────────────────────────
        SectionTitle("🤖 AI Section", AccentPurple, AccentPink)
        ToolGrid(
            items = listOf(
                ToolItem("🤖", "Claude AI",   AccentBlue,          "https://claude.ai"),
                ToolItem("♊",  "Gemini",      Color(0xFF00C853),   "https://gemini.google.com"),
                ToolItem("💬", "ChatGPT",     Color(0xFF74B9FF),   "https://chat.openai.com"),
                ToolItem("🧠", "DeepSeek",    Color(0xFF6C63FF),   "https://chat.deepseek.com"),
                ToolItem("🔍", "Perplexity",  Color(0xFF00CEC9),   "https://www.perplexity.ai"),
                ToolItem("✨", "Gamma AI",    Color(0xFFA29BFE),   "https://gamma.app")
            ),
            onOpenUrl = onOpenUrl
        )

        // ── Tomorrow's Tasks ────────────────────────────────────────────
        SectionTitle("✅ Tomorrow's Tasks", AccentGreen, AccentTeal)
        TaskSection()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Native Tools 2×2 grid
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NativeToolsGrid(
    onCalculator: () -> Unit,
    onUnitConv:   () -> Unit,
    onPomodoro:   () -> Unit,
    onQuickNotes: () -> Unit,
    onGraphCalculator: () -> Unit
) {
    val tools = listOf(
        Triple("🧮", "Calculator",   onCalculator) to Pair(AccentYellow, Color(0xFFFF8E00)),
        Triple("📏", "Unit Converter", onUnitConv) to Pair(AccentLime,   Color(0xFF38F9D7)),
        Triple("🍅", "Pomodoro",     onPomodoro)   to Pair(AccentRed,    AccentOrange),
        Triple("📝", "Quick Notes",  onQuickNotes) to Pair(AccentPurple, AccentPink),
        Triple("📈", "Graph Calculator", onGraphCalculator) to Pair(AccentBlue, AccentCyan)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        tools.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (info, colors) ->
                    val (emoji, label, onClick) = info
                    val (colorA, colorB) = colors
                    NativeToolCard(
                        emoji  = emoji,
                        label  = label,
                        colorA = colorA,
                        colorB = colorB,
                        onClick = onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NativeToolCard(
    emoji: String, label: String,
    colorA: Color, colorB: Color,
    onClick: () -> Unit, modifier: Modifier
) {
    Card(
        modifier = modifier.height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(BgCard, Color(0xFF252540))),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(listOf(colorA.copy(alpha = 0.3f), Color.Transparent)),
                        CircleShape
                    )
            )
            // "Native" badge
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .background(colorA.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Offline", fontSize = 9.sp, color = colorA, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(emoji, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = colorA)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(3.dp).width(40.dp)
                        .background(
                            Brush.horizontalGradient(listOf(colorA, colorB, Color.Transparent)),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Native PDF Merge card (full-width)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NativePdfMergeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF3A0A0A), Color(0xFF252540))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔀", fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PDF Merge", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                    Text("সরাসরি ফোনে — ইন্টারনেট ছাড়াই", fontSize = 11.sp, color = TextMuted)
                }
                Box(
                    modifier = Modifier
                        .background(AccentRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Offline", fontSize = 10.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Native PDF Tools card (full-width)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NativePdfToolsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF252540))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🖼️", fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PDF & Image Tools", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Text("Convert · Split · Compress — ইন্টারনেট ছাড়াই", fontSize = 11.sp, color = TextMuted)
                }
                Box(
                    modifier = Modifier
                        .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Offline", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doc Scanner card (CamScanner-style) — full-width hero card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DocScannerCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0A2A4A), Color(0xFF0D3B6E), Color(0xFF0A2A4A))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            // Glow circle background decoration
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 10.dp)
                    .background(
                        Brush.radialGradient(listOf(AccentCyan.copy(alpha = 0.25f), Color.Transparent)),
                        CircleShape
                    )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Camera icon box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.linearGradient(listOf(AccentCyan.copy(0.2f), AccentBlue.copy(0.15f))),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷", fontSize = 28.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Doc Scanner",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CamScanner-এর মতো — Auto edge detect, Magic Color, PDF export",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    // Feature tags
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("✂️ Crop", "🎨 Filter", "📄 PDF", "🖼️ Gallery").forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(tag, fontSize = 9.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(AccentCyan, AccentBlue)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Offline", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section title
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(title: String, colorA: Color, colorB: Color) {
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(5.dp).height(28.dp)
                .background(Brush.verticalGradient(listOf(colorA, colorB)), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool item & grid (WebView tools)
// ─────────────────────────────────────────────────────────────────────────────
private data class ToolItem(val emoji: String, val label: String, val color: Color, val url: String)

@Composable
private fun ToolGrid(items: List<ToolItem>, onOpenUrl: (String, String) -> Unit) {
    val rows = items.chunked(2)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { item ->
                    ToolCard(item = item, modifier = Modifier.weight(1f), onOpenUrl = onOpenUrl)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem, modifier: Modifier, onOpenUrl: (String, String) -> Unit) {
    Card(
        modifier = modifier.height(110.dp).clickable { onOpenUrl(item.url, "${item.emoji} ${item.label}") },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BgCard, Color(0xFF252540))), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(listOf(item.color.copy(alpha = 0.3f), Color.Transparent)),
                        CircleShape
                    )
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(item.emoji, fontSize = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = item.color)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(3.dp).width(40.dp)
                        .background(
                            Brush.horizontalGradient(listOf(item.color, Color.Transparent)),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NATIVE SCREEN 1 — Scientific Calculator
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun ScientificCalculatorScreen(onBack: () -> Unit) {
    var display   by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var justEvaled by remember { mutableStateOf(false) }
    var isDeg     by remember { mutableStateOf(true) }

    fun toRad(x: Double) = if (isDeg) Math.toRadians(x) else x

    fun evalExpr(expr: String): String {
        return try {
            // Replace display symbols
            var e = expr
                .replace("×", "*").replace("÷", "/")
                .replace("π", Math.PI.toString())
                .replace("e", Math.E.toString())
            // Simple recursive descent is complex; use stack-based approach
            // For trig/log we parse manually
            fun parseFull(s: String): Double {
                var str = s.trim()
                // trig / log functions
                val funcRegex = Regex("(sin|cos|tan|log|ln|√)\\(([^)]+)\\)")
                str = funcRegex.replace(str) { m ->
                    val fn  = m.groupValues[1]
                    val arg = parseFull(m.groupValues[2])
                    when (fn) {
                        "sin" -> sin(toRad(arg))
                        "cos" -> cos(toRad(arg))
                        "tan" -> tan(toRad(arg))
                        "log" -> log10(arg)
                        "ln"  -> ln(arg)
                        "√"   -> sqrt(arg)
                        else  -> arg
                    }.toString()
                }
                // Power
                str = str.replace(Regex("([\\d.]+)\\^([\\d.]+)")) { m ->
                    m.groupValues[1].toDouble().pow(m.groupValues[2].toDouble()).toString()
                }
                // Evaluate arithmetic via script-style eval (Kotlin can't do eval,
                // so we use a simple left-to-right with precedence)
                return evalArithmetic(str)
            }
            val result = parseFull(e)
            if (result == result.toLong().toDouble()) result.toLong().toString()
            else "%.8f".format(result).trimEnd('0').trimEnd('.')
        } catch (_: Exception) { "Error" }
    }

    fun onBtn(btn: String) {
        when (btn) {
            "C"   -> { display = "0"; expression = ""; justEvaled = false }
            "⌫"  -> {
                if (justEvaled) { display = "0"; expression = ""; justEvaled = false }
                else {
                    expression = if (expression.length <= 1) "" else expression.dropLast(1)
                    display = expression.ifEmpty { "0" }
                }
            }
            "="   -> {
                val result = evalExpr(expression)
                display = result
                expression = if (result == "Error") "" else result
                justEvaled = true
            }
            "±"   -> {
                if (display != "0" && display != "Error") {
                    if (display.startsWith("-")) { display = display.drop(1); expression = display }
                    else { display = "-$display"; expression = display }
                }
            }
            "%"   -> {
                val v = display.toDoubleOrNull()
                if (v != null) {
                    val r = v / 100
                    display = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
                    expression = display
                }
            }
            "sin(", "cos(", "tan(", "log(", "ln(", "√(" -> {
                if (justEvaled) { expression = btn; justEvaled = false }
                else expression += btn
                display = expression
            }
            ")" -> { expression += ")"; display = expression }
            "π", "e" -> {
                if (justEvaled) { expression = btn; justEvaled = false }
                else expression += btn
                display = expression
            }
            "x²" -> {
                val v = display.toDoubleOrNull()
                if (v != null) {
                    val r = v * v
                    expression = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
                    display = expression; justEvaled = true
                }
            }
            "x³" -> {
                val v = display.toDoubleOrNull()
                if (v != null) {
                    val r = v * v * v
                    expression = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
                    display = expression; justEvaled = true
                }
            }
            "1/x" -> {
                val v = display.toDoubleOrNull()
                if (v != null && v != 0.0) {
                    expression = (1.0 / v).toString(); display = expression; justEvaled = true
                }
            }
            else -> {
                if (justEvaled && btn.first().isDigit()) { expression = btn; justEvaled = false }
                else if (justEvaled && !btn.first().isDigit()) { /* keep expression for chaining ops */ justEvaled = false; expression += btn }
                else expression += btn
                display = expression.ifEmpty { "0" }
            }
        }
    }

    // Button layout
    val scientificRow = listOf("sin(", "cos(", "tan(", "log(", "ln(", "√(")
    val row2 = listOf("x²", "x³", "1/x", "^", "π", "e")
    val mainRows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    fun btnColor(btn: String): Color = when {
        btn == "=" -> AccentGreen
        btn in listOf("÷", "×", "-", "+", "^") -> AccentOrange
        btn in listOf("C", "⌫") -> AccentRed
        btn in listOf("%", "±") -> Color(0xFF3A3A5A)
        btn in scientificRow || btn in row2 -> Color(0xFF2A2A4A)
        else -> Color(0xFF1E1E38)
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        // Top bar
        TopBar("🧮 Calculator", onBack)

        // Display
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF0A0A15))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(expression.ifEmpty { "0" }, fontSize = 14.sp, color = TextMuted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                val dispFontSize = when {
                    display.length > 14 -> 22.sp
                    display.length > 10 -> 28.sp
                    else -> 40.sp
                }
                Text(display, fontSize = dispFontSize, fontWeight = FontWeight.Light,
                    color = TextWhite, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(
                        onClick = { isDeg = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isDeg) AccentYellow else TextMuted
                        )
                    ) { Text("DEG", fontSize = 12.sp, fontWeight = if (isDeg) FontWeight.Bold else FontWeight.Normal) }
                    TextButton(
                        onClick = { isDeg = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (!isDeg) AccentYellow else TextMuted
                        )
                    ) { Text("RAD", fontSize = 12.sp, fontWeight = if (!isDeg) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }

        // Buttons
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Scientific row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                scientificRow.forEach { btn ->
                    CalcBtn(btn.removeSuffix("("), Modifier.weight(1f), btnColor(btn)) { onBtn(btn) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row2.forEach { btn ->
                    CalcBtn(btn, Modifier.weight(1f), btnColor(btn)) { onBtn(btn) }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Close bracket standalone
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CalcBtn(")", Modifier.width(60.dp), Color(0xFF2A2A4A)) { onBtn(")") }
            }
            // Main rows
            mainRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { btn ->
                        CalcBtn(btn, Modifier.weight(1f), btnColor(btn)) { onBtn(btn) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcBtn(label: String, modifier: Modifier, bg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        contentPadding = PaddingValues(2.dp)
    ) {
        Text(
            label,
            fontSize = if (label.length > 3) 10.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite,
            textAlign = TextAlign.Center
        )
    }
}

// Simple left-to-right arithmetic evaluator with +,-,*,/ precedence
private fun evalArithmetic(expr: String): Double {
    val tokens = mutableListOf<String>()
    var i = 0
    val s = expr.replace("--", "+").replace("+-", "-")
    while (i < s.length) {
        when {
            s[i].isDigit() || s[i] == '.' -> {
                var num = ""
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) { num += s[i]; i++ }
                tokens.add(num)
            }
            s[i] == '-' && (tokens.isEmpty() || tokens.last() in listOf("+","-","*","/")) -> {
                var num = "-"
                i++
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) { num += s[i]; i++ }
                tokens.add(num)
            }
            s[i] in listOf('+', '-', '*', '/') -> { tokens.add(s[i].toString()); i++ }
            else -> i++
        }
    }
    // Handle * and /
    var j = 1
    while (j < tokens.size) {
        if (tokens[j] == "*" || tokens[j] == "/") {
            val left  = tokens[j-1].toDouble()
            val right = tokens[j+1].toDouble()
            val res   = if (tokens[j] == "*") left * right else left / right
            tokens[j-1] = res.toString()
            tokens.removeAt(j); tokens.removeAt(j)
        } else j += 2
    }
    // Handle + and -
    var result = tokens[0].toDouble()
    j = 1
    while (j < tokens.size) {
        val op  = tokens[j]
        val num = tokens[j+1].toDouble()
        result  = if (op == "+") result + num else result - num
        j += 2
    }
    return result
}

// ═════════════════════════════════════════════════════════════════════════════
//  NATIVE SCREEN 2 — Unit Converter
// ═════════════════════════════════════════════════════════════════════════════
private enum class UnitCategory(val label: String, val emoji: String) {
    LENGTH("Length", "📏"), MASS("Mass", "⚖️"),
    TEMP("Temperature", "🌡️"), AREA("Area", "🗺️"),
    SPEED("Speed", "🚀"), TIME("Time", "⏱️"),
    VOLUME("Volume", "🧪"), DATA("Data", "💾")
}

private val unitData: Map<UnitCategory, List<Pair<String, Double>>> = mapOf(
    UnitCategory.LENGTH to listOf("mm" to 1e-3, "cm" to 1e-2, "m" to 1.0, "km" to 1e3, "inch" to 0.0254, "ft" to 0.3048, "yard" to 0.9144, "mile" to 1609.344),
    UnitCategory.MASS   to listOf("mg" to 1e-6, "g" to 1e-3, "kg" to 1.0, "ton" to 1e3, "lb" to 0.453592, "oz" to 0.0283495),
    UnitCategory.AREA   to listOf("mm²" to 1e-6, "cm²" to 1e-4, "m²" to 1.0, "km²" to 1e6, "acre" to 4046.86, "hectare" to 1e4, "ft²" to 0.092903),
    UnitCategory.SPEED  to listOf("m/s" to 1.0, "km/h" to 0.277778, "mph" to 0.44704, "knot" to 0.514444, "ft/s" to 0.3048),
    UnitCategory.TIME   to listOf("ms" to 1e-3, "s" to 1.0, "min" to 60.0, "hr" to 3600.0, "day" to 86400.0, "week" to 604800.0, "month" to 2.628e6, "year" to 3.156e7),
    UnitCategory.VOLUME to listOf("ml" to 1e-3, "L" to 1.0, "m³" to 1e3, "cup" to 0.236588, "fl oz" to 0.0295735, "pint" to 0.473176, "gallon" to 3.78541),
    UnitCategory.DATA   to listOf("bit" to 1.0, "byte" to 8.0, "KB" to 8192.0, "MB" to 8388608.0, "GB" to 8589934592.0, "TB" to 8.796e12),
    UnitCategory.TEMP   to listOf("°C" to 0.0, "°F" to 0.0, "K" to 0.0)   // handled specially
)

@Composable
private fun UnitConverterScreen(onBack: () -> Unit) {
    var category  by remember { mutableStateOf(UnitCategory.LENGTH) }
    val units     = unitData[category] ?: emptyList()
    var fromIdx   by remember { mutableStateOf(0) }
    var toIdx     by remember { mutableStateOf(1) }
    var inputVal  by remember { mutableStateOf("1") }

    // Reset indices when category changes
    LaunchedEffect(category) { fromIdx = 0; toIdx = 1; inputVal = "1" }

    fun convert(): String {
        val v = inputVal.toDoubleOrNull() ?: return "—"
        if (category == UnitCategory.TEMP) {
            val fromU = units[fromIdx].first
            val toU   = units[toIdx].first
            val celsius = when (fromU) {
                "°C" -> v
                "°F" -> (v - 32) * 5 / 9
                "K"  -> v - 273.15
                else -> v
            }
            val result = when (toU) {
                "°C" -> celsius
                "°F" -> celsius * 9 / 5 + 32
                "K"  -> celsius + 273.15
                else -> celsius
            }
            return "%.4f".format(result).trimEnd('0').trimEnd('.')
        }
        val fromFactor = units[fromIdx].second
        val toFactor   = units[toIdx].second
        val result = v * fromFactor / toFactor
        return if (result == result.toLong().toDouble() && result < 1e12)
            result.toLong().toString()
        else "%.6f".format(result).trimEnd('0').trimEnd('.')
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        TopBar("📏 Unit Converter", onBack)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category chips
            val scroll2 = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(scroll2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitCategory.entries.forEach { cat ->
                    val selected = cat == category
                    FilterChip(
                        selected = selected,
                        onClick  = { category = cat },
                        label    = { Text("${cat.emoji} ${cat.label}", fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor     = Color.White,
                            containerColor         = BgCard,
                            labelColor             = TextMuted
                        )
                    )
                }
            }

            // Result card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF1A2A40))),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${inputVal.ifEmpty { "0" }} ${units.getOrNull(fromIdx)?.first ?: ""}",
                            fontSize = 22.sp, color = AccentBlue, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text("=", fontSize = 28.sp, color = TextMuted)
                        Text(
                            "${convert()} ${units.getOrNull(toIdx)?.first ?: ""}",
                            fontSize = 26.sp, color = AccentGreen, fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // From / To selectors
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UnitDropdown("From", units, fromIdx, Modifier.weight(1f)) { fromIdx = it }
                UnitDropdown("To",   units, toIdx,   Modifier.weight(1f)) { toIdx   = it }
            }

            // Swap
            Button(
                onClick = { val tmp = fromIdx; fromIdx = toIdx; toIdx = tmp },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A4A))
            ) { Text("⇅ Swap", color = AccentYellow, fontWeight = FontWeight.Bold) }

            // Number pad
            OutlinedTextField(
                value = inputVal,
                onValueChange = { if (it.matches(Regex("[0-9.\\-]*"))) inputVal = it },
                modifier  = Modifier.fillMaxWidth(),
                label     = { Text("Value", color = TextMuted) },
                singleLine = true,
                colors    = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = TextWhite,
                    unfocusedTextColor   = TextWhite,
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = Color(0xFF333355),
                    cursorColor          = AccentBlue
                ),
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
private fun UnitDropdown(label: String, units: List<Pair<String, Double>>, selectedIdx: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape  = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333355))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 10.sp, color = TextMuted)
                Text(units.getOrNull(selectedIdx)?.first ?: "—", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BgCard2)
        ) {
            units.forEachIndexed { idx, (name, _) ->
                DropdownMenuItem(
                    text = { Text(name, color = if (idx == selectedIdx) AccentBlue else TextWhite) },
                    onClick = { onSelect(idx); expanded = false }
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NATIVE SCREEN 3 — Pomodoro Timer
// ═════════════════════════════════════════════════════════════════════════════
private enum class PomodoroPhase(val label: String, val color: Color, val defaultMin: Int) {
    FOCUS("Focus", AccentRed, 25),
    SHORT_BREAK("Short Break", AccentGreen, 5),
    LONG_BREAK("Long Break", AccentBlue, 15)
}

@Composable
private fun PomodoroScreen(onBack: () -> Unit) {
    var phase        by remember { mutableStateOf(PomodoroPhase.FOCUS) }
    var totalSeconds by remember { mutableStateOf(PomodoroPhase.FOCUS.defaultMin * 60) }
    var remaining   by remember { mutableStateOf(totalSeconds) }
    var running     by remember { mutableStateOf(false) }
    var sessions    by remember { mutableStateOf(0) }
    val scope       = rememberCoroutineScope()

    LaunchedEffect(running) {
        if (running) {
            while (remaining > 0 && running) {
                delay(1000L)
                remaining--
            }
            if (remaining == 0) {
                running = false
                if (phase == PomodoroPhase.FOCUS) sessions++
            }
        }
    }

    fun setPhase(p: PomodoroPhase) {
        phase = p; running = false
        totalSeconds = p.defaultMin * 60; remaining = totalSeconds
    }

    val progress = if (totalSeconds > 0) remaining.toFloat() / totalSeconds else 0f
    val mins = remaining / 60
    val secs = remaining % 60
    val timeStr = "%02d:%02d".format(mins, secs)

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        TopBar("🍅 Pomodoro", onBack)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Phase tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PomodoroPhase.entries.forEach { p ->
                    FilterChip(
                        selected = p == phase,
                        onClick  = { setPhase(p) },
                        label    = { Text(p.label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = p.color,
                            selectedLabelColor     = Color.White,
                            containerColor         = BgCard,
                            labelColor             = TextMuted
                        )
                    )
                }
            }

            // Timer circle
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color    = phase.color,
                    trackColor = Color(0xFF1A1A2E),
                    strokeWidth = 12.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeStr, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(phase.label, fontSize = 14.sp, color = phase.color)
                }
            }

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Reset
                OutlinedButton(
                    onClick = { running = false; remaining = totalSeconds },
                    shape  = RoundedCornerShape(50.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, phase.color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = phase.color)
                ) { Text("↺ Reset") }

                // Play / Pause
                Button(
                    onClick = { running = !running },
                    shape  = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = phase.color),
                    modifier = Modifier.width(140.dp).height(48.dp)
                ) {
                    Text(if (running) "⏸ Pause" else "▶ Start",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Sessions counter
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Focus Sessions", fontSize = 12.sp, color = TextMuted)
                        Text("Today", fontSize = 11.sp, color = TextMuted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(minOf(sessions, 8)) {
                            Text("🍅", fontSize = 20.sp)
                        }
                        if (sessions == 0) Text("—", color = TextMuted, fontSize = 16.sp)
                        if (sessions > 8) Text("+${sessions-8}", color = AccentRed, fontSize = 14.sp)
                    }
                    TextButton(onClick = { sessions = 0 }) {
                        Text("Reset", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }

            // Tips
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF0D1F12))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💡 Pomodoro Tips", fontSize = 13.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Text("• ২৫ মিনিট focus → ৫ মিনিট break", fontSize = 12.sp, color = TextMuted)
                    Text("• ৪টি session শেষে ১৫ মিনিট long break", fontSize = 12.sp, color = TextMuted)
                    Text("• Phone down রাখো, notification বন্ধ করো", fontSize = 12.sp, color = TextMuted)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NATIVE SCREEN 4 — Quick Notes (multi-note, SharedPrefs)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun QuickNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("quick_notes_prefs", Context.MODE_PRIVATE) }

    data class Note(val id: String, val title: String, val body: String, val ts: Long)

    fun loadNotes(): List<Note> {
        val keys = prefs.getStringSet("note_ids", emptySet()) ?: emptySet()
        return keys.mapNotNull { id ->
            val title = prefs.getString("note_title_$id", null) ?: return@mapNotNull null
            val body  = prefs.getString("note_body_$id",  "") ?: ""
            val ts    = prefs.getLong("note_ts_$id", 0L)
            Note(id, title, body, ts)
        }.sortedByDescending { it.ts }
    }

    var notes    by remember { mutableStateOf(loadNotes()) }
    var editing  by remember { mutableStateOf<Note?>(null) }
    var titleTf  by remember { mutableStateOf("") }
    var bodyTf   by remember { mutableStateOf("") }
    var showNew  by remember { mutableStateOf(false) }

    fun saveNote(id: String, title: String, body: String) {
        val ids = (prefs.getStringSet("note_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(id)
        prefs.edit()
            .putStringSet("note_ids", ids)
            .putString("note_title_$id", title)
            .putString("note_body_$id",  body)
            .putLong("note_ts_$id", System.currentTimeMillis())
            .apply()
        notes = loadNotes()
    }

    fun deleteNote(id: String) {
        val ids = (prefs.getStringSet("note_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(id)
        prefs.edit()
            .putStringSet("note_ids", ids)
            .remove("note_title_$id").remove("note_body_$id").remove("note_ts_$id")
            .apply()
        notes = loadNotes()
    }

    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard2)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentBlue)
            }
            Text("📝 Quick Notes", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = TextWhite, modifier = Modifier.weight(1f))
            IconButton(onClick = { titleTf = ""; bodyTf = ""; editing = null; showNew = true }) {
                Icon(Icons.Default.Add, contentDescription = "New note", tint = AccentYellow)
            }
        }

        // Editor overlay
        if (showNew || editing != null) {
            Column(
                modifier = Modifier.fillMaxSize().background(BgDeep).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = titleTf, onValueChange = { titleTf = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Title", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentYellow, unfocusedBorderColor = Color(0xFF333355),
                        cursorColor = AccentYellow
                    ), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = bodyTf, onValueChange = { bodyTf = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Note...", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentPurple, unfocusedBorderColor = Color(0xFF333355),
                        cursorColor = AccentPurple
                    ), shape = RoundedCornerShape(14.dp), maxLines = 20
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showNew = false; editing = null },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val id = editing?.id ?: UUID.randomUUID().toString()
                            val t  = titleTf.ifBlank { "Untitled" }
                            saveNote(id, t, bodyTf)
                            showNew = false; editing = null
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
                    ) { Text("💾 Save", color = Color(0xFF1A1000), fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            // Note list
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("কোনো note নেই", color = TextMuted, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { titleTf = ""; bodyTf = ""; showNew = true }) {
                            Text("+ নতুন note যোগ করো", color = AccentYellow)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    notes.forEach { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                editing = note; titleTf = note.title; bodyTf = note.body; showNew = false
                            },
                            shape  = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentYellow)
                                    if (note.body.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(note.body, fontSize = 13.sp, color = TextMuted, maxLines = 2)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(sdf.format(Date(note.ts)), fontSize = 11.sp, color = Color(0xFF555575))
                                }
                                IconButton(
                                    onClick = { deleteNote(note.id) },
                                    modifier = Modifier.size(32.dp)
                                ) { Text("🗑️", fontSize = 16.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NATIVE SCREEN 5 — PDF Merge
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun NativePdfMergeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status       by remember { mutableStateOf("") }
    var isMerging    by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { selectedUris = selectedUris + uris; status = "" }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        TopBar("🔀 PDF Merge", onBack)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("PDF ফাইল যোগ করো", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (selectedUris.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                        .background(BgCard, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("কোনো ফাইল বাছাই হয়নি", color = TextMuted) }
            } else {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${selectedUris.size}টি ফাইল", fontSize = 12.sp, color = TextMuted,
                            modifier = Modifier.padding(bottom = 8.dp))
                        selectedUris.forEachIndexed { index, uri ->
                            val name = remember(uri) {
                                context.contentResolver.query(uri, null, null, null, null)
                                    ?.use { c ->
                                        val col = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        c.moveToFirst(); if (col >= 0) c.getString(col) else null
                                    } ?: "file_$index"
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${index+1}.", fontSize = 13.sp, color = AccentBlue, modifier = Modifier.width(28.dp))
                                Text("📄", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 13.sp, color = TextWhite, modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { selectedUris = selectedUris.toMutableList().also { it.removeAt(index) } },
                                    modifier = Modifier.size(28.dp)) {
                                    Text("✕", color = AccentRed, fontSize = 13.sp)
                                }
                            }
                            if (index < selectedUris.lastIndex) HorizontalDivider(color = Color(0xFF2A2A4A), thickness = 1.dp)
                        }
                    }
                }
                TextButton(onClick = { selectedUris = emptyList(); status = "" }, modifier = Modifier.align(Alignment.End)) {
                    Text("🗑️ সব মুছো", color = AccentRed, fontSize = 13.sp)
                }
            }

            if (selectedUris.size >= 2) {
                Button(
                    onClick = {
                        isMerging = true; status = "⏳ Merge হচ্ছে..."
                        scope.launch {
                            status = mergePdfs(context, selectedUris)
                            isMerging = false
                            if (status.startsWith("✅")) selectedUris = emptyList()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isMerging,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    if (isMerging) { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                    Text("🔀 Merge করো (${selectedUris.size}টি PDF)", color = Color(0xFF0D1F0D), fontWeight = FontWeight.Bold)
                }
            }

            if (status.isNotEmpty()) {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.startsWith("✅")) Color(0xFF0D2A15) else Color(0xFF2A0D0D)
                    ), modifier = Modifier.fillMaxWidth()
                ) {
                    Text(status, modifier = Modifier.padding(14.dp), fontSize = 13.sp,
                        color = if (status.startsWith("✅")) AccentGreen else AccentRed)
                }
            }

            Text(
                "• ইন্টারনেট ছাড়াই কাজ করে\n• Merged ফাইল Downloads-এ সেভ হবে\n• কমপক্ষে ২টি PDF দরকার",
                fontSize = 12.sp, color = TextMuted, lineHeight = 20.sp
            )
        }
    }
}

private suspend fun mergePdfs(context: Context, uris: List<Uri>): String =
    withContext(Dispatchers.IO) {
        try {
            val outputDoc = PdfDocument(); var globalPage = 1
            for (uri in uris) {
                val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext "❌ ফাইল খোলা যায়নি"
                pfd.use { desc ->
                    PdfRenderer(desc).use { r ->
                        for (i in 0 until r.pageCount) {
                            r.openPage(i).use { p ->
                                val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pi = PdfDocument.PageInfo.Builder(p.width, p.height, globalPage++).create()
                                val op = outputDoc.startPage(pi)
                                op.canvas.drawBitmap(bmp, 0f, 0f, null)
                                outputDoc.finishPage(op); bmp.recycle()
                            }
                        }
                    }
                }
            }
            val fileName = "Merged_${System.currentTimeMillis()}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: return@withContext "❌ সেভ করা যায়নি"
                context.contentResolver.openOutputStream(uri)?.use { outputDoc.writeTo(it) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs(); File(dir, fileName).outputStream().use { outputDoc.writeTo(it) }
            }
            outputDoc.close()
            "✅ Merge সফল! Downloads/$fileName"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Shared TopBar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BgCard2)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentBlue)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Personal Diary Card (navigates to diary.kt) + Home Screen Shortcut
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PersonalDiaryCard(onClick: () -> Unit) {
    val context = LocalContext.current
    var shortcutAdded by remember { mutableStateOf(false) }

    // Home screen shortcut pinning
    fun addShortcut() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val sm = context.getSystemService(ShortcutManager::class.java)
            if (sm.isRequestPinShortcutSupported) {
                val intent = Intent(context, com.rasel.RasFocus.MainActivity::class.java).apply {
                    action = "com.rasel.RasFocus.OPEN_DIARY"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val info = ShortcutInfo.Builder(context, "diary_shortcut")
                    .setShortLabel("Diary")
                    .setLongLabel("Personal Diary")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_edit))
                    .setIntent(intent)
                    .build()
                sm.requestPinShortcut(info, null)
                shortcutAdded = true
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A1835), Color(0xFF251840))),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Glow accent top-right
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.TopEnd)
                        .background(
                            Brush.radialGradient(listOf(AccentPurple.copy(alpha = 0.25f), Color.Transparent)),
                            CircleShape
                        )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(AccentPurple.copy(alpha = 0.3f), AccentPink.copy(alpha = 0.2f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📓", fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Personal Diary",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            SimpleDateFormat("dd MMM yyyy — EEEE", Locale.getDefault()).format(Date()),
                            fontSize = 11.sp,
                            color = AccentPurple
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "আজকের অনুভূতি, চিন্তা ও স্মৃতি লিখে রাখো",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentPurple.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Open Diary",
                            tint = AccentPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(top = 72.dp)
                        .height(3.dp)
                        .fillMaxWidth(0.5f)
                        .background(
                            Brush.horizontalGradient(listOf(AccentPurple, AccentPink, Color.Transparent)),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // ── Home Screen Shortcut button — Diary card এর ঠিক নিচে ─────────
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (shortcutAdded) AccentGreen.copy(alpha = 0.15f)
                        else AccentPurple.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { addShortcut() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (shortcutAdded) "✅" else "📌", fontSize = 14.sp)
                    Text(
                        if (shortcutAdded) "Shortcut Added!" else "Add to Home Screen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (shortcutAdded) AccentGreen else AccentPurple
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tomorrow's Tasks
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TaskSection() {
    val context  = LocalContext.current
    val prefs    = remember { context.getSharedPreferences("study_tools_prefs", Context.MODE_PRIVATE) }
    var taskList by remember { mutableStateOf<MutableList<String>>(prefs.getStringSet("tasks", emptySet())?.toMutableList() ?: mutableListOf()) }
    var newTask  by remember { mutableStateOf("") }

    fun saveTasks(list: MutableList<String>) { prefs.edit().putStringSet("tasks", list.toSet()).apply(); taskList = list.toMutableList() }

    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43))), RoundedCornerShape(24.dp))
            .padding(20.dp)
        ) {
            Column {
                Text("📅 আগামীকাল — ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)}",
                    fontSize = 13.sp, color = AccentGreen, modifier = Modifier.padding(bottom = 16.dp))
                if (taskList.isEmpty()) {
                    Text("এখনো কোনো কাজ নেই। নিচে যোগ করুন!", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 12.dp))
                } else {
                    taskList.forEachIndexed { index, task ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 16.sp); Spacer(Modifier.width(12.dp))
                            Text(task, fontSize = 14.sp, color = TextWhite, modifier = Modifier.weight(1f))
                            IconButton(onClick = { val u = taskList.toMutableList().also { it.removeAt(index) }; saveTasks(u) },
                                modifier = Modifier.size(28.dp)) { Text("✕", color = AccentRed, fontSize = 14.sp) }
                        }
                        if (index < taskList.lastIndex) HorizontalDivider(color = Color(0xFF1A3A4A), thickness = 1.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTask, onValueChange = { newTask = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("নতুন কাজ...", color = Color(0xFF44666A)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentGreen.copy(alpha = 0.7f),
                            unfocusedBorderColor = Color(0xFF1A3A4A), cursorColor = AccentGreen
                        ), shape = RoundedCornerShape(14.dp), singleLine = true
                    )
                    Button(
                        onClick = {
                            val t = newTask.trim()
                            if (t.isNotEmpty()) { saveTasks(taskList.toMutableList().also { it.add(t) }); newTask = "" }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                    ) { Text("+", color = Color(0xFF0D1F0D), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WebView screen — RasBrowser-style settings (fast render, Chrome UA, bypass JS)
// ─────────────────────────────────────────────────────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StudyWebView(url: String, title: String, onBack: () -> Unit) {
    var canGoBack  by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle  by remember { mutableStateOf(title) }
    var progress   by remember { mutableStateOf(0) }
    BackHandler { if (canGoBack) webViewRef?.goBack() else onBack() }

    // RasBrowser থেকে নেওয়া — WebView কে real Chrome মনে করাবে
    val bypassJs = """
        (function() {
            try { Object.defineProperty(navigator, 'webdriver', { get: () => undefined, configurable: true }); } catch(e) {}
            if (!window.chrome) {
                window.chrome = {
                    app: { isInstalled: false, InstallState: {}, RunningState: {} },
                    runtime: { OnInstalledReason: {}, OnRestartRequiredReason: {}, PlatformArch: {}, PlatformNaclArch: {}, PlatformOs: {}, RequestUpdateCheckStatus: {} }
                };
            }
            try { Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5], configurable: true }); } catch(e) {}
            try { Object.defineProperty(navigator, 'languages', { get: () => ['en-US','en','bn'], configurable: true }); } catch(e) {}
            const origQuery = window.navigator.permissions?.query;
            if (origQuery) {
                window.navigator.permissions.query = (params) =>
                    params.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : origQuery.call(window.navigator.permissions, params);
            }
        })();
    """.trimIndent()

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard2).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (canGoBack) webViewRef?.goBack() else onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentBlue)
            }
            Spacer(Modifier.width(8.dp))
            Text(pageTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite,
                modifier = Modifier.weight(1f), maxLines = 1)
            if (progress in 1..99) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(20.dp),
                    color = AccentBlue,
                    strokeWidth = 2.dp
                )
            }
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close", tint = AccentRed) }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view.evaluateJavascript(bypassJs, null)
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            canGoBack = view.canGoBack()
                            view.evaluateJavascript(bypassJs, null)
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            canGoBack = view?.canGoBack() ?: false
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                        override fun onReceivedTitle(view: WebView?, t: String?) {
                            if (!t.isNullOrBlank()) pageTitle = t
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        // Chrome 136 UA — sites WebView detect করতে পারবে না
                        userAgentString = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.125 Mobile Safari/537.36"
                    }
                    // Third-party cookie — login session ঠিক রাখে
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    // Offscreen pre-raster — scroll smooth করে
                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.OFF_SCREEN_PRERASTER)) {
                        androidx.webkit.WebSettingsCompat.setOffscreenPreRaster(settings, true)
                    }
                    loadUrl(url)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

package com.rasel.RasFocus.combo.selfcontrol.study_tools

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.*

// --- কালার প্যালেট ---
val BgCanvas = Color(0xFF121212)
val BgSurface = Color(0xFF191919)
val BgInput = Color(0xFF242424)
val AccentPrimary = Color(0xFF00E5FF)
val AccentSecondary = Color(0xFF00E676)
internal val GcAccentRed = Color(0xFFFF1744)
internal val GcAccentOrange = Color(0xFFFF9100)
val AccentGold = Color(0xFFFFD700)
internal val GcAccentPurple = Color(0xFFB388FF)
val TextMain = Color(0xFFF5F5F5)
val TextDim = Color(0xFF9E9E9E)

// --- ডাটা ক্লাস ও ম্যাথ লজিক ---
data class Complex(val re: Double, val im: Double) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: Complex): Complex {
        val den = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / den, (im * o.re - re * o.im) / den)
    }
    fun abs() = hypot(re, im)
}

// Durand-Kerner অ্যালগরিদম (Pole-Zero Root বের করার জন্য)
fun findRoots(coeffs: DoubleArray): List<Complex> {
    val n = coeffs.size - 1
    if (n <= 0) return emptyList()
    val aN = coeffs.last()
    if (aN == 0.0) return emptyList()
    val a = coeffs.reversed().map { it / aN }.toDoubleArray()

    val roots = MutableList(n) { i ->
        val angle = (2 * Math.PI * i) / n + 0.1
        Complex(cos(angle), sin(angle))
    }

    for (iter in 0..100) {
        var maxDiff = 0.0
        for (i in 0 until n) {
            val z = roots[i]
            var p = Complex(a[n], 0.0)
            for (k in n - 1 downTo 0) { p = p * z + Complex(a[k], 0.0) }
            var denom = Complex(1.0, 0.0)
            for (j in 0 until n) { if (i != j) denom = denom * (z - roots[j]) }
            val change = p / denom
            roots[i] = roots[i] - change
            if (change.abs() > maxDiff) maxDiff = change.abs()
        }
        if (maxDiff < 1e-6) break
    }
    return roots
}

fun computeFFT(yArr: DoubleArray, fs: Double): Pair<DoubleArray, DoubleArray> {
    val n = yArr.size
    val kMax = n / 2
    val freqs = DoubleArray(kMax)
    val mags = DoubleArray(kMax)
    for (k in 0 until kMax) {
        var re = 0.0; var im = 0.0
        for (i in 0 until n) {
            val angle = (2.0 * Math.PI * k * i) / n
            re += yArr[i] * cos(angle)
            im -= yArr[i] * sin(angle)
        }
        mags[k] = sqrt(re * re + im * im) / n
        freqs[k] = (k * fs) / n
    }
    return Pair(freqs, mags)
}

fun computeLinearRegression(x: DoubleArray, y: DoubleArray): Triple<Double, Double, Double> {
    val n = x.size
    var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumX2 = 0.0
    for (i in 0 until n) { sumX += x[i]; sumY += y[i]; sumXY += x[i]*y[i]; sumX2 += x[i]*x[i] }
    val m = java.lang.Double.valueOf((n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX))
    val c = java.lang.Double.valueOf((sumY - m * sumX) / n)
    val yMean = sumY / n
    var ssTot = 0.0; var ssRes = 0.0
    for (i in 0 until n) {
        val fit = m * x[i] + c
        ssTot += (y[i] - yMean).pow(2)
        ssRes += (y[i] - fit).pow(2)
    }
    val r2 = if (ssTot == 0.0) 1.0 else 1.0 - (ssRes / ssTot)
    return Triple(if(m.isNaN()) 0.0 else m, if(c.isNaN()) 0.0 else c, if(r2.isNaN()) 0.0 else r2)
}

fun parseMatlabArray(str: String): DoubleArray {
    return try {
        if (str.startsWith("[")) {
            str.removeSurrounding("[", "]").split(",").map { it.trim().toDouble() }.toDoubleArray()
        } else if (str.contains(":")) {
            val parts = str.split(":").map { it.toDouble() }
            val start = parts[0]
            val step = if (parts.size == 3) parts[1] else 1.0
            val end = if (parts.size == 3) parts[2] else parts[1]
            val list = mutableListOf<Double>()
            var v = start
            while (v <= end + 1e-9) { list.add(v); v += step }
            list.toDoubleArray()
        } else {
            doubleArrayOf(str.toDouble())
        }
    } catch (e: Exception) { doubleArrayOf() }
}

// --- Main UI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalAnalyzerProScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Model & FFT", "Data & Fit", "Pole-Zero")

    // Continuous States
    var equation by remember { mutableStateOf("A * exp(-D*x/(2*M)) * cos(x*sqrt(K/M))") }
    var xMin by remember { mutableStateOf("0") }
    var xMax by remember { mutableStateOf("20") }
    var isFftEnabled by remember { mutableStateOf(false) }
    var isAnimated by remember { mutableStateOf(false) }
    val sliderVals = remember { mutableStateMapOf("A" to 5f, "F" to 10f, "M" to 2f, "D" to 1.5f, "K" to 15f, "T" to 2f, "L" to 5f, "C" to 1f, "R" to 10f) }

    // Discrete States
    var matlabX by remember { mutableStateOf("1:1:10") }
    var matlabY by remember { mutableStateOf("[2.1, 4.3, 5.8, 8.2, 10.1, 11.9, 14.0, 15.8, 18.1, 20.2]") }
    var isFitEnabled by remember { mutableStateOf(false) }
    var plotType by remember { mutableStateOf("scatter") }

    // Pole-Zero States
    var pzNum by remember { mutableStateOf("[1]") }
    var pzDen by remember { mutableStateOf("[1, 2, 5]") }

    // Graph Data States
    var plotX by remember { mutableStateOf(doubleArrayOf()) }
    var plotY by remember { mutableStateOf(doubleArrayOf()) }
    var fitY by remember { mutableStateOf(doubleArrayOf()) }
    var pzZeros by remember { mutableStateOf<List<Complex>>(emptyList()) }
    var pzPoles by remember { mutableStateOf<List<Complex>>(emptyList()) }

    // Metrics States
    var peakAmp by remember { mutableStateOf(0.0) }
    var rmsVal by remember { mutableStateOf(0.0) }
    var areaVal by remember { mutableStateOf(0.0) }
    var fitEq by remember { mutableStateOf("N/A") }
    var fitR2 by remember { mutableStateOf("N/A") }
    var pzStable by remember { mutableStateOf("N/A") }
    var pzOrder by remember { mutableStateOf(0) }

    // Core Logic Engine
    LaunchedEffect(
        selectedTab, equation, xMin, xMax, isFftEnabled, matlabX, matlabY,
        isFitEnabled, plotType, pzNum, pzDen, sliderVals.values.toList()
    ) {
        try {
            if (selectedTab == 0) { // Continuous
                val min = xMin.toDoubleOrNull() ?: 0.0
                val max = xMax.toDoubleOrNull() ?: 20.0
                val samples = 1000
                val step = (max - min) / samples

                val expr = ExpressionBuilder(equation)
                    .variables("x", "A", "F", "M", "D", "K", "T", "L", "C", "R")
                    .build()
                sliderVals.forEach { (k, v) -> expr.setVariable(k, v.toDouble()) }

                val tX = DoubleArray(samples + 1)
                val tY = DoubleArray(samples + 1)
                var pPeak = 0.0; var pSumSq = 0.0; var pArea = 0.0

                for (i in 0..samples) {
                    val cx = min + i * step
                    tX[i] = cx
                    val cy = try { expr.setVariable("x", cx).evaluate() } catch (e: Exception) { 0.0 }
                    tY[i] = cy
                    if (abs(cy) > pPeak) pPeak = abs(cy)
                    pSumSq += cy * cy
                    pArea += cy * step
                }

                peakAmp = pPeak; rmsVal = sqrt(pSumSq / samples); areaVal = pArea

                if (isFftEnabled) {
                    val (f, m) = computeFFT(tY, 1 / step)
                    plotX = f; plotY = m
                } else {
                    plotX = tX; plotY = tY
                }
            } else if (selectedTab == 1) { // Discrete
                val xArr = parseMatlabArray(matlabX)
                var yArr = parseMatlabArray(matlabY)
                
                // If Y is a formula
                if (yArr.isEmpty() && !matlabY.startsWith("[")) {
                    val expr = ExpressionBuilder(matlabY).variables("x").build()
                    yArr = DoubleArray(xArr.size) { i ->
                        try { expr.setVariable("x", xArr[i]).evaluate() } catch (e: Exception) { 0.0 }
                    }
                }
                
                plotX = xArr; plotY = yArr

                if (isFitEnabled && xArr.size > 1 && yArr.size == xArr.size) {
                    val (m, c, r2) = computeLinearRegression(xArr, yArr)
                    fitEq = "y = ${String.format("%.2f", m)}x + ${String.format("%.2f", c)}"
                    fitR2 = String.format("%.4f", r2)
                    fitY = DoubleArray(xArr.size) { i -> m * xArr[i] + c }
                } else {
                    fitEq = "N/A"; fitR2 = "N/A"; fitY = doubleArrayOf()
                }
            } else if (selectedTab == 2) { // Pole-Zero
                val numCoeffs = parseMatlabArray(pzNum)
                val denCoeffs = parseMatlabArray(pzDen)
                pzZeros = findRoots(numCoeffs)
                pzPoles = findRoots(denCoeffs)
                pzOrder = max(0, denCoeffs.size - 1)
                
                var stable = true
                for (p in pzPoles) { if (p.re >= 0) stable = false }
                pzStable = if (stable) "Stable (LHP)" else "Unstable (RHP)"
            }
        } catch (e: Exception) { /* Ignore errors during typing */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Analyzer PRO", color = TextMain, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSurface)
            )
        },
        containerColor = BgCanvas
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            // --- Custom Universal Graph Canvas ---
            GraphRenderer(selectedTab, plotX, plotY, fitY, plotType, isFftEnabled, isAnimated, pzZeros, pzPoles)

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BgSurface,
                contentColor = AccentPrimary,
                indicator = { tabPositions -> TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = AccentPrimary) }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                        Text(title, modifier = Modifier.padding(16.dp), color = if (selectedTab == i) AccentPrimary else TextDim)
                    }
                }
            }

            // --- Scrollable Control Panels ---
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                if (selectedTab == 0) {
                    // Continuous Panel
                    InputField("Signal / Equation", equation) { equation = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InputField("X-Min", xMin, Modifier.weight(1f)) { xMin = it }
                        InputField("X-Max", xMax, Modifier.weight(1f)) { xMax = it }
                    }
                    ToggleRow("View FFT Spectrum", isFftEnabled, GcAccentPurple) { isFftEnabled = it }
                    ToggleRow("Digital Signal Flow (Animate)", isAnimated, AccentPrimary) { isAnimated = it }
                    
                    Card(colors = CardDefaults.cardColors(containerColor = BgInput), border = CardDefaults.outlinedCardBorder(true)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Live Parameters", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            // Show sliders dynamically if they exist in equation
                            sliderVals.keys.filter { equation.contains(it) }.forEach { key ->
                                ParameterSlider(key, sliderVals[key]!!, if(key=="D"||key=="C") 0f else 0.1f, if(key=="K"||key=="F") 50f else 20f) { sliderVals[key] = it }
                            }
                        }
                    }
                    MetricsCard(listOf("Peak Amplitude" to String.format("%.3f", peakAmp), "RMS Value" to String.format("%.3f", rmsVal), "Area (Integral)" to String.format("%.3f", areaVal)))
                    
                    // Presets
                    Text("📐 Global Engineering Models", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    PresetBtn("Damped Harmonic (Spring)") { equation = "A * exp(-D*x/(2*M)) * cos(x*sqrt(K/M))"; xMin = "0"; xMax = "20" }
                    PresetBtn("RLC Circuit Oscillation") { equation = "K * exp(-x/(2*L)) * sin(x/sqrt(L*C))"; xMin = "0"; xMax = "30" }
                    PresetBtn("Fourier Square Synthesis") { equation = "A * (sin(x) + (1/3)*sin(3*x) + (1/5)*sin(5*x))"; xMin = "0"; xMax = "20" }

                } else if (selectedTab == 1) {
                    // Discrete Panel
                    InputField("X Data (start:step:end OR [array])", matlabX, color = GcAccentOrange) { matlabX = it }
                    InputField("Y Data (f(x) OR [array])", matlabY, color = GcAccentOrange) { matlabY = it }
                    ToggleRow("Show Linear Best Fit (Regression)", isFitEnabled, AccentSecondary) { isFitEnabled = it }
                    
                    Text("Plot Type", color = TextDim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("scatter", "lines", "bar").forEach { type ->
                            Button(
                                onClick = { plotType = type },
                                colors = ButtonDefaults.buttonColors(containerColor = if(plotType == type) AccentPrimary else BgInput)
                            ) { Text(type.uppercase(), color = if(plotType == type) Color.Black else TextMain) }
                        }
                    }
                    
                    MetricsCard(listOf("Equation (Fit)" to fitEq, "R-Squared" to fitR2))
                    
                    Text("💻 MATLAB / Data Examples", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    PresetBtn("Discrete Sine") { matlabX = "0:1:20"; matlabY = "sin(x)"; plotType = "scatter" }
                    PresetBtn("Sinc Function") { matlabX = "-10:0.1:10"; matlabY = "sin(x)/x"; plotType = "lines" }

                } else if (selectedTab == 2) {
                    // Pole-Zero Panel
                    InputField("Numerator Coeffs [an, ..., a0]", pzNum, color = GcAccentPurple) { pzNum = it }
                    InputField("Denominator Coeffs [bn, ..., b0]", pzDen, color = GcAccentPurple) { pzDen = it }
                    MetricsCard(listOf("System Type" to "Order: $pzOrder", "Stability" to pzStable))
                }
            }
        }
    }
}

// --- Helper Composable Functions ---
@Composable
fun InputField(label: String, value: String, modifier: Modifier = Modifier, color: Color = AccentPrimary, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, color = TextDim, fontSize = 12.sp) },
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = color, unfocusedIndicatorColor = Color.DarkGray, focusedTextColor = TextMain, unfocusedTextColor = TextMain),
        modifier = modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    )
}

@Composable
fun ToggleRow(text: String, checked: Boolean, color: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(BgInput, RoundedCornerShape(8.dp)).padding(16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = color))
    }
}

@Composable
fun ParameterSlider(name: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$name = ${String.format("%.1f", value)}", color = AccentSecondary, modifier = Modifier.width(60.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Slider(value = value, onValueChange = onValueChange, valueRange = min..max, colors = SliderDefaults.colors(thumbColor = AccentSecondary, activeTrackColor = AccentSecondary))
    }
}

@Composable
fun MetricsCard(metrics: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = BgInput), border = CardDefaults.outlinedCardBorder(true)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = TextMain, fontSize = 14.sp)
                    Text(value, color = AccentGold, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun PresetBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain), shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Left)
    }
}

// --- Universal Graph Renderer (Continuous, Discrete, PZ) ---
@Composable
fun GraphRenderer(
    tab: Int, xData: DoubleArray, yData: DoubleArray, fitY: DoubleArray,
    plotType: String, isFft: Boolean, isAnimated: Boolean,
    pzZeros: List<Complex>, pzPoles: List<Complex>
) {
    val infiniteTransition = rememberInfiniteTransition()
    val flowPhase by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart))

    Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(BgCanvas).border(1.dp, Color.DarkGray).padding(16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (tab == 0 || tab == 1) {
                if (xData.isEmpty() || yData.isEmpty() || xData.size != yData.size) return@Canvas
                val minX = xData.minOrNull() ?: 0.0; val maxX = xData.maxOrNull() ?: 1.0
                val minY = (yData.minOrNull() ?: -1.0) * 1.2; val maxY = (yData.maxOrNull() ?: 1.0) * 1.2
                val rx = if (maxX == minX) 1.0 else maxX - minX; val ry = if (maxY == minY) 1.0 else maxY - minY

                // Zero line
                val zY = h - ((0.0 - minY) / ry * h).toFloat()
                if (zY in 0f..h) drawLine(Color.White.copy(alpha=0.2f), Offset(0f, zY), Offset(w, zY), strokeWidth = 2f)

                // Render Discrete Bar
                if (tab == 1 && plotType == "bar") {
                    val barW = (w / xData.size) * 0.8f
                    for (i in xData.indices) {
                        val px = ((xData[i] - minX) / rx * w).toFloat()
                        val py = h - ((yData[i] - minY) / ry * h).toFloat()
                        drawRect(GcAccentOrange, Offset(px - barW/2, py), Size(barW, zY - py))
                    }
                }

                // Render Lines / Path
                if (tab == 0 || (tab == 1 && plotType == "lines")) {
                    val path = Path()
                    for (i in xData.indices) {
                        val px = ((xData[i] - minX) / rx * w).toFloat()
                        val py = h - ((yData[i] - minY) / ry * h).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    val clr = if(tab==0 && isFft) GcAccentPurple else if (tab==1) GcAccentPurple else AccentPrimary
                    drawPath(path, clr, style = Stroke(width = 3f))

                    // Animation Logic (Continuous Only)
                    if (tab == 0 && isAnimated) {
                        val points = 3
                        val spacing = xData.size / points
                        val offsetIdx = (flowPhase * xData.size).toInt()
                        for (p in 0 until points) {
                            val i = (offsetIdx + p * spacing) % xData.size
                            val px = ((xData[i] - minX) / rx * w).toFloat()
                            val py = h - ((yData[i] - minY) / ry * h).toFloat()
                            drawCircle(Color.White, radius = 8f, center = Offset(px, py))
                        }
                    }
                }

                // Render Scatter (Stem)
                if (tab == 1 && plotType == "scatter") {
                    for (i in xData.indices) {
                        val px = ((xData[i] - minX) / rx * w).toFloat()
                        val py = h - ((yData[i] - minY) / ry * h).toFloat()
                        drawLine(AccentSecondary.copy(alpha=0.5f), Offset(px, zY), Offset(px, py), 2f)
                        drawCircle(AccentSecondary, radius = 6f, center = Offset(px, py))
                    }
                }

                // Render Fit Line
                if (tab == 1 && fitY.isNotEmpty()) {
                    val fitPath = Path()
                    for (i in xData.indices) {
                        val px = ((xData[i] - minX) / rx * w).toFloat()
                        val py = h - ((fitY[i] - minY) / ry * h).toFloat()
                        if (i == 0) fitPath.moveTo(px, py) else fitPath.lineTo(px, py)
                    }
                    drawPath(fitPath, GcAccentRed, style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                }
            } else if (tab == 2) {
                // Pole-Zero Map Render
                val allRoots = pzZeros + pzPoles
                if (allRoots.isEmpty()) return@Canvas
                val maxAbs = allRoots.maxOfOrNull { max(abs(it.re), abs(it.im)) } ?: 1.0
                val scale = maxAbs * 1.5 // padding
                
                drawLine(Color.White.copy(0.3f), Offset(w/2, 0f), Offset(w/2, h), 2f) // Imaginary Axis
                drawLine(Color.White.copy(0.3f), Offset(0f, h/2), Offset(w, h/2), 2f) // Real Axis

                pzZeros.forEach { z ->
                    val px = w/2 + (z.re / scale * w/2).toFloat()
                    val py = h/2 - (z.im / scale * h/2).toFloat()
                    drawCircle(AccentPrimary, radius = 8f, center = Offset(px, py), style = Stroke(width = 3f))
                }
                pzPoles.forEach { p ->
                    val px = w/2 + (p.re / scale * w/2).toFloat()
                    val py = h/2 - (p.im / scale * h/2).toFloat()
                    drawLine(GcAccentRed, Offset(px-8f, py-8f), Offset(px+8f, py+8f), 3f)
                    drawLine(GcAccentRed, Offset(px-8f, py+8f), Offset(px+8f, py-8f), 3f)
                }
            }
        }
    }
}
// --- Entry point called from StudyToolsScreen ---
@Composable
fun GraphicCalculatorScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    UniversalAnalyzerProScreen()
}
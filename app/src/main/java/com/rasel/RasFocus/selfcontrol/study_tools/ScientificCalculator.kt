package com.rasel.RasFocus.selfcontrol.study_tools

// ─────────────────────────────────────────────────────────────────────────────
//  Scientific Calculator — Casio 991 ES Plus Clone
//  Natural textbook display, shift/alpha modes, full function set
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.*

// ─── Colors (991 ES Plus theme) ───────────────────────────────────────────────
private val CalcBg      = Color(0xFF1A1A2E)
private val DisplayBg   = Color(0xFF0D3B2E)
private val DisplayText = Color(0xFF7FFF9F)
private val BtnGray     = Color(0xFF2D2D44)
private val BtnDark     = Color(0xFF1E1E30)
private val BtnBlue     = Color(0xFF0066CC)
private val BtnRed      = Color(0xFFCC2200)
private val BtnOrange   = Color(0xFFFF6600)
private val ShiftColor  = Color(0xFFFFCC00)
private val AlphaColor  = Color(0xFFFF3399)
private val TextLight   = Color(0xFFE0E0E0)
private val CalcTextDim     = Color(0xFF888899)

// ─── Calculator State ─────────────────────────────────────────────────────────

private enum class AngleMode { DEG, RAD, GRAD }
private enum class CalcMode  { NORMAL, SHIFT, ALPHA }

@Composable
fun Calc991ESPlusScreen(onBack: () -> Unit) {
    var expr        by remember { mutableStateOf("") }
    var display     by remember { mutableStateOf("0") }
    var result      by remember { mutableStateOf("") }
    var angleMode   by remember { mutableStateOf(AngleMode.DEG) }
    var calcMode    by remember { mutableStateOf(CalcMode.NORMAL) }
    var history     by remember { mutableStateOf(listOf<String>()) }
    var isNewInput  by remember { mutableStateOf(true) }
    var memory      by remember { mutableStateOf(0.0) }

    fun toRad(x: Double) = when (angleMode) {
        AngleMode.DEG  -> Math.toRadians(x)
        AngleMode.RAD  -> x
        AngleMode.GRAD -> x * PI / 200.0
    }
    fun fromRad(x: Double) = when (angleMode) {
        AngleMode.DEG  -> Math.toDegrees(x)
        AngleMode.RAD  -> x
        AngleMode.GRAD -> x * 200.0 / PI
    }

    fun evaluate(expression: String): String {
        return try {
            var e = expression
                .replace("×", "*").replace("÷", "/")
                .replace("π", PI.toString())
                .replace("e", E.toString())
                .replace("%", "/100")

            // Handle trig functions with angle mode
            val trigFns = listOf("sin", "cos", "tan", "asin", "acos", "atan")
            for (fn in trigFns) {
                if (e.contains(fn)) {
                    val regex = Regex("$fn\\(([^)]+)\\)")
                    e = regex.replace(e) { match ->
                        val inner = match.groupValues[1].toDoubleOrNull()
                            ?: ExpressionBuilder(match.groupValues[1]).build().evaluate()
                        when (fn) {
                            "sin"  -> sin(toRad(inner)).toString()
                            "cos"  -> cos(toRad(inner)).toString()
                            "tan"  -> tan(toRad(inner)).toString()
                            "asin" -> fromRad(asin(inner)).toString()
                            "acos" -> fromRad(acos(inner)).toString()
                            "atan" -> fromRad(atan(inner)).toString()
                            else   -> match.value
                        }
                    }
                }
            }

            // Handle special functions
            e = e.replace(Regex("log\\(([^)]+)\\)")) { m ->
                val v = m.groupValues[1].toDoubleOrNull() ?: ExpressionBuilder(m.groupValues[1]).build().evaluate()
                log10(v).toString()
            }
            e = e.replace(Regex("ln\\(([^)]+)\\)")) { m ->
                val v = m.groupValues[1].toDoubleOrNull() ?: ExpressionBuilder(m.groupValues[1]).build().evaluate()
                ln(v).toString()
            }
            e = e.replace(Regex("√\\(([^)]+)\\)")) { m ->
                val v = m.groupValues[1].toDoubleOrNull() ?: ExpressionBuilder(m.groupValues[1]).build().evaluate()
                sqrt(v).toString()
            }
            e = e.replace(Regex("abs\\(([^)]+)\\)")) { m ->
                val v = m.groupValues[1].toDoubleOrNull() ?: ExpressionBuilder(m.groupValues[1]).build().evaluate()
                abs(v).toString()
            }

            val res = ExpressionBuilder(e).build().evaluate()
            val formatted = if (res == res.toLong().toDouble() && !res.isInfinite())
                res.toLong().toString() else "%.10g".format(res).trimEnd('0').trimEnd('.')
            formatted
        } catch (ex: Exception) {
            "Error"
        }
    }

    fun onKey(key: String) {
        when (key) {
            "AC" -> { expr = ""; display = "0"; result = ""; isNewInput = true; calcMode = CalcMode.NORMAL }
            "DEL" -> {
                calcMode = CalcMode.NORMAL
                if (expr.isNotEmpty()) {
                    expr = expr.dropLast(1)
                    display = if (expr.isEmpty()) "0" else expr
                }
            }
            "=" -> {
                calcMode = CalcMode.NORMAL
                if (expr.isNotEmpty()) {
                    val res = evaluate(expr)
                    if (res != "Error") {
                        history = (listOf("$expr = $res") + history).take(10)
                        result  = res
                        expr    = res
                        display = res
                        isNewInput = true
                    } else {
                        result = "Error"
                    }
                }
            }
            "SHIFT" -> calcMode = if (calcMode == CalcMode.SHIFT) CalcMode.NORMAL else CalcMode.SHIFT
            "ALPHA" -> calcMode = if (calcMode == CalcMode.ALPHA) CalcMode.NORMAL else CalcMode.ALPHA
            "DEG/RAD" -> {
                angleMode = when (angleMode) {
                    AngleMode.DEG  -> AngleMode.RAD
                    AngleMode.RAD  -> AngleMode.GRAD
                    AngleMode.GRAD -> AngleMode.DEG
                }
                calcMode = CalcMode.NORMAL
            }
            "M+" -> { memory += result.toDoubleOrNull() ?: 0.0; calcMode = CalcMode.NORMAL }
            "M-" -> { memory -= result.toDoubleOrNull() ?: 0.0; calcMode = CalcMode.NORMAL }
            "MR" -> {
                val m = if (memory == memory.toLong().toDouble()) memory.toLong().toString() else memory.toString()
                if (isNewInput) { expr = m; isNewInput = false } else expr += m
                display = expr; calcMode = CalcMode.NORMAL
            }
            "MC" -> { memory = 0.0; calcMode = CalcMode.NORMAL }
            "ANS" -> {
                if (result.isNotEmpty()) {
                    if (isNewInput) { expr = result; isNewInput = false } else expr += result
                    display = expr
                }
                calcMode = CalcMode.NORMAL
            }
            else -> {
                calcMode = CalcMode.NORMAL
                if (isNewInput && key in listOf("0","1","2","3","4","5","6","7","8","9",".")) {
                    expr = key; isNewInput = false
                } else {
                    if (isNewInput) isNewInput = false
                    expr += key
                }
                display = expr
                // Live preview
                val preview = evaluate(expr)
                result = if (preview != "Error") preview else ""
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CalcBg)
    ) {
        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF111122)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = TextLight, fontSize = 20.sp)
            }
            Text("Scientific Calculator", color = TextLight,
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(angleMode.name, color = ShiftColor, fontSize = 12.sp,
                modifier = Modifier.background(Color(0xFF222233), RoundedCornerShape(4.dp)).padding(4.dp, 2.dp))
        }

        // ── Display ────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().background(DisplayBg).padding(12.dp, 8.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Mode indicators
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (calcMode == CalcMode.SHIFT)
                        Text("SHIFT", color = ShiftColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (calcMode == CalcMode.ALPHA)
                        Text("ALPHA", color = AlphaColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (memory != 0.0)
                        Text("M", color = TextLight, fontSize = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                // Expression
                Text(
                    text = if (display == "0" && expr.isEmpty()) "0" else display,
                    color = DisplayText,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.height(4.dp))
                // Result preview
                if (result.isNotEmpty()) {
                    Text(
                        text = "= $result",
                        color = DisplayText.copy(alpha = .7f),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // ── Button grid ────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Row 1: SHIFT, ALPHA, mode, DEL, AC
            CalcRow {
                CalcBtn("SHIFT", ShiftColor,  if(calcMode==CalcMode.SHIFT) Color.Yellow else BtnDark, Modifier.weight(1f)) { onKey("SHIFT") }
                CalcBtn("ALPHA", AlphaColor,  if(calcMode==CalcMode.ALPHA) Color.Magenta else BtnDark, Modifier.weight(1f)) { onKey("ALPHA") }
                CalcBtn(angleMode.name, CalcTextDim, BtnDark, Modifier.weight(1f)) { onKey("DEG/RAD") }
                CalcBtn("DEL",   TextLight,   BtnOrange, Modifier.weight(1f)) { onKey("DEL") }
                CalcBtn("AC",    TextLight,   BtnRed,    Modifier.weight(1f)) { onKey("AC") }
            }
            // Row 2: sin, cos, tan, (, )
            CalcRow {
                CalcBtnDual(main="sin(",  shift="sin⁻¹(", alpha="A", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "asin(" else "sin(") }
                CalcBtnDual(main="cos(",  shift="cos⁻¹(", alpha="B", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "acos(" else "cos(") }
                CalcBtnDual(main="tan(",  shift="tan⁻¹(", alpha="C", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "atan(" else "tan(") }
                CalcBtn("(",     TextLight, BtnGray, Modifier.weight(1f)) { onKey("(") }
                CalcBtn(")",     TextLight, BtnGray, Modifier.weight(1f)) { onKey(")") }
            }
            // Row 3: log, ln, √, x², x³
            CalcRow {
                CalcBtnDual(main="log(",  shift="10^", alpha="D", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "10^(" else "log(") }
                CalcBtnDual(main="ln(",   shift="e^",  alpha="E", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "e^(" else "ln(") }
                CalcBtnDual(main="√(",    shift="x²",  alpha="F", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "^2" else "√(") }
                CalcBtnDual(main="^",     shift="x³",  alpha="G", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "^3" else "^") }
                CalcBtnDual(main="abs(",  shift="n!",  alpha="H", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "!" else "abs(") }
            }
            // Row 4: M+, M-, MR, MC, π/e
            CalcRow {
                CalcBtn("M+",  ShiftColor, BtnDark, Modifier.weight(1f)) { onKey("M+") }
                CalcBtn("M-",  ShiftColor, BtnDark, Modifier.weight(1f)) { onKey("M-") }
                CalcBtn("MR",  ShiftColor, BtnDark, Modifier.weight(1f)) { onKey("MR") }
                CalcBtn("MC",  ShiftColor, BtnDark, Modifier.weight(1f)) { onKey("MC") }
                CalcBtnDual(main="π", shift="e", alpha="I", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "e" else "π") }
            }
            // Row 5: 7, 8, 9, ÷, %
            CalcRow {
                CalcBtn("7",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey("7") }
                CalcBtn("8",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey("8") }
                CalcBtn("9",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey("9") }
                CalcBtn("÷",  TextLight, BtnBlue,   Modifier.weight(1f)) { onKey("÷") }
                CalcBtn("%",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey("%") }
            }
            // Row 6: 4, 5, 6, ×, 1/x
            CalcRow {
                CalcBtn("4",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("4") }
                CalcBtn("5",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("5") }
                CalcBtn("6",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("6") }
                CalcBtn("×",   TextLight, BtnBlue,   Modifier.weight(1f)) { onKey("×") }
                CalcBtnDual(main="x⁻¹", shift="EXP", alpha="J", mode=calcMode, mod=Modifier.weight(1f)) { onKey(if(calcMode==CalcMode.SHIFT) "E" else "^(-1)") }
            }
            // Row 7: 1, 2, 3, -, ANS
            CalcRow {
                CalcBtn("1",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("1") }
                CalcBtn("2",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("2") }
                CalcBtn("3",   TextLight, BtnGray,   Modifier.weight(1f)) { onKey("3") }
                CalcBtn("-",   TextLight, BtnBlue,   Modifier.weight(1f)) { onKey("-") }
                CalcBtn("ANS", TextLight, BtnDark,   Modifier.weight(1f)) { onKey("ANS") }
            }
            // Row 8: 0, ., ±, +, =
            CalcRow {
                CalcBtn("0",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey("0") }
                CalcBtn(".",  TextLight, BtnGray,   Modifier.weight(1f)) { onKey(".") }
                CalcBtn("±",  TextLight, BtnGray,   Modifier.weight(1f)) {
                    if (expr.startsWith("-")) expr = expr.drop(1) else expr = "-$expr"
                    display = expr
                }
                CalcBtn("+",  TextLight, BtnBlue,   Modifier.weight(1f)) { onKey("+") }
                CalcBtn("=",  Color.White, Color(0xFF00AA44), Modifier.weight(1f)) { onKey("=") }
            }
        }
    }
}

// ─── Helper Composables ───────────────────────────────────────────────────────

@Composable
private fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun CalcBtn(
    label   : String,
    textColor: Color,
    bgColor  : Color,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CalcBtnDual(
    main    : String,
    shift   : String,
    alpha   : String,
    mode    : CalcMode,
    mod     : Modifier = Modifier,
    onClick : () -> Unit
) {
    Box(
        mod
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BtnGray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(shift, color = ShiftColor, fontSize = 8.sp)
                Text(alpha, color = AlphaColor, fontSize = 8.sp)
            }
            Text(
                text = when (mode) {
                    CalcMode.SHIFT -> shift
                    CalcMode.ALPHA -> alpha
                    else           -> main
                },
                color = when (mode) {
                    CalcMode.SHIFT -> ShiftColor
                    CalcMode.ALPHA -> AlphaColor
                    else           -> TextLight
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

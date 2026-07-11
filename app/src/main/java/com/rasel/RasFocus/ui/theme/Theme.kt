package com.rasel.RasFocus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==========================================
// 1. COLORS
// ==========================================
val PrimaryTeal      = Color(0xFF0096B4)
val PrimaryTealDark  = Color(0xFF006E85)
val PrimaryTealLight = Color(0xFF4FC3D6)
val SoftWhite        = Color(0xFFF8FAFC) // Universal Soft Background
val PremiumCardBg    = Color(0xFFFFFFFF) // Crisp white for cards against soft bg
val SlateDark        = Color(0xFF0F172A) // Dark mode background
val SlateCard        = Color(0xFF1E293B) // Dark mode card
val SlateCardVariant = Color(0xFF334155) 
val TextDark         = Color(0xFF0F172A)
val TextGray         = Color(0xFF64748B)

val LockRed          = Color(0xFFFF3B30)
val ErrorRed         = Color(0xFFEF4444)
val SuccessGreen     = Color(0xFF10B981)
val WarningAmber     = Color(0xFFFB8C00)

class RasFocusColorScheme(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val isDark: Boolean
)

val LightColors = RasFocusColorScheme(
    primary = PrimaryTeal,
    background = SoftWhite,
    surface = PremiumCardBg,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = TextDark,
    textPrimary = TextDark,
    textSecondary = TextGray,
    divider = Color(0xFFE2E8F0),
    isDark = false
)

val DarkColors = RasFocusColorScheme(
    primary = PrimaryTealLight,
    background = SlateDark,
    surface = SlateCard,
    surfaceVariant = SlateCardVariant,
    onBackground = Color(0xFFF8FAFC),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    divider = Color(0xFF334155),
    isDark = true
)

// ==========================================
// 2. TYPOGRAPHY
// ==========================================
val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

// ==========================================
// 3. THEME PROVIDER
// ==========================================
val LocalRasFocusColors = staticCompositionLocalOf<RasFocusColorScheme> { LightColors }

object RasFocusTheme {
    val colors: RasFocusColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalRasFocusColors.current
    
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = AppTypography
}

@Composable
fun RasFocusAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = Color.White,
            onBackground = colors.onBackground,
            onSurface = colors.onBackground
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = Color.White,
            onBackground = colors.onBackground,
            onSurface = colors.onBackground
        )
    }

    CompositionLocalProvider(
        LocalRasFocusColors provides colors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// ==========================================
// STATIC COLOR OBJECT (for non-Composable usage in ChildControlModule etc.)
// ==========================================
object RasFocusColors {
    val PrimaryTeal       = Color(0xFF0096B4)
    val PrimaryTealLight  = Color(0xFF4FC3D6)
    val BackgroundWhite   = Color(0xFFF8FAFC)
    val SurfaceOffWhite   = Color(0xFFF1F5F9)
    val SurfaceCard       = Color(0xFFFFFFFF)
    val OnBackground      = Color(0xFF0F172A)
    val SubtleText        = Color(0xFF64748B)
    val DividerColor      = Color(0xFFE2E8F0)
    val ErrorRed          = Color(0xFFEF4444)
    val SuccessGreen      = Color(0xFF10B981)
    val WarningAmber      = Color(0xFFFB8C00)
}

package com.rasel.RasFocus.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = RasFocusTheme.colors.surface,
    colors: CardColors = CardDefaults.cardColors(containerColor = containerColor),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(20.dp),
        colors = colors,
        elevation = elevation
    ) {
        content()
    }
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = RasFocusTheme.colors.primary,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(text = text, style = RasFocusTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> AnimatedSwapContainer(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        modifier = modifier,
        label = "AnimatedSwapContainer"
    ) { state ->
        content(state)
    }
}

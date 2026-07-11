package com.rasel.pdfviewer.combo.selfcontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasel.pdfviewer.RasFocusColors

// ============================================================
// ENHANCED ADULT BLOCKER SCREEN
// Blocks adult/explicit content across browser + app traffic.
// No-arg composable — matches existing call site exactly:
//   composable("adult_block") { EnhancedAdultBlockerScreen() }
// ============================================================

private data class AdultBlockOption(
    val title: String,
    val subtitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedAdultBlockerScreen() {
    var masterEnabled by remember { mutableStateOf(true) }
    var blockBrowserAdult by remember { mutableStateOf(true) }
    var blockAppStoreMature by remember { mutableStateOf(true) }
    var blockSearchExplicit by remember { mutableStateOf(true) }
    var blockIncognitoBypass by remember { mutableStateOf(true) }

    val options = remember {
        listOf(
            AdultBlockOption("Browser-এ Adult Sites Block", "Known adult/explicit websites browser-এ খুলবে না"),
            AdultBlockOption("Mature App Content Block", "App Store-এর 18+ rated app install/run বন্ধ করে"),
            AdultBlockOption("Explicit Search Filter", "Google/Bing-এ explicit search result filter করে"),
            AdultBlockOption("Incognito Bypass Block", "Incognito/Private mode দিয়ে filter এড়ানো বন্ধ করে")
        )
    }

    Scaffold(
        containerColor = RasFocusColors.BackgroundWhite,
        topBar = {
            TopAppBar(
                title = { Text("Adult Content Blocker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RasFocusColors.BackgroundWhite,
                    titleContentColor = RasFocusColors.OnBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (masterEnabled)
                    RasFocusColors.SuccessGreen.copy(alpha = 0.10f)
                else
                    RasFocusColors.ErrorRed.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (masterEnabled) Icons.Filled.Shield else Icons.Filled.Block,
                        contentDescription = null,
                        tint = if (masterEnabled) RasFocusColors.SuccessGreen else RasFocusColors.ErrorRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (masterEnabled) "Adult Block Active" else "Adult Block Off",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RasFocusColors.OnBackground
                        )
                        Text(
                            text = if (masterEnabled)
                                "সব explicit content filter চালু আছে"
                            else
                                "Filter বন্ধ — child device-এ ON রাখা recommended",
                            fontSize = 12.sp,
                            color = RasFocusColors.SubtleText
                        )
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = { masterEnabled = it }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Block Categories",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = RasFocusColors.OnBackground
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = RasFocusColors.SurfaceCard
            ) {
                Column {
                    AdultBlockRow(
                        title = options[0].title,
                        subtitle = options[0].subtitle,
                        checked = blockBrowserAdult,
                        enabled = masterEnabled,
                        onChecked = { blockBrowserAdult = it }
                    )
                    AdultBlockRow(
                        title = options[1].title,
                        subtitle = options[1].subtitle,
                        checked = blockAppStoreMature,
                        enabled = masterEnabled,
                        onChecked = { blockAppStoreMature = it }
                    )
                    AdultBlockRow(
                        title = options[2].title,
                        subtitle = options[2].subtitle,
                        checked = blockSearchExplicit,
                        enabled = masterEnabled,
                        onChecked = { blockSearchExplicit = it }
                    )
                    AdultBlockRow(
                        title = options[3].title,
                        subtitle = options[3].subtitle,
                        checked = blockIncognitoBypass,
                        enabled = masterEnabled,
                        onChecked = { blockIncognitoBypass = it },
                        isLast = true
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "এই filter device-এর DNS ও Accessibility Service ব্যবহার করে কাজ করে। সম্পূর্ণ protection-এর জন্য Accessibility permission চালু রাখুন।",
                fontSize = 12.sp,
                color = RasFocusColors.SubtleText,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdultBlockRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { onChecked(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (enabled) RasFocusColors.OnBackground else RasFocusColors.SubtleText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = RasFocusColors.SubtleText,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChecked
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = RasFocusColors.DividerColor
            )
        }
    }
}

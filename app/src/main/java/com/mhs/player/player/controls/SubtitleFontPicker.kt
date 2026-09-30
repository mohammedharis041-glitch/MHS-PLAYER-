package com.mhs.player.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v2-beta: Font picker for subtitles (issue #2)
 *
 * Usage in SubtitleSettingsSheet:
 * ```
 * SubtitleFontPicker(
 *   selectedFamily = settings.subtitleFontFamily,
 *   onSelect = { viewModel.setSubtitleFontFamily(it) },
 *   onImport = { /* launch SAF for .ttf/.otf */ }
 * )
 * ```
 */
@Composable
fun SubtitleFontPicker(
    selectedFamily: String,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bundledFonts = listOf("Default", "Inter", "Roboto Condensed", "Poppins", "Noto Sans Malayalam")

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "FONT",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = Color.White.copy(0.4f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(bundledFonts) { font ->
                val isSelected = font == selectedFamily
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Color(0xFF00E5CC).copy(0.15f)
                            else Color.White.copy(0.06f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF00E5CC).copy(0.4f)
                            else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(font) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        font,
                        color = if (isSelected) Color.White else Color.White.copy(0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.04f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                        .clickable { onImport() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Import .ttf", color = Color.White.copy(0.6f), fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Preview: The quick brown fox jumps over the lazy dog",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.65f), RoundedCornerShape(6.dp))
                .padding(12.dp)
        )
    }
}

/**
 * v2-beta: Off-toggle that preserves cached track (fix for issue #1)
 *
 * Replace existing "Off" handling:
 * OLD: onSelectTrack(-1) -> clears track
 * NEW:
 * ```
 * if (trackManager.hasCachedTrack() && !trackManager.isEnabled.value) {
 *   // Show "Subtitles: Off — [label]" chip with Re-enable button
 * } else {
 *   trackManager.disableWithoutClearing()
 *   player.setSubtitleEnabled(false) // disable renderer, don't clear
 * }
 * ```
 */
@Composable
fun SubtitleOffChip(
    cachedLabel: String,
    onReEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.06f))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Subtitles: Off — $cachedLabel",
            color = Color.White.copy(0.7f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Re-enable",
            color = Color(0xFF00E5CC),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onReEnable() }
                .padding(8.dp)
        )
    }
}

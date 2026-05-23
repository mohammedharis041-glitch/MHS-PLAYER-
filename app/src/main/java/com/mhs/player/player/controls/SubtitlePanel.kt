package com.mhs.player.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitlePanel(
    subtitleText: String,
    fontSize: Float = 16f,
    showBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (subtitleText.isBlank()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showBackground) {
            Text(
                text = subtitleText,
                fontSize = fontSize.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = (fontSize * 1.4f).sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Text(
                text = subtitleText,
                fontSize = fontSize.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = (fontSize * 1.4f).sp
            )
        }
    }
}


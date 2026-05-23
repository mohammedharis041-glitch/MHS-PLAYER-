package com.mhs.player.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.ui.theme.AccentCyan
import com.mhs.player.ui.theme.Primary
import com.mhs.player.ui.theme.glassCard
import com.mhs.player.ui.theme.glassButton
import com.mhs.player.ui.theme.accentGlow

@Composable
fun AudioSettingsSheet(
    currentTracks: List<PlayerController.TrackInfo>,
    audioDelayMs: Long,
    onSelectTrack: (Int) -> Unit,
    onUpdateDelay: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var activeSlider by remember { mutableStateOf<String?>(null) }

    val fillAlpha by animateFloatAsState(
        targetValue = if (activeSlider != null) 0.01f else 0.45f,
        animationSpec = tween(300),
        label = "fill_alpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (activeSlider != null) 0.03f else 0.20f,
        animationSpec = tween(300),
        label = "border_alpha"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "header_alpha"
    )
    val tracksAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "tracks_alpha"
    )
    val delayAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "delay") 1f else 0.08f,
        animationSpec = tween(300),
        label = "delay_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.92f)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .glassCard(cornerRadius = 24.dp, fillAlpha = fillAlpha, borderAlpha = borderAlpha, useDarkBg = false)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = headerAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Audio Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
    
                // Audio Tracks
                item {
                    Column(modifier = Modifier.graphicsLayer { alpha = tracksAlpha }) {
                        Text("Select Audio Track", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.6f))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                
                items(currentTracks) { track ->
                    val isSupported = track.isSupported
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = tracksAlpha }
                            .clickable(enabled = isSupported) { onSelectTrack(track.index) }
                            .padding(vertical = 8.dp)
                            .alpha(if (isSupported) 1f else 0.4f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                track.label,
                                color = if (track.isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (track.isSelected) FontWeight.Black else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (!isSupported) {
                                Text(
                                    "Hardware decoder not supported",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (track.isSelected) {
                            Icon(
                                Icons.Default.CheckCircle, 
                                null, 
                                tint = AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
    
                // Audio Sync (Delay) - Premium Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = delayAlpha }
                            .glassCard(cornerRadius = 24.dp, fillAlpha = 0.05f, borderAlpha = 0.2f)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sync, null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Audio Sync", 
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .glassButton(cornerRadius = 8.dp, fillAlpha = 0.15f)
                                            .clickable { onUpdateDelay(0L) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = AccentCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("${audioDelayMs}ms", color = AccentCyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                            
                            Slider(
                                value = audioDelayMs.toFloat(),
                                onValueChange = { 
                                    activeSlider = "delay"
                                    onUpdateDelay(it.toLong()) 
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = -5000f..5000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { 
                                    onUpdateDelay(audioDelayMs - 10) 
                                }) {
                                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White.copy(0.4f))
                                }
                                
                                IconButton(onClick = { 
                                    onUpdateDelay(audioDelayMs - 50) 
                                }) {
                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.White.copy(0.7f))
                                }
                                
                                Text(
                                    "Fine Adjust", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = Color.White.copy(0.5f)
                                )
                                
                                IconButton(onClick = { 
                                    onUpdateDelay(audioDelayMs + 50) 
                                }) {
                                    Icon(Icons.Default.AddCircleOutline, null, tint = Color.White.copy(0.7f))
                                }
                                
                                IconButton(onClick = { 
                                    onUpdateDelay(audioDelayMs + 10) 
                                }) {
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


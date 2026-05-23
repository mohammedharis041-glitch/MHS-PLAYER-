package com.mhs.player.player.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhs.player.settings.SettingsRepository
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassOverlay
import com.mhs.player.ui.theme.glassButton
import com.mhs.player.ui.theme.accentGlow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas

@Composable
fun SubtitleSettingsSheet(
    settings: SettingsRepository.AppSettings,
    currentTracks: List<PlayerController.TrackInfo>,
    onSelectTrack: (Int) -> Unit,
    onSearchOnline: () -> Unit,
    onSelectLocalFile: () -> Unit,
    onUpdateSubtitleSize: (Float) -> Unit,
    onUpdateSubtitleOpacity: (Float) -> Unit,
    onUpdateSubtitlePosition: (Float) -> Unit,
    onUpdateTargetLang: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateTranslationEnabled: (Boolean) -> Unit,
    subtitleDelayMs: Long = 0L,
    onUpdateDelay: (Long) -> Unit = {},
    subtitleSpeed: Float = 1.0f,
    onUpdateSpeed: (Float) -> Unit = {},
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
    val transAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "trans_alpha"
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "source_alpha"
    )
    val tracksLabelAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "tracks_label_alpha"
    )
    val tracksAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "tracks_alpha"
    )
    val appearanceLabelAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "appearance_label_alpha"
    )
    val tempoLabelAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null) 1f else 0.08f,
        animationSpec = tween(300),
        label = "tempo_label_alpha"
    )

    val sizeAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "size") 1f else 0.08f,
        animationSpec = tween(300),
        label = "size_alpha"
    )
    val positionAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "position") 1f else 0.08f,
        animationSpec = tween(300),
        label = "position_alpha"
    )
    val opacityAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "opacity") 1f else 0.08f,
        animationSpec = tween(300),
        label = "opacity_alpha"
    )
    val delayAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "delay") 1f else 0.08f,
        animationSpec = tween(300),
        label = "delay_alpha"
    )
    val speedAlpha by animateFloatAsState(
        targetValue = if (activeSlider == null || activeSlider == "speed") 1f else 0.08f,
        animationSpec = tween(300),
        label = "speed_alpha"
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
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .glassOverlay(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {} // block clicks inside the sheet
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Drag handle lookalike
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(0.2f), RoundedCornerShape(2.dp))
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Compact Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = headerAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Subtitle Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }

                // AI Translation Feature Card (COMPACT)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = transAlpha }
                            .glassCard(shape = AppShapes.RoundedMD, isPlaybackActive = true)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome, null, 
                                        tint = AppColors.CyanGlow, 
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "AI Translation",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Switch(
                                    checked = settings.subtitleTranslationEnabled,
                                    onCheckedChange = onUpdateTranslationEnabled,
                                    modifier = Modifier.scale(0.8f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AppColors.CyanGlow,
                                        uncheckedTrackColor = Color.White.copy(0.1f)
                                    )
                                )
                            }
                            
                            if (settings.subtitleTranslationEnabled) {
                                Spacer(Modifier.height(8.dp))
                                // Compact API input
                                OutlinedTextField(
                                    value = settings.subtitleApiKey,
                                    onValueChange = onUpdateApiKey,
                                    placeholder = { Text("Gemini API Key", color = Color.White.copy(0.3f), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppColors.CyanGlow,
                                        unfocusedBorderColor = Color.White.copy(0.1f)
                                    )
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val langs = listOf("ml" to "ML", "hi" to "HI", "ar" to "AR", "en" to "EN", "ta" to "TA", "te" to "TE")
                                    langs.forEach { (code, name) ->
                                        FilterChip(
                                            selected = settings.subtitleTargetLang == code,
                                            onClick = { onUpdateTargetLang(code) },
                                            label = { Text(name, fontSize = 11.sp, color = if (settings.subtitleTargetLang == code) Color.Black else Color.White) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AppColors.CyanGlow,
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color.White.copy(0.04f)
                                            ),
                                            border = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtitle Sourcing Actions
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = sourceAlpha }
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Online Search (PRIMARY)
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .clip(AppShapes.RoundedMD)
                                .background(AppColors.CyanGlow)
                                .clickable { onSearchOnline() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDownload, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Search Online",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black
                                )
                            }
                        }
                        
                        // Local File
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(AppShapes.RoundedMD)
                                .background(Color.White.copy(0.08f))
                                .border(1.dp, Color.White.copy(0.1f), AppShapes.RoundedMD)
                                .clickable { onSelectLocalFile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Local File",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "TRACKS",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = Color.White.copy(0.4f),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .graphicsLayer { alpha = tracksLabelAlpha }
                    )
                }

                items(currentTracks) { track ->
                    val isSupported = track.isSupported
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .graphicsLayer { alpha = tracksAlpha }
                            .clip(AppShapes.RoundedMD)
                            .background(if (track.isSelected) AppColors.CyanGlow.copy(0.1f) else Color.Transparent)
                            .border(
                                1.dp, 
                                if (track.isSelected) AppColors.CyanGlow.copy(0.3f) else Color.Transparent,
                                AppShapes.RoundedMD
                            )
                            .clickable(enabled = isSupported) { onSelectTrack(track.index) }
                            .alpha(if (isSupported) 1f else 0.5f)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (track.isSelected) Icons.Default.CheckCircle else Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = if (track.isSelected) AppColors.CyanGlow else Color.White.copy(0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                track.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (track.isSelected) Color.White else Color.White.copy(0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isSupported) {
                                Text(
                                    "Codec not supported",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(0.8f)
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }

                item {
                    Text(
                        "APPEARANCE & SYNC",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = Color.White.copy(0.4f),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .graphicsLayer { alpha = appearanceLabelAlpha }
                    )
                }

                // Premium Real-time Subtitle Preview Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .graphicsLayer { alpha = appearanceLabelAlpha }
                            .clip(AppShapes.RoundedMD)
                            .background(Color(0xFF060609)) // Matte deep charcoal mimicking video screen
                            .border(BorderStroke(1.dp, Color.White.copy(0.08f)), AppShapes.RoundedMD),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient backdrop mesh to simulate video content behind subtitles
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = AppColors.CyanGlow.copy(alpha = 0.12f),
                                radius = size.minDimension * 0.45f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.35f)
                            )
                            drawCircle(
                                color = AppColors.AccentViolet.copy(alpha = 0.12f),
                                radius = size.minDimension * 0.35f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.65f)
                            )
                        }

                        // Video overlay simulated content shadow
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                        )

                        // Subtitle text reacting dynamically to settings adjustments
                        Text(
                            text = "The universe is full of magical things...",
                            color = Color.White.copy(alpha = settings.subtitleOpacity),
                            style = AppTypography.SubtitleText.copy(
                                fontSize = (settings.subtitleSize * 0.68f).coerceAtLeast(10f).sp, // Scaled for HUD preview
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .align(
                                    when {
                                        settings.subtitlePosition < 0.33f -> Alignment.TopCenter
                                        settings.subtitlePosition > 0.66f -> Alignment.BottomCenter
                                        else -> Alignment.Center
                                    }
                                )
                                .padding(
                                    top = if (settings.subtitlePosition < 0.33f) 8.dp else 0.dp,
                                    bottom = if (settings.subtitlePosition > 0.66f) 8.dp else 0.dp
                                )
                        )
                    }
                }

                // Appearance settings (Size, Position, Opacity, Delay)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.RoundedMD)
                            .background(Color.White.copy(0.04f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Size
                        Column(modifier = Modifier.graphicsLayer { alpha = sizeAlpha }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Font Size", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.8f))
                                Text("${settings.subtitleSize.toInt()} sp", color = AppColors.CyanGlow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.subtitleSize,
                                onValueChange = {
                                    activeSlider = "size"
                                    onUpdateSubtitleSize(it)
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = 10f..48f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppColors.CyanGlow,
                                    inactiveTrackColor = Color.White.copy(0.1f)
                                )
                            )
                        }

                        // Position
                        Column(modifier = Modifier.graphicsLayer { alpha = positionAlpha }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vertical Position", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.8f))
                                Text("${(settings.subtitlePosition * 100).toInt()}%", color = AppColors.CyanGlow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.subtitlePosition,
                                onValueChange = {
                                    activeSlider = "position"
                                    onUpdateSubtitlePosition(it)
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppColors.CyanGlow,
                                    inactiveTrackColor = Color.White.copy(0.1f)
                                )
                            )
                        }

                        // Opacity
                        Column(modifier = Modifier.graphicsLayer { alpha = opacityAlpha }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Opacity", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.8f))
                                Text("${(settings.subtitleOpacity * 100).toInt()}%", color = AppColors.CyanGlow, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = settings.subtitleOpacity,
                                onValueChange = {
                                    activeSlider = "opacity"
                                    onUpdateSubtitleOpacity(it)
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = 0.2f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppColors.CyanGlow,
                                    inactiveTrackColor = Color.White.copy(0.1f)
                                )
                            )
                        }

                        // Robust Subtitle Sync (Delay)
                        Column(modifier = Modifier.graphicsLayer { alpha = delayAlpha }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sync, null, tint = AppColors.CyanGlow, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Synchronization", 
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .glassButton(cornerRadius = 8.dp, fillAlpha = 0.15f)
                                            .clickable { onUpdateDelay(0) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = AppColors.CyanGlow, fontWeight = FontWeight.Bold)
                                    }
                                }
                                val delaySec = subtitleDelayMs / 1000f
                                val sign = if (delaySec > 0) "+" else ""
                                Text("$sign${String.format("%.2f", delaySec)}s", color = AppColors.CyanGlow, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                            
                            Slider(
                                value = subtitleDelayMs.toFloat(),
                                onValueChange = { 
                                    activeSlider = "delay"
                                    onUpdateDelay(it.toLong()) 
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = -30000f..30000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppColors.CyanGlow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                            
                            // Jump Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs - 1000) 
                                }) {
                                    Icon(Icons.Default.KeyboardDoubleArrowLeft, null, tint = Color.White.copy(0.3f))
                                }
                                
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs - 50) 
                                }) {
                                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White.copy(0.4f))
                                }
    
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs - 250) 
                                }) {
                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.White.copy(0.7f))
                                }
    
                                Text(
                                    "Fine Sync", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    color = Color.White.copy(0.5f)
                                )
                                
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs + 250) 
                                }) {
                                    Icon(Icons.Default.AddCircleOutline, null, tint = Color.White.copy(0.7f))
                                }
    
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs + 50) 
                                }) {
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(0.4f))
                                }
    
                                IconButton(onClick = { 
                                    onUpdateDelay(subtitleDelayMs + 1000) 
                                }) {
                                    Icon(Icons.Default.KeyboardDoubleArrowRight, null, tint = Color.White.copy(0.3f))
                                }
                            }
                        }
                    }
                }

                // Subtitle Speed (Tempo)
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "TEMPO / SPEED",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = Color.White.copy(0.4f),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .graphicsLayer { alpha = tempoLabelAlpha }
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = speedAlpha }
                            .glassCard(shape = AppShapes.RoundedMD, isPlaybackActive = true)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, null, tint = AppColors.CyanGlow, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Subtitle Speed", 
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .glassButton(cornerRadius = 8.dp, fillAlpha = 0.15f)
                                            .clickable { onUpdateSpeed(1.0f) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = AppColors.CyanGlow, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("${String.format("%.2f", subtitleSpeed)}x", color = AppColors.CyanGlow, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                            
                            Slider(
                                value = subtitleSpeed,
                                onValueChange = { 
                                    activeSlider = "speed"
                                    onUpdateSpeed(it) 
                                },
                                onValueChangeFinished = { activeSlider = null },
                                valueRange = 0.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppColors.CyanGlow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                            
                            Text(
                                "Adjust if subtitles get out of sync over time",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

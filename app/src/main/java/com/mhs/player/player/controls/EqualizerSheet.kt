package com.mhs.player.player.controls

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.AppSpacing
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassOverlay
import com.mhs.player.ui.screens.PlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyRow

// Built-in EQ presets with band gain levels (mB, 5 bands: 60Hz 230Hz 910Hz 3.6kHz 14kHz)
private data class EqPreset(val name: String, val gains: List<Int>)
private val EQ_PRESETS = listOf(
    EqPreset("Flat",       listOf(0, 0, 0, 0, 0)),
    EqPreset("Bass Boost", listOf(600, 400, 0, 0, 0)),
    EqPreset("Rock",       listOf(500, 200, -100, 200, 400)),
    EqPreset("Pop",        listOf(-100, 200, 400, 200, -100)),
    EqPreset("Jazz",       listOf(300, 200, 0, 200, 400)),
    EqPreset("Classical",  listOf(500, 300, -200, 200, 400)),
    EqPreset("Vocal",      listOf(-200, 0, 400, 300, -100)),
    EqPreset("Dance",      listOf(500, 300, 100, 300, 200)),
    EqPreset("Podcast",    listOf(-300, 0, 300, 300, 100)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    audioSessionId: Int,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    // Release AudioEffectsManager's instances so we can take exclusive control
    // (Android only allows one active Equalizer per audio session)
    val effectsManager = viewModel.playerController.audioEffectsManager
    DisposableEffect(audioSessionId) {
        effectsManager.release()
        onDispose {
            // Re-initialize AudioEffectsManager when sheet closes
            if (audioSessionId > 0) {
                effectsManager.initialize(audioSessionId)
            }
        }
    }

    // Create Equalizer only once per audioSession
    val eq = remember(audioSessionId) {
        try {
            Equalizer(0, audioSessionId).also {
                it.enabled = settings.equalizerEnabled
                // Apply saved band levels
                val savedBands = settings.equalizerBands.split(",").mapNotNull { s -> s.toIntOrNull() }
                savedBands.forEachIndexed { b, level ->
                    if (b < it.numberOfBands) {
                        it.setBandLevel(b.toShort(), level.toShort())
                    }
                }
            }
        } catch (e: Exception) { null }
    }
    val bassBoost = remember(audioSessionId) {
        try {
            BassBoost(0, audioSessionId).also {
                it.enabled = settings.equalizerEnabled
                it.setStrength((settings.equalizerBassBoost * 10).toShort())
            }
        } catch (e: Exception) { null }
    }

    DisposableEffect(eq, bassBoost) {
        onDispose {
            eq?.release()
            bassBoost?.release()
        }
    }

    val numBands = eq?.numberOfBands?.toInt() ?: 5
    val minLevel = eq?.bandLevelRange?.get(0)?.toInt() ?: -1500
    val maxLevel = eq?.bandLevelRange?.get(1)?.toInt() ?: 1500

    // Band labels (Hz)
    val bandLabels = remember(eq) {
        (0 until numBands).map { b ->
            val centerFreq = eq?.getCenterFreq(b.toShort()) ?: 0
            val hz = centerFreq / 1000
            if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"
        }
    }

    // Current band levels as state — initialize from hardware if available, else flat
    val bandLevels = remember(eq) {
        mutableStateListOf(*Array(numBands) { b ->
            eq?.getBandLevel(b.toShort())?.toFloat() ?: 0f
        })
    }

    // Initialize state from persistent settings
    var selectedPreset by remember { mutableIntStateOf(settings.equalizerPreset) }
    var bassBoostStrength by remember { mutableIntStateOf(settings.equalizerBassBoost) }
    var eqEnabled by remember { mutableStateOf(settings.equalizerEnabled) }

    // Side effect to apply settings if they change in repository (e.g. initial load)
    LaunchedEffect(settings.equalizerEnabled) {
        eqEnabled = settings.equalizerEnabled
        eq?.enabled = eqEnabled
        bassBoost?.enabled = eqEnabled
    }
    
    LaunchedEffect(settings.equalizerBassBoost) {
        bassBoostStrength = settings.equalizerBassBoost
        bassBoost?.setStrength((bassBoostStrength * 10).toShort())
    }

    var activeSlider by remember { mutableStateOf<String?>(null) }

    val fillAlpha by animateFloatAsState(
        targetValue = if (activeSlider != null) 0.01f else 0.90f,
        animationSpec = tween(300),
        label = "fill_alpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (activeSlider != null) 0.03f else 0.25f,
        animationSpec = tween(300),
        label = "border_alpha"
    )
    val otherElementsAlpha by animateFloatAsState(
        targetValue = if (activeSlider != null) 0.08f else 1.0f,
        animationSpec = tween(300),
        label = "other_elements_alpha"
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
                .graphicsLayer { alpha = fillAlpha }
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = false)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = otherElementsAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Equalizer, null,
                        tint = AppColors.CyanGlow,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        "Equalizer",
                        style = AppTypography.StandardTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = {
                        eqEnabled = it
                        eq?.enabled = it
                        bassBoost?.enabled = it
                        viewModel.setEqualizerEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.CyanGlow,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Preset chips
            Column(modifier = Modifier.graphicsLayer { alpha = otherElementsAlpha }) {
                Text(
                    "Presets", 
                    style = AppTypography.StandardTypography.labelMedium, 
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EQ_PRESETS.size) { i ->
                        val preset = EQ_PRESETS[i]
                        FilterChip(
                            selected = selectedPreset == i,
                            onClick = {
                                selectedPreset = i
                                viewModel.setEqualizerPreset(i)
                                preset.gains.forEachIndexed { b, gain ->
                                    if (b < numBands) {
                                        val clamped = gain.coerceIn(minLevel, maxLevel)
                                        bandLevels[b] = clamped.toFloat()
                                        eq?.setBandLevel(b.toShort(), clamped.toShort())
                                    }
                                }
                                viewModel.setEqualizerBands(bandLevels.joinToString(",") { it.toInt().toString() })
                            },
                            label = { Text(preset.name, style = AppTypography.StandardTypography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.CyanGlow,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White.copy(alpha = 0.7f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedPreset == i,
                                borderColor = Color.White.copy(alpha = 0.12f),
                                selectedBorderColor = AppColors.CyanGlow,
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Band sliders
            Text(
                "Bands", 
                style = AppTypography.StandardTypography.labelMedium, 
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer { alpha = otherElementsAlpha }
            )
            Spacer(Modifier.height(8.dp))

            if (eq != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(numBands) { b ->
                        val currentBandAlpha by animateFloatAsState(
                            targetValue = if (activeSlider == null || activeSlider == "band_$b") 1.0f else 0.08f,
                            animationSpec = tween(300),
                            label = "band_alpha_$b"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = currentBandAlpha }
                        ) {
                            // Current level label
                            Text(
                                "${(bandLevels[b] / 100).toInt()}dB",
                                style = AppTypography.StandardTypography.labelSmall,
                                color = if (bandLevels[b] != 0f) AppColors.CyanGlow else Color.White.copy(alpha = 0.4f)
                            )
                            
                            // Sleek Vertical Glass Column
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VerticalEqSlider(
                                    value = bandLevels[b],
                                    onValueChange = { v ->
                                        activeSlider = "band_$b"
                                        selectedPreset = -1  // custom
                                        bandLevels[b] = v
                                        eq.setBandLevel(b.toShort(), v.toInt().toShort())
                                    },
                                    onValueChangeFinished = {
                                        activeSlider = null
                                        viewModel.setEqualizerPreset(-1)
                                        viewModel.setEqualizerBands(bandLevels.joinToString(",") { it.toInt().toString() })
                                    },
                                    valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                                    activeGradient = if (b % 2 == 0) AppColors.CyanBlueGradient else AppColors.VioletPinkGradient
                                )
                            }
                            
                            // Band label
                            Text(
                                bandLabels.getOrElse(b) { "" },
                                style = AppTypography.StandardTypography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Equalizer not available for this audio session.",
                    color = Color.White.copy(alpha = 0.5f),
                    style = AppTypography.StandardTypography.bodySmall,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Bass Boost
            if (bassBoost != null) {
                val currentBassAlpha by animateFloatAsState(
                    targetValue = if (activeSlider == null || activeSlider == "bass") 1.0f else 0.08f,
                    animationSpec = tween(300),
                    label = "bass_alpha"
                )
                Column(modifier = Modifier.graphicsLayer { alpha = currentBassAlpha }) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.GraphicEq, null, tint = AppColors.CyanGlow, modifier = Modifier.size(20.dp))
                        Text(
                            "Bass Boost", 
                            style = AppTypography.StandardTypography.bodyMedium, 
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${bassBoostStrength}%", 
                            style = AppTypography.StandardTypography.bodySmall, 
                            color = AppColors.CyanGlow
                        )
                    }
                    Slider(
                        value = bassBoostStrength.toFloat(),
                        onValueChange = {
                            activeSlider = "bass"
                            bassBoostStrength = it.toInt()
                            bassBoost.setStrength((it * 10).toInt().coerceIn(0, 1000).toShort())
                        },
                        onValueChangeFinished = {
                            activeSlider = null
                            viewModel.setEqualizerBassBoost(bassBoostStrength)
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.CyanGlow,
                            activeTrackColor = AppColors.CyanGlow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.10f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Reset button
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    selectedPreset = 0
                    viewModel.setEqualizerPreset(0)
                    repeat(numBands) { b ->
                        bandLevels[b] = 0f
                        eq?.setBandLevel(b.toShort(), 0)
                    }
                    viewModel.setEqualizerBands(bandLevels.joinToString(",") { it.toInt().toString() })
                    bassBoostStrength = 0
                    bassBoost?.setStrength(0)
                    viewModel.setEqualizerBassBoost(0)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = otherElementsAlpha },
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = AppColors.CyanBlueGradient
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset to Flat")
            }
        }
    }
}

/** Sleek Custom Vertical Equalizer Slider with Gradient Fill and Touch Feedback */
@Composable
private fun VerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedRange<Float>,
    modifier: Modifier = Modifier,
    activeGradient: Brush = AppColors.CyanBlueGradient
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val height = size.height
                    if (height > 0) {
                        val fraction = (1f - (down.position.y / height)).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                    
                    var drag = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (!anyPressed) {
                            onValueChangeFinished()
                            break
                        }
                        val dragChange = event.changes.firstOrNull { it.id == drag.id }
                        if (dragChange != null) {
                            if (dragChange.positionChange() != Offset.Zero) {
                                val fraction = (1f - (dragChange.position.y / height)).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newValue)
                                dragChange.consume()
                            }
                        } else {
                            val newPointer = event.changes.firstOrNull { it.pressed }
                            if (newPointer != null) {
                                drag = newPointer
                            }
                        }
                    }
                }
            }
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        
        // Active Gradient Fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .align(Alignment.BottomCenter)
                .background(activeGradient)
        )
        
        // Sleek thumb overlay
        if (fraction > 0f) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val thumbOffset = with(density) { (fraction * maxHeight.toPx()) }
                        translationY = -thumbOffset
                    }
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, AppColors.CyanGlow.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}


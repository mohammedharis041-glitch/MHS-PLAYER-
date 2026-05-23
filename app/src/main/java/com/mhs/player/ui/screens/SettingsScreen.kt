package com.mhs.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mhs.player.BuildConfig
import com.mhs.player.navigation.Screen
import com.mhs.player.settings.SettingsViewModel
import com.mhs.player.updater.UpdateViewModel
import com.mhs.player.updater.ui.UpdateCard
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.themeAccentGradient
import com.mhs.player.ui.theme.themeOnAccent
import com.mhs.player.ui.theme.designsystem.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {

            // ── PLAYBACK ────────────────────────────────────────────────
            item { SettingsSectionHeader("Playback", Icons.Default.PlayCircle) }

            item {
                // Seek duration chip selector
                SettingsChipSelector(
                    icon = Icons.Default.FastForward,
                    title = "Double-Tap Seek Duration",
                    subtitle = "Seconds skipped per double tap",
                    options = listOf(5, 10, 15, 30),
                    selectedValue = settings.seekDurationPreset,
                    onSelect = { viewModel.setSeekDurationPreset(it) },
                    labelFor = { "${it}s" }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Memory,
                    title = "Hardware Decoding",
                    subtitle = "Use GPU for video decoding",
                    checked = settings.hardwareDecoding,
                    onCheckedChange = viewModel::setHardwareDecoding
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Speed,
                    title = "Enhanced Playback Mode",
                    subtitle = "Ultra-low latency hardware decoding & snappier seeking (MX HW+ style)",
                    checked = settings.enhancedPlaybackMode,
                    onCheckedChange = viewModel::setEnhancedPlaybackMode
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.SaveAlt,
                    title = "Remember Position",
                    subtitle = "Resume from where you left off",
                    checked = settings.rememberPosition,
                    onCheckedChange = viewModel::setRememberPosition
                )
            }

            item {
                val resumeLabel = when (settings.resumePreference) {
                    com.mhs.player.settings.SettingsRepository.ResumePreference.ALWAYS_RESUME -> "Always Resume"
                    com.mhs.player.settings.SettingsRepository.ResumePreference.ALWAYS_START_OVER -> "Always Start Over"
                    else -> "Ask every time"
                }
                SettingsActionItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Resume Preference",
                    subtitle = resumeLabel,
                    actionLabel = if (settings.resumePreference != com.mhs.player.settings.SettingsRepository.ResumePreference.ASK) "Reset" else null,
                    onAction = { viewModel.resetResumePreference() }
                )
            }

            item {
                SettingsChipSelector(
                    icon = Icons.Default.ScreenRotation,
                    title = "Screen Orientation",
                    subtitle = "Default orientation for video playback",
                    options = com.mhs.player.settings.SettingsRepository.OrientationMode.entries.toList(),
                    selectedValue = settings.orientationMode,
                    onSelect = { viewModel.setOrientationMode(it) },
                    labelFor = { mode ->
                        mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.PictureInPicture,
                    title = "Auto PiP on Home",
                    subtitle = "Enter PiP when pressing home button",
                    checked = settings.pipOnHome,
                    onCheckedChange = viewModel::setPipOnHome
                )
            }

            // ── GESTURES ────────────────────────────────────────────────
            item { SettingsSectionHeader("Gestures", Icons.Default.TouchApp) }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.TouchApp,
                    title = "Double Tap Seek",
                    subtitle = "Double tap sides to seek forward/backward",
                    checked = settings.doubleTapSeek,
                    onCheckedChange = viewModel::setDoubleTapSeek
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Brightness6,
                    title = "Brightness Gesture",
                    subtitle = "Swipe left side to adjust brightness",
                    checked = settings.brightnessGesture,
                    onCheckedChange = viewModel::setBrightnessGesture
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.VolumeUp,
                    title = "Volume Gesture",
                    subtitle = "Swipe right side to adjust volume",
                    checked = settings.volumeGesture,
                    onCheckedChange = viewModel::setVolumeGesture
                )
            }

            item {
                SettingsSliderItem(
                    icon = Icons.Default.SwipeRight,
                    title = "Seek Speed",
                    subtitle = "Swipe left/right: ${(settings.seekSensitivity * 100).toInt()}% speed",
                    value = settings.seekSensitivity,
                    valueRange = 0.3f..3.0f,
                    steps = 8,
                    onValueChange = viewModel::setSeekSensitivity
                )
            }

            item {
                SettingsSliderItem(
                    icon = Icons.Default.Speed,
                    title = "Swipe Speed",
                    subtitle = "Volume/Brightness: ${(settings.swipeSensitivity * 100).toInt()}% speed",
                    value = settings.swipeSensitivity,
                    valueRange = 0.3f..3.0f,
                    steps = 8,
                    onValueChange = viewModel::setSwipeSensitivity
                )
            }

            // ── SUBTITLES ───────────────────────────────────────────────
            item { SettingsSectionHeader("Subtitles", Icons.Default.Subtitles) }

            item {
                SettingsSliderItem(
                    icon = Icons.Default.TextFields,
                    title = "Subtitle Size",
                    subtitle = "${settings.subtitleSize.toInt()}sp",
                    value = settings.subtitleSize,
                    valueRange = 10f..28f,
                    steps = 8,
                    onValueChange = viewModel::setSubtitleSize
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.BackupTable,
                    title = "Subtitle Background",
                    subtitle = "Show background behind subtitles",
                    checked = settings.subtitleBackground,
                    onCheckedChange = viewModel::setSubtitleBackground
                )
            }

            item {
                SettingsChipSelector(
                    icon = Icons.Default.Language,
                    title = "Subtitle Search Language",
                    subtitle = "Default language for auto subtitle search",
                    options = listOf("en", "ar", "fr", "de", "es", "tr", "ru"),
                    selectedValue = settings.subtitleLanguage,
                    onSelect = { viewModel.setSubtitleLanguage(it) },
                    labelFor = { lang ->
                        mapOf("en" to "EN", "ar" to "AR", "fr" to "FR", "de" to "DE",
                            "es" to "ES", "tr" to "TR", "ru" to "RU")[lang] ?: lang.uppercase()
                    }
                )
            }

            item {
                var apiKeyExpanded by remember { mutableStateOf(false) }
                var apiKeyText by remember { mutableStateOf(settings.subtitleApiKey) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { apiKeyExpanded = !apiKeyExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Key, null, tint = OnSurfaceVariant, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenSubtitles API Key", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                if (settings.subtitleApiKey.isBlank()) "Optional — tap to set for higher rate limits"
                                else "••••••••${settings.subtitleApiKey.takeLast(4)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (apiKeyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = OnSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = apiKeyExpanded) {
                        Column {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { apiKeyText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Paste API key here…") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeAccent(),
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    viewModel.setSubtitleApiKey(apiKeyText)
                                    apiKeyExpanded = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("Save", color = themeAccent()) }
                        }
                    }
                }
            }


            // ── APPEARANCE ──────────────────────────────────────────────
            item { SettingsSectionHeader("Appearance", Icons.Default.Palette) }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Nightlight,
                    title = "Dark Mode",
                    subtitle = if (settings.darkMode) "AMOLED black theme" else "Light theme",
                    checked = settings.darkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
            }

            item {
                SettingsSliderItem(
                    icon = Icons.Default.HighQuality,
                    title = "Thumbnail Quality",
                    subtitle = "Level ${settings.thumbnailQuality} — ${listOf("Low", "Medium", "High", "Ultra")[settings.thumbnailQuality.coerceIn(0, 3)]}",
                    value = settings.thumbnailQuality.toFloat(),
                    valueRange = 0f..3f,
                    steps = 2,
                    onValueChange = { viewModel.setThumbnailQuality(it.toInt()) }
                )
            }

            // ── ABOUT ───────────────────────────────────────────────────
            item { SettingsSectionHeader("About", Icons.Default.Info) }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "MHS Player",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} — Premium Media Player"
                )
            }

            item {
                val updateViewModel: UpdateViewModel = hiltViewModel()
                UpdateCard(
                    viewModel = updateViewModel,
                    onNavigateToUpdateScreen = { navController.navigate(Screen.Update.route) }
                )
            }
        }
    }
}

// ─────────────────────────────────── COMPONENTS ───────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(3.dp, 18.dp)
                .background(themeAccentGradient(), RoundedCornerShape(2.dp))
        )
        Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = themeAccent()
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptics.tick(); onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (checked) themeAccent().copy(0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (checked) themeAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = themeOnAccent(),
                checkedTrackColor = themeAccent(),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun <T> SettingsChipSelector(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<T>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(themeAccent().copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(start = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = option == selectedValue
                val haptics = rememberHaptics()
                FilterChip(
                    selected = selected,
                    onClick = { haptics.tick(); onSelect(option) },
                    label = {
                        Text(
                            labelFor(option),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = themeAccent(),
                        selectedLabelColor = themeOnAccent(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(themeAccent().copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(start = 56.dp, top = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = themeAccent(),
                activeTrackColor = themeAccent(),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(themeAccent().copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptics.click(); onAction() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(themeAccent().copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, color = themeAccent(), style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Default.ChevronRight, null, tint = themeAccent(), modifier = Modifier.size(16.dp))
            }
        }
    }
}


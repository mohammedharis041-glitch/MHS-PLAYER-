package com.mhs.player.updater.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhs.player.BuildConfig
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.themeAccentGradient
import com.mhs.player.ui.theme.themeOnAccent
import com.mhs.player.updater.UpdateViewModel
import com.mhs.player.updater.download.DownloadState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val settings by viewModel.updaterSettings.collectAsStateWithLifecycle()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    val hasUpdate = updateResult?.hasUpdate == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F0E13),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "System Updates",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.click()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0E13),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Version Info Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(0.02f))
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(themeAccent().copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = themeAccent(),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "MHS Player",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Current Version: v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(0.6f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val lastCheckedText = if (settings.lastCheckedTimestamp > 0) {
                        val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        "Last checked: ${sdf.format(Date(settings.lastCheckedTimestamp))}"
                    } else {
                        "Never checked"
                    }
                    Text(
                        text = lastCheckedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.4f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            haptics.tick()
                            viewModel.checkForUpdates(isManual = true)
                        },
                        enabled = !isChecking && downloadState !is DownloadState.Downloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeAccent(),
                            contentColor = themeOnAccent(),
                            disabledContainerColor = themeAccent().copy(0.5f),
                            disabledContentColor = themeOnAccent().copy(0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = themeOnAccent(),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        } else {
                            Text("Check for Updates", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // In-place Update Status Panel (shows when update available)
            AnimatedVisibility(
                visible = hasUpdate,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                updateResult?.let { result ->
                    val release = result.latestRelease
                    if (release != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(themeAccent().copy(0.04f))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(themeAccent().copy(0.3f), themeAccent().copy(0.05f))
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NewReleases,
                                        contentDescription = null,
                                        tint = themeAccent(),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Update Available: v${release.tagName}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }

                                Divider(color = Color.White.copy(0.08f))

                                when (downloadState) {
                                    is DownloadState.Idle, is DownloadState.Paused, is DownloadState.Error -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "Changelog:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White.copy(0.7f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 120.dp)
                                                    .background(Color.White.copy(0.02f), RoundedCornerShape(8.dp))
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    text = result.changelog,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(0.8f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                "Size: ${if (result.apkSize > 0) formatSize(result.apkSize) else "Unknown"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(0.5f)
                                            )
                                            Text(
                                                "Published: ${release.publishedAt?.substringBefore("T") ?: "Recent"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(0.5f)
                                            )
                                        }

                                        if (downloadState is DownloadState.Error) {
                                            Text(
                                                text = (downloadState as DownloadState.Error).message,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                haptics.tick()
                                                viewModel.startDownload()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = themeAccent(),
                                                contentColor = themeOnAccent()
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Download & Install", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }

                                    is DownloadState.Downloading -> {
                                        val state = downloadState as DownloadState.Downloading
                                        val percentage = (state.progress * 100).toInt()
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Downloading...", color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodySmall)
                                                Text("$percentage%", color = themeAccent(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            }

                                            LinearProgressIndicator(
                                                progress = state.progress,
                                                color = themeAccent(),
                                                trackColor = Color.White.copy(0.08f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "${formatSize(state.bytesDownloaded)} / ${formatSize(state.totalBytes)}",
                                                    color = Color.White.copy(0.5f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    "Speed: ${formatSpeed(state.speedBps)}",
                                                    color = Color.White.copy(0.5f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    haptics.click()
                                                    viewModel.cancelDownload()
                                                },
                                                border = BorderStroke(1.dp, Color.Red.copy(0.3f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(0.8f)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Cancel Download", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }

                                    is DownloadState.Success -> {
                                        val state = downloadState as DownloadState.Success
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = "Download complete and ready to install!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(0.9f),
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = {
                                                    haptics.tick()
                                                    viewModel.installApk(state.filePath)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF4CAF50),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Install Update", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Preferences Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(0.02f))
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Update Preferences",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Auto-Check switch
                    PreferenceSwitchItem(
                        icon = Icons.Default.Autorenew,
                        title = "Auto-Check for Updates",
                        subtitle = "Regularly look for updates in the background",
                        checked = settings.autoCheck,
                        onCheckedChange = { viewModel.setAutoCheck(it) }
                    )

                    Divider(color = Color.White.copy(0.05f))

                    // Stable / Beta channel chip select
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
                                Icon(Icons.Default.Language, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Update Channel", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                Text("Receive stable releases or bleeding-edge beta builds", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.padding(start = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("stable", "beta").forEach { channel ->
                                val selected = settings.updateChannel == channel
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics.tick()
                                        viewModel.setUpdateChannel(channel)
                                        // Reset search results if channels switched
                                        viewModel.dismissDialog()
                                    },
                                    label = {
                                        Text(
                                            channel.uppercase(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeAccent(),
                                        selectedLabelColor = themeOnAccent(),
                                        containerColor = Color.White.copy(0.05f),
                                        labelColor = Color.White.copy(0.6f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchItem(
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
            .clickable {
                haptics.tick()
                onCheckedChange(!checked)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (checked) themeAccent().copy(0.15f)
                    else Color.White.copy(0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (checked) themeAccent() else Color.White.copy(0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = themeOnAccent(),
                checkedTrackColor = themeAccent(),
                uncheckedThumbColor = Color.White.copy(0.4f),
                uncheckedTrackColor = Color.White.copy(0.1f)
            )
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
}

private fun formatSpeed(bytesPerSec: Long): String {
    val kbps = bytesPerSec / 1024.0
    val mbps = kbps / 1024.0
    return if (mbps > 0.1) String.format("%.1f MB/s", mbps) else String.format("%.0f KB/s", kbps)
}

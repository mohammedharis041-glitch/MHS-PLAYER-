package com.mhs.player.updater.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mhs.player.updater.UpdateChecker
import com.mhs.player.updater.download.DownloadState
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.themeAccentGradient
import com.mhs.player.ui.theme.themeOnAccent

@Composable
fun UpdateDialog(
    updateResult: UpdateChecker.UpdateResult,
    downloadState: DownloadState,
    onUpdateClick: () -> Unit,
    onLaterClick: () -> Unit,
    onSkipClick: () -> Unit,
    onInstallClick: (String) -> Unit,
    onCancelClick: () -> Unit
) {
    val isForced = updateResult.isForced
    val latestRelease = updateResult.latestRelease ?: return

    Dialog(
        onDismissRequest = { if (!isForced && downloadState !is DownloadState.Downloading) onLaterClick() },
        properties = DialogProperties(
            dismissOnBackPress = !isForced,
            dismissOnClickOutside = !isForced
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0E13).copy(alpha = 0.92f))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(0.15f), Color.White.copy(0.02f))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Icon with Accent Glow
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
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

                // Title & Version
                Text(
                    text = if (isForced) "Critical Update Required" else "New Update Available!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "v${latestRelease.tagName}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = themeAccent()
                    )
                    if (latestRelease.prerelease) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF9800).copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "BETA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                            )
                        }
                    }
                    if (isForced) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE53935).copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "MANDATORY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider
                Divider(color = Color.White.copy(0.08f), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic body depending on Download State
                when (downloadState) {
                    is DownloadState.Idle, is DownloadState.Paused, is DownloadState.Error -> {
                        // Changelog scroll area
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .background(Color.White.copy(0.03f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "What's New:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateResult.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.85f),
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // File Size & Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "File Size: ${if (updateResult.apkSize > 0) formatSize(updateResult.apkSize) else "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.6f)
                            )
                            Text(
                                text = "Published: ${formatDate(latestRelease.publishedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (downloadState is DownloadState.Error) {
                            Text(
                                text = "Error: ${downloadState.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!isForced) {
                                OutlinedButton(
                                    onClick = onLaterClick,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(0.12f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Later", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Button(
                                onClick = onUpdateClick,
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeAccent(),
                                    contentColor = themeOnAccent()
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Update Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        if (!isForced) {
                            TextButton(
                                onClick = onSkipClick,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    "Skip This Version",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.4f)
                                )
                            }
                        }
                    }

                    is DownloadState.Downloading -> {
                        // Progress state display
                        val progressPercentage = (downloadState.progress * 100).toInt()
                        val sizeText = "${formatSize(downloadState.bytesDownloaded)} / ${formatSize(downloadState.totalBytes)}"
                        val speedText = formatSpeed(downloadState.speedBps)
                        val etaText = formatEta(downloadState.etaSeconds)

                        Text(
                            text = "Downloading...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(0.9f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Glow Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(downloadState.progress)
                                    .background(themeAccent())
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Download Specs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sizeText, color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                            Text("$progressPercentage%", color = themeAccent(), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speed: $speedText", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                            Text("ETA: $etaText", color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Cancel Download Action
                        OutlinedButton(
                            onClick = onCancelClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(0.8f)),
                            border = BorderStroke(1.dp, Color.Red.copy(0.2f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    is DownloadState.Success -> {
                        // Completed State
                        Text(
                            text = "Download Complete!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(0.9f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { onInstallClick(downloadState.filePath) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeAccent(),
                                contentColor = themeOnAccent()
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Install Now", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
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

private fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "Calculating"
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
}

private fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank() || dateStr == "N/A") return "Recent"
    // Basic date clean up (e.g. 2026-05-21T03:39:21Z -> 2026-05-21)
    return dateStr.substringBefore("T")
}

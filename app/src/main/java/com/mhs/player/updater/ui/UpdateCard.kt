package com.mhs.player.updater.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.themeOnAccent
import com.mhs.player.updater.UpdateViewModel
import com.mhs.player.updater.download.DownloadState

@Composable
fun UpdateCard(
    viewModel: UpdateViewModel,
    onNavigateToUpdateScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val settings by viewModel.updaterSettings.collectAsStateWithLifecycle()

    val hasUpdate = updateResult?.hasUpdate == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptics.click()
                onNavigateToUpdateScreen()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141318).copy(alpha = 0.6f)
        ),
        border = if (hasUpdate) {
            CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(themeAccent().copy(0.4f), themeAccent().copy(0.1f))
                )
            )
        } else {
            CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(Color.White.copy(0.05f))
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon section with dynamic status glow
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasUpdate) themeAccent().copy(0.15f)
                        else Color.White.copy(0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasUpdate) Icons.Default.NewReleases else Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = if (hasUpdate) themeAccent() else Color.White.copy(0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Update",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = Triple(hasUpdate, isChecking, downloadState),
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "update_card_status"
                ) { (hasUp, checking, download) ->
                    val statusText = when {
                        checking -> "Checking for updates..."
                        download is DownloadState.Downloading -> "Downloading update (${(download.progress * 100).toInt()}%)..."
                        download is DownloadState.Success -> "Update ready to install"
                        hasUp -> "New update v${updateResult?.latestRelease?.tagName} available!"
                        else -> "Software is up to date"
                    }
                    val textColor = when {
                        hasUp || download is DownloadState.Success -> themeAccent()
                        else -> Color.White.copy(0.5f)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }

            // Arrow button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go to updates",
                    tint = Color.White.copy(0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

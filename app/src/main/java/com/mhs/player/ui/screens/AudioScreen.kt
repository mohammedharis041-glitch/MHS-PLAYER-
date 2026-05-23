package com.mhs.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.navigation.Screen
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.rememberSmoothFlingBehavior
import com.mhs.player.ui.theme.themeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    navController: NavController,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<MediaItemModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeAccent())
                }
            } else if (uiState.filteredAudios.isEmpty()) {
                EmptyState(message = "No audio files found")
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    flingBehavior = rememberSmoothFlingBehavior()
                ) {
                    item {
                        Text(
                            "${uiState.filteredAudios.size} tracks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    itemsIndexed(
                        uiState.filteredAudios,
                        key = { _, audio -> audio.id },
                        contentType = { _, _ -> "audio_item" }
                    ) { idx, audio ->
                        AudioListItem(
                            item = audio,
                            onClick = {
                                navController.navigate(Screen.AudioPlayer.createRoute(audio.id, idx))
                            },
                            onLongClick = { selectedItem = audio },
                            onMoreClick = { selectedItem = audio },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    // File operations bottom sheet
    selectedItem?.let { item ->
        FileOperationsSheet(
            item = item,
            onDismiss = { selectedItem = null },
            onActionDone = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
                viewModel.refresh()
            },
            onOpenFolder = { path ->
                navController.navigate(Screen.FolderContent.createRoute(path))
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AudioListItem(
    item: MediaItemModel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glassCard(cornerRadius = 16.dp, fillAlpha = 0.08f, borderAlpha = 0.2f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .accentGlow(color = AccentViolet, radius = 8.dp, offsetY = 2.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current

                // Determine the best art data source:
                // 1. MediaStore album art URI (fast, cached by MediaStore)
                // 2. AudioArtKey → embedded art via MediaMetadataRetriever (fallback)
                // 3. null → show placeholder icon
                val artData: Any? = when {
                    item.albumArtUri != null -> item.albumArtUri
                    else -> com.mhs.player.media.scanner.AudioArtFetcher.AudioArtKey(item.uri)
                }

                val request = remember(artData) {
                    ImageRequest.Builder(context)
                        .data(artData)
                        .allowHardware(false)
                        .crossfade(true)
                        .build()
                }

                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (painter.state) {
                        is coil.compose.AsyncImagePainter.State.Success ->
                            SubcomposeAsyncImageContent()
                        else -> Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.album,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.formattedDuration,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            // Three-dot button
            IconButton(
                onClick = { onMoreClick?.invoke() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun debugPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.graphics.vector.rememberVectorPainter(icon)
}

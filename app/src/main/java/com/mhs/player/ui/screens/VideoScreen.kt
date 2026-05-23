package com.mhs.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.SortOrder
import com.mhs.player.navigation.Screen
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.rememberSmoothFlingBehavior
import com.mhs.player.ui.theme.themeAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    navController: NavController,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle(emptyList())
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<MediaItemModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Videos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.displayName(),
                                        color = if (uiState.sortOrder == order) themeAccent()
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
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

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VideoFilter.entries.toTypedArray()) { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.setVideoFilter(filter) },
                        label = { Text(filter.displayName(), style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = themeAccent().copy(alpha = 0.2f),
                            selectedLabelColor = themeAccent(),
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.selectedFilter == filter,
                            selectedBorderColor = themeAccent().copy(alpha = 0.4f),
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeAccent())
                }
            } else if (uiState.filteredVideos.isEmpty()) {
                EmptyState(
                    message = if (uiState.searchQuery.isNotBlank()) "No results found"
                    else "No videos found"
                )
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    flingBehavior = rememberSmoothFlingBehavior()
                ) {
                    item {
                        Text(
                            "${uiState.filteredVideos.size} videos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    itemsIndexed(
                        uiState.filteredVideos,
                        key = { _, video -> video.id },
                        contentType = { _, _ -> "video_item" }
                    ) { idx, video ->
                        val isNew = recentHistory.none { it.mediaId == video.id }
                        VideoListItem(
                            item = video,
                            isNew = isNew,
                            onClick = {
                                navController.navigate(Screen.Player.createRoute(video.id, idx))
                            },
                            onLongClick = { selectedItem = video },
                            onMoreClick = { selectedItem = video },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    item: MediaItemModel,
    isNew: Boolean = false,
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 70.dp)
                    .accentGlow(color = AccentCyan, radius = 8.dp, offsetY = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val context = LocalContext.current
                val request = remember(item.uri) {
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(240, 135)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = debugPlaceholder(Icons.Default.VideoLibrary)
                )
                
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .glassButton(cornerRadius = 4.dp, fillAlpha = 0.8f)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Color(0xFFFF5722), // Deep Orange
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            fontSize = 9.sp
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .glassButton(cornerRadius = 4.dp, fillAlpha = 0.4f)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.resolution.isNotBlank()) {
                        ResolutionBadge(item.resolution)
                    }
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
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
private fun ResolutionBadge(resolution: String) {
    val label = when {
        resolution.contains("3840") || resolution.contains("2160") -> "4K"
        resolution.contains("1920") || resolution.contains("1080") -> "FHD"
        resolution.contains("1280") || resolution.contains("720") -> "HD"
        else -> resolution
    }
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = themeAccent().copy(alpha = 0.2f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = themeAccent(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = themeAccent(),
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.VideoLibrary,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun debugPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.graphics.vector.rememberVectorPainter(icon)
}

private fun SortOrder.displayName(): String = when (this) {
    SortOrder.NAME_ASC -> "Name (A-Z)"
    SortOrder.NAME_DESC -> "Name (Z-A)"
    SortOrder.DATE_ASC -> "Date (Oldest)"
    SortOrder.DATE_DESC -> "Date (Newest)"
    SortOrder.DURATION_ASC -> "Duration (Shortest)"
    SortOrder.DURATION_DESC -> "Duration (Longest)"
    SortOrder.SIZE_ASC -> "Size (Smallest)"
    SortOrder.SIZE_DESC -> "Size (Largest)"
}

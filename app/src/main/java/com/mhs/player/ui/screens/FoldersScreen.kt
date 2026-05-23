package com.mhs.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mhs.player.media.model.FolderModel
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.navigation.Screen
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.designsystem.rememberSmoothFlingBehavior
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    navController: NavController,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = themeAccent())
            }
        } else if (uiState.folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(message = "No folders found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(vertical = 8.dp),
                flingBehavior = rememberSmoothFlingBehavior()
            ) {
            item {
                Text(
                    "${uiState.folders.size} folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
                items(uiState.folders, key = { it.path }) { folder ->
                    FolderListItem(
                        folder = folder,
                        onClick = {
                            navController.navigate(Screen.FolderContent.createRoute(folder.path))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderListItem(
    folder: FolderModel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glassCard(cornerRadius = 16.dp, fillAlpha = 0.08f, borderAlpha = 0.2f)
            .clickable(onClick = onClick)
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
                    .accentGlow(color = AccentCyan, radius = 8.dp, offsetY = 2.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (folder.thumbnailUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(folder.thumbnailUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.itemCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentScreen(
    folderPath: String,
    navController: NavController,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Derive items reactively — updates once media finishes loading
    val items by remember(folderPath) {
        viewModel.uiState.map { state ->
            (state.videos + state.audios).filter { it.folderPath == folderPath }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val folderName = folderPath.substringAfterLast("/")

    var selectedItem by remember { mutableStateOf<MediaItemModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(folderName) },
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
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeAccent())
                }
            }
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(message = "No media in this folder")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    flingBehavior = rememberSmoothFlingBehavior()
                ) {
                    item {
                        Text(
                            "${items.size} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        if (item.isVideo) {
                            VideoListItem(
                                item = item,
                                onClick = {
                                    val idx = items.indexOf(item)
                                    navController.navigate(Screen.Player.createRoute(item.id, idx))
                                },
                                onLongClick = { selectedItem = item },
                                onMoreClick = { selectedItem = item }
                            )
                        } else {
                            AudioListItem(
                                item = item,
                                onClick = {
                                    val idx = items.indexOf(item)
                                    navController.navigate(Screen.AudioPlayer.createRoute(item.id, idx))
                                },
                                onLongClick = { selectedItem = item },
                                onMoreClick = { selectedItem = item }
                            )
                        }
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
            onOpenFolder = null // Already in the folder
        )
    }
}


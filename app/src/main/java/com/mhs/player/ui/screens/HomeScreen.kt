package com.mhs.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mhs.player.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.draw.scale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.*
import android.Manifest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import com.mhs.player.database.PlaybackHistory
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.navigation.Screen
import com.mhs.player.ui.components.MediaCardShimmer
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.themeAccent
import com.mhs.player.ui.theme.themeOnAccent
import com.mhs.player.ui.theme.themeOverlayScrim
import com.mhs.player.ui.theme.isDarkTheme
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import com.mhs.player.ui.theme.designsystem.rememberSmoothFlingBehavior
import com.mhs.player.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MediaViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.settings.collectAsStateWithLifecycle()

    // Permission handling: Requests granular media permissions + partial access + notifications
    val storagePermissionState = rememberMultiplePermissionsState(
        permissions = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Legacy
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Always request notification permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )

    // Proceed if ANY relevant media permission is granted (supports partial/granular access)
    val hasAnyMediaAccess = remember(storagePermissionState.permissions) {
        derivedStateOf {
            storagePermissionState.permissions.any { permState ->
                permState.status == PermissionStatus.Granted && 
                permState.permission != Manifest.permission.POST_NOTIFICATIONS
            }
        }
    }.value

    LaunchedEffect(hasAnyMediaAccess) {
        if (hasAnyMediaAccess) {
            viewModel.onPermissionGranted()
            // Prompt for notification permission on 13+ if not already granted
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val notifPerm = storagePermissionState.permissions.find { it.permission == Manifest.permission.POST_NOTIFICATIONS }
                if (notifPerm?.status is PermissionStatus.Denied) {
                    storagePermissionState.launchMultiplePermissionRequest()
                }
            }
        }
    }

    // Auto-request permissions on first load if none are granted yet
    LaunchedEffect(Unit) {
        if (!hasAnyMediaAccess) {
            storagePermissionState.launchMultiplePermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            flingBehavior = rememberSmoothFlingBehavior()
        ) {
            item {
                HomeHeader(
                    onSearchClick = { navController.navigate(Screen.Video.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }

            if (!uiState.hasPermission) {
                item {
                    PermissionBanner(
                        onClick = { storagePermissionState.launchMultiplePermissionRequest() }
                    )
                }
            }

            if (uiState.isLoading && recentHistory.isEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(5) { MediaCardShimmer() }
                    }
                }
            }

            if (recentHistory.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.continue_watching_label),
                        icon = Icons.Default.PlayArrow,
                        onSeeAll = { navController.navigate(Screen.History.route) }
                    )
                }
                item {
                    ContinueWatchingRow(
                        items = recentHistory,
                        onItemClick = { history ->
                            navController.navigate(Screen.Player.createRoute(history.mediaId, 0))
                        }
                    )
                }
            }

            if (uiState.filteredVideos.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.recent_videos),
                        icon = Icons.Default.VideoLibrary,
                        onSeeAll = { navController.navigate(Screen.Video.route) }
                    )
                }
                item {
                    HorizontalMediaRow(
                        items = uiState.filteredVideos.take(10),
                        history = recentHistory,
                        onItemClick = { item ->
                            val idx = uiState.filteredVideos.indexOf(item)
                            navController.navigate(Screen.Player.createRoute(item.id, idx))
                        }
                    )
                }
            }

            if (uiState.filteredAudios.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.recent_audio),
                        icon = Icons.Default.MusicNote,
                        onSeeAll = { navController.navigate(Screen.Audio.route) }
                    )
                }
                item {
                    HorizontalMediaRow(
                        items = uiState.filteredAudios.take(10),
                        history = recentHistory,
                        onItemClick = { item ->
                            val idx = uiState.filteredAudios.indexOf(item)
                            navController.navigate(Screen.AudioPlayer.createRoute(item.id, idx))
                        }
                    )
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Favorites",
                        icon = Icons.Default.Favorite,
                        onSeeAll = { navController.navigate(Screen.Favorites.route) }
                    )
                }
                item {
                    FavoritesRow(
                        items = favorites.take(10),
                        onItemClick = { fav ->
                            navController.navigate(Screen.Player.createRoute(fav.mediaId, 0))
                        }
                    )
                }
            }

            if (uiState.folders.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Folders",
                        icon = Icons.Default.Folder,
                        onSeeAll = { navController.navigate(Screen.Folders.route) }
                    )
                }
                item {
                    FoldersRow(
                        folders = uiState.folders.take(8),
                        onFolderClick = { folder ->
                            navController.navigate(Screen.FolderContent.createRoute(folder.path))
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Bottom navigation bar
        BottomNavBar(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (settingsState.showOnboarding) {
            OnboardingDialog(
                onDismiss = {
                    settingsViewModel.setShowOnboarding(false)
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gradientStart = if (isDark) Color(0xFF0A0A2E) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val gradientEnd = if (isDark) Color(0xFF161640) else MaterialTheme.colorScheme.surface
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradientStart, gradientEnd)
                )
            )
            .padding(top = 40.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = themeAccent()
                )
                Text(
                    text = stringResource(R.string.premium_experience),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = themeAccent(), modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        TextButton(onClick = onSeeAll) {
            Text(stringResource(R.string.see_all), color = themeAccent(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<PlaybackHistory>,
    onItemClick: (PlaybackHistory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { history ->
            ContinueWatchingCard(
                history = history,
                onClick = { onItemClick(history) }
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    history: PlaybackHistory,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .accentGlow(color = themeAccent(), radius = 12.dp, offsetY = 6.dp)
            .glassCard(cornerRadius = 16.dp, fillAlpha = 0.15f, borderAlpha = 0.35f)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val context = LocalContext.current
                val request = remember(history.uri) {
                    ImageRequest.Builder(context)
                        .data(history.uri)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = debugPlaceholder(Icons.Default.PlayArrow)
                )
                // Progress bar
                val progress = if (history.duration > 0)
                    (history.lastPosition.toFloat() / history.duration.toFloat()) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart),
                    color = themeAccent(),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                // Play icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .glassButton(cornerRadius = 18.dp, fillAlpha = 0.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = history.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HorizontalMediaRow(
    items: List<MediaItemModel>,
    history: List<PlaybackHistory>,
    onItemClick: (MediaItemModel) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val isNew = item.isVideo && history.none { it.mediaId == item.id }
            MediaThumbnailCard(
                item = item,
                isNew = isNew,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
private fun MediaThumbnailCard(
    item: MediaItemModel,
    isNew: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .accentGlow(color = AppColors.AccentViolet, radius = 10.dp, offsetY = 4.dp)
            .glassCard(cornerRadius = 12.dp, fillAlpha = 0.12f, borderAlpha = 0.30f)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                val context = LocalContext.current
                val thumbnailData = remember(item) { if (item.isAudio) item.albumArtUri else item.uri }
                val request = remember(thumbnailData) {
                    ImageRequest.Builder(context)
                        .data(thumbnailData)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = debugPlaceholder(if (item.isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary)
                )
                
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .glassButton(cornerRadius = 6.dp, fillAlpha = 0.8f)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Color(0xFFFF5722), // Deep Orange to stand out
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            fontSize = 9.sp
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .glassButton(cornerRadius = 6.dp, fillAlpha = 0.3f)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White // Keep white on dark thumbnail overlay
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FavoritesRow(
    items: List<com.mhs.player.database.FavoriteItem>,
    onItemClick: (com.mhs.player.database.FavoriteItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { fav ->
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .accentGlow(color = AppColors.AccentPink, radius = 10.dp, offsetY = 4.dp)
                    .glassCard(cornerRadius = 12.dp, fillAlpha = 0.12f, borderAlpha = 0.30f)
                    .clickable { onItemClick(fav) }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        val context = LocalContext.current
                        val request = remember(fav.uri) {
                            ImageRequest.Builder(context)
                                .data(fav.uri)
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = debugPlaceholder(Icons.Default.Favorite)
                        )
                        Icon(
                            Icons.Default.Favorite,
                            null,
                            tint = AppColors.AccentPink,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = fav.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersRow(
    folders: List<com.mhs.player.media.model.FolderModel>,
    onFolderClick: (com.mhs.player.media.model.FolderModel) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .accentGlow(color = themeAccent(), radius = 8.dp, offsetY = 2.dp)
                    .glassCard(cornerRadius = 12.dp, fillAlpha = 0.08f, borderAlpha = 0.25f)
                    .clickable { onFolderClick(folder) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        tint = themeAccent(),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${folder.itemCount} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = themeAccent().copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = themeAccent())
            Column {
                Text(
                    stringResource(R.string.storage_permission_needed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    stringResource(R.string.grant_permission_browse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun debugPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.graphics.vector.rememberVectorPainter(icon)
}

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    // Observe back stack as State so recomposition fires on every navigation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val haptics = rememberHaptics()

    data class NavTab(
        val route: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val label: String
    )

    val tabs = remember {
        listOf(
            NavTab(Screen.Home.route,    Icons.Default.Home,         "Home"),
            NavTab(Screen.Video.route,   Icons.Default.VideoLibrary, "Video"),
            NavTab(Screen.Audio.route,   Icons.Default.AudioFile,    "Audio"),
            NavTab(Screen.Folders.route, Icons.Default.Folder,       "Folders")
        )
    }

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .fillMaxWidth()
            .glassCard(cornerRadius = 24.dp, fillAlpha = 0.15f, borderAlpha = 0.35f)
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0)
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route

                // Spring-driven icon scale: pops up when selected
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1.20f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "nav_icon_scale_${tab.label}"
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            tab.icon,
                            tab.label,
                            modifier = Modifier.scale(iconScale)
                        )
                    },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            haptics.click()
                            navController.navigate(tab.route) {
                                // Always pop back to Home so we never stack up multiple copies
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = themeAccent(),
                        selectedTextColor = themeAccent(),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = themeAccent().copy(alpha = if (isDarkTheme()) 0.12f else 0.18f)
                    )
                )
            }
        }
    }
}


@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit
) {
    val darkTheme = isDarkTheme()
    val accent = themeAccent()
    val onAccent = themeOnAccent()
    var currentStep by remember { mutableIntStateOf(0) }
    
    val steps = listOf(
        OnboardingStep(
            title = "Welcome to MHS Player",
            description = "Experience the ultimate, state-of-the-art cinematic video player with fully customized hardware decoding, gesture controls, and gorgeous glassmorphic themes.",
            icon = Icons.Default.PlayArrow
        ),
        OnboardingStep(
            title = "Intelligent Touch Gestures",
            description = "Control everything with intuitive swipes:\n\n• Swipe Left Side: Brightness controls\n• Swipe Right Side: Sound volume controls\n• Swipe Center: Fast seek back & forward\n• Double-Tap Center: Direct Play / Pause toggle",
            icon = Icons.Default.TouchApp
        ),
        OnboardingStep(
            title = "Offline Subtitle Translations",
            description = "Auto-parse local subtitle files or query the web in real-time. Sync delay offsets or translate any subtitle instantly using Google ML Kit's highly advanced offline engine.",
            icon = Icons.Default.Translate
        ),
        OnboardingStep(
            title = "Premium Sound Equalizer",
            description = "Calibrate and boost your audio using our built-in 5-band Equalizer and Deep Bass Boost, custom tuned for headphones or external speaker systems.",
            icon = Icons.Default.Equalizer
        )
    )
    
    val step = steps[currentStep]
    
    Dialog(
        onDismissRequest = onDismiss, // Guarantee state is saved if dismissed via back press
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeOverlayScrim()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.92f)
                    .glassCard(
                        cornerRadius = 28.dp,
                        fillAlpha = if (darkTheme) 0.90f else 0.98f,
                        borderAlpha = if (darkTheme) 0.25f else 0.12f,
                        useDarkBg = darkTheme
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Progress Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    steps.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (index == currentStep) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentStep) accent
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                                .animateContentSize(tween(300))
                        )
                    }
                }
                
                // Icon Display
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .glassCard(cornerRadius = 45.dp, fillAlpha = 0.08f, borderAlpha = 0.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(42.dp)
                    )
                }
                
                // Title
                Text(
                    text = step.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                
                // Description
                Text(
                    text = step.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 120.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (currentStep < steps.lastIndex) {
                                currentStep++
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = onAccent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = if (currentStep == steps.lastIndex) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector
)

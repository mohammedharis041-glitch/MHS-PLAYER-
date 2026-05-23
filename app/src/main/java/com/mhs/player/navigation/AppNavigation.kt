package com.mhs.player.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.mhs.player.player.controls.GlobalMiniPlayer
import com.mhs.player.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Video : Screen("video")
    object Audio : Screen("audio")
    object Folders : Screen("folders")
    object History : Screen("history")
    object Favorites : Screen("favorites")
    object Settings : Screen("settings")
    object Update : Screen("update")
    object Player : Screen("player/{mediaId}/{queueIndex}") {
        fun createRoute(mediaId: Long, queueIndex: Int) = "player/$mediaId/$queueIndex"
    }
    object AudioPlayer : Screen("audio_player/{mediaId}/{queueIndex}") {
        fun createRoute(mediaId: Long, queueIndex: Int) = "audio_player/$mediaId/$queueIndex"
    }
    object FolderContent : Screen("folder/{encodedPath}") {
        fun createRoute(path: String) = "folder/${NavRouteEncoder.encode(path)}"
    }
    object ExternalPlayer : Screen("external_player/{encodedUri}") {
        fun createRoute(uri: String) = "external_player/${NavRouteEncoder.encode(uri)}"
    }
}

// Duration constants — tweak one place to change all transitions
private const val TAB_FADE_MS   = 200   // crossfade for tab switches (smooth, no slide jank)
private const val PUSH_SLIDE_MS = 320   // slide for push/pop screens (Settings, Player, etc.)
private const val POP_SLIDE_MS  = 260

@Composable
fun AppNavigation(
    externalUri: String? = null,
    onExternalUriConsumed: () -> Unit = {},
    onRequestExit: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    // Activity-scoped so mini player and player screens share one ViewModel.
    // Do not use navController.graph here — the graph is not set until NavHost runs.
    val activity = LocalContext.current as ComponentActivity
    val playerViewModel: PlayerViewModel = hiltViewModel(activity)

    BackHandler {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            onRequestExit()
        }
    }

    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            navController.navigate(Screen.ExternalPlayer.createRoute(externalUri))
            onExternalUriConsumed()
        }
    }

    val tabs = listOf(Screen.Home.route, Screen.Video.route, Screen.Audio.route, Screen.Folders.route)




    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragCumulative = 0f
                var navigated = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragCumulative = 0f
                        navigated = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (navigated) return@detectHorizontalDragGestures
                        dragCumulative += dragAmount
                        val currentRoute = navController.currentDestination?.route ?: return@detectHorizontalDragGestures
                        val currentIndex = tabs.indexOf(currentRoute)
                        if (currentIndex == -1) return@detectHorizontalDragGestures

                        if (dragCumulative > 150f && currentIndex > 0) {
                            change.consume()
                            navigated = true
                            navController.navigate(tabs[currentIndex - 1]) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else if (dragCumulative < -150f && currentIndex < tabs.size - 1) {
                            change.consume()
                            navigated = true
                            navController.navigate(tabs[currentIndex + 1]) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = {
                val from = initialState.destination.route
                val to   = targetState.destination.route
                val fromIdx = tabs.indexOf(from)
                val toIdx   = tabs.indexOf(to)
                if (fromIdx != -1 && toIdx != -1) {
                    fadeIn(animationSpec = tween(TAB_FADE_MS, easing = FastOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffset = { (it * 0.15f).toInt() }
                    ) + fadeIn(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                }
            },
            exitTransition = {
                val from = initialState.destination.route
                val to   = targetState.destination.route
                val fromIdx = tabs.indexOf(from)
                val toIdx   = tabs.indexOf(to)
                if (fromIdx != -1 && toIdx != -1) {
                    fadeOut(animationSpec = tween(TAB_FADE_MS, easing = FastOutSlowInEasing))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffset = { (it * 0.08f).toInt() }
                    ) + fadeOut(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                }
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                    initialOffset = { (it * 0.08f).toInt() }
                ) + fadeIn(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                    targetOffset = { (it * 0.15f).toInt() }
                ) + fadeOut(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
            }
        ) {
            // ── Tab screens — no individual overrides, use NavHost crossfade ──
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(Screen.Video.route) {
                VideoScreen(navController = navController)
            }
            composable(Screen.Audio.route) {
                AudioScreen(navController = navController)
            }
            composable(Screen.Folders.route) {
                FoldersScreen(navController = navController)
            }

            // ── Stack screens — smooth slide push/pop ──────────────────────
            composable(Screen.History.route) {
                HistoryScreen(navController = navController)
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(Screen.Update.route) {
                val updateViewModel: com.mhs.player.updater.UpdateViewModel = hiltViewModel()
                com.mhs.player.updater.ui.UpdateScreen(
                    viewModel = updateViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Player screens — vertical slide ───────────────────────────
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.LongType },
                    navArgument("queueIndex") { type = NavType.IntType }
                ),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffset = { (it * 0.25f).toInt() }
                    ) + fadeIn(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffset = { (it * 0.25f).toInt() }
                    ) + fadeOut(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffset = { (it * 0.25f).toInt() }
                    ) + fadeIn(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffset = { (it * 0.25f).toInt() }
                    ) + fadeOut(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
                }
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                val queueIndex = backStackEntry.arguments?.getInt("queueIndex") ?: 0
                PlayerScreen(
                    mediaId = mediaId,
                    queueIndex = queueIndex,
                    navController = navController,
                    viewModel = playerViewModel
                )
            }
            composable(
                route = Screen.AudioPlayer.route,
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.LongType },
                    navArgument("queueIndex") { type = NavType.IntType }
                ),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffset = { (it * 0.25f).toInt() }
                    ) + fadeIn(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffset = { (it * 0.25f).toInt() }
                    ) + fadeOut(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing),
                        initialOffset = { (it * 0.25f).toInt() }
                    ) + fadeIn(animationSpec = tween(PUSH_SLIDE_MS, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing),
                        targetOffset = { (it * 0.25f).toInt() }
                    ) + fadeOut(animationSpec = tween(POP_SLIDE_MS, easing = FastOutSlowInEasing))
                }
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
                val queueIndex = backStackEntry.arguments?.getInt("queueIndex") ?: 0
                AudioPlayerScreen(
                    mediaId = mediaId,
                    queueIndex = queueIndex,
                    navController = navController,
                    viewModel = playerViewModel
                )
            }
            composable(
                route = Screen.FolderContent.route,
                arguments = listOf(
                    navArgument("encodedPath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: ""
                FolderContentScreen(
                    folderPath = NavRouteEncoder.decode(encodedPath),
                    navController = navController
                )
            }
            composable(
                route = Screen.ExternalPlayer.route,
                arguments = listOf(
                    navArgument("encodedUri") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("encodedUri") ?: ""
                ExternalPlayerScreen(
                    uriString = NavRouteEncoder.decode(encodedUri),
                    navController = navController,
                    viewModel = playerViewModel
                )
            }
        }

        // Global Mini Player overlay
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isPlayerScreen = currentRoute?.startsWith("player") == true ||
                           currentRoute?.startsWith("audio_player") == true ||
                           currentRoute?.startsWith("external_player") == true

        if (!isPlayerScreen) {
            GlobalMiniPlayer(
                viewModel = playerViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (currentRoute in listOf("home", "video", "audio", "folders")) 80.dp else 16.dp),
                onNavigateToPlayer = {
                    val currentMedia = playerViewModel.currentMedia.value
                    if (currentMedia != null) {
                        val route = if (currentMedia.isVideo) {
                            if (currentMedia.id == 0L || currentMedia.folderPath.isEmpty()) {
                                Screen.ExternalPlayer.createRoute(currentMedia.uri.toString())
                            } else {
                                Screen.Player.createRoute(currentMedia.id, playerViewModel.currentQueueIndex.value)
                            }
                        } else {
                            Screen.AudioPlayer.createRoute(currentMedia.id, playerViewModel.currentQueueIndex.value)
                        }
                        navController.navigate(route)
                    }
                }
            )
        }
    }
}

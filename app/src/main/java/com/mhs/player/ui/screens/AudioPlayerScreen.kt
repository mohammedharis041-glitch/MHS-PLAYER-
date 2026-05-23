package com.mhs.player.ui.screens

import androidx.navigation.NavController
import androidx.compose.runtime.Composable

@Composable
fun AudioPlayerScreen(
    mediaId: Long,
    queueIndex: Int,
    navController: NavController,
    viewModel: PlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    com.mhs.player.player.audio.AudioPlayerScreen(
        mediaId = mediaId,
        queueIndex = queueIndex,
        navController = navController,
        viewModel = viewModel
    )
}

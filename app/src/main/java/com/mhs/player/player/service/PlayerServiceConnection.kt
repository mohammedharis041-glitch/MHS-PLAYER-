package com.mhs.player.player.service

import androidx.media3.exoplayer.ExoPlayer

/**
 * Singleton bridge that lets [MhsPlaybackService] expose its [ExoPlayer]
 * instance to [com.mhs.player.player.controller.PlayerController].
 *
 * Flow:
 *  1. App starts → PlayerController.initPlayer() binds to MediaBrowserCompat / starts service
 *  2. Service.onCreate() creates its ExoPlayer and calls setPlayer()
 *  3. PlayerController reads the player via getPlayer() and uses it for all playback
 */
object PlayerServiceConnection {
    @Volatile private var instance: ExoPlayer? = null
    private val listeners = mutableListOf<(ExoPlayer?) -> Unit>()

    fun setPlayer(player: ExoPlayer) {
        instance = player
        listeners.forEach { it(player) }
    }

    fun clearPlayer() {
        instance = null
        listeners.forEach { it(null) }
    }

    fun getPlayer(): ExoPlayer? = instance

    fun addListener(listener: (ExoPlayer?) -> Unit) {
        listeners.add(listener)
        // Immediately fire if already available
        instance?.let { listener(it) }
    }

    fun removeListener(listener: (ExoPlayer?) -> Unit) {
        listeners.remove(listener)
    }
}

package com.mhs.player.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import android.content.pm.ServiceInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.AndroidEntryPoint
import com.mhs.player.MainActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mhs.player.R
import com.mhs.player.player.controller.PlayerController
import javax.inject.Inject
import android.util.Log

/**
 * Foreground service that powers background audio playback with a media notification.
 *
 * Architecture:
 * - [PlayerController] creates the ExoPlayer singleton and registers it via [PlayerServiceConnection].
 * - When audio playback starts, [PlayerController] calls startForegroundService() → this service
 *   starts and wraps the SAME ExoPlayer in a [MediaSession].
 * - Media3 automatically shows a notification with album art + Prev/Play-Pause/Next controls.
 * - The notification also drives the lock screen media controls.
 *
 * No separate ExoPlayer is created here — we reuse the one from PlayerController.
 */
@AndroidEntryPoint
class MhsPlaybackService : MediaSessionService() {

    @Inject lateinit var playerController: PlayerController
    private var mediaSession: MediaSession? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        val player = PlayerServiceConnection.getPlayer()
        if (player == null) {
            stopSelf()
            return
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
            
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())
    }



    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("MHSPlayer-Lifecycle", "onTaskRemoved: App swiped away. Stopping all playback and service.")
        playerController.stopAll()
        playerController.release()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("MHSPlayer-Lifecycle", "Service onDestroy: Cleaning up")
        mediaSession?.run {
            // Don't release the player — it's owned by PlayerController
            release()
            mediaSession = null
        }
        playerController.release()
        super.onDestroy()
    }
}

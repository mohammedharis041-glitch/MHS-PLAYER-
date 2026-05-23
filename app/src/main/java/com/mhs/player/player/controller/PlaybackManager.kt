package com.mhs.player.player.controller

import com.mhs.player.database.FavoriteItem
import com.mhs.player.database.FavoritesDao
import com.mhs.player.database.HistoryDao
import com.mhs.player.database.PlaybackHistory
import com.mhs.player.media.model.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    private val playerController: PlayerController,
    private val historyDao: HistoryDao,
    private val favoritesDao: FavoritesDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Progress save debounce ─────────────────────────────────────────────
    // Prevent OOM by ensuring we never write to Room faster than once every
    // SAVE_INTERVAL_MS milliseconds. Spawning a coroutine + DB transaction
    // on every ExoPlayer position tick exhausts the 256 MB heap limit.
    private val SAVE_INTERVAL_MS = 5_000L
    @Volatile private var lastSaveTimeMs = 0L

    fun openMedia(item: MediaItemModel, allItems: List<MediaItemModel>, startIndex: Int) {
        scope.launch {
            val savedPosition = try {
                historyDao.getLastPosition(item.id) ?: 0L
            } catch (e: Exception) { 0L }

            val history = try { historyDao.getHistoryForMedia(item.id) } catch (e: Exception) { null }
            val subtitleDelay = history?.subtitleDelay ?: 0L
            val subtitleSpeed = history?.subtitleSpeed ?: 1.0f
            val subtitlePath = history?.subtitlePath

            // ExoPlayer MUST be called on the main thread
            withContext<Unit>(Dispatchers.Main) {
                playerController.setSubtitleDelay(subtitleDelay)
                playerController.setSubtitleSpeed(subtitleSpeed)

                subtitlePath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        playerController.loadExternalSubtitle(file, showToast = false)
                    }
                }

                // Always start from 0 initially.
                // PlayerViewModel will handle jumping to the saved position for videos if needed.
                playerController.playQueue(allItems, startIndex, 0L)
            }

            recordHistory(item)
        }
    }

    /**
     * Saves the current playback position to the database.
     *
     * Debounced to at most once every [SAVE_INTERVAL_MS] milliseconds to
     * prevent OOM caused by spawning a new Room transaction on every player tick.
     *
     * @param force Set to true to bypass the interval (e.g. on playback stop/complete).
     */
    fun saveProgress(item: MediaItemModel, position: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveTimeMs < SAVE_INTERVAL_MS) return
        lastSaveTimeMs = now

        scope.launch {
            try {
                historyDao.updatePosition(item.id, position)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("PlaybackManager", "OOM saving progress — skipping", e)
            } catch (e: Exception) {
                android.util.Log.w("PlaybackManager", "Failed to save progress: ${e.message}")
            }
        }
    }

    fun saveSubtitleSync(item: MediaItemModel, delay: Long, speed: Float) {
        scope.launch {
            try {
                historyDao.updateSubtitleSync(item.id, delay, speed)
            } catch (e: Exception) {
                android.util.Log.w("PlaybackManager", "Failed to save subtitle sync: ${e.message}")
            }
        }
    }

    fun saveSubtitlePath(item: MediaItemModel, path: String?) {
        scope.launch {
            try {
                historyDao.updateSubtitlePath(item.id, path)
            } catch (e: Exception) {
                android.util.Log.w("PlaybackManager", "Failed to save subtitle path: ${e.message}")
            }
        }
    }

    private suspend fun recordHistory(item: MediaItemModel) {
        try {
            val existing = historyDao.getHistoryForMedia(item.id)
            if (existing != null) {
                historyDao.updatePosition(item.id, existing.lastPosition)
            } else {
                historyDao.insertHistory(
                    PlaybackHistory(
                        mediaId = item.id,
                        title = item.title,
                        path = item.path,
                        uri = item.uri.toString(),
                        duration = item.duration,
                        mediaType = item.mediaType.name
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackManager", "Failed to record history: ${e.message}")
        }
    }

    fun toggleFavorite(item: MediaItemModel) {
        scope.launch {
            try {
                val isFav = favoritesDao.isFavoriteOnce(item.id)
                if (isFav) {
                    favoritesDao.deleteFavorite(item.id)
                } else {
                    favoritesDao.insertFavorite(
                        FavoriteItem(
                            mediaId = item.id,
                            title = item.title,
                            path = item.path,
                            uri = item.uri.toString(),
                            duration = item.duration,
                            mediaType = item.mediaType.name
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("PlaybackManager", "Failed to toggle favorite: ${e.message}")
            }
        }
    }
}

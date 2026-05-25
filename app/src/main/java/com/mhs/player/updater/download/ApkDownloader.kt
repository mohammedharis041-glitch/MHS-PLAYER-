package com.mhs.player.updater.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val sharedPrefs = context.getSharedPreferences("mhs_updater_download", Context.MODE_PRIVATE)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private val downloadStateFlow = _downloadState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    // Speed calculation helpers
    private var lastDownloadedBytes = 0L
    private var lastTimeMs = 0L
    private var currentSpeedBps = 0L

    init {
        // Recover download status if there's a saved download ID
        val savedId = getSavedDownloadId()
        if (savedId != -1L) {
            startPolling(savedId)
        }
    }

    fun startDownload(url: String, fileName: String): Flow<DownloadState> {
        // Cancel any existing download first
        cancelDownload()

        val destinationFile = File(context.getExternalFilesDir(null), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        saveFileName(fileName)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("MHS Player Update")
            setDescription("Downloading latest update")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, null, fileName)
            addRequestHeader("User-Agent", "MHSPlayer-Updater")
        }

        val downloadId = downloadManager.enqueue(request)
        saveDownloadId(downloadId)

        // Reset speed calculators
        lastDownloadedBytes = 0L
        lastTimeMs = 0L
        currentSpeedBps = 0L

        startPolling(downloadId)

        return downloadStateFlow
    }

    fun pauseDownload() {
        cancelDownload()
    }

    fun cancelDownload() {
        val savedId = getSavedDownloadId()
        if (savedId != -1L) {
            downloadManager.remove(savedId)
            clearDownloadId()
        }
        pollingJob?.cancel()
        _downloadState.value = DownloadState.Idle
    }

    fun getDownloadStateFlow(): Flow<DownloadState> {
        return downloadStateFlow
    }

    private fun startPolling(downloadId: Long) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val state = queryDownloadStatus(downloadId)
                _downloadState.value = state
                
                if (state is DownloadState.Success || state is DownloadState.Error || state is DownloadState.Idle) {
                    if (state is DownloadState.Success || state is DownloadState.Error) {
                        clearDownloadId()
                    }
                    break
                }
                delay(500) // Poll every 500ms
            }
        }
    }

    private fun queryDownloadStatus(id: Long): DownloadState {
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)
        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return DownloadState.Idle
        }

        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
        val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        
        if (statusIdx == -1 || reasonIdx == -1 || downloadedIdx == -1 || totalIdx == -1) {
            cursor.close()
            return DownloadState.Idle
        }

        val status = cursor.getInt(statusIdx)
        val reason = cursor.getInt(reasonIdx)
        val downloaded = cursor.getLong(downloadedIdx)
        val total = cursor.getLong(totalIdx)
        cursor.close()

        when (status) {
            DownloadManager.STATUS_PENDING -> {
                return DownloadState.Downloading(
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    speedBps = 0L,
                    etaSeconds = 0L
                )
            }
            DownloadManager.STATUS_PAUSED -> {
                return DownloadState.Paused
            }
            DownloadManager.STATUS_RUNNING -> {
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                val speed = calculateSpeed(downloaded)
                val eta = calculateEta(downloaded, total)
                return DownloadState.Downloading(
                    progress = progress,
                    bytesDownloaded = downloaded,
                    totalBytes = total,
                    speedBps = speed,
                    etaSeconds = eta
                )
            }
            DownloadManager.STATUS_SUCCESSFUL -> {
                val fileName = getSavedFileName() ?: "MHSPlayer-update.apk"
                val file = File(context.getExternalFilesDir(null), fileName)
                return if (file.exists()) {
                    DownloadState.Success(file.absolutePath)
                } else {
                    DownloadState.Error("Downloaded file not found")
                }
            }
            DownloadManager.STATUS_FAILED -> {
                val errorMessage = getErrorMessage(reason)
                return DownloadState.Error(errorMessage)
            }
            else -> return DownloadState.Idle
        }
    }

    private fun calculateSpeed(downloaded: Long): Long {
        val now = System.currentTimeMillis()
        if (lastTimeMs == 0L) {
            lastTimeMs = now
            lastDownloadedBytes = downloaded
            return 0L
        }

        val timeDiff = now - lastTimeMs
        if (timeDiff >= 1000) {
            val bytesDiff = downloaded - lastDownloadedBytes
            currentSpeedBps = (bytesDiff * 1000) / timeDiff
            lastTimeMs = now
            lastDownloadedBytes = downloaded
        }
        return currentSpeedBps
    }

    private fun calculateEta(downloaded: Long, total: Long): Long {
        val speed = currentSpeedBps
        if (speed <= 0L || total <= 0L) return 0L
        val remainingBytes = total - downloaded
        return remainingBytes / speed
    }

    private fun getSavedDownloadId(): Long {
        return sharedPrefs.getLong("download_id", -1L)
    }

    private fun saveDownloadId(id: Long) {
        sharedPrefs.edit().putLong("download_id", id).apply()
    }

    private fun clearDownloadId() {
        sharedPrefs.edit().remove("download_id").apply()
    }

    private fun getSavedFileName(): String? {
        return sharedPrefs.getString("file_name", null)
    }

    private fun saveFileName(name: String) {
        sharedPrefs.edit().putString("file_name", name).apply()
    }

    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No external storage device found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage file error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Data transfer error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many HTTP redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown download error"
            else -> "Download failed (Code: $reason)"
        }
    }
}

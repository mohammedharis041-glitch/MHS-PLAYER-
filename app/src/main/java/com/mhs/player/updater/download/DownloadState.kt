package com.mhs.player.updater.download

sealed class DownloadState {
    object Idle : DownloadState()
    
    data class Downloading(
        val progress: Float,             // Range: 0.0f - 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBps: Long,              // Speed in bytes per second
        val etaSeconds: Long             // Estimated time remaining in seconds
    ) : DownloadState()
    
    data class Success(val filePath: String) : DownloadState()
    
    object Paused : DownloadState()
    
    data class Error(val message: String) : DownloadState()
}

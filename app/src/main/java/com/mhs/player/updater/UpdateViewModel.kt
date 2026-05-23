package com.mhs.player.updater

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhs.player.updater.data.UpdatePreferences
import com.mhs.player.updater.data.UpdateRepository
import com.mhs.player.updater.download.ApkDownloader
import com.mhs.player.updater.download.DownloadState
import com.mhs.player.updater.install.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
    private val repository: UpdateRepository,
    private val apkDownloader: ApkDownloader
) : ViewModel() {

    val updaterSettings = repository.preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UpdatePreferences.UpdaterSettings()
    )

    private val _updateResult = MutableStateFlow<UpdateChecker.UpdateResult?>(null)
    val updateResult: StateFlow<UpdateChecker.UpdateResult?> = _updateResult.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    val downloadState: StateFlow<DownloadState> = apkDownloader.getDownloadStateFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadState.Idle)

    fun checkForUpdates(isManual: Boolean) {
        viewModelScope.launch {
            if (_isChecking.value) return@launch
            _isChecking.value = true
            try {
                val result = updateChecker.checkForUpdates(isManual)
                if (result.hasUpdate) {
                    _updateResult.value = result
                } else {
                    _updateResult.value = null
                    if (isManual) {
                        Toast.makeText(context, "Your app is up to date!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isManual) {
                    Toast.makeText(context, "Failed to check for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun startDownload() {
        val result = _updateResult.value ?: return
        val url = result.apkUrl ?: return
        val tag = result.latestRelease?.tagName ?: "update"
        val fileName = "MHSPlayer-$tag.apk"
        
        viewModelScope.launch {
            apkDownloader.startDownload(url, fileName).collect()
        }
    }

    fun pauseDownload() {
        apkDownloader.pauseDownload()
    }

    fun cancelDownload() {
        apkDownloader.cancelDownload()
    }

    fun skipVersion() {
        val result = _updateResult.value ?: return
        val tag = result.latestRelease?.tagName ?: return
        viewModelScope.launch {
            repository.preferences.setSkippedVersion(tag)
            dismissDialog()
        }
    }

    fun dismissDialog() {
        _updateResult.value = null
    }

    fun installApk(filePath: String) {
        val file = File(filePath)
        ApkInstaller.installApk(context, file)
    }

    fun setUpdateChannel(channel: String) {
        viewModelScope.launch {
            repository.preferences.setUpdateChannel(channel)
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setAutoCheck(enabled)
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setAutoDownload(enabled)
        }
    }
}

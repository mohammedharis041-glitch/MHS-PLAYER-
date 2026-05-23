package com.mhs.player.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhs.player.database.FavoritesDao
import com.mhs.player.database.HistoryDao
import com.mhs.player.media.folders.FolderManager
import com.mhs.player.media.model.*
import com.mhs.player.media.scanner.MediaScanner
import com.mhs.player.media.search.SearchManager
import com.mhs.player.media.sorting.SortManager
import com.mhs.player.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class VideoFilter {
    ALL, FOUR_K, HD, LONG, SHORT;
    fun displayName() = when(this) {
        ALL -> "All"
        FOUR_K -> "4K"
        HD -> "HD"
        LONG -> "> 20m"
        SHORT -> "< 5m"
    }
}

data class MediaUiState(
    val videos: List<MediaItemModel> = emptyList(),
    val audios: List<MediaItemModel> = emptyList(),
    val folders: List<FolderModel> = emptyList(),
    val filteredVideos: List<MediaItemModel> = emptyList(),
    val filteredAudios: List<MediaItemModel> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val selectedFilter: VideoFilter = VideoFilter.ALL,
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner,
    private val sortManager: SortManager,
    private val folderManager: FolderManager,
    private val searchManager: SearchManager,
    private val historyDao: HistoryDao,
    private val favoritesDao: FavoritesDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    val recentHistory = historyDao.getRecentHistory(20).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val favorites = favoritesDao.getAllFavorites().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        checkPermissionAndLoad()
        observeSearch()
    }

    fun checkPermissionAndLoad() {
        val hasPermission = hasMediaPermission()
        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)
        if (hasPermission) loadMedia()
    }

    fun onPermissionGranted() {
        _uiState.value = _uiState.value.copy(hasPermission = true)
        loadMedia()
    }

    fun refresh() = loadMedia()

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val videos = mediaScanner.scanVideos()
                val audios = mediaScanner.scanAudio()
                val allMedia = videos + audios
                val folders = folderManager.buildFolders(allMedia)
                val sortOrder = _uiState.value.sortOrder
                val sortedVideos = sortManager.sort(videos, sortOrder)
                val sortedAudios = sortManager.sort(audios, sortOrder)
                _uiState.value = _uiState.value.copy(
                    videos = sortedVideos,
                    audios = sortedAudios,
                    filteredVideos = sortedVideos,
                    filteredAudios = sortedAudios,
                    folders = folders,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load media"
                )
            }
        }
    }

    fun updateSearch(query: String) {
        searchManager.updateQuery(query)
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        applyFiltersAndSort()
    }

    fun setVideoFilter(filter: VideoFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val videos = searchManager.search(state.videos, state.searchQuery)
        val filtered = when (state.selectedFilter) {
            VideoFilter.ALL -> videos
            VideoFilter.FOUR_K -> videos.filter { (it.width ?: 0) >= 3840 }
            VideoFilter.HD -> videos.filter { (it.width ?: 0) >= 1280 }
            VideoFilter.LONG -> videos.filter { it.duration >= 20 * 60 * 1000 }
            VideoFilter.SHORT -> videos.filter { it.duration <= 5 * 60 * 1000 }
        }
        _uiState.value = state.copy(
            filteredVideos = sortManager.sort(filtered, state.sortOrder),
            filteredAudios = sortManager.sort(searchManager.search(state.audios, state.searchQuery), state.sortOrder)
        )
    }

    fun getItemsInFolder(folderPath: String): List<MediaItemModel> {
        val all = _uiState.value.videos + _uiState.value.audios
        return folderManager.getItemsInFolder(all, folderPath)
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _uiState.map { it.searchQuery }.distinctUntilChanged().collect {
                applyFiltersAndSort()
            }
        }
    }

    /**
     * Checks if the app has the necessary media permissions.
     * Supports Android 13/14/15 granular and partial access.
     */
    private fun hasMediaPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+: Full access (Video or Audio) OR Partial access (Visual User Selected)
                val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                val hasPartial = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                
                hasVideo || hasAudio || hasPartial
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13: Granular Video or Audio access
                val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                
                hasVideo || hasAudio
            }
            else -> {
                // Android 12 and below: Legacy READ_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}

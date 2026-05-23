package com.mhs.player.media.search

import com.mhs.player.media.model.MediaItemModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchManager @Inject constructor() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class)
    val debouncedQuery: Flow<String> = _query
        .debounce(300)
        .distinctUntilChanged()

    fun updateQuery(q: String) {
        _query.value = q
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun search(items: List<MediaItemModel>, query: String): List<MediaItemModel> {
        if (query.isBlank()) return items
        val lower = query.lowercase().trim()
        return items.filter {
            it.title.lowercase().contains(lower) ||
            it.displayName.lowercase().contains(lower) ||
            it.folderName.lowercase().contains(lower) ||
            it.artist.lowercase().contains(lower) ||
            it.album.lowercase().contains(lower)
        }
    }

    fun getSuggestions(items: List<MediaItemModel>, query: String): List<String> {
        if (query.length < 2) return emptyList()
        val lower = query.lowercase().trim()
        val suggestions = mutableSetOf<String>()
        items.forEach { item ->
            if (item.title.lowercase().contains(lower)) suggestions.add(item.title)
            if (item.folderName.lowercase().contains(lower)) suggestions.add(item.folderName)
            if (item.artist.lowercase().contains(lower) && item.artist.isNotBlank())
                suggestions.add(item.artist)
        }
        return suggestions.take(5).toList()
    }
}

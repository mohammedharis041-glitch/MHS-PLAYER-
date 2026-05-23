package com.mhs.player.media.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchManager: SearchManager
) : ViewModel() {

    val query: StateFlow<String> = searchManager.query

    fun updateQuery(q: String) = searchManager.updateQuery(q)

    fun clearQuery() = searchManager.clearQuery()
}

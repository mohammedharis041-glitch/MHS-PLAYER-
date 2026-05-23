package com.mhs.player.player.controller

import com.mhs.player.media.model.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor() {

    private val _queue = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val queue: StateFlow<List<MediaItemModel>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var originalQueue: List<MediaItemModel> = emptyList()

    enum class RepeatMode { NONE, ONE, ALL }

    val currentItem: MediaItemModel?
        get() = _queue.value.getOrNull(_currentIndex.value)

    val hasNext: Boolean
        get() = when (_repeatMode.value) {
            RepeatMode.ALL -> true
            RepeatMode.ONE -> true
            RepeatMode.NONE -> _currentIndex.value < _queue.value.size - 1
        }

    val hasPrevious: Boolean
        get() = _currentIndex.value > 0

    fun setQueue(items: List<MediaItemModel>, startIndex: Int = 0) {
        if (items.isEmpty()) {
            _queue.value = emptyList()
            _currentIndex.value = 0
            return
        }
        originalQueue = items
        _queue.value = if (_shuffleMode.value) items.shuffled() else items
        val newIndex = if (_shuffleMode.value) {
            _queue.value.indexOfFirst { it.id == items.getOrNull(startIndex)?.id }.coerceAtLeast(0)
        } else {
            startIndex.coerceIn(0, items.size - 1)
        }
        _currentIndex.value = newIndex
    }

    fun moveToNext(): MediaItemModel? {
        val queue = _queue.value
        val nextIndex = when (_repeatMode.value) {
            RepeatMode.ONE -> _currentIndex.value
            RepeatMode.ALL -> (_currentIndex.value + 1) % queue.size
            RepeatMode.NONE -> {
                val next = _currentIndex.value + 1
                if (next < queue.size) next else return null
            }
        }
        _currentIndex.value = nextIndex
        return queue.getOrNull(nextIndex)
    }

    fun moveToPrevious(): MediaItemModel? {
        val queue = _queue.value
        val prevIndex = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = prevIndex
        return queue.getOrNull(prevIndex)
    }

    fun jumpTo(index: Int): MediaItemModel? {
        val queue = _queue.value
        if (index !in queue.indices) return null
        _currentIndex.value = index
        return queue[index]
    }

    fun toggleShuffle() {
        val enabled = !_shuffleMode.value
        _shuffleMode.value = enabled
        val currentItem = currentItem
        if (enabled) {
            val shuffled = originalQueue.shuffled().toMutableList()
            currentItem?.let {
                shuffled.remove(it)
                shuffled.add(0, it)
            }
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            _queue.value = originalQueue
            _currentIndex.value = originalQueue.indexOfFirst { it.id == currentItem?.id }
                .coerceAtLeast(0)
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    fun addToQueue(item: MediaItemModel) {
        val newQueue = _queue.value.toMutableList()
        newQueue.add(item)
        _queue.value = newQueue
        originalQueue = newQueue
    }

    fun removeFromQueue(index: Int) {
        val newQueue = _queue.value.toMutableList()
        if (index in newQueue.indices) {
            newQueue.removeAt(index)
            _queue.value = newQueue
            originalQueue = newQueue
            if (_currentIndex.value >= newQueue.size) {
                _currentIndex.value = (newQueue.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        originalQueue = emptyList()
        _currentIndex.value = 0
    }
}

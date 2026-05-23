package com.mhs.player.media.sorting

import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SortManager @Inject constructor() {

    fun sort(items: List<MediaItemModel>, sortOrder: SortOrder): List<MediaItemModel> =
        when (sortOrder) {
            SortOrder.NAME_ASC -> items.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> items.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ASC -> items.sortedBy { it.dateModified }
            SortOrder.DATE_DESC -> items.sortedByDescending { it.dateModified }
            SortOrder.DURATION_ASC -> items.sortedBy { it.duration }
            SortOrder.DURATION_DESC -> items.sortedByDescending { it.duration }
            SortOrder.SIZE_ASC -> items.sortedBy { it.size }
            SortOrder.SIZE_DESC -> items.sortedByDescending { it.size }
        }

    fun sortOrderFromString(str: String): SortOrder = try {
        SortOrder.valueOf(str)
    } catch (e: IllegalArgumentException) {
        SortOrder.DATE_DESC
    }
}

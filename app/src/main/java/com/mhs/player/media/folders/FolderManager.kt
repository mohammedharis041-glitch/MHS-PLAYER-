package com.mhs.player.media.folders

import com.mhs.player.media.model.FolderModel
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderManager @Inject constructor() {

    fun buildFolders(items: List<MediaItemModel>): List<FolderModel> {
        return items
            .groupBy { it.folderPath }
            .map { (path, folderItems) ->
                FolderModel(
                    path = path,
                    name = folderItems.first().folderName,
                    itemCount = folderItems.size,
                    totalDuration = folderItems.sumOf { it.duration },
                    thumbnailUri = folderItems.firstOrNull { it.isVideo }?.uri
                        ?: folderItems.firstOrNull()?.albumArtUri,
                    lastModified = folderItems.maxOf { it.dateModified },
                    mediaType = if (folderItems.any { it.isVideo }) MediaType.VIDEO else MediaType.AUDIO
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun getItemsInFolder(items: List<MediaItemModel>, folderPath: String): List<MediaItemModel> =
        items.filter { it.folderPath == folderPath }

    fun getSubFolders(items: List<MediaItemModel>, parentPath: String): List<FolderModel> {
        return items
            .filter { it.folderPath.startsWith(parentPath) && it.folderPath != parentPath }
            .groupBy { it.folderPath }
            .map { (path, folderItems) ->
                FolderModel(
                    path = path,
                    name = folderItems.first().folderName,
                    itemCount = folderItems.size,
                    totalDuration = folderItems.sumOf { it.duration },
                    thumbnailUri = folderItems.firstOrNull { it.isVideo }?.uri,
                    lastModified = folderItems.maxOf { it.dateModified },
                    mediaType = if (folderItems.any { it.isVideo }) MediaType.VIDEO else MediaType.AUDIO
                )
            }
    }
}

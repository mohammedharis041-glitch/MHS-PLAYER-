package com.mhs.player.media.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.mhs.player.database.HiddenFolderDao
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.MediaType
import com.mhs.player.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hiddenFolderDao: HiddenFolderDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun scanVideos(): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItemModel>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeTypeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                val path = c.getString(dataCol) ?: ""
                val displayName = c.getString(nameCol) ?: ""
                val rawTitle = c.getString(titleCol) ?: ""
                val title = cleanTitle(rawTitle, displayName)
                val folderName = c.getString(bucketNameCol) ?: getFolderName(path)
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""

                items.add(
                    MediaItemModel(
                        id = id,
                        uri = contentUri,
                        title = title,
                        displayName = displayName,
                        path = path,
                        folderPath = getFolderPath(path),
                        folderName = folderName,
                        duration = c.getLong(durationCol),
                        size = c.getLong(sizeCol),
                        dateAdded = c.getLong(dateAddedCol) * 1000,
                        dateModified = c.getLong(dateModifiedCol) * 1000,
                        mimeType = c.getString(mimeTypeCol) ?: "video/*",
                        mediaType = MediaType.VIDEO,
                        width = width,
                        height = height,
                        resolution = resolution
                    )
                )
            }
        }
        val hiddenPaths = hiddenFolderDao.getAllHiddenPaths().toSet()
        items.filter { item ->
            hiddenPaths.none { hidden -> item.folderPath.startsWith(hidden) }
        }
    }

    suspend fun scanAudio(): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItemModel>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeTypeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                // Only build an album art URI when we actually have a valid album ID.
                // albumId == 0 means the track has no album entry → no art.
                val albumArtUri: Uri? = if (albumId > 0L) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                } else null
                val path = c.getString(dataCol) ?: ""
                val displayName = c.getString(nameCol) ?: ""
                val rawTitle = c.getString(titleCol) ?: ""
                val title = if (rawTitle.isNotBlank()) rawTitle else cleanTitle("", displayName)
                val folderName = c.getString(bucketNameCol) ?: getFolderName(path)

                items.add(
                    MediaItemModel(
                        id = id,
                        uri = contentUri,
                        title = title,
                        displayName = displayName,
                        path = path,
                        folderPath = getFolderPath(path),
                        folderName = folderName,
                        duration = c.getLong(durationCol),
                        size = c.getLong(sizeCol),
                        dateAdded = c.getLong(dateAddedCol) * 1000,
                        dateModified = c.getLong(dateModifiedCol) * 1000,
                        mimeType = c.getString(mimeTypeCol) ?: "audio/*",
                        mediaType = MediaType.AUDIO,
                        artist = c.getString(artistCol) ?: "Unknown Artist",
                        album = c.getString(albumCol) ?: "Unknown Album",
                        albumArtUri = albumArtUri
                    )
                )
            }
        }
        val hiddenPaths = hiddenFolderDao.getAllHiddenPaths().toSet()
        items.filter { item ->
            hiddenPaths.none { hidden -> item.folderPath.startsWith(hidden) }
        }
    }

    private fun cleanTitle(title: String, displayName: String): String {
        if (title.isNotBlank() && !title.contains(".")) return title
        val name = displayName.ifBlank { title }
        return name.substringBeforeLast(".").replace("_", " ").trim()
    }

    private fun getFolderPath(path: String): String =
        File(path).parent ?: ""

    private fun getFolderName(path: String): String =
        File(path).parentFile?.name ?: "Unknown"
}

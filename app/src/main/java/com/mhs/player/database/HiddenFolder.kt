package com.mhs.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "hidden_folders",
    indices = [Index(value = ["folderPath"], unique = true)]
)
data class HiddenFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderPath: String,
    val folderName: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface HiddenFolderDao {
    @Query("SELECT * FROM hidden_folders ORDER BY addedAt DESC")
    fun getAllHiddenFolders(): Flow<List<HiddenFolder>>

    @Query("SELECT folderPath FROM hidden_folders")
    suspend fun getAllHiddenPaths(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_folders WHERE folderPath = :path)")
    suspend fun isHidden(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideFolder(folder: HiddenFolder)

    @Query("DELETE FROM hidden_folders WHERE folderPath = :path")
    suspend fun unhideFolder(path: String)

    @Query("DELETE FROM hidden_folders")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM hidden_folders")
    fun getHiddenCount(): Flow<Int>
}

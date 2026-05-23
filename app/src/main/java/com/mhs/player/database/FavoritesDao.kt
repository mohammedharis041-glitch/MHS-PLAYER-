package com.mhs.player.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    fun isFavorite(mediaId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavoriteOnce(mediaId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteItem)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun deleteFavorite(mediaId: Long)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoritesCount(): Flow<Int>
}

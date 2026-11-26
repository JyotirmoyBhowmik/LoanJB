package com.jyotirmoyb.loan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AmfiNavDao {

    @Query(
        """
        SELECT * FROM amfi_nav_schemes
        WHERE schemeName LIKE '%' || :query || '%'
           OR schemeCode LIKE :query || '%'
           OR isinGrowth LIKE '%' || :query || '%'
           OR amcName LIKE '%' || :query || '%'
        ORDER BY isFavourite DESC, schemeName ASC
        LIMIT :limit
        """
    )
    fun searchSchemes(query: String, limit: Int = 80): Flow<List<AmfiNavEntity>>

    @Query("SELECT * FROM amfi_nav_schemes ORDER BY isFavourite DESC, schemeName ASC LIMIT :limit")
    fun observeTopSchemes(limit: Int = 80): Flow<List<AmfiNavEntity>>

    @Query("SELECT * FROM amfi_nav_schemes WHERE isFavourite = 1 ORDER BY schemeName ASC")
    fun observeFavourites(): Flow<List<AmfiNavEntity>>

    @Query("SELECT schemeCode FROM amfi_nav_schemes WHERE isFavourite = 1")
    suspend fun getFavouriteCodes(): List<String>

    @Query("SELECT COUNT(*) FROM amfi_nav_schemes")
    fun observeTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(items: List<AmfiNavEntity>)

    @Query("UPDATE amfi_nav_schemes SET isFavourite = :isFav WHERE schemeCode = :schemeCode")
    suspend fun setFavourite(schemeCode: String, isFav: Boolean)

    @Query("SELECT * FROM amfi_nav_sync WHERE id = 1")
    fun observeSyncMeta(): Flow<AmfiNavSyncEntity?>

    @Query("SELECT * FROM amfi_nav_sync WHERE id = 1")
    suspend fun getSyncMetaOnce(): AmfiNavSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMeta(meta: AmfiNavSyncEntity)
}

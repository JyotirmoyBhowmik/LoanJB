package com.jyotirmoyb.loan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteConfigDao {

    @Query("SELECT * FROM remote_config_cache ORDER BY fileName ASC")
    fun observeAllCaches(): Flow<List<RemoteConfigCacheEntity>>

    @Query("SELECT * FROM remote_config_cache WHERE fileName = :fileName LIMIT 1")
    suspend fun getCacheByFile(fileName: String): RemoteConfigCacheEntity?

    @Query("SELECT * FROM remote_config_cache")
    suspend fun getAllCachesOnce(): List<RemoteConfigCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(entity: RemoteConfigCacheEntity)

    @Query("DELETE FROM remote_config_cache WHERE fileName = :fileName")
    suspend fun deleteCacheByFile(fileName: String)

    @Query("DELETE FROM remote_config_cache")
    suspend fun clearAllCaches()

    @Query("SELECT * FROM manual_overrides ORDER BY `key` ASC")
    fun observeManualOverrides(): Flow<List<ManualOverrideEntity>>

    @Query("SELECT * FROM manual_overrides ORDER BY `key` ASC")
    suspend fun getAllManualOverridesOnce(): List<ManualOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManualOverride(entity: ManualOverrideEntity)

    @Query("DELETE FROM manual_overrides WHERE `key` = :key")
    suspend fun deleteManualOverride(key: String)

    @Query("DELETE FROM manual_overrides")
    suspend fun clearAllManualOverrides()

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun observeSyncMetadata(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getSyncMetadataOnce(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(entity: SyncMetadataEntity)
}

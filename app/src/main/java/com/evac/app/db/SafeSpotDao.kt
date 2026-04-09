package com.evac.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [SafeSpotEntity].
 *
 * Uses REPLACE strategy for upserts — the repository layer handles
 * timestamp-based conflict resolution before calling [upsertSpot].
 */
@Dao
interface SafeSpotDao {

    /**
     * Insert or replace a single safe spot.
     * Caller (Repository) must check [SafeSpotEntity.updatedAt] BEFORE calling.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpot(spot: SafeSpotEntity)

    /**
     * Reactive stream of all active safe spots, ordered by most recently updated.
     * Used by MapFragment to plot pins on the map.
     */
    @Query("SELECT * FROM safe_spots WHERE is_active = 1 ORDER BY updated_at DESC")
    fun getAllActiveSafeSpots(): Flow<List<SafeSpotEntity>>

    /**
     * Reactive stream of ALL safe spots (active + inactive). For debug/admin screens.
     */
    @Query("SELECT * FROM safe_spots ORDER BY updated_at DESC")
    fun getAllSafeSpots(): Flow<List<SafeSpotEntity>>

    /**
     * Fetch a single spot by ID. Used by the repository during ingestion
     * to compare timestamps for conflict resolution.
     */
    @Query("SELECT * FROM safe_spots WHERE id = :id LIMIT 1")
    suspend fun getSpotById(id: String): SafeSpotEntity?

    /**
     * Count of active spots — for quick status display.
     */
    @Query("SELECT COUNT(*) FROM safe_spots WHERE is_active = 1")
    suspend fun getActiveCount(): Int
}

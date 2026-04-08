package com.evac.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * EvacDao — Data Access Object for the Reverse Mesh (ACKs + Bulletins).
 *
 * Critical design decisions:
 *   1. **Deduplication**: All @Insert use OnConflictStrategy.IGNORE.
 *      In a mesh network, the same message arrives from multiple peers constantly.
 *      IGNORE silently drops duplicates at the SQLite level — zero UI spam,
 *      zero exceptions, zero wasted CPU on conflict resolution.
 *
 *   2. **Targeted Routing (ACKs)**: getMyAcks() filters by localDeviceId.
 *      Every phone stores every ACK (for store-and-forward relay), but the UI
 *      should ONLY show ACKs whose target_device_id matches the local phone.
 *
 *   3. **Garbage Collection**: purgeExpired() deletes rows where expires_at < now.
 *      This prevents infinite loops and unbounded DB growth during network floods.
 *
 *   4. **Return type Long for inserts**: Room returns the rowId on success, or -1
 *      when IGNORE fires. The repository layer uses this to distinguish "newly
 *      inserted" from "already had it" — critical for one-time UI notifications.
 */
@Dao
interface EvacDao {

    // ──────────────────────────────────────────────────────────────────────────
    //  INSERTS — OnConflictStrategy.IGNORE prevents duplicate UI notifications
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Insert an ACK. Returns the rowId (>= 0) on success, or -1 if the UUID
     * already exists (duplicate silently ignored).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAck(ack: AckEntity): Long

    /**
     * Insert a Bulletin. Returns the rowId (>= 0) on success, or -1 if the UUID
     * already exists (duplicate silently ignored).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBulletin(bulletin: BulletinEntity): Long

    // ──────────────────────────────────────────────────────────────────────────
    //  QUERIES — Flow-based for reactive UI observation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Observe all active (non-expired) Bulletins, newest first.
     * Every phone shows every bulletin — no device filtering needed.
     */
    @Query("SELECT * FROM bulletins WHERE expires_at > :currentTime ORDER BY timestamp DESC")
    fun getActiveBulletins(currentTime: Long = System.currentTimeMillis()): Flow<List<BulletinEntity>>

    /**
     * Observe ACKs targeted ONLY at this device, newest first.
     * Data Mules carry ACKs in their DB but never see them in the UI.
     */
    @Query("SELECT * FROM acks WHERE target_device_id = :localDeviceId AND expires_at > :currentTime ORDER BY timestamp DESC")
    fun getMyAcks(
        localDeviceId: String,
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<AckEntity>>

    /**
     * Fetch all ACKs (regardless of target) for relay/forwarding purposes.
     * Used by SyncEngine to know which ACKs to send to newly connected peers.
     */
    @Query("SELECT * FROM acks WHERE expires_at > :currentTime")
    suspend fun getAllActiveAcks(currentTime: Long = System.currentTimeMillis()): List<AckEntity>

    /**
     * Fetch all Bulletins for relay/forwarding purposes.
     */
    @Query("SELECT * FROM bulletins WHERE expires_at > :currentTime")
    suspend fun getAllActiveBulletins(currentTime: Long = System.currentTimeMillis()): List<BulletinEntity>

    // ──────────────────────────────────────────────────────────────────────────
    //  GARBAGE COLLECTION — TTL-based purge to prevent memory/storage crashes
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Delete all expired ACKs. Returns number of rows deleted.
     */
    @Query("DELETE FROM acks WHERE expires_at < :currentTime")
    suspend fun purgeExpiredAcks(currentTime: Long = System.currentTimeMillis()): Int

    /**
     * Delete all expired Bulletins. Returns number of rows deleted.
     */
    @Query("DELETE FROM bulletins WHERE expires_at < :currentTime")
    suspend fun purgeExpiredBulletins(currentTime: Long = System.currentTimeMillis()): Int
}

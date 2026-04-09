package com.evac.app.repository

import android.util.Log
import com.evac.app.db.SafeSpotDao
import com.evac.app.db.SafeSpotEntity
import kotlinx.coroutines.flow.Flow

/**
 * The single ingestion funnel for all SafeSpot data.
 *
 * V1: Data arrives via HTTP sync (SafeSpotSyncManager).
 * V2: This SAME [ingestSafeSpots] function will be called by the
 *     Mesh payload receiver for offline peer-to-peer updates.
 *
 * Conflict resolution strategy: "Last-Write-Wins" based on [SafeSpotEntity.updatedAt].
 * This is intentionally simple for V1 but sufficient for mesh scenarios where
 * the Commander is the single source of truth.
 */
class SafeSpotRepository(private val dao: SafeSpotDao) {

    companion object {
        private const val TAG = "SafeSpotRepository"
    }

    /** Reactive stream of active safe spots for UI consumption. */
    val activeSafeSpots: Flow<List<SafeSpotEntity>> = dao.getAllActiveSafeSpots()

    /**
     * Ingest a batch of safe spots with timestamp-based conflict resolution.
     *
     * For each incoming spot:
     * - If it doesn't exist locally → INSERT
     * - If it exists but incoming has a NEWER [updatedAt] → REPLACE
     * - If it exists and incoming is OLDER or equal → SKIP (preserve local)
     *
     * @param incomingSpots List of spots from any source (HTTP, mesh, manual)
     * @return Number of spots actually written (inserted or updated)
     */
    suspend fun ingestSafeSpots(incomingSpots: List<SafeSpotEntity>): Int {
        var written = 0
        for (spot in incomingSpots) {
            val existing = dao.getSpotById(spot.id)
            if (existing == null) {
                // New spot — insert directly
                dao.upsertSpot(spot)
                written++
                Log.d(TAG, "INSERT new spot: ${spot.name} [${spot.id}]")
            } else if (spot.updatedAt > existing.updatedAt) {
                // Incoming is newer — overwrite
                dao.upsertSpot(spot)
                written++
                Log.d(TAG, "UPDATE spot (newer timestamp): ${spot.name} [${spot.id}]")
            } else {
                // Local is same age or newer — skip
                Log.d(TAG, "SKIP spot (local is current): ${spot.name} [${spot.id}]")
            }
        }
        Log.i(TAG, "Ingestion complete: $written/${incomingSpots.size} spots written")
        return written
    }
}

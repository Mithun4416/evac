package com.evac.app.sync

import android.util.Log
import com.evac.app.db.SafeSpotEntity
import com.evac.app.repository.SafeSpotRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * V1 Pre-Disaster Sync: Fetches SafeSpot JSON from Firebase Hosting while
 * the internet is still active. Data is funneled through [SafeSpotRepository]
 * which handles timestamp-based conflict resolution.
 *
 * In V2, this class will be supplemented (not replaced) by the Mesh payload
 * receiver, which will call the same [SafeSpotRepository.ingestSafeSpots].
 */
class SafeSpotSyncManager(private val repository: SafeSpotRepository) {

    companion object {
        private const val TAG = "SafeSpotSyncManager"
    }

    private val db = FirebaseFirestore.getInstance()

    /**
     * Result sealed class for clean UI feedback.
     */
    sealed class SyncResult {
        data class Success(val totalFetched: Int, val written: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    /**
     * JSON model matching the Commander Dashboard export format.
     * Separate from [SafeSpotEntity] to decouple network schema from DB schema.
     */
    private data class SafeSpotJson(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val type: String,
        val is_active: Boolean,
        val updated_at: Long,
        val signature: String?
    )

    suspend fun sync(): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting sync from Firestore...")

                val snapshot = db.collection("safespots").get().await()

                if (snapshot.isEmpty) {
                    Log.i(TAG, "0 safespots found in Firestore")
                    return@withContext SyncResult.Success(0, 0)
                }

                // Convert to entities
                val entities = snapshot.documents.mapNotNull { doc ->
                    try {
                        SafeSpotEntity(
                            id = doc.getString("id") ?: return@mapNotNull null,
                            name = doc.getString("name") ?: return@mapNotNull null,
                            latitude = doc.getDouble("latitude") ?: return@mapNotNull null,
                            longitude = doc.getDouble("longitude") ?: return@mapNotNull null,
                            type = (doc.getString("type") ?: "SHELTER").uppercase(),
                            isActive = doc.getBoolean("is_active") ?: true,
                            updatedAt = doc.getLong("updated_at") ?: System.currentTimeMillis(),
                            signature = doc.getString("signature")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                Log.i(TAG, "Parsed ${entities.size} spots from Firestore")

                // Funnel through repository (timestamp conflict resolution)
                val written = repository.ingestSafeSpots(entities)

                Log.i(TAG, "Sync complete: $written/${entities.size} spots written")
                SyncResult.Success(totalFetched = entities.size, written = written)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during sync", e)
                SyncResult.Error("Sync failed: ${e.localizedMessage}")
            }
        }

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Start observing Firestore for real-time updates.
     */
    fun startListening() {
        if (listenerRegistration != null) return
        
        Log.i(TAG, "Starting real-time SafeSpot listener...")
        listenerRegistration = db.collection("safespots").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "SafeSpot listen failed.", error)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val entities = snapshot.documents.mapNotNull { doc ->
                        try {
                            SafeSpotEntity(
                                id = doc.getString("id") ?: return@mapNotNull null,
                                name = doc.getString("name") ?: return@mapNotNull null,
                                latitude = doc.getDouble("latitude") ?: return@mapNotNull null,
                                longitude = doc.getDouble("longitude") ?: return@mapNotNull null,
                                type = (doc.getString("type") ?: "SHELTER").uppercase(),
                                isActive = doc.getBoolean("is_active") ?: true,
                                updatedAt = doc.getLong("updated_at") ?: System.currentTimeMillis(),
                                signature = doc.getString("signature")
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val written = repository.ingestSafeSpots(entities)
                    Log.i(TAG, "Realtime sync: $written/${entities.size} spots updated automatically")
                }
            }
        }
    }

    /**
     * Stop observing real-time updates to save battery/bandwidth.
     */
    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}

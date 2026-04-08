package com.evac.app.ui.bulletin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.util.DeviceFingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * BulletinViewModel — feeds the Alerts section with data from BOTH data pipelines:
 *
 *   Pipeline 1 (Legacy): messages table WHERE type IN ('BULLETIN','ACK')
 *     → populated by GatewayManager + SyncEngine (cloud + peer SOS routing)
 *
 *   Pipeline 2 (Reverse Mesh): acks + bulletins tables
 *     → populated by EvacRepository (offline store-and-forward relay)
 *
 * Both flows are combined into a single list, deduplicated by ID, and sorted
 * by timestamp (newest first). This ensures the Alerts section shows data
 * regardless of which pipeline delivered it.
 */
class BulletinViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val localDeviceId = DeviceFingerprint.getId(application)

    /**
     * Combined Flow that merges ALL three sources:
     *   1. Legacy messages table (BULLETIN + ACK types)
     *   2. Reverse Mesh bulletins table (all bulletins for everyone)
     *   3. Reverse Mesh acks table (only ACKs targeted at THIS device)
     *
     * Deduplication: uses ID/UUID as the key. If the same message exists in
     * both the legacy table and the Reverse Mesh table, it appears only once.
     */
    val bulletinsAndAcks: Flow<List<MessageEntity>> = combine(
        database.messageDao().getBulletinsAndAcks(),
        database.evacDao().getActiveBulletins(),
        database.evacDao().getMyAcks(localDeviceId)
    ) { legacyMessages, reverseMeshBulletins, reverseMeshAcks ->

        // Convert Reverse Mesh entities to MessageEntity for the adapter
        val bulletinEntities = reverseMeshBulletins.map { bulletin ->
            MessageEntity(
                id = bulletin.uuid,
                type = "BULLETIN",
                timestamp = bulletin.timestamp,
                body = bulletin.message
            )
        }

        val ackEntities = reverseMeshAcks.map { ack ->
            MessageEntity(
                id = ack.uuid,
                type = "ACK",
                timestamp = ack.timestamp,
                body = ack.message,
                targetDeviceId = ack.targetDeviceId
            )
        }

        // Merge all sources, dedup by ID, sort by timestamp DESC
        (legacyMessages + bulletinEntities + ackEntities)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }
}
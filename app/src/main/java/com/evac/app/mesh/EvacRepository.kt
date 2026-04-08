package com.evac.app.mesh

import android.util.Log
import com.evac.app.db.AckEntity
import com.evac.app.db.BulletinEntity
import com.evac.app.db.EvacDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EvacRepository — The Routing Brain of the Reverse Mesh.
 *
 * This is the critical layer that separates two concerns that MUST NOT be conflated:
 *
 *   1. **Database Insertion** (for store-and-forward relay):
 *      Every incoming ACK/Bulletin is inserted into Room so that this device's
 *      SyncEngine can relay it to the next peer. This happens for ALL messages.
 *
 *   2. **UI Notification** (for the end-user):
 *      A UI alert fires ONLY when:
 *        - The message is genuinely NEW (not a duplicate — INSERT returned rowId >= 0).
 *        - For ACKs: the target_device_id matches this phone's DeviceFingerprint.
 *        - For Bulletins: always (every device shows every bulletin).
 *
 * Without this separation, "Data Mule" phones would get popup storms for
 * ACKs they're merely carrying, and duplicate messages from mesh reconnections
 * would trigger repeat notifications.
 */
class EvacRepository(private val evacDao: EvacDao) {

    companion object {
        private const val TAG = "EvacRepository"
    }

    /**
     * Sealed class representing the result of handling an incoming payload.
     * The UI layer consumes this to decide whether to display an alert.
     */
    sealed class IncomingResult {

        /** A new Bulletin that should be shown to this device's user. */
        data class NewBulletin(val bulletin: BulletinEntity) : IncomingResult()

        /** A new ACK targeted at this specific device — show a popup. */
        data class NewTargetedAck(val ack: AckEntity) : IncomingResult()

        /** The message was stored for relay but is NOT for this device's UI.
         *  Reasons: duplicate, ACK for someone else, or expired. */
        object Silent : IncomingResult()
    }

    /**
     * Process raw bytes received from the mesh network.
     *
     * The flow:
     *   1. Deserialize the bytes into an entity.
     *   2. Insert into Room (IGNORE on duplicate UUID).
     *   3. Check if this insertion was new (rowId >= 0).
     *   4. For ACKs, additionally check if target_device_id == localDeviceId.
     *   5. Return [IncomingResult] for the UI layer.
     *
     * @param bytes         Raw payload from Nearby Connections / WiFi Direct.
     * @param localDeviceId This phone's DeviceFingerprint (SHA-256 of ANDROID_ID).
     * @return [IncomingResult] indicating whether the UI should react.
     */
    suspend fun handleIncomingPayload(
        bytes: ByteArray,
        localDeviceId: String
    ): IncomingResult = withContext(Dispatchers.IO) {

        // ── Step 1: Deserialize ──────────────────────────────────────────
        val payload = MeshPayloadSerializer.deserialize(bytes)
        if (payload == null) {
            Log.w(TAG, "Deserialization failed — dropping payload")
            return@withContext IncomingResult.Silent
        }

        // ── Step 2 & 3: Insert + check if new ────────────────────────────
        when (payload.type) {
            MeshPayloadSerializer.PayloadType.BULLETIN -> {
                val bulletin = payload.bulletin!!

                // TTL check: drop if already expired
                if (bulletin.expiresAt < System.currentTimeMillis()) {
                    Log.d(TAG, "Expired bulletin ${bulletin.uuid} — dropping")
                    return@withContext IncomingResult.Silent
                }

                val rowId = evacDao.insertBulletin(bulletin)

                if (rowId >= 0) {
                    // Genuinely new bulletin — every device shows it
                    Log.i(TAG, "✅ NEW Bulletin inserted: ${bulletin.uuid}")
                    return@withContext IncomingResult.NewBulletin(bulletin)
                } else {
                    // Duplicate — silently ignored at DB level
                    Log.d(TAG, "Duplicate bulletin ${bulletin.uuid} — IGNORE fired")
                    return@withContext IncomingResult.Silent
                }
            }

            MeshPayloadSerializer.PayloadType.ACK -> {
                val ack = payload.ack!!

                // TTL check: drop if already expired
                if (ack.expiresAt < System.currentTimeMillis()) {
                    Log.d(TAG, "Expired ACK ${ack.uuid} — dropping")
                    return@withContext IncomingResult.Silent
                }

                // Always insert — even if not for us — so we can relay it
                val rowId = evacDao.insertAck(ack)

                if (rowId >= 0) {
                    Log.i(TAG, "✅ NEW ACK inserted: ${ack.uuid} (target: ${ack.targetDeviceId})")

                    // Step 4: UI notification ONLY if this is OUR ACK
                    return@withContext if (ack.targetDeviceId == localDeviceId) {
                        Log.i(TAG, "🎯 ACK ${ack.uuid} is FOR THIS DEVICE — triggering UI")
                        IncomingResult.NewTargetedAck(ack)
                    } else {
                        Log.d(TAG, "📦 ACK ${ack.uuid} is for ${ack.targetDeviceId} — carrying as Data Mule")
                        IncomingResult.Silent
                    }
                } else {
                    // Duplicate — already carrying this ACK
                    Log.d(TAG, "Duplicate ACK ${ack.uuid} — IGNORE fired")
                    return@withContext IncomingResult.Silent
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Garbage Collection — called periodically by TtlCleanupWorker / MeshService
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Purge all expired ACKs and Bulletins from the database.
     * Prevents unbounded DB growth during prolonged mesh flooding.
     *
     * @return Total number of rows deleted across both tables.
     */
    suspend fun purgeExpired(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val acksDeleted = evacDao.purgeExpiredAcks(now)
        val bulletinsDeleted = evacDao.purgeExpiredBulletins(now)
        val total = acksDeleted + bulletinsDeleted
        if (total > 0) {
            Log.i(TAG, "🧹 Garbage collection: purged $acksDeleted ACKs + $bulletinsDeleted Bulletins")
        }
        total
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Relay helpers — used by SyncEngine to get messages for forwarding
    // ──────────────────────────────────────────────────────────────────────────

    /** Get all active ACKs for relay to a newly connected peer. */
    suspend fun getAllActiveAcks(): List<AckEntity> = withContext(Dispatchers.IO) {
        evacDao.getAllActiveAcks()
    }

    /** Get all active Bulletins for relay to a newly connected peer. */
    suspend fun getAllActiveBulletins(): List<BulletinEntity> = withContext(Dispatchers.IO) {
        evacDao.getAllActiveBulletins()
    }
}

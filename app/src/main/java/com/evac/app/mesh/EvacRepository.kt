package com.evac.app.mesh

import android.util.Log
import com.evac.app.db.AckEntity
import com.evac.app.db.BulletinEntity
import com.evac.app.db.EvacDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * EvacRepository — The Routing Brain of the Reverse Mesh.
 *
 * This class fixes the critical "Hoarder Bug" by ensuring that EVERY phone
 * acts as a ROUTER, not just a storage node. The contract is:
 *
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  IF insert returns rowId != -1L (message is NEW, not a duplicate)  │
 *   │  THEN immediately broadcast the raw bytes to all connected peers.  │
 *   │  This applies whether the data came from cloud OR from a peer.     │
 *   └─────────────────────────────────────────────────────────────────────┘
 *
 * The dedup-to-relay pipeline:
 *   1. Deserialize incoming bytes → Entity.
 *   2. Insert into Room with OnConflictStrategy.IGNORE.
 *   3. Room returns rowId: >= 0 means NEW, -1 means DUPLICATE.
 *   4. If NEW → broadcast(bytes) to mesh peers (the relay).
 *   5. If NEW + ACK + target matches this device → also trigger UI.
 *   6. If DUPLICATE → do nothing. No broadcast, no UI. Silent drop.
 *
 * @param evacDao   The Room DAO for ACKs and Bulletins.
 * @param broadcaster The mesh transport layer (NearbyManager wrapper).
 */
class EvacRepository(
    private val evacDao: EvacDao,
    private val broadcaster: MeshNetworkBroadcaster
) {

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
         *  Reasons: duplicate, ACK for someone else, expired, or relay-only. */
        object Silent : IncomingResult()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FUNCTION A: Cloud-to-Mesh Bridge (handleCloudSync)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called when the device has internet and pulls new data from an API/Firebase.
     * Simulates receiving a Bulletin from the cloud and injecting it into the mesh.
     *
     * The flow:
     *   1. Fetch/receive a Bulletin from the cloud (simulated here).
     *   2. Insert into Room (IGNORE dedup — another peer might have already
     *      pushed the same Bulletin into our DB via the mesh).
     *   3. If the insert succeeds (rowId != -1L), this is NEW to us.
     *      CRITICAL: Serialize it and broadcast to all offline mesh peers.
     *      This is how cloud data enters the offline mesh.
     *   4. Return the result for UI notification.
     *
     * In production, replace the simulated Bulletin with real API/Firestore data.
     */
    suspend fun handleCloudSync(): IncomingResult = withContext(Dispatchers.IO) {

        // ── Simulate: Bulletin fetched from cloud API ────────────────────
        val now = System.currentTimeMillis()
        val bulletin = BulletinEntity(
            uuid = UUID.randomUUID().toString(),
            message = "Emergency Shelter open at City Hall. Bring ID.",
            timestamp = now,                               // 13-digit millis
            expiresAt = now + (12 * 60 * 60 * 1000L)       // TTL: 12 hours in MILLIS
        )

        // ── Insert with dedup ────────────────────────────────────────────
        val rowId = evacDao.insertBulletin(bulletin)

        if (rowId != -1L) {
            // ✅ NEW — this cloud data doesn't exist in our mesh DB yet.
            Log.i(TAG, "☁️→📡 Cloud Bulletin inserted: ${bulletin.uuid}")

            // ═══════════════════════════════════════════════════════════════
            // 🚨 THE RELAY FIX: Push cloud data into the offline mesh.
            // Without this line, cloud messages STOP at this device.
            // ═══════════════════════════════════════════════════════════════
            val bytes = MeshPayloadSerializer.serialize(bulletin)
            broadcaster.broadcast(bytes)
            Log.i(TAG, "☁️→📡 Broadcast cloud Bulletin to mesh peers")

            return@withContext IncomingResult.NewBulletin(bulletin)
        } else {
            // Duplicate — we already had this (maybe a peer relayed it first)
            Log.d(TAG, "☁️ Cloud Bulletin ${bulletin.uuid} already in DB — skipping relay")
            return@withContext IncomingResult.Silent
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FUNCTION B: Offline Peer-to-Peer Handler (handleIncomingOfflinePayload)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called when raw bytes arrive from an offline mesh peer via
     * Nearby Connections / WiFi Direct.
     *
     * The flow:
     *   1. Deserialize the bytes into an Entity.
     *   2. TTL check — drop if already expired.
     *   3. Insert into Room (IGNORE on duplicate UUID).
     *   4. Check the rowId:
     *      - If rowId != -1L → Message is NEW.
     *        a) BROADCAST the SAME bytes to all OTHER connected peers.
     *           This is the Store-and-Forward relay. Without this,
     *           messages die at the first receiver (the "Hoarder Bug").
     *        b) If it's an ACK and target matches this device → trigger UI.
     *        c) If it's a Bulletin → trigger UI.
     *      - If rowId == -1L → Duplicate. Do NOTHING. Silent drop.
     *
     * @param bytes         Raw payload from Nearby Connections / WiFi Direct.
     * @param localDeviceId This phone's DeviceFingerprint (SHA-256 of ANDROID_ID).
     * @return [IncomingResult] indicating whether the UI should react.
     */
    suspend fun handleIncomingOfflinePayload(
        bytes: ByteArray,
        localDeviceId: String
    ): IncomingResult = withContext(Dispatchers.IO) {

        // ── Step 1: Deserialize ──────────────────────────────────────────
        val payload = MeshPayloadSerializer.deserialize(bytes)
        if (payload == null) {
            Log.w(TAG, "Deserialization failed — dropping payload")
            return@withContext IncomingResult.Silent
        }

        // ── Step 2-4: Insert + Relay + UI decision ───────────────────────
        when (payload.type) {
            MeshPayloadSerializer.PayloadType.BULLETIN -> {
                val bulletin = payload.bulletin!!

                // TTL guard: drop if already expired (millis comparison)
                if (bulletin.expiresAt < System.currentTimeMillis()) {
                    Log.d(TAG, "⏰ Expired bulletin ${bulletin.uuid} — dropping")
                    return@withContext IncomingResult.Silent
                }

                val rowId = evacDao.insertBulletin(bulletin)

                if (rowId != -1L) {
                    // ✅ NEW Bulletin — this device has never seen it.
                    Log.i(TAG, "✅ NEW Bulletin inserted: ${bulletin.uuid}")

                    // ═════════════════════════════════════════════════════════
                    // 🚨 THE RELAY FIX: Forward to all connected mesh peers.
                    // The phone is now a Data Mule. It MUST NOT hoard.
                    // Re-use the original bytes — no re-serialization needed.
                    // ═════════════════════════════════════════════════════════
                    broadcaster.broadcast(bytes)
                    Log.i(TAG, "📡 Relayed Bulletin ${bulletin.uuid} to mesh peers")

                    return@withContext IncomingResult.NewBulletin(bulletin)
                } else {
                    // Duplicate — already have this Bulletin. Stay silent.
                    Log.d(TAG, "🔇 Duplicate Bulletin ${bulletin.uuid} — IGNORE fired, no relay")
                    return@withContext IncomingResult.Silent
                }
            }

            MeshPayloadSerializer.PayloadType.ACK -> {
                val ack = payload.ack!!

                // TTL guard: drop if already expired (millis comparison)
                if (ack.expiresAt < System.currentTimeMillis()) {
                    Log.d(TAG, "⏰ Expired ACK ${ack.uuid} — dropping")
                    return@withContext IncomingResult.Silent
                }

                // Always insert — even if not for us — for relay purposes.
                val rowId = evacDao.insertAck(ack)

                if (rowId != -1L) {
                    // ✅ NEW ACK — first time this device has seen it.
                    Log.i(TAG, "✅ NEW ACK inserted: ${ack.uuid} (target: ${ack.targetDeviceId})")

                    // ═════════════════════════════════════════════════════════
                    // 🚨 THE RELAY FIX: Forward to all connected mesh peers.
                    // This ACK might not be for us, but the intended recipient
                    // could be 3 hops away. We MUST relay it forward.
                    // ═════════════════════════════════════════════════════════
                    broadcaster.broadcast(bytes)
                    Log.i(TAG, "📡 Relayed ACK ${ack.uuid} to mesh peers")

                    // UI decision: popup ONLY if this ACK is addressed to US.
                    return@withContext if (ack.targetDeviceId == localDeviceId) {
                        Log.i(TAG, "🎯 ACK ${ack.uuid} is FOR THIS DEVICE — triggering UI")
                        IncomingResult.NewTargetedAck(ack)
                    } else {
                        Log.d(TAG, "📦 ACK ${ack.uuid} is for ${ack.targetDeviceId} — Data Mule relay only")
                        IncomingResult.Silent
                    }
                } else {
                    // Duplicate — already carrying this ACK. No relay, no UI.
                    Log.d(TAG, "🔇 Duplicate ACK ${ack.uuid} — IGNORE fired, no relay")
                    return@withContext IncomingResult.Silent
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Garbage Collection — prevents infinite loops & memory crashes
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Purge all expired ACKs and Bulletins.
     * Compares expires_at against System.currentTimeMillis() (MILLISECONDS).
     *
     * @return Total number of rows deleted across both tables.
     */
    suspend fun purgeExpired(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val acksDeleted = evacDao.purgeExpiredAcks(now)
        val bulletinsDeleted = evacDao.purgeExpiredBulletins(now)
        val total = acksDeleted + bulletinsDeleted
        if (total > 0) {
            Log.i(TAG, "🧹 GC: purged $acksDeleted ACKs + $bulletinsDeleted Bulletins")
        }
        total
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Relay helpers — for delta sync when a new peer connects
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dump all active ACKs + Bulletins to a newly connected peer.
     * Called by MeshService in onPeerConnected().
     */
    suspend fun syncAllToPeer(peerEndpointId: String, sendToPeer: (String, ByteArray) -> Unit) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            val acks = evacDao.getAllActiveAcks(now)
            for (ack in acks) {
                sendToPeer(peerEndpointId, MeshPayloadSerializer.serialize(ack))
            }

            val bulletins = evacDao.getAllActiveBulletins(now)
            for (bulletin in bulletins) {
                sendToPeer(peerEndpointId, MeshPayloadSerializer.serialize(bulletin))
            }

            Log.i(TAG, "📤 Synced ${acks.size} ACKs + ${bulletins.size} Bulletins to peer $peerEndpointId")
        }
}

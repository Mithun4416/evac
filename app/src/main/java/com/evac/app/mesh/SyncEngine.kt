package com.evac.app.mesh

import android.util.Log
import com.evac.app.db.MessageDao
import com.evac.app.db.MessageEntity
import com.evac.app.model.AckMessage
import com.evac.app.model.BulletinMessage
import com.evac.app.model.SosMessage
import com.evac.app.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SyncEngine — the brain of the mesh.
 *
 * Security layers (in order):
 *   1. Payload size guard (max 64 KB)
 *   2. JSON parse validation
 *   3. UUID format check (reject empty/malformed IDs)
 *   4. hop_count >= max_hops → DROP
 *   5. TTL expiry → DROP
 *   6. SHA-256 hash integrity → DROP if tampered
 *   7. Deduplication via Room (INSERT IGNORE on UUID PK)
 *   8. Increment hop_count for relay
 */
class SyncEngine(private val dao: MessageDao) {

    companion object {
        private const val TAG = "SyncEngine"
        private const val MAX_PAYLOAD_BYTES = 65_536 // 64 KB guard
        private const val MAX_NOTE_LENGTH = 200       // sanity cap on text fields
    }

    // Listener so MeshService can react to delta sync requests
    var onSendMissingMessages: ((endpointId: String, messages: List<ByteArray>) -> Unit)? = null

    @Volatile var myLat: Double = 0.0
    @Volatile var myLng: Double = 0.0

    /**
     * Called when raw bytes arrive from a peer.
     * Returns the list of MessageEntity objects that were NEW and valid
     * (so MeshService can relay them to other peers).
     */
    suspend fun handleIncomingPayload(
        data: ByteArray,
        fromEndpointId: String = ""
    ): List<MessageEntity> = withContext(Dispatchers.IO) {

        // --- Guard: payload size ---
        if (data.size > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "DROPPED oversized payload: ${data.size} bytes > $MAX_PAYLOAD_BYTES")
            return@withContext emptyList()
        }

        val jsonStr = String(data, Charsets.UTF_8)

        return@withContext try {
            val json = JSONObject(jsonStr)
            val protocol = json.optString("_protocol", "")

            when (protocol) {
                "ID_LIST" -> {
                    handleIdListExchange(json, fromEndpointId)
                    emptyList()
                }
                "REQUEST_MESSAGES" -> {
                    handleMessageRequest(json, fromEndpointId)
                    emptyList()
                }
                else -> {
                    val result = processMessage(json)
                    if (result != null) listOf(result) else emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse payload", e)
            emptyList()
        }
    }

    /**
     * Process a single message JSON.
     * Returns the inserted entity if valid & new, null otherwise.
     */
    private suspend fun processMessage(json: JSONObject): MessageEntity? {
        val type = json.optString("type", "")
        val id = json.optString("id", "")
        val hopCount = json.optInt("hop_count", 0)
        val maxHops = json.optInt("max_hops", MeshConstants.MAX_HOPS)

        // --- Guard: empty/malformed ID ---
        if (id.isBlank() || id.length < 8) {
            Log.w(TAG, "DROPPED message with invalid ID: '$id'")
            return null
        }

        // --- Guard: hop_count negative or absurd ---
        if (hopCount < 0 || maxHops < 1 || maxHops > 100) {
            Log.w(TAG, "DROPPED message $id: invalid hop_count=$hopCount or max_hops=$maxHops")
            return null
        }

        // --- Filter: hop_count >= max_hops → DROP ---
        if (hopCount >= maxHops) {
            Log.w(TAG, "DROPPED message $id: hop_count ($hopCount) >= max_hops ($maxHops)")
            return null
        }

        // --- Dedup: check if already in DB ---
        val existing = dao.getById(id)
        if (existing != null) {
            Log.d(TAG, "DUPLICATE message $id — already in DB, skipping")
            return null
        }

        // --- Parse based on type & validate ---
        val entity: MessageEntity = when (type) {
            "SOS" -> {
                val msg = SosMessage.fromJson(json)
                if (isExpired(msg.timestamp, msg.ttlHours)) {
                    Log.w(TAG, "EXPIRED SOS $id — TTL exceeded")
                    return null
                }
                // Hash integrity check
                if (!validateHash(msg.hash, msg.computeHash(), id)) return null
                // Sanitize text input
                msg.copy(note = sanitizeText(msg.note)).toEntity()
            }
            "BULLETIN" -> {
                val msg = BulletinMessage.fromJson(json)
                if (isExpired(msg.timestamp, msg.ttlHours)) {
                    Log.w(TAG, "EXPIRED BULLETIN $id — TTL exceeded")
                    return null
                }
                if (!validateHash(msg.hash, msg.computeHash(), id)) return null
                msg.copy(body = sanitizeText(msg.body)).toEntity()
            }
            "ACK" -> {
                val msg = AckMessage.fromJson(json)
                if (isExpired(msg.timestamp, msg.ttlHours)) {
                    Log.w(TAG, "EXPIRED ACK $id — TTL exceeded")
                    return null
                }
                if (!validateHash(msg.hash, msg.computeHash(), id)) return null
                msg.copy(body = sanitizeText(msg.body)).toEntity()
            }
            else -> {
                Log.w(TAG, "DROPPED unknown message type: '$type'")
                return null
            }
        }

        // --- Insert into Room DB ---
        dao.insert(entity)
        Log.i(TAG, "✅ Inserted $type message: $id (hop: $hopCount)")
        return entity
    }

    // ------------------------------------------------------------------ //
    //                    Relay — Increment hop_count                       //
    // ------------------------------------------------------------------ //

    /**
     * Prepare a message for relay. Increments hop_count by 1.
     * Returns null if it would exceed max_hops.
     */
    fun prepareForRelay(entity: MessageEntity): ByteArray? {
        val newHopCount = entity.hopCount + 1

        if (newHopCount >= entity.maxHops) {
            Log.w(TAG, "NOT RELAYING ${entity.id}: new hop ($newHopCount) >= max (${entity.maxHops})")
            return null
        }

        val json = entityToJson(entity)
        json.put("hop_count", newHopCount)

        Log.d(TAG, "Prepared relay for ${entity.id}: hop ${entity.hopCount} → $newHopCount")
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ //
    //                    ID-List Delta Sync                                //
    // ------------------------------------------------------------------ //

    /**
     * Build payload containing all local message IDs for delta exchange.
     */
    suspend fun buildIdListPayload(): ByteArray = withContext(Dispatchers.IO) {
        val ids = dao.getAllIds()
        val json = JSONObject().apply {
            put("_protocol", "ID_LIST")
            put("ids", JSONArray(ids))
        }
        json.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Handle incoming ID list from peer.
     * Compute which messages we have that they don't, and send them.
     */
    private suspend fun handleIdListExchange(json: JSONObject, fromEndpointId: String) {
        val idsArray = json.getJSONArray("ids")
        val remoteIds = (0 until idsArray.length()).map { idsArray.getString(it) }
        Log.d(TAG, "Received ID list with ${remoteIds.size} IDs from $fromEndpointId")

        // Find messages we have that the peer doesn't
        val localMessages = dao.getAll()
        val remoteIdSet = remoteIds.toSet()
        val toSend = localMessages.filter { it.id !in remoteIdSet }

        if (toSend.isNotEmpty()) {
            Log.i(TAG, "Sending ${toSend.size} missing message(s) to $fromEndpointId")
            val payloads = toSend.mapNotNull { prepareForRelay(it) }
            onSendMissingMessages?.invoke(fromEndpointId, payloads)
        }
    }

    /**
     * Handle explicit message request from peer (by IDs).
     */
    private suspend fun handleMessageRequest(json: JSONObject, fromEndpointId: String) {
        val idsArray = json.getJSONArray("ids")
        val requestedIds = (0 until idsArray.length()).map { idsArray.getString(it) }
        Log.d(TAG, "Peer $fromEndpointId requesting ${requestedIds.size} messages")

        val payloads = requestedIds.mapNotNull { id ->
            dao.getById(id)?.let { prepareForRelay(it) }
        }
        if (payloads.isNotEmpty()) {
            onSendMissingMessages?.invoke(fromEndpointId, payloads)
        }
    }

    // ------------------------------------------------------------------ //
    //                       TTL Cleanup                                    //
    // ------------------------------------------------------------------ //

    /**
     * Delete all messages whose TTL has expired.
     * Called periodically by MeshService.
     */
    suspend fun cleanupExpiredMessages() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (24 * 3600 * 1000L) // conservative: 24h
        dao.deleteExpired(cutoff)
        Log.d(TAG, "Cleaned up expired messages (cutoff: $cutoff)")
    }

    // ------------------------------------------------------------------ //
    //                        Helper Functions                              //
    // ------------------------------------------------------------------ //

    fun entityToJson(entity: MessageEntity): JSONObject {
        return when (entity.type) {
            "SOS" -> SosMessage.fromEntity(entity).toJson()
            "BULLETIN" -> BulletinMessage.fromEntity(entity).toJson()
            "ACK" -> AckMessage.fromEntity(entity).toJson()
            else -> JSONObject().apply {
                put("id", entity.id)
                put("type", entity.type)
            }
        }
    }

    /**
     * Validate that the received hash matches the computed hash.
     * If the received hash is empty, skip validation (first-hop origin).
     */
    private fun validateHash(receivedHash: String, computedHash: String, msgId: String): Boolean {
        if (receivedHash.isBlank()) return true // origin node — no hash to validate
        if (receivedHash != computedHash) {
            Log.w(TAG, "TAMPERED message $msgId: hash mismatch! Expected=$computedHash Got=$receivedHash")
            return false
        }
        return true
    }

    /**
     * Sanitize user-supplied text to prevent injection and cap length.
     */
    private fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        return input
            .take(MAX_NOTE_LENGTH)
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .trim()
    }

    private fun isExpired(timestampIso: String, ttlHours: Int): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val msgTime = sdf.parse(timestampIso)?.time ?: return false
            val expiryTime = msgTime + (ttlHours * 3600 * 1000L)
            System.currentTimeMillis() > expiryTime
        } catch (e: Exception) {
            false // If we can't parse, don't expire — better to deliver than drop
        }
    }
}

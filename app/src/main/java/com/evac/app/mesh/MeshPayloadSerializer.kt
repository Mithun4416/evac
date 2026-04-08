package com.evac.app.mesh

import android.util.Log
import com.evac.app.db.AckEntity
import com.evac.app.db.BulletinEntity
import org.json.JSONObject

/**
 * MeshPayloadSerializer — converts Reverse Mesh entities to/from ByteArray
 * for transmission over Nearby Connections / WiFi Direct.
 *
 * Wire format (JSON over UTF-8):
 * ```json
 * {
 *   "payload_type": "ACK" | "BULLETIN",
 *   "uuid": "...",
 *   "message": "...",
 *   "timestamp": 1234567890123,
 *   "expires_at": 1234567890123,
 *   "target_device_id": "..."       // ACK only
 * }
 * ```
 *
 * Design notes:
 *   - Uses org.json (bundled in Android) instead of Gson/Kotlinx Serialization
 *     to keep the dependency tree zero-addition (matching the existing codebase).
 *   - The [PayloadType] enum is serialized as a string in the "payload_type" field
 *     so the receiving phone knows how to parse before deserializing.
 *   - Strict validation on deserialization: missing/malformed fields → null return
 *     (caller drops the payload).
 */
object MeshPayloadSerializer {

    private const val TAG = "MeshPayloadSerializer"

    /**
     * Discriminator embedded in the wire payload so the receiver knows
     * which entity type to deserialize into.
     */
    enum class PayloadType {
        ACK,
        BULLETIN
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  SERIALIZE → ByteArray (for Nearby Connections sendPayload)
    // ──────────────────────────────────────────────────────────────────────────

    /** Serialize an [AckEntity] to a transmittable ByteArray. */
    fun serialize(ack: AckEntity): ByteArray {
        val json = JSONObject().apply {
            put("payload_type", PayloadType.ACK.name)
            put("uuid", ack.uuid)
            put("message", ack.message)
            put("timestamp", ack.timestamp)
            put("expires_at", ack.expiresAt)
            put("target_device_id", ack.targetDeviceId)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    /** Serialize a [BulletinEntity] to a transmittable ByteArray. */
    fun serialize(bulletin: BulletinEntity): ByteArray {
        val json = JSONObject().apply {
            put("payload_type", PayloadType.BULLETIN.name)
            put("uuid", bulletin.uuid)
            put("message", bulletin.message)
            put("timestamp", bulletin.timestamp)
            put("expires_at", bulletin.expiresAt)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  DESERIALIZE — ByteArray → typed result
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Wrapper holding the deserialized result.
     * Exactly one of [ack] or [bulletin] is non-null.
     */
    data class DeserializedPayload(
        val type: PayloadType,
        val ack: AckEntity? = null,
        val bulletin: BulletinEntity? = null
    )

    /**
     * Deserialize raw bytes received from the mesh.
     *
     * @return A [DeserializedPayload] on success, or null if the data is
     *         malformed / unrecognized (caller should drop the packet silently).
     */
    fun deserialize(bytes: ByteArray): DeserializedPayload? {
        return try {
            val jsonStr = String(bytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val typeStr = json.optString("payload_type", "")
            val payloadType = try {
                PayloadType.valueOf(typeStr)
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Unknown payload_type: '$typeStr' — dropping packet")
                return null
            }

            val uuid = json.optString("uuid", "")
            if (uuid.isBlank() || uuid.length < 8) {
                Log.w(TAG, "Invalid UUID in payload — dropping packet")
                return null
            }

            val message = json.optString("message", "")
            val timestamp = json.optLong("timestamp", 0L)
            val expiresAt = json.optLong("expires_at", 0L)

            if (timestamp <= 0L || expiresAt <= 0L) {
                Log.w(TAG, "Invalid timestamp/expires_at in payload — dropping packet")
                return null
            }

            when (payloadType) {
                PayloadType.ACK -> {
                    val targetDeviceId = json.optString("target_device_id", "")
                    if (targetDeviceId.isBlank()) {
                        Log.w(TAG, "ACK missing target_device_id — dropping packet")
                        return null
                    }
                    DeserializedPayload(
                        type = PayloadType.ACK,
                        ack = AckEntity(
                            uuid = uuid,
                            message = message,
                            timestamp = timestamp,
                            expiresAt = expiresAt,
                            targetDeviceId = targetDeviceId
                        )
                    )
                }
                PayloadType.BULLETIN -> {
                    DeserializedPayload(
                        type = PayloadType.BULLETIN,
                        bulletin = BulletinEntity(
                            uuid = uuid,
                            message = message,
                            timestamp = timestamp,
                            expiresAt = expiresAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize payload", e)
            null
        }
    }
}

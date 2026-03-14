package com.evac.app.model

import com.evac.app.db.MessageEntity
import com.evac.app.util.HashUtil
import org.json.JSONObject
import java.util.UUID

/**
 * ACK message — sent from Command Center to a specific victim device.
 */
data class AckMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "ACK",
    val targetDeviceId: String,
    val body: String = "",
    val timestamp: String,
    val ttlHours: Int = 6,
    val hopCount: Int = 0,
    val maxHops: Int = 10,
    val signature: String = "",
    val hash: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("target_device_id", targetDeviceId)
        put("body", body)
        put("timestamp", timestamp)
        put("ttl_hours", ttlHours)
        put("hop_count", hopCount)
        put("max_hops", maxHops)
        put("ed25519_signature", signature)
        put("hash", computeHash())
    }

    fun computeHash(): String {
        val raw = "$id$type$targetDeviceId$body$timestamp$ttlHours$hopCount$maxHops"
        return HashUtil.sha256(raw)
    }

    fun toEntity(): MessageEntity = MessageEntity(
        id = id,
        type = type,
        timestamp = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(timestamp)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() },
        hopCount = hopCount,
        maxHops = maxHops,
        ttlHours = ttlHours,
        hash = computeHash(),
        targetDeviceId = targetDeviceId,
        body = body,
        signature = signature
    )

    companion object {
        fun fromJson(json: JSONObject): AckMessage = AckMessage(
            id = json.getString("id"),
            type = json.optString("type", "ACK"),
            targetDeviceId = json.getString("target_device_id"),
            body = json.optString("body", ""),
            timestamp = json.getString("timestamp"),
            ttlHours = json.optInt("ttl_hours", 6),
            hopCount = json.optInt("hop_count", 0),
            maxHops = json.optInt("max_hops", 10),
            signature = json.optString("ed25519_signature", ""),
            hash = json.optString("hash", "")
        )

        fun fromEntity(entity: MessageEntity): AckMessage = AckMessage(
            id = entity.id,
            targetDeviceId = entity.targetDeviceId ?: "",
            body = entity.body ?: "",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(entity.timestamp)),
            ttlHours = entity.ttlHours,
            hopCount = entity.hopCount,
            maxHops = entity.maxHops,
            signature = entity.signature ?: "",
            hash = entity.hash
        )
    }
}

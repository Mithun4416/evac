package com.evac.app.model

import com.evac.app.db.MessageEntity
import com.evac.app.util.HashUtil
import org.json.JSONObject
import java.util.UUID

/**
 * Bulletin message — signed by Command Center, broadcast via mesh.
 */
data class BulletinMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "BULLETIN",
    val alertType: String = "GENERAL",  // FLOOD | EARTHQUAKE | CYCLONE | GENERAL
    val body: String = "",
    val timestamp: String,
    val ttlHours: Int = 12,
    val hopCount: Int = 0,
    val maxHops: Int = 10,
    val zoneLat: Double = 0.0,
    val zoneLng: Double = 0.0,
    val zoneRadiusKm: Double = 5.0,
    val signature: String = "",
    val hash: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("alert_type", alertType)
        put("body", body)
        put("timestamp", timestamp)
        put("ttl_hours", ttlHours)
        put("hop_count", hopCount)
        put("max_hops", maxHops)
        put("affected_zone", JSONObject().apply {
            put("lat", zoneLat)
            put("lng", zoneLng)
            put("radius_km", zoneRadiusKm)
        })
        put("ed25519_signature", signature)
        put("hash", computeHash())
    }

    fun computeHash(): String {
        val raw = "$id$type$alertType$body$timestamp$ttlHours$hopCount$maxHops$zoneLat$zoneLng$zoneRadiusKm"
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
        alertType = alertType,
        body = body,
        zoneLat = zoneLat,
        zoneLng = zoneLng,
        zoneRadiusKm = zoneRadiusKm,
        signature = signature
    )

    companion object {
        fun fromJson(json: JSONObject): BulletinMessage {
            val zone = json.optJSONObject("affected_zone")
            return BulletinMessage(
                id = json.getString("id"),
                type = json.optString("type", "BULLETIN"),
                alertType = json.optString("alert_type", "GENERAL"),
                body = json.optString("body", ""),
                timestamp = json.getString("timestamp"),
                ttlHours = json.optInt("ttl_hours", 12),
                hopCount = json.optInt("hop_count", 0),
                maxHops = json.optInt("max_hops", 10),
                zoneLat = zone?.optDouble("lat", 0.0) ?: 0.0,
                zoneLng = zone?.optDouble("lng", 0.0) ?: 0.0,
                zoneRadiusKm = zone?.optDouble("radius_km", 5.0) ?: 5.0,
                signature = json.optString("ed25519_signature", ""),
                hash = json.optString("hash", "")
            )
        }

        fun fromEntity(entity: MessageEntity): BulletinMessage = BulletinMessage(
            id = entity.id,
            alertType = entity.alertType ?: "GENERAL",
            body = entity.body ?: "",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(entity.timestamp)),
            ttlHours = entity.ttlHours,
            hopCount = entity.hopCount,
            maxHops = entity.maxHops,
            zoneLat = entity.zoneLat ?: 0.0,
            zoneLng = entity.zoneLng ?: 0.0,
            zoneRadiusKm = entity.zoneRadiusKm ?: 5.0,
            signature = entity.signature ?: "",
            hash = entity.hash
        )
    }
}

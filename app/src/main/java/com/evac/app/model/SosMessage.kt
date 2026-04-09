package com.evac.app.model

import com.evac.app.db.MessageEntity
import com.evac.app.util.HashUtil
import org.json.JSONObject
import java.util.UUID

/**
 * SOS message matching the project JSON schema.
 */
data class SosMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "SOS",
    val status: String,              // MEDICAL | TRAPPED | HAZARD | SAFE
    val deviceId: String,
    val timestamp: String,           // ISO-8601
    val ttlHours: Int = 24,
    val hopCount: Int = 0,
    val maxHops: Int = 10,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float = 0f,
    val peopleCount: Int = 1,
    val batteryPct: Int = 100,
    val note: String = "",
    val phraseKey: String = "",
    val isVolumeSos: Boolean = false,
    val hash: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("status", status)
        put("device_id", deviceId)
        put("timestamp", timestamp)
        put("ttl_hours", ttlHours)
        put("hop_count", hopCount)
        put("max_hops", maxHops)
        put("lat", lat)
        put("lng", lng)
        put("accuracy_m", accuracyM.toDouble())
        put("people_count", peopleCount)
        put("battery_pct", batteryPct)
        put("note", note)
        put("phrase_key", phraseKey)
        put("is_volume_sos", isVolumeSos)
        put("hash", computeHash())
    }

    fun computeHash(): String {
        val raw = "$id$type$status$deviceId$timestamp$ttlHours$maxHops" +
                "$lat$lng$accuracyM$peopleCount$batteryPct$note$phraseKey$isVolumeSos"
        return HashUtil.sha256(raw)
    }

    fun toEntity(): MessageEntity = MessageEntity(
        id = id,
        type = type,
        timestamp = try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() },
        hopCount = hopCount,
        maxHops = maxHops,
        ttlHours = ttlHours,
        hash = computeHash(),
        status = status,
        deviceId = deviceId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        peopleCount = peopleCount,
        batteryPct = batteryPct,
        note = note,
        phraseKey = phraseKey,
        isVolumeSos = isVolumeSos
    )

    companion object {
        fun fromJson(json: JSONObject): SosMessage = SosMessage(
            id = json.getString("id"),
            type = json.optString("type", "SOS"),
            status = json.getString("status"),
            deviceId = json.getString("device_id"),
            timestamp = json.getString("timestamp"),
            ttlHours = json.optInt("ttl_hours", 24),
            hopCount = json.optInt("hop_count", 0),
            maxHops = json.optInt("max_hops", 10),
            lat = json.getDouble("lat"),
            lng = json.getDouble("lng"),
            accuracyM = json.optDouble("accuracy_m", 0.0).toFloat(),
            peopleCount = json.optInt("people_count", 1),
            batteryPct = json.optInt("battery_pct", 100),
            note = json.optString("note", ""),
            phraseKey = json.optString("phrase_key", ""),
            isVolumeSos = json.optBoolean("is_volume_sos", false),
            hash = json.optString("hash", "")
        )

        fun fromEntity(entity: MessageEntity): SosMessage = SosMessage(
            id = entity.id,
            status = entity.status ?: "MEDICAL",
            deviceId = entity.deviceId ?: "",
            timestamp = run {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.format(java.util.Date(entity.timestamp))
            },
            ttlHours = entity.ttlHours,
            hopCount = entity.hopCount,
            maxHops = entity.maxHops,
            lat = entity.lat ?: 0.0,
            lng = entity.lng ?: 0.0,
            accuracyM = entity.accuracyM ?: 0f,
            peopleCount = entity.peopleCount ?: 1,
            batteryPct = entity.batteryPct ?: 100,
            note = entity.note ?: "",
            phraseKey = entity.phraseKey ?: "",
            isVolumeSos = entity.isVolumeSos,
            hash = entity.hash
        )
    }
}

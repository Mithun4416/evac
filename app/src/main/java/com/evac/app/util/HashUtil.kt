package com.evac.app.util

import java.security.MessageDigest

object HashUtil {

    fun sha256(input: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashMessage(
        id: String,
        type: String,
        status: String?,
        deviceId: String,
        timestamp: Long,
        lat: Double?,
        lng: Double?,
        peopleCount: Int?,
        batteryPct: Int?,
        note: String?
    ): String {
        val raw = "$id|$type|$status|$deviceId|$timestamp|$lat|$lng|$peopleCount|$batteryPct|$note"
        return sha256(raw)
    }
}
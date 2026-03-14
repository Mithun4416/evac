package com.evac.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val type: String,           // SOS, BULLETIN, ACK
    val status: String?,        // MEDICAL, TRAPPED, HAZARD, SAFE
    val deviceId: String,
    val timestamp: Long,
    val ttlHours: Int,
    val hopCount: Int,
    val maxHops: Int,
    val lat: Double?,
    val lng: Double?,
    val accuracyM: Float?,
    val peopleCount: Int?,
    val batteryPct: Int?,
    val note: String?,
    val phraseKey: String?,
    val isVolumeSos: Boolean?,
    val hash: String,
    val body: String?,          // For BULLETIN and ACK
    val targetDeviceId: String?,// For ACK
    val signature: String?      // Ed25519 signature
)
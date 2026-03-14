package com.evac.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single Room entity for ALL message types (SOS, BULLETIN, ACK).
 * The [type] column discriminates which fields are populated.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,

    // Discriminator: "SOS", "BULLETIN", "ACK"
    val type: String,

    // --- Common fields ---
    val timestamp: Long,
    @ColumnInfo(name = "hop_count") val hopCount: Int = 0,
    @ColumnInfo(name = "max_hops") val maxHops: Int = 10,
    @ColumnInfo(name = "ttl_hours") val ttlHours: Int = 24,
    val hash: String = "",

    // --- SOS-specific ---
    val status: String? = null,          // MEDICAL | TRAPPED | HAZARD | SAFE
    @ColumnInfo(name = "device_id") val deviceId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float? = null,
    @ColumnInfo(name = "people_count") val peopleCount: Int? = null,
    @ColumnInfo(name = "battery_pct") val batteryPct: Int? = null,
    val note: String? = null,
    @ColumnInfo(name = "phrase_key") val phraseKey: String? = null,
    @ColumnInfo(name = "is_volume_sos") val isVolumeSos: Boolean = false,

    // --- BULLETIN-specific ---
    @ColumnInfo(name = "alert_type") val alertType: String? = null,
    val body: String? = null,
    @ColumnInfo(name = "zone_lat") val zoneLat: Double? = null,
    @ColumnInfo(name = "zone_lng") val zoneLng: Double? = null,
    @ColumnInfo(name = "zone_radius_km") val zoneRadiusKm: Double? = null,

    // --- ACK-specific ---
    @ColumnInfo(name = "target_device_id") val targetDeviceId: String? = null,

    // --- Shared signed field (BULLETIN + ACK) ---
    val signature: String? = null,

    // --- Firebase sync tracking ---
    @ColumnInfo(name = "synced_to_firebase") val syncedToFirebase: Boolean = false
)

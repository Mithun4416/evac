package com.evac.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for ACK messages — sent by Command Center to a specific victim.
 *
 * Routing contract:
 *   - Every device in the mesh stores this row (for store-and-forward relay).
 *   - Only the device whose DeviceFingerprint matches [targetDeviceId] shows a UI alert.
 *   - Deduplication: OnConflictStrategy.IGNORE on [uuid] PK.
 *   - Garbage collection: rows where [expiresAt] < System.currentTimeMillis() are purged.
 *
 * ⚠️ TIME UNIT CONTRACT: [timestamp] and [expiresAt] are ALWAYS 13-digit epoch
 *    MILLISECONDS (matching System.currentTimeMillis()), NEVER Unix seconds.
 *    Using seconds will cause instant TTL deletion.
 */
@Entity(tableName = "acks")
data class AckEntity(

    /** Globally unique message ID (UUID v4). Primary key for dedup. */
    @PrimaryKey
    val uuid: String,

    /** The body/content of the acknowledgment. */
    val message: String,

    /** Epoch MILLISECONDS when this ACK was created at the Command Center. */
    val timestamp: Long,

    /** Epoch MILLISECONDS after which this ACK should be garbage-collected. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    /**
     * SHA-256 device fingerprint of the intended recipient.
     * Only this device's UI reacts; all others are silent "Data Mules."
     */
    @ColumnInfo(name = "target_device_id")
    val targetDeviceId: String
)

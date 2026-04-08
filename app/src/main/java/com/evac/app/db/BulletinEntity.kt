package com.evac.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for Broadcast Bulletins — sent by Command Center to everyone.
 *
 * Routing contract:
 *   - Every device in the mesh stores AND displays this message.
 *   - Deduplication is handled at the DB level via OnConflictStrategy.IGNORE on [uuid].
 *   - Garbage collection is driven by [expiresAt]; rows past TTL are purged periodically.
 */
@Entity(tableName = "bulletins")
data class BulletinEntity(

    /** Globally unique message ID (UUID v4). Primary key for dedup. */
    @PrimaryKey
    val uuid: String,

    /** The bulletin content / alert body. */
    val message: String,

    /** Epoch millis when this Bulletin was created at the Command Center. */
    val timestamp: Long,

    /** Epoch millis after which this Bulletin should be garbage-collected. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)

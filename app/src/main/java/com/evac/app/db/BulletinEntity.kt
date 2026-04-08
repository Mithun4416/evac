package com.evac.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for Broadcast Bulletins — sent by Command Center to everyone.
 *
 * Routing contract:
 *   - Every device in the mesh stores AND displays this message.
 *   - Deduplication: OnConflictStrategy.IGNORE on [uuid] PK.
 *   - Garbage collection: rows where [expiresAt] < System.currentTimeMillis() are purged.
 *
 * ⚠️ TIME UNIT CONTRACT: [timestamp] and [expiresAt] are ALWAYS 13-digit epoch
 *    MILLISECONDS (matching System.currentTimeMillis()), NEVER Unix seconds.
 *    Using seconds will cause instant TTL deletion.
 */
@Entity(tableName = "bulletins")
data class BulletinEntity(

    /** Globally unique message ID (UUID v4). Primary key for dedup. */
    @PrimaryKey
    val uuid: String,

    /** The bulletin content / alert body. */
    val message: String,

    /** Epoch MILLISECONDS when this Bulletin was created at the Command Center. */
    val timestamp: Long,

    /** Epoch MILLISECONDS after which this Bulletin should be garbage-collected. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)

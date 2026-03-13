package com.evac.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entity (maps to JSON schema)
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val timestamp: Long,
    val senderId: String,
    val hops: Int = 0
)

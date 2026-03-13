package com.evac.app.model

// ACK message data class
data class AckMessage(
    val id: String,
    val type: String,
    val refMessageId: String,
    val senderId: String,
    val timestamp: Long
)

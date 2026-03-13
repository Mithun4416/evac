package com.evac.app.model

// Bulletin message data class
data class BulletinMessage(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val senderId: String,
    val signature: String,
    val timestamp: Long
)

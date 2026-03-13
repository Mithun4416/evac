package com.evac.app.model

// Data class matching JSON schema
data class SosMessage(
    val id: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val peopleCount: Int,
    val note: String,
    val phrase: String,
    val senderId: String,
    val timestamp: Long
)

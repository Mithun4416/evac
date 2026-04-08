package com.evac.app.ui.responder

import org.osmdroid.util.GeoPoint

data class SosTask(
    val id: String,
    val status: String,
    val lat: Double,
    val lng: Double,
    val peopleCount: Int,
    val batteryPct: Int?,
    val note: String?,
    val deviceId: String?,
    val timestamp: Long,
    val distanceMeters: Float,
    val etaMinutes: Int = 0,
    val routePoints: List<GeoPoint> = emptyList(),
    val isAssignedToMe: Boolean = false
)

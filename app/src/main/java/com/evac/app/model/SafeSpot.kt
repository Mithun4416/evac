package com.evac.app.model

enum class SpotType {
    SCHOOL, HOSPITAL, HIGH_GROUND, STADIUM, POLICE
}

enum class DisasterType {
    FLOOD, EARTHQUAKE, CYCLONE, FIRE, GENERAL
}

data class SafeSpot(
    val name: String,
    val type: SpotType,
    val lat: Double,
    val lng: Double,
    val capacity: Int,
    val suitableFor: List<DisasterType>
)

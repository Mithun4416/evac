package com.evac.app.util

import kotlin.math.*

/**
 * Pure Kotlin Haversine formula implementation for offline distance calculations.
 * No external dependencies — works without Google Play Services.
 *
 * Used by the "No-Map" fallback when OSMDroid tiles fail to load.
 */
object HaversineUtil {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculate the great-circle distance between two GPS coordinates.
     * @return Distance in kilometers
     */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate the initial bearing from point 1 to point 2.
     * @return Bearing in degrees (0-360, where 0=North, 90=East, 180=South, 270=West)
     */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Convert a bearing angle to a human-readable cardinal direction.
     * Uses 8 cardinal points (N, NE, E, SE, S, SW, W, NW).
     */
    fun bearingToCardinal(bearingDeg: Double): String {
        val normalized = (bearingDeg + 360) % 360
        return when {
            normalized < 22.5  -> "North"
            normalized < 67.5  -> "NE"
            normalized < 112.5 -> "East"
            normalized < 157.5 -> "SE"
            normalized < 202.5 -> "South"
            normalized < 247.5 -> "SW"
            normalized < 292.5 -> "West"
            normalized < 337.5 -> "NW"
            else               -> "North"
        }
    }

    /**
     * Format a distance for display: "1.2km" or "450m" for sub-kilometer distances.
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()}m"
        } else {
            "${"%.1f".format(distanceKm)}km"
        }
    }
}

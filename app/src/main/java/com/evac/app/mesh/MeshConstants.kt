package com.evac.app.mesh

// SERVICE_ID, TTL, MAX_HOPS, intervals
object MeshConstants {
    const val SERVICE_ID = "com.evac.mesh"
    const val TTL_SECONDS = 3600
    const val MAX_HOPS = 10
    const val SCAN_INTERVAL_MS = 5000L
    const val ADVERTISE_INTERVAL_MS = 5000L
}

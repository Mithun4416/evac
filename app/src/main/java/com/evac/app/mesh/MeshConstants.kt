package com.evac.app.mesh

object MeshConstants {

    // Nearby Connections service ID — must be unique to your app
    const val SERVICE_ID = "com.evac.app.mesh"

    // Message limits
    const val MAX_HOPS = 10
    const val TTL_HOURS_SOS = 24
    const val TTL_HOURS_BULLETIN = 12
    const val TTL_HOURS_ACK = 6
    const val TTL_SECONDS = 86400 // 24 hours

    // Sync intervals
    const val SYNC_INTERVAL_NORMAL_MS = 30_000L       // 30 seconds
    const val SYNC_INTERVAL_POWER_SAVER_MS = 300_000L // 5 minutes
    const val REBROADCAST_INTERVAL_MS = 600_000L      // 10 minutes

    // Notification
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "evac_mesh"
    const val NOTIFICATION_CHANNEL_NAME = "EVAC Mesh Network"
}
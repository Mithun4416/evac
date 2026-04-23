package com.evac.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.evac.app.MainActivity
import com.evac.app.R
import kotlin.math.*

/**
 * Detects nearby SOS signals within a configurable radius and fires
 * an Android notification + broadcasts a local Intent.
 *
 * Works entirely OFFLINE — no internet required.
 */
object ProximityAlertManager {

    private const val TAG = "ProximityAlert"
    private const val CHANNEL_ID = "evac_proximity"
    private const val CHANNEL_NAME = "Nearby Survivor Alerts"
    private const val NOTIF_ID_BASE = 5000

    /** Radius in meters within which we alert — 1 km default */
    const val ALERT_RADIUS_M = 1000.0

    /** Broadcast action sent to the app when a nearby SOS is found */
    const val ACTION_NEARBY_SOS = "com.evac.app.ACTION_NEARBY_SOS"
    const val EXTRA_DISTANCE_M = "distance_m"
    const val EXTRA_SOS_STATUS = "sos_status"
    const val EXTRA_PEOPLE_COUNT = "people_count"

    // Track notified SOS ids to avoid repeat alerts for the same signal
    private val alreadyAlerted = mutableSetOf<String>()

    /**
     * Call this whenever a new SOS message is received from the mesh.
     * @param ctx          Application context
     * @param myLat        This device's current latitude (from GPS/fused)
     * @param myLng        This device's current longitude
     * @param myDeviceId   This device's own ID (to exclude self)
     * @param sosId        Unique ID of the received SOS
     * @param sosLat       SOS origin latitude
     * @param sosLng       SOS origin longitude
     * @param sosStatus    e.g. "MEDICAL", "TRAPPED", "HAZARD", "SAFE"
     * @param peopleCount  Number of people at the SOS location
     */
    fun checkAndAlert(
        ctx: Context,
        myLat: Double,
        myLng: Double,
        myDeviceId: String,
        sosId: String,
        sosLat: Double?,
        sosLng: Double?,
        sosStatus: String,
        peopleCount: Int
    ) {
        // Ignore own SOS
        if (sosId.contains(myDeviceId)) return

        // Need valid location
        if (sosLat == null || sosLng == null ||
            sosLat == 0.0 || sosLng == 0.0 ||
            myLat == 0.0 || myLng == 0.0
        ) {
            Log.d(TAG, "Skipping proximity check — missing GPS for $sosId")
            return
        }

        // Only alert once per SOS id
        if (alreadyAlerted.contains(sosId)) return

        val distanceM = haversineMeters(myLat, myLng, sosLat, sosLng)
        Log.d(TAG, "SOS $sosId is ${distanceM.toInt()}m away (threshold: ${ALERT_RADIUS_M}m)")

        if (distanceM <= ALERT_RADIUS_M) {
            alreadyAlerted.add(sosId)
            val distStr = formatDistance(distanceM)

            Log.i(TAG, "NEARBY SURVIVOR DETECTED — $sosStatus, $distStr away")

            // Fire notification
            fireNotification(ctx, sosStatus, peopleCount, distStr, sosId)

            // Broadcast to app UI (CitizenFragment listens)
            val intent = Intent(ACTION_NEARBY_SOS).apply {
                putExtra(EXTRA_DISTANCE_M, distanceM.toFloat())
                putExtra(EXTRA_SOS_STATUS, sosStatus)
                putExtra(EXTRA_PEOPLE_COUNT, peopleCount)
            }
            ctx.sendBroadcast(intent)
        }
    }

    /** Clear alerts (call on fresh app start or SOS resolution) */
    fun clearAlerted(sosId: String) {
        alreadyAlerted.remove(sosId)
    }

    fun clearAll() {
        alreadyAlerted.clear()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun fireNotification(
        ctx: Context,
        status: String,
        people: Int,
        distStr: String,
        sosId: String
    ) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (safe to call repeatedly)
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when another survivor is detected within 1km via mesh"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Survivor Nearby — $distStr Away"
        val body = "$status signal · $people person${if (people > 1) "s" else ""} · Tap to view map"

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        // Use hash of sosId so each unique SOS gets its own notification slot
        nm.notify(NOTIF_ID_BASE + sosId.hashCode() % 100, notif)
    }

    /**
     * Haversine formula — returns distance in metres between two GPS coords.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0 // Earth radius in metres
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return R * 2 * asin(sqrt(a))
    }

    private fun formatDistance(m: Double): String =
        if (m < 1000) "${m.toInt()}m" else "${"%.1f".format(m / 1000)}km"
}

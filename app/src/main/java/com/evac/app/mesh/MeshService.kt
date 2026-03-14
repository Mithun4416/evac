package com.evac.app.mesh

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.evac.app.R
import com.evac.app.gateway.GatewayManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class MeshService : Service() {

    private val TAG = "MeshService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var nearbyManager: NearbyManager
    private lateinit var syncEngine: SyncEngine
    private var gatewayManager: GatewayManager? = null

    // ── Location tracking ─────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            syncEngine.myLat = loc.latitude
            syncEngine.myLng = loc.longitude
            Log.d(TAG, "Location updated: ${loc.latitude}, ${loc.longitude}")
        }
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(
            MeshConstants.NOTIFICATION_ID,
            buildNotification()
        )

        // Init managers
        nearbyManager = NearbyManager(
            context = this,
            onMessageReceived = { json ->
                syncEngine.onMessageReceived(json)
            },
            onPeerConnected = { endpointId ->
                Log.d(TAG, "Peer connected: $endpointId")
                syncEngine.onPeerConnected(endpointId)
            },
            onPeerDisconnected = { endpointId ->
                Log.d(TAG, "Peer disconnected: $endpointId")
            }
        )

        syncEngine = SyncEngine(this, nearbyManager)

        // Start mesh
        nearbyManager.startAdvertising("EVAC-${android.os.Build.MODEL}")
        nearbyManager.startDiscovery()

        // ── Start location updates for proximity detection ────────────────────
        startLocationUpdates()

        // Start gateway sync (safe – won't crash if Firebase unavailable)
        try {
            gatewayManager = GatewayManager(this)
            gatewayManager?.startPeriodicSync(scope)
        } catch (e: Exception) {
            Log.w(TAG, "GatewayManager init failed – cloud sync disabled: $e")
        }

        // Periodic purge of expired messages
        scope.launch {
            while (true) {
                delay(MeshConstants.REBROADCAST_INTERVAL_MS)
                syncEngine.purgeExpired()
            }
        }

        Log.d(TAG, "MeshService started")
    }

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permission not granted — proximity detection disabled")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 15_000L // every 15 seconds
        ).apply {
            setMinUpdateDistanceMeters(10f) // only update if moved 10m
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, Looper.getMainLooper()
        )

        Log.d(TAG, "Location updates started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.stopAll()
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        scope.cancel()
        Log.d(TAG, "MeshService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            MeshConstants.NOTIFICATION_CHANNEL_ID,
            MeshConstants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, MeshConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("EVAC Mesh Active")
            .setContentText("Offline network running — proximity alerts enabled")
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setOngoing(true)
            .build()
    }
}
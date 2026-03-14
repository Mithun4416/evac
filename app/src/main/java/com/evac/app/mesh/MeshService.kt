package com.evac.app.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.evac.app.R
import com.evac.app.gateway.GatewayManager
import kotlinx.coroutines.*

class MeshService : Service() {

    private val TAG = "MeshService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var nearbyManager: NearbyManager
    private lateinit var syncEngine: SyncEngine
    private var gatewayManager: GatewayManager? = null

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.stopAll()
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
            .setContentText("Offline network running")
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setOngoing(true)
            .build()
    }
}
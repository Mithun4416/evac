package com.evac.app.mesh

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.evac.app.R
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.gateway.GatewayManager
import com.evac.app.model.SosMessage
import com.evac.app.util.DeviceFingerprint
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import com.evac.app.mesh.ExtendedRangeManager

/**
 * MeshService — foreground service keeping the mesh alive.
 *
 * Wires:
 *   NearbyManager → SyncEngine → Room DB → relay → NearbyManager
 *   GatewayManager → Firestore ↔ Room DB ↔ mesh
 *
 * Also runs:
 *   - TTL cleanup job every 30 minutes
 *   - Delta sync on every peer connection
 */
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        private const val CHANNEL_ID = "evac_mesh_channel"
        private const val NOTIFICATION_ID = 1
        private const val TTL_CLEANUP_INTERVAL_MS = 30 * 60 * 1000L // 30 min

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    private val binder = MeshBinder()
    private lateinit var nearbyManager: NearbyManager
    private lateinit var syncEngine: SyncEngine
    private lateinit var gatewayManager: GatewayManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ttlCleanupJob: Job? = null

    // Callback for UI to listen for new messages
    var onNewMessage: ((MessageEntity) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

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
        Log.i(TAG, "MeshService created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Initialize DB + engines
        val dao = AppDatabase.getInstance(applicationContext).messageDao()
        syncEngine = SyncEngine(dao)
        nearbyManager = NearbyManager(applicationContext)

        // ---- Wire SyncEngine delta sync callback ----
        // When SyncEngine determines messages are missing on a peer,
        // it calls this to send them via NearbyManager
        syncEngine.onSendMissingMessages = { endpointId, payloads ->
            for (payload in payloads) {
                nearbyManager.sendPayload(endpointId, payload)
            }
            Log.d(TAG, "Sent ${payloads.size} missing message(s) to $endpointId")
        }

        // ---- Extended Range Aggressive Rebroadcast ----
        serviceScope.launch {
            while (isActive) {
                if (ExtendedRangeManager.isExtendedModeActive) {
                    val endpoints = nearbyManager.getConnectedEndpoints()
                    if (endpoints.isNotEmpty()) {
                        val idListPayload = syncEngine.buildIdListPayload()
                        Log.d(TAG, "Aggressive Rebroadcast: Syncing via ${endpoints.size} peers.")
                        for (ep in endpoints) {
                            nearbyManager.sendPayload(ep, idListPayload)
                        }
                    }
                }
                delay(30_000L) // 30 seconds interval for extended mode
            }
        }

        // ── Start location updates for proximity detection ────────────────────
        startLocationUpdates()
        // ---- Wire NearbyManager → SyncEngine → relay ----
        nearbyManager.messageListener = object : NearbyManager.MessageListener {
            override fun onMessageReceived(endpointId: String, data: ByteArray) {
                serviceScope.launch {
                    val newMessages = syncEngine.handleIncomingPayload(data, endpointId)

                    for (entity in newMessages) {
                        Log.i(TAG, "New ${entity.type} from mesh: ${entity.id}")

                        // Notify UI
                        onNewMessage?.invoke(entity)

                        // Relay to other peers with incremented hop_count
                        val relayBytes = syncEngine.prepareForRelay(entity)
                        if (relayBytes != null) {
                            nearbyManager.broadcastPayload(relayBytes)
                            Log.d(TAG, "Relayed ${entity.id} to ${nearbyManager.getConnectedEndpoints().size} peer(s)")
                        }
                    }
                }
            }

            override fun onPeerConnected(endpointId: String) {
                Log.i(TAG, "Peer connected: $endpointId — starting delta sync")
                serviceScope.launch {
                    val idListPayload = syncEngine.buildIdListPayload()
                    nearbyManager.sendPayload(endpointId, idListPayload)
                }
            }

            override fun onPeerDisconnected(endpointId: String) {
                Log.w(TAG, "Peer disconnected: $endpointId")
            }
        }

        // ---- Initialize Gateway (Firebase bridge) ----
        gatewayManager = GatewayManager(applicationContext)
        gatewayManager.onNewMessageFromCloud = { entity ->
            serviceScope.launch {
                val relayBytes = syncEngine.prepareForRelay(entity)
                if (relayBytes != null) {
                    nearbyManager.broadcastPayload(relayBytes)
                    Log.d(TAG, "Relayed cloud message ${entity.id} into mesh")
                }
            }
        }
        gatewayManager.start()

        // ---- Start TTL cleanup job (every 30 min) ----
        startTtlCleanup()

        Log.i(TAG, "MeshService fully initialized")
        
        // Try starting the mesh if permissions are already granted (e.g. app restart)
        startMeshNetwork()
    }

    private fun startMeshNetwork() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        var hasBluetooth = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                           ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                           ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }

        var hasWifi = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasWifi = ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        }

        if ((hasFine || hasCoarse) && hasBluetooth && hasWifi) {
            val localName = "evac_${DeviceFingerprint.getId(applicationContext).take(8)}"
            nearbyManager.startAdvertisingAndDiscovery(localName)
            Log.i(TAG, "Mesh network started securely as '$localName'")
        } else {
            Log.w(TAG, "Cannot start mesh network: Missing required permissions")
        }
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
        if (intent?.action == "ACTION_START_MESH") {
            Log.i(TAG, "Received ACTION_START_MESH")
            startMeshNetwork()
            startLocationUpdates() // Also kickstart location if permissions just got granted
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ttlCleanupJob?.cancel()
        nearbyManager.stopAll()
        gatewayManager.stop()
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        serviceScope.cancel()
        Log.i(TAG, "MeshService destroyed")
    }

    // ------------------------------------------------------------------ //
    //               Public API — used by UI (CitizenViewModel)            //
    // ------------------------------------------------------------------ //

    /**
     * Inject a locally-created message into the mesh.
     * Inserts into Room DB, then broadcasts to all connected peers.
     */
    fun sendMessage(entity: MessageEntity) {
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).messageDao()
            dao.insert(entity)
            Log.i(TAG, "Inserted local ${entity.type}: ${entity.id}")

            val json = syncEngine.entityToJson(entity)
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            nearbyManager.broadcastPayload(bytes)
            Log.i(TAG, "Broadcast ${entity.type} to mesh (${bytes.size} bytes)")
        }
    }

    fun sendSos(sosMessage: SosMessage) {
        sendMessage(sosMessage.toEntity())
    }

    // ------------------------------------------------------------------ //
    //                       TTL Cleanup                                   //
    // ------------------------------------------------------------------ //

    private fun startTtlCleanup() {
        ttlCleanupJob = serviceScope.launch {
            while (isActive) {
                delay(TTL_CLEANUP_INTERVAL_MS)
                try {
                    syncEngine.cleanupExpiredMessages()
                    Log.d(TAG, "TTL cleanup completed")
                } catch (e: Exception) {
                    Log.e(TAG, "TTL cleanup failed", e)
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //                       Notification                                  //
    // ------------------------------------------------------------------ //

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Evac Mesh Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the mesh network active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EVAC Mesh Active")
            .setContentText("Offline network running — proximity alerts enabled")
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setOngoing(true)
            .build()
    }
}

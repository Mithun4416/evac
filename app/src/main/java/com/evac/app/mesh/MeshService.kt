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
import com.evac.app.db.AckEntity
import com.evac.app.db.AppDatabase
import com.evac.app.db.BulletinEntity
import com.evac.app.db.MessageEntity
import com.evac.app.gateway.GatewayManager
import com.evac.app.model.SosMessage
import com.evac.app.util.DeviceFingerprint
import com.google.android.gms.location.*
import kotlinx.coroutines.*

/**
 * MeshService — foreground service keeping the mesh alive.
 *
 * Wires TWO data pipelines:
 *
 *   Pipeline 1 (Legacy — SOS messages):
 *     NearbyManager → SyncEngine → messages table → relay → NearbyManager
 *
 *   Pipeline 2 (Reverse Mesh — Bulletins + ACKs):
 *     NearbyManager → EvacRepository → acks/bulletins tables → relay → NearbyManager
 *     GatewayManager → EvacRepository → acks/bulletins tables → relay → NearbyManager
 *
 * The key insight: when bytes arrive from a peer, we try BOTH pipelines.
 * SyncEngine handles SOS/legacy format; EvacRepository handles Reverse Mesh format.
 * This ensures backward compatibility while enabling the new relay-based routing.
 */
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        private const val CHANNEL_ID = "evac_mesh_channel"
        private const val ALERT_CHANNEL_ID = "evac_alerts_channel"
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
    private lateinit var evacRepository: EvacRepository
    private lateinit var localDeviceId: String
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ttlCleanupJob: Job? = null

    // Callback for UI to listen for new messages (legacy SOS pipeline)
    var onNewMessage: ((MessageEntity) -> Unit)? = null

    // Callback for UI to listen for Reverse Mesh events (Bulletins + targeted ACKs)
    var onReverseMeshResult: ((EvacRepository.IncomingResult) -> Unit)? = null

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

        localDeviceId = DeviceFingerprint.getId(applicationContext)

        // Initialize DB + engines
        val database = AppDatabase.getInstance(applicationContext)
        val messageDao = database.messageDao()
        val evacDao = database.evacDao()

        syncEngine = SyncEngine(messageDao)
        nearbyManager = NearbyManager(applicationContext)

        // ═══════════════════════════════════════════════════════════════════
        // 🚨 THE CRITICAL WIRING: Create the MeshNetworkBroadcaster that
        //    wraps NearbyManager.broadcastPayload(). This is what EvacRepository
        //    calls after every successful non-duplicate insert to RELAY data.
        // ═══════════════════════════════════════════════════════════════════
        val broadcaster = object : MeshNetworkBroadcaster {
            override fun broadcast(data: ByteArray) {
                nearbyManager.broadcastPayload(data)
            }
        }
        evacRepository = EvacRepository(evacDao, broadcaster)
        Log.i(TAG, "✅ EvacRepository wired with MeshNetworkBroadcaster → relay is ACTIVE")

        // ---- Wire SyncEngine delta sync callback (legacy SOS) ----
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
                delay(30_000L)
            }
        }

        // ── Start location updates for proximity detection ────────────────
        startLocationUpdates()

        // ═══════════════════════════════════════════════════════════════════
        // THE DUAL-PIPELINE MESSAGE LISTENER
        //
        // When bytes arrive from a mesh peer, we need to route them to the
        // correct pipeline. The discriminator is the "payload_type" field:
        //   - If present ("ACK"/"BULLETIN") → Reverse Mesh → EvacRepository
        //   - If absent (has "type":"SOS" etc) → Legacy → SyncEngine
        // ═══════════════════════════════════════════════════════════════════
        nearbyManager.messageListener = object : NearbyManager.MessageListener {
            override fun onMessageReceived(endpointId: String, data: ByteArray) {
                serviceScope.launch {
                    // Try to detect if this is a Reverse Mesh payload
                    val isReverseMesh = isReverseMeshPayload(data)

                    if (isReverseMesh) {
                        // ── Reverse Mesh Pipeline ────────────────────────
                        // EvacRepository handles:
                        //   1. Deserialize
                        //   2. Insert with IGNORE dedup
                        //   3. If NEW → broadcaster.broadcast(bytes) ← THE RELAY
                        //   4. Return result for UI
                        val result = evacRepository.handleIncomingOfflinePayload(data, localDeviceId)
                        Log.i(TAG, "Reverse Mesh payload from $endpointId → $result")

                        // Trigger High-Priority Push Notification
                        when (result) {
                            is EvacRepository.IncomingResult.NewBulletin -> {
                                showHeadsUpNotification("Emergency Broadcast", result.bulletin.message)
                            }
                            is EvacRepository.IncomingResult.NewTargetedAck -> {
                                showHeadsUpNotification("Rescuer Response Received", result.ack.message)
                            }
                            else -> {}
                        }

                        // Notify UI
                        onReverseMeshResult?.invoke(result)
                    } else {
                        // ── Legacy SOS Pipeline ──────────────────────────
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
            }

            override fun onPeerConnected(endpointId: String) {
                Log.i(TAG, "Peer connected: $endpointId — starting FULL sync")
                serviceScope.launch {
                    // Legacy delta sync (SOS messages)
                    val idListPayload = syncEngine.buildIdListPayload()
                    nearbyManager.sendPayload(endpointId, idListPayload)

                    // ═════════════════════════════════════════════════════
                    // 🚨 REVERSE MESH DELTA SYNC — push all active ACKs
                    //    and Bulletins to the newly connected peer.
                    //    Without this, the peer would have to wait for a
                    //    re-broadcast to receive existing messages.
                    // ═════════════════════════════════════════════════════
                    evacRepository.syncAllToPeer(endpointId) { ep, bytes ->
                        nearbyManager.sendPayload(ep, bytes)
                    }
                }
            }

            override fun onPeerDisconnected(endpointId: String) {
                Log.w(TAG, "Peer disconnected: $endpointId")
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // GATEWAY: Cloud → Mesh Bridge (Bulletins + ACKs from Firebase)
        //
        // When GatewayManager downloads a BULLETIN or ACK from Firestore,
        // we ALSO insert it into the Reverse Mesh tables and relay it.
        // This is the Cloud-to-Mesh bridge that was MISSING before.
        // ═══════════════════════════════════════════════════════════════════
        gatewayManager = GatewayManager(applicationContext)
        gatewayManager.onNewMessageFromCloud = { entity ->
            serviceScope.launch {
                if (entity.type == "SOS") {
                    // Legacy relay (only for SOS pipeline)
                    val relayBytes = syncEngine.prepareForRelay(entity)
                    if (relayBytes != null) {
                        nearbyManager.broadcastPayload(relayBytes)
                        Log.d(TAG, "Relayed cloud message ${entity.id} into legacy mesh")
                    }
                }

                // ═════════════════════════════════════════════════════════
                // 🚨 THE CLOUD-TO-MESH BRIDGE FIX:
                // When a Bulletin/ACK arrives from Firestore, convert it
                // to an AckEntity/BulletinEntity, insert into the Reverse
                // Mesh tables, and broadcast to offline peers.
                // ═════════════════════════════════════════════════════════
                when (entity.type) {
                    "BULLETIN" -> {
                        val now = System.currentTimeMillis()
                        val bulletin = BulletinEntity(
                            uuid = entity.id,
                            message = entity.body ?: "",
                            timestamp = entity.timestamp,
                            expiresAt = now + (entity.ttlHours * 60 * 60 * 1000L)
                        )
                        val rowId = evacRepository.evacDao.insertBulletin(bulletin)
                        if (rowId != -1L) {
                            val bytes = MeshPayloadSerializer.serialize(bulletin)
                            nearbyManager.broadcastPayload(bytes)
                            Log.i(TAG, "☁️→📡 Cloud BULLETIN ${entity.id} → Reverse Mesh relay")
                            
                            showHeadsUpNotification("Emergency Broadcast", bulletin.message)
                            
                            onReverseMeshResult?.invoke(EvacRepository.IncomingResult.NewBulletin(bulletin))
                        }
                    }
                    "ACK" -> {
                        val now = System.currentTimeMillis()
                        val ack = AckEntity(
                            uuid = entity.id,
                            message = entity.body ?: "",
                            timestamp = entity.timestamp,
                            expiresAt = now + (entity.ttlHours * 60 * 60 * 1000L),
                            targetDeviceId = entity.targetDeviceId ?: ""
                        )
                        val rowId = evacRepository.evacDao.insertAck(ack)
                        if (rowId != -1L) {
                            val bytes = MeshPayloadSerializer.serialize(ack)
                            nearbyManager.broadcastPayload(bytes)
                            Log.i(TAG, "☁️→📡 Cloud ACK ${entity.id} → Reverse Mesh relay")

                            // UI: only notify if targeted at this device
                            if (ack.targetDeviceId == localDeviceId) {
                                showHeadsUpNotification("Rescuer Response Received", ack.message)
                                onReverseMeshResult?.invoke(EvacRepository.IncomingResult.NewTargetedAck(ack))
                            }
                        }
                    }
                }
            }
        }
        gatewayManager.start()

        // ---- Start TTL cleanup job (every 30 min) ----
        startTtlCleanup()

        Log.i(TAG, "MeshService fully initialized — BOTH pipelines active")

        // Try starting the mesh if permissions are already granted
        startMeshNetwork()
    }

    /**
     * Detect whether incoming bytes are a Reverse Mesh payload.
     * Reverse Mesh payloads have "payload_type" field ("ACK"/"BULLETIN").
     * Legacy payloads have "type" field ("SOS"/"BULLETIN"/"ACK") + no "payload_type".
     */
    private fun isReverseMeshPayload(data: ByteArray): Boolean {
        return try {
            val json = org.json.JSONObject(String(data, Charsets.UTF_8))
            json.has("payload_type")
        } catch (_: Exception) {
            false
        }
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
            Priority.PRIORITY_HIGH_ACCURACY, 15_000L
        ).apply {
            setMinUpdateDistanceMeters(10f)
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
            startLocationUpdates()
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

    /** Expose the EvacRepository for direct access by ViewModels. */
    fun getEvacRepository(): EvacRepository = evacRepository

    // ------------------------------------------------------------------ //
    //                       TTL Cleanup                                   //
    // ------------------------------------------------------------------ //

    private fun startTtlCleanup() {
        ttlCleanupJob = serviceScope.launch {
            while (isActive) {
                delay(TTL_CLEANUP_INTERVAL_MS)
                try {
                    // Legacy cleanup
                    syncEngine.cleanupExpiredMessages()
                    // Reverse Mesh cleanup
                    evacRepository.purgeExpired()
                    Log.d(TAG, "TTL cleanup completed (both pipelines)")
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
            
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority offline mesh alerts"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun showHeadsUpNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
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

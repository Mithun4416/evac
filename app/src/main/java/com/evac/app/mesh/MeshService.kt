package com.evac.app.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.evac.app.R
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.gateway.GatewayManager
import com.evac.app.model.SosMessage
import com.evac.app.util.DeviceFingerprint
import kotlinx.coroutines.*

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

        // Start advertising + discovery
        val localName = "evac_${DeviceFingerprint.getId(applicationContext).take(8)}"
        nearbyManager.startAdvertisingAndDiscovery(localName)

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

        Log.i(TAG, "MeshService fully initialized as '$localName'")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ttlCleanupJob?.cancel()
        nearbyManager.stopAll()
        gatewayManager.stop()
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
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Evac Mesh Active")
            .setContentText("Scanning for nearby devices…")
            .setSmallIcon(R.drawable.ic_evac_logo)
            .setOngoing(true)
            .build()
    }
}

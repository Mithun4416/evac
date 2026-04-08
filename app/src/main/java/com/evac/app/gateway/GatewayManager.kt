package com.evac.app.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.mesh.SyncEngine
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*

/**
 * GatewayManager — bridges the offline mesh to Firebase Firestore.
 *
 * When internet is available:
 *   - Uploads unsynced local messages to Firestore
 *   - Listens for new Firestore documents and inserts them into Room
 *
 * Auto-activates when internet is detected (any phone can be a Gateway).
 */
class GatewayManager(private val context: Context) {

    companion object {
        private const val TAG = "GatewayManager"
        private const val COLLECTION_MESSAGES = "mesh_messages"
        private const val SYNC_INTERVAL_MS = 10_000L // 10 seconds for real-time dashboard
    }

    private val db = FirebaseFirestore.getInstance()
    private val dao = AppDatabase.getInstance(context).messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bulletinsListener: ListenerRegistration? = null
    private var acksListener: ListenerRegistration? = null
    private var syncJob: Job? = null

    // Callback to inject downloaded messages into the mesh
    var onNewMessageFromCloud: ((MessageEntity) -> Unit)? = null

    /**
     * Start the gateway — begins periodic upload + realtime download.
     */
    fun start() {
        Log.i(TAG, "Gateway starting...")
        startPeriodicUpload()
        startRealtimeDownload()
    }

    fun stop() {
        syncJob?.cancel()
        bulletinsListener?.remove()
        acksListener?.remove()
        scope.cancel()
        Log.i(TAG, "Gateway stopped")
    }

    // ------------------------------------------------------------------ //
    //               UPLOAD: Room → Firestore                              //
    // ------------------------------------------------------------------ //

    private fun startPeriodicUpload() {
        syncJob = scope.launch {
            while (isActive) {
                if (hasInternet()) {
                    uploadUnsynced()
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun uploadUnsynced() {
        try {
            val unsynced = dao.getUnsynced()
            if (unsynced.isEmpty()) return

            Log.i(TAG, "Uploading ${unsynced.size} unsynced message(s) to Firestore")

            for (entity in unsynced) {
                val data = entityToMap(entity)
                
                // Route to the correct dashboard collection based on message type
                val collectionName = when (entity.type) {
                    "SOS" -> "sos_messages"
                    "BULLETIN" -> "bulletins"
                    "ACK" -> "acks"
                    else -> "mesh_messages"
                }

                db.collection(collectionName)
                    .document(entity.id)
                    .set(data)
                    .addOnSuccessListener {
                        scope.launch {
                            dao.markSynced(entity.id)
                            Log.d(TAG, "Uploaded to $collectionName & marked synced: ${entity.id}")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to upload ${entity.id}", e)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload batch failed", e)
        }
    }

    // ------------------------------------------------------------------ //
    //               DOWNLOAD: Firestore → Room                            //
    // ------------------------------------------------------------------ //

    private fun startRealtimeDownload() {
        // 1. Listen for new Bulletins
        bulletinsListener = db.collection("bulletins")
            .addSnapshotListener { snapshots, error ->
                handleIncomingCloudData(snapshots, error)
            }

        // 2. Listen for new ACKs
        acksListener = db.collection("acks")
            .addSnapshotListener { snapshots, error ->
                handleIncomingCloudData(snapshots, error)
            }
    }

    private fun handleIncomingCloudData(snapshots: com.google.firebase.firestore.QuerySnapshot?, error: com.google.firebase.firestore.FirebaseFirestoreException?) {
        if (error != null) {
            Log.e(TAG, "Firestore listener error", error)
            return
        }

        snapshots?.documentChanges?.forEach { change ->
            if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                val doc = change.document
                val entity = mapToEntity(doc.id, doc.data)

                scope.launch {
                    val existing = dao.getById(entity.id)
                    if (existing == null) {
                        dao.insert(entity)
                        Log.i(TAG, "Downloaded from cloud: ${entity.type} ${entity.id}")
                        onNewMessageFromCloud?.invoke(entity)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //                    Conversion Helpers                                //
    // ------------------------------------------------------------------ //

    private fun entityToMap(e: MessageEntity): Map<String, Any?> = mapOf(
        "id" to e.id,
        "type" to e.type,
        "timestamp" to e.timestamp,
        "hop_count" to e.hopCount,
        "max_hops" to e.maxHops,
        "ttl_hours" to e.ttlHours,
        "hash" to e.hash,
        "status" to e.status,
        "device_id" to e.deviceId,
        "lat" to e.lat,
        "lng" to e.lng,
        "accuracy_m" to e.accuracyM,
        "people_count" to e.peopleCount,
        "battery_pct" to e.batteryPct,
        "note" to e.note,
        "phrase_key" to e.phraseKey,
        "is_volume_sos" to e.isVolumeSos,
        "assigned_to" to e.assignedTo,
        "alert_type" to e.alertType,
        "body" to e.body,
        "zone_lat" to e.zoneLat,
        "zone_lng" to e.zoneLng,
        "zone_radius_km" to e.zoneRadiusKm,
        "target_device_id" to e.targetDeviceId,
        "signature" to e.signature
    )

    private fun mapToEntity(id: String, data: Map<String, Any?>): MessageEntity = MessageEntity(
        id = id,
        type = (data["type"] as? String) ?: "SOS",
        timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
        hopCount = ((data["hop_count"] as? Long) ?: (data["hopCount"] as? Long))?.toInt() ?: 0,
        maxHops = ((data["max_hops"] as? Long) ?: (data["maxHops"] as? Long))?.toInt() ?: 10,
        ttlHours = ((data["ttl_hours"] as? Long) ?: (data["ttlHours"] as? Long))?.toInt() ?: 24,
        hash = (data["hash"] as? String) ?: "",
        status = data["status"] as? String,
        deviceId = (data["device_id"] as? String) ?: (data["deviceId"] as? String),
        lat = data["lat"] as? Double,
        lng = data["lng"] as? Double,
        accuracyM = (data["accuracy_m"] as? Double)?.toFloat(),
        peopleCount = ((data["people_count"] as? Long) ?: (data["peopleCount"] as? Long))?.toInt(),
        batteryPct = ((data["battery_pct"] as? Long) ?: (data["batteryPct"] as? Long))?.toInt(),
        note = data["note"] as? String,
        phraseKey = data["phrase_key"] as? String,
        isVolumeSos = (data["is_volume_sos"] as? Boolean) ?: (data["isVolumeSos"] as? Boolean) ?: false,
        assignedTo = (data["assigned_to"] as? String) ?: (data["assignedTo"] as? String),
        alertType = (data["alert_type"] as? String) ?: (data["alertType"] as? String),
        body = data["body"] as? String,
        zoneLat = data["zone_lat"] as? Double,
        zoneLng = data["zone_lng"] as? Double,
        zoneRadiusKm = data["zone_radius_km"] as? Double,
        targetDeviceId = (data["target_device_id"] as? String) ?: (data["targetDeviceId"] as? String),
        signature = data["signature"] as? String,
        syncedToFirebase = true // came from Firebase, already synced
    )

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

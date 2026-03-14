package com.evac.app.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GatewayManager(private val context: Context) {

    private val TAG = "GatewayManager"
    private val database = AppDatabase.getDatabase(context)
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "Firebase not configured – cloud sync disabled: $e")
        null
    }

    // ── Start periodic sync every 5 minutes ──────────────────────
    fun startPeriodicSync(scope: CoroutineScope) {
        if (firestore == null) return
        scope.launch {
            while (true) {
                delay(300_000L) // 5 minutes
                if (isConnected()) {
                    uploadToCloud()
                    listenForAcksAndBulletins()
                }
            }
        }
    }

    // ── Upload all local SOS messages to Firestore ────────────────
    suspend fun uploadToCloud() {
        val fs = firestore ?: return
        try {
            val messages = database.messageDao()
                .getSosMessages()
                .first()

            messages.forEach { message ->
                val data = hashMapOf(
                    "id"            to message.id,
                    "type"          to message.type,
                    "status"        to message.status,
                    "device_id"     to message.deviceId,
                    "timestamp"     to message.timestamp,
                    "lat"           to message.lat,
                    "lng"           to message.lng,
                    "people_count"  to message.peopleCount,
                    "battery_pct"   to message.batteryPct,
                    "note"          to message.note,
                    "hop_count"     to message.hopCount,
                    "is_volume_sos" to message.isVolumeSos
                )

                fs.collection("sos_messages")
                    .document(message.id)
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Uploaded: ${message.id}")
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Upload failed: $it")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadToCloud error: $e")
        }
    }

    // ── Listen for ACKs and Bulletins from Firestore ──────────────
    fun listenForAcksAndBulletins() {
        val fs = firestore ?: return
        // Listen for ACKs
        fs.collection("acks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "ACK listener error: $error")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val message = MessageEntity(
                            id             = data["id"] as? String ?: return@forEach,
                            type           = "ACK",
                            status         = null,
                            deviceId       = "",
                            timestamp      = data["timestamp"] as? Long
                                ?: System.currentTimeMillis(),
                            ttlHours       = 6,
                            hopCount       = 0,
                            maxHops        = 10,
                            lat            = null,
                            lng            = null,
                            accuracyM      = null,
                            peopleCount    = null,
                            batteryPct     = null,
                            note           = null,
                            phraseKey      = null,
                            isVolumeSos    = null,
                            hash           = "gateway",
                            body           = data["body"] as? String,
                            targetDeviceId = data["targetDeviceId"] as? String,
                            signature      = data["signature"] as? String
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            database.messageDao().insert(message)
                            Log.d(TAG, "Saved ACK: ${message.id}")
                        }
                    }
                }
            }

        // Listen for Bulletins
        fs.collection("bulletins")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Bulletin listener error: $error")
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val message = MessageEntity(
                            id             = data["id"] as? String ?: return@forEach,
                            type           = "BULLETIN",
                            status         = null,
                            deviceId       = "",
                            timestamp      = data["timestamp"] as? Long
                                ?: System.currentTimeMillis(),
                            ttlHours       = 12,
                            hopCount       = 0,
                            maxHops        = 10,
                            lat            = null,
                            lng            = null,
                            accuracyM      = null,
                            peopleCount    = null,
                            batteryPct     = null,
                            note           = null,
                            phraseKey      = null,
                            isVolumeSos    = null,
                            hash           = "gateway",
                            body           = data["body"] as? String,
                            targetDeviceId = null,
                            signature      = data["signature"] as? String
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            database.messageDao().insert(message)
                            Log.d(TAG, "Saved Bulletin: ${message.id}")
                        }
                    }
                }
            }
    }

    // ── Check internet connectivity ───────────────────────────────
    fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
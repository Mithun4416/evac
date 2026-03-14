package com.evac.app.mesh

import android.content.Context
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncEngine(
    private val context: Context,
    private val nearbyManager: NearbyManager
) {

    private val TAG = "SyncEngine"
    private val database = AppDatabase.getDatabase(context)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    data class SyncPacket(
        val type: String,       // "ID_LIST" or "MESSAGES"
        val ids: List<String>?, // for ID_LIST
        val messages: List<MessageEntity>? // for MESSAGES
    )

    // ── Called when peer connects — exchange ID lists ─────────────
    fun onPeerConnected(endpointId: String) {
        scope.launch {
            val ids = database.messageDao().getAllIds()
            val packet = SyncPacket(
                type = "ID_LIST",
                ids = ids,
                messages = null
            )
            nearbyManager.sendTo(endpointId, gson.toJson(packet))
            Log.d(TAG, "Sent ID list (${ids.size} IDs) to $endpointId")
        }
    }

    // ── Called when message received from peer ────────────────────
    fun onMessageReceived(json: String) {
        scope.launch {
            try {
                val packet = gson.fromJson(json, SyncPacket::class.java)

                when (packet.type) {
                    "ID_LIST" -> handleIdList(packet.ids ?: emptyList())
                    "MESSAGES" -> handleMessages(packet.messages ?: emptyList())
                    else -> Log.w(TAG, "Unknown packet type: ${packet.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $e")
            }
        }
    }

    // ── Peer sent us their ID list — find what they're missing ────
    private suspend fun handleIdList(theirIds: List<String>) {
        val ourIds = database.messageDao().getAllIds().toSet()
        val theyNeed = ourIds - theirIds.toSet()

        if (theyNeed.isEmpty()) {
            Log.d(TAG, "Peer is up to date")
            return
        }

        // Get messages they need, filter by hop count
        val toSend = database.messageDao()
            .getMessagesNotIn(theirIds)
            .filter { it.hopCount < it.maxHops }
            .map { it.copy(hopCount = it.hopCount + 1) } // increment hop

        val packet = SyncPacket(
            type = "MESSAGES",
            ids = null,
            messages = toSend
        )
        nearbyManager.broadcast(gson.toJson(packet))
        Log.d(TAG, "Sent ${toSend.size} missing messages")
    }

    // ── Peer sent us messages — save new ones ─────────────────────
    private suspend fun handleMessages(messages: List<MessageEntity>) {
        val now = System.currentTimeMillis()

        val valid = messages.filter { msg ->
            // Check TTL not expired
            val expiryMs = msg.ttlHours * 3_600_000L
            val age = now - msg.timestamp
            age < expiryMs && msg.hopCount <= msg.maxHops
        }

        database.messageDao().insertAll(valid)
        Log.d(TAG, "Saved ${valid.size} new messages from peer")
    }

    // ── Purge expired messages ────────────────────────────────────
    fun purgeExpired() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - (24 * 3_600_000L)
            database.messageDao().deleteExpired(cutoff)
            Log.d(TAG, "Purged expired messages")
        }
    }
}
package com.evac.app.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyManager(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit,
    private val onPeerConnected: (String) -> Unit,
    private val onPeerDisconnected: (String) -> Unit
) {

    private val TAG = "NearbyManager"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableSetOf<String>()

    // ── Advertising (so others can find us) ──────────────────────
    fun startAdvertising(deviceName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            deviceName,
            MeshConstants.SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener {
            Log.e(TAG, "Advertising failed: $it")
        }
    }

    // ── Discovery (finding other nodes) ──────────────────────────
    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            MeshConstants.SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener {
            Log.e(TAG, "Discovery failed: $it")
        }
    }

    // ── Send message to all connected peers ───────────────────────
    fun broadcast(payload: String) {
        val bytes = Payload.fromBytes(payload.toByteArray())
        connectedEndpoints.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, bytes)
        }
    }

    // ── Send to specific endpoint ──────────────────────────────────
    fun sendTo(endpointId: String, payload: String) {
        val bytes = Payload.fromBytes(payload.toByteArray())
        connectionsClient.sendPayload(endpointId, bytes)
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
    }

    // ── Callbacks ─────────────────────────────────────────────────
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept all connections for mesh
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            Log.d(TAG, "Connection initiated: $endpointId")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                onPeerConnected(endpointId)
                Log.d(TAG, "Connected: $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            onPeerDisconnected(endpointId)
            Log.d(TAG, "Disconnected: $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId")
            connectionsClient.requestConnection(
                "EVAC-Node",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val message = String(bytes)
                Log.d(TAG, "Message received from $endpointId")
                onMessageReceived(message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Optional: track transfer progress
        }
    }
}
package com.evac.app.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

/**
 * Wraps Google Nearby Connections API.
 * Uses P2P_CLUSTER strategy — supports many-to-many connections.
 *
 * Both advertises AND discovers simultaneously so every device is
 * both a sender and receiver.
 */
class NearbyManager(private val context: Context) {

    companion object {
        private const val TAG = "NearbyManager"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableSetOf<String>()

    var messageListener: MessageListener? = null

    // ------------------------------------------------------------------ //
    //                        Public API                                   //
    // ------------------------------------------------------------------ //

    fun startAdvertisingAndDiscovery(localName: String) {
        startAdvertising(localName)
        startDiscovery()
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        Log.d(TAG, "Stopped all Nearby Connections")
    }

    /** Send bytes to a specific connected endpoint. */
    fun sendPayload(endpointId: String, data: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(data))
            .addOnSuccessListener { Log.d(TAG, "Sent payload to $endpointId (${data.size} bytes)") }
            .addOnFailureListener { Log.e(TAG, "Failed to send to $endpointId", it) }
    }

    /** Broadcast bytes to ALL connected endpoints. */
    fun broadcastPayload(data: ByteArray) {
        val endpoints = connectedEndpoints.toList()
        Log.d(TAG, "Broadcasting to ${endpoints.size} endpoint(s)")
        for (ep in endpoints) {
            sendPayload(ep, data)
        }
    }

    fun getConnectedEndpoints(): Set<String> = connectedEndpoints.toSet()

    // ------------------------------------------------------------------ //
    //                      Advertising                                    //
    // ------------------------------------------------------------------ //

    private fun startAdvertising(localName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localName,
            MeshConstants.SERVICE_ID,
            connectionLifecycleCallback,
            options
        )
            .addOnSuccessListener { Log.i(TAG, "Advertising started as '$localName'") }
            .addOnFailureListener { Log.e(TAG, "Advertising failed", it) }
    }

    // ------------------------------------------------------------------ //
    //                       Discovery                                     //
    // ------------------------------------------------------------------ //

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            MeshConstants.SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )
            .addOnSuccessListener { Log.i(TAG, "Discovery started") }
            .addOnFailureListener { Log.e(TAG, "Discovery failed", it) }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "Endpoint discovered: $endpointId (${info.endpointName})")
            // Auto-connect to every discovered device
            connectionsClient.requestConnection(
                "evac_node",
                endpointId,
                connectionLifecycleCallback
            )
                .addOnSuccessListener { Log.d(TAG, "Requested connection to $endpointId") }
                .addOnFailureListener { Log.e(TAG, "Connection request failed to $endpointId", it) }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.w(TAG, "Endpoint lost: $endpointId")
        }
    }

    // ------------------------------------------------------------------ //
    //                   Connection Lifecycle                               //
    // ------------------------------------------------------------------ //

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "Connection initiated with $endpointId (${info.endpointName})")
            // Auto-accept — mesh trusts all devices
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    Log.i(TAG, "✅ Connected to $endpointId (total: ${connectedEndpoints.size})")
                    messageListener?.onPeerConnected(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                }
                else -> {
                    Log.e(TAG, "Connection failed with $endpointId: ${result.status}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            Log.w(TAG, "Disconnected from $endpointId (remaining: ${connectedEndpoints.size})")
            messageListener?.onPeerDisconnected(endpointId)
        }
    }

    // ------------------------------------------------------------------ //
    //                       Payload Handling                               //
    // ------------------------------------------------------------------ //

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            Log.d(TAG, "Received ${bytes.size} bytes from $endpointId")
            messageListener?.onMessageReceived(endpointId, bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Byte payloads arrive in full — no partial handling needed
        }
    }

    // ------------------------------------------------------------------ //
    //                         Listener Interface                          //
    // ------------------------------------------------------------------ //

    interface MessageListener {
        fun onMessageReceived(endpointId: String, data: ByteArray)
        fun onPeerConnected(endpointId: String)
        fun onPeerDisconnected(endpointId: String)
    }
}

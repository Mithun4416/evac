package com.evac.app.mesh

/**
 * MeshNetworkBroadcaster — abstraction over the physical mesh transport layer.
 *
 * This interface decouples the routing logic (EvacRepository) from the
 * hardware-specific broadcast mechanism (NearbyManager / WiFi Direct).
 *
 * Why an interface?
 *   - EvacRepository needs to call broadcast() after a successful DB insert,
 *     but it must NOT depend on NearbyManager directly (testability, SRP).
 *   - MeshService implements this by delegating to NearbyManager.broadcastPayload().
 *   - For unit tests, a fake implementation can capture broadcast calls.
 */
interface MeshNetworkBroadcaster {

    /**
     * Broadcast raw bytes to ALL currently connected offline mesh peers.
     *
     * This is the Store-and-Forward relay mechanism:
     *   - When a phone receives data and successfully inserts it (not a duplicate),
     *     it MUST call this to push the data to its neighbors.
     *   - Neighbors repeat the same process, creating a flood-fill across the mesh.
     *
     * @param data The serialized payload bytes (from MeshPayloadSerializer).
     */
    fun broadcast(data: ByteArray)
}

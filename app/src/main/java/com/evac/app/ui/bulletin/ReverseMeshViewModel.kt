package com.evac.app.ui.bulletin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AckEntity
import com.evac.app.db.AppDatabase
import com.evac.app.db.BulletinEntity
import com.evac.app.mesh.EvacRepository
import com.evac.app.mesh.MeshNetworkBroadcaster
import com.evac.app.util.DeviceFingerprint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ReverseMeshViewModel — observes the Reverse Mesh tables via Kotlin Flow
 * and routes incoming payloads through [EvacRepository].
 *
 * Observation channels:
 *
 *   1. **activeBulletins** (StateFlow) — live list of non-expired Bulletins.
 *   2. **myAcks** (StateFlow) — live list of ACKs targeted at THIS device.
 *   3. **incomingAckEvent** (SharedFlow) — one-shot event for ACK popup.
 *   4. **incomingBulletinEvent** (SharedFlow) — one-shot event for Bulletin toast.
 *
 * SharedFlow (not StateFlow) for events because:
 *   - No replay on config change → popup won't show again after rotation.
 *   - Each emission consumed exactly once per active collector.
 *
 * The [broadcaster] must be set by MeshService after binding. Without it,
 * incoming payloads will be stored but NOT relayed (defeating the mesh).
 */
class ReverseMeshViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ReverseMeshVM"
    }

    val localDeviceId: String = DeviceFingerprint.getId(application)
    private val database = AppDatabase.getInstance(application)
    private val evacDao = database.evacDao()

    /**
     * The broadcaster is injected by MeshService after the ViewModel is created.
     * MeshService wraps NearbyManager.broadcastPayload() behind this interface.
     *
     * Until setBroadcaster() is called, a no-op stub is used to prevent NPEs.
     * Messages will still be stored (for later delta sync) but won't relay in
     * real-time. MeshService should call setBroadcaster() in onCreate().
     */
    private var broadcaster: MeshNetworkBroadcaster = object : MeshNetworkBroadcaster {
        override fun broadcast(data: ByteArray) {
            Log.w(TAG, "⚠️ Broadcaster not set yet — payload stored but not relayed")
        }
    }

    /** Late-initialized repository — rebuilt when broadcaster is set. */
    var repository: EvacRepository = EvacRepository(evacDao, broadcaster)
        private set

    /**
     * Called by MeshService to inject the real broadcast implementation.
     * This must be called before any payloads are processed.
     */
    fun setBroadcaster(meshBroadcaster: MeshNetworkBroadcaster) {
        broadcaster = meshBroadcaster
        repository = EvacRepository(evacDao, broadcaster)
        Log.i(TAG, "✅ Broadcaster set — relay is now active")
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Flow 1: Live list of active Bulletins (for RecyclerView / list UI)
    // ──────────────────────────────────────────────────────────────────────────

    val activeBulletins: StateFlow<List<BulletinEntity>> =
        evacDao.getActiveBulletins()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────────
    //  Flow 2: Live list of MY ACKs (for ACK history UI)
    // ──────────────────────────────────────────────────────────────────────────

    val myAcks: StateFlow<List<AckEntity>> =
        evacDao.getMyAcks(localDeviceId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────────
    //  One-time events (SharedFlow — no replay on config change)
    // ──────────────────────────────────────────────────────────────────────────

    private val _incomingAckEvent = MutableSharedFlow<AckEntity>(
        extraBufferCapacity = 10
    )
    /** Collect in Fragment → show one-time ACK popup dialog. */
    val incomingAckEvent = _incomingAckEvent.asSharedFlow()

    private val _incomingBulletinEvent = MutableSharedFlow<BulletinEntity>(
        extraBufferCapacity = 10
    )
    /** Collect in Fragment → show one-time Bulletin toast. */
    val incomingBulletinEvent = _incomingBulletinEvent.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────────
    //  Process incoming offline payload from MeshService
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called by MeshService when bytes arrive from an offline mesh peer.
     *
     * The repository handles:
     *   1. Deserialize → Entity
     *   2. Insert with IGNORE dedup
     *   3. If NEW → broadcast to other peers (THE RELAY FIX)
     *   4. Return result → ViewModel emits UI event if needed
     */
    fun processIncomingOffline(bytes: ByteArray) {
        viewModelScope.launch {
            when (val result = repository.handleIncomingOfflinePayload(bytes, localDeviceId)) {
                is EvacRepository.IncomingResult.NewBulletin -> {
                    // StateFlow (activeBulletins) auto-updates via Room.
                    // Also emit one-shot event for toast/snackbar.
                    _incomingBulletinEvent.emit(result.bulletin)
                    Log.i(TAG, "📢 New Bulletin UI event: ${result.bulletin.uuid}")
                }
                is EvacRepository.IncomingResult.NewTargetedAck -> {
                    // StateFlow (myAcks) auto-updates via Room.
                    // Also emit one-shot event for popup dialog.
                    _incomingAckEvent.emit(result.ack)
                    Log.i(TAG, "🎯 Targeted ACK UI event: ${result.ack.uuid}")
                }
                is EvacRepository.IncomingResult.Silent -> {
                    // Data Mule relay-only, or duplicate. No UI action.
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Cloud-to-Mesh bridge
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called when the device has internet and pulls new Bulletins from an API.
     * The repository serializes and broadcasts to mesh peers automatically.
     */
    fun triggerCloudSync() {
        viewModelScope.launch {
            when (val result = repository.handleCloudSync()) {
                is EvacRepository.IncomingResult.NewBulletin -> {
                    _incomingBulletinEvent.emit(result.bulletin)
                    Log.i(TAG, "☁️ Cloud Bulletin synced to mesh: ${result.bulletin.uuid}")
                }
                else -> {
                    // Duplicate or silent — no action
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Garbage Collection
    // ──────────────────────────────────────────────────────────────────────────

    fun purgeExpired() {
        viewModelScope.launch {
            val count = repository.purgeExpired()
            Log.d(TAG, "Manual GC: purged $count expired messages")
        }
    }
}

package com.evac.app.ui.bulletin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AckEntity
import com.evac.app.db.AppDatabase
import com.evac.app.db.BulletinEntity
import com.evac.app.mesh.EvacRepository
import com.evac.app.util.DeviceFingerprint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ReverseMeshViewModel — observes the Reverse Mesh tables via Kotlin Flow.
 *
 * Two observation channels:
 *
 *   1. **activeBulletins** (StateFlow<List<BulletinEntity>>):
 *      Continuously emits the list of all non-expired Bulletins.
 *      The RecyclerView / LazyColumn binds to this for a live feed.
 *
 *   2. **incomingAckEvent** (SharedFlow<AckEntity>):
 *      A one-shot event stream for ACKs targeted at THIS device.
 *      The Fragment/Activity collects this to show a one-time popup/dialog.
 *      SharedFlow (not StateFlow) is used because:
 *        - It doesn't replay old events on config changes.
 *        - Each emission is consumed exactly once per collector.
 *
 * The ViewModel also holds a reference to [EvacRepository] so that
 * MeshService can call processIncoming() to route payloads through
 * the repository, and the ViewModel emits the result.
 */
class ReverseMeshViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ReverseMeshVM"
    }

    private val localDeviceId: String = DeviceFingerprint.getId(application)
    private val database = AppDatabase.getInstance(application)
    private val evacDao = database.evacDao()
    val repository = EvacRepository(evacDao)

    // ──────────────────────────────────────────────────────────────────────────
    //  Flow 1: Live list of active Bulletins (for RecyclerView / list UI)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * StateFlow of all non-expired bulletins, newest first.
     * Eagerly started so data is ready before the UI subscribes.
     */
    val activeBulletins: StateFlow<List<BulletinEntity>> =
        evacDao.getActiveBulletins()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────────
    //  Flow 2: Live list of MY ACKs (for ACK history list UI)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * StateFlow of ACKs targeted at this specific device, newest first.
     */
    val myAcks: StateFlow<List<AckEntity>> =
        evacDao.getMyAcks(localDeviceId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────────
    //  Event: One-time ACK popup (SharedFlow — no replay on config change)
    // ──────────────────────────────────────────────────────────────────────────

    private val _incomingAckEvent = MutableSharedFlow<AckEntity>(
        extraBufferCapacity = 10 // buffer in case UI is briefly paused
    )

    /** Collect this in the Fragment to trigger a one-time popup dialog. */
    val incomingAckEvent = _incomingAckEvent.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────────
    //  Event: One-time Bulletin notification (SharedFlow)
    // ──────────────────────────────────────────────────────────────────────────

    private val _incomingBulletinEvent = MutableSharedFlow<BulletinEntity>(
        extraBufferCapacity = 10
    )

    /** Collect this in the Fragment to trigger a snackbar / toast for new bulletins. */
    val incomingBulletinEvent = _incomingBulletinEvent.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────────
    //  Process incoming payload from MeshService
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called by MeshService when a Reverse Mesh payload arrives.
     * Routes through [EvacRepository.handleIncomingPayload] and emits
     * UI events only when appropriate.
     */
    fun processIncoming(bytes: ByteArray) {
        viewModelScope.launch {
            when (val result = repository.handleIncomingPayload(bytes, localDeviceId)) {
                is EvacRepository.IncomingResult.NewBulletin -> {
                    // StateFlow (activeBulletins) auto-updates via Room.
                    // Also emit a one-shot event for toast/snackbar.
                    _incomingBulletinEvent.emit(result.bulletin)
                    Log.i(TAG, "📢 New bulletin event emitted: ${result.bulletin.uuid}")
                }
                is EvacRepository.IncomingResult.NewTargetedAck -> {
                    // StateFlow (myAcks) auto-updates via Room.
                    // Also emit a one-shot event for popup dialog.
                    _incomingAckEvent.emit(result.ack)
                    Log.i(TAG, "🎯 Targeted ACK event emitted: ${result.ack.uuid}")
                }
                is EvacRepository.IncomingResult.Silent -> {
                    // Data Mule mode / duplicate — no UI action
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Garbage Collection trigger
    // ──────────────────────────────────────────────────────────────────────────

    /** Manually trigger garbage collection (also runs on a periodic schedule). */
    fun purgeExpired() {
        viewModelScope.launch {
            val count = repository.purgeExpired()
            Log.d(TAG, "Manual GC: purged $count expired messages")
        }
    }
}

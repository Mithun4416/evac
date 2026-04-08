package com.evac.app.ui.bulletin

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AckEntity
import com.evac.app.db.AppDatabase
import com.evac.app.db.BulletinEntity
import com.evac.app.mesh.EvacRepository
import com.evac.app.mesh.MeshService
import com.evac.app.util.DeviceFingerprint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ReverseMeshViewModel — observes the Reverse Mesh tables via Kotlin Flow
 * and binds to MeshService to receive real-time routing events.
 *
 * Observation channels:
 *   1. **activeBulletins** (StateFlow) — live list of non-expired Bulletins.
 *   2. **myAcks** (StateFlow) — live list of ACKs targeted at THIS device.
 *   3. **incomingAckEvent** (SharedFlow) — one-shot event for ACK popup.
 *   4. **incomingBulletinEvent** (SharedFlow) — one-shot event for Bulletin toast.
 *
 * This ViewModel binds to MeshService and registers as the
 * onReverseMeshResult listener, so it receives routing events
 * from BOTH offline peers and cloud-to-mesh bridge in real-time.
 */
class ReverseMeshViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ReverseMeshVM"
    }

    val localDeviceId: String = DeviceFingerprint.getId(application)
    private val database = AppDatabase.getInstance(application)
    private val evacDao = database.evacDao()

    // ── MeshService binding ──────────────────────────────────────────────
    private var meshService: MeshService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as MeshService.MeshBinder).getService()
            meshService = service
            Log.i(TAG, "✅ Bound to MeshService — listening for Reverse Mesh events")

            // Register to receive routing results from MeshService
            service.onReverseMeshResult = { result ->
                viewModelScope.launch {
                    handleResult(result)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            Log.w(TAG, "Lost connection to MeshService")
        }
    }

    init {
        // Bind to MeshService to get live routing events
        val intent = Intent(application, MeshService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        meshService?.onReverseMeshResult = null
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) { }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Flow 1: Live list of active Bulletins (for RecyclerView)
    // ──────────────────────────────────────────────────────────────────────

    val activeBulletins: StateFlow<List<BulletinEntity>> =
        evacDao.getActiveBulletins()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────
    //  Flow 2: Live list of MY ACKs (for ACK history)
    // ──────────────────────────────────────────────────────────────────────

    val myAcks: StateFlow<List<AckEntity>> =
        evacDao.getMyAcks(localDeviceId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ──────────────────────────────────────────────────────────────────────
    //  One-time events (SharedFlow — no replay on config change)
    // ──────────────────────────────────────────────────────────────────────

    private val _incomingAckEvent = MutableSharedFlow<AckEntity>(
        extraBufferCapacity = 10
    )
    val incomingAckEvent = _incomingAckEvent.asSharedFlow()

    private val _incomingBulletinEvent = MutableSharedFlow<BulletinEntity>(
        extraBufferCapacity = 10
    )
    val incomingBulletinEvent = _incomingBulletinEvent.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────
    //  Handle routing results from MeshService
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun handleResult(result: EvacRepository.IncomingResult) {
        when (result) {
            is EvacRepository.IncomingResult.NewBulletin -> {
                _incomingBulletinEvent.emit(result.bulletin)
                Log.i(TAG, "📢 New Bulletin UI event: ${result.bulletin.uuid}")
            }
            is EvacRepository.IncomingResult.NewTargetedAck -> {
                _incomingAckEvent.emit(result.ack)
                Log.i(TAG, "🎯 Targeted ACK UI event: ${result.ack.uuid}")
            }
            is EvacRepository.IncomingResult.Silent -> {
                // Data Mule relay-only, or duplicate. No UI action.
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Garbage Collection
    // ──────────────────────────────────────────────────────────────────────

    fun purgeExpired() {
        viewModelScope.launch {
            val repo = meshService?.getEvacRepository()
            if (repo != null) {
                val count = repo.purgeExpired()
                Log.d(TAG, "Manual GC: purged $count expired messages")
            }
        }
    }
}

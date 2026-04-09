package com.evac.app.ui.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.db.SafeSpotEntity
import com.evac.app.repository.SafeSpotRepository
import com.evac.app.sync.SafeSpotSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MapViewModel"
    }

    private val database = AppDatabase.getInstance(application)

    // --- SOS Messages (existing) ---
    val sosMessages: Flow<List<MessageEntity>> =
        database.messageDao().getSosMessages()

    // --- SafeSpots (new) ---
    private val safeSpotRepository = SafeSpotRepository(database.safeSpotDao())
    private val syncManager = SafeSpotSyncManager(safeSpotRepository)

    val activeSafeSpots: Flow<List<SafeSpotEntity>> =
        safeSpotRepository.activeSafeSpots

    init {
        // Automatically start real-time listener when the map is launched
        syncManager.startListening()
    }

    override fun onCleared() {
        super.onCleared()
        // Stop listening when the Map ViewModel goes out of scope to save battery
        syncManager.stopListening()
    }

    // --- Sync State ---
    sealed class SyncState {
        object Idle : SyncState()
        object Loading : SyncState()
        data class Success(val fetched: Int, val written: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    /**
     * Trigger a pre-disaster sync from Firebase Hosting.
     */
    fun syncSafeSpots() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            Log.i(TAG, "Starting SafeSpot sync...")

            when (val result = syncManager.sync()) {
                is SafeSpotSyncManager.SyncResult.Success -> {
                    _syncState.value = SyncState.Success(result.totalFetched, result.written)
                    Log.i(TAG, "Sync success: ${result.written}/${result.totalFetched}")
                }
                is SafeSpotSyncManager.SyncResult.Error -> {
                    _syncState.value = SyncState.Error(result.message)
                    Log.e(TAG, "Sync error: ${result.message}")
                }
            }
        }
    }
}
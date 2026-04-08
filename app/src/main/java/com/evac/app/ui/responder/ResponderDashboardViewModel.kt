package com.evac.app.ui.responder

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ResponderDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dao = AppDatabase.getInstance(application).messageDao()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    // Combined flow: whenever location updates OR the local DB updates, we recalculate tasks
    val tasksFlow = combine(
        dao.getSosMessages(),
        _currentLocation
    ) { messages, loc ->
        if (loc == null) return@combine emptyList<SosTask>()

        val currentUserEmail = auth.currentUser?.email ?: return@combine emptyList<SosTask>()
        val tasks = mutableListOf<SosTask>()
        var closestUnassignedId: String? = null
        var minDistance = Float.MAX_VALUE

        for (msg in messages) {
            val lat = msg.lat ?: continue
            val lng = msg.lng ?: continue

            val results = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, results)
            val dist = results[0]

            // 5km radius filter
            if (dist > 5000f) continue

            val isAssignedToMe = msg.assignedTo == currentUserEmail

            // Keep track of the nearest unassigned task to auto-claim it
            if (msg.assignedTo == null && dist < minDistance) {
                minDistance = dist
                closestUnassignedId = msg.id
            }

            tasks.add(
                SosTask(
                    id = msg.id,
                    status = msg.status ?: "UNKNOWN",
                    lat = lat,
                    lng = lng,
                    peopleCount = msg.peopleCount ?: 1,
                    batteryPct = msg.batteryPct,
                    note = msg.note,
                    deviceId = msg.deviceId,
                    timestamp = msg.timestamp,
                    distanceMeters = dist,
                    isAssignedToMe = isAssignedToMe
                )
            )
        }

        // Auto-claim the closest unassigned task
        if (closestUnassignedId != null) {
            claimTask(closestUnassignedId, currentUserEmail)
        }

        tasks.sortedBy { it.distanceMeters }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateLocation(location: Location) {
        _currentLocation.value = location
    }

    private fun claimTask(taskId: String, responderEmail: String) {
        Log.i("ResponderViewModel", "Auto-claiming task: $taskId for $responderEmail")
        db.collection("sos_messages").document(taskId)
            .update("assigned_to", responderEmail)
            .addOnSuccessListener {
                Log.i("ResponderViewModel", "Claim success: $taskId")
            }
            .addOnFailureListener { e ->
                Log.e("ResponderViewModel", "Claim failed", e)
            }
    }

    fun markResolved(taskId: String) {
        // Update the SOS status to SAFE to notify the Command Center that the victim is rescued
        db.collection("sos_messages").document(taskId)
            .update("status", "SAFE")
            .addOnSuccessListener {
                Log.i("ResponderViewModel", "Task $taskId marked as SAFE")
            }
            .addOnFailureListener { e ->
                Log.e("ResponderViewModel", "Failed to mark SAFE", e)
            }
    }
}

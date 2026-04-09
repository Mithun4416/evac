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
        val currentUserEmail = auth.currentUser?.email ?: return@combine emptyList<SosTask>()
        val tasks = mutableListOf<SosTask>()
        var closestUnassignedId: String? = null
        var minDistance = Float.MAX_VALUE

        for (msg in messages) {
            val isAssignedToMe = msg.assignedTo == currentUserEmail
            var dist = -1f // Represents unknown distance

            if (loc != null && msg.lat != null && msg.lng != null) {
                val results = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, msg.lat, msg.lng, results)
                dist = results[0]

                // Optional: strictly 5km radius filter only if location is verified
                if (dist > 5000f) continue
                
                if (msg.assignedTo == null && dist < minDistance) {
                    minDistance = dist
                    closestUnassignedId = msg.id
                }
            }

            tasks.add(
                SosTask(
                    id = msg.id,
                    status = msg.status ?: "UNKNOWN",
                    lat = msg.lat ?: 0.0,
                    lng = msg.lng ?: 0.0,
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

        // Auto-claim the closest unassigned task if location exists
        if (loc != null && closestUnassignedId != null) {
            claimTask(closestUnassignedId, currentUserEmail)
        }

        // Sort by distance if loc exists, otherwise sort by newest timestamp
        if (loc != null) {
            tasks.sortedBy { it.distanceMeters }
        } else {
            tasks.sortedByDescending { it.timestamp }
        }
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

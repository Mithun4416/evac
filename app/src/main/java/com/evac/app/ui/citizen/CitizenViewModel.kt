package com.evac.app.ui.citizen

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AppDatabase
import com.evac.app.mesh.MeshService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.evac.app.model.SosMessage
import com.evac.app.util.DeviceFingerprint
import java.text.SimpleDateFormat
import java.util.*

class CitizenViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CitizenViewModel"
        private const val RATE_LIMIT_MS = 60 * 1000L // 1 minute
    }

    private val _sosSent = MutableLiveData<Boolean>()
    val sosSent: LiveData<Boolean> = _sosSent

    private val _statusMsg = MutableLiveData<String>()
    val statusMsg: LiveData<String> = _statusMsg

    private var meshService: MeshService? = null
    private var lastSosTime = 0L

    fun canSendSos(): Boolean {
        return System.currentTimeMillis() - lastSosTime >= RATE_LIMIT_MS
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            meshService = (binder as MeshService.MeshBinder).getService()
            Log.i(TAG, "Bound to MeshService")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
        }
    }

    init {
        val intent = Intent(application, MeshService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) { }
    }

    /**
     * Send an SOS message into the mesh.
     *
     * @param status   MEDICAL | TRAPPED | HAZARD | SAFE
     * @param note     Optional text note (100 chars max)
     * @param peopleCount  Number of people (1-10+)
     * @param phraseKey    Optional pre-loaded phrase key
     * @param isVolumeSos  True if triggered by Volume Down 3×
     * @param location     GPS location (nullable)
     */
    fun sendSos(
        status: String,
        note: String = "",
        peopleCount: Int = 1,
        phraseKey: String = "",
        isVolumeSos: Boolean = false,
        location: Location? = null
    ) {
        // Rate limit: 1 SOS per 1 minute
        val now = System.currentTimeMillis()
        if (now - lastSosTime < RATE_LIMIT_MS) {
            val remainSec = (RATE_LIMIT_MS - (now - lastSosTime)) / 1000
            _statusMsg.value = "Rate limited. Wait ${remainSec}s"
            Log.w(TAG, "SOS rate limited — ${remainSec}s remaining")
            return
        }

        val ctx = getApplication<Application>()
        val deviceId = DeviceFingerprint.getId(ctx)
        val batteryPct = getBatteryLevel(ctx)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")

        val sos = SosMessage(
            status = status,
            deviceId = deviceId,
            timestamp = sdf.format(Date()),
            lat = location?.latitude ?: 0.0,
            lng = location?.longitude ?: 0.0,
            accuracyM = location?.accuracy ?: 0f,
            peopleCount = peopleCount,
            batteryPct = batteryPct,
            note = note.take(100),
            phraseKey = phraseKey,
            isVolumeSos = isVolumeSos
        )

        val service = meshService
        if (service != null) {
            service.sendSos(sos)
            lastSosTime = now
            _sosSent.value = true
            _statusMsg.value = "$status SOS sent!"
            Log.i(TAG, "SOS sent: ${sos.id} status=$status people=$peopleCount battery=$batteryPct%")
        } else {
            _statusMsg.value = "Mesh not ready. Saving locally..."
            // Fallback: save to Room directly
            val dao = AppDatabase.getInstance(ctx).messageDao()
            viewModelScope.launch(Dispatchers.IO) {
                dao.insert(sos.toEntity())
                Log.w(TAG, "MeshService not bound — saved SOS ${sos.id} locally")
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

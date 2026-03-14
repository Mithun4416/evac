package com.evac.app.ui.citizen

import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.util.DeviceFingerprint
import com.evac.app.util.EvacPowerManager
import com.evac.app.util.HashUtil
import kotlinx.coroutines.launch
import java.util.UUID

class CitizenViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private var lastSosTime = 0L
    val RATE_LIMIT_MS = 120_000L // 2 minutes

    fun canSendSos(): Boolean {
        return System.currentTimeMillis() - lastSosTime >= RATE_LIMIT_MS
    }

    fun sendSos(
        status: String,
        peopleCount: Int,
        note: String?,
        phraseKey: String?,
        context: Context
    ) {
        viewModelScope.launch {
            lastSosTime = System.currentTimeMillis()

            // Get location
            val locationManager = context
                .getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }

            val deviceId = DeviceFingerprint.getDeviceId(context)
            val batteryPct = EvacPowerManager.getBatteryPct(context)
            val id = UUID.randomUUID().toString()

            val hash = HashUtil.hashMessage(
                id = id,
                type = "SOS",
                status = status,
                deviceId = deviceId,
                timestamp = lastSosTime,
                lat = location?.latitude,
                lng = location?.longitude,
                peopleCount = peopleCount,
                batteryPct = batteryPct,
                note = note
            )

            val message = MessageEntity(
                id = id,
                type = "SOS",
                status = status,
                deviceId = deviceId,
                timestamp = lastSosTime,
                ttlHours = 24,
                hopCount = 0,
                maxHops = 10,
                lat = location?.latitude,
                lng = location?.longitude,
                accuracyM = location?.accuracy,
                peopleCount = peopleCount,
                batteryPct = batteryPct,
                note = note?.ifEmpty { null },
                phraseKey = phraseKey,
                isVolumeSos = false,
                hash = hash,
                body = null,
                targetDeviceId = null,
                signature = null
            )

            database.messageDao().insert(message)
        }
    }
}
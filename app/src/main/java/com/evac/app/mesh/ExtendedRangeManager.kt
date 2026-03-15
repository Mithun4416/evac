package com.evac.app.mesh

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

object ExtendedRangeManager : SensorEventListener {
    var isExtendedModeActive = false
        private set

    var isSkyRelayActive = false
        private set

    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null

    fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }

    fun setExtendedMode(enabled: Boolean, context: Context) {
        if (sensorManager == null) initialize(context)
        isExtendedModeActive = enabled
        if (enabled) {
            pressureSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            NearbyManager.enableExtendedBle(context)
            Log.d("ExtendedRange", "Extended Range Mode Enabled")
        } else {
            sensorManager?.unregisterListener(this)
            isSkyRelayActive = false
            Log.d("ExtendedRange", "Extended Range Mode Disabled")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            if (altitude > 200f) {
                if (!isSkyRelayActive) {
                    isSkyRelayActive = true
                    Log.d("ExtendedRange", "Altitude > 200m detected. SKY RELAY MODE ACTIVE.")
                }
            } else {
                if (isSkyRelayActive) {
                    isSkyRelayActive = false
                    Log.d("ExtendedRange", "Altitude dropped. Sky Relay disabled.")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

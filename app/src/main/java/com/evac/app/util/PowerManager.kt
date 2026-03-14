package com.evac.app.util

import android.content.Context
import android.os.BatteryManager

object EvacPowerManager {

    enum class PowerMode {
        NORMAL,         // Battery > 30%
        POWER_SAVER,    // Battery <= 30%
        EMERGENCY_BEACON // Battery <= 15%
    }

    fun getCurrentMode(context: Context): PowerMode {
        val pct = getBatteryPct(context)
        return when {
            pct <= 15 -> PowerMode.EMERGENCY_BEACON
            pct <= 30 -> PowerMode.POWER_SAVER
            else      -> PowerMode.NORMAL
        }
    }

    fun getBatteryPct(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // BLE scan interval in milliseconds based on mode
    fun getScanIntervalMs(mode: PowerMode): Long {
        return when (mode) {
            PowerMode.NORMAL           -> 30_000L   // 30 seconds
            PowerMode.POWER_SAVER      -> 300_000L  // 5 minutes
            PowerMode.EMERGENCY_BEACON -> 600_000L  // 10 minutes
        }
    }

    // GPS interval in milliseconds based on mode
    fun getGpsIntervalMs(mode: PowerMode): Long {
        return when (mode) {
            PowerMode.NORMAL           -> 60_000L   // 1 minute
            PowerMode.POWER_SAVER      -> 300_000L  // 5 minutes
            PowerMode.EMERGENCY_BEACON -> Long.MAX_VALUE // Off — use last known
        }
    }
}
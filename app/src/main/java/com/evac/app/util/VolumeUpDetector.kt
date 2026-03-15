package com.evac.app.util

class VolumeUpDetector(private val onBeaconToggle: () -> Unit) {
    private val pressTimes = mutableListOf<Long>()
    private val WINDOW_MS = 1500L
    private val REQUIRED_PRESSES = 3

    fun onVolumeUp() {
        val now = System.currentTimeMillis()
        pressTimes.removeAll { now - it > WINDOW_MS }
        pressTimes.add(now)

        if (pressTimes.size >= REQUIRED_PRESSES) {
            pressTimes.clear()
            onBeaconToggle()
        }
    }
}

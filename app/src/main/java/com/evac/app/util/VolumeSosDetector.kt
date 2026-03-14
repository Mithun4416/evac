package com.evac.app.util

class VolumeSosDetector(private val onSosDetected: () -> Unit) {

    private val pressTimes = mutableListOf<Long>()
    private val WINDOW_MS = 1500L   // 1.5 second window
    private val REQUIRED_PRESSES = 3

    fun onVolumeDown() {
        val now = System.currentTimeMillis()

        // Remove presses outside the time window
        pressTimes.removeAll { now - it > WINDOW_MS }

        // Add this press
        pressTimes.add(now)

        // Check if threshold reached
        if (pressTimes.size >= REQUIRED_PRESSES) {
            pressTimes.clear()
            onSosDetected()
        }
    }
}
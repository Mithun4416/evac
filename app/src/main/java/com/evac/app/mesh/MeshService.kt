package com.evac.app.mesh

import android.app.Service
import android.content.Intent
import android.os.IBinder

// ForegroundService – BLE scan + advertise
class MeshService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: Start BLE scanning and advertising
        // TODO: Initialize Nearby Connections
        return START_STICKY
    }
}

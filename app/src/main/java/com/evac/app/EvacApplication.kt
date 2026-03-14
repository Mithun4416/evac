package com.evac.app

import android.app.Application
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.mesh.MeshService

class EvacApplication : Application() {

    companion object {
        private const val TAG = "EvacApplication"
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Room database singleton
        database = AppDatabase.getInstance(this)
        Log.i(TAG, "Room database initialized")

        // Start MeshService as a foreground service
        MeshService.start(this)
        Log.i(TAG, "MeshService started")
    }
}
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

        // Initialize Firebase programmatically (same config as dashboard)
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey("AIzaSyDHmS0ZHUKJWCw_FXPDfxPenHhCSKSfkgI")
                    .setApplicationId("1:925392061448:android:evac_app")
                    .setProjectId("evac-dcb1a")
                    .setStorageBucket("evac-dcb1a.firebasestorage.app")
                    .setGcmSenderId("925392061448")
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(this, options)
                Log.d(TAG, "Firebase initialized programmatically")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init failed: $e")
        }

        // OSMDroid configuration
        org.osmdroid.config.Configuration.getInstance()
            .load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        // Start MeshService as a foreground service
        MeshService.start(this)
        Log.i(TAG, "MeshService started")
    }
}
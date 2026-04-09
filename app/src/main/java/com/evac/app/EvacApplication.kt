package com.evac.app

import android.app.Application
import android.util.Log
import com.evac.app.db.AppDatabase
import com.evac.app.mesh.MeshService
import com.evac.app.sync.SafeSpotSyncWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

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

        // Schedule SafeSpot background sync
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = PeriodicWorkRequestBuilder<SafeSpotSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SafeSpotSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.i(TAG, "SafeSpotSyncWorker scheduled")
    }
}
package com.evac.app

import android.app.Application
import android.util.Log
import com.evac.app.db.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class EvacApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase programmatically (same config as dashboard)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyDHmS0ZHUKJWCw_FXPDfxPenHhCSKSfkgI")
                    .setApplicationId("1:925392061448:android:evac_app")
                    .setProjectId("evac-dcb1a")
                    .setStorageBucket("evac-dcb1a.firebasestorage.app")
                    .setGcmSenderId("925392061448")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("EvacApplication", "Firebase initialized programmatically")
            }
        } catch (e: Exception) {
            Log.w("EvacApplication", "Firebase init failed: $e")
        }

        // OSMDroid configuration
        org.osmdroid.config.Configuration.getInstance()
            .load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }
}
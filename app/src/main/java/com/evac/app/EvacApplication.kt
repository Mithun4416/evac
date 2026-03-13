package com.evac.app

import android.app.Application

// Application class (init DB, start service)
class EvacApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: Initialize Room database
        // TODO: Start MeshService
    }
}

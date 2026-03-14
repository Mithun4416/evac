package com.evac.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * Generates a unique device fingerprint: sha256(ANDROID_ID).
 * Survives app reinstall on most devices.
 */
object DeviceFingerprint {

    @Volatile
    private var cachedId: String? = null

    @SuppressLint("HardwareIds")
    fun getId(context: Context): String {
        cachedId?.let { return it }
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val id = HashUtil.sha256(androidId)
        cachedId = id
        return id
    }
}

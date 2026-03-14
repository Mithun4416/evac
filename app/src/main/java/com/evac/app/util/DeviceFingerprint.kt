package com.evac.app.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceFingerprint {

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        return hashSHA256(androidId)
    }

    private fun hashSHA256(input: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
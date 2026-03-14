package com.evac.app.crypto

import android.util.Base64
import android.util.Log

object SignatureManager {

    private val TAG = "SignatureManager"

    // Hardcoded public key for hackathon demo
    // In production: load from secure storage
    private val PUBLIC_KEY_B64 =
        "REPLACE_WITH_YOUR_BASE64_PUBLIC_KEY"

    fun verifySignature(message: String, signatureB64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
            val signatureBytes = Base64.decode(signatureB64, Base64.DEFAULT)
            val messageBytes   = message.toByteArray()

            // TweetNaCl verify
            com.iwebpp.crypto.TweetNaclFast.Signature(
                publicKeyBytes, null
            ).detached_verify(messageBytes, signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error: $e")
            false
        }
    }

    fun isValidBulletin(body: String, signature: String?): Boolean {
        if (signature == null) return false
        return verifySignature(body, signature)
    }
}
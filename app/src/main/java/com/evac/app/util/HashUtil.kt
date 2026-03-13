package com.evac.app.util

import java.security.MessageDigest

// SHA-256 hashing
object HashUtil {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

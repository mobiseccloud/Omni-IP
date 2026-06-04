package com.mobisec.omniip.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class SecurityPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "omniip_security_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isPinLockEnabled(): Boolean {
        return sharedPreferences.getBoolean("is_pin_lock_enabled", false)
    }

    suspend fun setPin(plainPin: String) {
        val hashedPin = hashPin(plainPin)
        sharedPreferences.edit()
            .putString("hashed_pin", hashedPin)
            .putBoolean("is_pin_lock_enabled", true)
            .apply()
    }

    suspend fun removePin() {
        sharedPreferences.edit()
            .remove("hashed_pin")
            .putBoolean("is_pin_lock_enabled", false)
            .apply()
    }

    suspend fun verifyPin(inputPin: String): Boolean {
        val storedHash = sharedPreferences.getString("hashed_pin", null)
        if (storedHash == null) return false
        val inputHash = hashPin(inputPin)
        return storedHash == inputHash
    }

    private fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}

package com.mobisec.omniip.core

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    fun isAutoStartEnabled(): Boolean {
        return sharedPreferences.getBoolean("is_auto_start_enabled", false)
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean("is_auto_start_enabled", enabled)
            .apply()
    }

    fun isPinLockEnabled(): Boolean {
        return sharedPreferences.getBoolean("is_pin_lock_enabled", false)
    }

    suspend fun setPin(plainPin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hashedPin = hashPin(plainPin, salt)
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        
        sharedPreferences.edit()
            .putString("hashed_pin", hashedPin)
            .putString("pin_salt", saltStr)
            .putBoolean("is_pin_lock_enabled", true)
            .apply()
    }

    suspend fun removePin() {
        sharedPreferences.edit()
            .remove("hashed_pin")
            .remove("pin_salt")
            .putBoolean("is_pin_lock_enabled", false)
            .apply()
    }

    suspend fun verifyPin(inputPin: String): Boolean {
        val storedHash = sharedPreferences.getString("hashed_pin", null)
        val storedSaltStr = sharedPreferences.getString("pin_salt", null)
        if (storedHash == null || storedSaltStr == null) return false
        
        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(inputPin, salt)
        return storedHash == inputHash
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}

package com.jyotirmoyb.loan.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages optional local App Lock credentials (salted PBKDF2-HMAC-SHA256 PIN hash)
 * strictly inside Android Keystore-backed [EncryptedSharedPreferences], excluded from Auto Backup.
 *
 * Security invariants:
 * - Raw PIN is never stored, logged, or backed up.
 * - Verification uses constant-time [MessageDigest.isEqual].
 * - Biometric authentication delegates to Android [BiometricManager] / BiometricPrompt.
 */
class AppLockManager(context: Context) {

    private val appContext = context.applicationContext
    private val inMemoryFallback = HashMap<String, String>()
    private var encryptedPrefs: SharedPreferences? = null

    init {
        encryptedPrefs = runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    fun isBiometricOrDeviceCredentialAvailable(): Boolean {
        val manager = BiometricManager.from(appContext)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasPinConfigured(): Boolean {
        val salt = readString(KEY_PIN_SALT)
        val hash = readString(KEY_PIN_HASH)
        return !salt.isNullOrBlank() && !hash.isNullOrBlank()
    }

    fun setPin(pin: String): Result<Unit> {
        if (!isValidPinFormat(pin)) {
            return Result.failure(IllegalArgumentException("PIN must be 4 to 6 digits."))
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPinBytes(pin, salt)
        writeString(KEY_PIN_SALT, Base64Compat.encode(salt))
        writeString(KEY_PIN_HASH, Base64Compat.encode(hash))
        return Result.success(Unit)
    }

    fun verifyPin(candidatePin: String): Boolean {
        val saltB64 = readString(KEY_PIN_SALT) ?: return false
        val hashB64 = readString(KEY_PIN_HASH) ?: return false
        return verifyPinHash(candidatePin, saltB64, hashB64)
    }

    fun clearPin() {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
        } else {
            inMemoryFallback.remove(KEY_PIN_SALT)
            inMemoryFallback.remove(KEY_PIN_HASH)
        }
    }

    private fun readString(key: String): String? =
        encryptedPrefs?.getString(key, null) ?: inMemoryFallback[key]

    private fun writeString(key: String, value: String) {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().putString(key, value).apply()
        } else {
            inMemoryFallback[key] = value
        }
    }

    companion object {
        const val PREFS_FILE_NAME = "loanjb_security_prefs"
        private const val KEY_PIN_SALT = "pin_salt_b64"
        private const val KEY_PIN_HASH = "pin_hash_b64"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PIN_PBKDF2_ITERATIONS = 100_000
        private const val KDF_ALGO = "PBKDF2WithHmacSHA256"

        fun isValidPinFormat(pin: String): Boolean =
            pin.length in 4..6 && pin.all { it.isDigit() }

        fun createPinRecord(pin: String): Pair<String, String> {
            require(isValidPinFormat(pin)) { "PIN must be 4 to 6 digits." }
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val hash = hashPinBytes(pin, salt)
            return Base64Compat.encode(salt) to Base64Compat.encode(hash)
        }

        fun verifyPinHash(candidatePin: String, saltB64: String, expectedHashB64: String): Boolean {
            if (candidatePin.isEmpty()) return false
            val salt = runCatching { Base64Compat.decode(saltB64) }.getOrNull() ?: return false
            val expected = runCatching { Base64Compat.decode(expectedHashB64) }.getOrNull() ?: return false
            val actual = hashPinBytes(candidatePin, salt)
            return MessageDigest.isEqual(actual, expected)
        }

        private fun hashPinBytes(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_PBKDF2_ITERATIONS, HASH_BITS)
            return try {
                SecretKeyFactory.getInstance(KDF_ALGO).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }
}

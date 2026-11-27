package com.jyotirmoyb.loan.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages optional local App Lock credentials (salted PBKDF2-HMAC-SHA256 PIN hash)
 * stored encrypted with Android Keystore AES-256-GCM ([KeystoreEncryptedPrefs]),
 * excluded from Auto Backup & Device Transfer, with automatic one-time migration from
 * legacy EncryptedSharedPreferences.
 *
 * Security invariants:
 * - Raw PIN is never stored, logged, or backed up.
 * - Verification uses constant-time [MessageDigest.isEqual].
 * - Biometric authentication delegates to Android [BiometricManager] / BiometricPrompt.
 */
class AppLockManager internal constructor(private val appContext: Context, private val securePrefs: KeystoreEncryptedPrefs) {

    constructor(context: Context) : this(
        appContext = context.applicationContext,
        securePrefs = KeystoreEncryptedPrefs(
            context = context,
            prefsFileName = PREFS_FILE_NAME,
            keyAlias = KEYSTORE_ALIAS,
            legacyPrefsName = LEGACY_PREFS_FILE_NAME,
            legacyKeys = listOf(KEY_PIN_SALT, KEY_PIN_HASH)
        )
    )

    internal constructor(
        context: Context,
        cryptoEngineOverride: AesGcmCryptoEngine?,
        legacyReader: ((String, Collection<String>) -> Map<String, String>?)? = null,
        legacyDeleter: ((String) -> Boolean)? = null
    ) : this(
        appContext = context.applicationContext,
        securePrefs = KeystoreEncryptedPrefs(
            context = context,
            prefsFileName = PREFS_FILE_NAME,
            keyAlias = KEYSTORE_ALIAS,
            legacyPrefsName = LEGACY_PREFS_FILE_NAME,
            legacyKeys = listOf(KEY_PIN_SALT, KEY_PIN_HASH),
            cryptoEngineOverride = cryptoEngineOverride,
            legacyReader = legacyReader,
            legacyDeleter = legacyDeleter
        )
    )

    fun isBiometricOrDeviceCredentialAvailable(): Boolean {
        val manager = BiometricManager.from(appContext)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasPinConfigured(): Boolean {
        val salt = securePrefs.getString(KEY_PIN_SALT)
        val hash = securePrefs.getString(KEY_PIN_HASH)
        return !salt.isNullOrBlank() && !hash.isNullOrBlank()
    }

    fun setPin(pin: String): Result<Unit> {
        if (!isValidPinFormat(pin)) {
            return Result.failure(IllegalArgumentException("PIN must be 4 to 6 digits."))
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPinBytes(pin, salt)
        securePrefs.putString(KEY_PIN_SALT, Base64Compat.encode(salt))
        securePrefs.putString(KEY_PIN_HASH, Base64Compat.encode(hash))
        return Result.success(Unit)
    }

    fun verifyPin(candidatePin: String): Boolean {
        val saltB64 = securePrefs.getString(KEY_PIN_SALT) ?: return false
        val hashB64 = securePrefs.getString(KEY_PIN_HASH) ?: return false
        return verifyPinHash(candidatePin, saltB64, hashB64)
    }

    fun clearPin() {
        securePrefs.remove(KEY_PIN_SALT)
        securePrefs.remove(KEY_PIN_HASH)
    }

    companion object {
        const val PREFS_FILE_NAME = "loanjb_security_prefs_v2"
        const val LEGACY_PREFS_FILE_NAME = "loanjb_security_prefs"
        private const val KEYSTORE_ALIAS = "loanjb_app_lock_aes_gcm_v1"
        internal const val KEY_PIN_SALT = "pin_salt_b64"
        internal const val KEY_PIN_HASH = "pin_hash_b64"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PIN_PBKDF2_ITERATIONS = 100_000
        private const val KDF_ALGO = "PBKDF2WithHmacSHA256"

        fun isValidPinFormat(pin: String): Boolean = pin.length in 4..6 && pin.all { it.isDigit() }

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

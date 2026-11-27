package com.jyotirmoyb.loan.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction for authenticated AES-256-GCM encryption/decryption so production code
 * uses Android Keystore while unit tests can supply a deterministic JVM AES-GCM engine.
 */
interface AesGcmCryptoEngine {
    val isHardwareBacked: Boolean
    fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray>
    fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
}

/**
 * Android Keystore backed AES-256-GCM engine.
 * Keys are generated inside Android Keystore (non-exportable) with randomized IV enforcement.
 */
class AndroidKeystoreAesGcmEngine(private val keyAlias: String) : AesGcmCryptoEngine {

    override val isHardwareBacked: Boolean = true

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv to ciphertext
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
    }
}

/**
 * One-time reader for legacy `androidx.security:security-crypto` [EncryptedSharedPreferences] files.
 * Only invoked if the legacy XML file actually exists on disk, then deleted immediately after
 * values are rewritten in the new Android Keystore AES-256-GCM format.
 */
object LegacyEncryptedPrefsMigrator {

    fun legacyFileExists(context: Context, legacyPrefsName: String): Boolean {
        val dataDir = context.applicationInfo?.dataDir ?: return false
        return File(File(dataDir, "shared_prefs"), "$legacyPrefsName.xml").exists()
    }

    @Suppress("DEPRECATION")
    fun readLegacyValues(context: Context, legacyPrefsName: String, keys: Collection<String>): Map<String, String>? {
        if (!legacyFileExists(context, legacyPrefsName)) return null
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacyPrefs = EncryptedSharedPreferences.create(
                context,
                legacyPrefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            buildMap {
                for (key in keys) {
                    val value = legacyPrefs.getString(key, null)
                    if (!value.isNullOrEmpty()) {
                        put(key, value)
                    }
                }
            }
        }.getOrNull()
    }
}

/**
 * Lightweight Android Keystore + AES-256-GCM encrypted key-value store persisted in private
 * [SharedPreferences] (excluded from Android Auto Backup & Device Transfer).
 *
 * Security invariants:
 * - Every value is encrypted with a unique random 12-byte GCM IV and 128-bit auth tag.
 * - Associated Authenticated Data (AAD) binds each ciphertext to `prefsFileName:key` so
 *   ciphertexts cannot be transplanted between keys or files.
 * - Plaintext values are NEVER written to disk. If the Keystore engine is unavailable (e.g.
 *   plain JVM/Robolectric tests without a custom engine), values stay strictly in memory.
 */
class KeystoreEncryptedPrefs(
    context: Context,
    private val prefsFileName: String,
    keyAlias: String,
    legacyPrefsName: String? = null,
    legacyKeys: Collection<String> = emptyList(),
    cryptoEngineOverride: AesGcmCryptoEngine? = null,
    private val legacyReader: ((String, Collection<String>) -> Map<String, String>?)? = null,
    private val legacyDeleter: ((String) -> Boolean)? = null
) {

    private val appContext: Context = context.applicationContext
    private val rawPrefs: SharedPreferences =
        appContext.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
    private val inMemoryFallback = HashMap<String, String>()
    private val cryptoEngine: AesGcmCryptoEngine?

    val isEncryptedStorageActive: Boolean
        get() = cryptoEngine != null

    val isHardwareBacked: Boolean
        get() = cryptoEngine?.isHardwareBacked == true

    init {
        cryptoEngine = if (cryptoEngineOverride != null) {
            cryptoEngineOverride
        } else {
            runCatching {
                val engine = AndroidKeystoreAesGcmEngine(keyAlias)
                // Smoke-verify Keystore availability once before allowing disk writes
                val probe = "loanjb_probe".toByteArray(StandardCharsets.UTF_8)
                val probeAad = "$prefsFileName:__probe".toByteArray(StandardCharsets.UTF_8)
                val (iv, ct) = engine.encrypt(probe, probeAad)
                val decrypted = engine.decrypt(iv, ct, probeAad)
                require(decrypted.contentEquals(probe)) { "Keystore probe roundtrip failed" }
                engine
            }.getOrNull()
        }

        if (!legacyPrefsName.isNullOrBlank() && legacyKeys.isNotEmpty()) {
            migrateFromLegacyIfNeeded(legacyPrefsName, legacyKeys)
        }
    }

    fun getString(key: String): String? {
        val engine = cryptoEngine ?: return inMemoryFallback[key]
        val stored = rawPrefs.getString(storageKey(key), null) ?: return null
        val parts = stored.split(FIELD_SEPARATOR)
        if (parts.size != 3 || parts[0] != FORMAT_V1) return null
        return runCatching {
            val iv = Base64Compat.decode(parts[1])
            val ciphertext = Base64Compat.decode(parts[2])
            val plainBytes = engine.decrypt(iv, ciphertext, buildAad(key))
            String(plainBytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun putString(key: String, value: String) {
        val engine = cryptoEngine
        if (engine == null) {
            inMemoryFallback[key] = value
            return
        }
        val plainBytes = value.toByteArray(StandardCharsets.UTF_8)
        val (iv, ciphertext) = engine.encrypt(plainBytes, buildAad(key))
        val encoded = "$FORMAT_V1$FIELD_SEPARATOR${Base64Compat.encode(iv)}$FIELD_SEPARATOR${Base64Compat.encode(ciphertext)}"
        rawPrefs.edit().putString(storageKey(key), encoded).apply()
    }

    fun remove(key: String) {
        if (cryptoEngine == null) {
            inMemoryFallback.remove(key)
        } else {
            rawPrefs.edit().remove(storageKey(key)).apply()
        }
    }

    fun clear() {
        inMemoryFallback.clear()
        val migrated = rawPrefs.getBoolean(MIGRATION_DONE_KEY, false)
        rawPrefs.edit().clear().apply()
        if (migrated) {
            rawPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
        }
    }

    private fun migrateFromLegacyIfNeeded(legacyPrefsName: String, legacyKeys: Collection<String>) {
        if (rawPrefs.getBoolean(MIGRATION_DONE_KEY, false)) return

        val reader = legacyReader ?: { name, keys ->
            LegacyEncryptedPrefsMigrator.readLegacyValues(appContext, name, keys)
        }
        val deleter = legacyDeleter ?: { name ->
            appContext.deleteSharedPreferences(name)
        }

        val legacyValues = runCatching { reader(legacyPrefsName, legacyKeys) }.getOrNull()
        if (legacyValues != null) {
            var allWritten = true
            for ((key, value) in legacyValues) {
                if (value.isNotEmpty()) {
                    val result = runCatching { putString(key, value) }
                    if (result.isFailure) {
                        allWritten = false
                    }
                }
            }
            if (allWritten) {
                runCatching { deleter(legacyPrefsName) }
                rawPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
            }
        } else if (legacyReader == null && !LegacyEncryptedPrefsMigrator.legacyFileExists(appContext, legacyPrefsName)) {
            // Fresh install with no legacy file on disk: mark migration complete so we never check again.
            rawPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
        }
    }

    private fun storageKey(key: String): String = "$ENTRY_PREFIX$key"

    private fun buildAad(key: String): ByteArray = "$prefsFileName:$key".toByteArray(StandardCharsets.UTF_8)

    companion object {
        private const val FORMAT_V1 = "v1"
        private const val FIELD_SEPARATOR = ":"
        private const val ENTRY_PREFIX = "enc_"
        internal const val MIGRATION_DONE_KEY = "__loanjb_migrated_from_legacy_esp_v1"
    }
}

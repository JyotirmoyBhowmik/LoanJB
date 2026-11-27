package com.jyotirmoyb.loan.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.loan.data.market.MarketApiProviderSlot
import com.jyotirmoyb.loan.data.market.SecureApiKeyStore
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreEncryptedPrefsAndMigrationTest {

    private lateinit var context: Context
    private lateinit var testCryptoEngine: AesGcmCryptoEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteSharedPreferences(AppLockManager.PREFS_FILE_NAME)
        context.deleteSharedPreferences(AppLockManager.LEGACY_PREFS_FILE_NAME)
        context.deleteSharedPreferences(SecureApiKeyStore.PREFS_FILE_NAME)
        context.deleteSharedPreferences(SecureApiKeyStore.LEGACY_PREFS_FILE_NAME)
        context.deleteSharedPreferences(TEST_PREFS_FILE)
        testCryptoEngine = JvmTestAesGcmEngine()
    }

    @Test
    fun keystoreEncryptedPrefs_encryptsAtRest_andRejectsTamperingOrAadMismatch() {
        val prefs = KeystoreEncryptedPrefs(
            context = context,
            prefsFileName = TEST_PREFS_FILE,
            keyAlias = "test_alias",
            cryptoEngineOverride = testCryptoEngine
        )

        val secretValue = "super-secret-token-987654"
        prefs.putString("token_a", secretValue)
        assertEquals(secretValue, prefs.getString("token_a"))

        // Verify raw SharedPreferences never stores plaintext and uses v1:<iv>:<ct> format
        val rawPrefs = context.getSharedPreferences(TEST_PREFS_FILE, Context.MODE_PRIVATE)
        val rawStored = rawPrefs.getString("enc_token_a", null)
        assertNotNull(rawStored)
        assertTrue(rawStored!!.startsWith("v1:"))
        assertFalse(rawStored.contains(secretValue))

        // Re-writing same value produces a fresh random IV / distinct ciphertext
        prefs.putString("token_a", secretValue)
        val rawStoredSecond = rawPrefs.getString("enc_token_a", null)
        assertNotNull(rawStoredSecond)
        assertNotEquals(rawStored, rawStoredSecond)
        assertEquals(secretValue, prefs.getString("token_a"))

        // Swapping ciphertext to a different key fails AAD verification
        rawPrefs.edit().putString("enc_token_b", rawStoredSecond).commit()
        assertNull(prefs.getString("token_b"))

        // Tampering with ciphertext fails GCM authentication tag verification
        val parts = rawStoredSecond!!.split(":")
        val tampered = "${parts[0]}:${parts[1]}:AAAA${parts[2].drop(4)}"
        rawPrefs.edit().putString("enc_token_a", tampered).commit()
        assertNull(prefs.getString("token_a"))
    }

    @Test
    fun appLockManager_setVerifyClearPin_andRejectInvalidFormat() {
        val manager = AppLockManager(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { _, _ -> null }
        )

        assertFalse(manager.hasPinConfigured())
        assertTrue(manager.setPin("12").isFailure)
        assertTrue(manager.setPin("12ab").isFailure)
        assertTrue(manager.setPin("1234567").isFailure)

        assertTrue(manager.setPin("2580").isSuccess)
        assertTrue(manager.hasPinConfigured())
        assertTrue(manager.verifyPin("2580"))
        assertFalse(manager.verifyPin("0000"))
        assertFalse(manager.verifyPin(""))

        // Raw PIN must never appear in underlying SharedPreferences
        val rawEntries = context
            .getSharedPreferences(AppLockManager.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString("|")
        assertFalse(rawEntries.contains("2580"))

        // Clearing removes the stored PIN
        manager.clearPin()
        assertFalse(manager.hasPinConfigured())
        assertFalse(manager.verifyPin("2580"))
    }

    @Test
    fun appLockManager_migratesLegacyPinOnce_andDeletesLegacyPrefs() {
        val (legacySalt, legacyHash) = AppLockManager.createPinRecord("491302")
        val legacyReadCalls = AtomicInteger(0)
        val deletedNames = mutableListOf<String>()

        val migratedManager = AppLockManager(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { name, keys ->
                legacyReadCalls.incrementAndGet()
                assertEquals(AppLockManager.LEGACY_PREFS_FILE_NAME, name)
                assertTrue(keys.contains(AppLockManager.KEY_PIN_SALT))
                assertTrue(keys.contains(AppLockManager.KEY_PIN_HASH))
                mapOf(
                    AppLockManager.KEY_PIN_SALT to legacySalt,
                    AppLockManager.KEY_PIN_HASH to legacyHash
                )
            },
            legacyDeleter = { name ->
                deletedNames.add(name)
                true
            }
        )

        assertEquals(1, legacyReadCalls.get())
        assertEquals(listOf(AppLockManager.LEGACY_PREFS_FILE_NAME), deletedNames)
        assertTrue(migratedManager.hasPinConfigured())
        assertTrue(migratedManager.verifyPin("491302"))
        assertFalse(migratedManager.verifyPin("999999"))

        // Second instantiation must NOT re-read or re-delete legacy prefs, yet must keep migrated PIN
        val secondInstance = AppLockManager(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { _, _ ->
                legacyReadCalls.incrementAndGet()
                error("Legacy reader must not be invoked after migration completes")
            },
            legacyDeleter = { name ->
                deletedNames.add(name)
                true
            }
        )

        assertEquals(1, legacyReadCalls.get())
        assertEquals(1, deletedNames.size)
        assertTrue(secondInstance.hasPinConfigured())
        assertTrue(secondInstance.verifyPin("491302"))
    }

    @Test
    fun secureApiKeyStore_saveLoadMaskClear_andStatusFlow() = runTest {
        val store = SecureApiKeyStore(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { _, _ -> null }
        )

        assertNull(store.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals(0, store.statusFlow.first().configuredCount)

        val fxKey = "fx_live_key_9876"
        val metalsKey = "mp_live_key_4321"
        store.setApiKey(MarketApiProviderSlot.FX_EXCHANGERATE, "  $fxKey  ")
        store.setApiKey(MarketApiProviderSlot.METALS_BULLION, metalsKey)

        assertEquals(fxKey, store.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals(metalsKey, store.getApiKey(MarketApiProviderSlot.METALS_BULLION))

        val snapshot = store.statusFlow.first()
        assertEquals(2, snapshot.configuredCount)
        assertTrue(snapshot.hasKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals("••••••9876", snapshot.configuredSlots[MarketApiProviderSlot.FX_EXCHANGERATE])
        assertEquals("••••••4321", snapshot.configuredSlots[MarketApiProviderSlot.METALS_BULLION])

        // Raw keys must never appear in plaintext on disk
        val rawDump = context
            .getSharedPreferences(SecureApiKeyStore.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString("|")
        assertFalse(rawDump.contains(fxKey))
        assertFalse(rawDump.contains(metalsKey))

        // Clear single key
        store.clearApiKey(MarketApiProviderSlot.FX_EXCHANGERATE)
        assertNull(store.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals(metalsKey, store.getApiKey(MarketApiProviderSlot.METALS_BULLION))

        // Clear all keys
        store.clearAllKeys()
        assertNull(store.getApiKey(MarketApiProviderSlot.METALS_BULLION))
        assertEquals(0, store.statusFlow.first().configuredCount)
    }

    @Test
    fun secureApiKeyStore_migratesLegacyKeysOnce_andDeletesLegacyPrefs() = runTest {
        val legacyReadCalls = AtomicInteger(0)
        val deletedNames = mutableListOf<String>()

        val migratedStore = SecureApiKeyStore(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { name, keys ->
                legacyReadCalls.incrementAndGet()
                assertEquals(SecureApiKeyStore.LEGACY_PREFS_FILE_NAME, name)
                assertTrue(keys.contains(MarketApiProviderSlot.FX_EXCHANGERATE.prefKey))
                assertTrue(keys.contains(MarketApiProviderSlot.EQUITY_INDICES.prefKey))
                mapOf(
                    MarketApiProviderSlot.FX_EXCHANGERATE.prefKey to "legacy_fx_1122",
                    MarketApiProviderSlot.EQUITY_INDICES.prefKey to "legacy_eq_7788"
                )
            },
            legacyDeleter = { name ->
                deletedNames.add(name)
                true
            }
        )

        assertEquals(1, legacyReadCalls.get())
        assertEquals(listOf(SecureApiKeyStore.LEGACY_PREFS_FILE_NAME), deletedNames)
        assertEquals("legacy_fx_1122", migratedStore.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals("legacy_eq_7788", migratedStore.getApiKey(MarketApiProviderSlot.EQUITY_INDICES))
        assertNull(migratedStore.getApiKey(MarketApiProviderSlot.METALS_BULLION))
        assertEquals(2, migratedStore.statusFlow.first().configuredCount)

        // Second instantiation must skip migration and still read the migrated keys
        val reloadedStore = SecureApiKeyStore(
            context = context,
            cryptoEngineOverride = testCryptoEngine,
            legacyReader = { _, _ ->
                legacyReadCalls.incrementAndGet()
                error("Legacy reader must not be invoked after migration completes")
            },
            legacyDeleter = { name ->
                deletedNames.add(name)
                true
            }
        )

        assertEquals(1, legacyReadCalls.get())
        assertEquals(1, deletedNames.size)
        assertEquals("legacy_fx_1122", reloadedStore.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE))
        assertEquals("legacy_eq_7788", reloadedStore.getApiKey(MarketApiProviderSlot.EQUITY_INDICES))
    }

    private class JvmTestAesGcmEngine : AesGcmCryptoEngine {
        private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val secureRandom = SecureRandom()

        override val isHardwareBacked: Boolean = false

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintext)
            return iv to ciphertext
        }

        override fun decrypt(iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertext)
        }
    }

    companion object {
        private const val TEST_PREFS_FILE = "loanjb_test_keystore_prefs"
    }
}

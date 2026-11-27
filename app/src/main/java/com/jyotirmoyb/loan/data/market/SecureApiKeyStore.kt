package com.jyotirmoyb.loan.data.market

import android.content.Context
import com.jyotirmoyb.loan.data.security.AesGcmCryptoEngine
import com.jyotirmoyb.loan.data.security.KeystoreEncryptedPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MarketApiProviderSlot(val prefKey: String, val displayName: String, val description: String, val endpointHint: String) {
    FX_EXCHANGERATE(
        prefKey = "api_key_fx_exchangerate",
        displayName = "USD–INR Live FX (ExchangeRate-API)",
        description = "Optional free API key from exchangerate-api.com for live USD/INR quotes.",
        endpointHint = "https://v6.exchangerate-api.com"
    ),
    METALS_BULLION(
        prefKey = "api_key_metals_bullion",
        displayName = "Gold & Silver (MetalpriceAPI)",
        description = "Optional API key from metalpriceapi.com for live Gold (10g) & Silver (1kg) in INR.",
        endpointHint = "https://api.metalpriceapi.com"
    ),
    EQUITY_INDICES(
        prefKey = "api_key_equity_twelvedata",
        displayName = "Sensex & Nifty 50 (Twelve Data)",
        description = "Optional API key from twelvedata.com for live BSE Sensex & NSE Nifty 50 levels.",
        endpointHint = "https://api.twelvedata.com"
    )
}

data class ApiKeyStatusSnapshot(
    val configuredSlots: Map<MarketApiProviderSlot, String> = emptyMap(), // slot -> masked preview
    val isHardwareEncrypted: Boolean = true
) {
    fun hasKey(slot: MarketApiProviderSlot): Boolean = !configuredSlots[slot].isNullOrBlank()
    val configuredCount: Int get() = configuredSlots.size
}

/**
 * Stores user-entered optional market API keys encrypted via Android Keystore AES-256-GCM
 * ([KeystoreEncryptedPrefs]) and performs a one-time migration from legacy EncryptedSharedPreferences.
 *
 * Security invariants:
 * - No API keys are ever hardcoded in source code or assets.
 * - API keys are never written to plaintext preferences, Room tables, backups, or logs.
 * - If Android Keystore is unavailable (e.g. JVM unit tests), falls back strictly to an in-memory map.
 */
class SecureApiKeyStore internal constructor(private val securePrefs: KeystoreEncryptedPrefs) {

    constructor(context: Context) : this(
        securePrefs = KeystoreEncryptedPrefs(
            context = context,
            prefsFileName = PREFS_FILE_NAME,
            keyAlias = KEYSTORE_ALIAS,
            legacyPrefsName = LEGACY_PREFS_FILE_NAME,
            legacyKeys = MarketApiProviderSlot.entries.map { it.prefKey }
        )
    )

    internal constructor(
        context: Context,
        cryptoEngineOverride: AesGcmCryptoEngine?,
        legacyReader: ((String, Collection<String>) -> Map<String, String>?)? = null,
        legacyDeleter: ((String) -> Boolean)? = null
    ) : this(
        securePrefs = KeystoreEncryptedPrefs(
            context = context,
            prefsFileName = PREFS_FILE_NAME,
            keyAlias = KEYSTORE_ALIAS,
            legacyPrefsName = LEGACY_PREFS_FILE_NAME,
            legacyKeys = MarketApiProviderSlot.entries.map { it.prefKey },
            cryptoEngineOverride = cryptoEngineOverride,
            legacyReader = legacyReader,
            legacyDeleter = legacyDeleter
        )
    )

    private val _statusFlow = MutableStateFlow(buildSnapshot())
    val statusFlow: Flow<ApiKeyStatusSnapshot> = _statusFlow.asStateFlow()

    fun getApiKey(slot: MarketApiProviderSlot): String? {
        val raw = securePrefs.getString(slot.prefKey)
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setApiKey(slot: MarketApiProviderSlot, apiKey: String) {
        val cleaned = apiKey.trim()
        if (cleaned.isEmpty()) {
            clearApiKey(slot)
            return
        }
        securePrefs.putString(slot.prefKey, cleaned)
        _statusFlow.value = buildSnapshot()
    }

    fun clearApiKey(slot: MarketApiProviderSlot) {
        securePrefs.remove(slot.prefKey)
        _statusFlow.value = buildSnapshot()
    }

    fun clearAllKeys() {
        securePrefs.clear()
        _statusFlow.value = buildSnapshot()
    }

    private fun buildSnapshot(): ApiKeyStatusSnapshot {
        val map = LinkedHashMap<MarketApiProviderSlot, String>()
        for (slot in MarketApiProviderSlot.entries) {
            val value = getApiKey(slot)
            if (!value.isNullOrBlank()) {
                map[slot] = maskKey(value)
            }
        }
        return ApiKeyStatusSnapshot(
            configuredSlots = map,
            isHardwareEncrypted = securePrefs.isHardwareBacked
        )
    }

    companion object {
        const val PREFS_FILE_NAME = "loanjb_secure_api_keys_v2"
        const val LEGACY_PREFS_FILE_NAME = "loanjb_secure_api_keys"
        private const val KEYSTORE_ALIAS = "loanjb_api_keys_aes_gcm_v1"

        fun maskKey(raw: String): String {
            val t = raw.trim()
            if (t.length <= 4) return "••••"
            return "••••••${t.takeLast(4)}"
        }
    }
}

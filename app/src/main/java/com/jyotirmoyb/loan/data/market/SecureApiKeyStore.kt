package com.jyotirmoyb.loan.data.market

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MarketApiProviderSlot(
    val prefKey: String,
    val displayName: String,
    val description: String,
    val endpointHint: String
) {
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
 * Stores user-entered optional market API keys strictly in Android EncryptedSharedPreferences
 * backed by an AES-256-GCM MasterKey in Android Keystore.
 *
 * Security invariants:
 * - No API keys are ever hardcoded in source code or assets.
 * - API keys are never written to plaintext preferences, Room tables, backups, or logs.
 * - If Android Keystore is unavailable (e.g. JVM unit tests), falls back strictly to an in-memory map.
 */
class SecureApiKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val inMemoryFallback = HashMap<String, String>()
    private var encryptedPrefs: SharedPreferences? = null
    private var usingEncryption: Boolean = false

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
        }.onSuccess {
            usingEncryption = true
        }.getOrNull()
    }

    private val _statusFlow = MutableStateFlow(buildSnapshot())
    val statusFlow: Flow<ApiKeyStatusSnapshot> = _statusFlow.asStateFlow()

    fun getApiKey(slot: MarketApiProviderSlot): String? {
        val raw = encryptedPrefs?.getString(slot.prefKey, null) ?: inMemoryFallback[slot.prefKey]
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setApiKey(slot: MarketApiProviderSlot, apiKey: String) {
        val cleaned = apiKey.trim()
        if (cleaned.isEmpty()) {
            clearApiKey(slot)
            return
        }
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().putString(slot.prefKey, cleaned).apply()
        } else {
            inMemoryFallback[slot.prefKey] = cleaned
        }
        _statusFlow.value = buildSnapshot()
    }

    fun clearApiKey(slot: MarketApiProviderSlot) {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().remove(slot.prefKey).apply()
        } else {
            inMemoryFallback.remove(slot.prefKey)
        }
        _statusFlow.value = buildSnapshot()
    }

    fun clearAllKeys() {
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit().clear().apply()
        } else {
            inMemoryFallback.clear()
        }
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
            isHardwareEncrypted = usingEncryption
        )
    }

    companion object {
        private const val PREFS_FILE_NAME = "loanjb_secure_api_keys"

        fun maskKey(raw: String): String {
            val t = raw.trim()
            if (t.length <= 4) return "••••"
            return "••••••${t.takeLast(4)}"
        }
    }
}

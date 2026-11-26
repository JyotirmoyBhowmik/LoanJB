package com.jyotirmoyb.loan.features.datarates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.market.ApiKeyStatusSnapshot
import com.jyotirmoyb.loan.data.market.MarketApiProviderSlot
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.data.remote.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DataRatesUiState(
    val items: List<ResolvedConfigItem> = emptyList(),
    val selectedCategory: ConfigCategory? = null,
    val useOnlineData: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus(),
    val overrideCount: Int = 0,
    val marketStaleHours: Int = 24,
    val apiKeyStatus: ApiKeyStatusSnapshot = ApiKeyStatusSnapshot(),
    val snackbarMessage: String? = null
) {
    val groupedItems: Map<ConfigCategory, List<ResolvedConfigItem>>
        get() {
            val filtered = if (selectedCategory == null) items else items.filter { it.category == selectedCategory }
            return ConfigCategory.entries.mapNotNull { cat ->
                val list = filtered.filter { it.category == cat }
                if (list.isEmpty()) null else cat to list
            }.toMap()
        }
}

class DataRatesViewModel(application: Application) : AndroidViewModel(application) {

    private val ratesRepository = AppContainer.provideRatesRepository(application)
    private val prefsRepository = AppContainer.providePreferencesRepository(application)
    private val secureApiKeyStore = AppContainer.provideSecureApiKeyStore(application)

    private val _selectedCategory = MutableStateFlow<ConfigCategory?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DataRatesUiState> = combine(
        ratesRepository.resolvedItemsFlow,
        ratesRepository.syncStatusFlow,
        prefsRepository.preferencesFlow,
        secureApiKeyStore.statusFlow,
        _selectedCategory,
        _snackbar
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val items = args[0] as List<ResolvedConfigItem>
        val syncStatus = args[1] as SyncStatus
        val prefs = args[2] as com.jyotirmoyb.loan.data.UserPreferences
        val apiStatus = args[3] as ApiKeyStatusSnapshot
        val selectedCat = args[4] as ConfigCategory?
        val snackbar = args[5] as String?

        DataRatesUiState(
            items = items,
            selectedCategory = selectedCat,
            useOnlineData = prefs.useOnlineData,
            syncStatus = syncStatus,
            overrideCount = items.count { it.isOverridden },
            marketStaleHours = prefs.marketStaleHours,
            apiKeyStatus = apiStatus,
            snackbarMessage = snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataRatesUiState())

    fun selectCategory(category: ConfigCategory?) {
        _selectedCategory.value = category
    }

    fun setUseOnlineData(enabled: Boolean) {
        viewModelScope.launch {
            ratesRepository.setUseOnlineData(enabled)
            _snackbar.value = if (enabled) {
                "Online remote config enabled"
            } else {
                "Offline mode: using built-in seed & manual overrides"
            }
        }
    }

    fun setMarketStaleHours(hours: Int) {
        viewModelScope.launch {
            prefsRepository.setMarketStaleHours(hours)
            _snackbar.value = "Market stale threshold set to ${hours}h"
        }
    }

    fun saveApiKey(slot: MarketApiProviderSlot, rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) {
            clearApiKey(slot)
            return
        }
        secureApiKeyStore.setApiKey(slot, cleaned)
        _snackbar.value = "Saved encrypted API key for ${slot.displayName}"
    }

    fun clearApiKey(slot: MarketApiProviderSlot) {
        secureApiKeyStore.clearApiKey(slot)
        _snackbar.value = "Cleared API key for ${slot.displayName}"
    }

    fun refreshNow() {
        viewModelScope.launch {
            val summary = ratesRepository.refreshNow()
            _snackbar.value = summary.message
        }
    }

    fun saveOverride(item: ResolvedConfigItem, newValue: String, onValidationError: (String) -> Unit, onSaved: () -> Unit) {
        viewModelScope.launch {
            val res = ratesRepository.setManualOverride(item, newValue)
            res.fold(
                onSuccess = {
                    _snackbar.value = "Updated ${item.label}"
                    onSaved()
                },
                onFailure = { err ->
                    onValidationError(err.message ?: "Invalid value")
                }
            )
        }
    }

    fun resetToDefault(item: ResolvedConfigItem) {
        viewModelScope.launch {
            ratesRepository.resetToDefault(item.key)
            _snackbar.value = "Reset ${item.label} to default"
        }
    }

    fun resetAllOverrides() {
        viewModelScope.launch {
            ratesRepository.resetAllOverrides()
            _snackbar.value = "All manual overrides reset to default"
        }
    }

    suspend fun exportOverridesJson(): String = ratesRepository.exportOverridesJson()

    fun importOverridesJson(rawJson: String) {
        viewModelScope.launch {
            val res = ratesRepository.importOverridesJson(rawJson)
            _snackbar.value = res.fold(
                onSuccess = { count -> "Imported $count manual override(s)" },
                onFailure = { err -> err.message ?: "Import failed" }
            )
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun showMessage(message: String) {
        _snackbar.value = message
    }
}

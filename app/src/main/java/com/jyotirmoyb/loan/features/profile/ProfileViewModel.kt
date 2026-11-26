package com.jyotirmoyb.loan.features.profile

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.RegionCode
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val preferences: UserPreferences = UserPreferences(),
    val catalog: LoanCatalog = LoanCatalog(),
    val savedLoansCount: Int = 0,
    val activeOverridesCount: Int = 0,
    val hasBiometricAvailable: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val isPreferencesLoaded: Boolean = false,
    val snackbarMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)
    private val appLockManager = AppContainer.provideAppLockManager(application)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _pinStateVersion = MutableStateFlow(0)

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.preferencesFlow,
        repository.catalogFlow,
        ratesRepository.resolvedMapFlow,
        repository.savedLoansFlow,
        _snackbar,
        _pinStateVersion
    ) { values ->
        val prefs = values[0] as UserPreferences
        val cat = values[1] as LoanCatalog
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = values[2] as Map<String, com.jyotirmoyb.loan.data.remote.ResolvedConfigItem>
        @Suppress("UNCHECKED_CAST")
        val loans = values[3] as List<com.jyotirmoyb.loan.data.SavedLoanEntity>
        val msg = values[4] as String?

        ProfileUiState(
            preferences = prefs,
            catalog = DataSourceResolver.resolveCatalog(cat, resolvedMap),
            savedLoansCount = loans.size,
            activeOverridesCount = resolvedMap.values.count { it.isOverridden },
            hasBiometricAvailable = appLockManager.isBiometricOrDeviceCredentialAvailable(),
            hasPinConfigured = appLockManager.hasPinConfigured(),
            isPreferencesLoaded = true,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun showMessage(msg: String) {
        _snackbar.value = msg
    }

    fun serializeCatalog(catalog: LoanCatalog): String = repository.serializeCatalog(catalog)

    suspend fun createEncryptedBackupJson(password: String): Result<String> =
        repository.createEncryptedBackupJson(password)

    fun setRegion(region: RegionCode) {
        viewModelScope.launch { repository.setRegion(region) }
    }

    fun setCurrency(currency: CurrencyCode) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguageCode(languageCode: String) {
        val normalized = if (languageCode == "hi") "hi" else "en"
        viewModelScope.launch {
            repository.setLanguageCode(normalized)
            runCatching {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
            }
        }
    }

    fun setMonthlyIncome(income: Double) {
        viewModelScope.launch { repository.setMonthlyIncome(income) }
    }

    fun setPreferredTaxRegime(regime: String) {
        viewModelScope.launch { repository.setPreferredTaxRegime(regime) }
    }

    fun setUserAge(age: Int) {
        viewModelScope.launch { repository.setUserAge(age) }
    }

    fun setRiskProfile(profile: String) {
        viewModelScope.launch { repository.setRiskProfile(profile) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppLockEnabled(enabled)
            _snackbar.value = if (enabled) "App Lock enabled" else "App Lock disabled"
        }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFlagSecureEnabled(enabled)
            _snackbar.value = if (enabled) {
                "Screenshot & app-switcher protection enabled"
            } else {
                "Screenshot protection turned off"
            }
        }
    }

    fun setAppPin(pin: String): Result<Unit> {
        val res = appLockManager.setPin(pin)
        if (res.isSuccess) _pinStateVersion.value++
        return res
    }

    fun verifyAppPin(pin: String): Boolean = appLockManager.verifyPin(pin)

    fun clearAppPin() {
        appLockManager.clearPin()
        _pinStateVersion.value++
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { repository.setOnboardingCompleted(completed) }
    }

    fun saveCatalogObject(updatedCatalog: LoanCatalog) {
        viewModelScope.launch {
            val res = repository.saveCatalog(updatedCatalog)
            _snackbar.value = res.fold(
                onSuccess = { "Catalog saved" },
                onFailure = { it.message ?: "Could not save catalog" }
            )
        }
    }

    fun saveCatalogRawJson(rawJson: String) {
        viewModelScope.launch {
            val res = repository.saveCatalogRawJson(rawJson)
            _snackbar.value = res.fold(
                onSuccess = { "Catalog JSON updated" },
                onFailure = { it.message ?: "Invalid JSON" }
            )
        }
    }

    fun resetCatalogToDefault() {
        viewModelScope.launch {
            repository.resetCatalogToDefault()
            _snackbar.value = "Catalog reset to default seed"
        }
    }

    fun restoreBackupJson(rawJson: String, password: String? = null) {
        viewModelScope.launch {
            val res = repository.restoreFromBackupJson(rawJson, password)
            _snackbar.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Restore failed" }
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            appLockManager.clearPin()
            repository.resetAllData()
            _pinStateVersion.value++
            _snackbar.value = "All local data has been reset"
        }
    }
}

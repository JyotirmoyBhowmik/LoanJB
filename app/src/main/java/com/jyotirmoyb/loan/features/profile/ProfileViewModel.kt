package com.jyotirmoyb.loan.features.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.LoanCatalog
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
    val isPreferencesLoaded: Boolean = false,
    val snackbarMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.preferencesFlow,
        repository.catalogFlow,
        repository.savedLoansFlow,
        _snackbar
    ) { prefs, cat, loans, msg ->
        ProfileUiState(
            preferences = prefs,
            catalog = cat,
            savedLoansCount = loans.size,
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

    suspend fun createBackupJson(): String = repository.createBackupJson()

    fun setCurrency(currency: CurrencyCode) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setMonthlyIncome(income: Double) {
        viewModelScope.launch { repository.setMonthlyIncome(income) }
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

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            val res = repository.restoreFromBackupJson(rawJson)
            _snackbar.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Restore failed" }
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.resetAllData()
            _snackbar.value = "All local data has been reset"
        }
    }
}

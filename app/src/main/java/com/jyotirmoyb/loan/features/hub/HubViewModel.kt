package com.jyotirmoyb.loan.features.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.features.CalculatorModule
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.CalculatorSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HubSectionGroup(val section: CalculatorSection, val modules: List<CalculatorModule>)

data class HubUiState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sections: List<HubSectionGroup> = emptyList(),
    val savedLoansCount: Int = 0,
    val catalogCategoriesCount: Int = 0,
    val showStaleDataWarning: Boolean = false,
    val isLoading: Boolean = false
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)
    private val prefsRepository = AppContainer.providePreferencesRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)

    val uiState: StateFlow<HubUiState> = combine(
        _searchQuery,
        _isSearchActive,
        repository.savedLoansFlow,
        repository.catalogFlow,
        ratesRepository.resolvedItemsFlow,
        prefsRepository.preferencesFlow
    ) { args ->
        val query = args[0] as String
        val searchActive = args[1] as Boolean
        val loans = args[2] as List<*>
        val catalog = args[3] as LoanCatalog

        @Suppress("UNCHECKED_CAST")
        val resolvedItems = args[4] as List<ResolvedConfigItem>
        val prefs = args[5] as UserPreferences

        val trimmed = query.trim().lowercase()
        val filtered = if (trimmed.isEmpty()) {
            CalculatorRegistry.modules
        } else {
            CalculatorRegistry.modules.filter { mod ->
                mod.title.lowercase().contains(trimmed) ||
                    mod.subtitle.lowercase().contains(trimmed) ||
                    mod.section.title.lowercase().contains(trimmed) ||
                    mod.section.tag.lowercase().contains(trimmed) ||
                    (mod.badgeText?.lowercase()?.contains(trimmed) == true)
            }
        }
        val grouped = CalculatorSection.entries.mapNotNull { section ->
            val mods = filtered.filter { it.section == section }
            if (mods.isEmpty()) null else HubSectionGroup(section, mods)
        }
        val isStale = resolvedItems.isNotEmpty() &&
            DataSourceResolver.isTaxOrLoanDataStale(
                items = resolvedItems,
                thresholdDays = prefs.staleDataThresholdDays
            )
        HubUiState(
            searchQuery = query,
            isSearchActive = searchActive,
            sections = grouped,
            savedLoansCount = loans.size,
            catalogCategoriesCount = catalog.categories.size,
            showStaleDataWarning = isStale,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HubUiState(
            sections = CalculatorSection.entries.map { sec ->
                HubSectionGroup(sec, CalculatorRegistry.modules.filter { it.section == sec })
            }
        )
    )

    fun toggleSearch() {
        val next = !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) {
            _searchQuery.value = ""
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}

package com.jyotirmoyb.loan.features.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.features.CalculatorModule
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.CalculatorSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HubSectionGroup(
    val section: CalculatorSection,
    val modules: List<CalculatorModule>
)

data class HubUiState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sections: List<HubSectionGroup> = emptyList(),
    val savedLoansCount: Int = 0,
    val catalogCategoriesCount: Int = 0,
    val isLoading: Boolean = false
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)

    val uiState: StateFlow<HubUiState> = combine(
        _searchQuery,
        _isSearchActive,
        repository.savedLoansFlow,
        repository.catalogFlow
    ) { query, searchActive, loans, catalog ->
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
        HubUiState(
            searchQuery = query,
            isSearchActive = searchActive,
            sections = grouped,
            savedLoansCount = loans.size,
            catalogCategoriesCount = catalog.categories.size,
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

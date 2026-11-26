package com.jyotirmoyb.loan.features.tax.incometax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.TaxYearRules
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.IncomeTaxComparisonResult
import com.jyotirmoyb.loan.domain.IncomeTaxEngine
import com.jyotirmoyb.loan.domain.IncomeTaxInput
import com.jyotirmoyb.loan.domain.TaxpayerAgeGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeductionRegimeFilter {
    ALL,
    NEW_ONLY,
    OLD_ONLY
}

data class IncomeTaxUiState(
    val currency: CurrencyCode = CurrencyCode.INR,
    val catalog: TaxRulesCatalog = emptyTaxCatalog(),
    val selectedFyCode: String = "",
    val input: IncomeTaxInput = IncomeTaxInput(
        grossSalary = 1500000.0,
        otherIncome = 50000.0,
        isSalaried = true,
        ageGroup = TaxpayerAgeGroup.GENERAL,
        parentsAreSenior = false,
        deductionAmounts = mapOf(
            "sec80c" to 150000.0,
            "sec80d_self" to 25000.0,
            "sec24b" to 0.0
        )
    ),
    val deductionFilter: DeductionRegimeFilter = DeductionRegimeFilter.ALL,
    val result: IncomeTaxComparisonResult = IncomeTaxComparisonResult(isValid = false),
    val slabBreakdownExpanded: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val syncFeedback: String? = null
) {
    val activeFyRules: TaxYearRules?
        get() = catalog.taxYears.firstOrNull { it.fyCode == selectedFyCode }
            ?: catalog.taxYears.firstOrNull()
}

private fun emptyTaxCatalog(): TaxRulesCatalog = TaxRulesCatalog(
    taxYears = emptyList(),
    gstSlabs = listOf(0.0, 3.0, 5.0, 12.0, 18.0, 28.0),
    advanceTaxThreshold = 10000.0,
    advanceTaxSchedule = emptyList(),
    hraMetroPercent = 50.0,
    hraNonMetroPercent = 40.0,
    fdTdsGeneralThreshold = 50000.0,
    fdTdsSeniorThreshold = 100000.0,
    tdsRateWithPan = 10.0,
    tdsRateWithoutPan = 20.0,
    dataVersion = "",
    publishedOn = "",
    asOfDateFormatted = "",
    sourceName = "Income Tax Act India",
    sourceUrl = "",
    lastVerified = UNVERIFIED_MARKER,
    source = ConfigValueSource.BUILT_IN
)

class IncomeTaxViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val ratesRepository: RatesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeTaxUiState())
    val uiState: StateFlow<IncomeTaxUiState> = _uiState.asStateFlow()

    private var hasCustomSalary = false
    private var hasCustomAgeGroup = false
    private var hasCustomFilter = false

    init {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                ratesRepository.taxRulesCatalogFlow,
                ratesRepository.syncStatusFlow
            ) { prefs, catalog, syncStatus ->
                Triple(prefs, catalog, syncStatus)
            }.collect { (prefs, catalog, syncStatus) ->
                _uiState.update { state ->
                    val fyCode = state.selectedFyCode
                        .takeIf { code -> catalog.taxYears.any { it.fyCode == code } }
                        ?: catalog.taxYears.firstOrNull()?.fyCode.orEmpty()
                    val fyRules = catalog.taxYears.firstOrNull { it.fyCode == fyCode }
                        ?: catalog.taxYears.firstOrNull()

                    val profileAgeGroup = when {
                        prefs.userAge >= 80 -> TaxpayerAgeGroup.SUPER_SENIOR
                        prefs.userAge >= 60 -> TaxpayerAgeGroup.SENIOR
                        else -> TaxpayerAgeGroup.GENERAL
                    }
                    val effectiveInput = state.input.copy(
                        grossSalary = if (hasCustomSalary) {
                            state.input.grossSalary
                        } else {
                            (prefs.monthlyIncome * 12.0).takeIf { it > 0.0 } ?: state.input.grossSalary
                        },
                        ageGroup = if (hasCustomAgeGroup) state.input.ageGroup else profileAgeGroup
                    )
                    val effectiveFilter = if (hasCustomFilter) {
                        state.deductionFilter
                    } else {
                        if (prefs.preferredTaxRegime == "OLD") DeductionRegimeFilter.OLD_ONLY else DeductionRegimeFilter.ALL
                    }

                    val computed = if (fyRules != null) {
                        IncomeTaxEngine.calculateComparison(effectiveInput, fyRules)
                    } else {
                        IncomeTaxComparisonResult(isValid = false)
                    }
                    state.copy(
                        currency = prefs.currency,
                        catalog = catalog,
                        selectedFyCode = fyCode,
                        input = effectiveInput,
                        deductionFilter = effectiveFilter,
                        result = computed,
                        isCheckingUpdates = syncStatus.isSyncing
                    )
                }
            }
        }
    }

    fun selectFy(fyCode: String) {
        _uiState.update { state ->
            val fyRules = state.catalog.taxYears.firstOrNull { it.fyCode == fyCode } ?: return@update state
            state.copy(
                selectedFyCode = fyCode,
                result = IncomeTaxEngine.calculateComparison(state.input, fyRules)
            )
        }
    }

    fun updateGrossSalary(amount: Double) {
        hasCustomSalary = true
        updateInput { it.copy(grossSalary = amount.coerceAtLeast(0.0)) }
    }

    fun updateOtherIncome(amount: Double) = updateInput { it.copy(otherIncome = amount.coerceAtLeast(0.0)) }

    fun toggleSalaried(salaried: Boolean) = updateInput { it.copy(isSalaried = salaried) }

    fun selectAgeGroup(ageGroup: TaxpayerAgeGroup) {
        hasCustomAgeGroup = true
        updateInput { it.copy(ageGroup = ageGroup) }
    }

    fun toggleParentsSenior(senior: Boolean) = updateInput { it.copy(parentsAreSenior = senior) }

    fun updateDeduction(id: String, amount: Double) = updateInput { current ->
        current.copy(deductionAmounts = current.deductionAmounts + (id to amount.coerceAtLeast(0.0)))
    }

    fun setDeductionFilter(filter: DeductionRegimeFilter) {
        hasCustomFilter = true
        _uiState.update { it.copy(deductionFilter = filter) }
    }

    fun toggleSlabBreakdown() {
        _uiState.update { it.copy(slabBreakdownExpanded = !it.slabBreakdownExpanded) }
    }

    fun resetDeductions() = updateInput { it.copy(deductionAmounts = emptyMap()) }

    fun checkForUpdatedTaxRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, syncFeedback = null) }
            val summary = ratesRepository.refreshNow()
            _uiState.update { it.copy(isCheckingUpdates = false, syncFeedback = summary.message) }
        }
    }

    private fun updateInput(transform: (IncomeTaxInput) -> IncomeTaxInput) {
        _uiState.update { state ->
            val updated = transform(state.input)
            val fyRules = state.activeFyRules
            val computed = if (fyRules != null) {
                IncomeTaxEngine.calculateComparison(updated, fyRules)
            } else {
                IncomeTaxComparisonResult(isValid = false)
            }
            state.copy(input = updated, result = computed)
        }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepository: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return IncomeTaxViewModel(prefsRepo, ratesRepository) as T
        }
    }
}

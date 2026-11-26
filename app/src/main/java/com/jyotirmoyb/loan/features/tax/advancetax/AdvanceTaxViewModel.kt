package com.jyotirmoyb.loan.features.tax.advancetax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.AdvanceTaxAndTdsEngine
import com.jyotirmoyb.loan.domain.AdvanceTaxInput
import com.jyotirmoyb.loan.domain.AdvanceTaxResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.FdTdsQuickResult
import com.jyotirmoyb.loan.domain.MfTaxConfig
import com.jyotirmoyb.loan.domain.SalaryTdsQuickResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdvanceTaxHelperTab {
    ADVANCE_TAX,
    TDS_HELPER,
    CAPITAL_GAINS
}

data class AdvanceTaxUiState(
    val currency: CurrencyCode = CurrencyCode.INR,
    val selectedTab: AdvanceTaxHelperTab = AdvanceTaxHelperTab.ADVANCE_TAX,
    val catalog: TaxRulesCatalog = TaxRulesCatalog(
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
    ),
    val mfTaxConfig: MfTaxConfig = MfTaxConfig(12, 125000.0, 12.5, 20.0, 24, 12.5, 4.0),
    // Advance Tax state
    val advanceInput: AdvanceTaxInput = AdvanceTaxInput(),
    val advanceResult: AdvanceTaxResult = AdvanceTaxAndTdsEngine.calculateAdvanceTax(AdvanceTaxInput(), 10000.0, emptyList()),
    // FD TDS state
    val fdAnnualInterest: Double = 75000.0,
    val fdIsSenior: Boolean = false,
    val fdHasPan: Boolean = true,
    val fdTdsResult: FdTdsQuickResult? = null,
    // Salary TDS state
    val salaryAnnualTax: Double = 140000.0,
    val salaryTdsDeducted: Double = 50000.0,
    val salaryRemainingMonths: Int = 6,
    val salaryTdsResult: SalaryTdsQuickResult = AdvanceTaxAndTdsEngine.calculateSalaryTds(140000.0, 50000.0, 6),
    val isCheckingUpdates: Boolean = false,
    val syncFeedback: String? = null
)

class AdvanceTaxViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val ratesRepository: RatesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvanceTaxUiState())
    val uiState: StateFlow<AdvanceTaxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                ratesRepository.taxRulesCatalogFlow,
                ratesRepository.resolvedMapFlow,
                ratesRepository.syncStatusFlow
            ) { prefs, catalog, resolvedMap, sync ->
                QuadState(prefs.currency, catalog, resolvedMap, sync.isSyncing)
            }.collect { (currency, catalog, resolvedMap, isSyncing) ->
                _uiState.update { state ->
                    val fy = state.advanceInput.fyCode
                        .takeIf { code -> catalog.taxYears.any { it.fyCode == code } }
                        ?: catalog.taxYears.firstOrNull()?.fyCode
                        ?: "FY 2026-27"
                    val updatedAdvInput = state.advanceInput.copy(fyCode = fy)
                    state.copy(
                        currency = currency,
                        catalog = catalog,
                        mfTaxConfig = MfTaxConfig.fromResolvedConfig(resolvedMap),
                        advanceInput = updatedAdvInput,
                        advanceResult = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
                            updatedAdvInput,
                            catalog.advanceTaxThreshold,
                            catalog.advanceTaxSchedule
                        ),
                        fdTdsResult = AdvanceTaxAndTdsEngine.calculateFdTds(
                            state.fdAnnualInterest,
                            state.fdIsSenior,
                            state.fdHasPan,
                            catalog
                        ),
                        isCheckingUpdates = isSyncing
                    )
                }
            }
        }
    }

    fun selectTab(tab: AdvanceTaxHelperTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectFy(fyCode: String) = updateAdvanceInput { it.copy(fyCode = fyCode) }
    fun updateEstimatedAnnualTax(amount: Double) = updateAdvanceInput { it.copy(estimatedAnnualTax = amount.coerceAtLeast(0.0)) }
    fun updateTdsDeducted(amount: Double) = updateAdvanceInput { it.copy(tdsTcsDeducted = amount.coerceAtLeast(0.0)) }
    fun togglePresumptive(enabled: Boolean) = updateAdvanceInput { it.copy(isPresumptive44AdAda = enabled) }
    fun toggleSeniorExempt(exempt: Boolean) = updateAdvanceInput { it.copy(isSeniorWithoutBusiness = exempt) }

    fun updateFdInterest(amount: Double) = updateFdTds { it.copy(fdAnnualInterest = amount.coerceAtLeast(0.0)) }
    fun toggleFdSenior(senior: Boolean) = updateFdTds { it.copy(fdIsSenior = senior) }
    fun toggleFdHasPan(hasPan: Boolean) = updateFdTds { it.copy(fdHasPan = hasPan) }

    fun updateSalaryAnnualTax(amount: Double) = updateSalaryTds { it.copy(salaryAnnualTax = amount.coerceAtLeast(0.0)) }
    fun updateSalaryTdsDeducted(amount: Double) = updateSalaryTds { it.copy(salaryTdsDeducted = amount.coerceAtLeast(0.0)) }
    fun updateSalaryRemainingMonths(months: Int) = updateSalaryTds { it.copy(salaryRemainingMonths = months.coerceIn(1, 12)) }

    fun checkForUpdatedTaxRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, syncFeedback = null) }
            val summary = ratesRepository.refreshNow()
            _uiState.update { it.copy(isCheckingUpdates = false, syncFeedback = summary.message) }
        }
    }

    private fun updateAdvanceInput(transform: (AdvanceTaxInput) -> AdvanceTaxInput) {
        _uiState.update { state ->
            val next = transform(state.advanceInput)
            state.copy(
                advanceInput = next,
                advanceResult = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
                    next,
                    state.catalog.advanceTaxThreshold,
                    state.catalog.advanceTaxSchedule
                )
            )
        }
    }

    private fun updateFdTds(transform: (AdvanceTaxUiState) -> AdvanceTaxUiState) {
        _uiState.update { state ->
            val next = transform(state)
            next.copy(
                fdTdsResult = AdvanceTaxAndTdsEngine.calculateFdTds(
                    next.fdAnnualInterest,
                    next.fdIsSenior,
                    next.fdHasPan,
                    next.catalog
                )
            )
        }
    }

    private fun updateSalaryTds(transform: (AdvanceTaxUiState) -> AdvanceTaxUiState) {
        _uiState.update { state ->
            val next = transform(state)
            next.copy(
                salaryTdsResult = AdvanceTaxAndTdsEngine.calculateSalaryTds(
                    next.salaryAnnualTax,
                    next.salaryTdsDeducted,
                    next.salaryRemainingMonths
                )
            )
        }
    }

    private data class QuadState(
        val currency: CurrencyCode,
        val catalog: TaxRulesCatalog,
        val resolvedMap: Map<String, com.jyotirmoyb.loan.data.remote.ResolvedConfigItem>,
        val isSyncing: Boolean
    )

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepository: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdvanceTaxViewModel(prefsRepo, ratesRepository) as T
        }
    }
}

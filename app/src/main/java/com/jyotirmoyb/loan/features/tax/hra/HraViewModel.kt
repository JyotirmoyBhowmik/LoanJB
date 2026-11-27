package com.jyotirmoyb.loan.features.tax.hra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.HraEngine
import com.jyotirmoyb.loan.domain.HraInput
import com.jyotirmoyb.loan.domain.HraResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HraUiState(
    val currency: CurrencyCode = CurrencyCode.INR,
    val isMonthlyInputMode: Boolean = false,
    val input: HraInput = HraInput(
        basicPlusDaAnnual = 600000.0,
        hraReceivedAnnual = 240000.0,
        actualRentPaidAnnual = 240000.0,
        isMetroCity = true,
        marginalTaxSlabPercent = 30.0
    ),
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
        sourceName = "Income Tax Rules 2A",
        sourceUrl = "",
        lastVerified = UNVERIFIED_MARKER,
        source = ConfigValueSource.BUILT_IN
    ),
    val result: HraResult = HraEngine.calculate(HraInput(), 50.0, 40.0, 4.0),
    val isCheckingUpdates: Boolean = false,
    val syncFeedback: String? = null
)

class HraViewModel(private val prefsRepo: UserPreferencesRepository, private val ratesRepository: RatesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HraUiState())
    val uiState: StateFlow<HraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                ratesRepository.taxRulesCatalogFlow,
                ratesRepository.syncStatusFlow
            ) { prefs, catalog, sync -> Triple(prefs, catalog, sync) }.collect { (prefs, catalog, sync) ->
                _uiState.update { state ->
                    val cess = catalog.taxYears.firstOrNull()?.cessPercent ?: 4.0
                    state.copy(
                        currency = prefs.currency,
                        catalog = catalog,
                        result = HraEngine.calculate(state.input, catalog.hraMetroPercent, catalog.hraNonMetroPercent, cess),
                        isCheckingUpdates = sync.isSyncing
                    )
                }
            }
        }
    }

    fun setMonthlyInputMode(monthly: Boolean) {
        _uiState.update { it.copy(isMonthlyInputMode = monthly) }
    }

    fun updateBasicPlusDa(displayedAmount: Double) = updateInput { state ->
        val annual = if (state.isMonthlyInputMode) displayedAmount * 12.0 else displayedAmount
        state.input.copy(basicPlusDaAnnual = annual.coerceAtLeast(0.0))
    }

    fun updateHraReceived(displayedAmount: Double) = updateInput { state ->
        val annual = if (state.isMonthlyInputMode) displayedAmount * 12.0 else displayedAmount
        state.input.copy(hraReceivedAnnual = annual.coerceAtLeast(0.0))
    }

    fun updateRentPaid(displayedAmount: Double) = updateInput { state ->
        val annual = if (state.isMonthlyInputMode) displayedAmount * 12.0 else displayedAmount
        state.input.copy(actualRentPaidAnnual = annual.coerceAtLeast(0.0))
    }

    fun setMetroCity(isMetro: Boolean) = updateInput { it.input.copy(isMetroCity = isMetro) }

    fun selectMarginalSlab(slabPercent: Double) = updateInput { it.input.copy(marginalTaxSlabPercent = slabPercent) }

    fun checkForUpdatedTaxRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, syncFeedback = null) }
            val summary = ratesRepository.refreshNow()
            _uiState.update { it.copy(isCheckingUpdates = false, syncFeedback = summary.message) }
        }
    }

    private fun updateInput(transform: (HraUiState) -> HraInput) {
        _uiState.update { state ->
            val updated = transform(state)
            val cess = state.catalog.taxYears.firstOrNull()?.cessPercent ?: 4.0
            state.copy(
                input = updated,
                result = HraEngine.calculate(updated, state.catalog.hraMetroPercent, state.catalog.hraNonMetroPercent, cess)
            )
        }
    }

    class Factory(private val prefsRepo: UserPreferencesRepository, private val ratesRepository: RatesRepository) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HraViewModel(prefsRepo, ratesRepository) as T
    }
}

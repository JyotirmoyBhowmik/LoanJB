package com.jyotirmoyb.loan.features.tax.gst

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.GstCalculationMode
import com.jyotirmoyb.loan.domain.GstEngine
import com.jyotirmoyb.loan.domain.GstInput
import com.jyotirmoyb.loan.domain.GstResult
import com.jyotirmoyb.loan.domain.GstSupplyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GstUiState(
    val currency: CurrencyCode = CurrencyCode.INR,
    val input: GstInput = GstInput(
        amount = 10000.0,
        gstRatePercent = 18.0,
        mode = GstCalculationMode.ADD_GST,
        supplyType = GstSupplyType.INTRA_STATE
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
        sourceName = "CBIC GST Council",
        sourceUrl = "",
        lastVerified = UNVERIFIED_MARKER,
        source = ConfigValueSource.BUILT_IN
    ),
    val result: GstResult = GstEngine.calculate(GstInput()),
    val isCheckingUpdates: Boolean = false,
    val syncFeedback: String? = null
)

class GstViewModel(private val prefsRepo: UserPreferencesRepository, private val ratesRepository: RatesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GstUiState())
    val uiState: StateFlow<GstUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefsRepo.preferencesFlow,
                ratesRepository.taxRulesCatalogFlow,
                ratesRepository.syncStatusFlow
            ) { prefs, catalog, sync -> Triple(prefs, catalog, sync) }.collect { (prefs, catalog, sync) ->
                _uiState.update { state ->
                    state.copy(
                        currency = prefs.currency,
                        catalog = catalog,
                        isCheckingUpdates = sync.isSyncing
                    )
                }
            }
        }
    }

    fun updateAmount(amount: Double) = updateInput { it.copy(amount = amount.coerceAtLeast(0.0)) }

    fun updateRate(ratePercent: Double) = updateInput { it.copy(gstRatePercent = ratePercent.coerceIn(0.0, 100.0)) }

    fun selectMode(mode: GstCalculationMode) = updateInput { it.copy(mode = mode) }

    fun selectSupplyType(supplyType: GstSupplyType) = updateInput { it.copy(supplyType = supplyType) }

    fun checkForUpdatedTaxRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, syncFeedback = null) }
            val summary = ratesRepository.refreshNow()
            _uiState.update { it.copy(isCheckingUpdates = false, syncFeedback = summary.message) }
        }
    }

    private fun updateInput(transform: (GstInput) -> GstInput) {
        _uiState.update { state ->
            val updated = transform(state.input)
            state.copy(input = updated, result = GstEngine.calculate(updated))
        }
    }

    class Factory(private val prefsRepo: UserPreferencesRepository, private val ratesRepository: RatesRepository) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GstViewModel(prefsRepo, ratesRepository) as T
    }
}

package com.jyotirmoyb.loan.features.retirement.govschemes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.RetirementSchemesCatalog
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.ApyResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.PensionSchemesEngine
import com.jyotirmoyb.loan.domain.PmsymResult
import com.jyotirmoyb.loan.domain.SeniorSchemePayoutResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class GovPensionUiState(
    val catalog: RetirementSchemesCatalog = emptyCatalog(),
    val currency: CurrencyCode = CurrencyCode.INR,
    // APY state
    val apyEntryAge: Int = 25,
    val apyPensionSlab: Int = 5000,
    val apyResult: ApyResult = ApyResult(isValid = false),
    // PM-SYM state
    val pmsymEntryAge: Int = 28,
    val pmsymMonthlyIncome: Double = 12000.0,
    val pmsymResult: PmsymResult = PmsymResult(isValid = false),
    // PMVVY & Senior Schemes state
    val selectedSeniorSchemeId: String = "pmvvy",
    val seniorInvestmentAmount: Double = 1500000.0,
    val seniorPayoutResult: SeniorSchemePayoutResult? = null
) {
    companion object {
        fun emptyCatalog() = RetirementSchemesCatalog(
            apyTable = emptyList(),
            apyCorpusMultipliers = emptyMap(),
            pmsymTable = emptyList(),
            pmsymAssuredPension = 3000.0,
            pmsymIncomeCeiling = 15000.0,
            seniorPensionSchemes = emptyList(),
            assetAllocationMatrix = emptyList(),
            dataVersion = "",
            publishedOn = "",
            asOfDateFormatted = "",
            sourceName = "",
            lastVerified = UNVERIFIED_MARKER,
            source = ConfigValueSource.BUILT_IN
        )
    }
}

class GovPensionViewModel(
    prefsRepo: UserPreferencesRepository,
    ratesRepo: RatesRepository
) : ViewModel() {

    private data class Draft(
        val apyEntryAge: Int = 25,
        val apyPensionSlab: Int = 5000,
        val pmsymEntryAge: Int = 28,
        val pmsymMonthlyIncome: Double = 12000.0,
        val selectedSeniorSchemeId: String = "pmvvy",
        val seniorInvestmentAmount: Double = 1500000.0
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<GovPensionUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.retirementSchemesCatalogFlow
    ) { d, prefs, catalog ->
        val apyRes = PensionSchemesEngine.calculateApy(
            entryAge = d.apyEntryAge,
            desiredMonthlyPension = d.apyPensionSlab,
            apyTable = catalog.apyTable,
            corpusMultipliers = catalog.apyCorpusMultipliers
        )
        val pmsymRes = PensionSchemesEngine.calculatePmsym(
            entryAge = d.pmsymEntryAge,
            monthlyIncome = d.pmsymMonthlyIncome,
            pmsymTable = catalog.pmsymTable,
            assuredPension = catalog.pmsymAssuredPension,
            incomeCeiling = catalog.pmsymIncomeCeiling
        )
        val selectedScheme = catalog.seniorPensionSchemes.firstOrNull { it.id == d.selectedSeniorSchemeId }
            ?: catalog.seniorPensionSchemes.firstOrNull()
        val seniorRes = selectedScheme?.let {
            PensionSchemesEngine.calculateSeniorSchemePayout(it, d.seniorInvestmentAmount)
        }

        GovPensionUiState(
            catalog = catalog,
            currency = prefs.currency,
            apyEntryAge = d.apyEntryAge,
            apyPensionSlab = d.apyPensionSlab,
            apyResult = apyRes,
            pmsymEntryAge = d.pmsymEntryAge,
            pmsymMonthlyIncome = d.pmsymMonthlyIncome,
            pmsymResult = pmsymRes,
            selectedSeniorSchemeId = selectedScheme?.id ?: d.selectedSeniorSchemeId,
            seniorInvestmentAmount = d.seniorInvestmentAmount,
            seniorPayoutResult = seniorRes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GovPensionUiState())

    fun updateApyAge(age: Int) {
        draft.update { it.copy(apyEntryAge = age.coerceIn(18, 40)) }
    }

    fun selectApySlab(slab: Int) {
        draft.update { it.copy(apyPensionSlab = slab) }
    }

    fun updatePmsymAge(age: Int) {
        draft.update { it.copy(pmsymEntryAge = age.coerceIn(18, 40)) }
    }

    fun updatePmsymIncome(income: Double) {
        draft.update { it.copy(pmsymMonthlyIncome = income.coerceAtLeast(0.0)) }
    }

    fun selectSeniorScheme(schemeId: String) {
        draft.update { it.copy(selectedSeniorSchemeId = schemeId) }
    }

    fun updateSeniorInvestment(amount: Double) {
        draft.update { it.copy(seniorInvestmentAmount = amount.coerceAtLeast(0.0)) }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepo: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GovPensionViewModel(prefsRepo, ratesRepo) as T
    }
}

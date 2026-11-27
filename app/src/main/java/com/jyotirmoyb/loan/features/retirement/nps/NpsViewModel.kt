package com.jyotirmoyb.loan.features.retirement.nps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.NpsEngine
import com.jyotirmoyb.loan.domain.NpsInput
import com.jyotirmoyb.loan.domain.NpsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class NpsUiState(
    val input: NpsInput = NpsInput(),
    val result: NpsResult = NpsEngine.calculate(NpsInput()),
    val currency: CurrencyCode = CurrencyCode.INR,
    val minAnnuityPercent: Double = 40.0,
    val smallCorpusLimit: Double = 500000.0,
    val expectedReturnItem: ResolvedConfigItem? = null,
    val minAnnuityItem: ResolvedConfigItem? = null,
    val annuityRateItem: ResolvedConfigItem? = null,
    val hasCustomRates: Boolean = false
)

class NpsViewModel(prefsRepo: UserPreferencesRepository, ratesRepo: RatesRepository) : ViewModel() {

    private data class Draft(
        val input: NpsInput = NpsInput(),
        val hasCustomReturn: Boolean = false,
        val hasCustomAnnuitySplit: Boolean = false,
        val hasCustomAnnuityRate: Boolean = false
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<NpsUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.resolvedMapFlow
    ) { d, prefs, resolvedMap ->
        val retItem = resolvedMap[ConfigKeys.SCHEME_NPS_EXPECTED_RETURN]
        val minAnnItem = resolvedMap[ConfigKeys.SCHEME_NPS_MIN_ANNUITY_PERCENT]
        val annRateItem = resolvedMap[ConfigKeys.SCHEME_NPS_ANNUITY_RATE]

        val defReturn = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_NPS_EXPECTED_RETURN, 10.0)
        val minAnnuity = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_NPS_MIN_ANNUITY_PERCENT, 40.0)
        val defAnnRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_NPS_ANNUITY_RATE, 6.0)
        val smallLimit = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_NPS_SMALL_CORPUS_LIMIT, 500000.0)

        val effectiveInput = d.input.copy(
            expectedReturnPercent = if (d.hasCustomReturn) d.input.expectedReturnPercent else defReturn,
            annuityAllocationPercent = if (d.hasCustomAnnuitySplit) d.input.annuityAllocationPercent else minAnnuity,
            annuityRatePercent = if (d.hasCustomAnnuityRate) d.input.annuityRatePercent else defAnnRate
        )

        NpsUiState(
            input = effectiveInput,
            result = NpsEngine.calculate(effectiveInput, minAnnuity, smallLimit),
            currency = prefs.currency,
            minAnnuityPercent = minAnnuity,
            smallCorpusLimit = smallLimit,
            expectedReturnItem = retItem,
            minAnnuityItem = minAnnItem,
            annuityRateItem = annRateItem,
            hasCustomRates = d.hasCustomReturn || d.hasCustomAnnuitySplit || d.hasCustomAnnuityRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NpsUiState())

    fun updateCurrentAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(18, 65)
            val exitAge = d.input.retirementAge.coerceAtLeast(clamped + 1)
            d.copy(input = d.input.copy(currentAge = clamped, retirementAge = exitAge))
        }
    }

    fun updateExitAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(d.input.currentAge + 1, 75)
            d.copy(input = d.input.copy(retirementAge = clamped))
        }
    }

    fun updateMonthlyContribution(amount: Double) {
        draft.update { it.copy(input = it.input.copy(monthlyContribution = amount.coerceAtLeast(0.0))) }
    }

    fun updateExistingCorpus(amount: Double) {
        draft.update { it.copy(input = it.input.copy(existingCorpus = amount.coerceAtLeast(0.0))) }
    }

    fun updateStepUp(pct: Double) {
        draft.update { it.copy(input = it.input.copy(annualStepUpPercent = pct.coerceIn(0.0, 20.0))) }
    }

    fun updateExpectedReturn(rate: Double) {
        draft.update { it.copy(input = it.input.copy(expectedReturnPercent = rate.coerceIn(0.0, 22.0)), hasCustomReturn = true) }
    }

    fun updateAnnuityAllocation(pct: Double) {
        draft.update { it.copy(input = it.input.copy(annuityAllocationPercent = pct.coerceIn(0.0, 100.0)), hasCustomAnnuitySplit = true) }
    }

    fun updateAnnuityRate(rate: Double) {
        draft.update { it.copy(input = it.input.copy(annuityRatePercent = rate.coerceIn(0.0, 15.0)), hasCustomAnnuityRate = true) }
    }

    fun resetDefaults() {
        draft.update { it.copy(hasCustomReturn = false, hasCustomAnnuitySplit = false, hasCustomAnnuityRate = false) }
    }

    class Factory(private val prefsRepo: UserPreferencesRepository, private val ratesRepo: RatesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NpsViewModel(prefsRepo, ratesRepo) as T
    }
}

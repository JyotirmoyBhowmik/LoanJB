package com.jyotirmoyb.loan.features.retirement.epf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.EpfConfigRules
import com.jyotirmoyb.loan.domain.EpfEngine
import com.jyotirmoyb.loan.domain.EpfInput
import com.jyotirmoyb.loan.domain.EpfResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class EpfUiState(
    val input: EpfInput = EpfInput(),
    val rules: EpfConfigRules = EpfConfigRules(),
    val result: EpfResult = EpfEngine.calculate(EpfInput()),
    val currency: CurrencyCode = CurrencyCode.INR,
    val epfRateItem: ResolvedConfigItem? = null,
    val wageCeilingItem: ResolvedConfigItem? = null,
    val hasCustomRate: Boolean = false
)

class EpfViewModel(prefsRepo: UserPreferencesRepository, ratesRepo: RatesRepository) : ViewModel() {

    private data class Draft(val input: EpfInput = EpfInput(), val hasCustomRate: Boolean = false)

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<EpfUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.resolvedMapFlow
    ) { d, prefs, resolvedMap ->
        val epfRateItem = resolvedMap[ConfigKeys.SCHEME_EPF_RATE]
        val ceilingItem = resolvedMap[ConfigKeys.SCHEME_EPF_WAGE_CEILING]

        val rules = EpfConfigRules(
            employeePercent = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_EMPLOYEE_PERCENT, 12.0),
            employerEpfPercent = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_EMPLOYER_EPF_PERCENT, 3.67),
            employerEpsPercent = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_EMPLOYER_EPS_PERCENT, 8.33),
            wageCeilingMonthly = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_WAGE_CEILING, 15000.0),
            epsMonthlyCap = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_EPS_MONTHLY_CAP, 1250.0)
        )
        val defaultRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_EPF_RATE, 8.25)
        val effectiveInput = d.input.copy(
            epfInterestRatePercent = if (d.hasCustomRate) d.input.epfInterestRatePercent else defaultRate
        )

        EpfUiState(
            input = effectiveInput,
            rules = rules,
            result = EpfEngine.calculate(effectiveInput, rules),
            currency = prefs.currency,
            epfRateItem = epfRateItem,
            wageCeilingItem = ceilingItem,
            hasCustomRate = d.hasCustomRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpfUiState())

    fun updateCurrentAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(18, 57)
            val retAge = d.input.retirementAge.coerceAtLeast(clamped + 1)
            d.copy(input = d.input.copy(currentAge = clamped, retirementAge = retAge))
        }
    }

    fun updateRetirementAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(d.input.currentAge + 1, 65)
            d.copy(input = d.input.copy(retirementAge = clamped))
        }
    }

    fun updateBasicDa(amount: Double) {
        draft.update { it.copy(input = it.input.copy(monthlyBasicPlusDa = amount.coerceAtLeast(0.0))) }
    }

    fun updateExistingBalance(amount: Double) {
        draft.update { it.copy(input = it.input.copy(existingEpfBalance = amount.coerceAtLeast(0.0))) }
    }

    fun updateVpf(amount: Double) {
        draft.update { it.copy(input = it.input.copy(vpfExtraMonthly = amount.coerceAtLeast(0.0))) }
    }

    fun updateSalaryGrowth(pct: Double) {
        draft.update { it.copy(input = it.input.copy(annualSalaryGrowthPercent = pct.coerceIn(0.0, 20.0))) }
    }

    fun toggleWageCeiling(enabled: Boolean) {
        draft.update { it.copy(input = it.input.copy(applyWageCeiling = enabled)) }
    }

    fun updateInterestRate(rate: Double) {
        draft.update { it.copy(input = it.input.copy(epfInterestRatePercent = rate.coerceIn(0.0, 18.0)), hasCustomRate = true) }
    }

    fun resetRateToDefault() {
        draft.update { it.copy(hasCustomRate = false) }
    }

    class Factory(private val prefsRepo: UserPreferencesRepository, private val ratesRepo: RatesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EpfViewModel(prefsRepo, ratesRepo) as T
    }
}

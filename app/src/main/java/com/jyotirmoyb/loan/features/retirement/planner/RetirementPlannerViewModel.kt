package com.jyotirmoyb.loan.features.retirement.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.RetirementPlannerEngine
import com.jyotirmoyb.loan.domain.RetirementPlannerInput
import com.jyotirmoyb.loan.domain.RetirementPlannerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RetirementPlannerUiState(
    val input: RetirementPlannerInput = RetirementPlannerInput(),
    val result: RetirementPlannerResult = RetirementPlannerEngine.calculate(RetirementPlannerInput()),
    val currency: CurrencyCode = CurrencyCode.INR,
    val inflationItem: ResolvedConfigItem? = null,
    val preReturnItem: ResolvedConfigItem? = null,
    val postReturnItem: ResolvedConfigItem? = null,
    val hasCustomInflation: Boolean = false,
    val hasCustomPreReturn: Boolean = false,
    val hasCustomPostReturn: Boolean = false
)

class RetirementPlannerViewModel(
    prefsRepo: UserPreferencesRepository,
    ratesRepo: RatesRepository
) : ViewModel() {

    private data class DraftState(
        val input: RetirementPlannerInput = RetirementPlannerInput(),
        val hasCustomAge: Boolean = false,
        val hasCustomInflation: Boolean = false,
        val hasCustomPreReturn: Boolean = false,
        val hasCustomPostReturn: Boolean = false
    )

    private val draft = MutableStateFlow(DraftState())

    val uiState: StateFlow<RetirementPlannerUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.resolvedMapFlow
    ) { d, prefs, resolvedMap ->
        val infItem = resolvedMap[ConfigKeys.SCHEME_RETIREMENT_INFLATION]
        val preItem = resolvedMap[ConfigKeys.SCHEME_RETIREMENT_PRE_RETURN]
        val postItem = resolvedMap[ConfigKeys.SCHEME_RETIREMENT_POST_RETURN]

        val defaultInf = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_RETIREMENT_INFLATION, 6.0)
        val defaultPre = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_RETIREMENT_PRE_RETURN, 12.0)
        val defaultPost = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_RETIREMENT_POST_RETURN, 7.5)

        val effAge = if (d.hasCustomAge) d.input.currentAge else prefs.userAge.coerceIn(18, 70)
        val effRetAge = d.input.retirementAge.coerceAtLeast(effAge + 1)
        val effLifeExp = d.input.lifeExpectancy.coerceAtLeast(effRetAge + 5)

        val effectiveInput = d.input.copy(
            currentAge = effAge,
            retirementAge = effRetAge,
            lifeExpectancy = effLifeExp,
            inflationPercent = if (d.hasCustomInflation) d.input.inflationPercent else defaultInf,
            preRetirementReturnPercent = if (d.hasCustomPreReturn) d.input.preRetirementReturnPercent else defaultPre,
            postRetirementReturnPercent = if (d.hasCustomPostReturn) d.input.postRetirementReturnPercent else defaultPost
        )

        RetirementPlannerUiState(
            input = effectiveInput,
            result = RetirementPlannerEngine.calculate(effectiveInput),
            currency = prefs.currency,
            inflationItem = infItem,
            preReturnItem = preItem,
            postReturnItem = postItem,
            hasCustomInflation = d.hasCustomInflation,
            hasCustomPreReturn = d.hasCustomPreReturn,
            hasCustomPostReturn = d.hasCustomPostReturn
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetirementPlannerUiState())

    fun updateCurrentAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(18, 70)
            val retAge = d.input.retirementAge.coerceAtLeast(clamped + 1)
            val lifeExp = d.input.lifeExpectancy.coerceAtLeast(retAge + 5)
            d.copy(
                input = d.input.copy(currentAge = clamped, retirementAge = retAge, lifeExpectancy = lifeExp),
                hasCustomAge = true
            )
        }
    }

    fun updateRetirementAge(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(d.input.currentAge + 1, 80)
            val lifeExp = d.input.lifeExpectancy.coerceAtLeast(clamped + 1)
            d.copy(input = d.input.copy(retirementAge = clamped, lifeExpectancy = lifeExp))
        }
    }

    fun updateLifeExpectancy(age: Int) {
        draft.update { d ->
            val clamped = age.coerceIn(d.input.retirementAge + 1, 100)
            d.copy(input = d.input.copy(lifeExpectancy = clamped))
        }
    }

    fun updateMonthlyExpenses(amount: Double) {
        draft.update { it.copy(input = it.input.copy(currentMonthlyExpenses = amount.coerceAtLeast(0.0))) }
    }

    fun updateExistingSavings(amount: Double) {
        draft.update { it.copy(input = it.input.copy(existingRetirementSavings = amount.coerceAtLeast(0.0))) }
    }

    fun updateCurrentSip(amount: Double) {
        draft.update { it.copy(input = it.input.copy(currentMonthlySip = amount.coerceAtLeast(0.0))) }
    }

    fun updateInflation(rate: Double) {
        draft.update {
            it.copy(
                input = it.input.copy(inflationPercent = rate.coerceIn(0.0, 18.0)),
                hasCustomInflation = true
            )
        }
    }

    fun updatePreReturn(rate: Double) {
        draft.update {
            it.copy(
                input = it.input.copy(preRetirementReturnPercent = rate.coerceIn(0.0, 28.0)),
                hasCustomPreReturn = true
            )
        }
    }

    fun updatePostReturn(rate: Double) {
        draft.update {
            it.copy(
                input = it.input.copy(postRetirementReturnPercent = rate.coerceIn(0.0, 20.0)),
                hasCustomPostReturn = true
            )
        }
    }

    fun resetRatesToDefault() {
        draft.update {
            it.copy(
                hasCustomInflation = false,
                hasCustomPreReturn = false,
                hasCustomPostReturn = false
            )
        }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepo: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RetirementPlannerViewModel(prefsRepo, ratesRepo) as T
    }
}

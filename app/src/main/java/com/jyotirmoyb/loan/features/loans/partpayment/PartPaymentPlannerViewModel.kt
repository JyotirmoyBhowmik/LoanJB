package com.jyotirmoyb.loan.features.loans.partpayment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.PartPaymentPlanInput
import com.jyotirmoyb.loan.domain.PartPaymentPlanResult
import com.jyotirmoyb.loan.domain.PartPaymentPlannerEngine
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PartPaymentPlannerUiState(
    val input: PartPaymentPlanInput = PartPaymentPlanInput(),
    val rateSourceItem: ResolvedConfigItem? = null,
    val result: PartPaymentPlanResult = PartPaymentPlannerEngine.plan(PartPaymentPlanInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

class PartPaymentPlannerViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<PartPaymentPlanInput, PartPaymentPlanResult> {

    override val id: String = "part_payment_planner"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(PartPaymentPlanInput())
    private val _userEditedRate = MutableStateFlow(false)

    val uiState: StateFlow<PartPaymentPlannerUiState> = combine(
        _input,
        _userEditedRate,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { rawInput, editedRate, prefs, resolvedMap ->
        val defaultRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.HOME_RATE_MIN, 8.75)
        val effectiveInput = rawInput.copy(
            annualRatePercent = if (editedRate) rawInput.annualRatePercent else defaultRate
        )
        PartPaymentPlannerUiState(
            input = effectiveInput,
            rateSourceItem = resolvedMap[ConfigKeys.HOME_RATE_MIN],
            result = compute(effectiveInput),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartPaymentPlannerUiState())

    override fun compute(input: PartPaymentPlanInput): PartPaymentPlanResult =
        PartPaymentPlannerEngine.plan(input)

    fun updatePrincipal(v: Double) {
        _input.value = _input.value.copy(principal = v.coerceAtLeast(0.0))
    }

    fun updateRate(v: Double) {
        _userEditedRate.value = true
        _input.value = _input.value.copy(annualRatePercent = v.coerceIn(0.0, 35.0))
    }

    fun resetRateToDefault() {
        _userEditedRate.value = false
    }

    fun updateRemainingMonths(months: Int) {
        val clamped = months.coerceIn(12, 360)
        val cur = _input.value
        val currentTargetMonths = ((cur.targetPayoffYear - cur.startYear) * 12) + (cur.targetPayoffMonth - cur.startMonth)
        val adjustedTarget = if (currentTargetMonths >= clamped || currentTargetMonths < 6) {
            (clamped * 3 / 4).coerceAtLeast(6)
        } else {
            currentTargetMonths
        }
        val (ty, tm) = PartPaymentPlannerEngine.addMonthsToYearMonth(cur.startYear, cur.startMonth, adjustedTarget)
        _input.value = cur.copy(
            remainingMonths = clamped,
            targetPayoffYear = ty,
            targetPayoffMonth = tm
        )
    }

    fun setTargetYearMonth(year: Int, month: Int) {
        _input.value = _input.value.copy(
            targetPayoffYear = year.coerceIn(2026, 2075),
            targetPayoffMonth = month.coerceIn(1, 12)
        )
    }

    fun applyYearsEarlierPreset(yearsEarlier: Int) {
        val cur = _input.value
        val targetMonths = (cur.remainingMonths - yearsEarlier * 12).coerceAtLeast(6)
        val (ty, tm) = PartPaymentPlannerEngine.addMonthsToYearMonth(cur.startYear, cur.startMonth, targetMonths)
        _input.value = cur.copy(targetPayoffYear = ty, targetPayoffMonth = tm)
    }
}

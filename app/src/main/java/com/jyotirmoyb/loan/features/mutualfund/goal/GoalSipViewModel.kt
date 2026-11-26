package com.jyotirmoyb.loan.features.mutualfund.goal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.GoalSipInput
import com.jyotirmoyb.loan.domain.GoalSipResult
import com.jyotirmoyb.loan.domain.SipLumpsumEngine
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class GoalSipUiState(
    val input: GoalSipInput = defaultGoalInput(),
    val returnText: String = "12",
    val inflationText: String = "6",
    val stepUpText: String = "0",
    val result: GoalSipResult = SipLumpsumEngine.calculateGoalSip(defaultGoalInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

private fun defaultGoalInput(): GoalSipInput {
    val cal = Calendar.getInstance()
    return GoalSipInput(
        targetCorpusToday = 10000000.0, // ₹1 Cr
        years = 15,
        expectedReturnPercent = 12.0,
        inflationPercent = 6.0,
        adjustTargetForInflation = true,
        annualStepUpPercent = 0.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1
    )
}

class GoalSipViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<GoalSipInput, GoalSipResult> {

    override val id: String = "mf_goal"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultGoalInput())
    private val _returnText = MutableStateFlow("12")
    private val _inflationText = MutableStateFlow("6")
    private val _stepUpText = MutableStateFlow("0")
    private val _customReturn = MutableStateFlow(false)
    private val _customInflation = MutableStateFlow(false)

    val uiState: StateFlow<GoalSipUiState> = combine(
        _input,
        _returnText,
        _inflationText,
        _stepUpText,
        _customReturn,
        _customInflation,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { arr ->
        val raw = arr[0] as GoalSipInput
        val retStr = arr[1] as String
        val infStr = arr[2] as String
        val stepStr = arr[3] as String
        val customRet = arr[4] as Boolean
        val customInf = arr[5] as Boolean
        val prefs = arr[6] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val resolved = arr[7] as Map<String, ResolvedConfigItem>

        val defaultRet = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_EQUITY_EXPECTED_RETURN, 12.0)
        val defaultInf = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_DEFAULT_INFLATION, 6.0)
        val effective = raw.copy(
            expectedReturnPercent = if (customRet) raw.expectedReturnPercent else defaultRet,
            inflationPercent = if (customInf) raw.inflationPercent else defaultInf
        )

        GoalSipUiState(
            input = effective,
            returnText = if (customRet) retStr else fmt(defaultRet),
            inflationText = if (customInf) infStr else fmt(defaultInf),
            stepUpText = stepStr,
            result = compute(effective),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalSipUiState())

    override fun compute(input: GoalSipInput): GoalSipResult = SipLumpsumEngine.calculateGoalSip(input)

    fun updateTargetCorpus(amount: Double) {
        _input.value = _input.value.copy(targetCorpusToday = amount)
    }

    fun updateYears(years: Int) {
        _input.value = _input.value.copy(years = years.coerceIn(1, 50))
    }

    fun setAdjustForInflation(enabled: Boolean) {
        _input.value = _input.value.copy(adjustTargetForInflation = enabled)
    }

    fun updateReturnRate(text: String) {
        _returnText.value = text
        _customReturn.value = true
        text.toDoubleOrNull()?.let { _input.value = _input.value.copy(expectedReturnPercent = it) }
    }

    fun updateInflation(text: String) {
        _inflationText.value = text
        _customInflation.value = true
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(inflationPercent = v.coerceIn(0.0, 25.0))
    }

    fun updateStepUp(text: String) {
        _stepUpText.value = text
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(annualStepUpPercent = v.coerceIn(0.0, 50.0))
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

package com.jyotirmoyb.loan.features.mutualfund.swp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.SwpEngine
import com.jyotirmoyb.loan.domain.SwpInput
import com.jyotirmoyb.loan.domain.SwpResult
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SwpUiState(
    val input: SwpInput = defaultSwpInput(),
    val returnText: String = "8",
    val stepUpText: String = "0",
    val result: SwpResult = SwpEngine.calculate(defaultSwpInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

private fun defaultSwpInput(): SwpInput {
    val cal = Calendar.getInstance()
    return SwpInput(
        initialCorpus = 5000000.0,
        monthlyWithdrawal = 40000.0,
        expectedReturnPercent = 8.0,
        tenureYears = 20,
        annualWithdrawalStepUpPercent = 0.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1
    )
}

class SwpViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<SwpInput, SwpResult> {

    override val id: String = "swp"

    private val repository = AppContainer.provideRepository(application)
    private val _input = MutableStateFlow(defaultSwpInput())
    private val _returnText = MutableStateFlow("8")
    private val _stepUpText = MutableStateFlow("0")

    val uiState: StateFlow<SwpUiState> = combine(
        _input,
        _returnText,
        _stepUpText,
        repository.preferencesFlow
    ) { inp, retStr, stepStr, prefs: UserPreferences ->
        SwpUiState(
            input = inp,
            returnText = retStr,
            stepUpText = stepStr,
            result = compute(inp),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SwpUiState())

    override fun compute(input: SwpInput): SwpResult = SwpEngine.calculate(input)

    fun updateCorpus(amount: Double) {
        _input.value = _input.value.copy(initialCorpus = amount)
    }

    fun updateMonthlyWithdrawal(amount: Double) {
        _input.value = _input.value.copy(monthlyWithdrawal = amount)
    }

    fun updateReturnRate(text: String) {
        _returnText.value = text
        text.toDoubleOrNull()?.let { _input.value = _input.value.copy(expectedReturnPercent = it) }
    }

    fun updateStepUp(text: String) {
        _stepUpText.value = text
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(annualWithdrawalStepUpPercent = v.coerceIn(0.0, 25.0))
    }

    fun updateTenureYears(years: Int) {
        _input.value = _input.value.copy(tenureYears = years.coerceIn(1, 50))
    }

    fun applySustainableWithdrawal() {
        val sustained = compute(_input.value).sustainableMonthlyWithdrawal
        if (sustained > 0.0) {
            _input.value = _input.value.copy(monthlyWithdrawal = sustained, annualWithdrawalStepUpPercent = 0.0)
            _stepUpText.value = "0"
        }
    }
}

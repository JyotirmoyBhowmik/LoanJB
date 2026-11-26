package com.jyotirmoyb.loan.features.mutualfund.lumpsum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LumpsumInput
import com.jyotirmoyb.loan.domain.LumpsumResult
import com.jyotirmoyb.loan.domain.SipLumpsumEngine
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class LumpsumUiState(
    val input: LumpsumInput = defaultLumpsumInput(),
    val returnRateText: String = "12",
    val inflationText: String = "6",
    val isReturnCustom: Boolean = false,
    val returnRateItem: ResolvedConfigItem? = null,
    val result: LumpsumResult = SipLumpsumEngine.calculateLumpsum(defaultLumpsumInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

private fun defaultLumpsumInput(): LumpsumInput {
    val cal = Calendar.getInstance()
    return LumpsumInput(
        principal = 500000.0,
        expectedReturnPercent = 12.0,
        tenureYears = 10,
        inflationPercent = 6.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1
    )
}

class LumpsumViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<LumpsumInput, LumpsumResult> {

    override val id: String = "lumpsum"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultLumpsumInput())
    private val _returnText = MutableStateFlow("12")
    private val _inflationText = MutableStateFlow("6")
    private val _customReturn = MutableStateFlow(false)
    private val _customInflation = MutableStateFlow(false)

    val uiState: StateFlow<LumpsumUiState> = combine(
        _input,
        _returnText,
        _inflationText,
        _customReturn,
        _customInflation,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { arr ->
        val raw = arr[0] as LumpsumInput
        val retStr = arr[1] as String
        val infStr = arr[2] as String
        val customRet = arr[3] as Boolean
        val customInf = arr[4] as Boolean
        val prefs = arr[5] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val resolved = arr[6] as Map<String, ResolvedConfigItem>

        val defaultRet = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_EQUITY_EXPECTED_RETURN, 12.0)
        val defaultInf = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_DEFAULT_INFLATION, 6.0)
        val effective = raw.copy(
            expectedReturnPercent = if (customRet) raw.expectedReturnPercent else defaultRet,
            inflationPercent = if (customInf) raw.inflationPercent else defaultInf
        )

        LumpsumUiState(
            input = effective,
            returnRateText = if (customRet) retStr else fmt(defaultRet),
            inflationText = if (customInf) infStr else fmt(defaultInf),
            isReturnCustom = customRet,
            returnRateItem = resolved[ConfigKeys.MF_EQUITY_EXPECTED_RETURN],
            result = compute(effective),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LumpsumUiState())

    override fun compute(input: LumpsumInput): LumpsumResult = SipLumpsumEngine.calculateLumpsum(input)

    fun updatePrincipal(amount: Double) {
        _input.value = _input.value.copy(principal = amount)
    }

    fun updateReturnRate(text: String) {
        _returnText.value = text
        _customReturn.value = true
        text.toDoubleOrNull()?.let { _input.value = _input.value.copy(expectedReturnPercent = it) }
    }

    fun resetReturn() {
        _customReturn.value = false
    }

    fun updateInflation(text: String) {
        _inflationText.value = text
        _customInflation.value = true
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(inflationPercent = v.coerceIn(0.0, 25.0))
    }

    fun updateTenureYears(years: Int) {
        _input.value = _input.value.copy(tenureYears = years.coerceIn(1, 50))
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

package com.jyotirmoyb.loan.features.mutualfund.sip

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.SipInput
import com.jyotirmoyb.loan.domain.SipLumpsumEngine
import com.jyotirmoyb.loan.domain.SipResult
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SipUiState(
    val input: SipInput = defaultSipInput(),
    val returnRateText: String = "12",
    val stepUpText: String = "10",
    val inflationText: String = "6",
    val isReturnCustom: Boolean = false,
    val selectedFundLabel: String? = null,
    val returnRateItem: ResolvedConfigItem? = null,
    val inflationItem: ResolvedConfigItem? = null,
    val result: SipResult = SipLumpsumEngine.calculateSip(defaultSipInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

private fun defaultSipInput(): SipInput {
    val cal = Calendar.getInstance()
    return SipInput(
        monthlySip = 10000.0,
        expectedReturnPercent = 12.0,
        tenureYears = 10,
        annualStepUpPercent = 10.0,
        inflationPercent = 6.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1
    )
}

class SipViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<SipInput, SipResult> {

    override val id: String = "sip"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultSipInput())
    private val _returnRateText = MutableStateFlow("12")
    private val _stepUpText = MutableStateFlow("10")
    private val _inflationText = MutableStateFlow("6")
    private val _customReturn = MutableStateFlow(false)
    private val _customInflation = MutableStateFlow(false)
    private val _selectedFundLabel = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SipUiState> = combine(
        _input,
        _returnRateText,
        _stepUpText,
        _inflationText,
        _customReturn,
        _customInflation,
        _selectedFundLabel,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { arr ->
        val raw = arr[0] as SipInput
        val retStr = arr[1] as String
        val stepStr = arr[2] as String
        val infStr = arr[3] as String
        val customRet = arr[4] as Boolean
        val customInf = arr[5] as Boolean
        val fundLabel = arr[6] as String?
        val prefs = arr[7] as UserPreferences

        @Suppress("UNCHECKED_CAST")
        val resolved = arr[8] as Map<String, ResolvedConfigItem>

        val defaultRet = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_EQUITY_EXPECTED_RETURN, 12.0)
        val defaultInf = DataSourceResolver.resolveDouble(resolved, ConfigKeys.MF_DEFAULT_INFLATION, 6.0)

        val effective = raw.copy(
            expectedReturnPercent = if (customRet) raw.expectedReturnPercent else defaultRet,
            inflationPercent = if (customInf) raw.inflationPercent else defaultInf
        )

        SipUiState(
            input = effective,
            returnRateText = if (customRet) retStr else fmt(defaultRet),
            stepUpText = stepStr,
            inflationText = if (customInf) infStr else fmt(defaultInf),
            isReturnCustom = customRet || fundLabel != null,
            selectedFundLabel = fundLabel,
            returnRateItem = resolved[ConfigKeys.MF_EQUITY_EXPECTED_RETURN],
            inflationItem = resolved[ConfigKeys.MF_DEFAULT_INFLATION],
            result = compute(effective),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SipUiState())

    override fun compute(input: SipInput): SipResult = SipLumpsumEngine.calculateSip(input)

    fun updateMonthlySip(amount: Double) {
        _input.value = _input.value.copy(monthlySip = amount)
    }

    fun updateReturnRateText(text: String) {
        _returnRateText.value = text
        _customReturn.value = true
        text.toDoubleOrNull()?.let { r ->
            _input.value = _input.value.copy(expectedReturnPercent = r)
        }
    }

    fun updateStepUpText(text: String) {
        _stepUpText.value = text
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(annualStepUpPercent = v.coerceIn(0.0, 50.0))
    }

    fun updateInflationText(text: String) {
        _inflationText.value = text
        _customInflation.value = true
        val v = text.toDoubleOrNull() ?: 0.0
        _input.value = _input.value.copy(inflationPercent = v.coerceIn(0.0, 25.0))
    }

    fun updateTenureYears(years: Int) {
        _input.value = _input.value.copy(tenureYears = years.coerceIn(1, 50))
    }

    fun applyFundCagrPreset(fundName: String, navInfo: String, cagrPercent: Double) {
        val clamped = cagrPercent.coerceIn(0.5, 45.0)
        _selectedFundLabel.value = "$fundName ($navInfo)"
        _customReturn.value = true
        _returnRateText.value = fmt(clamped)
        _input.value = _input.value.copy(expectedReturnPercent = clamped)
    }

    fun resetReturnToDefault() {
        _customReturn.value = false
        _selectedFundLabel.value = null
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

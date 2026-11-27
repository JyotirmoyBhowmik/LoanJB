package com.jyotirmoyb.loan.features.deposits.smallsavings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.SmallSavingsConfig
import com.jyotirmoyb.loan.domain.SmallSavingsEngine
import com.jyotirmoyb.loan.domain.SmallSavingsInput
import com.jyotirmoyb.loan.domain.SmallSavingsResult
import com.jyotirmoyb.loan.domain.SmallSavingsSchemeType
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmallSavingsUiState(
    val input: SmallSavingsInput = defaultSmallSavingsInput(),
    val config: SmallSavingsConfig = SmallSavingsConfig.fromResolvedConfig(emptyMap()),
    val activeRateKey: String = ConfigKeys.SCHEME_PPF_RATE,
    val activeRateItem: ResolvedConfigItem? = null,
    val quarterItem: ResolvedConfigItem? = null,
    val rateOverrideText: String = "7.1",
    val isRateOverridden: Boolean = false,
    val result: SmallSavingsResult = SmallSavingsEngine.calculate(
        defaultSmallSavingsInput(),
        SmallSavingsConfig.fromResolvedConfig(emptyMap())
    ),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

private fun defaultSmallSavingsInput(): SmallSavingsInput {
    val cal = Calendar.getInstance()
    return SmallSavingsInput(
        scheme = SmallSavingsSchemeType.PPF,
        contributionAmount = 150000.0,
        ppfExtensionBlocks = 0,
        ppfContributeDuringExtension = true,
        pomisJointAccount = false,
        potdTenureYears = 5,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1,
        startDay = cal.get(Calendar.DAY_OF_MONTH)
    )
}

class SmallSavingsViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<SmallSavingsInput, SmallSavingsResult> {

    override val id: String = "small_savings"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultSmallSavingsInput())
    private val _snackbar = MutableStateFlow<String?>(null)
    private var latestConfig: SmallSavingsConfig = SmallSavingsConfig.fromResolvedConfig(emptyMap())

    val uiState: StateFlow<SmallSavingsUiState> = combine(
        _input,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _snackbar
    ) { inp, prefs, resolvedMap, msg ->
        val config = SmallSavingsConfig.fromResolvedConfig(resolvedMap)
        latestConfig = config
        val rateKey = config.rateKeyFor(inp.scheme, inp.potdTenureYears)
        val rateItem = resolvedMap[rateKey]
        val activeRate = config.rateFor(inp.scheme, inp.potdTenureYears)
        val rateText = "%.2f".format(activeRate).trimEnd('0').trimEnd('.')

        SmallSavingsUiState(
            input = inp,
            config = config,
            activeRateKey = rateKey,
            activeRateItem = rateItem,
            quarterItem = resolvedMap[ConfigKeys.SCHEME_QUARTER_LABEL],
            rateOverrideText = rateText,
            isRateOverridden = rateItem?.isOverridden == true,
            result = compute(inp),
            currency = prefs.currency,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmallSavingsUiState())

    override fun compute(input: SmallSavingsInput): SmallSavingsResult = SmallSavingsEngine.calculate(input, latestConfig)

    fun selectScheme(scheme: SmallSavingsSchemeType) {
        val defaultAmt = when (scheme) {
            SmallSavingsSchemeType.PPF -> latestConfig.ppfYearlyCap
            SmallSavingsSchemeType.SSY -> latestConfig.ssyYearlyCap
            SmallSavingsSchemeType.NSC -> 100000.0
            SmallSavingsSchemeType.SCSS -> 1500000.0
            SmallSavingsSchemeType.PO_MIS -> latestConfig.pomisSingleCap
            SmallSavingsSchemeType.PO_TD -> 500000.0
            SmallSavingsSchemeType.PO_RD -> 10000.0
            SmallSavingsSchemeType.KVP -> 100000.0
        }
        _input.value = _input.value.copy(
            scheme = scheme,
            contributionAmount = defaultAmt
        )
    }

    fun updateContribution(amount: Double) {
        _input.value = _input.value.copy(contributionAmount = amount)
    }

    fun setPpfExtensionBlocks(blocks: Int) {
        _input.value = _input.value.copy(ppfExtensionBlocks = blocks.coerceIn(0, 3))
    }

    fun setPpfContributeDuringExtension(enabled: Boolean) {
        _input.value = _input.value.copy(ppfContributeDuringExtension = enabled)
    }

    fun setPoMisJoint(isJoint: Boolean) {
        _input.value = _input.value.copy(pomisJointAccount = isJoint)
    }

    fun setPoTdYears(years: Int) {
        _input.value = _input.value.copy(potdTenureYears = years)
    }

    fun overrideActiveRate(rate: Double) {
        val key = latestConfig.rateKeyFor(_input.value.scheme, _input.value.potdTenureYears)
        if (rate <= 0.0 || rate > 25.0) return
        viewModelScope.launch {
            ratesRepository.setManualOverrideByKey(key, rate)
            _snackbar.value = "Updated ${_input.value.scheme.shortTitle} rate to $rate%."
        }
    }

    fun resetActiveRateToDefault() {
        val key = latestConfig.rateKeyFor(_input.value.scheme, _input.value.potdTenureYears)
        viewModelScope.launch {
            ratesRepository.resetToDefault(key)
            _snackbar.value = "Reset ${_input.value.scheme.shortTitle} rate to notified default."
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}

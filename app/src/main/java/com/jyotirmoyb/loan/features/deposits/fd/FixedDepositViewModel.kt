package com.jyotirmoyb.loan.features.deposits.fd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CompoundingFrequency
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.FdMode
import com.jyotirmoyb.loan.domain.FixedDepositEngine
import com.jyotirmoyb.loan.domain.FixedDepositInput
import com.jyotirmoyb.loan.domain.FixedDepositResult
import com.jyotirmoyb.loan.domain.PayoutFrequency
import com.jyotirmoyb.loan.domain.TdsConfig
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class FixedDepositUiState(
    val input: FixedDepositInput = defaultFdInput(),
    val rateText: String = "7.0",
    val isRateOverridden: Boolean = false,
    val fdRateItem: ResolvedConfigItem? = null,
    val seniorBonusItem: ResolvedConfigItem? = null,
    val tdsThresholdItem: ResolvedConfigItem? = null,
    val tdsRateItem: ResolvedConfigItem? = null,
    val tdsConfig: TdsConfig = TdsConfig.fromResolvedConfig(emptyMap()),
    val result: FixedDepositResult = FixedDepositEngine.calculate(
        defaultFdInput(),
        TdsConfig.fromResolvedConfig(emptyMap())
    ),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

private fun defaultFdInput(): FixedDepositInput {
    val cal = Calendar.getInstance()
    return FixedDepositInput(
        principal = 500000.0,
        annualRatePercent = 7.0,
        isSeniorCitizen = false,
        seniorBonusPercent = 0.5,
        tenureMonths = 60,
        mode = FdMode.CUMULATIVE,
        compounding = CompoundingFrequency.QUARTERLY,
        payoutFrequency = PayoutFrequency.QUARTERLY,
        hasPan = true,
        taxSlabPercent = 30.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1,
        startDay = cal.get(Calendar.DAY_OF_MONTH)
    )
}

class FixedDepositViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<FixedDepositInput, FixedDepositResult> {

    override val id: String = "fixed_deposit"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultFdInput())
    private val _rateText = MutableStateFlow("7.0")
    private val _userEditedRate = MutableStateFlow(false)
    private val _snackbar = MutableStateFlow<String?>(null)
    private var latestTdsConfig: TdsConfig = TdsConfig.fromResolvedConfig(emptyMap())

    val uiState: StateFlow<FixedDepositUiState> = combine(
        _input,
        _rateText,
        _userEditedRate,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _snackbar
    ) { arr ->
        val rawInput = arr[0] as FixedDepositInput
        val rateStr = arr[1] as String
        val userEdited = arr[2] as Boolean
        val prefs = arr[3] as com.jyotirmoyb.loan.data.UserPreferences
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = arr[4] as Map<String, ResolvedConfigItem>
        val snack = arr[5] as String?

        val defaultFdRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_FD_GENERAL, 7.0)
        val seniorBonus = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_FD_SENIOR_BONUS, 0.5)
        val tdsCfg = TdsConfig.fromResolvedConfig(resolvedMap)
        latestTdsConfig = tdsCfg

        val effectiveInput = rawInput.copy(
            annualRatePercent = if (userEdited) rawInput.annualRatePercent else defaultFdRate,
            seniorBonusPercent = seniorBonus
        )
        val displayRateText = if (userEdited) rateStr else formatRate(defaultFdRate)
        val tdsThresholdKey = if (effectiveInput.isSeniorCitizen) {
            ConfigKeys.TDS_FD_SENIOR_THRESHOLD
        } else {
            ConfigKeys.TDS_FD_GENERAL_THRESHOLD
        }
        val tdsRateKey = if (effectiveInput.hasPan) {
            ConfigKeys.TDS_RATE_WITH_PAN
        } else {
            ConfigKeys.TDS_RATE_WITHOUT_PAN
        }

        FixedDepositUiState(
            input = effectiveInput,
            rateText = displayRateText,
            isRateOverridden = userEdited || resolvedMap[ConfigKeys.DEPOSIT_FD_GENERAL]?.isOverridden == true,
            fdRateItem = resolvedMap[ConfigKeys.DEPOSIT_FD_GENERAL],
            seniorBonusItem = resolvedMap[ConfigKeys.DEPOSIT_FD_SENIOR_BONUS],
            tdsThresholdItem = resolvedMap[tdsThresholdKey],
            tdsRateItem = resolvedMap[tdsRateKey],
            tdsConfig = tdsCfg,
            result = compute(effectiveInput),
            currency = prefs.currency,
            snackbarMessage = snack
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FixedDepositUiState())

    override fun compute(input: FixedDepositInput): FixedDepositResult =
        FixedDepositEngine.calculate(input, latestTdsConfig)

    fun setMode(mode: FdMode) {
        _input.value = _input.value.copy(mode = mode)
    }

    fun updatePrincipal(amount: Double) {
        _input.value = _input.value.copy(principal = amount)
    }

    fun updateRateText(text: String) {
        _rateText.value = text
        _userEditedRate.value = true
        text.toDoubleOrNull()?.let { parsed ->
            _input.value = _input.value.copy(annualRatePercent = parsed)
        }
    }

    fun resetRateToDefault() {
        _userEditedRate.value = false
        viewModelScope.launch {
            ratesRepository.resetToDefault(ConfigKeys.DEPOSIT_FD_GENERAL)
            _snackbar.value = "FD rate reset to default."
        }
    }

    fun setSeniorCitizen(enabled: Boolean) {
        _input.value = _input.value.copy(isSeniorCitizen = enabled)
    }

    fun setCompounding(freq: CompoundingFrequency) {
        _input.value = _input.value.copy(compounding = freq)
    }

    fun setPayoutFrequency(freq: PayoutFrequency) {
        _input.value = _input.value.copy(payoutFrequency = freq)
    }

    fun updateTenureMonths(months: Int) {
        _input.value = _input.value.copy(tenureMonths = months.coerceIn(1, 360))
    }

    fun setHasPan(hasPan: Boolean) {
        _input.value = _input.value.copy(hasPan = hasPan)
    }

    fun setTaxSlab(slabPercent: Double) {
        _input.value = _input.value.copy(taxSlabPercent = slabPercent)
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun formatRate(rate: Double): String =
        if (rate % 1.0 == 0.0) "${rate.toInt()}" else "%.2f".format(rate).trimEnd('0').trimEnd('.')
}

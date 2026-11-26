package com.jyotirmoyb.loan.features.deposits.rd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.RecurringDepositEngine
import com.jyotirmoyb.loan.domain.RecurringDepositInput
import com.jyotirmoyb.loan.domain.RecurringDepositResult
import com.jyotirmoyb.loan.domain.TdsConfig
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class RecurringDepositUiState(
    val input: RecurringDepositInput = defaultRdInput(),
    val rateText: String = "6.75",
    val isRateOverridden: Boolean = false,
    val rdRateItem: ResolvedConfigItem? = null,
    val tdsThresholdItem: ResolvedConfigItem? = null,
    val result: RecurringDepositResult = RecurringDepositEngine.calculate(
        defaultRdInput(),
        TdsConfig.fromResolvedConfig(emptyMap())
    ),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

private fun defaultRdInput(): RecurringDepositInput {
    val cal = Calendar.getInstance()
    return RecurringDepositInput(
        monthlyDeposit = 10000.0,
        annualRatePercent = 6.75,
        isSeniorCitizen = false,
        seniorBonusPercent = 0.5,
        tenureMonths = 60,
        hasPan = true,
        taxSlabPercent = 30.0,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1,
        startDay = cal.get(Calendar.DAY_OF_MONTH)
    )
}

class RecurringDepositViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<RecurringDepositInput, RecurringDepositResult> {

    override val id: String = "recurring_deposit"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultRdInput())
    private val _rateText = MutableStateFlow("6.75")
    private val _userEditedRate = MutableStateFlow(false)
    private val _snackbar = MutableStateFlow<String?>(null)
    private var latestTdsConfig: TdsConfig = TdsConfig.fromResolvedConfig(emptyMap())

    val uiState: StateFlow<RecurringDepositUiState> = combine(
        _input,
        _rateText,
        _userEditedRate,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _snackbar
    ) { arr ->
        val rawInput = arr[0] as RecurringDepositInput
        val rateStr = arr[1] as String
        val userEdited = arr[2] as Boolean
        val prefs = arr[3] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = arr[4] as Map<String, ResolvedConfigItem>
        val snack = arr[5] as String?

        val defaultRdRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_RD_GENERAL, 6.75)
        val seniorBonus = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_FD_SENIOR_BONUS, 0.5)
        val tdsCfg = TdsConfig.fromResolvedConfig(resolvedMap)
        latestTdsConfig = tdsCfg

        val effectiveInput = rawInput.copy(
            annualRatePercent = if (userEdited) rawInput.annualRatePercent else defaultRdRate,
            seniorBonusPercent = seniorBonus
        )
        val displayRateText = if (userEdited) rateStr else "%.2f".format(defaultRdRate).trimEnd('0').trimEnd('.')
        val tdsThresholdKey = if (effectiveInput.isSeniorCitizen) {
            ConfigKeys.TDS_FD_SENIOR_THRESHOLD
        } else {
            ConfigKeys.TDS_FD_GENERAL_THRESHOLD
        }

        RecurringDepositUiState(
            input = effectiveInput,
            rateText = displayRateText,
            isRateOverridden = userEdited || resolvedMap[ConfigKeys.DEPOSIT_RD_GENERAL]?.isOverridden == true,
            rdRateItem = resolvedMap[ConfigKeys.DEPOSIT_RD_GENERAL],
            tdsThresholdItem = resolvedMap[tdsThresholdKey],
            result = compute(effectiveInput),
            currency = prefs.currency,
            snackbarMessage = snack
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringDepositUiState())

    override fun compute(input: RecurringDepositInput): RecurringDepositResult =
        RecurringDepositEngine.calculate(input, latestTdsConfig)

    fun updateMonthlyDeposit(amount: Double) {
        _input.value = _input.value.copy(monthlyDeposit = amount)
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
            ratesRepository.resetToDefault(ConfigKeys.DEPOSIT_RD_GENERAL)
            _snackbar.value = "RD rate reset to default."
        }
    }

    fun setSeniorCitizen(enabled: Boolean) {
        _input.value = _input.value.copy(isSeniorCitizen = enabled)
    }

    fun updateTenureMonths(months: Int) {
        _input.value = _input.value.copy(tenureMonths = months.coerceIn(6, 240))
    }

    fun setHasPan(hasPan: Boolean) {
        _input.value = _input.value.copy(hasPan = hasPan)
    }

    fun setTaxSlab(slab: Double) {
        _input.value = _input.value.copy(taxSlabPercent = slab)
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}

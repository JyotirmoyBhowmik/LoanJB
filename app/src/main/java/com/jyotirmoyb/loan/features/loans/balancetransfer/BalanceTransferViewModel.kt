package com.jyotirmoyb.loan.features.loans.balancetransfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.BalanceTransferAnalysisInput
import com.jyotirmoyb.loan.domain.BalanceTransferAnalysisResult
import com.jyotirmoyb.loan.domain.BalanceTransferEngine
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BalanceTransferUiState(
    val input: BalanceTransferAnalysisInput = BalanceTransferAnalysisInput(),
    val gstSourceItem: ResolvedConfigItem? = null,
    val newRateSourceItem: ResolvedConfigItem? = null,
    val result: BalanceTransferAnalysisResult = BalanceTransferEngine.analyze(BalanceTransferAnalysisInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

class BalanceTransferViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<BalanceTransferAnalysisInput, BalanceTransferAnalysisResult> {

    override val id: String = "balance_transfer"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(BalanceTransferAnalysisInput())
    private val _userEditedNewRate = MutableStateFlow(false)
    private val _userEditedGstRate = MutableStateFlow(false)

    val uiState: StateFlow<BalanceTransferUiState> = combine(
        _input,
        _userEditedNewRate,
        _userEditedGstRate,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { rawInput, editedNewRate, editedGst, prefs, resolvedMap ->
        val defaultHomeRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.HOME_RATE_MIN, 8.40)
        val defaultGstRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.GST_BANKING_SERVICES, 18.0)

        val effectiveInput = rawInput.copy(
            newAnnualRatePercent = if (editedNewRate) rawInput.newAnnualRatePercent else defaultHomeRate,
            gstRatePercent = if (editedGst) rawInput.gstRatePercent else defaultGstRate
        )

        BalanceTransferUiState(
            input = effectiveInput,
            gstSourceItem = resolvedMap[ConfigKeys.GST_BANKING_SERVICES],
            newRateSourceItem = resolvedMap[ConfigKeys.HOME_RATE_MIN],
            result = compute(effectiveInput),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BalanceTransferUiState())

    override fun compute(input: BalanceTransferAnalysisInput): BalanceTransferAnalysisResult =
        BalanceTransferEngine.analyze(input)

    fun updateInput(transform: (BalanceTransferAnalysisInput) -> BalanceTransferAnalysisInput) {
        _input.value = transform(_input.value)
    }

    fun updateNewRate(rate: Double) {
        _userEditedNewRate.value = true
        _input.value = _input.value.copy(newAnnualRatePercent = rate.coerceIn(0.5, 35.0))
    }

    fun resetNewRateToDefault() {
        _userEditedNewRate.value = false
    }

    fun updateGstRate(rate: Double) {
        _userEditedGstRate.value = true
        _input.value = _input.value.copy(gstRatePercent = rate.coerceIn(0.0, 40.0))
    }

    fun resetGstRateToDefault() {
        _userEditedGstRate.value = false
    }
}

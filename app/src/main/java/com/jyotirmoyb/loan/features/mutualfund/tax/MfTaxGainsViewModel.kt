package com.jyotirmoyb.loan.features.mutualfund.tax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.MfFundTaxCategory
import com.jyotirmoyb.loan.domain.MfTaxConfig
import com.jyotirmoyb.loan.domain.MfTaxGainsEngine
import com.jyotirmoyb.loan.domain.MfTaxInput
import com.jyotirmoyb.loan.domain.MfTaxResult
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MfTaxGainsUiState(
    val input: MfTaxInput = MfTaxInput(),
    val config: MfTaxConfig = MfTaxConfig.fromResolvedConfig(emptyMap()),
    val equityLtcgExemptionItem: ResolvedConfigItem? = null,
    val equityLtcgRateItem: ResolvedConfigItem? = null,
    val equityStcgRateItem: ResolvedConfigItem? = null,
    val hybridLtcgRateItem: ResolvedConfigItem? = null,
    val result: MfTaxResult = MfTaxGainsEngine.calculate(MfTaxInput(), MfTaxConfig.fromResolvedConfig(emptyMap())),
    val currency: CurrencyCode = CurrencyCode.INR
)

class MfTaxGainsViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<MfTaxInput, MfTaxResult> {

    override val id: String = "mf_tax"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(MfTaxInput())
    private var latestConfig: MfTaxConfig = MfTaxConfig.fromResolvedConfig(emptyMap())

    val uiState: StateFlow<MfTaxGainsUiState> = combine(
        _input,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { inp, prefs: UserPreferences, resolvedMap ->
        val cfg = MfTaxConfig.fromResolvedConfig(resolvedMap)
        latestConfig = cfg
        MfTaxGainsUiState(
            input = inp,
            config = cfg,
            equityLtcgExemptionItem = resolvedMap[ConfigKeys.TAX_MF_EQUITY_LTCG_EXEMPTION],
            equityLtcgRateItem = resolvedMap[ConfigKeys.TAX_MF_EQUITY_LTCG_RATE],
            equityStcgRateItem = resolvedMap[ConfigKeys.TAX_MF_EQUITY_STCG_RATE],
            hybridLtcgRateItem = resolvedMap[ConfigKeys.TAX_MF_HYBRID_LTCG_RATE],
            result = compute(inp),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MfTaxGainsUiState())

    override fun compute(input: MfTaxInput): MfTaxResult = MfTaxGainsEngine.calculate(input, latestConfig)

    fun selectCategory(category: MfFundTaxCategory) {
        _input.value = _input.value.copy(category = category)
    }

    fun updateInvested(amount: Double) {
        _input.value = _input.value.copy(investedAmount = amount)
    }

    fun updateRedemption(amount: Double) {
        _input.value = _input.value.copy(redemptionValue = amount)
    }

    fun updateHoldingMonths(months: Int) {
        _input.value = _input.value.copy(holdingMonths = months.coerceIn(1, 360))
    }

    fun selectTaxSlab(slabPercent: Double) {
        _input.value = _input.value.copy(userTaxSlabPercent = slabPercent)
    }
}

package com.jyotirmoyb.loan.features.loans.taxbenefit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanTaxBenefitEngine
import com.jyotirmoyb.loan.domain.LoanTaxBenefitInput
import com.jyotirmoyb.loan.domain.LoanTaxBenefitResult
import com.jyotirmoyb.loan.domain.TaxSlabsConfig
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoanTaxBenefitUiState(
    val input: LoanTaxBenefitInput = LoanTaxBenefitInput(),
    val config: TaxSlabsConfig = TaxSlabsConfig.fromResolvedConfig(emptyMap()),
    val sec24bItem: ResolvedConfigItem? = null,
    val sec80cItem: ResolvedConfigItem? = null,
    val sec80eItem: ResolvedConfigItem? = null,
    val newStdDedItem: ResolvedConfigItem? = null,
    val oldStdDedItem: ResolvedConfigItem? = null,
    val result: LoanTaxBenefitResult = LoanTaxBenefitEngine.calculate(
        LoanTaxBenefitInput(),
        TaxSlabsConfig.fromResolvedConfig(emptyMap())
    ),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

class LoanTaxBenefitViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<LoanTaxBenefitInput, LoanTaxBenefitResult> {

    override val id: String = "loan_tax_benefit"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(LoanTaxBenefitInput())
    private val _snackbar = MutableStateFlow<String?>(null)
    private var latestConfig: TaxSlabsConfig = TaxSlabsConfig.fromResolvedConfig(emptyMap())

    val uiState: StateFlow<LoanTaxBenefitUiState> = combine(
        _input,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _snackbar
    ) { inp, prefs, resolvedMap, msg ->
        val config = TaxSlabsConfig.fromResolvedConfig(resolvedMap)
        latestConfig = config
        LoanTaxBenefitUiState(
            input = inp,
            config = config,
            sec24bItem = resolvedMap[ConfigKeys.SEC_24B_CAP],
            sec80cItem = resolvedMap[ConfigKeys.SEC_80C_CAP],
            sec80eItem = resolvedMap[ConfigKeys.SEC_80E_MAX_YEARS],
            newStdDedItem = resolvedMap[ConfigKeys.TAX_NEW_STD_DEDUCTION],
            oldStdDedItem = resolvedMap[ConfigKeys.TAX_OLD_STD_DEDUCTION],
            result = compute(inp),
            currency = prefs.currency,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoanTaxBenefitUiState())

    override fun compute(input: LoanTaxBenefitInput): LoanTaxBenefitResult = LoanTaxBenefitEngine.calculate(input, latestConfig)

    fun updateInput(transform: (LoanTaxBenefitInput) -> LoanTaxBenefitInput) {
        _input.value = transform(_input.value)
    }

    fun overrideTaxCap(key: String, value: Double) {
        viewModelScope.launch {
            ratesRepository.setManualOverrideByKey(key, value)
            _snackbar.value = "Updated tax cap override."
        }
    }

    fun resetTaxCap(key: String) {
        viewModelScope.launch {
            ratesRepository.resetToDefault(key)
            _snackbar.value = "Reset tax cap to default."
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}

package com.jyotirmoyb.loan.features.deposits.compare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.DepositComparisonEngine
import com.jyotirmoyb.loan.domain.DepositComparisonInput
import com.jyotirmoyb.loan.domain.DepositComparisonReport
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DepositComparisonUiState(
    val input: DepositComparisonInput = DepositComparisonInput(),
    val fdRateText: String = "7.0",
    val rdRateText: String = "6.75",
    val ppfRateText: String = "7.1",
    val debtRateText: String = "7.25",
    val hasCustomRates: Boolean = false,
    val fdRateItem: ResolvedConfigItem? = null,
    val ppfRateItem: ResolvedConfigItem? = null,
    val debtRateItem: ResolvedConfigItem? = null,
    val report: DepositComparisonReport = DepositComparisonEngine.compare(DepositComparisonInput()),
    val currency: CurrencyCode = CurrencyCode.INR
)

private data class ComparisonRateOverrides(
    val fd: Double? = null,
    val rd: Double? = null,
    val ppf: Double? = null,
    val debt: Double? = null,
    val fdText: String? = null,
    val rdText: String? = null,
    val ppfText: String? = null,
    val debtText: String? = null
)

class DepositComparisonViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<DepositComparisonInput, DepositComparisonReport> {

    override val id: String = "deposit_comparison"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _monthlyContribution = MutableStateFlow(10000.0)
    private val _horizonYears = MutableStateFlow(15)
    private val _taxSlabPercent = MutableStateFlow(30.0)
    private val _overrides = MutableStateFlow(ComparisonRateOverrides())

    val uiState: StateFlow<DepositComparisonUiState> = combine(
        _monthlyContribution,
        _horizonYears,
        _taxSlabPercent,
        _overrides,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { arr ->
        val monthly = arr[0] as Double
        val years = arr[1] as Int
        val slab = arr[2] as Double
        val ov = arr[3] as ComparisonRateOverrides
        val prefs = arr[4] as UserPreferences

        @Suppress("UNCHECKED_CAST")
        val resolvedMap = arr[5] as Map<String, ResolvedConfigItem>

        val defaultFd = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_FD_GENERAL, 7.0)
        val defaultRd = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_RD_GENERAL, 6.75)
        val defaultPpf = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_PPF_RATE, 7.1)
        val ppfCap = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.SCHEME_PPF_YEARLY_CAP, 150000.0)
        val defaultDebt = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.DEPOSIT_DEBT_FUND_EXPECTED, 7.25)
        val cess = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TAX_CESS_PERCENT, 4.0)

        val fdRate = ov.fd ?: defaultFd
        val rdRate = ov.rd ?: defaultRd
        val ppfRate = ov.ppf ?: defaultPpf
        val debtRate = ov.debt ?: defaultDebt

        val inp = DepositComparisonInput(
            monthlyContribution = monthly,
            horizonYears = years,
            taxSlabPercent = slab,
            cessPercent = cess,
            fdAnnualRatePercent = fdRate,
            rdAnnualRatePercent = rdRate,
            ppfAnnualRatePercent = ppfRate,
            ppfYearlyCap = ppfCap,
            debtFundExpectedReturnPercent = debtRate
        )

        DepositComparisonUiState(
            input = inp,
            fdRateText = ov.fdText ?: fmtRate(defaultFd),
            rdRateText = ov.rdText ?: fmtRate(defaultRd),
            ppfRateText = ov.ppfText ?: fmtRate(defaultPpf),
            debtRateText = ov.debtText ?: fmtRate(defaultDebt),
            hasCustomRates = ov.fd != null || ov.rd != null || ov.ppf != null || ov.debt != null,
            fdRateItem = resolvedMap[ConfigKeys.DEPOSIT_FD_GENERAL],
            ppfRateItem = resolvedMap[ConfigKeys.SCHEME_PPF_RATE],
            debtRateItem = resolvedMap[ConfigKeys.DEPOSIT_DEBT_FUND_EXPECTED],
            report = compute(inp),
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DepositComparisonUiState())

    override fun compute(input: DepositComparisonInput): DepositComparisonReport = DepositComparisonEngine.compare(input)

    fun updateMonthly(amount: Double) {
        _monthlyContribution.value = amount
    }

    fun updateHorizonYears(years: Int) {
        _horizonYears.value = years.coerceIn(1, 30)
    }

    fun updateTaxSlab(slab: Double) {
        _taxSlabPercent.value = slab
    }

    fun updateFdRate(text: String) {
        _overrides.value = _overrides.value.copy(fdText = text, fd = text.toDoubleOrNull() ?: _overrides.value.fd)
    }

    fun updateRdRate(text: String) {
        _overrides.value = _overrides.value.copy(rdText = text, rd = text.toDoubleOrNull() ?: _overrides.value.rd)
    }

    fun updatePpfRate(text: String) {
        _overrides.value = _overrides.value.copy(ppfText = text, ppf = text.toDoubleOrNull() ?: _overrides.value.ppf)
    }

    fun updateDebtRate(text: String) {
        _overrides.value = _overrides.value.copy(debtText = text, debt = text.toDoubleOrNull() ?: _overrides.value.debt)
    }

    fun resetRatesToDefault() {
        _overrides.value = ComparisonRateOverrides()
    }

    private fun fmtRate(v: Double): String = if (v % 1.0 == 0.0) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

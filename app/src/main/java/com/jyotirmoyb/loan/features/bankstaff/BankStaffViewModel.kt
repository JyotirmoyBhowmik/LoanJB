package com.jyotirmoyb.loan.features.bankstaff

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CalculationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanCalculationEngine
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StaffLoanPreset(
    val id: String,
    val title: String,
    val description: String,
    val defaultAmount: Double,
    val staffRate: Double,
    val marketRate: Double,
    val tenureMonths: Int,
    val staffRateKey: String? = null,
    val defaultAmountKey: String? = null,
    val tenureMonthsKey: String? = null
)

data class BankStaffUiState(
    val presets: List<StaffLoanPreset> = defaultStaffPresets(),
    val resolvedMap: Map<String, ResolvedConfigItem> = emptyMap(),
    val selectedPresetId: String = "staff_housing",
    val amount: Double = 4000000.0,
    val staffRate: Double = 6.5,
    val marketRate: Double = 9.0,
    val tenureMonths: Int = 240,
    val staffRateSource: ResolvedConfigItem? = null,
    val staffResult: CalculationResult = LoanCalculationEngine.calculate(
        CalculationInput(principal = 4000000.0, annualRate = 6.5, tenureMonths = 240, processingFeePercent = 0.0)
    ),
    val marketResult: CalculationResult = LoanCalculationEngine.calculate(
        CalculationInput(principal = 4000000.0, annualRate = 9.0, tenureMonths = 240, processingFeePercent = 0.5)
    ),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
) {
    val totalInterestSaved: Double
        get() = maxOf(0.0, LoanCalculationEngine.roundPaisa(marketResult.totalInterest - staffResult.totalInterest))

    val monthlyEmiSaved: Double
        get() = maxOf(0.0, LoanCalculationEngine.roundPaisa(marketResult.monthlyEmi - staffResult.monthlyEmi))
}

fun resolveStaffPresets(resolved: Map<String, ResolvedConfigItem> = emptyMap()): List<StaffLoanPreset> = defaultStaffPresets(resolved)

private fun defaultStaffPresets(resolved: Map<String, ResolvedConfigItem> = emptyMap()): List<StaffLoanPreset> = listOf(
    StaffLoanPreset(
        id = "staff_housing",
        title = "Staff Housing Loan",
        description = "Concessional PSU / private bank staff home loan (0% processing fee)",
        defaultAmount = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_HOUSING_AMOUNT, 4000000.0),
        staffRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_HOUSING, 6.5),
        marketRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.HOME_RATE_MIN, 8.5),
        tenureMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.BANK_STAFF_HOUSING_TENURE, 240),
        staffRateKey = ConfigKeys.BANK_STAFF_HOUSING,
        defaultAmountKey = ConfigKeys.BANK_STAFF_HOUSING_AMOUNT,
        tenureMonthsKey = ConfigKeys.BANK_STAFF_HOUSING_TENURE
    ),
    StaffLoanPreset(
        id = "staff_vehicle",
        title = "Staff Car / 2W Loan",
        description = "Subsidized employee conveyance loan",
        defaultAmount = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_VEHICLE_AMOUNT, 1000000.0),
        staffRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_VEHICLE, 7.0),
        marketRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.VEHICLE_CAR_MIN, 9.0),
        tenureMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.BANK_STAFF_VEHICLE_TENURE, 84),
        staffRateKey = ConfigKeys.BANK_STAFF_VEHICLE,
        defaultAmountKey = ConfigKeys.BANK_STAFF_VEHICLE_AMOUNT,
        tenureMonthsKey = ConfigKeys.BANK_STAFF_VEHICLE_TENURE
    ),
    StaffLoanPreset(
        id = "staff_personal",
        title = "Staff Clean OD / Personal",
        description = "Low-rate staff personal / festival facility",
        defaultAmount = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_PERSONAL_AMOUNT, 500000.0),
        staffRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_PERSONAL, 7.5),
        marketRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.PERSONAL_RATE_MIN, 11.0),
        tenureMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.BANK_STAFF_PERSONAL_TENURE, 60),
        staffRateKey = ConfigKeys.BANK_STAFF_PERSONAL,
        defaultAmountKey = ConfigKeys.BANK_STAFF_PERSONAL_AMOUNT,
        tenureMonthsKey = ConfigKeys.BANK_STAFF_PERSONAL_TENURE
    ),
    StaffLoanPreset(
        id = "staff_education",
        title = "Staff Child Education",
        description = "Concessional rate for staff dependents' higher studies",
        defaultAmount = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_EDUCATION_AMOUNT, 1500000.0),
        staffRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.BANK_STAFF_EDUCATION, 6.0),
        marketRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.EDUCATION_INDIA_MIN, 9.5),
        tenureMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.BANK_STAFF_EDUCATION_TENURE, 120),
        staffRateKey = ConfigKeys.BANK_STAFF_EDUCATION,
        defaultAmountKey = ConfigKeys.BANK_STAFF_EDUCATION_AMOUNT,
        tenureMonthsKey = ConfigKeys.BANK_STAFF_EDUCATION_TENURE
    )
)

class BankStaffViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<CalculationInput, CalculationResult> {

    override val id: String = "bank_staff_loan"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _selectedId = MutableStateFlow("staff_housing")
    private val _amount = MutableStateFlow(4000000.0)
    private val _staffRate = MutableStateFlow(6.5)
    private val _marketRate = MutableStateFlow(8.5)
    private val _tenureMonths = MutableStateFlow(240)
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BankStaffUiState> = combine(
        _selectedId,
        _amount,
        _staffRate,
        _marketRate,
        _tenureMonths,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _snackbar
    ) { values ->
        val selId = values[0] as String
        val amt = values[1] as Double
        val sRate = values[2] as Double
        val mRate = values[3] as Double
        val months = values[4] as Int
        val prefs = values[5] as com.jyotirmoyb.loan.data.UserPreferences

        @Suppress("UNCHECKED_CAST")
        val resolvedMap = values[6] as Map<String, ResolvedConfigItem>
        val msg = values[7] as String?

        val presets = defaultStaffPresets(resolvedMap)
        val activePreset = presets.firstOrNull { it.id == selId }
        val staffRateSource = activePreset?.staffRateKey?.let { resolvedMap[it] }

        val staffInput = buildStaffCalculationInput(amt, sRate, months)
        val marketInput = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
            principal = amt,
            assetValue = amt * 1.25,
            annualRate = mRate,
            tenureMonths = months,
            processingFeePercent = 0.5
        )
        BankStaffUiState(
            presets = presets,
            resolvedMap = resolvedMap,
            selectedPresetId = selId,
            amount = amt,
            staffRate = sRate,
            marketRate = mRate,
            tenureMonths = months,
            staffRateSource = staffRateSource,
            staffResult = compute(staffInput),
            marketResult = compute(marketInput),
            currency = prefs.currency,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BankStaffUiState())

    override fun compute(input: CalculationInput): CalculationResult = LoanCalculationEngine.calculate(input)

    fun selectPreset(preset: StaffLoanPreset) {
        _selectedId.value = preset.id
        _amount.value = preset.defaultAmount
        _staffRate.value = preset.staffRate
        _marketRate.value = preset.marketRate
        _tenureMonths.value = preset.tenureMonths
    }

    fun updateAmount(amount: Double) {
        _amount.value = amount.coerceAtLeast(0.0)
    }

    fun updateStaffRate(rate: Double) {
        _staffRate.value = rate.coerceIn(0.5, 25.0)
    }

    fun updateMarketRate(rate: Double) {
        _marketRate.value = rate.coerceIn(1.0, 30.0)
    }

    fun updateTenureMonths(months: Int) {
        _tenureMonths.value = months.coerceIn(6, 360)
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun saveTemplateOverride(preset: StaffLoanPreset, newRate: Double, newAmount: Double, newTenureMonths: Int) {
        viewModelScope.launch {
            preset.staffRateKey?.let { ratesRepository.setManualOverrideByKey(it, newRate) }
            preset.defaultAmountKey?.let { ratesRepository.setManualOverrideByKey(it, newAmount) }
            preset.tenureMonthsKey?.let { ratesRepository.setManualOverrideByKey(it, newTenureMonths.toDouble()) }
            if (_selectedId.value == preset.id) {
                _staffRate.value = newRate
                _amount.value = newAmount
                _tenureMonths.value = newTenureMonths
            }
            _snackbar.value = "Saved template override for \"${preset.title}\""
        }
    }

    fun resetTemplateToDefault(preset: StaffLoanPreset) {
        viewModelScope.launch {
            preset.staffRateKey?.let { ratesRepository.resetToDefault(it) }
            preset.defaultAmountKey?.let { ratesRepository.resetToDefault(it) }
            preset.tenureMonthsKey?.let { ratesRepository.resetToDefault(it) }
            _snackbar.value = "Reset \"${preset.title}\" template to default"
        }
    }

    fun buildCurrentStaffInput(): CalculationInput = buildStaffCalculationInput(_amount.value, _staffRate.value, _tenureMonths.value)

    fun saveStaffLoan() {
        viewModelScope.launch {
            val state = uiState.value
            val preset = state.presets.firstOrNull { it.id == state.selectedPresetId }
            val input = buildCurrentStaffInput()
            repository.saveLoan(
                SavedLoanEntity(
                    nickname = preset?.title ?: "Bank Staff Loan",
                    lender = "Employer Bank (Staff)",
                    categoryId = "home",
                    categoryName = "Bank Staff Loan",
                    variantSummary = "Subsidized ${String.format("%.2f", state.staffRate)}% • 0% Fee",
                    inputJson = repository.serializeInput(input)
                )
            )
            _snackbar.value = "Saved \"${preset?.title ?: "Bank Staff Loan"}\" to Saved Loans"
        }
    }

    fun addBothToCompare() {
        viewModelScope.launch {
            val state = uiState.value
            val staffInput = buildCurrentStaffInput()
            val marketInput = staffInput.copy(annualRate = state.marketRate, processingFeePercent = 0.5)
            repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "Staff Rate (${String.format("%.2f", state.staffRate)}%)",
                    categoryId = "home",
                    variantSummary = "Bank Staff Concessional",
                    inputJson = repository.serializeInput(staffInput)
                )
            )
            repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "Market Rate (${String.format("%.2f", state.marketRate)}%)",
                    categoryId = "home",
                    variantSummary = "Standard Retail Offer",
                    inputJson = repository.serializeInput(marketInput)
                )
            )
            _snackbar.value = "Added Staff vs Market comparison scenarios"
        }
    }

    private fun buildStaffCalculationInput(amt: Double, rate: Double, months: Int): CalculationInput = CalculationInput(
        categoryId = "home",
        ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL"),
        principal = amt,
        assetValue = amt * 1.25,
        annualRate = rate,
        tenureMonths = months,
        processingFeePercent = 0.0,
        prepaymentFeePercent = 0.0
    )
}

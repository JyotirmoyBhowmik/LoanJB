package com.jyotirmoyb.loan.features.loans.eligibility

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CibilBand
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.EligibilityForwardInput
import com.jyotirmoyb.loan.domain.EligibilityResult
import com.jyotirmoyb.loan.domain.EligibilityReverseInput
import com.jyotirmoyb.loan.domain.FoirConfig
import com.jyotirmoyb.loan.domain.LoanEligibilityEngine
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EligibilityUiState(
    val isReverseMode: Boolean = false,
    val monthlyIncome: Double = 120000.0,
    val existingEmis: Double = 15000.0,
    val ageYears: Int = 32,
    val retirementAgeYears: Int = 60,
    val annualRatePercent: Double = 8.5,
    val desiredTenureYears: Int = 20,
    val customFoirPercent: Double? = null,
    val defaultFoirPercent: Double = 50.0,
    val foirSourceItem: ResolvedConfigItem? = null,
    val rateSourceItem: ResolvedConfigItem? = null,
    val cibilBand: CibilBand = CibilBand.EXCELLENT_750_PLUS,
    val reverseMonthlyEmiBudget: Double = 45000.0,
    val result: EligibilityResult = EligibilityResult(isValid = false),
    val currency: CurrencyCode = CurrencyCode.INR
) {
    val activeFoirPercent: Double get() = customFoirPercent ?: defaultFoirPercent
}

class EligibilityViewModel(application: Application) :
    AndroidViewModel(application),
    CalculatorEngineContract<EligibilityForwardInput, EligibilityResult> {

    override val id: String = "loan_eligibility"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _isReverse = MutableStateFlow(false)
    private val _monthlyIncome = MutableStateFlow<Double?>(null)
    private val _existingEmis = MutableStateFlow(15000.0)
    private val _age = MutableStateFlow<Int?>(null)
    private val _retirementAge = MutableStateFlow(60)
    private val _rate = MutableStateFlow<Double?>(null)
    private val _tenureYears = MutableStateFlow(20)
    private val _customFoir = MutableStateFlow<Double?>(null)
    private val _cibil = MutableStateFlow(CibilBand.EXCELLENT_750_PLUS)
    private val _reverseBudget = MutableStateFlow(45000.0)

    private var latestFoirConfig: FoirConfig = FoirConfig(40.0, 50.0, 55.0, 50000.0, 150000.0)

    val uiState: StateFlow<EligibilityUiState> = combine(
        _isReverse,
        _monthlyIncome,
        _existingEmis,
        _age,
        _retirementAge,
        _rate,
        _tenureYears,
        _customFoir,
        _cibil,
        _reverseBudget,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow
    ) { values ->
        val reverse = values[0] as Boolean
        val customIncome = values[1] as Double?
        val existing = values[2] as Double
        val customAge = values[3] as Int?
        val retire = values[4] as Int
        val customRate = values[5] as Double?
        val tenureYrs = values[6] as Int
        val customFoir = values[7] as Double?
        val cibil = values[8] as CibilBand
        val reverseEmi = values[9] as Double
        val prefs = values[10] as com.jyotirmoyb.loan.data.UserPreferences

        @Suppress("UNCHECKED_CAST")
        val resolvedMap = values[11] as Map<String, ResolvedConfigItem>

        val income = customIncome ?: prefs.monthlyIncome.takeIf { it > 0.0 } ?: 120000.0
        val age = (customAge ?: prefs.userAge).coerceIn(18, 75)

        val foirConfig = FoirConfig.fromResolvedConfig(resolvedMap)
        latestFoirConfig = foirConfig
        val defaultFoir = foirConfig.defaultFoirForIncome(income)
        val foirSourceKey = foirConfig.sourceKeyForIncome(income)
        val foirSourceItem = resolvedMap[foirSourceKey]

        val defaultHomeRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.HOME_RATE_MIN, 8.5)
        val effectiveInputRate = customRate ?: defaultHomeRate
        val rateSourceItem = resolvedMap[ConfigKeys.HOME_RATE_MIN]

        val result = if (!reverse) {
            LoanEligibilityEngine.calculateForward(
                EligibilityForwardInput(
                    monthlyIncome = income,
                    existingEmis = existing,
                    ageYears = age,
                    retirementAgeYears = retire,
                    baseAnnualRatePercent = effectiveInputRate,
                    desiredTenureYears = tenureYrs,
                    customFoirPercent = customFoir,
                    cibilBand = cibil
                ),
                foirConfig
            )
        } else {
            LoanEligibilityEngine.calculateReverse(
                EligibilityReverseInput(
                    affordableMonthlyEmi = reverseEmi,
                    annualRatePercent = effectiveInputRate,
                    tenureMonths = tenureYrs * 12,
                    monthlyIncome = income,
                    existingEmis = existing,
                    cibilBand = cibil
                )
            )
        }

        EligibilityUiState(
            isReverseMode = reverse,
            monthlyIncome = income,
            existingEmis = existing,
            ageYears = age,
            retirementAgeYears = retire,
            annualRatePercent = effectiveInputRate,
            desiredTenureYears = tenureYrs,
            customFoirPercent = customFoir,
            defaultFoirPercent = defaultFoir,
            foirSourceItem = foirSourceItem,
            rateSourceItem = rateSourceItem,
            cibilBand = cibil,
            reverseMonthlyEmiBudget = reverseEmi,
            result = result,
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EligibilityUiState())

    override fun compute(input: EligibilityForwardInput): EligibilityResult =
        LoanEligibilityEngine.calculateForward(input, latestFoirConfig)

    fun setReverseMode(reverse: Boolean) {
        _isReverse.value = reverse
    }
    fun updateMonthlyIncome(v: Double) {
        _monthlyIncome.value = v.coerceAtLeast(0.0)
    }
    fun updateExistingEmis(v: Double) {
        _existingEmis.value = v.coerceAtLeast(0.0)
    }
    fun updateAge(v: Int) {
        _age.value = v.coerceIn(18, 75)
    }
    fun updateRetirementAge(v: Int) {
        _retirementAge.value = v.coerceIn(25, 80)
    }
    fun updateRate(v: Double) {
        _rate.value = v.coerceIn(0.5, 35.0)
    }
    fun resetRateToDefault() {
        _rate.value = null
    }
    fun updateTenureYears(v: Int) {
        _tenureYears.value = v.coerceIn(1, 35)
    }
    fun updateCustomFoir(v: Double) {
        _customFoir.value = v.coerceIn(10.0, 80.0)
    }
    fun resetFoirToDefault() {
        _customFoir.value = null
    }
    fun selectCibilBand(band: CibilBand) {
        _cibil.value = band
    }
    fun updateReverseEmiBudget(v: Double) {
        _reverseBudget.value = v.coerceAtLeast(0.0)
    }
}

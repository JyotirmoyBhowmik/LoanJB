package com.jyotirmoyb.loan.features.planners.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.BudgetSplitResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.EmergencyFundResult
import com.jyotirmoyb.loan.domain.FinancialRulesEngine
import com.jyotirmoyb.loan.domain.InsuranceCoverResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class FinancialRuleTab { BUDGET_50_30_20, EMERGENCY_FUND, INSURANCE_COVER }

data class FinancialRulesUiState(
    val activeTab: FinancialRuleTab = FinancialRuleTab.BUDGET_50_30_20,
    val currency: CurrencyCode = CurrencyCode.INR,
    // 50/30/20 Budget
    val monthlyIncome: Double = 100000.0,
    val needsPercent: Double = 50.0,
    val wantsPercent: Double = 30.0,
    val savingsPercent: Double = 20.0,
    val hasCustomSplit: Boolean = false,
    val budgetResult: BudgetSplitResult = FinancialRulesEngine.calculateBudgetSplit(100000.0, 50.0, 30.0, 20.0),
    val budgetConfigItem: ResolvedConfigItem? = null,
    // Emergency Fund
    val monthlyEssentialExpenses: Double = 45000.0,
    val targetMonths: Int = 6,
    val hasCustomEmergencyMonths: Boolean = false,
    val currentLiquidSavings: Double = 120000.0,
    val monthlyEmergencyAllocation: Double = 15000.0,
    val emergencyResult: EmergencyFundResult = FinancialRulesEngine.calculateEmergencyFund(45000.0, 6, 120000.0, 15000.0),
    val emergencyConfigItem: ResolvedConfigItem? = null,
    // Insurance Cover
    val annualIncome: Double = 1200000.0,
    val existingLiabilities: Double = 2500000.0,
    val existingTermCover: Double = 5000000.0,
    val termIncomeMultiplier: Double = 12.0,
    val hasCustomTermMultiplier: Boolean = false,
    val isMetroCity: Boolean = true,
    val familyMembersCount: Int = 3,
    val existingHealthCover: Double = 500000.0,
    val insuranceResult: InsuranceCoverResult = FinancialRulesEngine.calculateInsuranceCover(
        annualIncome = 1200000.0,
        existingLiabilities = 2500000.0,
        existingTermCover = 5000000.0,
        termIncomeMultiplier = 12.0,
        isMetroCity = true,
        familyMembersCount = 3,
        existingHealthCover = 500000.0,
        metroBaseHealthCover = 1000000.0,
        nonMetroBaseHealthCover = 500000.0
    ),
    val termConfigItem: ResolvedConfigItem? = null,
    val healthConfigItem: ResolvedConfigItem? = null
)

class FinancialRulesViewModel(
    prefsRepo: UserPreferencesRepository,
    ratesRepo: RatesRepository
) : ViewModel() {

    private data class Draft(
        val activeTab: FinancialRuleTab = FinancialRuleTab.BUDGET_50_30_20,
        val monthlyIncome: Double = 100000.0,
        val hasCustomMonthlyIncome: Boolean = false,
        val needsPercent: Double = 50.0,
        val wantsPercent: Double = 30.0,
        val savingsPercent: Double = 20.0,
        val hasCustomSplit: Boolean = false,
        val monthlyEssentialExpenses: Double = 45000.0,
        val targetMonths: Int = 6,
        val hasCustomEmergencyMonths: Boolean = false,
        val currentLiquidSavings: Double = 120000.0,
        val monthlyEmergencyAllocation: Double = 15000.0,
        val annualIncome: Double = 1200000.0,
        val hasCustomAnnualIncome: Boolean = false,
        val existingLiabilities: Double = 2500000.0,
        val existingTermCover: Double = 5000000.0,
        val termIncomeMultiplier: Double = 12.0,
        val hasCustomTermMultiplier: Boolean = false,
        val isMetroCity: Boolean = true,
        val familyMembersCount: Int = 3,
        val existingHealthCover: Double = 500000.0
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<FinancialRulesUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.resolvedMapFlow
    ) { d, prefs, resolvedMap ->
        val defNeeds = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_NEEDS_PERCENT, 50.0)
        val defWants = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_WANTS_PERCENT, 30.0)
        val defSavings = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_SAVINGS_PERCENT, 20.0)
        val defEmergencyMonths = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_EMERGENCY_MONTHS, 6.0).toInt()
        val defTermMult = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_TERM_MULTIPLIER, 12.0)
        val metroHealth = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_HEALTH_METRO, 1000000.0)
        val nonMetroHealth = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.PLANNER_HEALTH_NON_METRO, 500000.0)

        val effNeeds = if (d.hasCustomSplit) d.needsPercent else defNeeds
        val effWants = if (d.hasCustomSplit) d.wantsPercent else defWants
        val effSavings = if (d.hasCustomSplit) d.savingsPercent else defSavings
        val effMonths = if (d.hasCustomEmergencyMonths) d.targetMonths else defEmergencyMonths
        val effTermMult = if (d.hasCustomTermMultiplier) d.termIncomeMultiplier else defTermMult

        val effMonthlyIncome = if (d.hasCustomMonthlyIncome) d.monthlyIncome else prefs.monthlyIncome.takeIf { it > 0.0 } ?: d.monthlyIncome
        val effAnnualIncome = if (d.hasCustomAnnualIncome) d.annualIncome else (effMonthlyIncome * 12.0)

        val budgetRes = FinancialRulesEngine.calculateBudgetSplit(
            monthlyIncome = effMonthlyIncome,
            needsPercent = effNeeds,
            wantsPercent = effWants,
            savingsPercent = effSavings
        )
        val emergencyRes = FinancialRulesEngine.calculateEmergencyFund(
            monthlyEssentialExpenses = d.monthlyEssentialExpenses,
            targetMonths = effMonths,
            currentLiquidSavings = d.currentLiquidSavings,
            monthlyAllocation = d.monthlyEmergencyAllocation
        )
        val insuranceRes = FinancialRulesEngine.calculateInsuranceCover(
            annualIncome = effAnnualIncome,
            existingLiabilities = d.existingLiabilities,
            existingTermCover = d.existingTermCover,
            termIncomeMultiplier = effTermMult,
            isMetroCity = d.isMetroCity,
            familyMembersCount = d.familyMembersCount,
            existingHealthCover = d.existingHealthCover,
            metroBaseHealthCover = metroHealth,
            nonMetroBaseHealthCover = nonMetroHealth
        )

        FinancialRulesUiState(
            activeTab = d.activeTab,
            currency = prefs.currency,
            monthlyIncome = effMonthlyIncome,
            needsPercent = effNeeds,
            wantsPercent = effWants,
            savingsPercent = effSavings,
            hasCustomSplit = d.hasCustomSplit,
            budgetResult = budgetRes,
            budgetConfigItem = resolvedMap[ConfigKeys.PLANNER_NEEDS_PERCENT],
            monthlyEssentialExpenses = d.monthlyEssentialExpenses,
            targetMonths = effMonths,
            hasCustomEmergencyMonths = d.hasCustomEmergencyMonths,
            currentLiquidSavings = d.currentLiquidSavings,
            monthlyEmergencyAllocation = d.monthlyEmergencyAllocation,
            emergencyResult = emergencyRes,
            emergencyConfigItem = resolvedMap[ConfigKeys.PLANNER_EMERGENCY_MONTHS],
            annualIncome = effAnnualIncome,
            existingLiabilities = d.existingLiabilities,
            existingTermCover = d.existingTermCover,
            termIncomeMultiplier = effTermMult,
            hasCustomTermMultiplier = d.hasCustomTermMultiplier,
            isMetroCity = d.isMetroCity,
            familyMembersCount = d.familyMembersCount,
            existingHealthCover = d.existingHealthCover,
            insuranceResult = insuranceRes,
            termConfigItem = resolvedMap[ConfigKeys.PLANNER_TERM_MULTIPLIER],
            healthConfigItem = if (d.isMetroCity) resolvedMap[ConfigKeys.PLANNER_HEALTH_METRO] else resolvedMap[ConfigKeys.PLANNER_HEALTH_NON_METRO]
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinancialRulesUiState())

    fun selectTab(tab: FinancialRuleTab) {
        draft.update { it.copy(activeTab = tab) }
    }

    fun updateMonthlyIncome(amount: Double) {
        draft.update { it.copy(monthlyIncome = amount.coerceAtLeast(0.0), hasCustomMonthlyIncome = true) }
    }

    fun updateBudgetSplit(needs: Double, wants: Double, savings: Double) {
        draft.update {
            it.copy(
                needsPercent = needs.coerceIn(0.0, 100.0),
                wantsPercent = wants.coerceIn(0.0, 100.0),
                savingsPercent = savings.coerceIn(0.0, 100.0),
                hasCustomSplit = true
            )
        }
    }

    fun resetBudgetSplitToDefault() {
        draft.update { it.copy(hasCustomSplit = false) }
    }

    fun updateEssentialExpenses(amount: Double) {
        draft.update { it.copy(monthlyEssentialExpenses = amount.coerceAtLeast(0.0)) }
    }

    fun updateTargetMonths(months: Int) {
        draft.update { it.copy(targetMonths = months.coerceIn(1, 24), hasCustomEmergencyMonths = true) }
    }

    fun resetEmergencyMonthsToDefault() {
        draft.update { it.copy(hasCustomEmergencyMonths = false) }
    }

    fun updateCurrentLiquidSavings(amount: Double) {
        draft.update { it.copy(currentLiquidSavings = amount.coerceAtLeast(0.0)) }
    }

    fun updateMonthlyEmergencyAllocation(amount: Double) {
        draft.update { it.copy(monthlyEmergencyAllocation = amount.coerceAtLeast(0.0)) }
    }

    fun updateAnnualIncome(amount: Double) {
        draft.update { it.copy(annualIncome = amount.coerceAtLeast(0.0), hasCustomAnnualIncome = true) }
    }

    fun updateExistingLiabilities(amount: Double) {
        draft.update { it.copy(existingLiabilities = amount.coerceAtLeast(0.0)) }
    }

    fun updateExistingTermCover(amount: Double) {
        draft.update { it.copy(existingTermCover = amount.coerceAtLeast(0.0)) }
    }

    fun updateTermMultiplier(mult: Double) {
        draft.update { it.copy(termIncomeMultiplier = mult.coerceIn(5.0, 25.0), hasCustomTermMultiplier = true) }
    }

    fun resetTermMultiplierToDefault() {
        draft.update { it.copy(hasCustomTermMultiplier = false) }
    }

    fun setMetroCity(metro: Boolean) {
        draft.update { it.copy(isMetroCity = metro) }
    }

    fun updateFamilyMembersCount(count: Int) {
        draft.update { it.copy(familyMembersCount = count.coerceIn(1, 10)) }
    }

    fun updateExistingHealthCover(amount: Double) {
        draft.update { it.copy(existingHealthCover = amount.coerceAtLeast(0.0)) }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepo: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FinancialRulesViewModel(prefsRepo, ratesRepo) as T
    }
}

package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.AssetAllocationRow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

// =============================================================================
// 1. MULTI-GOAL PLANNER ENGINE (CHILD EDUCATION, HOME, CAR, WEDDING, TRAVEL)
// =============================================================================

enum class GoalCategory(
    val displayName: String,
    val defaultInflation: Double,
    val defaultReturn: Double,
    val defaultYears: Int,
    val defaultCost: Double
) {
    CHILD_EDUCATION("Child Education", 8.0, 12.0, 15, 2500000.0),
    HOME("Home Down-Payment", 7.0, 11.0, 7, 2000000.0),
    CAR("Car Purchase", 6.0, 9.0, 4, 1200000.0),
    WEDDING("Wedding", 7.0, 11.0, 10, 1500000.0),
    TRAVEL("Dream Travel", 6.0, 9.0, 3, 400000.0),
    CUSTOM("Custom Goal", 6.0, 11.0, 5, 1000000.0)
}

data class FinancialGoalItem(
    val id: String,
    val name: String,
    val category: GoalCategory,
    val currentCost: Double,
    val yearsToGoal: Int,
    val inflationPercent: Double,
    val currentSavings: Double,
    val expectedReturnPercent: Double,
    val enabled: Boolean = true
)

data class GoalCalculationResult(
    val goal: FinancialGoalItem,
    val inflationAdjustedTarget: Double,
    val futureValueOfCurrentSavings: Double,
    val netTargetGap: Double,
    val monthlySipNeeded: Double,
    val totalSipInvestment: Double,
    val estimatedSipGain: Double,
    val isAlreadyFunded: Boolean
)

data class MultiGoalSummaryResult(
    val goalResults: List<GoalCalculationResult>,
    val activeGoalCount: Int,
    val totalCurrentCost: Double,
    val totalInflationAdjustedTarget: Double,
    val totalCurrentSavings: Double,
    val totalFutureValueOfSavings: Double,
    val combinedMonthlySipNeeded: Double
)

object MultiGoalPlannerEngine {

    fun calculateSingleGoal(goal: FinancialGoalItem): GoalCalculationResult {
        val safeYears = goal.yearsToGoal.coerceIn(1, 50)
        val safeCost = goal.currentCost.coerceAtLeast(0.0)
        val safeSavings = goal.currentSavings.coerceAtLeast(0.0)
        val inf = goal.inflationPercent.coerceIn(0.0, 25.0) / 100.0
        val ret = goal.expectedReturnPercent.coerceIn(0.0, 30.0) / 100.0
        val months = safeYears * 12
        val monthlyRate = ret / 12.0

        val targetBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(safeCost).multiply(MoneyMath.bd((1.0 + inf).pow(safeYears.toDouble())), MoneyMath.MC)
        )
        val fvSavingsBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(safeSavings).multiply(MoneyMath.bd((1.0 + monthlyRate).pow(months.toDouble())), MoneyMath.MC)
        )
        val netGapBd = MoneyMath.roundPaiseBd(targetBd.subtract(fvSavingsBd).max(BigDecimal.ZERO))

        val sipNeededBd = if (netGapBd <= BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else if (abs(monthlyRate) < 1e-10) {
            MoneyMath.roundPaiseBd(netGapBd.divide(BigDecimal.valueOf(months.toLong()), MoneyMath.MC))
        } else {
            // Annuity-due monthly SIP factor: [((1+r)^n - 1) / r] * (1+r)
            val sipFactor = (((1.0 + monthlyRate).pow(months.toDouble()) - 1.0) / monthlyRate) * (1.0 + monthlyRate)
            MoneyMath.roundPaiseBd(netGapBd.divide(MoneyMath.bd(sipFactor), MoneyMath.MC))
        }

        val totalSipOutflowBd = MoneyMath.roundPaiseBd(
            sipNeededBd.multiply(BigDecimal.valueOf(months.toLong()))
        )
        val estGainBd = MoneyMath.roundPaiseBd(netGapBd.subtract(totalSipOutflowBd).max(BigDecimal.ZERO))

        return GoalCalculationResult(
            goal = goal,
            inflationAdjustedTarget = targetBd.toDouble(),
            futureValueOfCurrentSavings = fvSavingsBd.toDouble(),
            netTargetGap = netGapBd.toDouble(),
            monthlySipNeeded = sipNeededBd.toDouble(),
            totalSipInvestment = totalSipOutflowBd.toDouble(),
            estimatedSipGain = estGainBd.toDouble(),
            isAlreadyFunded = netGapBd <= BigDecimal.ZERO && safeCost > 0.0
        )
    }

    fun calculateAll(goals: List<FinancialGoalItem>): MultiGoalSummaryResult {
        val allResults = goals.map { calculateSingleGoal(it) }
        val active = allResults.filter { it.goal.enabled }

        var sumCost = BigDecimal.ZERO
        var sumTarget = BigDecimal.ZERO
        var sumSavings = BigDecimal.ZERO
        var sumFvSavings = BigDecimal.ZERO
        var sumMonthlySip = BigDecimal.ZERO

        for (r in active) {
            sumCost = sumCost.add(MoneyMath.bd(r.goal.currentCost))
            sumTarget = sumTarget.add(MoneyMath.bd(r.inflationAdjustedTarget))
            sumSavings = sumSavings.add(MoneyMath.bd(r.goal.currentSavings))
            sumFvSavings = sumFvSavings.add(MoneyMath.bd(r.futureValueOfCurrentSavings))
            sumMonthlySip = sumMonthlySip.add(MoneyMath.bd(r.monthlySipNeeded))
        }

        return MultiGoalSummaryResult(
            goalResults = allResults,
            activeGoalCount = active.size,
            totalCurrentCost = MoneyMath.roundPaiseBd(sumCost).toDouble(),
            totalInflationAdjustedTarget = MoneyMath.roundPaiseBd(sumTarget).toDouble(),
            totalCurrentSavings = MoneyMath.roundPaiseBd(sumSavings).toDouble(),
            totalFutureValueOfSavings = MoneyMath.roundPaiseBd(sumFvSavings).toDouble(),
            combinedMonthlySipNeeded = MoneyMath.roundPaiseBd(sumMonthlySip).toDouble()
        )
    }
}

// =============================================================================
// 2. FINANCIAL RULES ENGINE (50/30/20, EMERGENCY FUND, INSURANCE COVER)
// =============================================================================

data class BudgetSplitResult(
    val isValid: Boolean,
    val validationError: String? = null,
    val monthlyIncome: Double = 0.0,
    val needsPercent: Double = 50.0,
    val wantsPercent: Double = 30.0,
    val savingsPercent: Double = 20.0,
    val needsMonthly: Double = 0.0,
    val wantsMonthly: Double = 0.0,
    val savingsMonthly: Double = 0.0,
    val needsAnnual: Double = 0.0,
    val wantsAnnual: Double = 0.0,
    val savingsAnnual: Double = 0.0
)

data class EmergencyFundResult(
    val monthlyEssentialExpenses: Double,
    val targetMonths: Int,
    val targetCorpus: Double,
    val currentLiquidSavings: Double,
    val shortfall: Double,
    val fundedPercent: Double,
    val monthsToBuild: Int?
)

data class InsuranceCoverResult(
    val recommendedTermCover: Double,
    val existingTermCover: Double,
    val termCoverGap: Double,
    val termMultiplierApplied: Double,
    val recommendedHealthCover: Double,
    val existingHealthCover: Double,
    val healthCoverGap: Double,
    val healthRuleNote: String
)

object FinancialRulesEngine {

    private val TWELVE = BigDecimal("12")

    fun calculateBudgetSplit(monthlyIncome: Double, needsPercent: Double, wantsPercent: Double, savingsPercent: Double): BudgetSplitResult {
        if (monthlyIncome < 0.0) {
            return BudgetSplitResult(isValid = false, validationError = "Monthly income cannot be negative.")
        }
        val sum = needsPercent + wantsPercent + savingsPercent
        if (needsPercent < 0.0 || wantsPercent < 0.0 || savingsPercent < 0.0 || abs(sum - 100.0) > 0.5) {
            return BudgetSplitResult(
                isValid = false,
                validationError = "Needs + Wants + Savings must add up to 100% (currently ${sum.toInt()}%)."
            )
        }

        val incBd = MoneyMath.bd(monthlyIncome)
        val needsBd = MoneyMath.percentageOf(incBd, MoneyMath.bd(needsPercent))
        val wantsBd = MoneyMath.percentageOf(incBd, MoneyMath.bd(wantsPercent))
        // Absorb rounding in Savings bucket so Needs + Wants + Savings == monthlyIncome exactly
        val savingsBd = MoneyMath.roundPaiseBd(incBd.subtract(needsBd).subtract(wantsBd).max(BigDecimal.ZERO))

        return BudgetSplitResult(
            isValid = true,
            monthlyIncome = MoneyMath.roundPaise(monthlyIncome),
            needsPercent = needsPercent,
            wantsPercent = wantsPercent,
            savingsPercent = savingsPercent,
            needsMonthly = needsBd.toDouble(),
            wantsMonthly = wantsBd.toDouble(),
            savingsMonthly = savingsBd.toDouble(),
            needsAnnual = MoneyMath.roundPaiseBd(needsBd.multiply(TWELVE)).toDouble(),
            wantsAnnual = MoneyMath.roundPaiseBd(wantsBd.multiply(TWELVE)).toDouble(),
            savingsAnnual = MoneyMath.roundPaiseBd(savingsBd.multiply(TWELVE)).toDouble()
        )
    }

    fun calculateEmergencyFund(
        monthlyEssentialExpenses: Double,
        targetMonths: Int,
        currentLiquidSavings: Double,
        monthlyAllocation: Double
    ): EmergencyFundResult {
        val safeExp = monthlyEssentialExpenses.coerceAtLeast(0.0)
        val safeMonths = targetMonths.coerceIn(1, 24)
        val safeCurrent = currentLiquidSavings.coerceAtLeast(0.0)

        val targetBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(safeExp).multiply(BigDecimal.valueOf(safeMonths.toLong()))
        )
        val currentBd = MoneyMath.roundPaiseBd(MoneyMath.bd(safeCurrent))
        val shortfallBd = MoneyMath.roundPaiseBd(targetBd.subtract(currentBd).max(BigDecimal.ZERO))

        val fundedPct = if (targetBd > BigDecimal.ZERO) {
            currentBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(targetBd, 1, RoundingMode.HALF_UP)
                .toDouble()
                .coerceIn(0.0, 100.0)
        } else {
            100.0
        }

        val monthsToBuild = when {
            shortfallBd <= BigDecimal.ZERO -> 0
            monthlyAllocation > 0.0 -> ceil(shortfallBd.toDouble() / monthlyAllocation).toInt()
            else -> null
        }

        return EmergencyFundResult(
            monthlyEssentialExpenses = MoneyMath.roundPaise(safeExp),
            targetMonths = safeMonths,
            targetCorpus = targetBd.toDouble(),
            currentLiquidSavings = currentBd.toDouble(),
            shortfall = shortfallBd.toDouble(),
            fundedPercent = fundedPct,
            monthsToBuild = monthsToBuild
        )
    }

    fun calculateInsuranceCover(
        annualIncome: Double,
        existingLiabilities: Double,
        existingTermCover: Double,
        termIncomeMultiplier: Double,
        isMetroCity: Boolean,
        familyMembersCount: Int,
        existingHealthCover: Double,
        metroBaseHealthCover: Double,
        nonMetroBaseHealthCover: Double
    ): InsuranceCoverResult {
        val safeIncome = annualIncome.coerceAtLeast(0.0)
        val safeLiab = existingLiabilities.coerceAtLeast(0.0)
        val safeTerm = existingTermCover.coerceAtLeast(0.0)
        val safeHealth = existingHealthCover.coerceAtLeast(0.0)
        val safeMembers = familyMembersCount.coerceIn(1, 10)

        val recTermBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(safeIncome).multiply(MoneyMath.bd(termIncomeMultiplier.coerceAtLeast(1.0)))
                .add(MoneyMath.bd(safeLiab))
        )
        val termGapBd = MoneyMath.roundPaiseBd(recTermBd.subtract(MoneyMath.bd(safeTerm)).max(BigDecimal.ZERO))

        // Health cover rule of thumb: Base city cover + 25% per additional family member beyond 2
        val baseHealth = if (isMetroCity) metroBaseHealthCover else nonMetroBaseHealthCover
        val extraMembers = (safeMembers - 2).coerceAtLeast(0)
        val memberFactor = 1.0 + (extraMembers * 0.25)
        val recHealthBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(baseHealth).multiply(MoneyMath.bd(memberFactor), MoneyMath.MC)
        )
        val healthGapBd = MoneyMath.roundPaiseBd(recHealthBd.subtract(MoneyMath.bd(safeHealth)).max(BigDecimal.ZERO))

        val cityLabel = if (isMetroCity) "Metro" else "Non-Metro"
        val healthNote = "$cityLabel base cover (${CurrencyFormatter.formatCompact(
            baseHealth,
            CurrencyCode.INR
        )}) scaled for $safeMembers member(s). Consider a super top-up if family includes senior parents."

        return InsuranceCoverResult(
            recommendedTermCover = recTermBd.toDouble(),
            existingTermCover = MoneyMath.roundPaise(safeTerm),
            termCoverGap = termGapBd.toDouble(),
            termMultiplierApplied = termIncomeMultiplier,
            recommendedHealthCover = recHealthBd.toDouble(),
            existingHealthCover = MoneyMath.roundPaise(safeHealth),
            healthCoverGap = healthGapBd.toDouble(),
            healthRuleNote = healthNote
        )
    }
}

// =============================================================================
// 3. EDUCATIONAL ASSET ALLOCATION ENGINE ("WHERE TO INVEST")
// =============================================================================

enum class RiskProfile(val id: String, val displayName: String, val summary: String) {
    CONSERVATIVE(
        id = "CONSERVATIVE",
        displayName = "Conservative",
        summary = "Prioritizes capital preservation and low volatility over high long-term growth."
    ),
    MODERATE(
        id = "MODERATE",
        displayName = "Moderate",
        summary = "Balances steady fixed-income stability with measured equity growth to beat inflation."
    ),
    AGGRESSIVE(
        id = "AGGRESSIVE",
        displayName = "Aggressive",
        summary = "Focuses on long-term wealth creation and can stay invested through market downturns."
    )
}

data class RiskQuestion(val id: String, val prompt: String, val options: List<RiskQuestionOption>)

// score: 1 = Conservative, 2 = Moderate, 3 = Aggressive
data class RiskQuestionOption(val label: String, val score: Int)

data class AssetAllocationResult(
    val isValid: Boolean,
    val age: Int,
    val ageBandLabel: String,
    val riskProfile: RiskProfile,
    val questionnaireScore: Int,
    val equityPercent: Double,
    val debtPercent: Double,
    val goldPercent: Double,
    val cashLiquidPercent: Double,
    val equityAmount: Double,
    val debtAmount: Double,
    val goldAmount: Double,
    val cashLiquidAmount: Double,
    val horizonHint: String,
    val rebalanceNote: String
)

object AssetAllocationEngine {

    val questionnaire: List<RiskQuestion> = listOf(
        RiskQuestion(
            id = "horizon",
            prompt = "1. When do you expect to need most of this money?",
            options = listOf(
                RiskQuestionOption("Within 3 years", 1),
                RiskQuestionOption("3 to 7 years", 2),
                RiskQuestionOption("More than 7 years", 3)
            )
        ),
        RiskQuestion(
            id = "drawdown",
            prompt = "2. If your portfolio temporarily drops 20% in a market crash, what would you do?",
            options = listOf(
                RiskQuestionOption("Exit immediately to protect capital", 1),
                RiskQuestionOption("Wait calmly without panicking", 2),
                RiskQuestionOption("Stay invested & continue regular contributions", 3)
            )
        ),
        RiskQuestion(
            id = "income_stability",
            prompt = "3. How predictable is your household income & emergency reserve?",
            options = listOf(
                RiskQuestionOption("Variable income / < 3 months emergency fund", 1),
                RiskQuestionOption("Steady income with 3–6 months emergency fund", 2),
                RiskQuestionOption("Very stable income with 6+ months emergency fund", 3)
            )
        ),
        RiskQuestion(
            id = "goal_priority",
            prompt = "4. What is your primary objective for these savings?",
            options = listOf(
                RiskQuestionOption("Safety of principal & regular income", 1),
                RiskQuestionOption("Balanced growth & stability", 2),
                RiskQuestionOption("Long-term inflation-beating growth", 3)
            )
        ),
        RiskQuestion(
            id = "experience",
            prompt = "5. How familiar are you with market-linked asset classes?",
            options = listOf(
                RiskQuestionOption("New — mostly used savings/FDs", 1),
                RiskQuestionOption("Some familiarity with debt & equity funds", 2),
                RiskQuestionOption("Comfortable with multi-year market cycles", 3)
            )
        )
    )

    fun scoreRiskProfile(answers: Map<String, Int>): Pair<Int, RiskProfile> {
        val total = questionnaire.sumOf { q -> (answers[q.id] ?: 2).coerceIn(1, 3) }
        val profile = when {
            total <= 8 -> RiskProfile.CONSERVATIVE
            total <= 12 -> RiskProfile.MODERATE
            else -> RiskProfile.AGGRESSIVE
        }
        return total to profile
    }

    fun resolveAllocation(
        age: Int,
        riskProfile: RiskProfile,
        questionnaireScore: Int,
        investableAmount: Double,
        matrix: List<AssetAllocationRow>
    ): AssetAllocationResult {
        val safeAge = age.coerceIn(18, 90)
        val matched = matrix.firstOrNull { row ->
            safeAge in row.ageMin..row.ageMax && row.riskProfile.equals(riskProfile.id, ignoreCase = true)
        } ?: matrix.firstOrNull { row ->
            row.riskProfile.equals(riskProfile.id, ignoreCase = true)
        } ?: matrix.firstOrNull()

        if (matched == null) {
            return AssetAllocationResult(
                isValid = false,
                age = safeAge,
                ageBandLabel = "",
                riskProfile = riskProfile,
                questionnaireScore = questionnaireScore,
                equityPercent = 0.0,
                debtPercent = 0.0,
                goldPercent = 0.0,
                cashLiquidPercent = 0.0,
                equityAmount = 0.0,
                debtAmount = 0.0,
                goldAmount = 0.0,
                cashLiquidAmount = 0.0,
                horizonHint = "",
                rebalanceNote = ""
            )
        }

        val amtBd = MoneyMath.bd(investableAmount.coerceAtLeast(0.0))
        val eqBd = MoneyMath.percentageOf(amtBd, MoneyMath.bd(matched.equityPercent))
        val debtBd = MoneyMath.percentageOf(amtBd, MoneyMath.bd(matched.debtPercent))
        val goldBd = MoneyMath.percentageOf(amtBd, MoneyMath.bd(matched.goldPercent))
        // Absorb rounding in Cash/Liquid bucket so amounts sum exactly to investableAmount
        val cashBd = MoneyMath.roundPaiseBd(amtBd.subtract(eqBd).subtract(debtBd).subtract(goldBd).max(BigDecimal.ZERO))

        return AssetAllocationResult(
            isValid = true,
            age = safeAge,
            ageBandLabel = matched.ageBandLabel,
            riskProfile = riskProfile,
            questionnaireScore = questionnaireScore,
            equityPercent = matched.equityPercent,
            debtPercent = matched.debtPercent,
            goldPercent = matched.goldPercent,
            cashLiquidPercent = matched.cashLiquidPercent,
            equityAmount = eqBd.toDouble(),
            debtAmount = debtBd.toDouble(),
            goldAmount = goldBd.toDouble(),
            cashLiquidAmount = cashBd.toDouble(),
            horizonHint = matched.horizonHint,
            rebalanceNote = matched.rebalanceNote
        )
    }
}

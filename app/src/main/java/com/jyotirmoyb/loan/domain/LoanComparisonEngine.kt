package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class ComparisonScenarioInput(
    val id: Long,
    val label: String,
    val categoryId: String,
    val categoryName: String,
    val variantSummary: String,
    val input: CalculationInput,
    val category: LoanCategory? = null
)

data class ComparisonScenarioEvaluated(
    val id: Long,
    val label: String,
    val categoryId: String,
    val categoryName: String,
    val variantSummary: String,
    val input: CalculationInput,
    val result: CalculationResult,
    val isCheapest: Boolean,
    val savingsVsMostExpensive: Double,
    val extraCostVsCheapest: Double,
    val yearlyBalances: List<Double>
)

data class LoanComparisonReport(
    val scenarios: List<ComparisonScenarioEvaluated> = emptyList(),
    val cheapestId: Long? = null,
    val maxSavings: Double = 0.0,
    val maxYears: Int = 0
)

/**
 * Pure domain engine evaluating up to 3 loan offers side-by-side.
 * Ranks by Effective Total Cost (principal + interest + fees + GST), flags the cheapest offer,
 * computes savings against the costliest offer, and builds year-by-year remaining balance series.
 */
object LoanComparisonEngine {

    const val MAX_SCENARIOS = 3

    fun evaluate(scenarios: List<ComparisonScenarioInput>): LoanComparisonReport {
        val capped = scenarios.take(MAX_SCENARIOS)
        if (capped.isEmpty()) return LoanComparisonReport()

        val computed = capped.map { item ->
            val result = LoanCalculationEngine.calculate(item.input, item.category)
            item to result
        }

        val validPairs = computed.filter { it.second.isValid }
        val cheapestPair = validPairs.minByOrNull { MoneyMath.bd(it.second.effectiveTotalCost) }
        val priciestPair = validPairs.maxByOrNull { MoneyMath.bd(it.second.effectiveTotalCost) }

        val minCostBd = cheapestPair?.let { MoneyMath.bd(it.second.effectiveTotalCost) } ?: BigDecimal.ZERO
        val maxCostBd = priciestPair?.let { MoneyMath.bd(it.second.effectiveTotalCost) } ?: BigDecimal.ZERO
        val maxSavingsBd = if (validPairs.size >= 2) {
            MoneyMath.roundPaiseBd(maxCostBd.subtract(minCostBd).max(BigDecimal.ZERO))
        } else {
            MoneyMath.ZERO
        }

        val evaluatedList = computed.map { (item, result) ->
            val costBd = MoneyMath.bd(result.effectiveTotalCost)
            val savingsVsMax = if (result.isValid && validPairs.size >= 2) {
                MoneyMath.roundPaiseBd(maxCostBd.subtract(costBd).max(BigDecimal.ZERO)).toDouble()
            } else {
                0.0
            }
            val extraVsMin = if (result.isValid && validPairs.size >= 2) {
                MoneyMath.roundPaiseBd(costBd.subtract(minCostBd).max(BigDecimal.ZERO)).toDouble()
            } else {
                0.0
            }
            val balances = buildYearlyBalances(item.input.principal, result)
            ComparisonScenarioEvaluated(
                id = item.id,
                label = item.label,
                categoryId = item.categoryId,
                categoryName = item.categoryName,
                variantSummary = item.variantSummary,
                input = item.input,
                result = result,
                isCheapest = cheapestPair != null && item.id == cheapestPair.first.id && validPairs.size >= 2,
                savingsVsMostExpensive = savingsVsMax,
                extraCostVsCheapest = extraVsMin,
                yearlyBalances = balances
            )
        }

        val maxYears = evaluatedList.maxOfOrNull { (it.yearlyBalances.size - 1).coerceAtLeast(0) } ?: 0

        return LoanComparisonReport(
            scenarios = evaluatedList,
            cheapestId = if (validPairs.size >= 2) cheapestPair?.first?.id else null,
            maxSavings = maxSavingsBd.setScale(2, RoundingMode.HALF_UP).toDouble(),
            maxYears = maxYears
        )
    }

    private fun buildYearlyBalances(principal: Double, result: CalculationResult): List<Double> {
        if (!result.isValid || result.schedule.isEmpty()) return listOf(MoneyMath.roundPaise(principal))
        val series = mutableListOf(MoneyMath.roundPaise(principal))
        if (result.yearlySummaries.isNotEmpty()) {
            result.yearlySummaries.forEach { yr ->
                series.add(MoneyMath.roundPaise(yr.endingBalance))
            }
        } else {
            series.add(MoneyMath.roundPaise(result.schedule.last().closingBalance))
        }
        return series
    }
}

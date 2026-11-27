package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow

// =============================================================================
// 1. RETIREMENT PLANNER ENGINE (CORPUS, SIP & SHORTFALL PROJECTION)
// =============================================================================

data class RetirementPlannerInput(
    val currentAge: Int = 30,
    val retirementAge: Int = 60,
    val lifeExpectancy: Int = 85,
    val currentMonthlyExpenses: Double = 60000.0,
    val existingRetirementSavings: Double = 500000.0,
    val currentMonthlySip: Double = 15000.0,
    val inflationPercent: Double = 6.0,
    val preRetirementReturnPercent: Double = 12.0,
    val postRetirementReturnPercent: Double = 7.5
)

data class RetirementProjectionPoint(
    val age: Int,
    val yearIndex: Int,
    val projectedCorpus: Double,
    val targetTrajectoryCorpus: Double,
    val shortfallAtAge: Double
)

data class RetirementPlannerResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val yearsToRetirement: Int = 0,
    val retirementDurationYears: Int = 0,
    val monthlyExpenseAtRetirement: Double = 0.0,
    val annualExpenseAtRetirement: Double = 0.0,
    val realPostRetirementReturnPercent: Double = 0.0,
    val requiredCorpusAtRetirement: Double = 0.0,
    val projectedCorpusFromExisting: Double = 0.0,
    val projectedCorpusFromCurrentSip: Double = 0.0,
    val projectedTotalCorpus: Double = 0.0,
    val corpusShortfall: Double = 0.0,
    val corpusSurplus: Double = 0.0,
    val fundingRatioPercent: Double = 0.0,
    val monthlySipNeededTotal: Double = 0.0,
    val additionalMonthlySipNeeded: Double = 0.0,
    val projectionPoints: List<RetirementProjectionPoint> = emptyList()
)

object RetirementPlannerEngine {

    private val TWELVE = BigDecimal("12")

    fun calculate(input: RetirementPlannerInput): RetirementPlannerResult {
        val errors = mutableListOf<String>()
        if (input.currentAge !in 18..75) errors.add("Current age must be between 18 and 75.")
        if (input.retirementAge <= input.currentAge || input.retirementAge > 85) {
            errors.add("Retirement age must be greater than current age (up to 85).")
        }
        if (input.lifeExpectancy <= input.retirementAge || input.lifeExpectancy > 105) {
            errors.add("Life expectancy must be greater than retirement age (up to 105).")
        }
        if (input.currentMonthlyExpenses <= 0.0) errors.add("Monthly expenses must be greater than 0.")
        if (input.existingRetirementSavings < 0.0) errors.add("Existing savings cannot be negative.")
        if (input.currentMonthlySip < 0.0) errors.add("Current monthly SIP cannot be negative.")
        if (input.inflationPercent < 0.0 || input.inflationPercent > 20.0) {
            errors.add("Inflation must be between 0% and 20%.")
        }
        if (input.preRetirementReturnPercent < 0.0 || input.preRetirementReturnPercent > 30.0) {
            errors.add("Pre-retirement return must be between 0% and 30%.")
        }
        if (input.postRetirementReturnPercent < 0.0 || input.postRetirementReturnPercent > 25.0) {
            errors.add("Post-retirement return must be between 0% and 25%.")
        }
        if (errors.isNotEmpty()) {
            return RetirementPlannerResult(isValid = false, validationErrors = errors)
        }

        val yearsToRet = input.retirementAge - input.currentAge
        val retYears = input.lifeExpectancy - input.retirementAge
        val preMonths = yearsToRet * 12
        val postMonths = retYears * 12

        val infRate = input.inflationPercent / 100.0
        val preReturn = input.preRetirementReturnPercent / 100.0
        val postReturn = input.postRetirementReturnPercent / 100.0

        // 1. Future monthly expense at retirement age
        val infFactor = (1.0 + infRate).pow(yearsToRet.toDouble())
        val monthlyExpAtRetBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(input.currentMonthlyExpenses).multiply(MoneyMath.bd(infFactor), MoneyMath.MC)
        )
        val annualExpAtRetBd = MoneyMath.roundPaiseBd(monthlyExpAtRetBd.multiply(TWELVE, MoneyMath.MC))

        // 2. Real return during retirement & Present Value of inflation-indexed monthly expenses (annuity due)
        val realAnnualRate = ((1.0 + postReturn) / (1.0 + infRate)) - 1.0
        val realMonthlyRate = (1.0 + realAnnualRate).pow(1.0 / 12.0) - 1.0

        val pvFactor = if (abs(realMonthlyRate) < 1e-10) {
            postMonths.toDouble()
        } else {
            val disc = (1.0 + realMonthlyRate).pow(-postMonths.toDouble())
            ((1.0 - disc) / realMonthlyRate) * (1.0 + realMonthlyRate)
        }
        val requiredCorpusBd = MoneyMath.roundPaiseBd(
            monthlyExpAtRetBd.multiply(MoneyMath.bd(pvFactor), MoneyMath.MC)
        )

        // 3. Projected corpus from existing savings & current monthly SIP (monthly compounding)
        val rPreM = preReturn / 12.0
        val existingFactor = (1.0 + rPreM).pow(preMonths.toDouble())
        val projectedExistingBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(input.existingRetirementSavings).multiply(MoneyMath.bd(existingFactor), MoneyMath.MC)
        )

        val sipUnitFactor = if (abs(rPreM) < 1e-10) {
            preMonths.toDouble()
        } else {
            (((1.0 + rPreM).pow(preMonths.toDouble()) - 1.0) / rPreM) * (1.0 + rPreM)
        }

        val projectedSipBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(input.currentMonthlySip).multiply(MoneyMath.bd(sipUnitFactor), MoneyMath.MC)
        )
        val projectedTotalBd = MoneyMath.roundPaiseBd(projectedExistingBd.add(projectedSipBd))

        val shortfallBd = MoneyMath.roundPaiseBd(requiredCorpusBd.subtract(projectedTotalBd).max(BigDecimal.ZERO))
        val surplusBd = MoneyMath.roundPaiseBd(projectedTotalBd.subtract(requiredCorpusBd).max(BigDecimal.ZERO))

        // 4. Monthly SIP needed to bridge gap after existing savings
        val netGapAfterExistingBd = requiredCorpusBd.subtract(projectedExistingBd).max(BigDecimal.ZERO)
        val totalSipNeededBd = if (sipUnitFactor > 0.0 && netGapAfterExistingBd > BigDecimal.ZERO) {
            MoneyMath.roundPaiseBd(netGapAfterExistingBd.divide(MoneyMath.bd(sipUnitFactor), MoneyMath.MC))
        } else {
            BigDecimal.ZERO
        }

        val additionalSipNeededBd = MoneyMath.roundPaiseBd(
            totalSipNeededBd.subtract(MoneyMath.bd(input.currentMonthlySip)).max(BigDecimal.ZERO)
        )

        val fundingRatio = if (requiredCorpusBd > BigDecimal.ZERO) {
            projectedTotalBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(requiredCorpusBd, 1, RoundingMode.HALF_UP)
                .toDouble()
                .coerceAtLeast(0.0)
        } else {
            100.0
        }

        // 5. Year-by-year trajectory for Corpus vs Required Target & Shortfall chart
        val points = mutableListOf<RetirementProjectionPoint>()
        var runningProjected = MoneyMath.bd(input.existingRetirementSavings)
        var runningTarget = MoneyMath.bd(input.existingRetirementSavings)
        val monthlyRateBd = MoneyMath.bd(rPreM)
        val currentSipBd = MoneyMath.bd(input.currentMonthlySip)

        points.add(
            RetirementProjectionPoint(
                age = input.currentAge,
                yearIndex = 0,
                projectedCorpus = MoneyMath.roundPaiseBd(runningProjected).toDouble(),
                targetTrajectoryCorpus = MoneyMath.roundPaiseBd(runningTarget).toDouble(),
                shortfallAtAge = 0.0
            )
        )

        for (year in 1..yearsToRet) {
            for (m in 1..12) {
                runningProjected = runningProjected.add(currentSipBd)
                    .multiply(MoneyMath.ONE.add(monthlyRateBd), MoneyMath.MC)
                runningTarget = runningTarget.add(totalSipNeededBd)
                    .multiply(MoneyMath.ONE.add(monthlyRateBd), MoneyMath.MC)
            }
            val projRounded = MoneyMath.roundPaiseBd(runningProjected)
            val targetRounded = if (year == yearsToRet) {
                requiredCorpusBd.max(projRounded)
            } else {
                MoneyMath.roundPaiseBd(runningTarget)
            }
            val gapAtAge = MoneyMath.roundPaiseBd(targetRounded.subtract(projRounded).max(BigDecimal.ZERO))
            points.add(
                RetirementProjectionPoint(
                    age = input.currentAge + year,
                    yearIndex = year,
                    projectedCorpus = projRounded.toDouble(),
                    targetTrajectoryCorpus = targetRounded.toDouble(),
                    shortfallAtAge = gapAtAge.toDouble()
                )
            )
        }

        return RetirementPlannerResult(
            isValid = true,
            yearsToRetirement = yearsToRet,
            retirementDurationYears = retYears,
            monthlyExpenseAtRetirement = monthlyExpAtRetBd.toDouble(),
            annualExpenseAtRetirement = annualExpAtRetBd.toDouble(),
            realPostRetirementReturnPercent = BigDecimal.valueOf(realAnnualRate * 100.0)
                .setScale(2, RoundingMode.HALF_UP).toDouble(),
            requiredCorpusAtRetirement = requiredCorpusBd.toDouble(),
            projectedCorpusFromExisting = projectedExistingBd.toDouble(),
            projectedCorpusFromCurrentSip = projectedSipBd.toDouble(),
            projectedTotalCorpus = projectedTotalBd.toDouble(),
            corpusShortfall = shortfallBd.toDouble(),
            corpusSurplus = surplusBd.toDouble(),
            fundingRatioPercent = fundingRatio,
            monthlySipNeededTotal = totalSipNeededBd.toDouble(),
            additionalMonthlySipNeeded = additionalSipNeededBd.toDouble(),
            projectionPoints = points
        )
    }
}

// =============================================================================
// 2. NATIONAL PENSION SYSTEM (NPS) ENGINE
// =============================================================================

data class NpsInput(
    val currentAge: Int = 30,
    val retirementAge: Int = 60,
    val monthlyContribution: Double = 10000.0,
    val annualStepUpPercent: Double = 5.0,
    val existingCorpus: Double = 100000.0,
    val expectedReturnPercent: Double = 10.0,
    val annuityAllocationPercent: Double = 40.0,
    val annuityRatePercent: Double = 6.0
)

data class NpsYearlyRow(val age: Int, val annualContribution: Double, val totalInvestedToDate: Double, val closingCorpus: Double)

data class NpsResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val investmentYears: Int = 0,
    val totalInvested: Double = 0.0,
    val wealthGained: Double = 0.0,
    val projectedCorpus: Double = 0.0,
    val annuityPercentApplied: Double = 40.0,
    val lumpSumPercentApplied: Double = 60.0,
    val lumpSumWithdrawal: Double = 0.0,
    val annuityCorpus: Double = 0.0,
    val estimatedMonthlyPension: Double = 0.0,
    val eligibleForFullLumpSumExit: Boolean = false,
    val yearlyRows: List<NpsYearlyRow> = emptyList()
)

object NpsEngine {

    private val TWELVE = BigDecimal("12")

    fun calculate(input: NpsInput, minAnnuityPercent: Double = 40.0, smallCorpusFullExitLimit: Double = 500000.0): NpsResult {
        val errors = mutableListOf<String>()
        if (input.currentAge !in 18..69) errors.add("Entry age must be between 18 and 69.")
        if (input.retirementAge <= input.currentAge || input.retirementAge > 75) {
            errors.add("Exit age must be greater than current age (up to 75).")
        }
        if (input.monthlyContribution < 0.0) errors.add("Monthly contribution cannot be negative.")
        if (input.existingCorpus < 0.0) errors.add("Existing NPS balance cannot be negative.")
        if (input.monthlyContribution == 0.0 && input.existingCorpus == 0.0) {
            errors.add("Enter a monthly contribution or existing NPS balance.")
        }
        if (input.expectedReturnPercent < 0.0 || input.expectedReturnPercent > 25.0) {
            errors.add("Expected return must be between 0% and 25%.")
        }
        if (input.annuityRatePercent < 0.0 || input.annuityRatePercent > 20.0) {
            errors.add("Annuity rate must be between 0% and 20%.")
        }
        if (errors.isNotEmpty()) return NpsResult(isValid = false, validationErrors = errors)

        val years = input.retirementAge - input.currentAge
        val monthlyRateBd = MoneyMath.bd(input.expectedReturnPercent / 1200.0)
        val stepFactor = MoneyMath.ONE.add(MoneyMath.bd(input.annualStepUpPercent.coerceIn(0.0, 25.0) / 100.0))

        var corpusBd = MoneyMath.bd(input.existingCorpus)
        var totalInvestedBd = MoneyMath.bd(input.existingCorpus)
        var currentMonthlyBd = MoneyMath.bd(input.monthlyContribution)
        val rows = mutableListOf<NpsYearlyRow>()

        for (y in 1..years) {
            var yearlyContrib = BigDecimal.ZERO
            for (m in 1..12) {
                corpusBd = corpusBd.add(currentMonthlyBd)
                    .multiply(MoneyMath.ONE.add(monthlyRateBd), MoneyMath.MC)
                totalInvestedBd = totalInvestedBd.add(currentMonthlyBd)
                yearlyContrib = yearlyContrib.add(currentMonthlyBd)
            }
            rows.add(
                NpsYearlyRow(
                    age = input.currentAge + y,
                    annualContribution = MoneyMath.roundPaiseBd(yearlyContrib).toDouble(),
                    totalInvestedToDate = MoneyMath.roundPaiseBd(totalInvestedBd).toDouble(),
                    closingCorpus = MoneyMath.roundPaiseBd(corpusBd).toDouble()
                )
            )
            currentMonthlyBd = currentMonthlyBd.multiply(stepFactor, MoneyMath.MC)
        }

        val finalCorpusBd = MoneyMath.roundPaiseBd(corpusBd)
        val finalInvestedBd = MoneyMath.roundPaiseBd(totalInvestedBd)
        val gainBd = MoneyMath.roundPaiseBd(finalCorpusBd.subtract(finalInvestedBd).max(BigDecimal.ZERO))

        val fullExitEligible = finalCorpusBd <= MoneyMath.bd(smallCorpusFullExitLimit)
        val effectiveMinAnnuity = if (fullExitEligible) 0.0 else minAnnuityPercent.coerceIn(0.0, 100.0)
        val annuityPct = input.annuityAllocationPercent.coerceIn(effectiveMinAnnuity, 100.0)
        val lumpSumPct = (100.0 - annuityPct).coerceIn(0.0, 100.0)

        val annuityCorpusBd = MoneyMath.percentageOf(finalCorpusBd, MoneyMath.bd(annuityPct))
        val lumpSumBd = MoneyMath.roundPaiseBd(finalCorpusBd.subtract(annuityCorpusBd).max(BigDecimal.ZERO))

        val annualPensionBd = MoneyMath.percentageOf(annuityCorpusBd, MoneyMath.bd(input.annuityRatePercent))
        val monthlyPensionBd = MoneyMath.roundPaiseBd(annualPensionBd.divide(TWELVE, MoneyMath.MC))

        return NpsResult(
            isValid = true,
            investmentYears = years,
            totalInvested = finalInvestedBd.toDouble(),
            wealthGained = gainBd.toDouble(),
            projectedCorpus = finalCorpusBd.toDouble(),
            annuityPercentApplied = annuityPct,
            lumpSumPercentApplied = lumpSumPct,
            lumpSumWithdrawal = lumpSumBd.toDouble(),
            annuityCorpus = annuityCorpusBd.toDouble(),
            estimatedMonthlyPension = monthlyPensionBd.toDouble(),
            eligibleForFullLumpSumExit = fullExitEligible,
            yearlyRows = rows
        )
    }
}

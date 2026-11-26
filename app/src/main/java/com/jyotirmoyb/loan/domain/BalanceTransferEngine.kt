package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class TransferRecommendation {
    STRONGLY_RECOMMENDED,
    WORTH_CONSIDERING,
    NOT_RECOMMENDED
}

data class BalanceTransferAnalysisInput(
    val currentOutstanding: Double = 3500000.0,
    val currentAnnualRatePercent: Double = 9.75,
    val remainingMonths: Int = 180,
    val newAnnualRatePercent: Double = 8.40,
    val processingFeePercent: Double = 0.5,
    val processingFeeFlat: Double = 0.0,
    val gstOnFeesEnabled: Boolean = true,
    val gstRatePercent: Double = 18.0,
    val legalAndValuationCharges: Double = 12000.0,
    val foreclosureFeePercent: Double = 0.0,
    val includeTopUp: Boolean = false,
    val topUpAmount: Double = 500000.0,
    val topUpAnnualRatePercent: Double = 9.25,
    val topUpTenureMonths: Int = 120
)

data class BalanceTransferAnalysisResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val currentEmi: Double = 0.0,
    val newEmi: Double = 0.0,
    val monthlyEmiSavings: Double = 0.0,
    val currentRemainingInterest: Double = 0.0,
    val newTotalInterest: Double = 0.0,
    val processingFeeAmount: Double = 0.0,
    val gstOnFeeAmount: Double = 0.0,
    val foreclosureFeeAmount: Double = 0.0,
    val legalChargesAmount: Double = 0.0,
    val totalSwitchingCost: Double = 0.0,
    val grossInterestSavings: Double = 0.0,
    val netLifetimeSavings: Double = 0.0,
    val breakEvenMonth: Int? = null,
    val topUpEmi: Double = 0.0,
    val topUpTotalInterest: Double = 0.0,
    val blendedEmi: Double = 0.0,
    val recommendation: TransferRecommendation = TransferRecommendation.NOT_RECOMMENDED,
    val recommendationHeadline: String = "",
    val recommendationDetail: String = ""
)

/**
 * Pure domain engine for Loan Balance Transfer & Top-Up analysis.
 * Accounts for processing fee (% + flat), GST on fees, legal/valuation charges, and foreclosure charges
 * using exact BigDecimal math, and computes break-even month and net lifetime savings.
 */
object BalanceTransferEngine {

    fun analyze(input: BalanceTransferAnalysisInput): BalanceTransferAnalysisResult {
        val errors = mutableListOf<String>()
        if (input.currentOutstanding <= 0.0) errors.add("Current outstanding must be greater than 0.")
        if (input.remainingMonths <= 0 || input.remainingMonths > 420) {
            errors.add("Remaining tenure must be between 1 and 420 months.")
        }
        if (input.currentAnnualRatePercent <= 0.0 || input.currentAnnualRatePercent > 40.0) {
            errors.add("Current rate must be between 0.1% and 40%.")
        }
        if (input.newAnnualRatePercent <= 0.0 || input.newAnnualRatePercent > 40.0) {
            errors.add("New bank rate must be between 0.1% and 40%.")
        }
        if (input.includeTopUp && input.topUpAmount <= 0.0) {
            errors.add("Top-up amount must be greater than 0 when enabled.")
        }
        if (input.includeTopUp && (input.topUpTenureMonths <= 0 || input.topUpTenureMonths > 360)) {
            errors.add("Top-up tenure must be between 1 and 360 months.")
        }
        if (errors.isNotEmpty()) {
            return BalanceTransferAnalysisResult(isValid = false, validationErrors = errors)
        }

        val principalBd = MoneyMath.bd(input.currentOutstanding)
        val monthsBd = BigDecimal(input.remainingMonths)

        val currentEmiBd = MoneyMath.standardEmiBd(
            principalBd,
            MoneyMath.bd(input.currentAnnualRatePercent),
            input.remainingMonths
        )
        val newEmiBd = MoneyMath.standardEmiBd(
            principalBd,
            MoneyMath.bd(input.newAnnualRatePercent),
            input.remainingMonths
        )

        val currentInterestBd = MoneyMath.roundPaiseBd(
            currentEmiBd.multiply(monthsBd, MoneyMath.MC).subtract(principalBd).max(BigDecimal.ZERO)
        )
        val newInterestBd = MoneyMath.roundPaiseBd(
            newEmiBd.multiply(monthsBd, MoneyMath.MC).subtract(principalBd).max(BigDecimal.ZERO)
        )

        val monthlySavingsBd = MoneyMath.roundPaiseBd(currentEmiBd.subtract(newEmiBd))
        val grossInterestSavingsBd = MoneyMath.roundPaiseBd(currentInterestBd.subtract(newInterestBd))

        // Switching costs
        val procPercentFeeBd = MoneyMath.percentageOf(principalBd, MoneyMath.bd(input.processingFeePercent.coerceAtLeast(0.0)))
        val procFlatFeeBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.processingFeeFlat.coerceAtLeast(0.0)))
        val processingFeeTotalBd = MoneyMath.roundPaiseBd(procPercentFeeBd.add(procFlatFeeBd))
        val foreclosureFeeBd = MoneyMath.percentageOf(principalBd, MoneyMath.bd(input.foreclosureFeePercent.coerceAtLeast(0.0)))
        val legalChargesBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.legalAndValuationCharges.coerceAtLeast(0.0)))

        // GST applies to processing + foreclosure fees (and legal bank fees if taxed; standard Indian banks levy GST on processing & foreclosure fees)
        val gstBaseBd = processingFeeTotalBd.add(foreclosureFeeBd)
        val gstOnFeesBd = if (input.gstOnFeesEnabled && input.gstRatePercent > 0.0) {
            MoneyMath.percentageOf(gstBaseBd, MoneyMath.bd(input.gstRatePercent))
        } else {
            MoneyMath.ZERO
        }

        val totalSwitchingCostBd = MoneyMath.roundPaiseBd(
            processingFeeTotalBd.add(gstOnFeesBd).add(foreclosureFeeBd).add(legalChargesBd)
        )

        val netLifetimeSavingsBd = MoneyMath.roundPaiseBd(
            grossInterestSavingsBd.subtract(totalSwitchingCostBd)
        )

        val breakEvenMonth: Int? = if (monthlySavingsBd > BigDecimal.ZERO) {
            if (totalSwitchingCostBd <= BigDecimal.ZERO) {
                1
            } else {
                val raw = totalSwitchingCostBd.divide(monthlySavingsBd, 0, RoundingMode.CEILING).toInt()
                if (raw <= input.remainingMonths) raw else null
            }
        } else {
            null
        }

        // Optional Top-Up calculation
        var topUpEmiBd = MoneyMath.ZERO
        var topUpInterestBd = MoneyMath.ZERO
        if (input.includeTopUp && input.topUpAmount > 0.0) {
            val topUpPrincipalBd = MoneyMath.bd(input.topUpAmount)
            topUpEmiBd = MoneyMath.standardEmiBd(
                topUpPrincipalBd,
                MoneyMath.bd(input.topUpAnnualRatePercent),
                input.topUpTenureMonths
            )
            topUpInterestBd = MoneyMath.roundPaiseBd(
                topUpEmiBd.multiply(BigDecimal(input.topUpTenureMonths), MoneyMath.MC)
                    .subtract(topUpPrincipalBd)
                    .max(BigDecimal.ZERO)
            )
        }
        val blendedEmiBd = MoneyMath.roundPaiseBd(newEmiBd.add(topUpEmiBd))

        val (recommendation, headline, detail) = buildRecommendation(
            netSavings = netLifetimeSavingsBd.toDouble(),
            breakEvenMonth = breakEvenMonth,
            remainingMonths = input.remainingMonths,
            rateDrop = input.currentAnnualRatePercent - input.newAnnualRatePercent
        )

        return BalanceTransferAnalysisResult(
            isValid = true,
            currentEmi = currentEmiBd.toDouble(),
            newEmi = newEmiBd.toDouble(),
            monthlyEmiSavings = monthlySavingsBd.toDouble(),
            currentRemainingInterest = currentInterestBd.toDouble(),
            newTotalInterest = newInterestBd.toDouble(),
            processingFeeAmount = processingFeeTotalBd.toDouble(),
            gstOnFeeAmount = gstOnFeesBd.toDouble(),
            foreclosureFeeAmount = foreclosureFeeBd.toDouble(),
            legalChargesAmount = legalChargesBd.toDouble(),
            totalSwitchingCost = totalSwitchingCostBd.toDouble(),
            grossInterestSavings = grossInterestSavingsBd.toDouble(),
            netLifetimeSavings = netLifetimeSavingsBd.toDouble(),
            breakEvenMonth = breakEvenMonth,
            topUpEmi = topUpEmiBd.toDouble(),
            topUpTotalInterest = topUpInterestBd.toDouble(),
            blendedEmi = blendedEmiBd.toDouble(),
            recommendation = recommendation,
            recommendationHeadline = headline,
            recommendationDetail = detail
        )
    }

    private fun buildRecommendation(
        netSavings: Double,
        breakEvenMonth: Int?,
        remainingMonths: Int,
        rateDrop: Double
    ): Triple<TransferRecommendation, String, String> {
        return when {
            rateDrop <= 0.0 || netSavings <= 0.0 || breakEvenMonth == null -> Triple(
                TransferRecommendation.NOT_RECOMMENDED,
                "Stay With Current Lender",
                "Switching costs outweigh the rate benefit over your remaining tenure. Negotiate a conversion rate with your existing bank instead."
            )
            breakEvenMonth <= minOf(24, (remainingMonths / 3).coerceAtLeast(6)) && netSavings >= 50000.0 -> Triple(
                TransferRecommendation.STRONGLY_RECOMMENDED,
                "Strong Transfer Savings",
                "You recover all switching costs by month $breakEvenMonth and lock in substantial net interest savings."
            )
            else -> Triple(
                TransferRecommendation.WORTH_CONSIDERING,
                "Moderate Transfer Benefit",
                "Transfer breaks even in month $breakEvenMonth. Worth pursuing if you plan to keep the loan longer than $breakEvenMonth months or can negotiate lower processing fees."
            )
        }
    }
}

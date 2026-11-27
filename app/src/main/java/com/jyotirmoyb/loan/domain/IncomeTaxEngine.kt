package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.SurchargeBand
import com.jyotirmoyb.loan.data.remote.TaxDeductionRule
import com.jyotirmoyb.loan.data.remote.TaxRegimeRules
import com.jyotirmoyb.loan.data.remote.TaxSlabBand
import com.jyotirmoyb.loan.data.remote.TaxYearRules
import java.math.BigDecimal
import java.math.RoundingMode

enum class TaxpayerAgeGroup(val label: String) {
    GENERAL("Below 60"),
    SENIOR("Senior (60–79)"),
    SUPER_SENIOR("Super Senior (80+)")
}

data class IncomeTaxInput(
    val grossSalary: Double = 1500000.0,
    val otherIncome: Double = 0.0,
    val isSalaried: Boolean = true,
    val ageGroup: TaxpayerAgeGroup = TaxpayerAgeGroup.GENERAL,
    val parentsAreSenior: Boolean = false,
    val deductionAmounts: Map<String, Double> = emptyMap()
)

data class SlabLineItem(val rangeLabel: String, val ratePercent: Double, val taxableSlice: Double, val taxAmount: Double)

data class AppliedDeductionItem(
    val id: String,
    val label: String,
    val section: String,
    val enteredAmount: Double,
    val appliedAmount: Double,
    val capApplied: Double?
)

data class RegimeTaxCalculation(
    val regimeId: String,
    val displayName: String,
    val grossTotalIncome: Double,
    val standardDeduction: Double,
    val otherDeductionsTotal: Double,
    val appliedDeductions: List<AppliedDeductionItem>,
    val taxableIncome: Double,
    val slabBreakdown: List<SlabLineItem>,
    val slabTax: Double,
    val rebate87A: Double,
    val rebateMarginalRelief: Double,
    val taxAfterRebate: Double,
    val surchargeRatePercent: Double,
    val surchargeBeforeRelief: Double,
    val surchargeMarginalRelief: Double,
    val surchargePayable: Double,
    val cessPercent: Double,
    val cessAmount: Double,
    val totalTaxPayable: Double,
    val monthlyTdsEstimate: Double,
    val effectiveTaxRatePercent: Double
)

data class IncomeTaxComparisonResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val fyCode: String = "",
    val ayCode: String = "",
    val lawReference: String = "",
    val newRegime: RegimeTaxCalculation? = null,
    val oldRegime: RegimeTaxCalculation? = null,
    val recommendedRegimeId: String = "NEW",
    val recommendedRegimeName: String = "",
    val annualTaxSavings: Double = 0.0,
    val monthlyTaxSavings: Double = 0.0,
    val whyBullets: List<String> = emptyList(),
    val breakevenExtraOldDeductions: Double? = null
)

/**
 * Pure BigDecimal Indian Income Tax Engine.
 * Every slab, rebate limit, marginal relief rule, standard deduction, surcharge band,
 * deduction cap/applicability, and cess rate is driven exclusively by [TaxYearRules] from taxslabs.json.
 */
object IncomeTaxEngine {

    fun calculateComparison(input: IncomeTaxInput, fyRules: TaxYearRules): IncomeTaxComparisonResult {
        val errors = mutableListOf<String>()
        if (input.grossSalary < 0.0) errors.add("Gross salary cannot be negative.")
        if (input.otherIncome < 0.0) errors.add("Other income cannot be negative.")
        if (input.deductionAmounts.values.any { it < 0.0 }) errors.add("Deduction amounts cannot be negative.")
        if (errors.isNotEmpty()) {
            return IncomeTaxComparisonResult(isValid = false, validationErrors = errors)
        }

        val newCalc = calculateSingleRegime(
            input = input,
            regime = fyRules.newRegime,
            deductionRules = fyRules.deductions,
            cessPercent = fyRules.cessPercent
        )
        val oldCalc = calculateSingleRegime(
            input = input,
            regime = fyRules.oldRegime,
            deductionRules = fyRules.deductions,
            cessPercent = fyRules.cessPercent
        )

        val newTaxBd = MoneyMath.bd(newCalc.totalTaxPayable)
        val oldTaxBd = MoneyMath.bd(oldCalc.totalTaxPayable)
        val diffBd = MoneyMath.roundPaiseBd(newTaxBd.subtract(oldTaxBd).abs())
        val monthlyDiffBd = MoneyMath.roundPaiseBd(diffBd.divide(BigDecimal("12"), MoneyMath.MC))

        val (recId, recName) = when {
            newTaxBd < oldTaxBd -> "NEW" to newCalc.displayName
            oldTaxBd < newTaxBd -> "OLD" to oldCalc.displayName
            else -> "TIE" to "Either Regime (Equal Tax)"
        }

        val breakeven = if (newTaxBd < oldTaxBd && oldCalc.taxableIncome > 0.0) {
            findExtraOldDeductionsForBreakeven(input, fyRules, newCalc.totalTaxPayable)
        } else {
            null
        }

        val whyBullets = buildWhyBullets(
            input = input,
            fyRules = fyRules,
            newCalc = newCalc,
            oldCalc = oldCalc,
            recId = recId,
            annualSavings = diffBd.toDouble(),
            breakevenExtraOld = breakeven
        )

        return IncomeTaxComparisonResult(
            isValid = true,
            fyCode = fyRules.fyCode,
            ayCode = fyRules.ayCode,
            lawReference = fyRules.lawReference,
            newRegime = newCalc,
            oldRegime = oldCalc,
            recommendedRegimeId = recId,
            recommendedRegimeName = recName,
            annualTaxSavings = diffBd.toDouble(),
            monthlyTaxSavings = monthlyDiffBd.toDouble(),
            whyBullets = whyBullets,
            breakevenExtraOldDeductions = breakeven
        )
    }

    fun calculateSingleRegime(
        input: IncomeTaxInput,
        regime: TaxRegimeRules,
        deductionRules: List<TaxDeductionRule>,
        cessPercent: Double
    ): RegimeTaxCalculation {
        val grossSalaryBd = MoneyMath.bd(input.grossSalary.coerceAtLeast(0.0))
        val otherIncomeBd = MoneyMath.bd(input.otherIncome.coerceAtLeast(0.0))
        val grossTotalBd = MoneyMath.roundPaiseBd(grossSalaryBd.add(otherIncomeBd))

        val stdDedBd = if (input.isSalaried && grossSalaryBd > BigDecimal.ZERO) {
            MoneyMath.bd(regime.standardDeductionSalaried).min(grossSalaryBd)
        } else {
            BigDecimal.ZERO
        }

        val isNewRegime = regime.regimeId.equals("NEW", ignoreCase = true)
        val isTaxpayerSenior = input.ageGroup != TaxpayerAgeGroup.GENERAL

        val appliedDeductions = mutableListOf<AppliedDeductionItem>()
        var otherDeductionsBd = BigDecimal.ZERO

        for (rule in deductionRules) {
            val allowed = if (isNewRegime) rule.allowedInNew else rule.allowedInOld
            if (!allowed) continue
            val entered = (input.deductionAmounts[rule.id] ?: 0.0).coerceAtLeast(0.0)
            val useSeniorCap = if (rule.id == "sec80d_parents") input.parentsAreSenior else isTaxpayerSenior
            val cap = if (useSeniorCap && rule.capSenior != null) rule.capSenior else rule.capGeneral
            val applied = if (cap != null) minOf(entered, cap) else entered
            if (entered > 0.0 || applied > 0.0) {
                appliedDeductions.add(
                    AppliedDeductionItem(
                        id = rule.id,
                        label = rule.label,
                        section = rule.section,
                        enteredAmount = MoneyMath.roundPaise(entered),
                        appliedAmount = MoneyMath.roundPaise(applied),
                        capApplied = cap
                    )
                )
            }
            otherDeductionsBd = otherDeductionsBd.add(MoneyMath.bd(applied))
        }
        otherDeductionsBd = MoneyMath.roundPaiseBd(otherDeductionsBd)

        val taxableIncomeBd = MoneyMath.roundPaiseBd(
            grossTotalBd.subtract(stdDedBd).subtract(otherDeductionsBd).max(BigDecimal.ZERO)
        )

        val activeSlabs = selectSlabsForAge(regime, input.ageGroup)
        val (slabLines, slabTaxBd) = computeProgressiveSlabs(taxableIncomeBd, activeSlabs)

        // Section 87A Rebate & Marginal Relief
        val (rebateBd, rebateReliefBd, taxAfterRebateBd) = applyRebateAndMarginalRelief(
            taxableIncome = taxableIncomeBd,
            slabTax = slabTaxBd,
            regime = regime
        )

        // Surcharge & Surcharge Marginal Relief
        val (surchargeRate, surchargeRawBd, surchargeReliefBd, surchargeNetBd) = computeSurchargeWithRelief(
            taxableIncome = taxableIncomeBd,
            taxAfterRebate = taxAfterRebateBd,
            regime = regime,
            slabs = activeSlabs
        )

        val taxPlusSurchargeBd = MoneyMath.roundPaiseBd(taxAfterRebateBd.add(surchargeNetBd))
        val cessBd = MoneyMath.percentageOf(taxPlusSurchargeBd, MoneyMath.bd(cessPercent))
        val totalTaxBd = MoneyMath.roundPaiseBd(taxPlusSurchargeBd.add(cessBd))
        val monthlyTdsBd = MoneyMath.roundPaiseBd(totalTaxBd.divide(BigDecimal("12"), MoneyMath.MC))

        val effectiveRate = if (grossTotalBd > BigDecimal.ZERO) {
            totalTaxBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(grossTotalBd, 2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }

        return RegimeTaxCalculation(
            regimeId = regime.regimeId,
            displayName = regime.displayName,
            grossTotalIncome = grossTotalBd.toDouble(),
            standardDeduction = MoneyMath.roundPaiseBd(stdDedBd).toDouble(),
            otherDeductionsTotal = otherDeductionsBd.toDouble(),
            appliedDeductions = appliedDeductions,
            taxableIncome = taxableIncomeBd.toDouble(),
            slabBreakdown = slabLines,
            slabTax = slabTaxBd.toDouble(),
            rebate87A = rebateBd.toDouble(),
            rebateMarginalRelief = rebateReliefBd.toDouble(),
            taxAfterRebate = taxAfterRebateBd.toDouble(),
            surchargeRatePercent = surchargeRate,
            surchargeBeforeRelief = surchargeRawBd.toDouble(),
            surchargeMarginalRelief = surchargeReliefBd.toDouble(),
            surchargePayable = surchargeNetBd.toDouble(),
            cessPercent = cessPercent,
            cessAmount = cessBd.toDouble(),
            totalTaxPayable = totalTaxBd.toDouble(),
            monthlyTdsEstimate = monthlyTdsBd.toDouble(),
            effectiveTaxRatePercent = effectiveRate
        )
    }

    private fun selectSlabsForAge(regime: TaxRegimeRules, ageGroup: TaxpayerAgeGroup): List<TaxSlabBand> = when (ageGroup) {
        TaxpayerAgeGroup.SUPER_SENIOR -> regime.slabsSuperSenior ?: regime.slabsSenior ?: regime.slabsGeneral
        TaxpayerAgeGroup.SENIOR -> regime.slabsSenior ?: regime.slabsGeneral
        TaxpayerAgeGroup.GENERAL -> regime.slabsGeneral
    }

    internal fun computeProgressiveSlabs(taxableIncome: BigDecimal, slabs: List<TaxSlabBand>): Pair<List<SlabLineItem>, BigDecimal> {
        var prevUpper = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO
        val lines = mutableListOf<SlabLineItem>()

        for (band in slabs) {
            val upper = band.upTo?.let { MoneyMath.bd(it) }
            val slice = if (taxableIncome <= prevUpper) {
                BigDecimal.ZERO
            } else if (upper == null) {
                taxableIncome.subtract(prevUpper).max(BigDecimal.ZERO)
            } else {
                taxableIncome.min(upper).subtract(prevUpper).max(BigDecimal.ZERO)
            }

            val rateBd = MoneyMath.bd(band.ratePercent)
            val taxForBand = if (slice > BigDecimal.ZERO && rateBd > BigDecimal.ZERO) {
                MoneyMath.roundPaiseBd(
                    slice.multiply(rateBd, MoneyMath.MC).divide(MoneyMath.HUNDRED, MoneyMath.MC)
                )
            } else {
                BigDecimal.ZERO
            }

            val rangeLabel = if (upper == null) {
                "Above ${CurrencyFormatter.formatCompact(prevUpper.toDouble(), CurrencyCode.INR)}"
            } else {
                "${CurrencyFormatter.formatCompact(
                    prevUpper.toDouble(),
                    CurrencyCode.INR
                )} – ${CurrencyFormatter.formatCompact(upper.toDouble(), CurrencyCode.INR)}"
            }

            lines.add(
                SlabLineItem(
                    rangeLabel = rangeLabel,
                    ratePercent = band.ratePercent,
                    taxableSlice = MoneyMath.roundPaiseBd(slice).toDouble(),
                    taxAmount = taxForBand.toDouble()
                )
            )
            totalTax = totalTax.add(taxForBand)
            if (upper != null) {
                prevUpper = upper
            }
        }
        return lines to MoneyMath.roundPaiseBd(totalTax)
    }

    private data class RebateTriple(val rebate87A: BigDecimal, val marginalRelief: BigDecimal, val taxAfterRebate: BigDecimal)

    private fun applyRebateAndMarginalRelief(taxableIncome: BigDecimal, slabTax: BigDecimal, regime: TaxRegimeRules): RebateTriple {
        val limitBd = MoneyMath.bd(regime.rebateIncomeLimit)
        val maxRebateBd = MoneyMath.bd(regime.maxRebateAmount)

        if (taxableIncome <= limitBd && limitBd > BigDecimal.ZERO) {
            val rebate = slabTax.min(maxRebateBd)
            val after = MoneyMath.roundPaiseBd(slabTax.subtract(rebate).max(BigDecimal.ZERO))
            return RebateTriple(MoneyMath.roundPaiseBd(rebate), BigDecimal.ZERO, after)
        }

        // Marginal relief immediately above the Section 87A rebate threshold (applicable under New Regime)
        if (regime.rebateMarginalRelief && limitBd > BigDecimal.ZERO && taxableIncome > limitBd) {
            val excessIncome = taxableIncome.subtract(limitBd)
            if (slabTax > excessIncome) {
                val relief = MoneyMath.roundPaiseBd(slabTax.subtract(excessIncome))
                val payable = MoneyMath.roundPaiseBd(excessIncome)
                return RebateTriple(BigDecimal.ZERO, relief, payable)
            }
        }

        return RebateTriple(BigDecimal.ZERO, BigDecimal.ZERO, slabTax)
    }

    private data class SurchargeQuad(
        val ratePercent: Double,
        val rawSurcharge: BigDecimal,
        val marginalRelief: BigDecimal,
        val netSurcharge: BigDecimal
    )

    private fun computeSurchargeWithRelief(
        taxableIncome: BigDecimal,
        taxAfterRebate: BigDecimal,
        regime: TaxRegimeRules,
        slabs: List<TaxSlabBand>
    ): SurchargeQuad {
        if (taxAfterRebate <= BigDecimal.ZERO || regime.surchargeBands.isEmpty()) {
            return SurchargeQuad(0.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        }
        val sortedBands: List<SurchargeBand> = regime.surchargeBands.sortedBy { it.aboveIncome }
        val activeIndex = sortedBands.indexOfLast { taxableIncome > MoneyMath.bd(it.aboveIncome) }
        if (activeIndex < 0) {
            return SurchargeQuad(0.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val activeBand = sortedBands[activeIndex]
        val rateBd = MoneyMath.bd(activeBand.ratePercent)
        val rawSurcharge = MoneyMath.percentageOf(taxAfterRebate, rateBd)

        if (!regime.surchargeMarginalRelief) {
            return SurchargeQuad(activeBand.ratePercent, rawSurcharge, BigDecimal.ZERO, rawSurcharge)
        }

        // Statutory Surcharge Marginal Relief:
        // Total (Tax + Surcharge) cannot exceed (Tax + Surcharge at threshold T) + (Income - T)
        val thresholdBd = MoneyMath.bd(activeBand.aboveIncome)
        val (_, baseTaxAtT) = computeProgressiveSlabs(thresholdBd, slabs)
        val prevRatePercent = if (activeIndex > 0) sortedBands[activeIndex - 1].ratePercent else 0.0
        val surchargeAtT = if (prevRatePercent > 0.0) {
            MoneyMath.percentageOf(baseTaxAtT, MoneyMath.bd(prevRatePercent))
        } else {
            BigDecimal.ZERO
        }

        val taxPlusSurchargeAtT = baseTaxAtT.add(surchargeAtT)
        val excessIncome = taxableIncome.subtract(thresholdBd)
        val maxAllowedTotal = taxPlusSurchargeAtT.add(excessIncome)
        val actualTotal = taxAfterRebate.add(rawSurcharge)

        val relief = if (actualTotal > maxAllowedTotal) {
            MoneyMath.roundPaiseBd(actualTotal.subtract(maxAllowedTotal).min(rawSurcharge))
        } else {
            BigDecimal.ZERO
        }
        val netSurcharge = MoneyMath.roundPaiseBd(rawSurcharge.subtract(relief).max(BigDecimal.ZERO))

        return SurchargeQuad(activeBand.ratePercent, rawSurcharge, relief, netSurcharge)
    }

    private fun findExtraOldDeductionsForBreakeven(input: IncomeTaxInput, fyRules: TaxYearRules, targetNewTax: Double): Double? {
        // Binary search extra Old-Regime deduction amount needed for Old tax <= New tax
        val maxAdditional = (input.grossSalary + input.otherIncome).coerceAtMost(50_000_000.0)
        if (maxAdditional <= 0.0) return null

        val testMax = calculateSingleRegime(
            input = input.copy(
                deductionAmounts = input.deductionAmounts + ("hra" to ((input.deductionAmounts["hra"] ?: 0.0) + maxAdditional))
            ),
            regime = fyRules.oldRegime,
            deductionRules = fyRules.deductions,
            cessPercent = fyRules.cessPercent
        )
        if (testMax.totalTaxPayable > targetNewTax) return null

        var low = 0.0
        var high = maxAdditional
        repeat(40) {
            val mid = (low + high) / 2.0
            val calc = calculateSingleRegime(
                input = input.copy(
                    deductionAmounts = input.deductionAmounts + ("hra" to ((input.deductionAmounts["hra"] ?: 0.0) + mid))
                ),
                regime = fyRules.oldRegime,
                deductionRules = fyRules.deductions,
                cessPercent = fyRules.cessPercent
            )
            if (calc.totalTaxPayable <= targetNewTax) {
                high = mid
            } else {
                low = mid
            }
        }
        return MoneyMath.roundPaise(high)
    }

    private fun buildWhyBullets(
        input: IncomeTaxInput,
        fyRules: TaxYearRules,
        newCalc: RegimeTaxCalculation,
        oldCalc: RegimeTaxCalculation,
        recId: String,
        annualSavings: Double,
        breakevenExtraOld: Double?
    ): List<String> {
        val bullets = mutableListOf<String>()
        val fmt = { v: Double -> CurrencyFormatter.format(v, CurrencyCode.INR) }

        when (recId) {
            "NEW" -> bullets.add(
                "${newCalc.displayName} saves ${fmt(
                    annualSavings
                )} per year (${fmt(annualSavings / 12.0)}/mo) over Old Regime for ${fyRules.fyCode}."
            )
            "OLD" -> bullets.add(
                "${oldCalc.displayName} saves ${fmt(
                    annualSavings
                )} per year (${fmt(
                    annualSavings / 12.0
                )}/mo) thanks to ${fmt(oldCalc.otherDeductionsTotal)} in eligible Chapter VI-A / HRA deductions."
            )
            else -> bullets.add("Both regimes produce the exact same tax payable (${fmt(newCalc.totalTaxPayable)}) for ${fyRules.fyCode}.")
        }

        if (input.isSalaried && newCalc.standardDeduction > oldCalc.standardDeduction) {
            val extraStd = newCalc.standardDeduction - oldCalc.standardDeduction
            bullets.add(
                "Higher Standard Deduction: New Regime gives ${fmt(
                    newCalc.standardDeduction
                )} vs ${fmt(oldCalc.standardDeduction)} in Old (+${fmt(extraStd)})."
            )
        }

        if (newCalc.rebate87A > 0.0) {
            bullets.add(
                "Sec 87A Rebate zeroes out New Regime tax (taxable income ${fmt(
                    newCalc.taxableIncome
                )} ≤ ${fmt(fyRules.newRegime.rebateIncomeLimit)})."
            )
        } else if (newCalc.rebateMarginalRelief > 0.0) {
            bullets.add(
                "Marginal Relief of ${fmt(
                    newCalc.rebateMarginalRelief
                )} protects your New Regime tax from exceeding income above ${fmt(fyRules.newRegime.rebateIncomeLimit)}."
            )
        }

        if (oldCalc.rebate87A > 0.0) {
            bullets.add("Sec 87A Rebate of ${fmt(oldCalc.rebate87A)} applies under Old Regime.")
        }

        if (newCalc.surchargeRatePercent != oldCalc.surchargeRatePercent &&
            (newCalc.surchargePayable > 0.0 || oldCalc.surchargePayable > 0.0)
        ) {
            bullets.add(
                "Surcharge cap: New Regime caps surcharge at ${newCalc.surchargeRatePercent.toInt()}% vs ${oldCalc.surchargeRatePercent.toInt()}% in Old Regime."
            )
        }

        if (breakevenExtraOld != null && breakevenExtraOld > 0.0 && recId == "NEW") {
            bullets.add(
                "Break-even insight: You would need about ${fmt(
                    breakevenExtraOld
                )} in additional Old Regime deductions to match New Regime tax."
            )
        }

        return bullets
    }
}

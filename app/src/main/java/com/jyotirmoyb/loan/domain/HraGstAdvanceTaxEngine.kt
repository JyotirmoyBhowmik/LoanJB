package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.AdvanceTaxInstallmentRule
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import java.math.BigDecimal
import java.math.RoundingMode

// =============================================================================
// 1. HRA EXEMPTION ENGINE (SECTION 10(13A) / RULE 2A)
// =============================================================================

data class HraInput(
    val basicPlusDaAnnual: Double = 600000.0,
    val hraReceivedAnnual: Double = 240000.0,
    val actualRentPaidAnnual: Double = 240000.0,
    val isMetroCity: Boolean = true,
    val marginalTaxSlabPercent: Double = 30.0
)

data class HraRuleBreakdown(
    val title: String,
    val formulaHint: String,
    val amountAnnual: Double,
    val isWinningLeast: Boolean
)

data class HraResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val rule1ActualHra: Double = 0.0,
    val rule2RentMinusTenPercent: Double = 0.0,
    val rule3CitySalaryPercent: Double = 0.0,
    val appliedCityPercent: Double = 50.0,
    val exemptHraAnnual: Double = 0.0,
    val exemptHraMonthly: Double = 0.0,
    val taxableHraAnnual: Double = 0.0,
    val taxableHraMonthly: Double = 0.0,
    val estimatedTaxSavedAnnual: Double = 0.0,
    val requiresLandlordPan: Boolean = false,
    val ruleRows: List<HraRuleBreakdown> = emptyList()
)

object HraEngine {

    private const val LANDLORD_PAN_ANNUAL_RENT_LIMIT = 100000.0

    fun calculate(
        input: HraInput,
        metroPercent: Double,
        nonMetroPercent: Double,
        cessPercent: Double = 4.0
    ): HraResult {
        val errors = mutableListOf<String>()
        if (input.basicPlusDaAnnual <= 0.0) errors.add("Basic + DA salary must be greater than 0.")
        if (input.hraReceivedAnnual < 0.0) errors.add("HRA received cannot be negative.")
        if (input.actualRentPaidAnnual < 0.0) errors.add("Actual rent paid cannot be negative.")
        if (errors.isNotEmpty()) return HraResult(isValid = false, validationErrors = errors)

        val basicBd = MoneyMath.bd(input.basicPlusDaAnnual)
        val hraBd = MoneyMath.bd(input.hraReceivedAnnual)
        val rentBd = MoneyMath.bd(input.actualRentPaidAnnual)
        val cityPct = if (input.isMetroCity) metroPercent else nonMetroPercent

        val rule1Bd = MoneyMath.roundPaiseBd(hraBd)
        val tenPercentBasic = MoneyMath.percentageOf(basicBd, BigDecimal("10"))
        val rule2Bd = MoneyMath.roundPaiseBd(rentBd.subtract(tenPercentBasic).max(BigDecimal.ZERO))
        val rule3Bd = MoneyMath.percentageOf(basicBd, MoneyMath.bd(cityPct))

        val exemptBd = rule1Bd.min(rule2Bd).min(rule3Bd).max(BigDecimal.ZERO)
        val taxableBd = MoneyMath.roundPaiseBd(hraBd.subtract(exemptBd).max(BigDecimal.ZERO))

        val baseTaxSaved = MoneyMath.percentageOf(exemptBd, MoneyMath.bd(input.marginalTaxSlabPercent.coerceIn(0.0, 45.0)))
        val cessOnSaved = MoneyMath.percentageOf(baseTaxSaved, MoneyMath.bd(cessPercent))
        val totalTaxSaved = MoneyMath.roundPaiseBd(baseTaxSaved.add(cessOnSaved))

        val win1 = exemptBd == rule1Bd
        val win2 = !win1 && exemptBd == rule2Bd
        val win3 = !win1 && !win2 && exemptBd == rule3Bd

        val rows = listOf(
            HraRuleBreakdown(
                title = "1. Actual HRA Received",
                formulaHint = "Annual HRA component from employer",
                amountAnnual = rule1Bd.toDouble(),
                isWinningLeast = win1
            ),
            HraRuleBreakdown(
                title = "2. Rent Paid minus 10% of Basic + DA",
                formulaHint = "Max(0, Rent − 10% of Basic+DA)",
                amountAnnual = rule2Bd.toDouble(),
                isWinningLeast = win2
            ),
            HraRuleBreakdown(
                title = "3. ${cityPct.toInt()}% of Basic + DA (${if (input.isMetroCity) "Metro" else "Non-Metro"})",
                formulaHint = "Delhi, Mumbai, Kolkata, Chennai = ${metroPercent.toInt()}%; others = ${nonMetroPercent.toInt()}%",
                amountAnnual = rule3Bd.toDouble(),
                isWinningLeast = win3
            )
        )

        return HraResult(
            isValid = true,
            rule1ActualHra = rule1Bd.toDouble(),
            rule2RentMinusTenPercent = rule2Bd.toDouble(),
            rule3CitySalaryPercent = rule3Bd.toDouble(),
            appliedCityPercent = cityPct,
            exemptHraAnnual = exemptBd.toDouble(),
            exemptHraMonthly = MoneyMath.roundPaiseBd(exemptBd.divide(BigDecimal("12"), MoneyMath.MC)).toDouble(),
            taxableHraAnnual = taxableBd.toDouble(),
            taxableHraMonthly = MoneyMath.roundPaiseBd(taxableBd.divide(BigDecimal("12"), MoneyMath.MC)).toDouble(),
            estimatedTaxSavedAnnual = totalTaxSaved.toDouble(),
            requiresLandlordPan = input.actualRentPaidAnnual > LANDLORD_PAN_ANNUAL_RENT_LIMIT,
            ruleRows = rows
        )
    }
}

// =============================================================================
// 2. GST CALCULATOR ENGINE (ADD / REVERSE + CGST/SGST/IGST SPLIT)
// =============================================================================

enum class GstCalculationMode(val label: String) {
    ADD_GST("Add GST (Exclusive)"),
    REMOVE_GST("Remove GST (Inclusive)")
}

enum class GstSupplyType(val label: String) {
    INTRA_STATE("Intra-State (CGST + SGST)"),
    INTER_STATE("Inter-State (IGST)")
}

data class GstInput(
    val amount: Double = 10000.0,
    val gstRatePercent: Double = 18.0,
    val mode: GstCalculationMode = GstCalculationMode.ADD_GST,
    val supplyType: GstSupplyType = GstSupplyType.INTRA_STATE
)

data class GstResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val baseAmount: Double = 0.0,
    val gstRatePercent: Double = 0.0,
    val totalGstAmount: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val grossAmount: Double = 0.0
)

object GstEngine {

    fun calculate(input: GstInput): GstResult {
        val errors = mutableListOf<String>()
        if (input.amount <= 0.0) errors.add("Amount must be greater than 0.")
        if (input.gstRatePercent < 0.0 || input.gstRatePercent > 100.0) {
            errors.add("GST rate must be between 0% and 100%.")
        }
        if (errors.isNotEmpty()) return GstResult(isValid = false, validationErrors = errors)

        val amountBd = MoneyMath.bd(input.amount)
        val rateBd = MoneyMath.bd(input.gstRatePercent)

        val baseBd: BigDecimal
        val totalGstBd: BigDecimal
        val grossBd: BigDecimal

        when (input.mode) {
            GstCalculationMode.ADD_GST -> {
                baseBd = MoneyMath.roundPaiseBd(amountBd)
                totalGstBd = MoneyMath.percentageOf(baseBd, rateBd)
                grossBd = MoneyMath.roundPaiseBd(baseBd.add(totalGstBd))
            }
            GstCalculationMode.REMOVE_GST -> {
                grossBd = MoneyMath.roundPaiseBd(amountBd)
                val divisor = MoneyMath.ONE.add(rateBd.divide(MoneyMath.HUNDRED, MoneyMath.MC), MoneyMath.MC)
                baseBd = grossBd.divide(divisor, 2, RoundingMode.HALF_UP)
                totalGstBd = MoneyMath.roundPaiseBd(grossBd.subtract(baseBd).max(BigDecimal.ZERO))
            }
        }

        val cgstBd: BigDecimal
        val sgstBd: BigDecimal
        val igstBd: BigDecimal

        when (input.supplyType) {
            GstSupplyType.INTRA_STATE -> {
                cgstBd = totalGstBd.divide(BigDecimal("2"), 2, RoundingMode.HALF_UP)
                sgstBd = MoneyMath.roundPaiseBd(totalGstBd.subtract(cgstBd))
                igstBd = BigDecimal.ZERO
            }
            GstSupplyType.INTER_STATE -> {
                cgstBd = BigDecimal.ZERO
                sgstBd = BigDecimal.ZERO
                igstBd = totalGstBd
            }
        }

        return GstResult(
            isValid = true,
            baseAmount = baseBd.toDouble(),
            gstRatePercent = input.gstRatePercent,
            totalGstAmount = totalGstBd.toDouble(),
            cgstAmount = cgstBd.toDouble(),
            sgstAmount = sgstBd.toDouble(),
            igstAmount = igstBd.toDouble(),
            grossAmount = grossBd.toDouble()
        )
    }
}

// =============================================================================
// 3. ADVANCE TAX SCHEDULE & TDS QUICK HELPER ENGINE
// =============================================================================

data class AdvanceTaxInput(
    val fyCode: String = "FY 2026-27",
    val estimatedAnnualTax: Double = 120000.0,
    val tdsTcsDeducted: Double = 20000.0,
    val isPresumptive44AdAda: Boolean = false,
    val isSeniorWithoutBusiness: Boolean = false
)

data class AdvanceTaxInstallmentRow(
    val dueDateFormatted: String,
    val label: String,
    val cumulativePercent: Double,
    val cumulativeAmount: Double,
    val installmentAmount: Double
)

data class AdvanceTaxResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val netAdvanceTaxPayable: Double = 0.0,
    val thresholdApplied: Double = 10000.0,
    val isExempt: Boolean = false,
    val exemptionReason: String = "",
    val installments: List<AdvanceTaxInstallmentRow> = emptyList()
)

data class FdTdsQuickResult(
    val annualInterest: Double,
    val threshold: Double,
    val appliedRatePercent: Double,
    val isTdsApplicable: Boolean,
    val estimatedTds: Double,
    val netInterestAfterTds: Double,
    val guidanceNote: String
)

data class SalaryTdsQuickResult(
    val remainingTaxPayable: Double,
    val remainingMonths: Int,
    val estimatedMonthlyTds: Double,
    val guidanceNote: String
)

object AdvanceTaxAndTdsEngine {

    fun calculateAdvanceTax(
        input: AdvanceTaxInput,
        threshold: Double,
        scheduleRules: List<AdvanceTaxInstallmentRule>
    ): AdvanceTaxResult {
        val errors = mutableListOf<String>()
        if (input.estimatedAnnualTax < 0.0) errors.add("Estimated annual tax cannot be negative.")
        if (input.tdsTcsDeducted < 0.0) errors.add("TDS / TCS deducted cannot be negative.")
        if (errors.isNotEmpty()) return AdvanceTaxResult(isValid = false, validationErrors = errors)

        val netTaxBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(input.estimatedAnnualTax).subtract(MoneyMath.bd(input.tdsTcsDeducted)).max(BigDecimal.ZERO)
        )

        if (input.isSeniorWithoutBusiness) {
            return AdvanceTaxResult(
                isValid = true,
                netAdvanceTaxPayable = netTaxBd.toDouble(),
                thresholdApplied = threshold,
                isExempt = true,
                exemptionReason = "Resident senior citizens (60+) with no business or profession income are exempt from Advance Tax under Section 207."
            )
        }

        if (netTaxBd < MoneyMath.bd(threshold)) {
            return AdvanceTaxResult(
                isValid = true,
                netAdvanceTaxPayable = netTaxBd.toDouble(),
                thresholdApplied = threshold,
                isExempt = true,
                exemptionReason = "Advance Tax is not required because net tax liability after TDS/TCS is below ${CurrencyFormatter.format(threshold, CurrencyCode.INR)} (Sec 208)."
            )
        }

        val startYear = extractFyStartYear(input.fyCode)
        val endYear = startYear + 1

        if (input.isPresumptive44AdAda) {
            return AdvanceTaxResult(
                isValid = true,
                netAdvanceTaxPayable = netTaxBd.toDouble(),
                thresholdApplied = threshold,
                isExempt = false,
                installments = listOf(
                    AdvanceTaxInstallmentRow(
                        dueDateFormatted = "15 Mar $endYear",
                        label = "Single Presumptive Instalment (Sec 44AD / 44ADA)",
                        cumulativePercent = 100.0,
                        cumulativeAmount = netTaxBd.toDouble(),
                        installmentAmount = netTaxBd.toDouble()
                    )
                )
            )
        }

        val effectiveRules = scheduleRules.ifEmpty {
            listOf(
                AdvanceTaxInstallmentRule("15 Jun", 6, 15, 15.0, "1st Instalment"),
                AdvanceTaxInstallmentRule("15 Sep", 9, 15, 45.0, "2nd Instalment"),
                AdvanceTaxInstallmentRule("15 Dec", 12, 15, 75.0, "3rd Instalment"),
                AdvanceTaxInstallmentRule("15 Mar", 3, 15, 100.0, "4th Instalment")
            )
        }

        val rows = mutableListOf<AdvanceTaxInstallmentRow>()
        var prevCumulativeBd = BigDecimal.ZERO

        for ((index, rule) in effectiveRules.withIndex()) {
            val isLast = index == effectiveRules.lastIndex
            val cumBd = if (isLast) {
                netTaxBd // Final instalment absorbs rounding cleanly
            } else {
                MoneyMath.percentageOf(netTaxBd, MoneyMath.bd(rule.cumulativePercent))
            }
            val instBd = MoneyMath.roundPaiseBd(cumBd.subtract(prevCumulativeBd).max(BigDecimal.ZERO))
            val yearForDate = if (rule.calendarMonth >= 4) startYear else endYear
            val dateText = MoneyMath.formatDateDdMmmYyyy(rule.calendarDay, rule.calendarMonth, yearForDate)
            rows.add(
                AdvanceTaxInstallmentRow(
                    dueDateFormatted = dateText,
                    label = rule.label,
                    cumulativePercent = rule.cumulativePercent,
                    cumulativeAmount = cumBd.toDouble(),
                    installmentAmount = instBd.toDouble()
                )
            )
            prevCumulativeBd = cumBd
        }

        return AdvanceTaxResult(
            isValid = true,
            netAdvanceTaxPayable = netTaxBd.toDouble(),
            thresholdApplied = threshold,
            isExempt = false,
            installments = rows
        )
    }

    fun calculateFdTds(
        annualInterest: Double,
        isSeniorCitizen: Boolean,
        hasPan: Boolean,
        catalog: TaxRulesCatalog
    ): FdTdsQuickResult {
        val safeInterest = annualInterest.coerceAtLeast(0.0)
        val threshold = if (isSeniorCitizen) catalog.fdTdsSeniorThreshold else catalog.fdTdsGeneralThreshold
        val rate = if (hasPan) catalog.tdsRateWithPan else catalog.tdsRateWithoutPan
        val applicable = safeInterest > threshold
        val tds = if (applicable) {
            MoneyMath.percentageOf(MoneyMath.bd(safeInterest), MoneyMath.bd(rate)).toDouble()
        } else 0.0
        val net = MoneyMath.roundPaise((safeInterest - tds).coerceAtLeast(0.0))

        val note = when {
            !applicable -> "Annual FD interest is within the ${CurrencyFormatter.format(threshold, CurrencyCode.INR)} Sec 194A threshold — no bank TDS applies."
            !hasPan -> "Without PAN, Section 206AA mandates higher ${rate.toInt()}% TDS on total interest."
            isSeniorCitizen -> "Bank deducts ${rate.toInt()}% TDS above ${CurrencyFormatter.format(threshold, CurrencyCode.INR)}. Submit Form 15H if total taxable income is nil."
            else -> "Bank deducts ${rate.toInt()}% TDS above ${CurrencyFormatter.format(threshold, CurrencyCode.INR)}. Submit Form 15G if total taxable income is below the basic exemption limit."
        }

        return FdTdsQuickResult(
            annualInterest = MoneyMath.roundPaise(safeInterest),
            threshold = threshold,
            appliedRatePercent = rate,
            isTdsApplicable = applicable,
            estimatedTds = tds,
            netInterestAfterTds = net,
            guidanceNote = note
        )
    }

    fun calculateSalaryTds(
        estimatedAnnualTax: Double,
        tdsAlreadyDeducted: Double,
        remainingMonthsInFy: Int
    ): SalaryTdsQuickResult {
        val safeMonths = remainingMonthsInFy.coerceIn(1, 12)
        val remainingTaxBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(estimatedAnnualTax.coerceAtLeast(0.0))
                .subtract(MoneyMath.bd(tdsAlreadyDeducted.coerceAtLeast(0.0)))
                .max(BigDecimal.ZERO)
        )
        val monthlyBd = MoneyMath.roundPaiseBd(
            remainingTaxBd.divide(BigDecimal.valueOf(safeMonths.toLong()), MoneyMath.MC)
        )
        val note = if (remainingTaxBd <= BigDecimal.ZERO) {
            "Your estimated annual tax is already covered by TDS deducted so far."
        } else {
            "Under Section 192, employers average remaining tax liability across the remaining $safeMonths month(s) of the Financial Year."
        }
        return SalaryTdsQuickResult(
            remainingTaxPayable = remainingTaxBd.toDouble(),
            remainingMonths = safeMonths,
            estimatedMonthlyTds = monthlyBd.toDouble(),
            guidanceNote = note
        )
    }

    private fun extractFyStartYear(fyCode: String): Int {
        val match = Regex("(\\d{4})").find(fyCode)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2026
    }
}

package com.jyotirmoyb.loan.domain

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Canonical pure Kotlin loan calculation engine.
 * Uses BigDecimal (via [MoneyMath]) internally for exact paise-level accounting and guarantees
 * that the final instalment absorbs rounding residuals so closing balances end at exactly 0.00.
 *
 * Specialty/commercial/gold calculators live in [LoanSpecialtyCalculators] and
 * fixed-rate domain helpers in [FixedRateLoanCalculator].
 */
object LoanCalculationEngine {

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun roundPaisa(value: Double): Double = MoneyMath.roundPaise(value)

    fun roundCurrency(value: Double): Double = MoneyMath.roundPaise(value)

    fun standardEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): Double = MoneyMath.standardEmiBd(
        MoneyMath.bd(principal),
        MoneyMath.bd(annualRatePercent),
        tenureMonths
    ).toDouble()

    fun calculateEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): Double =
        standardEmi(principal, annualRatePercent, tenureMonths)

    fun buildSensitivityTable(principal: Double, baseRate: Double, tenureMonths: Int): List<RateSensitivityRow> =
        LoanInsightsCalculator.buildSensitivityTable(principal, baseRate, tenureMonths)

    fun calculateFlatVsReducing(principal: Double, quotedAnnualRate: Double, tenureMonths: Int): FlatVsReducingComparison =
        LoanInsightsCalculator.calculateFlatVsReducing(principal, quotedAnnualRate, tenureMonths)

    fun calculateBalanceTransfer(bt: BalanceTransferInput): BalanceTransferResult = LoanInsightsCalculator.calculateBalanceTransfer(bt)

    fun calculateTopUp(tu: TopUpInput): TopUpResult = LoanInsightsCalculator.calculateTopUp(tu)

    fun validateFixedRateRequest(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): List<String> = FixedRateLoanCalculator.validateFixedRateRequest(request, category)

    fun calculateFixedRateLoan(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): FixedRateLoanResult = FixedRateLoanCalculator.calculateFixedRateLoan(request, category)

    fun calculate(input: CalculationInput, category: LoanCategory? = null): CalculationResult {
        val flags = input.ruleFlags.toSet()
        val ltvCap = resolveLtvCap(input, category)
        val errors = validateInput(input, category, flags, ltvCap)

        val ltvPercent = if (input.assetValue > 0.0) {
            roundPaisa((input.principal / input.assetValue) * 100.0)
        } else {
            0.0
        }
        val downPayment = if (input.assetValue > input.principal) {
            roundPaisa(input.assetValue - input.principal)
        } else {
            0.0
        }
        val isLtvExceeded = (category?.secured == true || flags.contains("ASSET_MARGIN_SHARE")) &&
            input.assetValue > 0.0 &&
            ltvPercent > ltvCap + 0.01

        if (errors.isNotEmpty()) {
            return CalculationResult(
                isValid = false,
                validationErrors = errors,
                ltvPercent = ltvPercent,
                ltvCapPercent = ltvCap,
                isLtvExceeded = isLtvExceeded,
                downPayment = downPayment
            )
        }

        return when {
            flags.contains("BIZ_BILL_DISCOUNT") ->
                LoanSpecialtyCalculators.calculateBillDiscounting(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("BIZ_LC_TR") ->
                LoanSpecialtyCalculators.calculateLcTrustReceipt(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("FAC_FLEXI_LOC") || flags.contains("FAC_OVERDRAFT") || flags.contains("BIZ_CASH_CREDIT") ->
                LoanSpecialtyCalculators.calculateFlexiOrCashCredit(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("ASSET_GOLD_INTEREST_ONLY") ->
                LoanSpecialtyCalculators.calculateGoldInterestOnly(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("ASSET_GOLD_BULLET") ->
                LoanSpecialtyCalculators.calculateGoldBullet(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("RATE_FLAT") ->
                LoanSpecialtyCalculators.calculateFlatRateLoan(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            else -> calculateAmortizingLoan(input, flags, ltvCap, ltvPercent, downPayment, isLtvExceeded)
        }
    }

    private fun resolveLtvCap(input: CalculationInput, category: LoanCategory?): Double {
        if (category == null) return 80.0
        var cap = category.ltvMaxPercent
        for (group in category.variantGroups) {
            val selectedId = input.selectedVariantsByGroup[group.id]
            val variant = group.variants.firstOrNull { it.id == selectedId || input.ruleFlags.contains(it.ruleFlag) }
            if (variant?.ltvCapOverride != null) {
                cap = variant.ltvCapOverride
            }
        }
        return cap
    }

    fun validateInput(
        input: CalculationInput,
        category: LoanCategory?,
        flags: Set<String> = input.ruleFlags.toSet(),
        ltvCap: Double = resolveLtvCap(input, category)
    ): List<String> {
        val errors = mutableListOf<String>()
        if (flags.contains("BIZ_BILL_DISCOUNT")) {
            if (input.billInvoiceAmount <= 0.0) errors.add("Invoice amount must be greater than 0.")
            if (input.billAdvancePercent !in 10.0..100.0) errors.add("Advance percentage must be between 10% and 100%.")
            if (input.billDiscountRatePercent !in 0.0..40.0) errors.add("Discount rate must be between 0% and 40%.")
            if (input.billDays !in 1..365) errors.add("Bill tenure must be between 1 and 365 days.")
            return errors
        }

        if (input.principal <= 0.0) errors.add("Loan amount must be greater than 0.")
        if (input.annualRate < 0.0 || input.annualRate > 40.0) errors.add("Annual interest rate must be between 0% and 40%.")
        if (input.tenureMonths <= 0) {
            errors.add("Tenure must be at least 1 month.")
        } else if (category != null && !flags.contains("BIZ_LC_TR")) {
            if (input.tenureMonths < category.tenureMinMonths || input.tenureMonths > category.tenureMaxMonths) {
                errors.add(
                    "Tenure for ${category.name} must be between ${category.tenureMinMonths} and ${category.tenureMaxMonths} months."
                )
            }
        }
        val enforceLtv = (category?.secured == true || flags.contains("ASSET_MARGIN_SHARE")) &&
            !flags.contains("PURPOSE_BALANCE_TRANSFER") &&
            !flags.contains("PURPOSE_TOP_UP")
        if (enforceLtv && input.assetValue > 0.0) {
            val ltv = (input.principal / input.assetValue) * 100.0
            if (ltv > ltvCap + 0.01) {
                errors.add(
                    "LTV (${String.format(
                        Locale.US,
                        "%.1f",
                        ltv
                    )}%) exceeds ${String.format(Locale.US, "%.0f", ltvCap)}% cap. Increase asset value or reduce loan amount."
                )
            }
        }
        if (flags.contains("EMI_BALLOON") && (input.balloonPercent <= 0.0 || input.balloonPercent >= 80.0)) {
            errors.add("Balloon lump sum must be between 1% and 79% of principal.")
        }
        if (flags.contains("RATE_HYBRID") && (input.hybridFloatingRate < 0.0 || input.hybridFloatingRate > 45.0)) {
            errors.add("Post-fixed floating rate must be between 0% and 45%.")
        }
        return errors
    }

    internal fun calculateFeeBundle(
        basePrincipal: Double,
        input: CalculationInput,
        isFloating: Boolean,
        totalPrepaid: Double,
        forceGstOnFees: Boolean = false
    ): FeeBundle {
        val procFeeBd = MoneyMath.percentageOf(MoneyMath.bd(basePrincipal), MoneyMath.bd(input.processingFeePercent))
        val effectivePrepayFeePct = if (isFloating && input.prepaymentFeePercent == 0.0) 0.0 else input.prepaymentFeePercent
        val prepayFeeBd = MoneyMath.percentageOf(MoneyMath.bd(totalPrepaid), MoneyMath.bd(effectivePrepayFeePct))
        val gstApplicable = input.gstOnFeesEnabled || forceGstOnFees
        val gstOnFeeBd = if (gstApplicable) {
            MoneyMath.percentageOf(procFeeBd.add(prepayFeeBd), MoneyMath.bd(input.gstRatePercent))
        } else {
            MoneyMath.ZERO
        }
        val insBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.insurancePremium))
        val totalFeesBd = MoneyMath.roundPaiseBd(procFeeBd.add(gstOnFeeBd).add(insBd).add(prepayFeeBd))
        return FeeBundle(
            processingFee = procFeeBd.toDouble(),
            gstOnFee = gstOnFeeBd.toDouble(),
            insurance = insBd.toDouble(),
            prepaymentFee = prepayFeeBd.toDouble(),
            totalFees = totalFeesBd.toDouble()
        )
    }

    internal data class FeeBundle(
        val processingFee: Double,
        val gstOnFee: Double,
        val insurance: Double,
        val prepaymentFee: Double,
        val totalFees: Double
    )

    internal fun computeLoanFreeDate(input: CalculationInput, totalMonths: Int): String {
        val months = max(1, totalMonths)
        val totalM = (input.startMonth - 1) + (months - 1)
        val finalMonth = ((totalM % 12) + 12) % 12 + 1
        val finalYear = input.startYear + (totalM / 12)
        val day = if (input.proRataFirstMonthEnabled) input.emiDayOfMonth else input.startDay
        return MoneyMath.formatDateDdMmmYyyy(day.coerceIn(1, 28), finalMonth, finalYear)
    }

    private fun calculateAmortizingLoan(
        input: CalculationInput,
        flags: Set<String>,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val isEblr = flags.contains("RATE_EBLR")
        val isMclr = flags.contains("RATE_MCLR")
        val isFloating = flags.contains("RATE_FLOATING") || isEblr || isMclr
        val isHybrid = flags.contains("RATE_HYBRID")
        val isStepUp = flags.contains("EMI_STEP_UP") && !input.isStepDown
        val isStepDown = flags.contains("EMI_STEP_DOWN") || (flags.contains("EMI_STEP_UP") && input.isStepDown)
        val isOffset = flags.contains("EMI_OFFSET")
        val isTranche = flags.contains("DISB_TRANCHE_PRE_EMI") || flags.contains("EDU_TRANCHES")
        val isMoratorium =
            flags.contains("TERM_MORATORIUM") ||
                flags.contains("MOR_SIMPLE") ||
                flags.contains("MOR_COMPOUND") ||
                flags.contains("MORATORIUM")
        val isMoratoriumCompound =
            flags.contains("MOR_COMPOUND") ||
                ((flags.contains("TERM_MORATORIUM") || flags.contains("MORATORIUM")) && input.moratoriumCompound)
        val isBalloon = flags.contains("EMI_BALLOON")
        val isFlatCompare = flags.contains("BASIS_FLAT_COMPARE") || input.categoryId == "personal"
        val isFdLoan = flags.contains("ASSET_FD")
        val isMarginShare = flags.contains("ASSET_MARGIN_SHARE")
        val isCreditCardEmi = flags.contains("CC_EMI") || flags.contains("CC_NO_COST_EMI") || input.categoryId == "credit_card_emi"

        val effectiveInitialRate = input.annualRate

        val baselineSchedule = if (input.prepaymentEnabled && input.prepayments.any { it.amount > 0.0 }) {
            LoanScheduleSimulator.simulateSchedule(
                input.copy(prepaymentEnabled = false, prepayments = emptyList()),
                effectiveInitialRate, isFloating, isMclr, isHybrid, isStepUp, isStepDown,
                isOffset, isTranche, isMoratorium, isMoratoriumCompound, isBalloon, isCreditCardEmi
            ).rows
        } else {
            null
        }

        val nonOffsetSchedule = if (isOffset && input.offsetBalance > 0.0) {
            LoanScheduleSimulator.simulateSchedule(
                input.copy(offsetBalance = 0.0),
                effectiveInitialRate, isFloating, isMclr, isHybrid, isStepUp, isStepDown,
                false, isTranche, isMoratorium, isMoratoriumCompound, isBalloon, isCreditCardEmi
            ).rows
        } else {
            null
        }

        val nonPmaySchedule = if (input.pmaySubsidyEnabled && input.pmaySubsidyAmount > 0.0) {
            LoanScheduleSimulator.simulateSchedule(
                input.copy(pmaySubsidyEnabled = false),
                effectiveInitialRate, isFloating, isMclr, isHybrid, isStepUp, isStepDown,
                isOffset, isTranche, isMoratorium, isMoratoriumCompound, isBalloon, isCreditCardEmi
            ).rows
        } else {
            null
        }

        val sim = LoanScheduleSimulator.simulateSchedule(
            input, effectiveInitialRate, isFloating, isMclr, isHybrid, isStepUp, isStepDown,
            isOffset, isTranche, isMoratorium, isMoratoriumCompound, isBalloon, isCreditCardEmi
        )

        val rows = sim.rows
        val totalPrincipal = roundPaisa(rows.sumOf { it.principalPaid })
        val totalInterest = roundPaisa(rows.sumOf { it.interestPaid })
        val totalGstOnInterest = roundPaisa(rows.sumOf { it.gstOnInterest })
        val totalPayment = roundPaisa(totalPrincipal + totalInterest + totalGstOnInterest)

        val totalPrepaid = roundPaisa(rows.sumOf { it.extraPrepayment })
        val fees = calculateFeeBundle(input.principal, input, isFloating, totalPrepaid, forceGstOnFees = isCreditCardEmi)
        val effectiveTotalCost = roundPaisa(totalPayment + fees.totalFees)

        val emiRows = rows.filter { it.phaseLabel != "Pre-EMI" && it.phaseLabel != "Moratorium" }
        val firstEmi = emiRows.firstOrNull()?.emiPaid ?: rows.firstOrNull()?.emiPaid ?: 0.0
        val lastEmi = emiRows.lastOrNull()?.emiPaid ?: rows.lastOrNull()?.emiPaid ?: 0.0
        val headlineEmi = if (isStepUp || isStepDown || isTranche || isMoratorium) firstEmi else (rows.firstOrNull()?.emiPaid ?: 0.0)

        val sensitivityRows = if (isFloating ||
            isHybrid
        ) {
            buildSensitivityTable(input.principal, effectiveInitialRate, input.tenureMonths)
        } else {
            emptyList()
        }
        val offsetInterestSaved = if (nonOffsetSchedule !=
            null
        ) {
            roundPaisa(max(0.0, nonOffsetSchedule.sumOf { it.interestPaid } - totalInterest))
        } else {
            0.0
        }
        val pmayInterestSaved = if (nonPmaySchedule !=
            null
        ) {
            roundPaisa(max(0.0, nonPmaySchedule.sumOf { it.interestPaid } - totalInterest))
        } else {
            0.0
        }
        val flatVsReducing = if (isFlatCompare) calculateFlatVsReducing(input.principal, effectiveInitialRate, input.tenureMonths) else null

        val prepaymentImpact = if (baselineSchedule != null) {
            val baseInt = roundPaisa(baselineSchedule.sumOf { it.interestPaid })
            val interestSaved = roundPaisa(max(0.0, baseInt - totalInterest))
            PrepaymentImpact(
                baselineTotalInterest = baseInt,
                newTotalInterest = totalInterest,
                interestSaved = interestSaved,
                prepaymentFees = fees.prepaymentFee,
                netSavings = roundPaisa(interestSaved - fees.prepaymentFee),
                baselineMonths = baselineSchedule.size,
                newMonths = rows.size,
                monthsSaved = max(0, baselineSchedule.size - rows.size)
            )
        } else {
            null
        }

        val btResult = if (input.balanceTransfer.enabled ||
            flags.contains("PURPOSE_BALANCE_TRANSFER")
        ) {
            calculateBalanceTransfer(input.balanceTransfer)
        } else {
            null
        }
        val topUpResult = if (input.topUp.enabled || flags.contains("PURPOSE_TOP_UP")) calculateTopUp(input.topUp) else null

        val fdNetCost = if (isFdLoan) {
            val fdVal = if (input.assetValue > 0.0) input.assetValue else input.principal / 0.85
            val years = input.tenureMonths / 12.0
            val fdEarned = roundPaisa(fdVal * ((1.0 + input.fdRatePercent / 100.0 / 4.0).pow(4.0 * years) - 1.0))
            val netCost = roundPaisa(totalInterest - fdEarned)
            val effNetRate = if (input.principal > 0.0 && years > 0.0) roundPaisa(effectiveInitialRate - input.fdRatePercent) else 0.0
            FdNetCostResult(
                roundPaisa(fdVal),
                roundPaisa(input.principal),
                input.fdRatePercent,
                effectiveInitialRate,
                totalInterest,
                fdEarned,
                netCost,
                effNetRate
            )
        } else {
            null
        }

        val marginCallResult = if (isMarginShare) {
            val pledged = if (input.shareValue > 0.0) input.shareValue else input.assetValue
            val currentLtv = if (pledged > 0.0) roundPaisa((input.principal / pledged) * 100.0) else 0.0
            val maxAllowedLtv = min(ltvCap, 100.0 - input.minMarginPercent)
            val triggerValue = if (maxAllowedLtv > 0.0) roundPaisa(input.principal / (maxAllowedLtv / 100.0)) else 0.0
            val dropPct = if (pledged > 0.0) roundPaisa(((pledged - triggerValue) / pledged) * 100.0) else 0.0
            MarginCallResult(
                roundPaisa(pledged),
                roundPaisa(input.principal),
                currentLtv,
                maxAllowedLtv,
                input.minMarginPercent,
                triggerValue,
                dropPct,
                pledged <= triggerValue
            )
        } else {
            null
        }

        val isGoldAuctionRisk = (flags.contains("ASSET_GOLD_EMI") || input.categoryId == "gold") &&
            ltvPercent >= input.goldAuctionRiskLtvPercent - 0.01 &&
            input.assetValue > 0.0

        val goldSummary = if (flags.contains("ASSET_GOLD_EMI") || input.categoryId == "gold") {
            GoldLoanSummary(
                "Standard Monthly EMI",
                headlineEmi,
                lastEmi,
                totalInterest,
                totalPayment,
                ltvPercent,
                isGoldAuctionRisk,
                input.goldAuctionRiskLtvPercent
            )
        } else {
            null
        }

        val yearly = groupByYear(rows)
        val interestRatio = if (totalPrincipal > 0.0) roundPaisa((totalInterest / totalPrincipal) * 100.0) else 0.0

        return CalculationResult(
            isValid = true,
            monthlyEmi = headlineEmi,
            firstEmi = firstEmi,
            lastEmi = lastEmi,
            totalPrincipal = totalPrincipal,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            gstOnInterestAmount = totalGstOnInterest,
            brokenPeriodInterest = sim.brokenPeriodInterest,
            insuranceAmount = fees.insurance,
            prepaymentFeeAmount = fees.prepaymentFee,
            totalFees = fees.totalFees,
            effectiveTotalCost = effectiveTotalCost,
            interestToPrincipalPercent = interestRatio,
            loanFreeDateLabel = computeLoanFreeDate(input, rows.size),
            actualTenureMonths = rows.size,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = yearly,
            insights = VariantInsights(
                sensitivityRows = sensitivityRows,
                offsetInterestSaved = offsetInterestSaved,
                moratoriumExtraInterest = sim.moratoriumExtraInterest,
                flatVsReducing = flatVsReducing,
                prepaymentImpact = prepaymentImpact,
                balanceTransferResult = btResult,
                topUpResult = topUpResult,
                fdNetCost = fdNetCost,
                marginCallResult = marginCallResult,
                goldSummary = goldSummary,
                pmaySubsidyApplied = sim.pmaySubsidyApplied,
                pmayInterestSaved = pmayInterestSaved,
                taxBenefitSummary = LoanInsightsCalculator.calculateTaxBenefit(input, yearly)
            )
        )
    }

    internal fun dateMeta(startYear: Int, startMonth: Int, offsetOneBased: Int): Triple<String, Int, String> {
        val totalM = (startMonth - 1) + (offsetOneBased - 1)
        val mIdx = ((totalM % 12) + 12) % 12
        val year = startYear + (totalM / 12)
        val monthLabel = "${MONTH_NAMES[mIdx]} '${year % 100}"
        val yearGroupIdx = ((offsetOneBased - 1) / 12) + 1

        val groupStartOffset = (yearGroupIdx - 1) * 12
        val gsTotal = (startMonth - 1) + groupStartOffset
        val geTotal = gsTotal + 11
        val startName = MONTH_NAMES[((gsTotal % 12) + 12) % 12]
        val endName = MONTH_NAMES[((geTotal % 12) + 12) % 12]
        val rangeLabel = "YEAR $yearGroupIdx • $startName – $endName"
        return Triple(monthLabel, yearGroupIdx, rangeLabel)
    }

    internal fun groupByYear(rows: List<AmortizationRow>): List<YearlySummary> = rows.groupBy {
        it.yearGroupIndex
    }.entries.sortedBy { it.key }.map { (yIdx, groupRows) ->
        val p = roundPaisa(groupRows.sumOf { it.principalPaid })
        val i = roundPaisa(groupRows.sumOf { it.interestPaid })
        YearlySummary(
            yearIndex = yIdx,
            label = groupRows.first().yearRangeLabel,
            principalPaid = p,
            interestPaid = i,
            totalPaid = roundPaisa(p + i),
            endingBalance = groupRows.last().closingBalance,
            rows = groupRows
        )
    }
}

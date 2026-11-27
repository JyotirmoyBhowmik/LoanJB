package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

/**
 * Specialty, commercial, flat-rate, and gold loan calculators extracted from
 * [LoanCalculationEngine] to keep individual source files focused and under 500 lines.
 */
internal object LoanSpecialtyCalculators {

    fun calculateFlatRateLoan(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val months = max(1, input.tenureMonths)
        val pBd = MoneyMath.bd(input.principal)
        val comparison = LoanCalculationEngine.calculateFlatVsReducing(input.principal, input.annualRate, months)
        val totalIntBd = MoneyMath.bd(comparison.flatTotalInterest)
        val monthlyPrincipalBd = pBd.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
        val monthlyInterestBd = totalIntBd.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)

        val rows = mutableListOf<AmortizationRow>()
        var remaining = MoneyMath.roundPaiseBd(pBd)
        var intRemaining = MoneyMath.roundPaiseBd(totalIntBd)

        for (m in 1..months) {
            val isLast = m == months
            val prinPaid = if (isLast) remaining else monthlyPrincipalBd.min(remaining)
            val intPaid = if (isLast) intRemaining.max(MoneyMath.ZERO) else monthlyInterestBd
            val closing = if (isLast) MoneyMath.ZERO else MoneyMath.roundPaiseBd(remaining.subtract(prinPaid))
            val emi = MoneyMath.roundPaiseBd(prinPaid.add(intPaid))
            val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, m)
            rows.add(
                AmortizationRow(
                    monthNumber = m,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = remaining.toDouble(),
                    emiPaid = emi.toDouble(),
                    principalPaid = prinPaid.toDouble(),
                    interestPaid = intPaid.toDouble(),
                    extraPrepayment = 0.0,
                    closingBalance = closing.toDouble(),
                    activeRate = input.annualRate,
                    phaseLabel = "Flat"
                )
            )
            remaining = closing
            intRemaining = MoneyMath.roundPaiseBd(intRemaining.subtract(intPaid))
        }

        val totalPrincipal = LoanCalculationEngine.roundPaisa(rows.sumOf { it.principalPaid })
        val totalInterest = LoanCalculationEngine.roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = LoanCalculationEngine.roundPaisa(totalPrincipal + totalInterest)
        val fees = LoanCalculationEngine.calculateFeeBundle(input.principal, input, isFloating = false, totalPrepaid = 0.0)
        val interestRatio = if (totalPrincipal > 0.0) LoanCalculationEngine.roundPaisa((totalInterest / totalPrincipal) * 100.0) else 0.0
        val yearly = LoanCalculationEngine.groupByYear(rows)

        return CalculationResult(
            isValid = true,
            monthlyEmi = comparison.flatMonthlyEmi,
            firstEmi = rows.firstOrNull()?.emiPaid ?: 0.0,
            lastEmi = rows.lastOrNull()?.emiPaid ?: 0.0,
            totalPrincipal = totalPrincipal,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            insuranceAmount = fees.insurance,
            prepaymentFeeAmount = fees.prepaymentFee,
            totalFees = fees.totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(totalPayment + fees.totalFees),
            interestToPrincipalPercent = interestRatio,
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, rows.size),
            actualTenureMonths = rows.size,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = yearly,
            insights = VariantInsights(
                flatVsReducing = comparison,
                taxBenefitSummary = LoanInsightsCalculator.calculateTaxBenefit(input, yearly)
            )
        )
    }

    fun calculateFlexiOrCashCredit(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val rows = mutableListOf<AmortizationRow>()
        val months = max(1, input.tenureMonths)
        val rateBd = MoneyMath.bd(input.annualRate)
        val principalCapBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))
        var utilizedBd = MoneyMath.ZERO
        var peakBalanceBd = MoneyMath.ZERO
        val eventsByMonth = input.flexiEvents.groupBy { it.monthNumber }

        val effectiveEvents = if (input.flexiEvents.isEmpty()) {
            mapOf(1 to listOf(FlexiEvent(1, drawAmount = LoanCalculationEngine.roundPaisa(input.principal * 0.7))))
        } else {
            eventsByMonth
        }

        for (m in 1..months) {
            val evs = effectiveEvents[m].orEmpty()
            val drawBd = evs.fold(MoneyMath.ZERO) { acc, e -> acc.add(MoneyMath.bd(e.drawAmount)) }
            val repayBd = evs.fold(MoneyMath.ZERO) { acc, e -> acc.add(MoneyMath.bd(e.repayAmount)) }
            val openingBd = MoneyMath.roundPaiseBd(utilizedBd.add(drawBd)).min(principalCapBd)
            if (openingBd > peakBalanceBd) peakBalanceBd = openingBd
            val interestBd = MoneyMath.monthlyInterest(openingBd, rateBd)

            val principalRepaidBd = if (m == months) openingBd else repayBd.min(openingBd)
            val closingBd = MoneyMath.roundPaiseBd(openingBd.subtract(principalRepaidBd)).max(MoneyMath.ZERO)
            val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, m)
            rows.add(
                AmortizationRow(
                    monthNumber = m,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = openingBd.toDouble(),
                    emiPaid = MoneyMath.roundPaiseBd(principalRepaidBd.add(interestBd)).toDouble(),
                    principalPaid = principalRepaidBd.toDouble(),
                    interestPaid = interestBd.toDouble(),
                    extraPrepayment = 0.0,
                    closingBalance = closingBd.toDouble(),
                    activeRate = input.annualRate,
                    phaseLabel = if (m == months && openingBd > MoneyMath.ZERO) "Closure" else "Drawn"
                )
            )
            utilizedBd = closingBd
        }

        val totalPrincipal = LoanCalculationEngine.roundPaisa(rows.sumOf { it.principalPaid })
        val totalInterest = LoanCalculationEngine.roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = LoanCalculationEngine.roundPaisa(totalPrincipal + totalInterest)
        val fees = LoanCalculationEngine.calculateFeeBundle(input.principal, input, isFloating = false, totalPrepaid = 0.0)
        val avgMonthlyInterest = LoanCalculationEngine.roundPaisa(totalInterest / months)
        val interestRatio = if (totalPrincipal > 0.0) LoanCalculationEngine.roundPaisa((totalInterest / totalPrincipal) * 100.0) else 0.0

        return CalculationResult(
            isValid = true,
            monthlyEmi = avgMonthlyInterest,
            firstEmi = rows.firstOrNull()?.emiPaid ?: 0.0,
            lastEmi = rows.lastOrNull()?.emiPaid ?: 0.0,
            totalPrincipal = totalPrincipal,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            insuranceAmount = fees.insurance,
            totalFees = fees.totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(totalPayment + fees.totalFees),
            interestToPrincipalPercent = interestRatio,
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, rows.size),
            actualTenureMonths = rows.size,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = LoanCalculationEngine.groupByYear(rows),
            insights = VariantInsights(flexiPeakBalance = peakBalanceBd.toDouble())
        )
    }

    fun calculateBillDiscounting(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val advanceBd = MoneyMath.percentageOf(MoneyMath.bd(input.billInvoiceAmount), MoneyMath.bd(input.billAdvancePercent))
        val discountFeeBd = MoneyMath.dailyProRataInterest(advanceBd, MoneyMath.bd(input.billDiscountRatePercent), input.billDays)
        val fees = LoanCalculationEngine.calculateFeeBundle(advanceBd.toDouble(), input, isFloating = false, totalPrepaid = 0.0)
        val netDisbursed = LoanCalculationEngine.roundPaisa(max(0.01, advanceBd.toDouble() - discountFeeBd.toDouble() - fees.totalFees))
        val totalCost = LoanCalculationEngine.roundPaisa(discountFeeBd.toDouble() + fees.totalFees)
        val effectiveAnnualized = LoanCalculationEngine.roundPaisa((totalCost / netDisbursed) * (365.0 / input.billDays) * 100.0)

        val (_, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, 1)
        val row = AmortizationRow(
            monthNumber = 1,
            monthLabel = "${input.billDays}d Maturity",
            yearGroupIndex = yIdx,
            yearRangeLabel = yLabel,
            openingBalance = advanceBd.toDouble(),
            emiPaid = advanceBd.toDouble(),
            principalPaid = advanceBd.toDouble(),
            interestPaid = discountFeeBd.toDouble(),
            extraPrepayment = 0.0,
            closingBalance = 0.0,
            activeRate = effectiveAnnualized,
            phaseLabel = "Bill Settlement"
        )

        return CalculationResult(
            isValid = true,
            monthlyEmi = advanceBd.toDouble(),
            firstEmi = advanceBd.toDouble(),
            lastEmi = advanceBd.toDouble(),
            totalPrincipal = advanceBd.toDouble(),
            totalInterest = discountFeeBd.toDouble(),
            totalPayment = LoanCalculationEngine.roundPaisa(advanceBd.toDouble() + discountFeeBd.toDouble()),
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            totalFees = fees.totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(advanceBd.toDouble() + totalCost),
            interestToPrincipalPercent = if (advanceBd >
                MoneyMath.ZERO
            ) {
                LoanCalculationEngine.roundPaisa((discountFeeBd.toDouble() / advanceBd.toDouble()) * 100.0)
            } else {
                0.0
            },
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, 1),
            actualTenureMonths = 1,
            ltvPercent = input.billAdvancePercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = LoanCalculationEngine.roundPaisa(input.billInvoiceAmount - advanceBd.toDouble()),
            schedule = listOf(row),
            yearlySummaries = LoanCalculationEngine.groupByYear(listOf(row)),
            insights = VariantInsights(
                billDiscountResult = BillDiscountResult(
                    invoiceAmount = LoanCalculationEngine.roundPaisa(input.billInvoiceAmount),
                    advanceAmount = advanceBd.toDouble(),
                    discountFee = discountFeeBd.toDouble(),
                    netUpfrontDisbursed = netDisbursed,
                    days = input.billDays,
                    effectiveAnnualizedCostPercent = effectiveAnnualized
                )
            )
        )
    }

    fun calculateLcTrustReceipt(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val days = max(15, input.lcDays)
        val pBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))
        val interestBd = MoneyMath.dailyProRataInterest(pBd, MoneyMath.bd(input.annualRate), days)
        val commissionBd = MoneyMath.percentageOf(pBd, MoneyMath.bd(input.lcCommissionPercent))
        val fees = LoanCalculationEngine.calculateFeeBundle(input.principal, input, isFloating = false, totalPrepaid = 0.0)
        val totalFees = LoanCalculationEngine.roundPaisa(commissionBd.toDouble() + fees.totalFees)
        val maturityPay = LoanCalculationEngine.roundPaisa(pBd.toDouble() + interestBd.toDouble())

        val (_, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, 1)
        val row = AmortizationRow(
            monthNumber = 1,
            monthLabel = "${days}d TR Due",
            yearGroupIndex = yIdx,
            yearRangeLabel = yLabel,
            openingBalance = pBd.toDouble(),
            emiPaid = maturityPay,
            principalPaid = pBd.toDouble(),
            interestPaid = interestBd.toDouble(),
            extraPrepayment = 0.0,
            closingBalance = 0.0,
            activeRate = input.annualRate,
            phaseLabel = "LC / TR Maturity"
        )

        return CalculationResult(
            isValid = true,
            monthlyEmi = maturityPay,
            firstEmi = maturityPay,
            lastEmi = maturityPay,
            totalPrincipal = pBd.toDouble(),
            totalInterest = interestBd.toDouble(),
            totalPayment = maturityPay,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            insuranceAmount = fees.insurance,
            totalFees = totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(maturityPay + totalFees),
            interestToPrincipalPercent = if (pBd >
                MoneyMath.ZERO
            ) {
                LoanCalculationEngine.roundPaisa((interestBd.toDouble() / pBd.toDouble()) * 100.0)
            } else {
                0.0
            },
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, 1),
            actualTenureMonths = 1,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = listOf(row),
            yearlySummaries = LoanCalculationEngine.groupByYear(listOf(row)),
            insights = VariantInsights(lcCommissionFee = commissionBd.toDouble())
        )
    }

    fun calculateGoldInterestOnly(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val months = max(1, input.tenureMonths)
        val pBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))
        val monthlyInterestBd = MoneyMath.monthlyInterest(pBd, MoneyMath.bd(input.annualRate))
        val rows = mutableListOf<AmortizationRow>()
        for (m in 1..months) {
            val isLast = m == months
            val principalPaid = if (isLast) pBd.toDouble() else 0.0
            val closing = if (isLast) 0.0 else pBd.toDouble()
            val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, m)
            rows.add(
                AmortizationRow(
                    monthNumber = m,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = pBd.toDouble(),
                    emiPaid = LoanCalculationEngine.roundPaisa(monthlyInterestBd.toDouble() + principalPaid),
                    principalPaid = principalPaid,
                    interestPaid = monthlyInterestBd.toDouble(),
                    extraPrepayment = 0.0,
                    closingBalance = closing,
                    activeRate = input.annualRate,
                    phaseLabel = if (isLast) "Maturity" else "Interest-Only"
                )
            )
        }
        val totalInterest = LoanCalculationEngine.roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = LoanCalculationEngine.roundPaisa(pBd.toDouble() + totalInterest)
        val fees = LoanCalculationEngine.calculateFeeBundle(input.principal, input, isFloating = false, totalPrepaid = 0.0)
        val isAuctionRisk = input.assetValue > 0.0 && ltvPercent >= input.goldAuctionRiskLtvPercent - 0.01

        return CalculationResult(
            isValid = true,
            monthlyEmi = monthlyInterestBd.toDouble(),
            firstEmi = monthlyInterestBd.toDouble(),
            lastEmi = LoanCalculationEngine.roundPaisa(pBd.toDouble() + monthlyInterestBd.toDouble()),
            totalPrincipal = pBd.toDouble(),
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            insuranceAmount = fees.insurance,
            totalFees = fees.totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(totalPayment + fees.totalFees),
            interestToPrincipalPercent = if (pBd >
                MoneyMath.ZERO
            ) {
                LoanCalculationEngine.roundPaisa((totalInterest / pBd.toDouble()) * 100.0)
            } else {
                0.0
            },
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, months),
            actualTenureMonths = months,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = LoanCalculationEngine.groupByYear(rows),
            insights = VariantInsights(
                goldSummary = GoldLoanSummary(
                    modeLabel = "Monthly Interest-Only",
                    monthlyOutflow = monthlyInterestBd.toDouble(),
                    maturityLumpSum = LoanCalculationEngine.roundPaisa(pBd.toDouble() + monthlyInterestBd.toDouble()),
                    totalInterest = totalInterest,
                    totalPayable = totalPayment,
                    peakLtvPercent = ltvPercent,
                    isAuctionRiskAlert = isAuctionRisk,
                    auctionRiskThresholdLtv = input.goldAuctionRiskLtvPercent
                )
            )
        )
    }

    fun calculateGoldBullet(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val months = max(1, input.tenureMonths)
        val pBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))
        val rateBd = MoneyMath.bd(input.annualRate)
        val rows = mutableListOf<AmortizationRow>()
        var accruedBalanceBd = pBd
        var totalInterestBd = MoneyMath.ZERO

        for (m in 1..months) {
            val openingBd = accruedBalanceBd
            val interestBd = MoneyMath.monthlyInterest(openingBd, rateBd)
            totalInterestBd = MoneyMath.roundPaiseBd(totalInterestBd.add(interestBd))
            val isLast = m == months
            val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, m)
            if (isLast) {
                val bulletPay = MoneyMath.roundPaiseBd(pBd.add(totalInterestBd))
                rows.add(
                    AmortizationRow(
                        monthNumber = m,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = openingBd.toDouble(),
                        emiPaid = bulletPay.toDouble(),
                        principalPaid = pBd.toDouble(),
                        interestPaid = totalInterestBd.toDouble(),
                        extraPrepayment = 0.0,
                        closingBalance = 0.0,
                        activeRate = input.annualRate,
                        phaseLabel = "Bullet"
                    )
                )
                accruedBalanceBd = MoneyMath.ZERO
            } else {
                accruedBalanceBd = MoneyMath.roundPaiseBd(openingBd.add(interestBd))
                rows.add(
                    AmortizationRow(
                        monthNumber = m,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = openingBd.toDouble(),
                        emiPaid = 0.0,
                        principalPaid = 0.0,
                        interestPaid = 0.0,
                        extraPrepayment = 0.0,
                        closingBalance = accruedBalanceBd.toDouble(),
                        activeRate = input.annualRate,
                        phaseLabel = "Accruing"
                    )
                )
            }
        }

        val totalPayment = LoanCalculationEngine.roundPaisa(pBd.toDouble() + totalInterestBd.toDouble())
        val fees = LoanCalculationEngine.calculateFeeBundle(input.principal, input, isFloating = false, totalPrepaid = 0.0)
        val peakLtv = if (input.assetValue >
            0.0
        ) {
            LoanCalculationEngine.roundPaisa((totalPayment / input.assetValue) * 100.0)
        } else {
            ltvPercent
        }
        val isAuctionRisk = input.assetValue > 0.0 && peakLtv >= input.goldAuctionRiskLtvPercent - 0.01

        return CalculationResult(
            isValid = true,
            monthlyEmi = 0.0,
            firstEmi = 0.0,
            lastEmi = totalPayment,
            totalPrincipal = pBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            totalPayment = totalPayment,
            processingFeeAmount = fees.processingFee,
            gstOnFeeAmount = fees.gstOnFee,
            insuranceAmount = fees.insurance,
            totalFees = fees.totalFees,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(totalPayment + fees.totalFees),
            interestToPrincipalPercent = if (pBd >
                MoneyMath.ZERO
            ) {
                LoanCalculationEngine.roundPaisa((totalInterestBd.toDouble() / pBd.toDouble()) * 100.0)
            } else {
                0.0
            },
            loanFreeDateLabel = LoanCalculationEngine.computeLoanFreeDate(input, months),
            actualTenureMonths = months,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = LoanCalculationEngine.groupByYear(rows),
            insights = VariantInsights(
                goldSummary = GoldLoanSummary(
                    modeLabel = "Bullet Payment at Maturity",
                    monthlyOutflow = 0.0,
                    maturityLumpSum = totalPayment,
                    totalInterest = totalInterestBd.toDouble(),
                    totalPayable = totalPayment,
                    peakLtvPercent = peakLtv,
                    isAuctionRiskAlert = isAuctionRisk,
                    auctionRiskThresholdLtv = input.goldAuctionRiskLtvPercent
                )
            )
        )
    }
}

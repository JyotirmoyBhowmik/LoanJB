package com.jyotirmoyb.loan.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

object LoanCalculationEngine {

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun roundPaisa(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    /**
     * Standard EMI formula: P * r * (1+r)^n / ((1+r)^n - 1), r = annualRate / 12 / 100
     */
    fun standardEmi(principal: Double, annualRatePercent: Double, tenureMonths: Int): Double {
        if (principal <= 0.0 || tenureMonths <= 0) return 0.0
        val r = annualRatePercent / 12.0 / 100.0
        if (r <= 0.0) return roundPaisa(principal / tenureMonths)
        val factor = (1.0 + r).pow(tenureMonths)
        return roundPaisa(principal * r * factor / (factor - 1.0))
    }

    fun calculate(input: CalculationInput, category: LoanCategory? = null): CalculationResult {
        val flags = input.ruleFlags.toSet()
        val ltvCap = resolveLtvCap(input, category)
        val errors = validateInput(input, category, flags, ltvCap)

        val ltvPercent = if (input.assetValue > 0.0) {
            roundPaisa((input.principal / input.assetValue) * 100.0)
        } else 0.0
        val downPayment = if (input.assetValue > input.principal) {
            roundPaisa(input.assetValue - input.principal)
        } else 0.0
        val isLtvExceeded = (category?.secured == true || flags.contains("ASSET_MARGIN_SHARE")) &&
            input.assetValue > 0.0 && ltvPercent > ltvCap + 0.01

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

        // Dispatch specialized non-standard structures first
        return when {
            flags.contains("BIZ_BILL_DISCOUNT") -> calculateBillDiscounting(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("BIZ_LC_TR") -> calculateLcTrustReceipt(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("FAC_FLEXI_LOC") || flags.contains("FAC_OVERDRAFT") || flags.contains("BIZ_CASH_CREDIT") ->
                calculateFlexiOrCashCredit(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("ASSET_GOLD_INTEREST_ONLY") ->
                calculateGoldInterestOnly(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            flags.contains("ASSET_GOLD_BULLET") ->
                calculateGoldBullet(input, ltvCap, ltvPercent, downPayment, isLtvExceeded)
            else -> calculateAmortizingLoan(input, category, flags, ltvCap, ltvPercent, downPayment, isLtvExceeded)
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

        if (input.principal <= 0.0) {
            errors.add("Loan amount must be greater than 0.")
        }
        if (input.annualRate < 0.0 || input.annualRate > 40.0) {
            errors.add("Annual interest rate must be between 0% and 40%.")
        }
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
            !flags.contains("PURPOSE_BALANCE_TRANSFER") && !flags.contains("PURPOSE_TOP_UP")
        if (enforceLtv && input.assetValue > 0.0) {
            val ltv = (input.principal / input.assetValue) * 100.0
            if (ltv > ltvCap + 0.01) {
                errors.add(
                    "LTV (${String.format("%.1f", ltv)}%) exceeds ${String.format("%.0f", ltvCap)}% cap. Increase asset value or reduce loan amount."
                )
            }
        }
        if (flags.contains("EMI_BALLOON") && (input.balloonPercent <= 0.0 || input.balloonPercent >= 80.0)) {
            errors.add("Balloon lump sum must be between 1% and 79% of principal.")
        }
        if (flags.contains("RATE_HYBRID") && (input.hybridFloatingRate < 0.0 || input.hybridFloatingRate > 40.0)) {
            errors.add("Post-fixed floating rate must be between 0% and 40%.")
        }
        return errors
    }

    private fun calculateAmortizingLoan(
        input: CalculationInput,
        category: LoanCategory?,
        flags: Set<String>,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val isFloating = flags.contains("RATE_FLOATING")
        val isHybrid = flags.contains("RATE_HYBRID")
        val isStepUp = flags.contains("EMI_STEP_UP") && !input.isStepDown
        val isStepDown = flags.contains("EMI_STEP_DOWN") || (flags.contains("EMI_STEP_UP") && input.isStepDown)
        val isOffset = flags.contains("EMI_OFFSET")
        val isTranche = flags.contains("DISB_TRANCHE_PRE_EMI")
        val isMoratorium = flags.contains("TERM_MORATORIUM") || flags.contains("MOR_SIMPLE") || flags.contains("MOR_COMPOUND")
        val isMoratoriumCompound = flags.contains("MOR_COMPOUND") || (flags.contains("TERM_MORATORIUM") && input.moratoriumCompound)
        val isBalloon = flags.contains("EMI_BALLOON")
        val isFlatCompare = flags.contains("BASIS_FLAT_COMPARE")
        val isFdLoan = flags.contains("ASSET_FD")
        val isMarginShare = flags.contains("ASSET_MARGIN_SHARE")

        // Effective annual rate for FD loan if FD spread is used
        val effectiveInitialRate = if (isFdLoan && input.fdRatePercent > 0.0) {
            input.annualRate
        } else {
            input.annualRate
        }

        // Build baseline schedule (without prepayments) when prepayments are enabled to measure exact savings
        val baselineSchedule = if (input.prepaymentEnabled && input.prepayments.any { it.amount > 0.0 }) {
            simulateSchedule(
                input = input.copy(prepaymentEnabled = false, prepayments = emptyList()),
                initialRate = effectiveInitialRate,
                isFloating = isFloating,
                isHybrid = isHybrid,
                isStepUp = isStepUp,
                isStepDown = isStepDown,
                isOffset = isOffset,
                isTranche = isTranche,
                isMoratorium = isMoratorium,
                isMoratoriumCompound = isMoratoriumCompound,
                isBalloon = isBalloon
            ).rows
        } else null

        // Baseline without offset to calculate offset interest savings
        val nonOffsetSchedule = if (isOffset && input.offsetBalance > 0.0) {
            simulateSchedule(
                input = input.copy(offsetBalance = 0.0),
                initialRate = effectiveInitialRate,
                isFloating = isFloating,
                isHybrid = isHybrid,
                isStepUp = isStepUp,
                isStepDown = isStepDown,
                isOffset = false,
                isTranche = isTranche,
                isMoratorium = isMoratorium,
                isMoratoriumCompound = isMoratoriumCompound,
                isBalloon = isBalloon
            ).rows
        } else null

        val sim = simulateSchedule(
            input = input,
            initialRate = effectiveInitialRate,
            isFloating = isFloating,
            isHybrid = isHybrid,
            isStepUp = isStepUp,
            isStepDown = isStepDown,
            isOffset = isOffset,
            isTranche = isTranche,
            isMoratorium = isMoratorium,
            isMoratoriumCompound = isMoratoriumCompound,
            isBalloon = isBalloon
        )

        val rows = sim.rows
        val totalPrincipal = roundPaisa(rows.sumOf { it.principalPaid })
        val totalInterest = roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = roundPaisa(totalPrincipal + totalInterest)

        val processingFeeAmount = roundPaisa(input.principal * (input.processingFeePercent / 100.0))
        val insuranceAmount = roundPaisa(input.insurancePremium)
        val totalPrepaid = roundPaisa(rows.sumOf { it.extraPrepayment })
        val effectivePrepayFeePct = if (isFloating && input.prepaymentFeePercent == 0.0) 0.0 else input.prepaymentFeePercent
        val prepaymentFeeAmount = roundPaisa(totalPrepaid * (effectivePrepayFeePct / 100.0))
        val totalFees = roundPaisa(processingFeeAmount + insuranceAmount + prepaymentFeeAmount)
        val effectiveTotalCost = roundPaisa(totalPayment + totalFees)

        val emiRows = rows.filter { it.phaseLabel != "Pre-EMI" && it.phaseLabel != "Moratorium" }
        val firstEmi = emiRows.firstOrNull()?.emiPaid ?: rows.firstOrNull()?.emiPaid ?: 0.0
        val lastEmi = emiRows.lastOrNull()?.emiPaid ?: rows.lastOrNull()?.emiPaid ?: 0.0
        val headlineEmi = if (isStepUp || isStepDown || isTranche || isMoratorium) firstEmi else (rows.firstOrNull()?.emiPaid ?: 0.0)

        // Build variant-specific insights
        val sensitivityRows = if (isFloating || isHybrid) {
            buildSensitivityTable(input.principal, effectiveInitialRate, input.tenureMonths)
        } else emptyList()

        val offsetInterestSaved = if (nonOffsetSchedule != null) {
            roundPaisa(max(0.0, nonOffsetSchedule.sumOf { it.interestPaid } - totalInterest))
        } else 0.0

        val flatVsReducing = if (isFlatCompare) {
            calculateFlatVsReducing(input.principal, effectiveInitialRate, input.tenureMonths)
        } else null

        val prepaymentImpact = if (baselineSchedule != null) {
            val baseInt = roundPaisa(baselineSchedule.sumOf { it.interestPaid })
            val interestSaved = roundPaisa(max(0.0, baseInt - totalInterest))
            val netSavings = roundPaisa(interestSaved - prepaymentFeeAmount)
            PrepaymentImpact(
                baselineTotalInterest = baseInt,
                newTotalInterest = totalInterest,
                interestSaved = interestSaved,
                prepaymentFees = prepaymentFeeAmount,
                netSavings = netSavings,
                baselineMonths = baselineSchedule.size,
                newMonths = rows.size,
                monthsSaved = max(0, baselineSchedule.size - rows.size)
            )
        } else null

        val btResult = if (input.balanceTransfer.enabled || flags.contains("PURPOSE_BALANCE_TRANSFER")) {
            calculateBalanceTransfer(input.balanceTransfer)
        } else null

        val topUpResult = if (input.topUp.enabled || flags.contains("PURPOSE_TOP_UP")) {
            calculateTopUp(input.topUp)
        } else null

        val fdNetCost = if (isFdLoan) {
            val fdVal = if (input.assetValue > 0.0) input.assetValue else input.principal / 0.85
            val years = input.tenureMonths / 12.0
            // Quarterly compounded FD interest earned during loan period
            val fdEarned = roundPaisa(fdVal * ((1.0 + input.fdRatePercent / 100.0 / 4.0).pow(4.0 * years) - 1.0))
            val netCost = roundPaisa(totalInterest - fdEarned)
            val effNetRate = if (input.principal > 0.0 && years > 0.0) {
                roundPaisa(((effectiveInitialRate - input.fdRatePercent)))
            } else 0.0
            FdNetCostResult(
                fdValue = roundPaisa(fdVal),
                loanAmount = roundPaisa(input.principal),
                fdRatePercent = input.fdRatePercent,
                loanRatePercent = effectiveInitialRate,
                loanInterestPaid = totalInterest,
                fdInterestEarned = fdEarned,
                netInterestCost = netCost,
                effectiveNetRatePercent = effNetRate
            )
        } else null

        val marginCallResult = if (isMarginShare) {
            val pledged = if (input.shareValue > 0.0) input.shareValue else input.assetValue
            val currentLtv = if (pledged > 0.0) roundPaisa((input.principal / pledged) * 100.0) else 0.0
            val maxAllowedLtv = min(ltvCap, 100.0 - input.minMarginPercent)
            val triggerValue = if (maxAllowedLtv > 0.0) roundPaisa(input.principal / (maxAllowedLtv / 100.0)) else 0.0
            val dropPct = if (pledged > 0.0) roundPaisa(((pledged - triggerValue) / pledged) * 100.0) else 0.0
            MarginCallResult(
                pledgedShareValue = roundPaisa(pledged),
                loanAmount = roundPaisa(input.principal),
                currentLtvPercent = currentLtv,
                maxAllowedLtvPercent = maxAllowedLtv,
                minMarginPercent = input.minMarginPercent,
                marginCallTriggerValue = triggerValue,
                dropPercentToMarginCall = dropPct,
                isMarginCallTriggered = pledged <= triggerValue
            )
        } else null

        val goldSummary = if (flags.contains("ASSET_GOLD_EMI")) {
            GoldLoanSummary(
                modeLabel = "Standard Monthly EMI",
                monthlyOutflow = headlineEmi,
                maturityLumpSum = lastEmi,
                totalInterest = totalInterest,
                totalPayable = totalPayment
            )
        } else null

        return CalculationResult(
            isValid = true,
            monthlyEmi = headlineEmi,
            firstEmi = firstEmi,
            lastEmi = lastEmi,
            totalPrincipal = totalPrincipal,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = processingFeeAmount,
            insuranceAmount = insuranceAmount,
            prepaymentFeeAmount = prepaymentFeeAmount,
            totalFees = totalFees,
            effectiveTotalCost = effectiveTotalCost,
            actualTenureMonths = rows.size,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = groupByYear(rows),
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
                goldSummary = goldSummary
            )
        )
    }

    private data class SimulationOutput(
        val rows: List<AmortizationRow>,
        val moratoriumExtraInterest: Double = 0.0
    )

    private fun simulateSchedule(
        input: CalculationInput,
        initialRate: Double,
        isFloating: Boolean,
        isHybrid: Boolean,
        isStepUp: Boolean,
        isStepDown: Boolean,
        isOffset: Boolean,
        isTranche: Boolean,
        isMoratorium: Boolean,
        isMoratoriumCompound: Boolean,
        isBalloon: Boolean
    ): SimulationOutput {
        val rows = mutableListOf<AmortizationRow>()
        val sortedRateChanges = input.rateChanges.sortedBy { it.afterMonth }
        val activePrepayments = if (input.prepaymentEnabled) input.prepayments.filter { it.amount > 0.0 } else emptyList()

        var monthIdx = 1
        var currentRate = initialRate
        var balance = 0.0
        var moratoriumAccrued = 0.0

        // Phase 1: Tranche + Pre-EMI (disbursed in stages before full EMI)
        if (isTranche && input.tranches.isNotEmpty()) {
            val validTranches = input.tranches.filter { it.amount > 0.0 }.sortedBy { it.monthNumber }
            val trancheTotal = validTranches.sumOf { it.amount }
            val scale = if (trancheTotal > 0.0) input.principal / trancheTotal else 1.0
            val handoverMonth = validTranches.maxOfOrNull { it.monthNumber } ?: 1
            var disbursed = 0.0

            for (m in 1 until handoverMonth) {
                val added = validTranches.filter { it.monthNumber == m }.sumOf { roundPaisa(it.amount * scale) }
                disbursed = roundPaisa(min(input.principal, disbursed + added))
                currentRate = resolveActiveRate(m, initialRate, isFloating, isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges)
                val interest = roundPaisa(disbursed * (currentRate / 12.0 / 100.0))
                val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, monthIdx)
                rows.add(
                    AmortizationRow(
                        monthNumber = monthIdx,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = disbursed,
                        emiPaid = interest,
                        principalPaid = 0.0,
                        interestPaid = interest,
                        extraPrepayment = 0.0,
                        closingBalance = disbursed,
                        activeRate = currentRate,
                        phaseLabel = "Pre-EMI"
                    )
                )
                monthIdx++
            }
            balance = roundPaisa(input.principal)
        } else {
            balance = roundPaisa(input.principal)
        }

        // Phase 2: Moratorium (principal paused for N months)
        val effectiveMoratorium = if (isMoratorium) max(0, min(input.moratoriumMonths, 60)) else 0
        if (effectiveMoratorium > 0) {
            var simpleInterestBucket = 0.0
            for (k in 1..effectiveMoratorium) {
                currentRate = resolveActiveRate(monthIdx, initialRate, isFloating, isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges)
                val monthlyRate = currentRate / 12.0 / 100.0
                val opening = balance
                val interest = roundPaisa(balance * monthlyRate)
                val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, monthIdx)

                if (isMoratoriumCompound) {
                    // Unpaid interest capitalizes into principal each month
                    balance = roundPaisa(balance + interest)
                    moratoriumAccrued = roundPaisa(moratoriumAccrued + interest)
                    rows.add(
                        AmortizationRow(
                            monthNumber = monthIdx,
                            monthLabel = mLabel,
                            yearGroupIndex = yIdx,
                            yearRangeLabel = yLabel,
                            openingBalance = opening,
                            emiPaid = 0.0,
                            principalPaid = 0.0,
                            interestPaid = 0.0,
                            extraPrepayment = 0.0,
                            closingBalance = balance,
                            activeRate = currentRate,
                            phaseLabel = "Moratorium"
                        )
                    )
                } else {
                    // Simple interest paid monthly during moratorium
                    simpleInterestBucket = roundPaisa(simpleInterestBucket + interest)
                    rows.add(
                        AmortizationRow(
                            monthNumber = monthIdx,
                            monthLabel = mLabel,
                            yearGroupIndex = yIdx,
                            yearRangeLabel = yLabel,
                            openingBalance = opening,
                            emiPaid = interest,
                            principalPaid = 0.0,
                            interestPaid = interest,
                            extraPrepayment = 0.0,
                            closingBalance = balance,
                            activeRate = currentRate,
                            phaseLabel = "Moratorium"
                        )
                    )
                }
                monthIdx++
            }
        }

        // Phase 3: Amortizing Repayment
        val amortTenure = max(1, input.tenureMonths)
        val balloonAmount = if (isBalloon) roundPaisa(balance * (input.balloonPercent / 100.0)) else 0.0

        currentRate = resolveActiveRate(monthIdx, initialRate, isFloating, isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges)
        var currentEmi = when {
            isBalloon -> {
                val r = currentRate / 12.0 / 100.0
                val amortPart = standardEmi(balance - balloonAmount, currentRate, amortTenure)
                val balloonInterest = roundPaisa(balloonAmount * r)
                roundPaisa(amortPart + balloonInterest)
            }
            isStepUp -> {
                // Start ~15% lower than level EMI (bounded above monthly interest + small principal) so step-up closes cleanly
                val baseLevel = standardEmi(balance, currentRate, amortTenure)
                val minSafe = roundPaisa(balance * (currentRate / 12.0 / 100.0) * 1.05 + 100.0)
                max(minSafe, roundPaisa(baseLevel * 0.82))
            }
            isStepDown -> {
                // Start ~20% higher than level EMI and step down periodically
                val baseLevel = standardEmi(balance, currentRate, amortTenure)
                roundPaisa(baseLevel * 1.22)
            }
            else -> standardEmi(balance, currentRate, amortTenure)
        }

        val maxLoops = max(amortTenure + 240, 600)
        var amortStep = 1
        var previousRate = currentRate

        while (balance > 0.004 && amortStep <= maxLoops) {
            currentRate = resolveActiveRate(monthIdx, initialRate, isFloating, isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges)

            // Recompute EMI on rate change (for Hybrid or Floating)
            if (abs(currentRate - previousRate) > 0.0001) {
                val remainingMonths = max(1, amortTenure - amortStep + 1)
                currentEmi = if (isBalloon) {
                    val r = currentRate / 12.0 / 100.0
                    roundPaisa(standardEmi(max(0.0, balance - balloonAmount), currentRate, remainingMonths) + balloonAmount * r)
                } else {
                    standardEmi(balance, currentRate, remainingMonths)
                }
                previousRate = currentRate
            }

            // Apply Step-Up / Step-Down adjustment every stepIntervalMonths
            val interval = max(1, input.stepIntervalMonths)
            if ((isStepUp || isStepDown) && amortStep > 1 && (amortStep - 1) % interval == 0) {
                val pct = input.stepPercent / 100.0
                currentEmi = if (isStepUp) {
                    roundPaisa(currentEmi * (1.0 + pct))
                } else {
                    val monthlyInt = balance * (currentRate / 12.0 / 100.0)
                    val minSafeEmi = roundPaisa(monthlyInt + max(100.0, balance * 0.001))
                    max(minSafeEmi, roundPaisa(currentEmi * (1.0 - pct)))
                }
            }

            val opening = balance
            val monthlyRate = currentRate / 12.0 / 100.0
            val interestBearingPrincipal = if (isOffset) max(0.0, balance - input.offsetBalance) else balance
            val interest = roundPaisa(interestBearingPrincipal * monthlyRate)

            // Check if this is the final scheduled month for Balloon or standard exact-zero closure
            val isBalloonFinal = isBalloon && amortStep == amortTenure
            val isNaturalFinal = (!isStepUp && !isStepDown && !input.prepaymentEnabled && !isOffset && amortStep == amortTenure) ||
                amortStep == maxLoops

            var scheduledPrincipal: Double
            var emiThisMonth: Double

            if (isBalloonFinal) {
                scheduledPrincipal = balance
                emiThisMonth = roundPaisa(scheduledPrincipal + interest)
            } else if (isNaturalFinal || (currentEmi - interest) >= balance - 0.01) {
                // Final EMI absorbs rounding residual so closing balance is strictly 0.00
                scheduledPrincipal = balance
                emiThisMonth = roundPaisa(scheduledPrincipal + interest)
            } else {
                val rawPrincipal = roundPaisa(currentEmi - interest)
                scheduledPrincipal = max(0.0, min(balance, rawPrincipal))
                emiThisMonth = roundPaisa(scheduledPrincipal + interest)
            }

            var closing = roundPaisa(balance - scheduledPrincipal)

            // Apply any prepayment scheduled for this month
            var extraPrepay = 0.0
            var reduceEmiTriggered = false
            for (rule in activePrepayments) {
                val matches = if (rule.isRecurring) {
                    monthIdx >= rule.startMonth && (monthIdx - rule.startMonth) % max(1, rule.intervalMonths) == 0
                } else {
                    monthIdx == rule.startMonth
                }
                if (matches && closing > 0.0) {
                    val applied = roundPaisa(min(closing, rule.amount))
                    extraPrepay = roundPaisa(extraPrepay + applied)
                    closing = roundPaisa(closing - applied)
                    if (rule.mode == PrepaymentMode.REDUCE_EMI.name) {
                        reduceEmiTriggered = true
                    }
                }
            }

            if (closing < 0.01) closing = 0.0

            val phaseLabel = when {
                isBalloonFinal -> "Balloon"
                isHybrid && monthIdx <= input.hybridFixedMonths -> "Fixed"
                isHybrid -> "Floating"
                isFloating -> "Floating"
                else -> "EMI"
            }

            val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, monthIdx)
            rows.add(
                AmortizationRow(
                    monthNumber = monthIdx,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = opening,
                    emiPaid = roundPaisa(emiThisMonth + extraPrepay),
                    principalPaid = roundPaisa(scheduledPrincipal + extraPrepay),
                    interestPaid = interest,
                    extraPrepayment = extraPrepay,
                    closingBalance = closing,
                    activeRate = currentRate,
                    phaseLabel = phaseLabel
                )
            )

            balance = closing
            if (reduceEmiTriggered && balance > 0.0) {
                val remainingMonths = max(1, amortTenure - amortStep)
                currentEmi = standardEmi(balance, currentRate, remainingMonths)
            }

            monthIdx++
            amortStep++
        }

        return SimulationOutput(rows = rows, moratoriumExtraInterest = moratoriumAccrued)
    }

    private fun resolveActiveRate(
        monthNumber: Int,
        initialRate: Double,
        isFloating: Boolean,
        isHybrid: Boolean,
        hybridFixedMonths: Int,
        hybridFloatingRate: Double,
        rateChanges: List<RateChangeEvent>
    ): Double {
        var rate = initialRate
        if (isHybrid && monthNumber > hybridFixedMonths) {
            rate = hybridFloatingRate
        }
        if (isFloating || isHybrid) {
            for (ev in rateChanges) {
                if (monthNumber > ev.afterMonth) {
                    rate = ev.newAnnualRate.coerceIn(0.0, 40.0)
                }
            }
        }
        return rate
    }

    fun buildSensitivityTable(
        principal: Double,
        baseRate: Double,
        tenureMonths: Int
    ): List<RateSensitivityRow> {
        val baseEmi = standardEmi(principal, baseRate, tenureMonths)
        val baseTotalInterest = roundPaisa(baseEmi * tenureMonths - principal)
        val deltas = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
        return deltas.map { delta ->
            val rate = (baseRate + delta).coerceIn(0.1, 40.0)
            val emi = standardEmi(principal, rate, tenureMonths)
            val totalInt = roundPaisa(emi * tenureMonths - principal)
            RateSensitivityRow(
                deltaPercent = delta,
                effectiveRate = roundPaisa(rate),
                monthlyEmi = emi,
                totalInterest = totalInt,
                interestDiff = roundPaisa(totalInt - baseTotalInterest)
            )
        }
    }

    /**
     * Computes Flat Rate vs Reducing Balance Rate comparison + true Effective Annual Rate (IRR) of Flat loan
     */
    fun calculateFlatVsReducing(
        principal: Double,
        quotedAnnualRate: Double,
        tenureMonths: Int
    ): FlatVsReducingComparison {
        val years = tenureMonths / 12.0
        val flatTotalInterest = roundPaisa(principal * (quotedAnnualRate / 100.0) * years)
        val flatMonthlyEmi = roundPaisa((principal + flatTotalInterest) / tenureMonths)

        val reducingMonthlyEmi = standardEmi(principal, quotedAnnualRate, tenureMonths)
        val reducingTotalInterest = roundPaisa(reducingMonthlyEmi * tenureMonths - principal)
        val extraCost = roundPaisa(max(0.0, flatTotalInterest - reducingTotalInterest))

        // Solve for monthly IRR r where standardEmi(P, r*1200, n) == flatMonthlyEmi via binary search
        val earPercent = solveEffectiveAnnualRate(principal, flatMonthlyEmi, tenureMonths)

        return FlatVsReducingComparison(
            quotedRate = quotedAnnualRate,
            flatMonthlyEmi = flatMonthlyEmi,
            reducingMonthlyEmi = reducingMonthlyEmi,
            flatTotalInterest = flatTotalInterest,
            reducingTotalInterest = reducingTotalInterest,
            extraInterestCost = extraCost,
            effectiveAnnualRatePercent = roundPaisa(earPercent)
        )
    }

    private fun solveEffectiveAnnualRate(principal: Double, monthlyPayment: Double, months: Int): Double {
        if (principal <= 0.0 || months <= 0 || monthlyPayment * months <= principal) return 0.0
        var low = 0.0
        var high = 1.0 // up to 100% monthly (1200% APR)
        repeat(60) {
            val mid = (low + high) / 2.0
            val pv = if (mid < 1e-9) {
                monthlyPayment * months
            } else {
                monthlyPayment * (1.0 - (1.0 + mid).pow(-months)) / mid
            }
            if (pv > principal) {
                low = mid
            } else {
                high = mid
            }
        }
        val monthlyIrr = (low + high) / 2.0
        return monthlyIrr * 12.0 * 100.0
    }

    fun calculateBalanceTransfer(bt: BalanceTransferInput): BalanceTransferResult {
        val currentEmi = standardEmi(bt.currentOutstanding, bt.currentAnnualRate, bt.remainingMonths)
        val newEmi = standardEmi(bt.currentOutstanding, bt.newAnnualRate, bt.remainingMonths)
        val currentInt = roundPaisa(max(0.0, currentEmi * bt.remainingMonths - bt.currentOutstanding))
        val newInt = roundPaisa(max(0.0, newEmi * bt.remainingMonths - bt.currentOutstanding))
        val monthlySavings = roundPaisa(currentEmi - newEmi)
        val netSavings = roundPaisa(currentInt - newInt - bt.transferFees)
        val breakEven = if (monthlySavings > 0.0 && bt.transferFees > 0.0) {
            kotlin.math.ceil(bt.transferFees / monthlySavings).toInt()
        } else if (monthlySavings > 0.0) {
            1
        } else null

        return BalanceTransferResult(
            currentEmi = currentEmi,
            newEmi = newEmi,
            monthlySavings = monthlySavings,
            currentRemainingInterest = currentInt,
            newTotalInterest = newInt,
            transferFees = roundPaisa(bt.transferFees),
            netSavings = netSavings,
            breakEvenMonth = breakEven
        )
    }

    fun calculateTopUp(tu: TopUpInput): TopUpResult {
        val existingEmi = standardEmi(tu.existingOutstanding, tu.existingAnnualRate, tu.existingRemainingMonths)
        val topUpEmi = standardEmi(tu.topUpAmount, tu.topUpAnnualRate, tu.topUpTenureMonths)
        val blendedEmi = roundPaisa(existingEmi + topUpEmi)
        val existingInt = roundPaisa(max(0.0, existingEmi * tu.existingRemainingMonths - tu.existingOutstanding))
        val topUpInt = roundPaisa(max(0.0, topUpEmi * tu.topUpTenureMonths - tu.topUpAmount))
        return TopUpResult(
            existingEmi = existingEmi,
            topUpEmi = topUpEmi,
            blendedEmi = blendedEmi,
            combinedPrincipal = roundPaisa(tu.existingOutstanding + tu.topUpAmount),
            combinedTotalInterest = roundPaisa(existingInt + topUpInt)
        )
    }

    private fun calculateFlexiOrCashCredit(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val rows = mutableListOf<AmortizationRow>()
        val months = max(1, input.tenureMonths)
        val monthlyRate = input.annualRate / 12.0 / 100.0
        var utilized = 0.0
        var peakBalance = 0.0
        val eventsByMonth = input.flexiEvents.groupBy { it.monthNumber }

        // If user has no events configured, default to drawing 70% in month 1 and closing at end
        val effectiveEvents = if (input.flexiEvents.isEmpty()) {
            mapOf(1 to listOf(FlexiEvent(1, drawAmount = roundPaisa(input.principal * 0.7))))
        } else eventsByMonth

        for (m in 1..months) {
            val evs = effectiveEvents[m].orEmpty()
            val draw = evs.sumOf { it.drawAmount }
            val repay = evs.sumOf { it.repayAmount }
            val opening = roundPaisa(min(input.principal, utilized + draw))
            if (opening > peakBalance) peakBalance = opening
            val interest = roundPaisa(opening * monthlyRate)

            val principalRepaid = if (m == months) {
                opening // Clear remaining utilized balance at facility maturity
            } else {
                roundPaisa(min(opening, repay))
            }
            val closing = roundPaisa(max(0.0, opening - principalRepaid))
            val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, m)
            rows.add(
                AmortizationRow(
                    monthNumber = m,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = opening,
                    emiPaid = roundPaisa(principalRepaid + interest),
                    principalPaid = principalRepaid,
                    interestPaid = interest,
                    extraPrepayment = 0.0,
                    closingBalance = closing,
                    activeRate = input.annualRate,
                    phaseLabel = if (m == months && opening > 0.0) "Closure" else "Drawn"
                )
            )
            utilized = closing
        }

        val totalPrincipal = roundPaisa(rows.sumOf { it.principalPaid })
        val totalInterest = roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = roundPaisa(totalPrincipal + totalInterest)
        val procFee = roundPaisa(input.principal * (input.processingFeePercent / 100.0))
        val insFee = roundPaisa(input.insurancePremium)
        val totalFees = roundPaisa(procFee + insFee)
        val avgMonthlyInterest = roundPaisa(totalInterest / months)

        return CalculationResult(
            isValid = true,
            monthlyEmi = avgMonthlyInterest,
            firstEmi = rows.firstOrNull()?.emiPaid ?: 0.0,
            lastEmi = rows.lastOrNull()?.emiPaid ?: 0.0,
            totalPrincipal = totalPrincipal,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = procFee,
            insuranceAmount = insFee,
            totalFees = totalFees,
            effectiveTotalCost = roundPaisa(totalPayment + totalFees),
            actualTenureMonths = rows.size,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = groupByYear(rows),
            insights = VariantInsights(flexiPeakBalance = roundPaisa(peakBalance))
        )
    }

    private fun calculateBillDiscounting(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val advance = roundPaisa(input.billInvoiceAmount * (input.billAdvancePercent / 100.0))
        val discountFee = roundPaisa(advance * (input.billDiscountRatePercent / 100.0) * (input.billDays / 365.0))
        val procFee = roundPaisa(advance * (input.processingFeePercent / 100.0))
        val netDisbursed = roundPaisa(max(0.01, advance - discountFee - procFee))
        val totalCost = roundPaisa(discountFee + procFee)
        // Effective annualized cost = (totalCost / netDisbursed) * (365 / days) * 100
        val effectiveAnnualized = roundPaisa((totalCost / netDisbursed) * (365.0 / input.billDays) * 100.0)

        val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, 1)
        val row = AmortizationRow(
            monthNumber = 1,
            monthLabel = "${input.billDays}d Maturity",
            yearGroupIndex = yIdx,
            yearRangeLabel = yLabel,
            openingBalance = advance,
            emiPaid = advance,
            principalPaid = advance,
            interestPaid = discountFee,
            extraPrepayment = 0.0,
            closingBalance = 0.0,
            activeRate = effectiveAnnualized,
            phaseLabel = "Bill Settlement"
        )

        return CalculationResult(
            isValid = true,
            monthlyEmi = advance,
            firstEmi = advance,
            lastEmi = advance,
            totalPrincipal = advance,
            totalInterest = discountFee,
            totalPayment = roundPaisa(advance + discountFee),
            processingFeeAmount = procFee,
            totalFees = procFee,
            effectiveTotalCost = roundPaisa(advance + totalCost),
            actualTenureMonths = 1,
            ltvPercent = input.billAdvancePercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = roundPaisa(input.billInvoiceAmount - advance),
            schedule = listOf(row),
            yearlySummaries = groupByYear(listOf(row)),
            insights = VariantInsights(
                billDiscountResult = BillDiscountResult(
                    invoiceAmount = roundPaisa(input.billInvoiceAmount),
                    advanceAmount = advance,
                    discountFee = discountFee,
                    netUpfrontDisbursed = netDisbursed,
                    days = input.billDays,
                    effectiveAnnualizedCostPercent = effectiveAnnualized
                )
            )
        )
    }

    private fun calculateLcTrustReceipt(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val days = max(15, input.lcDays)
        val interest = roundPaisa(input.principal * (input.annualRate / 100.0) * (days / 365.0))
        val commission = roundPaisa(input.principal * (input.lcCommissionPercent / 100.0))
        val procFee = roundPaisa(input.principal * (input.processingFeePercent / 100.0))
        val totalFees = roundPaisa(commission + procFee + input.insurancePremium)
        val maturityPay = roundPaisa(input.principal + interest)

        val (_, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, 1)
        val row = AmortizationRow(
            monthNumber = 1,
            monthLabel = "${days}d TR Due",
            yearGroupIndex = yIdx,
            yearRangeLabel = yLabel,
            openingBalance = roundPaisa(input.principal),
            emiPaid = maturityPay,
            principalPaid = roundPaisa(input.principal),
            interestPaid = interest,
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
            totalPrincipal = roundPaisa(input.principal),
            totalInterest = interest,
            totalPayment = maturityPay,
            processingFeeAmount = procFee,
            insuranceAmount = roundPaisa(input.insurancePremium),
            totalFees = totalFees,
            effectiveTotalCost = roundPaisa(maturityPay + totalFees),
            actualTenureMonths = 1,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = listOf(row),
            yearlySummaries = groupByYear(listOf(row)),
            insights = VariantInsights(lcCommissionFee = commission)
        )
    }

    private fun calculateGoldInterestOnly(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val months = max(1, input.tenureMonths)
        val monthlyInterest = roundPaisa(input.principal * (input.annualRate / 12.0 / 100.0))
        val rows = mutableListOf<AmortizationRow>()
        for (m in 1..months) {
            val isLast = m == months
            val principalPaid = if (isLast) roundPaisa(input.principal) else 0.0
            val closing = if (isLast) 0.0 else roundPaisa(input.principal)
            val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, m)
            rows.add(
                AmortizationRow(
                    monthNumber = m,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = roundPaisa(input.principal),
                    emiPaid = roundPaisa(monthlyInterest + principalPaid),
                    principalPaid = principalPaid,
                    interestPaid = monthlyInterest,
                    extraPrepayment = 0.0,
                    closingBalance = closing,
                    activeRate = input.annualRate,
                    phaseLabel = if (isLast) "Maturity" else "Interest-Only"
                )
            )
        }
        val totalInterest = roundPaisa(rows.sumOf { it.interestPaid })
        val totalPayment = roundPaisa(input.principal + totalInterest)
        val procFee = roundPaisa(input.principal * (input.processingFeePercent / 100.0))
        val totalFees = roundPaisa(procFee + input.insurancePremium)

        return CalculationResult(
            isValid = true,
            monthlyEmi = monthlyInterest,
            firstEmi = monthlyInterest,
            lastEmi = roundPaisa(input.principal + monthlyInterest),
            totalPrincipal = roundPaisa(input.principal),
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = procFee,
            insuranceAmount = roundPaisa(input.insurancePremium),
            totalFees = totalFees,
            effectiveTotalCost = roundPaisa(totalPayment + totalFees),
            actualTenureMonths = months,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = groupByYear(rows),
            insights = VariantInsights(
                goldSummary = GoldLoanSummary(
                    modeLabel = "Monthly Interest-Only",
                    monthlyOutflow = monthlyInterest,
                    maturityLumpSum = roundPaisa(input.principal + monthlyInterest),
                    totalInterest = totalInterest,
                    totalPayable = totalPayment
                )
            )
        )
    }

    private fun calculateGoldBullet(
        input: CalculationInput,
        ltvCap: Double,
        ltvPercent: Double,
        downPayment: Double,
        isLtvExceeded: Boolean
    ): CalculationResult {
        val months = max(1, input.tenureMonths)
        val monthlyRate = input.annualRate / 12.0 / 100.0
        val rows = mutableListOf<AmortizationRow>()
        var accruedBalance = roundPaisa(input.principal)
        var totalInterest = 0.0

        for (m in 1..months) {
            val opening = accruedBalance
            val interest = roundPaisa(opening * monthlyRate)
            totalInterest = roundPaisa(totalInterest + interest)
            val isLast = m == months
            val (mLabel, yIdx, yLabel) = dateMeta(input.startYear, input.startMonth, m)
            if (isLast) {
                val bulletPay = roundPaisa(input.principal + totalInterest)
                rows.add(
                    AmortizationRow(
                        monthNumber = m,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = opening,
                        emiPaid = bulletPay,
                        principalPaid = roundPaisa(input.principal),
                        interestPaid = totalInterest,
                        extraPrepayment = 0.0,
                        closingBalance = 0.0,
                        activeRate = input.annualRate,
                        phaseLabel = "Bullet"
                    )
                )
                accruedBalance = 0.0
            } else {
                accruedBalance = roundPaisa(opening + interest)
                rows.add(
                    AmortizationRow(
                        monthNumber = m,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = opening,
                        emiPaid = 0.0,
                        principalPaid = 0.0,
                        interestPaid = 0.0,
                        extraPrepayment = 0.0,
                        closingBalance = accruedBalance,
                        activeRate = input.annualRate,
                        phaseLabel = "Accruing"
                    )
                )
            }
        }

        val totalPayment = roundPaisa(input.principal + totalInterest)
        val procFee = roundPaisa(input.principal * (input.processingFeePercent / 100.0))
        val totalFees = roundPaisa(procFee + input.insurancePremium)

        return CalculationResult(
            isValid = true,
            monthlyEmi = 0.0,
            firstEmi = 0.0,
            lastEmi = totalPayment,
            totalPrincipal = roundPaisa(input.principal),
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = procFee,
            insuranceAmount = roundPaisa(input.insurancePremium),
            totalFees = totalFees,
            effectiveTotalCost = roundPaisa(totalPayment + totalFees),
            actualTenureMonths = months,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = rows,
            yearlySummaries = groupByYear(rows),
            insights = VariantInsights(
                goldSummary = GoldLoanSummary(
                    modeLabel = "Bullet Payment at Maturity",
                    monthlyOutflow = 0.0,
                    maturityLumpSum = totalPayment,
                    totalInterest = totalInterest,
                    totalPayable = totalPayment
                )
            )
        )
    }

    private fun dateMeta(startYear: Int, startMonth: Int, offsetOneBased: Int): Triple<String, Int, String> {
        val totalM = (startMonth - 1) + (offsetOneBased - 1)
        val mIdx = ((totalM % 12) + 12) % 12
        val year = startYear + (totalM / 12)
        val monthLabel = "${MONTH_NAMES[mIdx]} '$year".replace("'$year", "'${year % 100}")
        val yearGroupIdx = ((offsetOneBased - 1) / 12) + 1

        val groupStartOffset = (yearGroupIdx - 1) * 12
        val gsTotal = (startMonth - 1) + groupStartOffset
        val geTotal = gsTotal + 11
        val startName = MONTH_NAMES[((gsTotal % 12) + 12) % 12]
        val endName = MONTH_NAMES[((geTotal % 12) + 12) % 12]
        val rangeLabel = "YEAR $yearGroupIdx • $startName – $endName"
        return Triple(monthLabel, yearGroupIdx, rangeLabel)
    }

    private fun groupByYear(rows: List<AmortizationRow>): List<YearlySummary> {
        return rows.groupBy { it.yearGroupIndex }.entries.sortedBy { it.key }.map { (yIdx, groupRows) ->
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
}

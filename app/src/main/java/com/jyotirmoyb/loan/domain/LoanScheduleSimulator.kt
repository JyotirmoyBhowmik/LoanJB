package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class SimulationOutput(
    val rows: List<AmortizationRow>,
    val moratoriumExtraInterest: Double = 0.0,
    val brokenPeriodInterest: Double = 0.0,
    val pmaySubsidyApplied: Double = 0.0
)

/**
 * Month-by-month BigDecimal amortization simulator extracted from [LoanCalculationEngine]
 * supporting tranches (Pre-EMI), moratoriums (simple & compound), step-up/step-down EMIs,
 * offset balances, balloon payments, floating/MCLR/hybrid rate resets, PMAY subsidy,
 * broken-period interest, and prepayments.
 */
internal object LoanScheduleSimulator {

    private fun calculateBrokenPeriodInterest(input: CalculationInput, annualRate: Double): BigDecimal {
        if (!input.proRataFirstMonthEnabled) return MoneyMath.ZERO
        val startD = input.startDay.coerceIn(1, 31)
        val emiD = input.emiDayOfMonth.coerceIn(1, 31)
        if (startD == emiD) return MoneyMath.ZERO
        val brokenDays = if (emiD > startD) emiD - startD else (30 - startD).coerceAtLeast(0) + emiD
        return MoneyMath.dailyProRataInterest(
            MoneyMath.bd(input.principal),
            MoneyMath.bd(annualRate),
            brokenDays.coerceIn(1, 30)
        )
    }

    fun simulateSchedule(
        input: CalculationInput,
        initialRate: Double,
        isFloating: Boolean,
        isMclr: Boolean,
        isHybrid: Boolean,
        isStepUp: Boolean,
        isStepDown: Boolean,
        isOffset: Boolean,
        isTranche: Boolean,
        isMoratorium: Boolean,
        isMoratoriumCompound: Boolean,
        isBalloon: Boolean,
        isCreditCardEmi: Boolean
    ): SimulationOutput {
        val rows = mutableListOf<AmortizationRow>()
        val sortedRateChanges = input.rateChanges.sortedBy { it.afterMonth }
        val activePrepayments = if (input.prepaymentEnabled) input.prepayments.filter { it.amount > 0.0 } else emptyList()
        val gstRateBd = MoneyMath.bd(input.gstRatePercent)

        var monthIdx = 1
        var currentRate = initialRate
        var balanceBd: BigDecimal
        var moratoriumAccruedBd = MoneyMath.ZERO
        var pmayAppliedBd = MoneyMath.ZERO
        val brokenPeriodBd = calculateBrokenPeriodInterest(input, initialRate)
        var brokenApplied = false

        if (isTranche && input.tranches.isNotEmpty()) {
            val validTranches = input.tranches.filter { it.amount > 0.0 }.sortedBy { it.monthNumber }
            val trancheTotal = validTranches.sumOf { it.amount }
            val scale = if (trancheTotal > 0.0) input.principal / trancheTotal else 1.0
            val handoverMonth = validTranches.maxOfOrNull { it.monthNumber } ?: 1
            var disbursedBd = MoneyMath.ZERO
            val principalCapBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))

            for (m in 1 until handoverMonth) {
                val added = validTranches.filter { it.monthNumber == m }
                    .fold(MoneyMath.ZERO) { acc, t -> acc.add(MoneyMath.roundPaiseBd(MoneyMath.bd(t.amount * scale))) }
                disbursedBd = MoneyMath.roundPaiseBd(disbursedBd.add(added)).min(principalCapBd)
                currentRate = resolveActiveRate(
                    monthIdx, initialRate, isFloating, isMclr, input.mclrResetMonths,
                    isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges
                )
                val interestBd = MoneyMath.monthlyInterest(disbursedBd, MoneyMath.bd(currentRate))

                var prepayBd = MoneyMath.ZERO
                for (rule in activePrepayments) {
                    if (matchesPrepaymentMonth(rule, monthIdx) && disbursedBd > MoneyMath.ZERO) {
                        val applied = MoneyMath.roundPaiseBd(MoneyMath.bd(rule.amount)).min(disbursedBd)
                        prepayBd = MoneyMath.roundPaiseBd(prepayBd.add(applied))
                        disbursedBd = MoneyMath.roundPaiseBd(disbursedBd.subtract(applied))
                    }
                }

                val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, monthIdx)
                rows.add(
                    AmortizationRow(
                        monthNumber = monthIdx,
                        monthLabel = mLabel,
                        yearGroupIndex = yIdx,
                        yearRangeLabel = yLabel,
                        openingBalance = MoneyMath.roundPaiseBd(disbursedBd.add(prepayBd)).toDouble(),
                        emiPaid = MoneyMath.roundPaiseBd(interestBd.add(prepayBd)).toDouble(),
                        principalPaid = prepayBd.toDouble(),
                        interestPaid = interestBd.toDouble(),
                        extraPrepayment = prepayBd.toDouble(),
                        closingBalance = disbursedBd.toDouble(),
                        activeRate = currentRate,
                        phaseLabel = "Pre-EMI"
                    )
                )
                monthIdx++
            }
            val preEmiPrepaid = rows.fold(MoneyMath.ZERO) { acc, r -> acc.add(MoneyMath.bd(r.extraPrepayment)) }
            balanceBd = MoneyMath.roundPaiseBd(principalCapBd.subtract(preEmiPrepaid)).max(MoneyMath.ZERO)
        } else {
            balanceBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.principal))
        }

        val effectiveMoratorium = if (isMoratorium) max(0, min(input.moratoriumMonths, 60)) else 0
        if (effectiveMoratorium > 0) {
            for (k in 1..effectiveMoratorium) {
                currentRate = resolveActiveRate(
                    monthIdx, initialRate, isFloating, isMclr, input.mclrResetMonths,
                    isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges
                )
                val openingBd = balanceBd
                val interestBd = MoneyMath.monthlyInterest(balanceBd, MoneyMath.bd(currentRate))
                val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, monthIdx)

                var prepayBd = MoneyMath.ZERO
                for (rule in activePrepayments) {
                    if (matchesPrepaymentMonth(rule, monthIdx) && balanceBd > MoneyMath.ZERO) {
                        val applied = MoneyMath.roundPaiseBd(MoneyMath.bd(rule.amount)).min(balanceBd)
                        prepayBd = MoneyMath.roundPaiseBd(prepayBd.add(applied))
                        balanceBd = MoneyMath.roundPaiseBd(balanceBd.subtract(applied))
                    }
                }

                if (isMoratoriumCompound) {
                    balanceBd = MoneyMath.roundPaiseBd(balanceBd.add(interestBd))
                    moratoriumAccruedBd = MoneyMath.roundPaiseBd(moratoriumAccruedBd.add(interestBd))
                    rows.add(
                        AmortizationRow(
                            monthIdx, mLabel, yIdx, yLabel, openingBd.toDouble(),
                            prepayBd.toDouble(), prepayBd.toDouble(), 0.0,
                            prepayBd.toDouble(), balanceBd.toDouble(), currentRate, "Moratorium"
                        )
                    )
                } else {
                    rows.add(
                        AmortizationRow(
                            monthIdx, mLabel, yIdx, yLabel, openingBd.toDouble(),
                            MoneyMath.roundPaiseBd(interestBd.add(prepayBd)).toDouble(),
                            prepayBd.toDouble(), interestBd.toDouble(),
                            prepayBd.toDouble(), balanceBd.toDouble(), currentRate, "Moratorium"
                        )
                    )
                }
                monthIdx++
            }
        }

        val amortTenure = max(1, input.tenureMonths)
        val balloonBd = if (isBalloon) MoneyMath.percentageOf(balanceBd, MoneyMath.bd(input.balloonPercent)) else MoneyMath.ZERO

        currentRate = resolveActiveRate(
            monthIdx, initialRate, isFloating, isMclr, input.mclrResetMonths,
            isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges
        )
        var currentEmiBd = when {
            isBalloon -> {
                val amortPart = MoneyMath.standardEmiBd(balanceBd.subtract(balloonBd), MoneyMath.bd(currentRate), amortTenure)
                val balloonInt = MoneyMath.monthlyInterest(balloonBd, MoneyMath.bd(currentRate))
                MoneyMath.roundPaiseBd(amortPart.add(balloonInt))
            }
            isStepUp -> {
                val baseLevel = MoneyMath.standardEmiBd(balanceBd, MoneyMath.bd(currentRate), amortTenure)
                val minSafe = MoneyMath.roundPaiseBd(
                    MoneyMath.monthlyInterest(balanceBd, MoneyMath.bd(currentRate))
                        .multiply(BigDecimal("1.05"))
                        .add(BigDecimal("100"))
                )
                MoneyMath.roundPaiseBd(baseLevel.multiply(BigDecimal("0.82"))).max(minSafe)
            }
            isStepDown -> {
                val baseLevel = MoneyMath.standardEmiBd(balanceBd, MoneyMath.bd(currentRate), amortTenure)
                MoneyMath.roundPaiseBd(baseLevel.multiply(BigDecimal("1.22")))
            }
            else -> MoneyMath.standardEmiBd(balanceBd, MoneyMath.bd(currentRate), amortTenure)
        }

        val maxLoops = max(amortTenure + 240, 600)
        var amortStep = 1
        var previousRate = currentRate
        val offsetBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.offsetBalance))

        while (balanceBd > BigDecimal("0.004") && amortStep <= maxLoops) {
            currentRate = resolveActiveRate(
                monthIdx, initialRate, isFloating, isMclr, input.mclrResetMonths,
                isHybrid, input.hybridFixedMonths, input.hybridFloatingRate, sortedRateChanges
            )

            if (abs(currentRate - previousRate) > 0.0001) {
                val remainingMonths = max(1, amortTenure - amortStep + 1)
                currentEmiBd = if (isBalloon) {
                    val amortPart = MoneyMath.standardEmiBd(
                        balanceBd.subtract(balloonBd).max(MoneyMath.ZERO),
                        MoneyMath.bd(currentRate),
                        remainingMonths
                    )
                    val balloonInt = MoneyMath.monthlyInterest(balloonBd, MoneyMath.bd(currentRate))
                    MoneyMath.roundPaiseBd(amortPart.add(balloonInt))
                } else {
                    MoneyMath.standardEmiBd(balanceBd, MoneyMath.bd(currentRate), remainingMonths)
                }
                previousRate = currentRate
            }

            val interval = max(1, input.stepIntervalMonths)
            if ((isStepUp || isStepDown) && amortStep > 1 && (amortStep - 1) % interval == 0) {
                val pctBd = MoneyMath.bd(input.stepPercent).divide(MoneyMath.HUNDRED, MoneyMath.MC)
                currentEmiBd = if (isStepUp) {
                    MoneyMath.roundPaiseBd(currentEmiBd.multiply(MoneyMath.ONE.add(pctBd, MoneyMath.MC)))
                } else {
                    val monthlyInt = MoneyMath.monthlyInterest(balanceBd, MoneyMath.bd(currentRate))
                    val floorExtra = balanceBd.multiply(BigDecimal("0.001")).max(BigDecimal("100"))
                    val minSafeEmi = MoneyMath.roundPaiseBd(monthlyInt.add(floorExtra))
                    MoneyMath.roundPaiseBd(currentEmiBd.multiply(MoneyMath.ONE.subtract(pctBd, MoneyMath.MC))).max(minSafeEmi)
                }
            }

            val openingBd = balanceBd
            val interestBearingBd = if (isOffset) balanceBd.subtract(offsetBd).max(MoneyMath.ZERO) else balanceBd
            var interestBd = MoneyMath.monthlyInterest(interestBearingBd, MoneyMath.bd(currentRate))

            val brokenThisMonth = if (!brokenApplied && brokenPeriodBd > BigDecimal.ZERO) {
                brokenApplied = true
                brokenPeriodBd
            } else {
                MoneyMath.ZERO
            }
            interestBd = MoneyMath.roundPaiseBd(interestBd.add(brokenThisMonth))

            val isBalloonFinal = isBalloon && amortStep == amortTenure
            val hasReduceTenurePrepay = input.prepaymentEnabled && activePrepayments.any { it.mode != PrepaymentMode.REDUCE_EMI.name }
            val isNaturalFinal =
                (!isStepUp && !isStepDown && !hasReduceTenurePrepay && !isOffset && amortStep == amortTenure) || amortStep == maxLoops

            val scheduledPrincipalBd: BigDecimal
            val emiThisMonthBd: BigDecimal

            if (isBalloonFinal || isNaturalFinal || currentEmiBd.subtract(interestBd) >= balanceBd.subtract(BigDecimal("0.01"))) {
                scheduledPrincipalBd = balanceBd
                emiThisMonthBd = MoneyMath.roundPaiseBd(scheduledPrincipalBd.add(interestBd))
            } else {
                val rawPrincipal = MoneyMath.roundPaiseBd(currentEmiBd.subtract(interestBd.subtract(brokenThisMonth)))
                scheduledPrincipalBd = rawPrincipal.max(MoneyMath.ZERO).min(balanceBd)
                emiThisMonthBd = MoneyMath.roundPaiseBd(scheduledPrincipalBd.add(interestBd))
            }

            var closingBd = MoneyMath.roundPaiseBd(balanceBd.subtract(scheduledPrincipalBd))

            var subsidyCreditBd = MoneyMath.ZERO
            if (input.pmaySubsidyEnabled &&
                input.pmaySubsidyAmount > 0.0 &&
                monthIdx == input.pmaySubsidyMonth.coerceAtLeast(1) &&
                closingBd > MoneyMath.ZERO
            ) {
                subsidyCreditBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.pmaySubsidyAmount)).min(closingBd)
                pmayAppliedBd = subsidyCreditBd
                closingBd = MoneyMath.roundPaiseBd(closingBd.subtract(subsidyCreditBd))
                if (closingBd > MoneyMath.ZERO) {
                    val remainingAfter = max(1, amortTenure - amortStep)
                    currentEmiBd = MoneyMath.standardEmiBd(closingBd, MoneyMath.bd(currentRate), remainingAfter)
                }
            }

            var extraPrepayBd = MoneyMath.ZERO
            var reduceEmiTriggered = false
            for (rule in activePrepayments) {
                if (matchesPrepaymentMonth(rule, monthIdx) && closingBd > MoneyMath.ZERO) {
                    val applied = MoneyMath.roundPaiseBd(MoneyMath.bd(rule.amount)).min(closingBd)
                    extraPrepayBd = MoneyMath.roundPaiseBd(extraPrepayBd.add(applied))
                    closingBd = MoneyMath.roundPaiseBd(closingBd.subtract(applied))
                    if (rule.mode == PrepaymentMode.REDUCE_EMI.name) {
                        reduceEmiTriggered = true
                    }
                }
            }

            if (closingBd < BigDecimal("0.01")) closingBd = MoneyMath.ZERO

            val gstOnInterestBd = if (isCreditCardEmi) MoneyMath.percentageOf(interestBd, gstRateBd) else MoneyMath.ZERO
            val phaseLabel = when {
                isBalloonFinal -> "Balloon"
                subsidyCreditBd > MoneyMath.ZERO -> "PMAY Subsidy"
                isCreditCardEmi -> "CC EMI"
                isHybrid && monthIdx <= input.hybridFixedMonths -> "Fixed"
                isHybrid -> "Floating"
                isFloating -> "Floating"
                else -> "EMI"
            }

            val (mLabel, yIdx, yLabel) = LoanCalculationEngine.dateMeta(input.startYear, input.startMonth, monthIdx)
            rows.add(
                AmortizationRow(
                    monthNumber = monthIdx,
                    monthLabel = mLabel,
                    yearGroupIndex = yIdx,
                    yearRangeLabel = yLabel,
                    openingBalance = openingBd.toDouble(),
                    emiPaid = MoneyMath.roundPaiseBd(emiThisMonthBd.add(extraPrepayBd).add(gstOnInterestBd)).toDouble(),
                    principalPaid = MoneyMath.roundPaiseBd(scheduledPrincipalBd.add(extraPrepayBd).add(subsidyCreditBd)).toDouble(),
                    interestPaid = interestBd.toDouble(),
                    extraPrepayment = extraPrepayBd.toDouble(),
                    closingBalance = closingBd.toDouble(),
                    activeRate = currentRate,
                    phaseLabel = phaseLabel,
                    gstOnInterest = gstOnInterestBd.toDouble()
                )
            )

            balanceBd = closingBd
            if (reduceEmiTriggered && balanceBd > MoneyMath.ZERO) {
                val remainingMonths = max(1, amortTenure - amortStep)
                currentEmiBd = MoneyMath.standardEmiBd(balanceBd, MoneyMath.bd(currentRate), remainingMonths)
            }

            monthIdx++
            amortStep++
        }

        return SimulationOutput(
            rows = rows,
            moratoriumExtraInterest = moratoriumAccruedBd.toDouble(),
            brokenPeriodInterest = if (brokenApplied) brokenPeriodBd.toDouble() else 0.0,
            pmaySubsidyApplied = pmayAppliedBd.toDouble()
        )
    }

    private fun matchesPrepaymentMonth(rule: PrepaymentRule, monthIdx: Int): Boolean = if (rule.isRecurring) {
        monthIdx >= rule.startMonth && (monthIdx - rule.startMonth) % max(1, rule.intervalMonths) == 0
    } else {
        monthIdx == rule.startMonth
    }

    private fun resolveActiveRate(
        monthNumber: Int,
        initialRate: Double,
        isFloating: Boolean,
        isMclr: Boolean,
        mclrResetMonths: Int,
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
            val resetInterval = if (isMclr) max(1, mclrResetMonths) else 1
            for (ev in rateChanges) {
                val effectiveAfter = if (isMclr && resetInterval > 1) {
                    ((ev.afterMonth + resetInterval - 1) / resetInterval) * resetInterval
                } else {
                    ev.afterMonth
                }
                if (monthNumber > effectiveAfter) {
                    rate = ev.newAnnualRate.coerceIn(0.0, 45.0)
                }
            }
        }
        return rate
    }
}

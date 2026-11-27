package com.jyotirmoyb.loan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-computed known-answer cross-checks for every core financial engine:
 * EMI (reducing, flat, step-up, balloon, moratorium simple & compound),
 * Prepayment (reduce tenure vs reduce EMI), FD (quarterly compounding & TDS thresholds),
 * RD (IBA formula), SIP with step-up, SWP, XIRR, NPS, EPF, and PPF.
 *
 * Every loan schedule explicitly asserts that the final closing balance is EXACTLY 0.0.
 */
class CalculationCrossCheckTest {

    @Test
    fun emi_reducingBalance_flat_stepUp_balloon_moratorium_allEndAtExactZeroBalance() {
        // 1) REDUCING BALANCE: P = ₹10,00,000, R = 12% p.a. (r = 0.01/mo), N = 12 months
        //    (1.01)^12 = 1.1268250301319697
        //    EMI = 10,00,000 * 0.01 * 1.126825030132 / 0.126825030132 = ₹88,848.79
        //    Month 1: interest = ₹10,000.00, principal = ₹78,848.79, closing = ₹9,21,151.21
        val reducing = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 1000000.0,
                annualRate = 12.0,
                tenureMonths = 12,
                ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD")
            )
        )
        assertTrue(reducing.isValid)
        assertEquals(88848.79, reducing.monthlyEmi, 0.01)
        assertEquals(10000.00, reducing.schedule.first().interestPaid, 0.01)
        assertEquals(78848.79, reducing.schedule.first().principalPaid, 0.01)
        assertEquals(921151.21, reducing.schedule.first().closingBalance, 0.01)
        assertEquals(1000000.0, reducing.totalPrincipal, 0.01)
        assertEquals(0.0, reducing.schedule.last().closingBalance, 0.0)

        // 2) FLAT RATE: P = ₹6,00,000, R = 10% flat, N = 24 months (2 years)
        //    Total flat interest = 6,00,000 * 10% * 2 = ₹1,20,000.00
        //    Monthly principal = 6,00,000 / 24 = ₹25,000.00; Monthly interest = 1,20,000 / 24 = ₹5,000.00
        //    Monthly EMI = ₹30,000.00; Total Payment = ₹7,20,000.00
        val flat = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 600000.0,
                annualRate = 10.0,
                tenureMonths = 24,
                ruleFlags = listOf("RATE_FLAT")
            )
        )
        assertTrue(flat.isValid)
        assertEquals(30000.0, flat.monthlyEmi, 0.01)
        assertEquals(120000.0, flat.totalInterest, 0.01)
        assertEquals(720000.0, flat.totalPayment, 0.01)
        assertEquals(600000.0, flat.totalPrincipal, 0.01)
        assertEquals(0.0, flat.schedule.last().closingBalance, 0.0)

        // 3) STEP-UP EMI: P = ₹10,00,000, R = 12%, N = 60m, step = +10% every 12 months
        //    Base level EMI (60m @12%) = 10,00,000 * 0.01 * 1.01^60 / (1.01^60 - 1) = ₹22,244.45
        //    Initial step-up EMI = 0.82 * 22,244.45 = ₹18,240.45
        //    Year 2 EMI (Month 13) = 18,240.45 * 1.10 = ₹20,064.50
        val stepUp = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 1000000.0,
                annualRate = 12.0,
                tenureMonths = 60,
                ruleFlags = listOf("RATE_FIXED", "EMI_STEP_UP"),
                stepPercent = 10.0,
                stepIntervalMonths = 12
            )
        )
        assertTrue(stepUp.isValid)
        assertEquals(18240.45, stepUp.firstEmi, 0.02)
        assertEquals(20064.50, stepUp.schedule[12].emiPaid, 0.02)
        assertEquals(1000000.0, stepUp.totalPrincipal, 0.01)
        assertEquals(0.0, stepUp.schedule.last().closingBalance, 0.0)

        // 4) BALLOON EMI: P = ₹10,00,000, R = 12%, N = 12m, Balloon = 20% (₹2,00,000)
        //    Amortizing part = ₹8,00,000 -> 12m EMI @12% = 0.8 * 88,848.79 = ₹71,079.03
        //    Monthly interest on ₹2,00,000 balloon = ₹2,000.00
        //    Regular EMI (Months 1..11) = 71,079.03 + 2,000.00 = ₹73,079.03
        //    Month 12 clears remaining regular balance + ₹2,00,000 balloon -> closingBalance == 0.0
        val balloon = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 1000000.0,
                annualRate = 12.0,
                tenureMonths = 12,
                ruleFlags = listOf("RATE_FIXED", "EMI_BALLOON"),
                balloonPercent = 20.0
            )
        )
        assertTrue(balloon.isValid)
        assertEquals(73079.03, balloon.monthlyEmi, 0.02)
        assertEquals("Balloon", balloon.schedule.last().phaseLabel)
        assertTrue(balloon.lastEmi > 270000.0)
        assertEquals(1000000.0, balloon.totalPrincipal, 0.01)
        assertEquals(0.0, balloon.schedule.last().closingBalance, 0.0)

        // 5a) MORATORIUM (SIMPLE): P = ₹10,00,000, R = 12%, 3m moratorium + 12m EMI
        //     Months 1..3 pay ₹10,000/mo simple interest (total ₹30,000); balance stays ₹10,00,000
        //     Months 4..15 pay standard 12m EMI = ₹88,848.79; final balance == 0.0
        val morSimple = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 1000000.0,
                annualRate = 12.0,
                tenureMonths = 12,
                ruleFlags = listOf("RATE_FIXED", "MOR_SIMPLE"),
                moratoriumMonths = 3
            )
        )
        assertTrue(morSimple.isValid)
        assertEquals(15, morSimple.actualTenureMonths)
        assertEquals(10000.0, morSimple.schedule[0].emiPaid, 0.01)
        assertEquals(1000000.0, morSimple.schedule[2].closingBalance, 0.01)
        assertEquals(88848.79, morSimple.firstEmi, 0.01)
        assertEquals(0.0, morSimple.schedule.last().closingBalance, 0.0)

        // 5b) MORATORIUM (COMPOUND): P = ₹10,00,000, R = 12%, 3m compound moratorium + 12m EMI
        //     M1 balance: 10,00,000 * 1.01 = 10,10,000
        //     M2 balance: 10,10,000 * 1.01 = 10,20,100
        //     M3 balance: 10,20,100 * 1.01 = 10,30,301 -> moratoriumExtraInterest = ₹30,301.00
        //     Months 4..15 EMI on ₹10,30,301 @12% for 12m = 10,30,301 * 0.08884878868 = ₹91,541.00
        val morCompound = LoanCalculationEngine.calculate(
            CalculationInput(
                principal = 1000000.0,
                annualRate = 12.0,
                tenureMonths = 12,
                ruleFlags = listOf("RATE_FIXED", "MOR_COMPOUND"),
                moratoriumMonths = 3
            )
        )
        assertTrue(morCompound.isValid)
        assertEquals(15, morCompound.actualTenureMonths)
        assertEquals(30301.0, morCompound.insights.moratoriumExtraInterest, 0.01)
        assertEquals(1030301.0, morCompound.schedule[2].closingBalance, 0.01)
        assertEquals(91541.00, morCompound.firstEmi, 0.02)
        assertEquals(0.0, morCompound.schedule.last().closingBalance, 0.0)
    }

    @Test
    fun prepayment_reduceTenureVsReduceEmi_savesMoreInterestAndClosesAtZero() {
        // P = ₹10,00,000, R = 12% p.a., N = 24m -> Baseline EMI = ₹47,073.47
        // Lump-sum prepayment of ₹2,00,000 at Month 6.
        val baseInput = CalculationInput(
            principal = 1000000.0,
            annualRate = 12.0,
            tenureMonths = 24,
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD")
        )
        val baseline = LoanCalculationEngine.calculate(baseInput)
        assertEquals(47073.47, baseline.monthlyEmi, 0.01)
        assertEquals(0.0, baseline.schedule.last().closingBalance, 0.0)

        val reduceTenure = LoanCalculationEngine.calculate(
            baseInput.copy(
                prepaymentEnabled = true,
                prepayments = listOf(PrepaymentRule(startMonth = 6, amount = 200000.0, mode = PrepaymentMode.REDUCE_TENURE.name))
            )
        )
        val reduceEmi = LoanCalculationEngine.calculate(
            baseInput.copy(
                prepaymentEnabled = true,
                prepayments = listOf(PrepaymentRule(startMonth = 6, amount = 200000.0, mode = PrepaymentMode.REDUCE_EMI.name))
            )
        )

        // Both must amortize full ₹10,00,000 principal and end at exact 0.0 closing balance
        assertEquals(1000000.0, reduceTenure.totalPrincipal, 0.01)
        assertEquals(0.0, reduceTenure.schedule.last().closingBalance, 0.0)
        assertEquals(1000000.0, reduceEmi.totalPrincipal, 0.01)
        assertEquals(0.0, reduceEmi.schedule.last().closingBalance, 0.0)

        // Reduce Tenure finishes strictly earlier (< 24 months) and saves strictly more interest than Reduce EMI
        assertTrue(reduceTenure.actualTenureMonths < 24)
        assertEquals(24, reduceEmi.actualTenureMonths)
        assertTrue(reduceEmi.schedule[6].emiPaid < baseline.monthlyEmi)
        assertTrue(reduceTenure.insights.prepaymentImpact!!.interestSaved > reduceEmi.insights.prepaymentImpact!!.interestSaved)
    }

    @Test
    fun fixedDeposit_quarterlyCompounding_andTdsThresholds_matchHandCalculations() {
        val tdsCfg = TdsConfig(
            generalThreshold = 50000.0,
            seniorThreshold = 100000.0,
            rateWithPanPercent = 10.0,
            rateWithoutPanPercent = 20.0,
            cessPercent = 4.0
        )

        // 1) Quarterly compounding: P = ₹1,00,000, R = 8%, 24 months (8 quarters, r/4 = 2%)
        //    Year 1: 1,00,000 * 1.02^4 = 1,00,000 * 1.08243216 = ₹1,08,243.22 (int = ₹8,243.22 < ₹50k -> TDS = ₹0)
        //    Year 2: 1,08,243.22 * 1.02^4 = ₹1,17,165.94 (int = ₹8,922.72 < ₹50k -> TDS = ₹0)
        val fd2Yr = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 100000.0,
                annualRatePercent = 8.0,
                tenureMonths = 24,
                compounding = CompoundingFrequency.QUARTERLY
            ),
            tdsCfg
        )
        assertTrue(fd2Yr.isValid)
        assertEquals(117165.94, fd2Yr.maturityAmount, 0.02)
        assertEquals(17165.94, fd2Yr.totalInterest, 0.02)
        assertEquals(0.0, fd2Yr.totalEstimatedTds, 0.001)

        // 2) TDS threshold checks on P = ₹10,00,000, R = 8%, 12 months (quarterly):
        //    Year 1 interest = 10,00,000 * (1.02^4 - 1) = ₹82,432.16
        //    General (threshold ₹50,000) WITH PAN (10%): TDS = ₹8,243.22
        val genPan = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 8.0,
                tenureMonths = 12,
                isSeniorCitizen = false,
                hasPan = true
            ),
            tdsCfg
        )
        assertEquals(82432.16, genPan.totalInterest, 0.01)
        assertEquals(8243.22, genPan.totalEstimatedTds, 0.01)

        //    General WITHOUT PAN (20%): TDS = ₹16,486.43
        val genNoPan = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 8.0,
                tenureMonths = 12,
                isSeniorCitizen = false,
                hasPan = false
            ),
            tdsCfg
        )
        assertEquals(16486.43, genNoPan.totalEstimatedTds, 0.01)

        //    Senior (threshold ₹1,00,000, 0% bonus so same ₹82,432.16 interest <= ₹1,00,000): TDS = ₹0.00
        val senior = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 8.0,
                seniorBonusPercent = 0.0,
                tenureMonths = 12,
                isSeniorCitizen = true,
                hasPan = true
            ),
            tdsCfg
        )
        assertEquals(0.0, senior.totalEstimatedTds, 0.001)
    }

    @Test
    fun recurringDeposit_ibaQuarterlyFormula_matchesHandSum() {
        // P = ₹10,000/mo, R = 8% p.a., N = 12 months
        // IBA formula: M = sum_{k=1..12} 10,000 * (1.02)^((13 - k) / 3)
        // Geometric series with r_m = 1.02^(1/3) = 1.006622709560113:
        //   M = 10,000 * r_m * (1.02^4 - 1) / (r_m - 1) = ₹1,25,293.26
        //   Total deposited = ₹1,20,000.00; Total interest = ₹5,293.26; EAR = (1.02^4 - 1)*100 = 8.24%
        val rd = RecurringDepositEngine.calculate(
            RecurringDepositInput(
                monthlyDeposit = 10000.0,
                annualRatePercent = 8.0,
                tenureMonths = 12,
                isSeniorCitizen = false
            ),
            TdsConfig(50000.0, 100000.0, 10.0, 20.0, 4.0)
        )
        assertTrue(rd.isValid)
        assertEquals(120000.0, rd.totalDeposited, 0.01)
        assertEquals(125293.26, rd.maturityAmount, 0.02)
        assertEquals(5293.26, rd.totalInterest, 0.02)
        assertEquals(8.24, rd.effectiveYieldPercent, 0.01)
    }

    @Test
    fun sipWithStepUp_matchesHandCompoundedAnnuityDue() {
        // Monthly SIP = ₹10,000, Expected return = 12% p.a. (1%/month), Tenure = 2 yrs, Step-up = 10%, Inflation = 0%
        // Annuity-due factor for 12 months @1%/mo: S_12 = 1.01 * (1.01^12 - 1) / 0.01 = 12.8093280433
        // Year 1 (₹10,000/mo): closing = 10,000 * 12.8093280433 = ₹1,28,093.28
        // Year 2 (₹11,000/mo): Y1 corpus grows by 1.01^12 = 1.126825030132 -> ₹1,44,338.72
        //                      Y2 SIPs grow to 11,000 * 12.8093280433     -> ₹1,40,902.61
        //                      Combined maturity = ₹2,85,241.32
        // Total invested = 12*10,000 + 12*11,000 = ₹2,52,000.00; Estimated returns = ₹33,241.32
        val sip = SipLumpsumEngine.calculateSip(
            SipInput(
                monthlySip = 10000.0,
                expectedReturnPercent = 12.0,
                tenureYears = 2,
                annualStepUpPercent = 10.0,
                inflationPercent = 0.0
            )
        )
        assertTrue(sip.isValid)
        assertEquals(252000.0, sip.totalInvested, 0.01)
        assertEquals(11000.0, sip.finalMonthlySip, 0.01)
        assertEquals(128093.28, sip.yearlyRows[0].closingValue, 0.02)
        assertEquals(285241.32, sip.maturityAmount, 0.02)
        assertEquals(33241.32, sip.estimatedReturns, 0.02)
    }

    @Test
    fun swp_perpetuityAndDepletion_matchExactHandCalculations() {
        // 1) Exact Perpetuity: ₹10,00,000 @ 12% p.a. (1%/month), withdrawing ₹10,000/mo for 5 years
        //    Monthly gain = ₹10,000 == Monthly withdrawal -> balance stays ₹10,00,000.00 for all 60 months!
        val perpetuity = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 1000000.0,
                monthlyWithdrawal = 10000.0,
                expectedReturnPercent = 12.0,
                tenureYears = 5
            )
        )
        assertTrue(perpetuity.isValid)
        assertFalse(perpetuity.corpusDepleted)
        assertEquals(60, perpetuity.monthsLasted)
        assertEquals(600000.0, perpetuity.totalWithdrawn, 0.01)
        assertEquals(1000000.0, perpetuity.finalBalance, 0.01)
        assertEquals(10000.0, perpetuity.perpetuityMonthlyWithdrawal, 0.01)

        // 2) Exact Depletion: ₹1,00,000 @ 0% return, withdrawing ₹10,000/mo over 2-year horizon (24m)
        //    Lasts exactly 10 months; final balance = ₹0.00; sustainable flat withdrawal = 1,00,000 / 24 = ₹4,166.67
        val depleting = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 100000.0,
                monthlyWithdrawal = 10000.0,
                expectedReturnPercent = 0.0,
                tenureYears = 2
            )
        )
        assertTrue(depleting.isValid)
        assertTrue(depleting.corpusDepleted)
        assertEquals(10, depleting.monthsLasted)
        assertEquals(100000.0, depleting.totalWithdrawn, 0.01)
        assertEquals(0.0, depleting.finalBalance, 0.01)
        assertEquals(4166.67, depleting.sustainableMonthlyWithdrawal, 0.01)
    }

    @Test
    fun xirr_singleYearAndMultiFlow_convergeToExactHandRates() {
        // 1) Exact 10.00% over 365 days: -1,00,000 on 2025-01-01 -> +1,10,000 on 2026-01-01
        val oneYear = XirrEngine.calculate(
            listOf(
                CashFlowEntry(1L, "2025-01-01", -100000.0),
                CashFlowEntry(2L, "2026-01-01", 110000.0)
            )
        )
        assertTrue(oneYear.isValid)
        assertEquals(10.0, oneYear.xirrPercent, 0.01)

        // 2) Exact 12.00% over two 365-day years (non-leap 2025..2027):
        //    1,00,000 * 1.12^2 + 1,00,000 * 1.12 = 1,25,440 + 1,12,000 = ₹2,37,440
        val multi = XirrEngine.calculate(
            listOf(
                CashFlowEntry(1L, "2025-01-01", -100000.0),
                CashFlowEntry(2L, "2026-01-01", -100000.0),
                CashFlowEntry(3L, "2027-01-01", 237440.0)
            )
        )
        assertTrue(multi.isValid)
        assertEquals(12.0, multi.xirrPercent, 0.01)
        assertEquals(200000.0, multi.totalInvested, 0.01)
        assertEquals(37440.0, multi.netProfit, 0.01)
    }

    @Test
    fun nps_compounding_minAnnuityRule_andSmallCorpusFullExit() {
        // 1) Small corpus (<= ₹5,00,000): Age 58->60 (24m), ₹10,000/mo, 12% (1%/mo), 0% step-up
        //    Annuity-due 24m @1%: 10,000 * 1.01 * (1.01^24 - 1) / 0.01 = 10,000 * 1.01 * 26.97346485 = ₹2,72,431.99
        //    Since ₹2,72,431.99 <= ₹5,00,000 -> eligibleForFullLumpSumExit == true
        val small = NpsEngine.calculate(
            NpsInput(
                currentAge = 58,
                retirementAge = 60,
                monthlyContribution = 10000.0,
                annualStepUpPercent = 0.0,
                existingCorpus = 0.0,
                expectedReturnPercent = 12.0,
                annuityAllocationPercent = 0.0,
                annuityRatePercent = 6.0
            )
        )
        assertTrue(small.isValid)
        assertEquals(240000.0, small.totalInvested, 0.01)
        assertEquals(272431.99, small.projectedCorpus, 0.05)
        assertTrue(small.eligibleForFullLumpSumExit)
        assertEquals(0.0, small.annuityPercentApplied, 0.001)

        // 2) Corpus > ₹5,00,000 enforces 40% min annuity:
        //    Existing ₹10,00,000, ₹0/mo, Age 59->60 (12m), 12% (1%/mo) -> 10,00,000 * 1.01^12 = ₹11,26,825.03
        //    Requested annuity 20% -> clamped to 40%: annuity = ₹4,50,730.01, lumpSum = ₹6,76,095.02
        //    Monthly pension @ 6% p.a. = (4,50,730.01 * 6%) / 12 = ₹2,253.65
        val large = NpsEngine.calculate(
            NpsInput(
                currentAge = 59,
                retirementAge = 60,
                monthlyContribution = 0.0,
                annualStepUpPercent = 0.0,
                existingCorpus = 1000000.0,
                expectedReturnPercent = 12.0,
                annuityAllocationPercent = 20.0,
                annuityRatePercent = 6.0
            )
        )
        assertTrue(large.isValid)
        assertFalse(large.eligibleForFullLumpSumExit)
        assertEquals(1126825.03, large.projectedCorpus, 0.05)
        assertEquals(40.0, large.annuityPercentApplied, 0.001)
        assertEquals(450730.01, large.annuityCorpus, 0.05)
        assertEquals(676095.02, large.lumpSumWithdrawal, 0.05)
        assertEquals(2253.65, large.estimatedMonthlyPension, 0.02)
    }

    @Test
    fun epf_monthlyRunningBalanceAndEpsCap_matchHandCalculation() {
        // Age 57->58 (1 yr), Basic+DA = ₹50,000, Existing = ₹1,00,000, Growth = 0%, Rate = 12% (1%/mo)
        // Employee EPF/mo = 12% of 50,000 = ₹6,000.00
        // EPS/mo = min(8.33% of min(50,000, 15,000), 1,250) = ₹1,249.50 (annual EPS = ₹14,994.00)
        // Employer EPF/mo = 12% of 50,000 - 1,249.50 = ₹4,750.50
        // Monthly EPF credit = 6,000 + 4,750.50 = ₹10,750.50 (annual = ₹1,29,006.00)
        // Running balance sum (m=1..12) = 12 * 1,00,000 + 78 * 10,750.50 = 12,00,000 + 8,38,539 = ₹20,38,539.00
        // Annual interest @ 1%/mo = ₹20,385.39
        // Closing EPF balance = 1,00,000 + 1,29,006 + 20,385.39 = ₹2,49,391.39
        val epf = EpfEngine.calculate(
            EpfInput(
                currentAge = 57,
                retirementAge = 58,
                monthlyBasicPlusDa = 50000.0,
                existingEpfBalance = 100000.0,
                annualSalaryGrowthPercent = 0.0,
                vpfExtraMonthly = 0.0,
                applyWageCeiling = false,
                epfInterestRatePercent = 12.0
            )
        )
        assertTrue(epf.isValid)
        assertEquals(6000.0, epf.firstMonthEmployeeEpf, 0.01)
        assertEquals(1249.50, epf.firstMonthEmployerEps, 0.01)
        assertEquals(4750.50, epf.firstMonthEmployerEpf, 0.01)
        assertEquals(14994.0, epf.epsTotalContribution, 0.01)
        assertEquals(20385.39, epf.totalInterestEarned, 0.02)
        assertEquals(249391.39, epf.projectedEpfCorpus, 0.02)
    }

    @Test
    fun ppf_annualCompoundingAndYearlyCap_matchHandCalculation() {
        val cfg = SmallSavingsConfig.fromResolvedConfig(emptyMap())
        // Annual deposit = ₹1,50,000, rate = 7.1%, tenure = 15 years
        // Year 1: (0 + 1,50,000) * 7.1% = ₹10,650.00 -> closing = ₹1,60,650.00
        // Year 2: (1,60,650 + 1,50,000) * 7.1% = ₹22,056.15 -> closing = ₹3,32,706.15
        // Year 3: (3,32,706.15 + 1,50,000) * 7.1% = ₹34,272.14 -> closing = ₹5,16,978.29
        // 15-year closed form: 1,50,000 * 1.071 * (1.071^15 - 1) / 0.071 = ₹40,68,209.16
        val ppf = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PPF, contributionAmount = 150000.0),
            cfg
        )
        assertTrue(ppf.isValid)
        assertEquals(2250000.0, ppf.totalInvested, 0.01)
        assertEquals(160650.00, ppf.yearlyRows[0].closingBalance, 0.01)
        assertEquals(332706.15, ppf.yearlyRows[1].closingBalance, 0.01)
        assertEquals(516978.29, ppf.yearlyRows[2].closingBalance, 0.01)
        assertEquals(4068209.16, ppf.maturityAmount, 0.50)

        // Entering above ₹1,50,000 triggers cap warning and caps deposit at ₹1,50,000/yr
        val capped = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PPF, contributionAmount = 200000.0),
            cfg
        )
        assertNotNull(capped.capWarning)
        assertEquals(2250000.0, capped.totalInvested, 0.01)
    }
}

package com.jyotirmoyb.loan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known-answer tests cross-checked against standard Indian bank (SBI / HDFC) reducing-balance
 * EMI formulas for every India loan variant, GST rule, PMAY subsidy, broken-period interest,
 * gold auction-risk threshold, and prepayment mode.
 */
class IndiaLoanVariantsKnownAnswerTest {

    @Test
    fun homeLoan_fixedSbiBenchmark_50LakhAt8Point5PercentFor20Years() {
        // SBI/HDFC standard benchmark: ₹50,00,000 @ 8.50% p.a. for 240 months -> EMI = ₹43,391.16
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL"),
            principal = 5000000.0,
            assetValue = 6500000.0,
            annualRate = 8.5,
            tenureMonths = 240,
            processingFeePercent = 0.5,
            gstOnFeesEnabled = true,
            gstRatePercent = 18.0
        )
        val res = LoanCalculationEngine.calculate(input)
        assertTrue(res.isValid)
        assertEquals(43391.16, res.monthlyEmi, 0.50)
        assertEquals(5413878.0, res.totalInterest, 20.0)
        assertEquals(25000.0, res.processingFeeAmount, 0.01)
        assertEquals(4500.0, res.gstOnFeeAmount, 0.01) // 18% GST on ₹25,000 processing fee
        assertEquals(0.0, res.schedule.last().closingBalance, 0.0001)
        assertTrue(res.interestToPrincipalPercent > 108.0)
        assertNotNull(res.insights.taxBenefitSummary)
        assertEquals(200000.0, res.insights.taxBenefitSummary!!.firstYearInterestEligible, 0.01)
    }

    @Test
    fun homeLoan_eblrRepoCutAndMclrResetCadence() {
        val eblrBase = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_EBLR", "EMI_STANDARD", "DISB_FULL"),
            principal = 4000000.0,
            assetValue = 5500000.0,
            benchmarkRatePercent = 6.5,
            spreadPercent = 2.0,
            annualRate = 8.5,
            tenureMonths = 240
        )
        val eblrWithRepoCut = eblrBase.copy(
            rateChanges = listOf(RateChangeEvent(afterMonth = 12, newAnnualRate = 8.0))
        )
        val baseRes = LoanCalculationEngine.calculate(eblrBase)
        val cutRes = LoanCalculationEngine.calculate(eblrWithRepoCut)
        assertTrue(cutRes.isValid)
        assertEquals(8.5, cutRes.schedule[11].activeRate, 0.001)
        assertEquals(8.0, cutRes.schedule[12].activeRate, 0.001) // Immediate EBLR reset after month 12
        assertTrue(cutRes.totalInterest < baseRes.totalInterest)
        assertEquals(0.0, cutRes.schedule.last().closingBalance, 0.0001)

        // Legacy MCLR with 6-month reset cadence: rate change at month 7 takes effect only after month 12 boundary
        val mclrInput = eblrBase.copy(
            ruleFlags = listOf("RATE_MCLR", "EMI_STANDARD", "DISB_FULL"),
            mclrResetMonths = 6,
            rateChanges = listOf(RateChangeEvent(afterMonth = 7, newAnnualRate = 8.0))
        )
        val mclrRes = LoanCalculationEngine.calculate(mclrInput)
        assertTrue(mclrRes.isValid)
        assertEquals(8.5, mclrRes.schedule[8].activeRate, 0.001) // Month 9 still at old rate before 12m reset
        assertEquals(8.0, mclrRes.schedule[12].activeRate, 0.001) // Month 13 resets to 8.0%
        assertEquals(0.0, mclrRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun homeLoan_pmaySubsidyCreditReducesPrincipalAndTotalInterest() {
        val base = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_EBLR", "EMI_STANDARD"),
            principal = 2500000.0,
            assetValue = 3500000.0,
            annualRate = 8.5,
            tenureMonths = 240,
            pmaySubsidyEnabled = false
        )
        val withPmay = base.copy(
            pmaySubsidyEnabled = true,
            pmaySubsidyAmount = 180000.0,
            pmaySubsidyMonth = 6
        )
        val baseRes = LoanCalculationEngine.calculate(base)
        val pmayRes = LoanCalculationEngine.calculate(withPmay)

        assertTrue(pmayRes.isValid)
        assertEquals(180000.0, pmayRes.insights.pmaySubsidyApplied, 0.01)
        assertTrue(pmayRes.insights.pmayInterestSaved > 150000.0)
        assertEquals("PMAY Subsidy", pmayRes.schedule[5].phaseLabel)
        assertTrue(pmayRes.totalInterest < baseRes.totalInterest)
        assertEquals(0.0, pmayRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun homeLoan_tranchePreEmiWithPartPaymentDuringPreEmi() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_EBLR", "EMI_STANDARD", "DISB_TRANCHE_PRE_EMI"),
            principal = 3000000.0,
            assetValue = 4500000.0,
            annualRate = 8.5,
            tenureMonths = 180,
            tranches = listOf(
                TrancheEntry(1, 1000000.0),
                TrancheEntry(7, 1000000.0),
                TrancheEntry(13, 1000000.0)
            ),
            prepaymentEnabled = true,
            prepayments = listOf(
                PrepaymentRule(startMonth = 6, amount = 200000.0, mode = PrepaymentMode.REDUCE_TENURE.name)
            )
        )
        val res = LoanCalculationEngine.calculate(input)
        assertTrue(res.isValid)
        assertEquals("Pre-EMI", res.schedule[5].phaseLabel)
        assertEquals(200000.0, res.schedule[5].extraPrepayment, 0.01)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun personalLoan_flatVsReducingEarAndFlexiOverdraft() {
        val flatInput = CalculationInput(
            categoryId = "personal",
            ruleFlags = listOf("FAC_TERM", "RATE_FLAT"),
            principal = 500000.0,
            assetValue = 0.0,
            annualRate = 10.0,
            tenureMonths = 36
        )
        val flatRes = LoanCalculationEngine.calculate(flatInput)
        assertTrue(flatRes.isValid)
        // 10% flat for 3 years on 5L = ₹1,50,000 total interest; EMI = ₹6,50,000 / 36 = ₹18,055.56
        assertEquals(150000.0, flatRes.totalInterest, 0.5)
        assertEquals(18055.56, flatRes.monthlyEmi, 0.5)
        val cmp = flatRes.insights.flatVsReducing!!
        assertTrue("10% flat over 36m should have ~17.9% reducing EAR", cmp.effectiveAnnualRatePercent in 17.5..18.5)
        assertEquals(0.0, flatRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun vehicleLoan_evAndBalloonOption() {
        val balloonInput = CalculationInput(
            categoryId = "auto",
            ruleFlags = listOf("VEH_CAR", "EMI_BALLOON"),
            principal = 1000000.0,
            assetValue = 1300000.0,
            annualRate = 9.25,
            tenureMonths = 60,
            balloonPercent = 25.0
        )
        val res = LoanCalculationEngine.calculate(balloonInput)
        assertTrue(res.isValid)
        assertEquals("Balloon", res.schedule.last().phaseLabel)
        assertTrue(res.lastEmi > 250000.0)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun educationLoan_simpleVsCompoundMoratoriumAndSec80eTaxBenefit() {
        val simple = CalculationInput(
            categoryId = "education",
            ruleFlags = listOf("EDU_INDIA", "MOR_SIMPLE", "DISB_FULL"),
            principal = 1200000.0,
            assetValue = 0.0,
            annualRate = 9.5,
            tenureMonths = 84,
            moratoriumMonths = 12,
            moratoriumCompound = false,
            taxBracketPercent = 30.0,
            section80eMaxYears = 8
        )
        val compound = simple.copy(
            ruleFlags = listOf("EDU_ABROAD", "MOR_COMPOUND", "DISB_FULL"),
            moratoriumCompound = true
        )
        val simpleRes = LoanCalculationEngine.calculate(simple)
        val compoundRes = LoanCalculationEngine.calculate(compound)

        assertTrue(simpleRes.isValid && compoundRes.isValid)
        assertTrue(compoundRes.firstEmi > simpleRes.firstEmi)
        assertTrue(compoundRes.totalPayment > simpleRes.totalPayment)
        assertNotNull(simpleRes.insights.taxBenefitSummary)
        assertTrue(simpleRes.insights.taxBenefitSummary!!.sectionTitle.contains("80E"))
        assertEquals(0.0, simpleRes.schedule.last().closingBalance, 0.0001)
        assertEquals(0.0, compoundRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun goldLoan_emiInterestOnlyBulletAndAuctionRiskAlert() {
        // Bullet gold loan starting at 72% LTV @ 12% for 12m: accrued maturity pushes effective LTV above 75% threshold
        val bulletInput = CalculationInput(
            categoryId = "gold",
            ruleFlags = listOf("ASSET_GOLD_BULLET"),
            principal = 720000.0,
            assetValue = 1000000.0,
            annualRate = 12.0,
            tenureMonths = 12,
            goldAuctionRiskLtvPercent = 75.0
        )
        val bulletRes = LoanCalculationEngine.calculate(bulletInput)
        assertTrue(bulletRes.isValid)
        assertEquals(0.0, bulletRes.monthlyEmi, 0.01)
        assertTrue(bulletRes.insights.goldSummary!!.isAuctionRiskAlert)
        assertEquals(0.0, bulletRes.schedule.last().closingBalance, 0.0001)

        // Conservative 50% LTV interest-only gold loan should NOT trigger auction risk
        val safeIo = bulletInput.copy(
            ruleFlags = listOf("ASSET_GOLD_INTEREST_ONLY"),
            principal = 500000.0
        )
        val safeIoRes = LoanCalculationEngine.calculate(safeIo)
        assertTrue(safeIoRes.isValid)
        assertEquals(5000.0, safeIoRes.monthlyEmi, 0.01)
        assertFalse(safeIoRes.insights.goldSummary!!.isAuctionRiskAlert)
        assertEquals(0.0, safeIoRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun creditCardEmi_applies18PercentGstOnProcessingFeeAndEveryMonthlyInterest() {
        val ccInput = CalculationInput(
            categoryId = "credit_card_emi",
            ruleFlags = listOf("CC_EMI", "EMI_STANDARD"),
            principal = 100000.0,
            assetValue = 0.0,
            annualRate = 16.0,
            tenureMonths = 12,
            processingFeePercent = 2.0,
            gstOnFeesEnabled = false, // CC EMI must ALWAYS apply GST on fee + interest
            gstRatePercent = 18.0
        )
        val res = LoanCalculationEngine.calculate(ccInput)
        assertTrue(res.isValid)
        // Processing fee = ₹2,000, GST on fee = 18% of ₹2,000 = ₹360
        assertEquals(2000.0, res.processingFeeAmount, 0.01)
        assertEquals(360.0, res.gstOnFeeAmount, 0.01)
        // GST on interest = 18% of totalInterest (within minor per-row rounding)
        assertEquals(res.totalInterest * 0.18, res.gstOnInterestAmount, 1.0)
        assertTrue(res.schedule.all { it.gstOnInterest >= 0.0 })
        assertEquals(0.0, res.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun proRataFirstMonth_brokenPeriodInterestAppliesWhenStartDayDiffersFromEmiDay() {
        val standard = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD"),
            principal = 3000000.0,
            assetValue = 4000000.0,
            annualRate = 9.0,
            tenureMonths = 120,
            startDay = 20,
            emiDayOfMonth = 5,
            proRataFirstMonthEnabled = false
        )
        val proRata = standard.copy(proRataFirstMonthEnabled = true)

        val stdRes = LoanCalculationEngine.calculate(standard)
        val proRes = LoanCalculationEngine.calculate(proRata)

        assertTrue(proRes.isValid)
        // 15 broken days on 30L @ 9% (365-day basis) = 30,00,000 * 0.09 * 15 / 365 = ₹11,095.89
        assertEquals(11095.89, proRes.brokenPeriodInterest, 1.0)
        assertTrue(proRes.schedule.first().interestPaid > stdRes.schedule.first().interestPaid)
        assertEquals(0.0, proRes.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun prepayment_reduceTenureVsReduceEmi_andRecurringPrepayment() {
        val base = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_EBLR", "EMI_STANDARD"),
            principal = 5000000.0,
            assetValue = 7000000.0,
            annualRate = 8.5,
            tenureMonths = 240,
            processingFeePercent = 0.0
        )
        val baseRes = LoanCalculationEngine.calculate(base)

        val yearlyReduceTenure = LoanCalculationEngine.calculate(
            base.copy(
                prepaymentEnabled = true,
                prepayments = listOf(
                    PrepaymentRule(
                        startMonth = 12,
                        amount = 200000.0,
                        isRecurring = true,
                        intervalMonths = 12,
                        mode = PrepaymentMode.REDUCE_TENURE.name
                    )
                )
            )
        )
        val oneTimeReduceEmi = LoanCalculationEngine.calculate(
            base.copy(
                prepaymentEnabled = true,
                prepayments = listOf(
                    PrepaymentRule(
                        startMonth = 12,
                        amount = 500000.0,
                        isRecurring = false,
                        mode = PrepaymentMode.REDUCE_EMI.name
                    )
                )
            )
        )
        val yearlyReduceEmi = LoanCalculationEngine.calculate(
            base.copy(
                prepaymentEnabled = true,
                prepayments = listOf(
                    PrepaymentRule(
                        startMonth = 12,
                        amount = 200000.0,
                        isRecurring = true,
                        intervalMonths = 12,
                        mode = PrepaymentMode.REDUCE_EMI.name
                    )
                )
            )
        )

        assertTrue(yearlyReduceTenure.isValid && oneTimeReduceEmi.isValid && yearlyReduceEmi.isValid)
        assertEquals(240, oneTimeReduceEmi.actualTenureMonths)
        assertTrue(oneTimeReduceEmi.schedule[12].emiPaid < baseRes.monthlyEmi)
        assertTrue(yearlyReduceTenure.actualTenureMonths < yearlyReduceEmi.actualTenureMonths)
        assertTrue(yearlyReduceTenure.totalInterest < yearlyReduceEmi.totalInterest)
        assertTrue(yearlyReduceEmi.totalInterest < baseRes.totalInterest)
        assertTrue(yearlyReduceTenure.insights.prepaymentImpact!!.monthsSaved > 60)
        assertEquals(0.0, yearlyReduceTenure.schedule.last().closingBalance, 0.0001)
        assertEquals(0.0, oneTimeReduceEmi.schedule.last().closingBalance, 0.0001)
        assertEquals(0.0, yearlyReduceEmi.schedule.last().closingBalance, 0.0001)
    }

    @Test
    fun currencyFormatter_indiaDigitGroupingAndLakhCroreShortLabels() {
        assertEquals("₹12,34,567", CurrencyFormatter.format(1234567.0, CurrencyCode.INR, false))
        assertEquals("28.0 Lakh", CurrencyFormatter.formatShortLabel(2800000.0, CurrencyCode.INR))
        assertEquals("1.2 Cr", CurrencyFormatter.formatShortLabel(12000000.0, CurrencyCode.INR))
        assertEquals("1.25 Cr", CurrencyFormatter.formatShortLabel(12500000.0, CurrencyCode.INR))
        assertEquals("Rs.12,34,567", CurrencyFormatter.format(1234567.0, CurrencyCode.NPR, false))
        assertEquals("$1,234,567", CurrencyFormatter.format(1234567.0, CurrencyCode.USD, false))
    }
}

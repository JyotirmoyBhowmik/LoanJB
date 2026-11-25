package com.jyotirmoyb.loan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanCalculationEngineTest {

    private val homeCategory = LoanCategory(
        id = "home",
        name = "Home Loan",
        shortName = "Home",
        tenureMinMonths = 60,
        tenureMaxMonths = 360,
        ltvMaxPercent = 80.0,
        secured = true
    )

    @Test
    fun standardFixedEmi_formulaAndExactZeroClosingBalance() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL"),
            principal = 2800000.0,
            assetValue = 4000000.0,
            annualRate = 10.5,
            tenureMonths = 72
        )
        val result = LoanCalculationEngine.calculate(input, homeCategory)
        assertTrue(result.isValid)
        // Standard EMI for 28L @ 10.5% for 72 months = 52,580.66 approx
        val expectedEmi = LoanCalculationEngine.standardEmi(2800000.0, 10.5, 72)
        assertEquals(expectedEmi, result.monthlyEmi, 0.01)
        assertEquals(72, result.schedule.size)
        assertEquals(0.0, result.schedule.last().closingBalance, 0.001)
        assertEquals(2800000.0, result.totalPrincipal, 0.01)
    }

    @Test
    fun floatingRate_rateChangeTimelineAndSensitivity() {
        val base = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD"),
            principal = 5000000.0,
            assetValue = 7000000.0,
            annualRate = 9.0,
            tenureMonths = 240
        )
        val withDrop = base.copy(rateChanges = listOf(RateChangeEvent(afterMonth = 36, newAnnualRate = 8.0)))

        val baseRes = LoanCalculationEngine.calculate(base, homeCategory)
        val dropRes = LoanCalculationEngine.calculate(withDrop, homeCategory)

        assertTrue(dropRes.isValid)
        assertTrue(dropRes.totalInterest < baseRes.totalInterest)
        assertEquals(5, dropRes.insights.sensitivityRows.size) // -2, -1, 0, +1, +2
        assertEquals(0.0, dropRes.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun hybridRate_switchesRateAfterFixedMonths() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_HYBRID", "EMI_STANDARD"),
            principal = 3000000.0,
            assetValue = 4000000.0,
            annualRate = 8.5,
            hybridFixedMonths = 24,
            hybridFloatingRate = 10.0,
            tenureMonths = 120
        )
        val res = LoanCalculationEngine.calculate(input, homeCategory)
        assertTrue(res.isValid)
        assertEquals(8.5, res.schedule[23].activeRate, 0.001)
        assertEquals(10.0, res.schedule[24].activeRate, 0.001)
        assertEquals("Fixed", res.schedule[0].phaseLabel)
        assertEquals("Floating", res.schedule[24].phaseLabel)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun stepUpAndStepDownEmi_closeAtExactZero() {
        val stepUp = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED", "EMI_STEP_UP"),
            principal = 2000000.0,
            assetValue = 3000000.0,
            annualRate = 9.0,
            tenureMonths = 180,
            stepPercent = 8.0,
            stepIntervalMonths = 12,
            isStepDown = false
        )
        val upRes = LoanCalculationEngine.calculate(stepUp, homeCategory)
        assertTrue(upRes.isValid)
        assertTrue(upRes.schedule[12].emiPaid > upRes.schedule[0].emiPaid)
        assertEquals(0.0, upRes.schedule.last().closingBalance, 0.001)

        val stepDown = stepUp.copy(ruleFlags = listOf("RATE_FIXED", "EMI_STEP_DOWN"), isStepDown = true)
        val downRes = LoanCalculationEngine.calculate(stepDown, homeCategory)
        assertTrue(downRes.isValid)
        assertTrue(downRes.schedule[12].emiPaid < downRes.schedule[0].emiPaid)
        assertEquals(0.0, downRes.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun offsetAccount_savesInterestVsStandard() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_OFFSET"),
            principal = 4000000.0,
            assetValue = 5500000.0,
            annualRate = 9.0,
            tenureMonths = 180,
            offsetBalance = 800000.0
        )
        val res = LoanCalculationEngine.calculate(input, homeCategory)
        assertTrue(res.isValid)
        assertTrue(res.insights.offsetInterestSaved > 100000.0)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun tranchePreEmi_chargesInterestOnlyBeforeHandover() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_TRANCHE_PRE_EMI"),
            principal = 3000000.0,
            assetValue = 4000000.0,
            annualRate = 9.0,
            tenureMonths = 120,
            tranches = listOf(
                TrancheEntry(1, 1000000.0),
                TrancheEntry(7, 1000000.0),
                TrancheEntry(13, 1000000.0)
            )
        )
        val res = LoanCalculationEngine.calculate(input, homeCategory)
        assertTrue(res.isValid)
        assertEquals("Pre-EMI", res.schedule[0].phaseLabel)
        assertEquals(0.0, res.schedule[0].principalPaid, 0.001)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun moratorium_compoundCapitalizesMoreThanSimple() {
        val simpleInput = CalculationInput(
            categoryId = "education",
            ruleFlags = listOf("MOR_SIMPLE", "DISB_FULL"),
            principal = 1500000.0,
            assetValue = 0.0,
            annualRate = 10.0,
            tenureMonths = 84,
            moratoriumMonths = 12,
            moratoriumCompound = false
        )
        val compoundInput = simpleInput.copy(
            ruleFlags = listOf("MOR_COMPOUND", "DISB_FULL"),
            moratoriumCompound = true
        )
        val simpleRes = LoanCalculationEngine.calculate(simpleInput, null)
        val compoundRes = LoanCalculationEngine.calculate(compoundInput, null)

        assertTrue(simpleRes.isValid && compoundRes.isValid)
        assertEquals(0.0, compoundRes.schedule[0].emiPaid, 0.001)
        assertTrue(compoundRes.insights.moratoriumExtraInterest > 0.0)
        assertTrue(compoundRes.firstEmi > simpleRes.firstEmi)
        assertEquals(0.0, simpleRes.schedule.last().closingBalance, 0.001)
        assertEquals(0.0, compoundRes.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun balloonPayment_hasLowerRegularEmiAndLargeFinalPayment() {
        val input = CalculationInput(
            categoryId = "auto",
            ruleFlags = listOf("EMI_BALLOON"),
            principal = 1200000.0,
            assetValue = 1600000.0,
            annualRate = 10.0,
            tenureMonths = 60,
            balloonPercent = 30.0
        )
        val res = LoanCalculationEngine.calculate(input, null)
        assertTrue(res.isValid)
        val stdEmi = LoanCalculationEngine.standardEmi(1200000.0, 10.0, 60)
        assertTrue(res.firstEmi < stdEmi)
        assertTrue(res.lastEmi > 360000.0)
        assertEquals("Balloon", res.schedule.last().phaseLabel)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun flatVsReducing_computesHigherEffectiveAnnualRate() {
        val input = CalculationInput(
            categoryId = "personal",
            ruleFlags = listOf("FAC_TERM", "BASIS_FLAT_COMPARE"),
            principal = 500000.0,
            assetValue = 0.0,
            annualRate = 10.0,
            tenureMonths = 36
        )
        val res = LoanCalculationEngine.calculate(input, null)
        val flat = res.insights.flatVsReducing
        assertNotNull(flat)
        assertTrue(flat!!.extraInterestCost > 50000.0)
        assertTrue(flat.effectiveAnnualRatePercent > 17.0) // 10% flat over 3y ≈ 17.9% reducing EAR
    }

    @Test
    fun flexiCashCredit_chargesInterestOnlyOnDrawnBalance() {
        val input = CalculationInput(
            categoryId = "business",
            ruleFlags = listOf("BIZ_CASH_CREDIT"),
            principal = 1000000.0,
            assetValue = 1500000.0,
            annualRate = 12.0,
            tenureMonths = 12,
            flexiEvents = listOf(
                FlexiEvent(1, drawAmount = 400000.0),
                FlexiEvent(6, repayAmount = 200000.0)
            )
        )
        val res = LoanCalculationEngine.calculate(input, null)
        assertTrue(res.isValid)
        // Month 1 interest on 4L @ 12% = 4,000
        assertEquals(4000.0, res.schedule[0].interestPaid, 0.01)
        assertEquals(0.0, res.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun billDiscounting_computesEffectiveAnnualizedCost() {
        val input = CalculationInput(
            categoryId = "business",
            ruleFlags = listOf("BIZ_BILL_DISCOUNT"),
            billInvoiceAmount = 1000000.0,
            billAdvancePercent = 80.0,
            billDiscountRatePercent = 12.0,
            billDays = 60,
            processingFeePercent = 0.5
        )
        val res = LoanCalculationEngine.calculate(input, null)
        assertTrue(res.isValid)
        val bd = res.insights.billDiscountResult!!
        assertEquals(800000.0, bd.advanceAmount, 0.01)
        assertTrue(bd.effectiveAnnualizedCostPercent > 12.0)
    }

    @Test
    fun goldLoans_interestOnlyAndBulletAtMaturity() {
        val ioInput = CalculationInput(
            categoryId = "asset_backed",
            ruleFlags = listOf("ASSET_GOLD_INTEREST_ONLY"),
            principal = 500000.0,
            assetValue = 700000.0,
            annualRate = 12.0,
            tenureMonths = 12
        )
        val ioRes = LoanCalculationEngine.calculate(ioInput, null)
        assertTrue(ioRes.isValid)
        assertEquals(5000.0, ioRes.monthlyEmi, 0.01)
        assertEquals(505000.0, ioRes.lastEmi, 0.01)
        assertEquals(0.0, ioRes.schedule.last().closingBalance, 0.001)

        val bulletRes = LoanCalculationEngine.calculate(ioInput.copy(ruleFlags = listOf("ASSET_GOLD_BULLET")), null)
        assertTrue(bulletRes.isValid)
        assertEquals(0.0, bulletRes.monthlyEmi, 0.01)
        assertTrue(bulletRes.lastEmi > 560000.0)
        assertEquals(0.0, bulletRes.schedule.last().closingBalance, 0.001)
    }

    @Test
    fun loanAgainstFd_andMarginShareLoanInsights() {
        val fdInput = CalculationInput(
            categoryId = "asset_backed",
            ruleFlags = listOf("ASSET_FD"),
            principal = 800000.0,
            assetValue = 1000000.0,
            annualRate = 8.75,
            fdRatePercent = 7.25,
            tenureMonths = 24
        )
        val fdRes = LoanCalculationEngine.calculate(fdInput, null)
        assertNotNull(fdRes.insights.fdNetCost)
        assertTrue(fdRes.insights.fdNetCost!!.fdInterestEarned > 0.0)

        val marginInput = CalculationInput(
            categoryId = "asset_backed",
            ruleFlags = listOf("ASSET_MARGIN_SHARE"),
            principal = 600000.0,
            assetValue = 1000000.0,
            shareValue = 1000000.0,
            minMarginPercent = 30.0,
            annualRate = 11.0,
            tenureMonths = 12
        )
        val marginRes = LoanCalculationEngine.calculate(marginInput, null)
        val mc = marginRes.insights.marginCallResult!!
        assertEquals(60.0, mc.currentLtvPercent, 0.01)
        assertFalse(mc.isMarginCallTriggered)
        assertTrue(mc.dropPercentToMarginCall > 10.0)
    }

    @Test
    fun prepayment_balanceTransfer_andTopUp() {
        val prepayInput = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD"),
            principal = 3000000.0,
            assetValue = 4500000.0,
            annualRate = 9.0,
            tenureMonths = 180,
            prepaymentEnabled = true,
            prepayments = listOf(PrepaymentRule(startMonth = 12, amount = 500000.0, mode = PrepaymentMode.REDUCE_TENURE.name)),
            prepaymentFeePercent = 0.0
        )
        val prepayRes = LoanCalculationEngine.calculate(prepayInput, homeCategory)
        val impact = prepayRes.insights.prepaymentImpact!!
        assertTrue(impact.interestSaved > 300000.0)
        assertTrue(impact.monthsSaved > 20)
        assertEquals(0.0, prepayRes.schedule.last().closingBalance, 0.001)

        val bt = LoanCalculationEngine.calculateBalanceTransfer(
            BalanceTransferInput(
                enabled = true,
                currentOutstanding = 3000000.0,
                currentAnnualRate = 10.5,
                remainingMonths = 180,
                newAnnualRate = 8.5,
                transferFees = 30000.0
            )
        )
        assertTrue(bt.netSavings > 500000.0)
        assertNotNull(bt.breakEvenMonth)
        assertTrue(bt.breakEvenMonth!! in 1..24)

        val topUp = LoanCalculationEngine.calculateTopUp(
            TopUpInput(
                enabled = true,
                existingOutstanding = 3000000.0,
                existingAnnualRate = 8.75,
                existingRemainingMonths = 180,
                topUpAmount = 1000000.0,
                topUpAnnualRate = 9.5,
                topUpTenureMonths = 120
            )
        )
        assertEquals(LoanCalculationEngine.roundPaisa(topUp.existingEmi + topUp.topUpEmi), topUp.blendedEmi, 0.01)
    }

    @Test
    fun validation_rejectsInvalidRateAmountTenureAndLtv() {
        val bad = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED"),
            principal = 9000000.0,
            assetValue = 10000000.0, // 90% LTV > 80% cap
            annualRate = 45.0, // > 40%
            tenureMonths = 12 // < 60m min
        )
        val res = LoanCalculationEngine.calculate(bad, homeCategory)
        assertFalse(res.isValid)
        assertTrue(res.validationErrors.size >= 3)
    }

    @Test
    fun indianDigitGrouping_formatsCorrectly() {
        assertEquals("₹12,34,567", CurrencyFormatter.format(1234567.0, CurrencyCode.INR, false))
        assertEquals("₹28,00,000.50", CurrencyFormatter.format(2800000.50, CurrencyCode.INR, true))
        assertEquals("$1,234,567", CurrencyFormatter.format(1234567.0, CurrencyCode.USD, false))
    }

    @Test
    fun catalogJson_parsesAllSixCategoriesAndVariantGroups() {
        val file = java.io.File("src/main/assets/catalog.json").let { f ->
            if (f.exists()) f else java.io.File("app/src/main/assets/catalog.json")
        }
        assertTrue("catalog.json must exist at ${file.absolutePath}", file.exists())
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val parsed = moshi.adapter(LoanCatalog::class.java).fromJson(file.readText())
        assertNotNull(parsed)
        val ids = parsed!!.categories.map { it.id }
        assertEquals(listOf("home", "personal", "auto", "education", "business", "asset_backed"), ids)
        val home = parsed.categories.first { it.id == "home" }
        assertEquals(60, home.tenureMinMonths)
        assertEquals(360, home.tenureMaxMonths)
        assertEquals(5, home.variantGroups.size)
        assertEquals(4, home.variantGroups.first { it.id == "key_terms" }.variants.size)
        parsed.categories.forEach { cat ->
            assertTrue("${cat.id} must have variant groups", cat.variantGroups.isNotEmpty())
            cat.variantGroups.forEach { group ->
                assertTrue("${cat.id}/${group.id} must have variants", group.variants.isNotEmpty())
            }
        }
    }
}

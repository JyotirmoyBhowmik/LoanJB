package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ConfigScalar
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RemoteConfigDocument
import com.jyotirmoyb.loan.data.remote.RemoteConfigItem
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.features.bankstaff.resolveStaffPresets
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoansSuiteEnginesTest {

    // --- 1. Loan Comparison Engine ---
    @Test
    fun comparisonEngine_evaluatesUpTo3OffersAndHighlightsCheapestIncludingGst() {
        val base = CalculationInput(
            categoryId = "home",
            principal = 5_000_000.0,
            tenureMonths = 240,
            gstOnFeesEnabled = true,
            gstRatePercent = 18.0
        )
        val offers = listOf(
            ComparisonScenarioInput(
                1L,
                "Bank A (8.4%)",
                "home",
                "Home",
                "Floating",
                base.copy(annualRate = 8.40, processingFeePercent = 0.25)
            ),
            ComparisonScenarioInput(
                2L,
                "Bank B (8.9%)",
                "home",
                "Home",
                "Floating",
                base.copy(annualRate = 8.90, processingFeePercent = 0.50)
            ),
            ComparisonScenarioInput(
                3L,
                "Bank C (8.6%, 15y)",
                "home",
                "Home",
                "Shorter Tenure",
                base.copy(annualRate = 8.60, tenureMonths = 180, processingFeePercent = 0.50)
            ),
            ComparisonScenarioInput(4L, "Bank D (Ignored 4th)", "home", "Home", "Ignored", base.copy(annualRate = 7.50))
        )

        val report = LoanComparisonEngine.evaluate(offers)

        // Caps strictly at 3 scenarios
        assertEquals(3, report.scenarios.size)
        // Shorter 15-yr offer has lowest total cost despite slightly higher rate than 20-yr 8.4%
        assertEquals(3L, report.cheapestId)
        assertTrue(report.scenarios.first { it.id == 3L }.isCheapest)
        assertFalse(report.scenarios.first { it.id == 2L }.isCheapest)
        assertTrue(report.maxSavings > 1_000_000.0)
        // Each scenario has fees + 18% GST included in total cost & a yearly balance curve ending at 0
        report.scenarios.forEach { s ->
            assertTrue(s.result.gstOnFeeAmount > 0.0)
            assertTrue(s.yearlyBalances.first() == 5_000_000.0)
            assertEquals(0.0, s.yearlyBalances.last(), 0.01)
            assertTrue(s.result.loanFreeDateLabel.isNotBlank())
        }
    }

    // --- 2. Loan Eligibility & Affordability Engine ---
    @Test
    fun eligibilityEngine_appliesDataDrivenFoirBandsAndAgeCap() {
        val foirCfg = FoirConfig(
            lowIncomeFoirPercent = 40.0,
            midIncomeFoirPercent = 50.0,
            highIncomeFoirPercent = 55.0,
            lowIncomeThreshold = 50_000.0,
            highIncomeThreshold = 150_000.0
        )

        assertEquals(40.0, foirCfg.defaultFoirForIncome(40_000.0), 0.001)
        assertEquals(50.0, foirCfg.defaultFoirForIncome(100_000.0), 0.001)
        assertEquals(55.0, foirCfg.defaultFoirForIncome(200_000.0), 0.001)

        // Age 50, retirement 60 -> tenure capped at 10 years even if 20 requested
        val res = LoanEligibilityEngine.calculateForward(
            EligibilityForwardInput(
                monthlyIncome = 100_000.0,
                existingEmis = 10_000.0,
                ageYears = 50,
                retirementAgeYears = 60,
                baseAnnualRatePercent = 8.5,
                desiredTenureYears = 20,
                cibilBand = CibilBand.EXCELLENT_750_PLUS
            ),
            foirCfg
        )
        assertTrue(res.isValid)
        assertEquals(120, res.appliedTenureMonths)
        assertEquals(40_000.0, res.maxEmi, 0.01) // 50% of 1L - 10k existing
        assertEquals(AffordabilityZone.STRETCH, res.zone) // 50% total EMI-to-income -> Stretch
        assertTrue(res.maxLoanAmount > 3_000_000.0)
    }

    @Test
    fun eligibilityEngine_reverseModeRoundTripsExactStandardEmi() {
        val targetEmi = 45_000.0
        val rate = 8.75
        val tenureMonths = 240

        val reverse = LoanEligibilityEngine.calculateReverse(
            EligibilityReverseInput(
                affordableMonthlyEmi = targetEmi,
                annualRatePercent = rate,
                tenureMonths = tenureMonths,
                monthlyIncome = 150_000.0,
                existingEmis = 0.0,
                cibilBand = CibilBand.EXCELLENT_750_PLUS
            )
        )
        assertTrue(reverse.isValid)
        assertEquals(AffordabilityZone.COMFORTABLE, reverse.zone) // 30% EMI/income -> Comfortable

        val recomputedEmi = MoneyMath.standardEmiBd(
            MoneyMath.bd(reverse.maxLoanAmount),
            MoneyMath.bd(rate),
            tenureMonths
        ).toDouble()
        assertTrue("Reverse principal should round-trip EMI within ₹1", abs(recomputedEmi - targetEmi) < 1.0)
    }

    @Test
    fun eligibilityEngine_classifiesRiskyForBelow650Cibil() {
        val foirCfg = FoirConfig(40.0, 50.0, 55.0, 50_000.0, 150_000.0)
        val res = LoanEligibilityEngine.calculateForward(
            EligibilityForwardInput(
                monthlyIncome = 150_000.0,
                existingEmis = 0.0,
                cibilBand = CibilBand.BELOW_650
            ),
            foirCfg
        )
        assertTrue(res.isValid)
        assertEquals(AffordabilityZone.RISKY, res.zone)
        assertTrue(res.effectiveAnnualRatePercent > 10.0)
    }

    // --- 3. Balance Transfer & Top-Up Engine ---
    @Test
    fun balanceTransferEngine_computesBreakEvenGstAndTopUpBlendedEmi() {
        val res = BalanceTransferEngine.analyze(
            BalanceTransferAnalysisInput(
                currentOutstanding = 4_000_000.0,
                currentAnnualRatePercent = 9.75,
                remainingMonths = 180,
                newAnnualRatePercent = 8.40,
                processingFeePercent = 0.50, // 20,000
                processingFeeFlat = 0.0,
                gstOnFeesEnabled = true,
                gstRatePercent = 18.0, // 3,600
                legalAndValuationCharges = 12_000.0,
                foreclosureFeePercent = 0.0,
                includeTopUp = true,
                topUpAmount = 500_000.0,
                topUpAnnualRatePercent = 9.25,
                topUpTenureMonths = 120
            )
        )
        assertTrue(res.isValid)
        assertEquals(20_000.0, res.processingFeeAmount, 0.01)
        assertEquals(3_600.0, res.gstOnFeeAmount, 0.01)
        assertEquals(35_600.0, res.totalSwitchingCost, 0.01)
        assertTrue(res.monthlyEmiSavings > 3_000.0)
        assertNotNull(res.breakEvenMonth)
        assertTrue(res.breakEvenMonth!! <= 12)
        assertTrue(res.netLifetimeSavings > 500_000.0)
        assertEquals(TransferRecommendation.STRONGLY_RECOMMENDED, res.recommendation)
        assertTrue(res.topUpEmi > 0.0)
        assertEquals(MoneyMath.roundPaise(res.newEmi + res.topUpEmi), res.blendedEmi, 0.01)
    }

    @Test
    fun balanceTransferEngine_recommendsStayingWhenNewRateIsHigher() {
        val res = BalanceTransferEngine.analyze(
            BalanceTransferAnalysisInput(
                currentOutstanding = 2_000_000.0,
                currentAnnualRatePercent = 8.50,
                remainingMonths = 120,
                newAnnualRatePercent = 9.00,
                processingFeePercent = 0.50,
                legalAndValuationCharges = 10_000.0
            )
        )
        assertTrue(res.isValid)
        assertNull(res.breakEvenMonth)
        assertTrue(res.netLifetimeSavings < 0.0)
        assertEquals(TransferRecommendation.NOT_RECOMMENDED, res.recommendation)
    }

    // --- 4. Loan Tax Benefit Engine (Data-Driven Old vs New Regime) ---
    @Test
    fun loanTaxBenefitEngine_readsCapsFromConfigAndComparesOldVsNewRegime() {
        val baseConfig = TaxSlabsConfig.fromResolvedConfig(emptyMap())
        val input = LoanTaxBenefitInput(
            grossAnnualIncome = 1_800_000.0,
            isSalaried = true,
            propertyOccupancy = PropertyOccupancy.SELF_OCCUPIED,
            homeLoanInterestPaid = 250_000.0, // Capped at 2,00,000
            homeLoanPrincipalPaid = 120_000.0, // 1,00,000 fits in remaining 80C room (1.5L - 50k)
            other80cInvestments = 50_000.0,
            educationLoanInterestPaid = 80_000.0 // Uncapped Sec 80E
        )

        val res = LoanTaxBenefitEngine.calculate(input, baseConfig)
        assertTrue(res.isValid)
        // Old Regime uses Sec 24(b) 2L + Sec 80C 1L + Sec 80E 80k = 3.8L total loan deductions
        assertEquals(200_000.0, res.oldRegime.sec24bDeductionUsed, 0.01)
        assertEquals(100_000.0, res.oldRegime.sec80cLoanDeductionUsed, 0.01)
        assertEquals(80_000.0, res.oldRegime.sec80eDeductionUsed, 0.01)
        // In 30% bracket + 4% cess -> 3.8L * 31.2% = 1,18,560 saved in Old Regime
        assertEquals(118_560.0, res.oldRegime.totalTaxSavedByLoans, 1.0)

        // New Regime disallows self-occupied 24b, 80C, 80E when config flags are 0
        assertEquals(0.0, res.newRegime.sec24bDeductionUsed, 0.01)
        assertEquals(0.0, res.newRegime.totalTaxSavedByLoans, 0.01)

        // Overriding Sec 24(b) cap via data immediately changes tax saved!
        val overrideMap = mapOf(
            ConfigKeys.SEC_24B_CAP to ResolvedConfigItem(
                key = ConfigKeys.SEC_24B_CAP,
                label = "Sec 24(b) Cap",
                category = ConfigCategory.TAX_SLABS,
                fileName = "taxslabs.json",
                valueText = "300000",
                numericValue = 300_000.0,
                unit = "INR",
                effectiveFrom = "2026-04-01",
                asOfDate = "2026-11-25",
                sourceName = "Manual",
                sourceUrl = "",
                lastVerified = "2026-11-25",
                source = ConfigValueSource.MANUAL,
                defaultValueText = "200000",
                defaultNumericValue = 200_000.0
            )
        )
        val overriddenConfig = TaxSlabsConfig.fromResolvedConfig(overrideMap)
        val resOverridden = LoanTaxBenefitEngine.calculate(input, overriddenConfig)
        assertEquals(250_000.0, resOverridden.oldRegime.sec24bDeductionUsed, 0.01)
        assertTrue(resOverridden.oldRegime.totalTaxSavedByLoans > res.oldRegime.totalTaxSavedByLoans)
    }

    // --- 5. Part-Payment Planner Engine ---
    @Test
    fun partPaymentPlannerEngine_computesExactExtraMonthlyToHitTargetDateAndZeroClosingBalance() {
        val res = PartPaymentPlannerEngine.plan(
            PartPaymentPlanInput(
                principal = 4_000_000.0,
                annualRatePercent = 8.75,
                remainingMonths = 240,
                startYear = 2026,
                startMonth = 11,
                startDay = 25,
                targetPayoffYear = 2041, // 15 years = 180 months (5 years early)
                targetPayoffMonth = 11
            )
        )
        assertTrue(res.isValid)
        assertEquals(180, res.targetMonths)
        assertTrue("Actual payoff months (${res.actualNewMonths}) must be <= target 180", res.actualNewMonths <= 180)
        assertTrue(res.extraMonthlyNeeded > 4_000.0)
        assertTrue(res.totalInterestSaved > 1_000_000.0)
        assertTrue(res.monthsSaved >= 60)
        assertEquals(0.0, res.plannedYearlyBalances.last(), 0.01)
        assertTrue(res.newLoanFreeDate.isNotBlank())
    }

    @Test
    fun partPaymentPlannerEngine_rejectsTargetDateLaterThanCurrentTenure() {
        val res = PartPaymentPlannerEngine.plan(
            PartPaymentPlanInput(
                principal = 2_000_000.0,
                annualRatePercent = 9.0,
                remainingMonths = 120,
                startYear = 2026,
                startMonth = 11,
                targetPayoffYear = 2040,
                targetPayoffMonth = 11
            )
        )
        assertFalse(res.isValid)
        assertTrue(res.validationErrors.isNotEmpty())
    }

    // --- 6. Bank Staff Editable Preset Templates (Data-Driven Resolution) ---
    @Test
    fun bankStaffPresets_resolveEditableManualOverridesOverSeedValues() {
        val seedDoc = RemoteConfigDocument(
            schemaVersion = 1,
            dataVersion = "2026.11.1",
            publishedOn = "2026-11-25",
            items = listOf(
                RemoteConfigItem(ConfigKeys.BANK_STAFF_HOUSING, ConfigScalar("6.5", 6.5), "%", "2026-04-01", "Seed"),
                RemoteConfigItem(ConfigKeys.BANK_STAFF_HOUSING_AMOUNT, ConfigScalar("4000000", 4000000.0), "INR", "2026-04-01", "Seed"),
                RemoteConfigItem(ConfigKeys.BANK_STAFF_HOUSING_TENURE, ConfigScalar("240", 240.0), "months", "2026-04-01", "Seed")
            )
        )
        val overrides = mapOf(
            ConfigKeys.BANK_STAFF_HOUSING to ManualOverrideEntity(ConfigKeys.BANK_STAFF_HOUSING, "5.75", 1L, "2026-11-25"),
            ConfigKeys.BANK_STAFF_HOUSING_AMOUNT to ManualOverrideEntity(ConfigKeys.BANK_STAFF_HOUSING_AMOUNT, "6000000", 1L, "2026-11-25"),
            ConfigKeys.BANK_STAFF_HOUSING_TENURE to ManualOverrideEntity(ConfigKeys.BANK_STAFF_HOUSING_TENURE, "300", 1L, "2026-11-25")
        )

        val resolved = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = emptyMap(),
            manualOverrides = overrides,
            useOnlineData = false
        ).associateBy { it.key }

        val presets = resolveStaffPresets(resolved)
        val housing = presets.first { it.id == "staff_housing" }
        assertEquals(5.75, housing.staffRate, 0.001)
        assertEquals(6_000_000.0, housing.defaultAmount, 0.01)
        assertEquals(300, housing.tenureMonths)
    }
}

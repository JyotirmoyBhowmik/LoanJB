package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.RemoteConfigMoshi
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

class DepositsSuiteEnginesTest {

    private val defaultTdsConfig = TdsConfig(
        generalThreshold = 50000.0,
        seniorThreshold = 100000.0,
        rateWithPanPercent = 10.0,
        rateWithoutPanPercent = 20.0,
        cessPercent = 4.0
    )

    private val defaultSmallSavingsConfig = SmallSavingsConfig.fromResolvedConfig(emptyMap())

    // -------------------------------------------------------------------------
    // 1. FIXED DEPOSIT CUMULATIVE — KNOWN-ANSWER COMPOUNDING TESTS
    // -------------------------------------------------------------------------

    @Test
    fun fdCumulative_compoundingFrequencies_knownAnswersFor1LakhAt8PercentFor1Year() {
        val base = FixedDepositInput(
            principal = 100000.0,
            annualRatePercent = 8.0,
            isSeniorCitizen = false,
            tenureMonths = 12,
            mode = FdMode.CUMULATIVE,
            taxSlabPercent = 0.0,
            startYear = 2026,
            startMonth = 4,
            startDay = 1
        )

        // Yearly: 100000 * (1.08)^1 = 108000.00
        val yearly = FixedDepositEngine.calculate(
            base.copy(compounding = CompoundingFrequency.YEARLY),
            defaultTdsConfig
        )
        assertTrue(yearly.isValid)
        assertEquals(108000.00, yearly.maturityAmount, 0.01)
        assertEquals(8000.00, yearly.totalInterest, 0.01)
        assertEquals("01 Apr 2027", yearly.maturityDateLabel)

        // Half-Yearly: 100000 * (1.04)^2 = 108160.00
        val halfYearly = FixedDepositEngine.calculate(
            base.copy(compounding = CompoundingFrequency.HALF_YEARLY),
            defaultTdsConfig
        )
        assertEquals(108160.00, halfYearly.maturityAmount, 0.01)
        assertEquals(8160.00, halfYearly.totalInterest, 0.01)

        // Quarterly (Indian Bank Default): 100000 * (1.02)^4 = 108243.216 -> 108243.22
        val quarterly = FixedDepositEngine.calculate(
            base.copy(compounding = CompoundingFrequency.QUARTERLY),
            defaultTdsConfig
        )
        assertEquals(108243.22, quarterly.maturityAmount, 0.01)
        assertEquals(8243.22, quarterly.totalInterest, 0.01)
        assertEquals(8.24, quarterly.effectivePreTaxYieldPercent, 0.02)

        // Monthly: 100000 * (1 + 0.08/12)^12 = 108299.95
        val monthly = FixedDepositEngine.calculate(
            base.copy(compounding = CompoundingFrequency.MONTHLY),
            defaultTdsConfig
        )
        assertEquals(108299.95, monthly.maturityAmount, 0.05)

        // Strict ordering: Monthly > Quarterly > Half-Yearly > Yearly
        assertTrue(monthly.maturityAmount > quarterly.maturityAmount)
        assertTrue(quarterly.maturityAmount > halfYearly.maturityAmount)
        assertTrue(halfYearly.maturityAmount > yearly.maturityAmount)
    }

    // -------------------------------------------------------------------------
    // 2. FD INTEREST PAYOUT — KNOWN-ANSWER PAYOUTS
    // -------------------------------------------------------------------------

    @Test
    fun fdPayout_monthlyQuarterlyHalfYearlyYearly_exactPayouts() {
        val base = FixedDepositInput(
            principal = 1200000.0,
            annualRatePercent = 7.0,
            isSeniorCitizen = false,
            tenureMonths = 12,
            mode = FdMode.PAYOUT,
            taxSlabPercent = 30.0
        )

        val monthly = FixedDepositEngine.calculate(base.copy(payoutFrequency = PayoutFrequency.MONTHLY), defaultTdsConfig)
        assertEquals(7000.00, monthly.periodicPayoutAmount, 0.01)
        assertEquals(12, monthly.totalPayoutCount)
        assertEquals(84000.00, monthly.totalPayoutReceived, 0.01)
        assertEquals(1200000.00, monthly.maturityAmount, 0.01)

        val quarterly = FixedDepositEngine.calculate(base.copy(payoutFrequency = PayoutFrequency.QUARTERLY), defaultTdsConfig)
        assertEquals(21000.00, quarterly.periodicPayoutAmount, 0.01)
        assertEquals(4, quarterly.totalPayoutCount)

        val halfYearly = FixedDepositEngine.calculate(base.copy(payoutFrequency = PayoutFrequency.HALF_YEARLY), defaultTdsConfig)
        assertEquals(42000.00, halfYearly.periodicPayoutAmount, 0.01)
        assertEquals(2, halfYearly.totalPayoutCount)

        val yearly = FixedDepositEngine.calculate(base.copy(payoutFrequency = PayoutFrequency.YEARLY), defaultTdsConfig)
        assertEquals(84000.00, yearly.periodicPayoutAmount, 0.01)
        assertEquals(1, yearly.totalPayoutCount)
    }

    // -------------------------------------------------------------------------
    // 3. TDS KNOWN-ANSWER TESTS (SEC 194A & 206AA + SENIOR CITIZEN THRESHOLD)
    // -------------------------------------------------------------------------

    @Test
    fun fdTds_belowThresholdAboveThresholdPanNoPanAndSeniorCitizen_exactRules() {
        // Case A: ₹5,00,000 @ 7% quarterly for 1 yr -> interest ₹35,929.51 (< ₹50,000) -> TDS = ₹0
        val belowLimit = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 500000.0,
                annualRatePercent = 7.0,
                isSeniorCitizen = false,
                tenureMonths = 12,
                hasPan = true,
                taxSlabPercent = 20.0
            ),
            defaultTdsConfig
        )
        assertEquals(35929.51, belowLimit.totalInterest, 0.05)
        assertEquals(0.0, belowLimit.totalEstimatedTds, 0.01)
        assertEquals(50000.0, belowLimit.appliedTdsThreshold, 0.01)

        // Case B: ₹10,00,000 @ 7% quarterly for 1 yr -> interest ₹71,859.03 (> ₹50,000)
        // With PAN (10%): TDS = ₹7,185.90
        val withPan = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 7.0,
                isSeniorCitizen = false,
                tenureMonths = 12,
                hasPan = true,
                taxSlabPercent = 30.0
            ),
            defaultTdsConfig
        )
        assertEquals(71859.03, withPan.totalInterest, 0.05)
        assertEquals(7185.90, withPan.totalEstimatedTds, 0.05)
        assertEquals(10.0, withPan.appliedTdsRatePercent, 0.01)
        // 30% + 4% cess = 31.2% effective tax -> ₹22,420.02
        assertEquals(22420.02, withPan.totalSlabTax, 0.10)
        assertTrue(withPan.effectivePostTaxYieldPercent < withPan.effectivePreTaxYieldPercent)

        // Case C: Same deposit WITHOUT PAN (20% under Sec 206AA) -> TDS = ₹14,371.81
        val withoutPan = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 7.0,
                isSeniorCitizen = false,
                tenureMonths = 12,
                hasPan = false,
                taxSlabPercent = 30.0
            ),
            defaultTdsConfig
        )
        assertEquals(14371.81, withoutPan.totalEstimatedTds, 0.05)
        assertEquals(20.0, withoutPan.appliedTdsRatePercent, 0.01)

        // Case D: Senior citizen (+0.5% bonus -> 7.5%), interest ₹77,135.87 (< ₹1,00,000 senior limit) -> TDS = ₹0
        val senior = FixedDepositEngine.calculate(
            FixedDepositInput(
                principal = 1000000.0,
                annualRatePercent = 7.0,
                isSeniorCitizen = true,
                seniorBonusPercent = 0.5,
                tenureMonths = 12,
                hasPan = true,
                taxSlabPercent = 20.0
            ),
            defaultTdsConfig
        )
        assertEquals(7.5, senior.effectiveNominalRatePercent, 0.01)
        assertEquals(100000.0, senior.appliedTdsThreshold, 0.01)
        assertEquals(0.0, senior.totalEstimatedTds, 0.01)
    }

    // -------------------------------------------------------------------------
    // 4. RECURRING DEPOSIT — IBA QUARTERLY COMPOUNDING KNOWN ANSWER
    // -------------------------------------------------------------------------

    @Test
    fun recurringDeposit_quarterlyCompounding_matchesIbaFormula() {
        val result = RecurringDepositEngine.calculate(
            RecurringDepositInput(
                monthlyDeposit = 10000.0,
                annualRatePercent = 6.75,
                isSeniorCitizen = false,
                tenureMonths = 60,
                hasPan = true,
                taxSlabPercent = 30.0
            ),
            defaultTdsConfig
        )
        assertTrue(result.isValid)
        assertEquals(600000.0, result.totalDeposited, 0.01)

        // Independent verification of IBA formula: sum_{k=1..60} 10000 * (1 + 6.75/400)^((61-k)/3)
        var expected = 0.0
        val qBase = 1.0 + 6.75 / 400.0
        for (k in 1..60) {
            expected += 10000.0 * qBase.pow((61 - k) / 3.0)
        }
        assertEquals(expected, result.maturityAmount, 1.0)
        assertEquals(result.maturityAmount - 600000.0, result.totalInterest, 0.02)
        assertEquals(5, result.yearlyRows.size)
        assertTrue(result.postTaxMaturityAmount < result.maturityAmount)
    }

    // -------------------------------------------------------------------------
    // 5. SMALL SAVINGS SCHEMES — KNOWN ANSWERS & CAPS
    // -------------------------------------------------------------------------

    @Test
    fun smallSavings_ppfSsyNscScssPoMisPoTdPoRdKvp_knownAnswers() {
        // PPF: ₹1,50,000/yr @ 7.1% for 15 yrs -> ₹40,68,209
        val ppf = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PPF, contributionAmount = 150000.0),
            defaultSmallSavingsConfig
        )
        assertTrue(ppf.isValid)
        assertEquals(2250000.0, ppf.totalInvested, 0.01)
        assertEquals(4068209.0, ppf.maturityAmount, 10.0)
        assertNull(ppf.capWarning)

        // PPF Cap Warning & Extension Block (+5 yrs)
        val ppfExt = SmallSavingsEngine.calculate(
            SmallSavingsInput(
                scheme = SmallSavingsSchemeType.PPF,
                contributionAmount = 200000.0, // exceeds 1.5L cap
                ppfExtensionBlocks = 1,
                ppfContributeDuringExtension = true
            ),
            defaultSmallSavingsConfig
        )
        assertNotNull(ppfExt.capWarning)
        assertEquals(3000000.0, ppfExt.totalInvested, 0.01) // capped at 1.5L * 20
        assertTrue(ppfExt.maturityAmount > 6600000.0)

        // SSY: ₹1,50,000/yr @ 8.2% (15 yrs deposit, 21 yrs maturity) -> total invested ₹22.5L, maturity > ₹69L
        val ssy = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.SSY, contributionAmount = 150000.0),
            defaultSmallSavingsConfig
        )
        assertEquals(2250000.0, ssy.totalInvested, 0.01)
        assertTrue(ssy.maturityAmount > 6900000.0)
        assertEquals(21, ssy.yearlyRows.size)

        // NSC: ₹1,00,000 @ 7.7% for 5 yrs -> 100000 * (1.077)^5 = ₹1,44,903.40
        val nsc = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.NSC, contributionAmount = 100000.0),
            defaultSmallSavingsConfig
        )
        assertEquals(144903.40, nsc.maturityAmount, 0.50)

        // SCSS: ₹30,00,000 @ 8.2% -> Quarterly payout exact ₹61,500.00
        val scss = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.SCSS, contributionAmount = 3000000.0),
            defaultSmallSavingsConfig
        )
        assertEquals(61500.00, scss.periodicPayoutAmount, 0.01)
        assertEquals(1230000.00, scss.totalInterest, 0.01)

        // PO MIS: Single ₹9,00,000 @ 7.4% -> Monthly payout exact ₹5,550.00
        val pomisSingle = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_MIS, contributionAmount = 900000.0, pomisJointAccount = false),
            defaultSmallSavingsConfig
        )
        assertEquals(5550.00, pomisSingle.periodicPayoutAmount, 0.01)
        assertNull(pomisSingle.capWarning)

        // PO MIS exceeding single cap warns, while joint allows ₹15,00,000
        val pomisOverSingle = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_MIS, contributionAmount = 1200000.0, pomisJointAccount = false),
            defaultSmallSavingsConfig
        )
        assertNotNull(pomisOverSingle.capWarning)
        val pomisJoint = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_MIS, contributionAmount = 1200000.0, pomisJointAccount = true),
            defaultSmallSavingsConfig
        )
        assertNull(pomisJoint.capWarning)
        assertEquals(7400.00, pomisJoint.periodicPayoutAmount, 0.01)

        // PO TD: 5Y rate (7.5%) > 1Y rate (6.9%)
        val potd1 = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_TD, contributionAmount = 100000.0, potdTenureYears = 1),
            defaultSmallSavingsConfig
        )
        val potd5 = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_TD, contributionAmount = 100000.0, potdTenureYears = 5),
            defaultSmallSavingsConfig
        )
        assertEquals(6.9, potd1.appliedRatePercent, 0.01)
        assertEquals(7.5, potd5.appliedRatePercent, 0.01)
        assertTrue(potd5.periodicPayoutAmount > potd1.periodicPayoutAmount)

        // PO RD: ₹10,000/mo @ 6.7% for 60 mos -> > ₹7,10,000
        val pord = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.PO_RD, contributionAmount = 10000.0),
            defaultSmallSavingsConfig
        )
        assertEquals(600000.0, pord.totalInvested, 0.01)
        assertTrue(pord.maturityAmount in 710000.0..716000.0)

        // KVP: ₹1,00,000 @ 7.5% for 115 months -> doubles to ~₹2,00,000
        val kvp = SmallSavingsEngine.calculate(
            SmallSavingsInput(scheme = SmallSavingsSchemeType.KVP, contributionAmount = 100000.0),
            defaultSmallSavingsConfig
        )
        assertEquals(115, kvp.tenureMonths)
        assertEquals(200000.0, kvp.maturityAmount, 1500.0)
    }

    // -------------------------------------------------------------------------
    // 6. FD vs RD vs PPF vs DEBT MUTUAL FUND POST-TAX COMPARISON
    // -------------------------------------------------------------------------

    @Test
    fun depositComparison_30PercentSlabOver15Years_ppfWinsAndDebtBeatsFdPostTax() {
        val report = DepositComparisonEngine.compare(
            DepositComparisonInput(
                monthlyContribution = 10000.0, // ₹1.2L/yr (within PPF ₹1.5L cap)
                horizonYears = 15,
                taxSlabPercent = 30.0,
                cessPercent = 4.0,
                fdAnnualRatePercent = 7.0,
                rdAnnualRatePercent = 6.75,
                ppfAnnualRatePercent = 7.1,
                ppfYearlyCap = 150000.0,
                debtFundExpectedReturnPercent = 7.25
            )
        )
        assertTrue(report.isValid)
        assertEquals(4, report.instruments.size)

        val fd = report.instruments.first { it.id == "fd" }
        val ppf = report.instruments.first { it.id == "ppf" }
        val debt = report.instruments.first { it.id == "debt_fund" }

        assertTrue("PPF (EEE) should win at 30% tax slab", ppf.isWinner)
        assertEquals(0.0, ppf.estimatedTax, 0.01)
        assertTrue("PPF post-tax maturity should beat FD", ppf.postTaxMaturity > fd.postTaxMaturity)
        assertTrue("Debt MF tax deferral should beat FD post-tax at 30% slab", debt.postTaxMaturity > fd.postTaxMaturity)
        assertTrue(report.taxAdvantageVsFd > 0.0)
    }

    // -------------------------------------------------------------------------
    // 7. BUNDLED REMOTE-CONFIG SCHEMAS VALIDITY CHECK
    // -------------------------------------------------------------------------

    @Test
    fun bundledDepositsConfigJson_passesRemoteConfigValidator() {
        val moshi = RemoteConfigMoshi.instance
        val files = listOf("schemes.json", "rates.json", "taxslabs.json")
        files.forEach { fileName ->
            val assetFile = File("src/main/assets/remote-config/$fileName")
            if (assetFile.exists()) {
                val res = RemoteConfigValidator.parseAndValidate(assetFile.readText(), moshi)
                assertTrue("Validation failed for $fileName: ${res.exceptionOrNull()?.message}", res.isSuccess)
            }
        }

        val schemesFile = File("src/main/assets/remote-config/schemes.json")
        if (schemesFile.exists()) {
            val doc = RemoteConfigValidator.parseAndValidate(schemesFile.readText(), moshi).getOrThrow()
            val keys = doc.items.map { it.key }.toSet()
            assertTrue(keys.contains(ConfigKeys.SCHEME_QUARTER_LABEL))
            assertTrue(keys.contains(ConfigKeys.SCHEME_PPF_RATE))
            assertTrue(keys.contains(ConfigKeys.SCHEME_SSY_RATE))
            assertTrue(keys.contains(ConfigKeys.SCHEME_SCSS_RATE))
            assertTrue(keys.contains(ConfigKeys.SCHEME_POMIS_RATE))
            assertTrue(keys.contains(ConfigKeys.SCHEME_KVP_DOUBLING_MONTHS))
        }
    }
}

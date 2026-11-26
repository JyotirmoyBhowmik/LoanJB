package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.RemoteConfigMoshi
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.pow

class MutualFundSuiteEnginesTest {

    // =========================================================================
    // 1. SIP CALCULATOR KNOWN-ANSWER TESTS
    // =========================================================================

    @Test
    fun sip_flatMonthlySip_matchesExactBeginningOfMonthFormula() {
        val result = SipLumpsumEngine.calculateSip(
            SipInput(
                monthlySip = 10000.0,
                expectedReturnPercent = 12.0,
                tenureYears = 10,
                annualStepUpPercent = 0.0,
                inflationPercent = 6.0
            )
        )
        assertTrue(result.isValid)
        assertEquals(1_200_000.0, result.totalInvested, 0.01)

        // Exact beginning-of-month SIP formula: P * [((1 + i)^n - 1) / i] * (1 + i)
        // i = 0.01, n = 120 => 10000 * ((1.01^120 - 1) / 0.01) * 1.01 ≈ 23,23,390.76
        val expectedMaturity = 10000.0 * (((1.01.pow(120) - 1.0) / 0.01)) * 1.01
        assertEquals(expectedMaturity, result.maturityAmount, 1.0)
        assertEquals(result.maturityAmount - result.totalInvested, result.estimatedReturns, 0.02)
        assertEquals(10000.0, result.finalMonthlySip, 0.01)
        assertEquals(10, result.yearlyRows.size)

        // Inflation-adjusted value: maturity / (1.06^10)
        val expectedReal = result.maturityAmount / 1.06.pow(10)
        assertEquals(expectedReal, result.inflationAdjustedValue, 1.0)
    }

    @Test
    fun sip_withAnnualStepUp_increasesInvestedFinalSipAndMaturity() {
        val flat = SipLumpsumEngine.calculateSip(
            SipInput(monthlySip = 10000.0, expectedReturnPercent = 12.0, tenureYears = 10, annualStepUpPercent = 0.0)
        )
        val stepUp = SipLumpsumEngine.calculateSip(
            SipInput(monthlySip = 10000.0, expectedReturnPercent = 12.0, tenureYears = 10, annualStepUpPercent = 10.0)
        )
        assertTrue(stepUp.isValid)
        // Year 10 SIP = 10000 * 1.10^9 = 23,579.48 (with paise rounding each year)
        val expectedFinalSip = 10000.0 * 1.10.pow(9)
        assertEquals(expectedFinalSip, stepUp.finalMonthlySip, 0.25)
        assertTrue(stepUp.totalInvested > flat.totalInvested)
        assertTrue(stepUp.maturityAmount > flat.maturityAmount)
    }

    @Test
    fun sip_invalidInput_returnsValidationErrors() {
        val res = SipLumpsumEngine.calculateSip(SipInput(monthlySip = -500.0, tenureYears = 0))
        assertFalse(res.isValid)
        assertTrue(res.validationErrors.isNotEmpty())
    }

    // =========================================================================
    // 2. LUMPSUM CALCULATOR KNOWN-ANSWER TESTS
    // =========================================================================

    @Test
    fun lumpsum_annualCompounding_matchesKnownFormula() {
        val result = SipLumpsumEngine.calculateLumpsum(
            LumpsumInput(
                principal = 100000.0,
                expectedReturnPercent = 12.0,
                tenureYears = 10,
                inflationPercent = 6.0
            )
        )
        assertTrue(result.isValid)
        assertEquals(100000.0, result.investedAmount, 0.01)
        // 1,00,000 * (1.12^10) = 3,10,584.82
        assertEquals(310584.82, result.maturityAmount, 0.05)
        assertEquals(210584.82, result.estimatedReturns, 0.05)
        assertEquals(310584.82 / 1.06.pow(10), result.inflationAdjustedValue, 0.5)
        assertEquals(10, result.yearlyRows.size)
    }

    // =========================================================================
    // 3. GOAL SIP CALCULATOR KNOWN-ANSWER TESTS
    // =========================================================================

    @Test
    fun goalSip_withoutInflation_hitsTargetCorpus() {
        val result = SipLumpsumEngine.calculateGoalSip(
            GoalSipInput(
                targetCorpusToday = 10_000_000.0, // ₹1 Cr
                years = 15,
                expectedReturnPercent = 12.0,
                inflationPercent = 6.0,
                adjustTargetForInflation = false,
                annualStepUpPercent = 0.0
            )
        )
        assertTrue(result.isValid)
        assertEquals(10_000_000.0, result.nominalTargetCorpus, 0.01)
        assertEquals(10_000_000.0, result.projectedCorpus, 50.0)
        // Expected SIP for ₹1 Cr in 15y @ 12% ≈ ₹19,818.61
        assertEquals(19818.61, result.requiredMonthlySip, 2.0)
    }

    @Test
    fun goalSip_withInflationAndStepUp_scalesNominalTargetAndHitsGoal() {
        val noInflation = SipLumpsumEngine.calculateGoalSip(
            GoalSipInput(
                targetCorpusToday = 10_000_000.0,
                years = 15,
                expectedReturnPercent = 12.0,
                inflationPercent = 6.0,
                adjustTargetForInflation = false,
                annualStepUpPercent = 0.0
            )
        )
        val withInflation = SipLumpsumEngine.calculateGoalSip(
            GoalSipInput(
                targetCorpusToday = 10_000_000.0,
                years = 15,
                expectedReturnPercent = 12.0,
                inflationPercent = 6.0,
                adjustTargetForInflation = true,
                annualStepUpPercent = 0.0
            )
        )
        val withInflationAndStepUp = SipLumpsumEngine.calculateGoalSip(
            GoalSipInput(
                targetCorpusToday = 10_000_000.0,
                years = 15,
                expectedReturnPercent = 12.0,
                inflationPercent = 6.0,
                adjustTargetForInflation = true,
                annualStepUpPercent = 10.0
            )
        )
        val expectedNominal = 10_000_000.0 * 1.06.pow(15) // ≈ 2,39,65,581.93
        assertEquals(expectedNominal, withInflation.nominalTargetCorpus, 1.0)
        assertTrue(withInflation.requiredMonthlySip > noInflation.requiredMonthlySip * 2.0)
        assertEquals(expectedNominal, withInflation.projectedCorpus, 100.0)

        // Adding 10% step-up reduces starting monthly SIP while still reaching the same target
        assertTrue(withInflationAndStepUp.requiredMonthlySip < withInflation.requiredMonthlySip)
        assertEquals(expectedNominal, withInflationAndStepUp.projectedCorpus, 250.0)
    }

    // =========================================================================
    // 4. SWP CALCULATOR KNOWN-ANSWER TESTS
    // =========================================================================

    @Test
    fun swp_excessiveWithdrawal_depletesBeforePlannedHorizon() {
        val result = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 2_000_000.0,
                monthlyWithdrawal = 40_000.0,
                expectedReturnPercent = 8.0,
                tenureYears = 10,
                annualWithdrawalStepUpPercent = 0.0
            )
        )
        assertTrue(result.isValid)
        assertTrue(result.corpusDepleted)
        assertTrue(result.monthsLasted in 55..70) // ~62 months
        assertEquals(0.0, result.finalBalance, 0.01)
        assertTrue(result.longevityLabel.startsWith("Corpus lasts"))
    }

    @Test
    fun swp_sustainableWithdrawal_lastsExactPlannedHorizon() {
        val probe = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 5_000_000.0,
                monthlyWithdrawal = 40_000.0,
                expectedReturnPercent = 8.0,
                tenureYears = 20
            )
        )
        assertTrue(probe.isValid)
        // Exact PMT for ₹50L @ 8% over 240 months ≈ ₹41,822.00
        assertEquals(41822.00, probe.sustainableMonthlyWithdrawal, 1.0)
        // Perpetuity = 50L * (8% / 12) = ₹33,333.33
        assertEquals(33333.33, probe.perpetuityMonthlyWithdrawal, 0.05)

        val exactRun = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 5_000_000.0,
                monthlyWithdrawal = probe.sustainableMonthlyWithdrawal,
                expectedReturnPercent = 8.0,
                tenureYears = 20
            )
        )
        assertEquals(240, exactRun.monthsLasted)
        assertEquals(0.0, exactRun.finalBalance, 50.0)
    }

    @Test
    fun swp_perpetuityWithdrawal_preservesPrincipal() {
        val result = SwpEngine.calculate(
            SwpInput(
                initialCorpus = 6_000_000.0,
                monthlyWithdrawal = 40_000.0, // Exact 8% annual = 40,000/mo
                expectedReturnPercent = 8.0,
                tenureYears = 15
            )
        )
        assertTrue(result.isValid)
        assertFalse(result.corpusDepleted)
        assertEquals(180, result.monthsLasted)
        assertEquals(6_000_000.0, result.finalBalance, 1.0)
    }

    // =========================================================================
    // 5. XIRR CALCULATOR KNOWN-ANSWER & BULK PARSE TESTS
    // =========================================================================

    @Test
    fun xirr_exactOneYearReturn_equalsExactTenPercent() {
        val result = XirrEngine.calculate(
            listOf(
                CashFlowEntry(1L, "2025-01-01", -100_000.0, "Buy"),
                CashFlowEntry(2L, "2026-01-01", 110_000.0, "Sell")
            )
        )
        assertTrue(result.isValid)
        assertEquals(10.0, result.xirrPercent, 0.05)
        assertEquals(100_000.0, result.totalInvested, 0.01)
        assertEquals(110_000.0, result.totalReturned, 0.01)
        assertEquals(10_000.0, result.netProfit, 0.01)
        assertEquals(365L, result.holdingDays)
    }

    @Test
    fun xirr_irregularMultiDateCashFlows_npvAtSolvedRateIsNearZero() {
        val flows = listOf(
            CashFlowEntry(1L, "2023-04-01", -50_000.0),
            CashFlowEntry(2L, "2023-09-15", -30_000.0),
            CashFlowEntry(3L, "2024-02-10", -20_000.0),
            CashFlowEntry(4L, "2024-08-20", 15_000.0),
            CashFlowEntry(5L, "2025-04-01", 115_000.0)
        )
        val result = XirrEngine.calculate(flows)
        assertTrue(result.isValid)
        assertTrue("Expected positive XIRR, got ${result.xirrPercent}", result.xirrPercent in 12.0..25.0)

        // Independent NPV verification at the solved XIRR
        val r = result.xirrPercent / 100.0
        val day0 = XirrEngine.parseIsoToEpochDay(flows.first().dateIso)!!
        val npv = flows.sumOf { entry ->
            val d = XirrEngine.parseIsoToEpochDay(entry.dateIso)!!
            entry.amount / (1.0 + r).pow((d - day0) / 365.0)
        }
        assertTrue("Expected |NPV| < 25 given 2-decimal rounded xirrPercent, was $npv", abs(npv) < 25.0)
    }

    @Test
    fun xirr_bulkParser_parsesMixedDateFormatsAndIgnoresComments() {
        val raw = """
            # Sample pasted statement
            2024-01-10, -10000, Jan SIP
            10-Feb-2024 -10000 Feb SIP
            10/01/2025; 23500; Value
            invalid line ignored
        """.trimIndent()
        val parsed = XirrEngine.parseBulkCashFlows(raw)
        assertEquals(3, parsed.size)
        assertEquals("2024-01-10", parsed[0].dateIso)
        assertEquals(-10000.0, parsed[0].amount, 0.01)
        assertEquals("2024-02-10", parsed[1].dateIso)
        assertEquals("2025-01-10", parsed[2].dateIso)
        assertEquals(23500.0, parsed[2].amount, 0.01)

        val xirr = XirrEngine.calculate(parsed)
        assertTrue(xirr.isValid)
        assertTrue(xirr.xirrPercent > 15.0)
    }

    // =========================================================================
    // 6. MUTUAL FUND CAPITAL GAINS TAX ENGINE TESTS (DATA-DRIVEN CONFIG)
    // =========================================================================

    private val defaultMfTaxConfig = MfTaxConfig(
        equityLtcgHoldingMonths = 12,
        equityLtcgExemption = 125_000.0,
        equityLtcgRatePercent = 12.5,
        equityStcgRatePercent = 20.0,
        hybridLtcgHoldingMonths = 24,
        hybridLtcgRatePercent = 12.5,
        cessPercent = 4.0
    )

    @Test
    fun mfTax_equityStcg_20PercentPlus4PercentCess() {
        val res = MfTaxGainsEngine.calculate(
            MfTaxInput(
                category = MfFundTaxCategory.EQUITY,
                investedAmount = 500_000.0,
                redemptionValue = 700_000.0, // ₹2,00,000 gain
                holdingMonths = 8
            ),
            defaultMfTaxConfig
        )
        assertTrue(res.isValid)
        assertFalse(res.isLongTerm)
        assertEquals(0.0, res.exemptionApplied, 0.01)
        assertEquals(200_000.0, res.taxableGain, 0.01)
        assertEquals(40_000.0, res.baseTax, 0.01)
        assertEquals(1_600.0, res.cessAmount, 0.01)
        assertEquals(41_600.0, res.totalTax, 0.01)
        assertEquals(658_400.0, res.netPostTaxProceeds, 0.01)
    }

    @Test
    fun mfTax_equityLtcg_applies125kExemptionAnd12Point5PercentPlusCess() {
        val res = MfTaxGainsEngine.calculate(
            MfTaxInput(
                category = MfFundTaxCategory.EQUITY,
                investedAmount = 500_000.0,
                redemptionValue = 825_000.0, // ₹3,25,000 gain -> ₹2,00,000 taxable
                holdingMonths = 18
            ),
            defaultMfTaxConfig
        )
        assertTrue(res.isValid)
        assertTrue(res.isLongTerm)
        assertEquals(125_000.0, res.exemptionApplied, 0.01)
        assertEquals(200_000.0, res.taxableGain, 0.01)
        assertEquals(25_000.0, res.baseTax, 0.01)
        assertEquals(1_000.0, res.cessAmount, 0.01)
        assertEquals(26_000.0, res.totalTax, 0.01)
    }

    @Test
    fun mfTax_equityLtcgUnderExemption_zeroTax() {
        val res = MfTaxGainsEngine.calculate(
            MfTaxInput(
                category = MfFundTaxCategory.EQUITY,
                investedAmount = 500_000.0,
                redemptionValue = 600_000.0, // ₹1,00,000 gain < ₹1.25L
                holdingMonths = 24
            ),
            defaultMfTaxConfig
        )
        assertTrue(res.isValid)
        assertTrue(res.isLongTerm)
        assertEquals(100_000.0, res.exemptionApplied, 0.01)
        assertEquals(0.0, res.taxableGain, 0.01)
        assertEquals(0.0, res.totalTax, 0.01)
    }

    @Test
    fun mfTax_debtFund_sec50AA_alwaysTaxedAtUserSlab() {
        val res = MfTaxGainsEngine.calculate(
            MfTaxInput(
                category = MfFundTaxCategory.DEBT,
                investedAmount = 500_000.0,
                redemptionValue = 700_000.0, // ₹2,00,000 gain
                holdingMonths = 48,
                userTaxSlabPercent = 30.0
            ),
            defaultMfTaxConfig
        )
        assertTrue(res.isValid)
        assertFalse(res.isLongTerm)
        assertEquals(30.0, res.appliedBaseRatePercent, 0.01)
        assertEquals(60_000.0, res.baseTax, 0.01)
        assertEquals(2_400.0, res.cessAmount, 0.01)
        assertEquals(62_400.0, res.totalTax, 0.01)
    }

    @Test
    fun mfTax_hybridGoldLtcg_24MonthsAt12Point5Percent() {
        val res = MfTaxGainsEngine.calculate(
            MfTaxInput(
                category = MfFundTaxCategory.HYBRID_GOLD_INTL,
                investedAmount = 400_000.0,
                redemptionValue = 600_000.0, // ₹2,00,000 gain
                holdingMonths = 30,
                userTaxSlabPercent = 30.0
            ),
            defaultMfTaxConfig
        )
        assertTrue(res.isValid)
        assertTrue(res.isLongTerm)
        assertEquals(12.5, res.appliedBaseRatePercent, 0.01)
        assertEquals(26_000.0, res.totalTax, 0.01)
    }

    // =========================================================================
    // 7. AMFI NAVAll.txt STREAMING PARSER TESTS (WITH FIXTURE FILE)
    // =========================================================================

    @Test
    fun amfiNavParser_streamsFixtureInBatchesAndParsesValidSchemesOnly() {
        val stream = javaClass.classLoader?.getResourceAsStream("amfi_nav_sample.txt")
            ?: File("src/test/resources/amfi_nav_sample.txt").takeIf { it.exists() }?.inputStream()
        assertNotNull("Fixture amfi_nav_sample.txt must be present in test resources", stream)

        val collected = mutableListOf<ParsedAmfiNav>()
        val batchSizes = mutableListOf<Int>()

        val summary = BufferedReader(InputStreamReader(stream!!, StandardCharsets.UTF_8)).use { reader ->
            AmfiNavParser.parseStream(reader = reader, batchSize = 3) { batch ->
                batchSizes.add(batch.size)
                collected.addAll(batch)
            }
        }

        // 8 valid schemes in fixture, 4 invalid/skipped rows (N.A. NAV, BADCODE, 0.0 NAV, IncompleteRow)
        assertEquals(8, summary.totalParsed)
        assertEquals(4, summary.skippedRows)
        assertEquals("24-Nov-2026", summary.latestNavDate)
        assertEquals(3, summary.batchesEmitted) // batches of 3, 3, 2
        assertEquals(listOf(3, 3, 2), batchSizes)
        assertEquals(8, collected.size)

        val ppfas = collected.first { it.schemeCode == "122639" }
        assertEquals("Parag Parikh Mutual Fund", ppfas.amcName)
        assertEquals("Open Ended Schemes(Equity Scheme - Flexi Cap Fund)", ppfas.categoryName)
        assertEquals("INF879O01027", ppfas.isinGrowth)
        assertEquals("", ppfas.isinReinvestment) // '-' normalized to empty string
        assertEquals(89.4521, ppfas.nav, 0.0001)
        assertEquals("24-Nov-2026", ppfas.navDate)

        val iciciIdcw = collected.first { it.schemeCode == "120587" }
        assertEquals("ICICI Prudential Mutual Fund", iciciIdcw.amcName)
        assertEquals("Open Ended Schemes(Debt Scheme - Liquid Fund)", iciciIdcw.categoryName)
        assertEquals("INF109K01Q56", iciciIdcw.isinGrowth)
        assertEquals("INF109K01Q64", iciciIdcw.isinReinvestment)
        assertEquals(100.2145, iciciIdcw.nav, 0.0001)
    }

    // =========================================================================
    // 8. BUNDLED REMOTE-CONFIG JSON VALIDATION FOR NEW MF KEYS
    // =========================================================================

    @Test
    fun bundledTaxSlabsAndRatesJson_containValidMutualFundKeys() {
        val moshi = RemoteConfigMoshi.instance

        val taxFile = listOf(
            File("src/main/assets/remote-config/taxslabs.json"),
            File("app/src/main/assets/remote-config/taxslabs.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Bundled taxslabs.json must exist", taxFile)

        val taxValidation = RemoteConfigValidator.parseAndValidate(taxFile!!.readText(), moshi)
        assertTrue(
            "taxslabs.json failed schema validation: ${taxValidation.exceptionOrNull()?.message}",
            taxValidation.isSuccess
        )
        val taxKeys = taxValidation.getOrThrow().items.map { it.key }.toSet()
        assertTrue(ConfigKeys.TAX_MF_EQUITY_LTCG_MONTHS in taxKeys)
        assertTrue(ConfigKeys.TAX_MF_EQUITY_LTCG_EXEMPTION in taxKeys)
        assertTrue(ConfigKeys.TAX_MF_EQUITY_LTCG_RATE in taxKeys)
        assertTrue(ConfigKeys.TAX_MF_EQUITY_STCG_RATE in taxKeys)
        assertTrue(ConfigKeys.TAX_MF_HYBRID_LTCG_MONTHS in taxKeys)
        assertTrue(ConfigKeys.TAX_MF_HYBRID_LTCG_RATE in taxKeys)

        val ratesFile = listOf(
            File("src/main/assets/remote-config/rates.json"),
            File("app/src/main/assets/remote-config/rates.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Bundled rates.json must exist", ratesFile)

        val ratesValidation = RemoteConfigValidator.parseAndValidate(ratesFile!!.readText(), moshi)
        assertTrue(
            "rates.json failed schema validation: ${ratesValidation.exceptionOrNull()?.message}",
            ratesValidation.isSuccess
        )
        val rateKeys = ratesValidation.getOrThrow().items.map { it.key }.toSet()
        assertTrue(ConfigKeys.MF_EQUITY_EXPECTED_RETURN in rateKeys)
        assertTrue(ConfigKeys.MF_DEFAULT_INFLATION in rateKeys)
    }
}

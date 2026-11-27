package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RemoteConfigMoshi
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.TaxYearRules
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxSuiteEnginesTest {

    private fun loadBundledTaxDoc() = run {
        val file = listOf(
            File("src/main/assets/remote-config/taxslabs.json"),
            File("app/src/main/assets/remote-config/taxslabs.json")
        ).firstOrNull { it.exists() }
        assertNotNull("taxslabs.json must exist", file)
        val res = RemoteConfigValidator.parseAndValidate(file!!.readText(), RemoteConfigMoshi.instance)
        assertTrue("taxslabs.json schema failed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        res.getOrThrow()
    }

    private fun fyRules(code: String): TaxYearRules {
        val doc = loadBundledTaxDoc()
        return doc.taxYears!!.first { it.fyCode == code }
    }

    private fun sampleCatalog(): TaxRulesCatalog {
        val doc = loadBundledTaxDoc()
        return TaxRulesCatalog(
            taxYears = doc.taxYears.orEmpty(),
            gstSlabs = doc.gstSlabs.orEmpty(),
            advanceTaxThreshold = doc.advanceTaxThreshold ?: 10000.0,
            advanceTaxSchedule = doc.advanceTaxSchedule.orEmpty(),
            hraMetroPercent = 50.0,
            hraNonMetroPercent = 40.0,
            fdTdsGeneralThreshold = 50000.0,
            fdTdsSeniorThreshold = 100000.0,
            tdsRateWithPan = 10.0,
            tdsRateWithoutPan = 20.0,
            dataVersion = doc.dataVersion,
            publishedOn = doc.publishedOn,
            asOfDateFormatted = "25 Nov 2026",
            sourceName = "Income Tax Act India",
            sourceUrl = "https://www.incometax.gov.in",
            lastVerified = "UNVERIFIED",
            source = ConfigValueSource.BUILT_IN
        )
    }

    // =========================================================================
    // 1. FY 2026-27 NEW REGIME — EVERY SLAB BOUNDARY
    // =========================================================================

    @Test
    fun fy2026_27_newRegime_allSlabBoundariesExact() {
        val fy = fyRules("FY 2026-27")
        val calcForTaxable = { taxable: Double ->
            IncomeTaxEngine.calculateSingleRegime(
                input = IncomeTaxInput(grossSalary = 0.0, otherIncome = taxable, isSalaried = false),
                regime = fy.newRegime,
                deductionRules = fy.deductions,
                cessPercent = fy.cessPercent
            )
        }

        // ₹4L -> 0% slab
        assertEquals(0.0, calcForTaxable(400_000.0).slabTax, 0.01)
        // ₹8L -> 5% on 4L = ₹20,000 slab tax, ₹0 payable after 87A
        val at8L = calcForTaxable(800_000.0)
        assertEquals(20_000.0, at8L.slabTax, 0.01)
        assertEquals(0.0, at8L.totalTaxPayable, 0.01)

        // ₹12L -> 20k + 10% on 4L = ₹60,000 slab tax, ₹0 payable after 87A
        val at12L = calcForTaxable(1_200_000.0)
        assertEquals(60_000.0, at12L.slabTax, 0.01)
        assertEquals(60_000.0, at12L.rebate87A, 0.01)
        assertEquals(0.0, at12L.totalTaxPayable, 0.01)

        // ₹16L -> 60k + 15% on 4L = ₹1,20,000 + 4% cess = ₹1,24,800
        val at16L = calcForTaxable(1_600_000.0)
        assertEquals(120_000.0, at16L.slabTax, 0.01)
        assertEquals(124_800.0, at16L.totalTaxPayable, 0.01)

        // ₹20L -> 120k + 20% on 4L = ₹2,00,000 + 4% cess = ₹2,08,000
        val at20L = calcForTaxable(2_000_000.0)
        assertEquals(200_000.0, at20L.slabTax, 0.01)
        assertEquals(208_000.0, at20L.totalTaxPayable, 0.01)

        // ₹24L -> 200k + 25% on 4L = ₹3,00,000 + 4% cess = ₹3,12,000
        val at24L = calcForTaxable(2_400_000.0)
        assertEquals(300_000.0, at24L.slabTax, 0.01)
        assertEquals(312_000.0, at24L.totalTaxPayable, 0.01)

        // ₹30L -> 300k + 30% on 6L = ₹4,80,000 + 4% cess = ₹4,99,200
        val at30L = calcForTaxable(3_000_000.0)
        assertEquals(480_000.0, at30L.slabTax, 0.01)
        assertEquals(499_200.0, at30L.totalTaxPayable, 0.01)
    }

    // =========================================================================
    // 2. FY 2026-27 NEW REGIME — SEC 87A REBATE & MARGINAL RELIEF EDGE CASES
    // =========================================================================

    @Test
    fun fy2026_27_newRegime_salariedAt12Point75L_zeroTaxDueToStdDeductionAndRebate() {
        val fy = fyRules("FY 2026-27")
        val calc = IncomeTaxEngine.calculateSingleRegime(
            input = IncomeTaxInput(grossSalary = 1_275_000.0, isSalaried = true),
            regime = fy.newRegime,
            deductionRules = fy.deductions,
            cessPercent = fy.cessPercent
        )
        assertEquals(75_000.0, calc.standardDeduction, 0.01)
        assertEquals(1_200_000.0, calc.taxableIncome, 0.01)
        assertEquals(60_000.0, calc.rebate87A, 0.01)
        assertEquals(0.0, calc.totalTaxPayable, 0.01)
    }

    @Test
    fun fy2026_27_newRegime_marginalReliefEdgeCasesAbove12L() {
        val fy = fyRules("FY 2026-27")
        val calc = { taxable: Double ->
            IncomeTaxEngine.calculateSingleRegime(
                input = IncomeTaxInput(grossSalary = 0.0, otherIncome = taxable, isSalaried = false),
                regime = fy.newRegime,
                deductionRules = fy.deductions,
                cessPercent = fy.cessPercent
            )
        }

        // ₹12,10,000: slab tax = 61,500; excess over 12L = 10,000 => marginal relief = 51,500; tax = 10,000 + 4% cess = 10,400
        val at1210k = calc(1_210_000.0)
        assertEquals(61_500.0, at1210k.slabTax, 0.01)
        assertEquals(51_500.0, at1210k.rebateMarginalRelief, 0.01)
        assertEquals(10_000.0, at1210k.taxAfterRebate, 0.01)
        assertEquals(10_400.0, at1210k.totalTaxPayable, 0.01)

        // ₹12,70,000: slab tax = 60,000 + 15% of 70,000 = 70,500 > 70,000 excess => marginal relief = 500; base tax = 70,000
        val at1270k = calc(1_270_000.0)
        assertEquals(70_500.0, at1270k.slabTax, 0.01)
        assertEquals(500.0, at1270k.rebateMarginalRelief, 0.01)
        assertEquals(70_000.0, at1270k.taxAfterRebate, 0.01)
        assertEquals(72_800.0, at1270k.totalTaxPayable, 0.01)

        // ₹12,75,000: slab tax = 60,000 + 11,250 = 71,250 <= 75,000 excess => NO marginal relief
        val at1275k = calc(1_275_000.0)
        assertEquals(0.0, at1275k.rebateMarginalRelief, 0.01)
        assertEquals(71_250.0, at1275k.taxAfterRebate, 0.01)
        assertEquals(74_100.0, at1275k.totalTaxPayable, 0.01)
    }

    // =========================================================================
    // 3. FY 2024-25 NEW REGIME & OLD REGIME AGE SLABS + REBATE CLIFF
    // =========================================================================

    @Test
    fun fy2024_25_newRegime_slabsAndRebateMarginalReliefAt7L() {
        val fy = fyRules("FY 2024-25")
        val calc = { taxable: Double ->
            IncomeTaxEngine.calculateSingleRegime(
                input = IncomeTaxInput(grossSalary = 0.0, otherIncome = taxable, isSalaried = false),
                regime = fy.newRegime,
                deductionRules = fy.deductions,
                cessPercent = fy.cessPercent
            )
        }

        // ₹7L -> ₹20,000 slab tax, ₹0 payable after 87A
        assertEquals(0.0, calc(700_000.0).totalTaxPayable, 0.01)

        // ₹7,10,000 -> slab tax ₹21,000; excess over 7L = ₹10,000 => marginal relief ₹11,000; payable ₹10,400
        val at710k = calc(710_000.0)
        assertEquals(11_000.0, at710k.rebateMarginalRelief, 0.01)
        assertEquals(10_400.0, at710k.totalTaxPayable, 0.01)

        // ₹15L -> 0 + 20k + 30k + 30k + 60k = ₹1,40,000 + 4% = ₹1,45,600
        assertEquals(145_600.0, calc(1_500_000.0).totalTaxPayable, 0.01)
    }

    @Test
    fun oldRegime_ageSlabsAndStrictRebateCliffAt5L() {
        val fy = fyRules("FY 2026-27")

        val calcOld = { taxable: Double, age: TaxpayerAgeGroup ->
            IncomeTaxEngine.calculateSingleRegime(
                input = IncomeTaxInput(grossSalary = 0.0, otherIncome = taxable, isSalaried = false, ageGroup = age),
                regime = fy.oldRegime,
                deductionRules = fy.deductions,
                cessPercent = fy.cessPercent
            )
        }

        // Exact ₹5,00,000 -> ₹0 tax via 87A
        assertEquals(0.0, calcOld(500_000.0, TaxpayerAgeGroup.GENERAL).totalTaxPayable, 0.01)

        // ₹5,00,001 -> Old Regime has NO marginal relief (strict cliff): ₹12,500.20 + 4% = ₹13,000.21
        val cliff = calcOld(500_001.0, TaxpayerAgeGroup.GENERAL)
        assertEquals(0.0, cliff.rebate87A, 0.01)
        assertEquals(0.0, cliff.rebateMarginalRelief, 0.01)
        assertEquals(12_500.20, cliff.slabTax, 0.01)
        assertEquals(13_000.21, cliff.totalTaxPayable, 0.02)

        // ₹10,00,000 across 3 age groups
        assertEquals(112_500.0, calcOld(1_000_000.0, TaxpayerAgeGroup.GENERAL).slabTax, 0.01)
        assertEquals(110_000.0, calcOld(1_000_000.0, TaxpayerAgeGroup.SENIOR).slabTax, 0.01)
        assertEquals(100_000.0, calcOld(1_000_000.0, TaxpayerAgeGroup.SUPER_SENIOR).slabTax, 0.01)
    }

    // =========================================================================
    // 4. SURCHARGE & SURCHARGE MARGINAL RELIEF + NEW REGIME 25% CAP
    // =========================================================================

    @Test
    fun surcharge_marginalReliefAt50L50k_andNewRegimeCapAt25Percent() {
        val fy = fyRules("FY 2026-27")

        val at50L = IncomeTaxEngine.calculateSingleRegime(
            input = IncomeTaxInput(grossSalary = 0.0, otherIncome = 5_000_000.0, isSalaried = false),
            regime = fy.newRegime,
            deductionRules = fy.deductions,
            cessPercent = fy.cessPercent
        )
        assertEquals(0.0, at50L.surchargeRatePercent, 0.01)
        assertEquals(0.0, at50L.surchargePayable, 0.01)

        // ₹50,50,000: 10% surcharge kicks in; marginal relief limits (tax+surcharge) increase to ₹50,000
        val at50L50k = IncomeTaxEngine.calculateSingleRegime(
            input = IncomeTaxInput(grossSalary = 0.0, otherIncome = 5_050_000.0, isSalaried = false),
            regime = fy.newRegime,
            deductionRules = fy.deductions,
            cessPercent = fy.cessPercent
        )
        assertEquals(10.0, at50L50k.surchargeRatePercent, 0.01)
        assertTrue("Expected positive surcharge marginal relief", at50L50k.surchargeMarginalRelief > 0.0)
        val totalBeforeCess50L = at50L.taxAfterRebate + at50L.surchargePayable
        val totalBeforeCess50L50k = at50L50k.taxAfterRebate + at50L50k.surchargePayable
        assertEquals(50_000.0, totalBeforeCess50L50k - totalBeforeCess50L, 0.05)

        // ₹6 Crore: New Regime caps surcharge at 25% while Old Regime applies 37%
        val comp6Cr = IncomeTaxEngine.calculateComparison(
            input = IncomeTaxInput(grossSalary = 60_000_000.0, isSalaried = true),
            fyRules = fy
        )
        assertTrue(comp6Cr.isValid)
        assertEquals(25.0, comp6Cr.newRegime!!.surchargeRatePercent, 0.01)
        assertEquals(37.0, comp6Cr.oldRegime!!.surchargeRatePercent, 0.01)
        assertEquals("NEW", comp6Cr.recommendedRegimeId)
        assertTrue(comp6Cr.whyBullets.isNotEmpty())
    }

    // =========================================================================
    // 5. DATA-DRIVEN DEDUCTION CAPS & REGIME FILTERING
    // =========================================================================

    @Test
    fun deductions_respectCapsAndRegimeApplicability() {
        val fy = fyRules("FY 2026-27")
        val comp = IncomeTaxEngine.calculateComparison(
            input = IncomeTaxInput(
                grossSalary = 2_000_000.0,
                isSalaried = true,
                deductionAmounts = mapOf(
                    "sec80c" to 250_000.0, // Capped at ₹1,50,000 in Old, 0 in New
                    "sec80ccd2" to 100_000.0, // Allowed in BOTH regimes
                    "sec24b" to 300_000.0, // Capped at ₹2,00,000 in Old, 0 in New
                    "hra" to 200_000.0 // Uncapped in Old, 0 in New
                )
            ),
            fyRules = fy
        )
        assertTrue(comp.isValid)
        // New regime allows only 80CCD(2) = ₹1,00,000
        assertEquals(100_000.0, comp.newRegime!!.otherDeductionsTotal, 0.01)
        // Old regime allows 1.5L + 1L + 2L + 2L = ₹6,50,000
        assertEquals(650_000.0, comp.oldRegime!!.otherDeductionsTotal, 0.01)
        assertEquals(comp.newRegime!!.totalTaxPayable / 12.0, comp.newRegime!!.monthlyTdsEstimate, 0.02)
    }

    // =========================================================================
    // 6. HRA, GST, ADVANCE TAX & TDS ENGINES
    // =========================================================================

    @Test
    fun hra_leastOfThreeRules_metroAndNonMetro() {
        val metro = HraEngine.calculate(
            input = HraInput(
                basicPlusDaAnnual = 600_000.0,
                hraReceivedAnnual = 240_000.0,
                actualRentPaidAnnual = 210_000.0, // Rent - 10% basic = 1,50,000 (least!)
                isMetroCity = true,
                marginalTaxSlabPercent = 30.0
            ),
            metroPercent = 50.0,
            nonMetroPercent = 40.0
        )
        assertTrue(metro.isValid)
        assertEquals(240_000.0, metro.rule1ActualHra, 0.01)
        assertEquals(150_000.0, metro.rule2RentMinusTenPercent, 0.01)
        assertEquals(300_000.0, metro.rule3CitySalaryPercent, 0.01)
        assertEquals(150_000.0, metro.exemptHraAnnual, 0.01)
        assertEquals(90_000.0, metro.taxableHraAnnual, 0.01)
        assertTrue(metro.requiresLandlordPan)

        // Non-metro where 40% of Basic is the least
        val nonMetro = HraEngine.calculate(
            input = HraInput(
                basicPlusDaAnnual = 500_000.0,
                hraReceivedAnnual = 250_000.0,
                actualRentPaidAnnual = 300_000.0,
                isMetroCity = false
            ),
            metroPercent = 50.0,
            nonMetroPercent = 40.0
        )
        assertEquals(200_000.0, nonMetro.rule3CitySalaryPercent, 0.01)
        assertEquals(200_000.0, nonMetro.exemptHraAnnual, 0.01)
    }

    @Test
    fun gst_addAndReverseRoundTrip_cgstSgstVsIgst() {
        val addIntra = GstEngine.calculate(
            GstInput(amount = 10_000.0, gstRatePercent = 18.0, mode = GstCalculationMode.ADD_GST, supplyType = GstSupplyType.INTRA_STATE)
        )
        assertTrue(addIntra.isValid)
        assertEquals(10_000.0, addIntra.baseAmount, 0.01)
        assertEquals(1_800.0, addIntra.totalGstAmount, 0.01)
        assertEquals(900.0, addIntra.cgstAmount, 0.01)
        assertEquals(900.0, addIntra.sgstAmount, 0.01)
        assertEquals(0.0, addIntra.igstAmount, 0.01)
        assertEquals(11_800.0, addIntra.grossAmount, 0.01)

        val removeInter = GstEngine.calculate(
            GstInput(amount = 11_800.0, gstRatePercent = 18.0, mode = GstCalculationMode.REMOVE_GST, supplyType = GstSupplyType.INTER_STATE)
        )
        assertTrue(removeInter.isValid)
        assertEquals(10_000.0, removeInter.baseAmount, 0.01)
        assertEquals(1_800.0, removeInter.totalGstAmount, 0.01)
        assertEquals(1_800.0, removeInter.igstAmount, 0.01)
        assertEquals(0.0, removeInter.cgstAmount, 0.01)
    }

    @Test
    fun advanceTaxAndTds_scheduleThresholdSeniorExemptionAndFdTds() {
        val cat = sampleCatalog()

        // Standard 4-instalment schedule for ₹1,00,000 net tax
        val std = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            input = AdvanceTaxInput(fyCode = "FY 2026-27", estimatedAnnualTax = 120_000.0, tdsTcsDeducted = 20_000.0),
            threshold = cat.advanceTaxThreshold,
            scheduleRules = cat.advanceTaxSchedule
        )
        assertTrue(std.isValid)
        assertFalse(std.isExempt)
        assertEquals(100_000.0, std.netAdvanceTaxPayable, 0.01)
        assertEquals(4, std.installments.size)
        assertEquals(listOf(15_000.0, 30_000.0, 30_000.0, 25_000.0), std.installments.map { it.installmentAmount })
        assertEquals("15 Jun 2026", std.installments[0].dueDateFormatted)
        assertEquals("15 Mar 2027", std.installments[3].dueDateFormatted)

        // Below ₹10,000 threshold -> exempt
        val belowThreshold = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            input = AdvanceTaxInput(estimatedAnnualTax = 18_000.0, tdsTcsDeducted = 10_000.0),
            threshold = cat.advanceTaxThreshold,
            scheduleRules = cat.advanceTaxSchedule
        )
        assertTrue(belowThreshold.isExempt)

        // Senior citizen without business -> exempt Sec 207
        val seniorExempt = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            input = AdvanceTaxInput(estimatedAnnualTax = 150_000.0, tdsTcsDeducted = 0.0, isSeniorWithoutBusiness = true),
            threshold = cat.advanceTaxThreshold,
            scheduleRules = cat.advanceTaxSchedule
        )
        assertTrue(seniorExempt.isExempt)

        // Presumptive Sec 44AD -> single 100% instalment by 15 Mar
        val presumptive = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            input = AdvanceTaxInput(
                fyCode = "FY 2026-27",
                estimatedAnnualTax = 80_000.0,
                tdsTcsDeducted = 0.0,
                isPresumptive44AdAda = true
            ),
            threshold = cat.advanceTaxThreshold,
            scheduleRules = cat.advanceTaxSchedule
        )
        assertEquals(1, presumptive.installments.size)
        assertEquals(80_000.0, presumptive.installments.first().installmentAmount, 0.01)

        // FD TDS quick helper
        val fdGeneral = AdvanceTaxAndTdsEngine.calculateFdTds(75_000.0, isSeniorCitizen = false, hasPan = true, catalog = cat)
        assertTrue(fdGeneral.isTdsApplicable)
        assertEquals(7_500.0, fdGeneral.estimatedTds, 0.01)

        val fdSeniorExempt = AdvanceTaxAndTdsEngine.calculateFdTds(75_000.0, isSeniorCitizen = true, hasPan = true, catalog = cat)
        assertFalse(fdSeniorExempt.isTdsApplicable)
        assertEquals(0.0, fdSeniorExempt.estimatedTds, 0.01)

        val fdNoPan = AdvanceTaxAndTdsEngine.calculateFdTds(75_000.0, isSeniorCitizen = false, hasPan = false, catalog = cat)
        assertEquals(15_000.0, fdNoPan.estimatedTds, 0.01)

        // Salary TDS quick helper
        val sal = AdvanceTaxAndTdsEngine.calculateSalaryTds(120_000.0, 30_000.0, 6)
        assertEquals(90_000.0, sal.remainingTaxPayable, 0.01)
        assertEquals(15_000.0, sal.estimatedMonthlyTds, 0.01)
    }

    @Test
    fun bundledTaxSlabsJson_containsVersionedFYsGstSlabsAndAdvanceSchedule() {
        val doc = loadBundledTaxDoc()
        assertEquals(3, doc.taxYears?.size)
        assertEquals(listOf("FY 2026-27", "FY 2025-26", "FY 2024-25"), doc.taxYears?.map { it.fyCode })
        assertEquals(listOf(0.0, 3.0, 5.0, 12.0, 18.0, 28.0), doc.gstSlabs)
        assertEquals(4, doc.advanceTaxSchedule?.size)
    }
}

package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.AdvanceTaxInstallmentRule
import com.jyotirmoyb.loan.data.remote.SurchargeBand
import com.jyotirmoyb.loan.data.remote.TaxDeductionRule
import com.jyotirmoyb.loan.data.remote.TaxRegimeRules
import com.jyotirmoyb.loan.data.remote.TaxSlabBand
import com.jyotirmoyb.loan.data.remote.TaxYearRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Independent known-answer verification suite for Indian Income Tax, HRA, GST,
 * Advance Tax, and Mutual Fund Capital Gains engines.
 *
 * All statutory rules below are constructed directly from official Finance Act
 * slab definitions (NEVER read from taxslabs.json), and every expected value is
 * worked out step-by-step by hand in comments.
 */
class IndependentTaxKnownAnswerTest {

    // Statutory FY 2025-26 / FY 2026-27 rules per Finance Act 2025 (Sec 115BAC(1A) & Old Regime).
    private val statutoryFyRules = TaxYearRules(
        fyCode = "FY 2026-27",
        ayCode = "AY 2027-28",
        effectiveFrom = "2026-04-01",
        lawReference = "Finance Act 2025",
        cessPercent = 4.0,
        newRegime = TaxRegimeRules(
            regimeId = "NEW",
            displayName = "New Tax Regime (Sec 115BAC)",
            standardDeductionSalaried = 75000.0,
            rebateIncomeLimit = 1200000.0,
            maxRebateAmount = 60000.0,
            rebateMarginalRelief = true,
            surchargeMarginalRelief = true,
            slabsGeneral = listOf(
                TaxSlabBand(upTo = 400000.0, ratePercent = 0.0),
                TaxSlabBand(upTo = 800000.0, ratePercent = 5.0),
                TaxSlabBand(upTo = 1200000.0, ratePercent = 10.0),
                TaxSlabBand(upTo = 1600000.0, ratePercent = 15.0),
                TaxSlabBand(upTo = 2000000.0, ratePercent = 20.0),
                TaxSlabBand(upTo = 2400000.0, ratePercent = 25.0),
                TaxSlabBand(upTo = null, ratePercent = 30.0)
            ),
            surchargeBands = listOf(
                SurchargeBand(aboveIncome = 5000000.0, ratePercent = 10.0),
                SurchargeBand(aboveIncome = 10000000.0, ratePercent = 15.0),
                SurchargeBand(aboveIncome = 20000000.0, ratePercent = 25.0)
            )
        ),
        oldRegime = TaxRegimeRules(
            regimeId = "OLD",
            displayName = "Old Tax Regime",
            standardDeductionSalaried = 50000.0,
            rebateIncomeLimit = 500000.0,
            maxRebateAmount = 12500.0,
            rebateMarginalRelief = false,
            surchargeMarginalRelief = true,
            slabsGeneral = listOf(
                TaxSlabBand(upTo = 250000.0, ratePercent = 0.0),
                TaxSlabBand(upTo = 500000.0, ratePercent = 5.0),
                TaxSlabBand(upTo = 1000000.0, ratePercent = 20.0),
                TaxSlabBand(upTo = null, ratePercent = 30.0)
            ),
            slabsSenior = listOf(
                TaxSlabBand(upTo = 300000.0, ratePercent = 0.0),
                TaxSlabBand(upTo = 500000.0, ratePercent = 5.0),
                TaxSlabBand(upTo = 1000000.0, ratePercent = 20.0),
                TaxSlabBand(upTo = null, ratePercent = 30.0)
            ),
            slabsSuperSenior = listOf(
                TaxSlabBand(upTo = 500000.0, ratePercent = 0.0),
                TaxSlabBand(upTo = 1000000.0, ratePercent = 20.0),
                TaxSlabBand(upTo = null, ratePercent = 30.0)
            ),
            surchargeBands = listOf(
                SurchargeBand(aboveIncome = 5000000.0, ratePercent = 10.0),
                SurchargeBand(aboveIncome = 10000000.0, ratePercent = 15.0),
                SurchargeBand(aboveIncome = 20000000.0, ratePercent = 25.0),
                SurchargeBand(aboveIncome = 50000000.0, ratePercent = 37.0)
            )
        ),
        deductions = listOf(
            TaxDeductionRule(
                id = "sec80c",
                label = "Section 80C",
                section = "80C",
                capGeneral = 150000.0,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "sec80ccd1b",
                label = "NPS Additional",
                section = "80CCD(1B)",
                capGeneral = 50000.0,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "sec80d_self",
                label = "Health Insurance Self",
                section = "80D",
                capGeneral = 25000.0,
                capSenior = 50000.0,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "sec80d_parents",
                label = "Health Insurance Parents",
                section = "80D",
                capGeneral = 25000.0,
                capSenior = 50000.0,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "sec24b",
                label = "Home Loan Interest",
                section = "24(b)",
                capGeneral = 200000.0,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "hra",
                label = "HRA Exemption",
                section = "10(13A)",
                capGeneral = null,
                allowedInOld = true,
                allowedInNew = false
            ),
            TaxDeductionRule(
                id = "sec80ccd2",
                label = "Employer NPS",
                section = "80CCD(2)",
                capGeneral = null,
                allowedInOld = true,
                allowedInNew = true
            )
        )
    )

    private fun calcNew(
        income: Double,
        isSalaried: Boolean = false,
        ageGroup: TaxpayerAgeGroup = TaxpayerAgeGroup.GENERAL,
        deductions: Map<String, Double> = emptyMap()
    ): RegimeTaxCalculation = IncomeTaxEngine.calculateSingleRegime(
        input = IncomeTaxInput(
            grossSalary = if (isSalaried) income else 0.0,
            otherIncome = if (isSalaried) 0.0 else income,
            isSalaried = isSalaried,
            ageGroup = ageGroup,
            deductionAmounts = deductions
        ),
        regime = statutoryFyRules.newRegime,
        deductionRules = statutoryFyRules.deductions,
        cessPercent = statutoryFyRules.cessPercent
    )

    private fun calcOld(
        income: Double,
        isSalaried: Boolean = false,
        ageGroup: TaxpayerAgeGroup = TaxpayerAgeGroup.GENERAL,
        deductions: Map<String, Double> = emptyMap()
    ): RegimeTaxCalculation = IncomeTaxEngine.calculateSingleRegime(
        input = IncomeTaxInput(
            grossSalary = if (isSalaried) income else 0.0,
            otherIncome = if (isSalaried) 0.0 else income,
            isSalaried = isSalaried,
            ageGroup = ageGroup,
            deductionAmounts = deductions
        ),
        regime = statutoryFyRules.oldRegime,
        deductionRules = statutoryFyRules.deductions,
        cessPercent = statutoryFyRules.cessPercent
    )

    @Test
    fun newRegime_slabBoundaryIncomes_matchHandCalculations() {
        // Non-salaried (standard deduction = 0) so taxableIncome == grossIncome.
        // 1) ₹3,99,999 and ₹4,00,000 -> entirely in 0% slab -> slabTax = ₹0.00
        assertEquals(0.0, calcNew(399999.0).slabTax, 0.001)
        assertEquals(0.0, calcNew(400000.0).slabTax, 0.001)

        // 2) ₹4,00,001 -> ₹1 at 5% = ₹0.05; Sec 87A rebate = ₹0.05 -> totalTax = ₹0.00
        val at4LPlus1 = calcNew(400001.0)
        assertEquals(0.05, at4LPlus1.slabTax, 0.001)
        assertEquals(0.05, at4LPlus1.rebate87A, 0.001)
        assertEquals(0.0, at4LPlus1.totalTaxPayable, 0.001)

        // 3) ₹8,00,000 -> 4L * 5% = ₹20,000; full rebate87A = ₹20,000 -> totalTax = ₹0
        val at8L = calcNew(800000.0)
        assertEquals(20000.0, at8L.slabTax, 0.001)
        assertEquals(0.0, at8L.totalTaxPayable, 0.001)

        // 4) ₹8,00,001 -> ₹20,000 + ₹1 * 10% = ₹20,000.10; full rebate87A -> totalTax = ₹0
        val at8LPlus1 = calcNew(800001.0)
        assertEquals(20000.10, at8LPlus1.slabTax, 0.001)
        assertEquals(0.0, at8LPlus1.totalTaxPayable, 0.001)

        // 5) ₹12,00,000 -> 20,000 + 4L * 10% = ₹60,000; rebate87A = ₹60,000 -> totalTax = ₹0
        val at12L = calcNew(1200000.0)
        assertEquals(60000.0, at12L.slabTax, 0.001)
        assertEquals(60000.0, at12L.rebate87A, 0.001)
        assertEquals(0.0, at12L.totalTaxPayable, 0.001)

        // 6) ₹16,00,000 -> 20k + 40k + 4L * 15% (60k) = ₹1,20,000; 4% cess = ₹4,800 -> ₹1,24,800
        val at16L = calcNew(1600000.0)
        assertEquals(120000.0, at16L.slabTax, 0.001)
        assertEquals(4800.0, at16L.cessAmount, 0.001)
        assertEquals(124800.0, at16L.totalTaxPayable, 0.001)

        // 7) ₹16,00,001 -> ₹1,20,000 + ₹1 * 20% = ₹1,20,000.20; 4% cess = ₹4,800.01 -> ₹1,24,800.21
        val at16LPlus1 = calcNew(1600001.0)
        assertEquals(120000.20, at16LPlus1.slabTax, 0.001)
        assertEquals(4800.01, at16LPlus1.cessAmount, 0.001)
        assertEquals(124800.21, at16LPlus1.totalTaxPayable, 0.001)

        // 8) ₹20,00,000 -> 1,20,000 + 4L * 20% (80k) = ₹2,00,000; 4% cess = ₹8,000 -> ₹2,08,000
        val at20L = calcNew(2000000.0)
        assertEquals(200000.0, at20L.slabTax, 0.001)
        assertEquals(208000.0, at20L.totalTaxPayable, 0.001)

        // 9) ₹24,00,000 -> 2,00,000 + 4L * 25% (1,00,000) = ₹3,00,000; 4% cess = ₹12,000 -> ₹3,12,000
        val at24L = calcNew(2400000.0)
        assertEquals(300000.0, at24L.slabTax, 0.001)
        assertEquals(312000.0, at24L.totalTaxPayable, 0.001)

        // 10) ₹24,00,001 -> ₹3,00,000 + ₹1 * 30% = ₹3,00,000.30; 4% cess = ₹12,000.01 -> ₹3,12,000.31
        val at24LPlus1 = calcNew(2400001.0)
        assertEquals(300000.30, at24LPlus1.slabTax, 0.001)
        assertEquals(12000.01, at24LPlus1.cessAmount, 0.001)
        assertEquals(312000.31, at24LPlus1.totalTaxPayable, 0.001)
    }

    @Test
    fun newRegime_rebate87AEdge_andMarginalRelief_matchHandCalculations() {
        // 1) ₹12,00,001 non-salaried:
        //    slabTax = 60,000 + 1 * 15% = ₹60,000.15
        //    excess above ₹12L = ₹1.00
        //    marginalRelief = 60,000.15 - 1.00 = ₹59,999.15
        //    taxAfterRebate = ₹1.00; 4% cess = ₹0.04; totalTax = ₹1.04
        val justAbove = calcNew(1200001.0)
        assertEquals(60000.15, justAbove.slabTax, 0.001)
        assertEquals(0.0, justAbove.rebate87A, 0.001)
        assertEquals(59999.15, justAbove.rebateMarginalRelief, 0.001)
        assertEquals(1.00, justAbove.taxAfterRebate, 0.001)
        assertEquals(0.04, justAbove.cessAmount, 0.001)
        assertEquals(1.04, justAbove.totalTaxPayable, 0.001)

        // 2) ₹12,10,000 non-salaried:
        //    slabTax = 60,000 + 10,000 * 15% = ₹61,500
        //    excess above ₹12L = ₹10,000
        //    marginalRelief = 61,500 - 10,000 = ₹51,500
        //    taxAfterRebate = ₹10,000; 4% cess = ₹400; totalTax = ₹10,400
        val at1210k = calcNew(1210000.0)
        assertEquals(61500.0, at1210k.slabTax, 0.001)
        assertEquals(51500.0, at1210k.rebateMarginalRelief, 0.001)
        assertEquals(10000.0, at1210k.taxAfterRebate, 0.001)
        assertEquals(400.0, at1210k.cessAmount, 0.001)
        assertEquals(10400.0, at1210k.totalTaxPayable, 0.001)

        // 3) Break-even of marginal relief: 60,000 + 0.15*(X - 12L) = X - 12L => 0.85*(X - 12L) = 60,000 => X - 12L = 70,588.24
        //    At ₹12,70,588: slabTax = 60,000 + 70,588 * 0.15 = ₹70,588.20 > ₹70,588 -> relief = ₹0.20
        val atReliefCutoff = calcNew(1270588.0)
        assertEquals(0.20, atReliefCutoff.rebateMarginalRelief, 0.001)
        assertEquals(70588.0, atReliefCutoff.taxAfterRebate, 0.001)

        // 4) ₹12,75,000 non-salaried -> slabTax = 60,000 + 75,000 * 15% = ₹71,250 < ₹75,000 excess -> NO marginal relief!
        //    cess 4% = ₹2,850; totalTax = ₹74,100
        val at1275kNonSal = calcNew(1275000.0, isSalaried = false)
        assertEquals(0.0, at1275kNonSal.rebateMarginalRelief, 0.001)
        assertEquals(71250.0, at1275kNonSal.taxAfterRebate, 0.001)
        assertEquals(2850.0, at1275kNonSal.cessAmount, 0.001)
        assertEquals(74100.0, at1275kNonSal.totalTaxPayable, 0.001)

        // 5) ₹12,75,000 SALARIED -> standard deduction ₹75,000 -> taxableIncome = ₹12,00,000 -> rebate ₹60,000 -> ₹0 tax!
        val at1275kSal = calcNew(1275000.0, isSalaried = true)
        assertEquals(75000.0, at1275kSal.standardDeduction, 0.001)
        assertEquals(1200000.0, at1275kSal.taxableIncome, 0.001)
        assertEquals(60000.0, at1275kSal.rebate87A, 0.001)
        assertEquals(0.0, at1275kSal.totalTaxPayable, 0.001)
    }

    @Test
    fun oldRegime_generalSeniorSuperSeniorSlabs_standardDeduction_andRebateCliff() {
        // 1) Standard deduction: salaried ₹50,000 vs non-salaried ₹0
        //    Salaried ₹5,50,000 -> taxable = ₹5,00,000 -> slabTax = ₹12,500 -> rebate87A = ₹12,500 -> ₹0 tax
        val salaried550k = calcOld(550000.0, isSalaried = true)
        assertEquals(50000.0, salaried550k.standardDeduction, 0.001)
        assertEquals(500000.0, salaried550k.taxableIncome, 0.001)
        assertEquals(12500.0, salaried550k.rebate87A, 0.001)
        assertEquals(0.0, salaried550k.totalTaxPayable, 0.001)

        // 2) Old Regime Sec 87A cliff at ₹5,00,001 (NO marginal relief under Old Regime!):
        //    slabTax = (2,50,000 * 5%) + (1 * 20%) = ₹12,500.20; rebate = 0; relief = 0
        //    4% cess = ₹500.01; totalTax = ₹13,000.21
        val oldCliff = calcOld(500001.0, isSalaried = false)
        assertEquals(12500.20, oldCliff.slabTax, 0.001)
        assertEquals(0.0, oldCliff.rebate87A, 0.001)
        assertEquals(0.0, oldCliff.rebateMarginalRelief, 0.001)
        assertEquals(500.01, oldCliff.cessAmount, 0.001)
        assertEquals(13000.21, oldCliff.totalTaxPayable, 0.001)

        // 3) Boundary ₹2,50,000 / ₹2,50,001 (General)
        assertEquals(0.0, calcOld(250000.0).slabTax, 0.001)
        assertEquals(0.05, calcOld(250001.0).slabTax, 0.001)

        // 4) General vs Senior (60-79) vs Super-Senior (80+) at ₹10,00,000 taxable income:
        //    General:      0..2.5L @0% + 2.5L..5L @5% (12,500) + 5L..10L @20% (1,00,000) = ₹1,12,500; +4% cess (4,500) = ₹1,17,000
        //    Senior:       0..3.0L @0% + 3.0L..5L @5% (10,000) + 5L..10L @20% (1,00,000) = ₹1,10,000; +4% cess (4,400) = ₹1,14,400
        //    Super-Senior: 0..5.0L @0%                         + 5L..10L @20% (1,00,000) = ₹1,00,000; +4% cess (4,000) = ₹1,04,000
        val general10L = calcOld(1000000.0, ageGroup = TaxpayerAgeGroup.GENERAL)
        val senior10L = calcOld(1000000.0, ageGroup = TaxpayerAgeGroup.SENIOR)
        val superSenior10L = calcOld(1000000.0, ageGroup = TaxpayerAgeGroup.SUPER_SENIOR)

        assertEquals(112500.0, general10L.slabTax, 0.001)
        assertEquals(117000.0, general10L.totalTaxPayable, 0.001)

        assertEquals(110000.0, senior10L.slabTax, 0.001)
        assertEquals(114400.0, senior10L.totalTaxPayable, 0.001)

        assertEquals(100000.0, superSenior10L.slabTax, 0.001)
        assertEquals(104000.0, superSenior10L.totalTaxPayable, 0.001)

        // 5) Senior exemption boundary ₹3,00,000 & Super-Senior ₹5,00,000 -> slabTax = ₹0
        assertEquals(0.0, calcOld(300000.0, ageGroup = TaxpayerAgeGroup.SENIOR).slabTax, 0.001)
        assertEquals(0.0, calcOld(500000.0, ageGroup = TaxpayerAgeGroup.SUPER_SENIOR).slabTax, 0.001)

        // 6) Above ₹10L (General, ₹15,00,000 non-salaried):
        //    1,12,500 + 5,00,000 * 30% (1,50,000) = ₹2,62,500; +4% cess (10,500) = ₹2,73,000
        val general15L = calcOld(1500000.0)
        assertEquals(262500.0, general15L.slabTax, 0.001)
        assertEquals(273000.0, general15L.totalTaxPayable, 0.001)
    }

    @Test
    fun surchargeBandEdges_marginalRelief_andNewRegime25PercentCap() {
        // 1) Exactly ₹50,00,000 (New Regime, non-salaried):
        //    slabTax = 3,00,000 + (50L - 24L) * 30% = 3,00,000 + 7,80,000 = ₹10,80,000
        //    surcharge = 0% (threshold is strictly > ₹50L); cess 4% = ₹43,200; total = ₹11,23,200
        val at50L = calcNew(5000000.0)
        assertEquals(1080000.0, at50L.slabTax, 0.001)
        assertEquals(0.0, at50L.surchargeRatePercent, 0.001)
        assertEquals(0.0, at50L.surchargePayable, 0.001)
        assertEquals(1123200.0, at50L.totalTaxPayable, 0.001)

        // 2) ₹51,00,000 (New Regime, non-salaried -> 10% surcharge band with marginal relief):
        //    slabTax = 10,80,000 + 1,00,000 * 30% = ₹11,10,000
        //    rawSurcharge (10%) = ₹1,11,000; actualTotal = ₹12,21,000
        //    maxAllowedTotal = taxAt50L (10,80,000) + excess (1,00,000) = ₹11,80,000
        //    surchargeMarginalRelief = 12,21,000 - 11,80,000 = ₹41,000
        //    netSurcharge = 1,11,000 - 41,000 = ₹70,000
        //    taxPlusSurcharge = ₹11,80,000; cess 4% = ₹47,200; totalTax = ₹12,27,200
        val at51L = calcNew(5100000.0)
        assertEquals(1110000.0, at51L.slabTax, 0.001)
        assertEquals(10.0, at51L.surchargeRatePercent, 0.001)
        assertEquals(111000.0, at51L.surchargeBeforeRelief, 0.001)
        assertEquals(41000.0, at51L.surchargeMarginalRelief, 0.001)
        assertEquals(70000.0, at51L.surchargePayable, 0.001)
        assertEquals(47200.0, at51L.cessAmount, 0.001)
        assertEquals(1227200.0, at51L.totalTaxPayable, 0.001)

        // 3) ₹1,01,00,000 (New Regime, non-salaried -> 15% surcharge band with marginal relief):
        //    At ₹1Cr: baseTax = 3L + 76L * 30% = ₹25,80,000; prev surcharge 10% = ₹2,58,000; totalAtT = ₹28,38,000
        //    At ₹1.01Cr: slabTax = 25,80,000 + 30,000 = ₹26,10,000; rawSurcharge 15% = ₹3,91,500; actual = ₹30,01,500
        //    maxAllowed = 28,38,000 + 1,00,000 = ₹29,38,000; relief = ₹63,500; netSurcharge = ₹3,28,000
        //    cess 4% on ₹29,38,000 = ₹1,17,520; totalTax = ₹30,55,520
        val at101L = calcNew(10100000.0)
        assertEquals(15.0, at101L.surchargeRatePercent, 0.001)
        assertEquals(391500.0, at101L.surchargeBeforeRelief, 0.001)
        assertEquals(63500.0, at101L.surchargeMarginalRelief, 0.001)
        assertEquals(328000.0, at101L.surchargePayable, 0.001)
        assertEquals(117520.0, at101L.cessAmount, 0.001)
        assertEquals(3055520.0, at101L.totalTaxPayable, 0.001)

        // 4) ₹6,00,00,000 (₹6 Cr, non-salaried) -> New Regime caps surcharge at 25% vs Old Regime 37%:
        //    New: slabTax = 3L + 5.76Cr * 30% = ₹1,75,80,000; surcharge 25% = ₹43,95,000 (no relief at 6Cr)
        //         taxPlusSurcharge = ₹2,19,75,000; cess 4% = ₹8,79,000; totalTax = ₹2,28,54,000
        //    Old: slabTax = 1,12,500 + 5.90Cr * 30% = ₹1,78,12,500; surcharge 37% = ₹65,90,625 (no relief at 6Cr)
        //         taxPlusSurcharge = ₹2,44,03,125; cess 4% = ₹9,76,125; totalTax = ₹2,53,79,250
        val cmp = IncomeTaxEngine.calculateComparison(
            IncomeTaxInput(grossSalary = 0.0, otherIncome = 60000000.0, isSalaried = false),
            statutoryFyRules
        )
        assertTrue(cmp.isValid)
        val newRegime = requireNotNull(cmp.newRegime)
        val oldRegime = requireNotNull(cmp.oldRegime)
        assertEquals(25.0, newRegime.surchargeRatePercent, 0.001)
        assertEquals(4395000.0, newRegime.surchargePayable, 0.001)
        assertEquals(22854000.0, newRegime.totalTaxPayable, 0.001)

        assertEquals(37.0, oldRegime.surchargeRatePercent, 0.001)
        assertEquals(6590625.0, oldRegime.surchargePayable, 0.001)
        assertEquals(25379250.0, oldRegime.totalTaxPayable, 0.001)
        assertEquals("NEW", cmp.recommendedRegimeId)
        assertEquals(2525250.0, cmp.annualTaxSavings, 0.001)
    }

    @Test
    fun hraEngine_leastOfThreeRules_metroVsNonMetro_andLandlordPan() {
        // Case 1: Rule 1 (Actual HRA) is the least
        // Basic=₹10L, HRA=₹1.5L, Rent=₹3L, Metro (50%):
        //   R1 = ₹1,50,000; R2 = 3,00,000 - 1,00,000 = ₹2,00,000; R3 = 50% of 10L = ₹5,00,000
        //   Exempt = ₹1,50,000; Taxable = ₹0; Rent > ₹1L => requiresLandlordPan = true
        val case1 = HraEngine.calculate(
            HraInput(basicPlusDaAnnual = 1000000.0, hraReceivedAnnual = 150000.0, actualRentPaidAnnual = 300000.0, isMetroCity = true),
            metroPercent = 50.0,
            nonMetroPercent = 40.0
        )
        assertTrue(case1.isValid)
        assertEquals(150000.0, case1.exemptHraAnnual, 0.001)
        assertEquals(0.0, case1.taxableHraAnnual, 0.001)
        assertTrue(case1.ruleRows[0].isWinningLeast)
        assertTrue(case1.requiresLandlordPan)

        // Case 2: Rule 2 (Rent - 10% Basic) is the least
        // Basic=₹10L, HRA=₹3L, Rent=₹2.2L, Metro (50%), 30% slab + 4% cess:
        //   R1 = ₹3,00,000; R2 = 2,20,000 - 1,00,000 = ₹1,20,000; R3 = ₹5,00,000
        //   Exempt = ₹1,20,000; Taxable = ₹1,80,000; TaxSaved = 1,20,000 * 30% * 1.04 = ₹37,440
        val case2 = HraEngine.calculate(
            HraInput(
                basicPlusDaAnnual = 1000000.0,
                hraReceivedAnnual = 300000.0,
                actualRentPaidAnnual = 220000.0,
                isMetroCity = true,
                marginalTaxSlabPercent = 30.0
            ),
            metroPercent = 50.0,
            nonMetroPercent = 40.0,
            cessPercent = 4.0
        )
        assertEquals(120000.0, case2.exemptHraAnnual, 0.001)
        assertEquals(180000.0, case2.taxableHraAnnual, 0.001)
        assertEquals(37440.0, case2.estimatedTaxSavedAnnual, 0.001)
        assertTrue(case2.ruleRows[1].isWinningLeast)

        // Case 3: Rule 3 (Non-Metro 40% of Basic) is the least
        // Basic=₹6L, HRA=₹3L, Rent=₹3.6L, Non-Metro (40%):
        //   R1 = ₹3,00,000; R2 = 3,60,000 - 60,000 = ₹3,00,000; R3 = 40% of 6L = ₹2,40,000
        //   Exempt = ₹2,40,000; Taxable = ₹60,000
        val case3 = HraEngine.calculate(
            HraInput(basicPlusDaAnnual = 600000.0, hraReceivedAnnual = 300000.0, actualRentPaidAnnual = 360000.0, isMetroCity = false),
            metroPercent = 50.0,
            nonMetroPercent = 40.0
        )
        assertEquals(40.0, case3.appliedCityPercent, 0.001)
        assertEquals(240000.0, case3.exemptHraAnnual, 0.001)
        assertEquals(60000.0, case3.taxableHraAnnual, 0.001)
        assertTrue(case3.ruleRows[2].isWinningLeast)

        // Landlord PAN threshold: exactly ₹1,00,000 => false; ₹1,00,001 => true
        val rentAt1L = HraEngine.calculate(
            HraInput(basicPlusDaAnnual = 500000.0, hraReceivedAnnual = 100000.0, actualRentPaidAnnual = 100000.0),
            50.0,
            40.0
        )
        assertFalse(rentAt1L.requiresLandlordPan)
    }

    @Test
    fun gstEngine_addAndRemove_intraStateAndInterState() {
        // 1) Add 18% GST on ₹10,000 (Intra-State):
        //    base = ₹10,000, totalGst = ₹1,800, CGST = ₹900, SGST = ₹900, IGST = ₹0, gross = ₹11,800
        val add18 = GstEngine.calculate(
            GstInput(amount = 10000.0, gstRatePercent = 18.0, mode = GstCalculationMode.ADD_GST, supplyType = GstSupplyType.INTRA_STATE)
        )
        assertTrue(add18.isValid)
        assertEquals(10000.0, add18.baseAmount, 0.001)
        assertEquals(1800.0, add18.totalGstAmount, 0.001)
        assertEquals(900.0, add18.cgstAmount, 0.001)
        assertEquals(900.0, add18.sgstAmount, 0.001)
        assertEquals(0.0, add18.igstAmount, 0.001)
        assertEquals(11800.0, add18.grossAmount, 0.001)

        // 2) Remove 18% GST from ₹11,800 (Inter-State):
        //    base = 11,800 / 1.18 = ₹10,000, totalGst = ₹1,800, IGST = ₹1,800, CGST = SGST = ₹0
        val rem18 = GstEngine.calculate(
            GstInput(amount = 11800.0, gstRatePercent = 18.0, mode = GstCalculationMode.REMOVE_GST, supplyType = GstSupplyType.INTER_STATE)
        )
        assertEquals(10000.0, rem18.baseAmount, 0.001)
        assertEquals(1800.0, rem18.totalGstAmount, 0.001)
        assertEquals(0.0, rem18.cgstAmount, 0.001)
        assertEquals(0.0, rem18.sgstAmount, 0.001)
        assertEquals(1800.0, rem18.igstAmount, 0.001)

        // 3) Remove 28% GST from ₹12,800 (Intra-State):
        //    base = 12,800 / 1.28 = ₹10,000, totalGst = ₹2,800, CGST = ₹1,400, SGST = ₹1,400
        val rem28 = GstEngine.calculate(
            GstInput(amount = 12800.0, gstRatePercent = 28.0, mode = GstCalculationMode.REMOVE_GST, supplyType = GstSupplyType.INTRA_STATE)
        )
        assertEquals(10000.0, rem28.baseAmount, 0.001)
        assertEquals(2800.0, rem28.totalGstAmount, 0.001)
        assertEquals(1400.0, rem28.cgstAmount, 0.001)
        assertEquals(1400.0, rem28.sgstAmount, 0.001)
    }

    @Test
    fun advanceTaxEngine_scheduleDates_thresholds_seniorAndPresumptiveExemptions() {
        val officialSchedule = listOf(
            AdvanceTaxInstallmentRule("15 Jun", 6, 15, 15.0, "1st Instalment"),
            AdvanceTaxInstallmentRule("15 Sep", 9, 15, 45.0, "2nd Instalment"),
            AdvanceTaxInstallmentRule("15 Dec", 12, 15, 75.0, "3rd Instalment"),
            AdvanceTaxInstallmentRule("15 Mar", 3, 15, 100.0, "4th Instalment")
        )

        // 1) Standard FY 2026-27: estimatedTax ₹1,20,000 - TDS ₹20,000 = ₹1,00,000 net:
        //    15 Jun 2026: 15% = ₹15,000 (inst ₹15,000)
        //    15 Sep 2026: 45% = ₹45,000 (inst ₹30,000)
        //    15 Dec 2026: 75% = ₹75,000 (inst ₹30,000)
        //    15 Mar 2027: 100% = ₹1,00,000 (inst ₹25,000)
        val standard = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            AdvanceTaxInput(fyCode = "FY 2026-27", estimatedAnnualTax = 120000.0, tdsTcsDeducted = 20000.0),
            threshold = 10000.0,
            scheduleRules = officialSchedule
        )
        assertTrue(standard.isValid)
        assertFalse(standard.isExempt)
        assertEquals(4, standard.installments.size)
        assertEquals("15 Jun 2026", standard.installments[0].dueDateFormatted)
        assertEquals(15000.0, standard.installments[0].installmentAmount, 0.001)
        assertEquals("15 Sep 2026", standard.installments[1].dueDateFormatted)
        assertEquals(30000.0, standard.installments[1].installmentAmount, 0.001)
        assertEquals("15 Dec 2026", standard.installments[2].dueDateFormatted)
        assertEquals(30000.0, standard.installments[2].installmentAmount, 0.001)
        assertEquals("15 Mar 2027", standard.installments[3].dueDateFormatted)
        assertEquals(25000.0, standard.installments[3].installmentAmount, 0.001)
        assertEquals(100000.0, standard.installments.sumOf { it.installmentAmount }, 0.001)

        // 2) Sec 208 threshold: net ₹9,999 < ₹10,000 -> exempt; net ₹10,000 -> NOT exempt
        val belowThreshold = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            AdvanceTaxInput(fyCode = "FY 2026-27", estimatedAnnualTax = 19999.0, tdsTcsDeducted = 10000.0),
            threshold = 10000.0,
            scheduleRules = officialSchedule
        )
        assertTrue(belowThreshold.isExempt)

        val atThreshold = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            AdvanceTaxInput(fyCode = "FY 2026-27", estimatedAnnualTax = 20000.0, tdsTcsDeducted = 10000.0),
            threshold = 10000.0,
            scheduleRules = officialSchedule
        )
        assertFalse(atThreshold.isExempt)

        // 3) Sec 207 Senior Citizen without business income -> exempt
        val senior = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            AdvanceTaxInput(
                fyCode = "FY 2026-27",
                estimatedAnnualTax = 200000.0,
                tdsTcsDeducted = 0.0,
                isSeniorWithoutBusiness = true
            ),
            threshold = 10000.0,
            scheduleRules = officialSchedule
        )
        assertTrue(senior.isExempt)

        // 4) Sec 44AD/44ADA presumptive -> single 100% instalment due 15 Mar 2027
        val presumptive = AdvanceTaxAndTdsEngine.calculateAdvanceTax(
            AdvanceTaxInput(
                fyCode = "FY 2026-27",
                estimatedAnnualTax = 100000.0,
                tdsTcsDeducted = 0.0,
                isPresumptive44AdAda = true
            ),
            threshold = 10000.0,
            scheduleRules = officialSchedule
        )
        assertEquals(1, presumptive.installments.size)
        assertEquals("15 Mar 2027", presumptive.installments[0].dueDateFormatted)
        assertEquals(100000.0, presumptive.installments[0].installmentAmount, 0.001)
    }

    @Test
    fun capitalGainsEngine_equityDebtHybridAndLoss_matchFinanceAct2024Rules() {
        // Statutory Finance (No. 2) Act, 2024 capital gains rules (constructed directly, NOT from JSON)
        val rules = MfTaxConfig(
            equityLtcgHoldingMonths = 12,
            equityLtcgExemption = 125000.0,
            equityLtcgRatePercent = 12.5,
            equityStcgRatePercent = 20.0,
            hybridLtcgHoldingMonths = 24,
            hybridLtcgRatePercent = 12.5,
            cessPercent = 4.0
        )

        // 1) Equity STCG (11 months): Buy ₹5L, Sell ₹7L -> gain = ₹2,00,000
        //    baseTax = 2,00,000 * 20% = ₹40,000; cess 4% = ₹1,600; totalTax = ₹41,600; net = ₹6,58,400
        val eqStcg = MfTaxGainsEngine.calculate(
            MfTaxInput(MfFundTaxCategory.EQUITY, investedAmount = 500000.0, redemptionValue = 700000.0, holdingMonths = 11),
            rules
        )
        assertFalse(eqStcg.isLongTerm)
        assertEquals(40000.0, eqStcg.baseTax, 0.001)
        assertEquals(1600.0, eqStcg.cessAmount, 0.001)
        assertEquals(41600.0, eqStcg.totalTax, 0.001)
        assertEquals(658400.0, eqStcg.netPostTaxProceeds, 0.001)

        // 2) Equity LTCG (12 months): Buy ₹5L, Sell ₹9L -> gain = ₹4,00,000
        //    exemption = ₹1,25,000; taxableGain = ₹2,75,000
        //    baseTax = 2,75,000 * 12.5% = ₹34,375; cess 4% = ₹1,375; totalTax = ₹35,750; net = ₹8,64,250
        val eqLtcg = MfTaxGainsEngine.calculate(
            MfTaxInput(MfFundTaxCategory.EQUITY, investedAmount = 500000.0, redemptionValue = 900000.0, holdingMonths = 12),
            rules
        )
        assertTrue(eqLtcg.isLongTerm)
        assertEquals(125000.0, eqLtcg.exemptionApplied, 0.001)
        assertEquals(275000.0, eqLtcg.taxableGain, 0.001)
        assertEquals(34375.0, eqLtcg.baseTax, 0.001)
        assertEquals(1375.0, eqLtcg.cessAmount, 0.001)
        assertEquals(35750.0, eqLtcg.totalTax, 0.001)
        assertEquals(864250.0, eqLtcg.netPostTaxProceeds, 0.001)

        // 3) Debt MF (Sec 50AA, 36m): always taxed at slab rate (30%) regardless of holding period
        //    gain = ₹1,00,000; baseTax = ₹30,000; cess 4% = ₹1,200; totalTax = ₹31,200
        val debt = MfTaxGainsEngine.calculate(
            MfTaxInput(
                MfFundTaxCategory.DEBT,
                investedAmount = 500000.0,
                redemptionValue = 600000.0,
                holdingMonths = 36,
                userTaxSlabPercent = 30.0
            ),
            rules
        )
        assertFalse(debt.isLongTerm)
        assertEquals(31200.0, debt.totalTax, 0.001)

        // 4) Hybrid / Gold boundary: 23m (STCG @30% slab -> ₹62,400) vs 24m (LTCG @12.5% -> ₹26,000)
        val hybridShort = MfTaxGainsEngine.calculate(
            MfTaxInput(
                MfFundTaxCategory.HYBRID_GOLD_INTL,
                investedAmount = 500000.0,
                redemptionValue = 700000.0,
                holdingMonths = 23,
                userTaxSlabPercent = 30.0
            ),
            rules
        )
        val hybridLong = MfTaxGainsEngine.calculate(
            MfTaxInput(
                MfFundTaxCategory.HYBRID_GOLD_INTL,
                investedAmount = 500000.0,
                redemptionValue = 700000.0,
                holdingMonths = 24,
                userTaxSlabPercent = 30.0
            ),
            rules
        )
        assertFalse(hybridShort.isLongTerm)
        assertEquals(62400.0, hybridShort.totalTax, 0.001)
        assertTrue(hybridLong.isLongTerm)
        assertEquals(26000.0, hybridLong.totalTax, 0.001)

        // 5) Capital Loss: Buy ₹5L, Sell ₹4.5L -> gain = -₹50,000, tax = ₹0, net = ₹4,50,000
        val loss = MfTaxGainsEngine.calculate(
            MfTaxInput(MfFundTaxCategory.EQUITY, investedAmount = 500000.0, redemptionValue = 450000.0, holdingMonths = 18),
            rules
        )
        assertEquals(-50000.0, loss.totalGain, 0.001)
        assertEquals(0.0, loss.totalTax, 0.001)
        assertEquals(450000.0, loss.netPostTaxProceeds, 0.001)
    }
}

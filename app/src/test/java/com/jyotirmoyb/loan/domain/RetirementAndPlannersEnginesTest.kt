package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.RemoteConfigMoshi
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetirementAndPlannersEnginesTest {

    private fun loadBundledSchemesDoc() = run {
        val file = listOf(
            File("src/main/assets/remote-config/schemes.json"),
            File("app/src/main/assets/remote-config/schemes.json")
        ).firstOrNull { it.exists() }
        assertNotNull("schemes.json must exist", file)
        val res = RemoteConfigValidator.parseAndValidate(file!!.readText(), RemoteConfigMoshi.instance)
        assertTrue("schemes.json validation failed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        res.getOrThrow()
    }

    // =========================================================================
    // 1. SCHEMES.JSON DATA VALIDATION
    // =========================================================================

    @Test
    fun bundledSchemesJson_containsFullApyPmsymSeniorAndAssetAllocationData() {
        val doc = loadBundledSchemesDoc()
        val apyTable = doc.apyTable.orEmpty()
        val pmsymTable = doc.pmsymTable.orEmpty()
        val seniorSchemes = doc.seniorPensionSchemes.orEmpty()
        val allocMatrix = doc.assetAllocationMatrix.orEmpty()

        assertEquals("APY table should cover ages 18..40", 23, apyTable.size)
        assertEquals(18, apyTable.first().entryAge)
        assertEquals(40, apyTable.last().entryAge)

        assertEquals("PM-SYM table should cover ages 18..40", 23, pmsymTable.size)
        assertEquals(55.0, pmsymTable.first { it.entryAge == 18 }.monthlyContribution, 0.01)
        assertEquals(200.0, pmsymTable.first { it.entryAge == 40 }.monthlyContribution, 0.01)

        val pmvvy = seniorSchemes.firstOrNull { it.id == "pmvvy" }
        assertNotNull("PMVVY rule must exist in schemes.json", pmvvy)
        assertTrue("PMVVY must be marked CLOSED to new entries", pmvvy!!.isClosed)

        val scss = seniorSchemes.firstOrNull { it.id == "scss" }
        assertNotNull("SCSS rule must exist in schemes.json", scss)
        assertFalse("SCSS must be marked OPEN", scss!!.isClosed)

        assertEquals("Asset allocation matrix should cover 5 age bands x 3 risk profiles", 15, allocMatrix.size)
        allocMatrix.forEach { row ->
            val sum = row.equityPercent + row.debtPercent + row.goldPercent + row.cashLiquidPercent
            assertEquals("Row ${row.ageBandLabel}/${row.riskProfile} must sum to 100%", 100.0, sum, 0.01)
        }
    }

    // =========================================================================
    // 2. RETIREMENT PLANNER ENGINE
    // =========================================================================

    @Test
    fun retirementPlanner_zeroRealReturn_exactAnnuityCorpus() {
        // When post-retirement return == inflation (6% == 6%), real return is 0%.
        // Required corpus == monthlyExpenseAtRetirement * postRetirementMonths
        val input = RetirementPlannerInput(
            currentAge = 35,
            retirementAge = 60, // 25 yrs
            lifeExpectancy = 85, // 25 yrs = 300 months
            currentMonthlyExpenses = 50000.0,
            existingRetirementSavings = 0.0,
            currentMonthlySip = 0.0,
            inflationPercent = 6.0,
            preRetirementReturnPercent = 12.0,
            postRetirementReturnPercent = 6.0
        )
        val res = RetirementPlannerEngine.calculate(input)
        assertTrue(res.isValid)
        assertEquals(25, res.yearsToRetirement)
        assertEquals(25, res.retirementDurationYears)
        assertEquals(0.0, res.realPostRetirementReturnPercent, 0.01)

        val expectedCorpus = res.monthlyExpenseAtRetirement * 300.0
        assertEquals(expectedCorpus, res.requiredCorpusAtRetirement, 1.0)
        assertEquals(res.requiredCorpusAtRetirement, res.corpusShortfall, 1.0)
        assertEquals(0.0, res.corpusSurplus, 0.01)
        assertTrue(res.monthlySipNeededTotal > 0.0)
        assertEquals(26, res.projectionPoints.size) // age 35..60 inclusive
    }

    @Test
    fun retirementPlanner_positiveVsNegativeRealReturn_ordersRequiredCorpusCorrectly() {
        val base = RetirementPlannerInput(
            currentAge = 30,
            retirementAge = 60,
            lifeExpectancy = 85,
            currentMonthlyExpenses = 60000.0,
            existingRetirementSavings = 500000.0,
            currentMonthlySip = 15000.0,
            inflationPercent = 6.0,
            preRetirementReturnPercent = 12.0
        )
        val positiveReal = RetirementPlannerEngine.calculate(base.copy(postRetirementReturnPercent = 8.0))
        val zeroReal = RetirementPlannerEngine.calculate(base.copy(postRetirementReturnPercent = 6.0))
        val negativeReal = RetirementPlannerEngine.calculate(base.copy(postRetirementReturnPercent = 4.0))

        assertTrue(positiveReal.requiredCorpusAtRetirement < zeroReal.requiredCorpusAtRetirement)
        assertTrue(zeroReal.requiredCorpusAtRetirement < negativeReal.requiredCorpusAtRetirement)
    }

    @Test
    fun retirementPlanner_investingRecommendedSip_closesShortfallToZero() {
        val initial = RetirementPlannerInput(
            currentAge = 30,
            retirementAge = 60,
            lifeExpectancy = 85,
            currentMonthlyExpenses = 60000.0,
            existingRetirementSavings = 500000.0,
            currentMonthlySip = 10000.0,
            inflationPercent = 6.0,
            preRetirementReturnPercent = 12.0,
            postRetirementReturnPercent = 7.5
        )
        val firstPass = RetirementPlannerEngine.calculate(initial)
        assertTrue(firstPass.corpusShortfall > 0.0)

        val fundedPass = RetirementPlannerEngine.calculate(
            initial.copy(currentMonthlySip = firstPass.monthlySipNeededTotal)
        )
        assertTrue(fundedPass.isValid)
        assertTrue("Shortfall should be near zero when investing recommended SIP", fundedPass.corpusShortfall <= 50.0)
        assertEquals(0.0, fundedPass.additionalMonthlySipNeeded, 1.0)
    }

    @Test
    fun retirementPlanner_invalidAges_returnsValidationErrors() {
        val invalid = RetirementPlannerEngine.calculate(
            RetirementPlannerInput(currentAge = 60, retirementAge = 55, lifeExpectancy = 50)
        )
        assertFalse(invalid.isValid)
        assertTrue(invalid.validationErrors.isNotEmpty())
    }

    // =========================================================================
    // 3. NPS ENGINE
    // =========================================================================

    @Test
    fun npsEngine_enforcesMandatoryMinAnnuityAboveThreshold() {
        val input = NpsInput(
            currentAge = 30,
            retirementAge = 60,
            monthlyContribution = 10000.0,
            annualStepUpPercent = 0.0,
            existingCorpus = 0.0,
            expectedReturnPercent = 10.0,
            annuityAllocationPercent = 10.0, // Below statutory 40%
            annuityRatePercent = 6.0
        )
        val res = NpsEngine.calculate(input, minAnnuityPercent = 40.0, smallCorpusFullExitLimit = 500000.0)
        assertTrue(res.isValid)
        assertFalse(res.eligibleForFullLumpSumExit)
        assertEquals(40.0, res.annuityPercentApplied, 0.01)
        assertEquals(60.0, res.lumpSumPercentApplied, 0.01)
        assertEquals(res.projectedCorpus, res.lumpSumWithdrawal + res.annuityCorpus, 1.0)
        // Monthly pension == annuityCorpus * 6% / 12
        val expectedMonthlyPension = res.annuityCorpus * 0.06 / 12.0
        assertEquals(expectedMonthlyPension, res.estimatedMonthlyPension, 1.0)
        assertEquals(30, res.yearlyRows.size)
    }

    @Test
    fun npsEngine_smallCorpusUnderLimit_allowsFullLumpSumExit() {
        val input = NpsInput(
            currentAge = 55,
            retirementAge = 60,
            monthlyContribution = 2000.0,
            annualStepUpPercent = 0.0,
            existingCorpus = 0.0,
            expectedReturnPercent = 8.0,
            annuityAllocationPercent = 0.0,
            annuityRatePercent = 6.0
        )
        val res = NpsEngine.calculate(input, minAnnuityPercent = 40.0, smallCorpusFullExitLimit = 500000.0)
        assertTrue(res.isValid)
        assertTrue(res.projectedCorpus <= 500000.0)
        assertTrue(res.eligibleForFullLumpSumExit)
        assertEquals(0.0, res.annuityPercentApplied, 0.01)
        assertEquals(res.projectedCorpus, res.lumpSumWithdrawal, 0.01)
        assertEquals(0.0, res.estimatedMonthlyPension, 0.01)
    }

    // =========================================================================
    // 4. EPF ENGINE
    // =========================================================================

    @Test
    fun epfEngine_wageCeilingToggle_capsMonthlyContributionsCorrectly() {
        val rules = EpfConfigRules(
            employeePercent = 12.0,
            employerEpfPercent = 3.67,
            employerEpsPercent = 8.33,
            wageCeilingMonthly = 15000.0,
            epsMonthlyCap = 1250.0
        )
        val base = EpfInput(
            currentAge = 30,
            retirementAge = 58,
            monthlyBasicPlusDa = 50000.0,
            existingEpfBalance = 0.0,
            annualSalaryGrowthPercent = 0.0,
            vpfExtraMonthly = 0.0,
            epfInterestRatePercent = 8.25
        )
        val uncapped = EpfEngine.calculate(base.copy(applyWageCeiling = false), rules)
        val capped = EpfEngine.calculate(base.copy(applyWageCeiling = true), rules)

        assertTrue(uncapped.isValid && capped.isValid)
        // Uncapped: employee = 12% of 50,000 = 6,000; EPS capped at 1,250; employer EPF = 6,000 - 1,250 = 4,750
        assertEquals(6000.0, uncapped.firstMonthEmployeeEpf, 0.5)
        assertEquals(1250.0, uncapped.firstMonthEmployerEps, 0.5)
        assertEquals(4750.0, uncapped.firstMonthEmployerEpf, 0.5)

        // Capped at 15,000: employee = 1,800; EPS = 1,249.50 (8.33% of 15,000); employer EPF = 550.50
        assertEquals(1800.0, capped.firstMonthEmployeeEpf, 0.5)
        assertEquals(1249.50, capped.firstMonthEmployerEps, 0.5)
        assertEquals(550.50, capped.firstMonthEmployerEpf, 0.5)

        assertTrue(uncapped.projectedEpfCorpus > capped.projectedEpfCorpus)
        assertTrue(uncapped.epsNote.contains("EPS-95"))
    }

    // =========================================================================
    // 5. GOVERNMENT PENSION SCHEMES (APY, PM-SYM, PMVVY & SENIOR SCHEMES)
    // =========================================================================

    @Test
    fun apyEngine_officialPfrdaTableLookups() {
        val doc = loadBundledSchemesDoc()
        val apyTable = doc.apyTable!!
        val multipliers = mapOf(1000 to 170000.0, 2000 to 340000.0, 3000 to 510000.0, 4000 to 680000.0, 5000 to 850000.0)

        val age18Slab1k = PensionSchemesEngine.calculateApy(18, 1000, apyTable, multipliers)
        assertTrue(age18Slab1k.isValid)
        assertEquals(42.0, age18Slab1k.monthlyContribution, 0.01)
        assertEquals(42, age18Slab1k.contributionYears)
        assertEquals(170000.0, age18Slab1k.indicativeNomineeCorpus, 0.01)

        val age18Slab5k = PensionSchemesEngine.calculateApy(18, 5000, apyTable, multipliers)
        assertEquals(210.0, age18Slab5k.monthlyContribution, 0.01)
        assertEquals(850000.0, age18Slab5k.indicativeNomineeCorpus, 0.01)

        val age40Slab5k = PensionSchemesEngine.calculateApy(40, 5000, apyTable, multipliers)
        assertEquals(1454.0, age40Slab5k.monthlyContribution, 0.01)
        assertEquals(20, age40Slab5k.contributionYears)

        val invalidAge = PensionSchemesEngine.calculateApy(45, 5000, apyTable, multipliers)
        assertFalse(invalidAge.isValid)
    }

    @Test
    fun pmsymEngine_oneToOneGovtMatchAndIncomeCeiling() {
        val doc = loadBundledSchemesDoc()
        val table = doc.pmsymTable!!

        val eligible = PensionSchemesEngine.calculatePmsym(
            entryAge = 18,
            monthlyIncome = 12000.0,
            pmsymTable = table,
            assuredPension = 3000.0,
            incomeCeiling = 15000.0
        )
        assertTrue(eligible.isValid)
        assertTrue(eligible.isIncomeEligible)
        assertEquals(55.0, eligible.subscriberMonthlyContribution, 0.01)
        assertEquals(55.0, eligible.govtMatchingMonthlyContribution, 0.01)
        assertEquals(eligible.totalSubscriberContribution, eligible.totalGovtContribution, 0.01)
        assertEquals(3000.0, eligible.assuredMonthlyPension, 0.01)

        val ineligible = PensionSchemesEngine.calculatePmsym(
            entryAge = 40,
            monthlyIncome = 22000.0,
            pmsymTable = table,
            assuredPension = 3000.0,
            incomeCeiling = 15000.0
        )
        assertTrue(ineligible.isValid)
        assertFalse(ineligible.isIncomeEligible)
        assertEquals(200.0, ineligible.subscriberMonthlyContribution, 0.01)
    }

    @Test
    fun seniorSchemes_pmvvyClosedStatusAndScssQuarterlyPayoutWithCap() {
        val doc = loadBundledSchemesDoc()
        val schemes = doc.seniorPensionSchemes!!
        val pmvvy = schemes.first { it.id == "pmvvy" }
        val scss = schemes.first { it.id == "scss" }

        assertTrue(pmvvy.isClosed)
        val pmvvyRes = PensionSchemesEngine.calculateSeniorSchemePayout(pmvvy, 2000000.0)
        assertTrue(pmvvyRes.isValid)
        assertTrue("20L exceeds PMVVY 15L cap", pmvvyRes.exceedsCap)
        assertEquals(1500000.0, pmvvyRes.effectiveCappedAmount, 0.01)

        assertFalse(scss.isClosed)
        // SCSS: 30,00,000 at 8.2% => annual 2,46,000 => quarterly 61,500
        val scssRes = PensionSchemesEngine.calculateSeniorSchemePayout(scss, 3000000.0)
        assertFalse(scssRes.exceedsCap)
        assertEquals(246000.0, scssRes.annualInterest, 1.0)
        assertEquals(61500.0, scssRes.periodicPayout, 1.0)
    }

    // =========================================================================
    // 6. MULTI-GOAL PLANNER ENGINE
    // =========================================================================

    @Test
    fun multiGoalPlanner_inflationAndCombinedSipOnlyIncludesEnabledGoals() {
        val g1 = FinancialGoalItem(
            id = "g1",
            name = "Education",
            category = GoalCategory.CHILD_EDUCATION,
            currentCost = 1000000.0,
            yearsToGoal = 10,
            inflationPercent = 6.0,
            currentSavings = 0.0,
            expectedReturnPercent = 12.0,
            enabled = true
        )
        val g2 = FinancialGoalItem(
            id = "g2",
            name = "Already Funded Car",
            category = GoalCategory.CAR,
            currentCost = 500000.0,
            yearsToGoal = 5,
            inflationPercent = 5.0,
            currentSavings = 600000.0, // FV grows well above target
            expectedReturnPercent = 9.0,
            enabled = true
        )
        val g3Disabled = FinancialGoalItem(
            id = "g3",
            name = "Paused Travel",
            category = GoalCategory.TRAVEL,
            currentCost = 400000.0,
            yearsToGoal = 3,
            inflationPercent = 6.0,
            currentSavings = 0.0,
            expectedReturnPercent = 9.0,
            enabled = false
        )

        val summary = MultiGoalPlannerEngine.calculateAll(listOf(g1, g2, g3Disabled))
        assertEquals(2, summary.activeGoalCount)

        val r1 = summary.goalResults.first { it.goal.id == "g1" }
        val r2 = summary.goalResults.first { it.goal.id == "g2" }
        // 10,00,000 * (1.06)^10 = 17,90,847.70
        assertEquals(1790847.70, r1.inflationAdjustedTarget, 2.0)
        assertTrue(r1.monthlySipNeeded > 0.0)

        assertTrue(r2.isAlreadyFunded)
        assertEquals(0.0, r2.monthlySipNeeded, 0.01)

        // Combined SIP excludes disabled goal g3 and equals r1 + r2
        assertEquals(r1.monthlySipNeeded, summary.combinedMonthlySipNeeded, 0.01)
    }

    // =========================================================================
    // 7. FINANCIAL RULES ENGINE (50/30/20, EMERGENCY FUND, INSURANCE COVER)
    // =========================================================================

    @Test
    fun financialRules_budgetSplit_sumsExactlyAndRejectsInvalidTotal() {
        val valid = FinancialRulesEngine.calculateBudgetSplit(100000.0, 50.0, 30.0, 20.0)
        assertTrue(valid.isValid)
        assertEquals(50000.0, valid.needsMonthly, 0.01)
        assertEquals(30000.0, valid.wantsMonthly, 0.01)
        assertEquals(20000.0, valid.savingsMonthly, 0.01)
        assertEquals(valid.monthlyIncome, valid.needsMonthly + valid.wantsMonthly + valid.savingsMonthly, 0.01)

        // Odd rupee income rounding absorption check
        val odd = FinancialRulesEngine.calculateBudgetSplit(99999.0, 33.33, 33.33, 33.34)
        assertTrue(odd.isValid)
        assertEquals(99999.0, odd.needsMonthly + odd.wantsMonthly + odd.savingsMonthly, 0.01)

        val invalid = FinancialRulesEngine.calculateBudgetSplit(100000.0, 60.0, 30.0, 20.0)
        assertFalse(invalid.isValid)
        assertNotNull(invalid.validationError)
    }

    @Test
    fun financialRules_emergencyFundAndInsuranceCover() {
        val emg = FinancialRulesEngine.calculateEmergencyFund(
            monthlyEssentialExpenses = 40000.0,
            targetMonths = 6,
            currentLiquidSavings = 100000.0,
            monthlyAllocation = 20000.0
        )
        assertEquals(240000.0, emg.targetCorpus, 0.01)
        assertEquals(140000.0, emg.shortfall, 0.01)
        assertEquals(7, emg.monthsToBuild)

        val noAlloc = FinancialRulesEngine.calculateEmergencyFund(40000.0, 6, 100000.0, 0.0)
        assertNull(noAlloc.monthsToBuild)

        val insMetro = FinancialRulesEngine.calculateInsuranceCover(
            annualIncome = 1200000.0,
            existingLiabilities = 2000000.0,
            existingTermCover = 5000000.0,
            termIncomeMultiplier = 12.0,
            isMetroCity = true,
            familyMembersCount = 4, // 2 extra members => 1.5x base
            existingHealthCover = 500000.0,
            metroBaseHealthCover = 1000000.0,
            nonMetroBaseHealthCover = 500000.0
        )
        // Term: 12L * 12 + 20L = 1.64 Cr; gap = 1.14 Cr
        assertEquals(16400000.0, insMetro.recommendedTermCover, 0.01)
        assertEquals(11400000.0, insMetro.termCoverGap, 0.01)
        // Health: 10L * 1.5 = 15L; gap = 10L
        assertEquals(1500000.0, insMetro.recommendedHealthCover, 0.01)
        assertEquals(1000000.0, insMetro.healthCoverGap, 0.01)
    }

    // =========================================================================
    // 8. EDUCATIONAL ASSET ALLOCATION ENGINE ("WHERE TO INVEST")
    // =========================================================================

    @Test
    fun assetAllocation_riskScoringBoundariesAndMatrixResolution() {
        val doc = loadBundledSchemesDoc()
        val matrix = doc.assetAllocationMatrix!!

        val allLow = AssetAllocationEngine.questionnaire.associate { it.id to 1 }
        val (scoreLow, profileLow) = AssetAllocationEngine.scoreRiskProfile(allLow)
        assertEquals(5, scoreLow)
        assertEquals(RiskProfile.CONSERVATIVE, profileLow)

        val allMid = AssetAllocationEngine.questionnaire.associate { it.id to 2 }
        val (scoreMid, profileMid) = AssetAllocationEngine.scoreRiskProfile(allMid)
        assertEquals(10, scoreMid)
        assertEquals(RiskProfile.MODERATE, profileMid)

        val allHigh = AssetAllocationEngine.questionnaire.associate { it.id to 3 }
        val (scoreHigh, profileHigh) = AssetAllocationEngine.scoreRiskProfile(allHigh)
        assertEquals(15, scoreHigh)
        assertEquals(RiskProfile.AGGRESSIVE, profileHigh)

        val youngAggressive = AssetAllocationEngine.resolveAllocation(
            age = 26,
            riskProfile = RiskProfile.AGGRESSIVE,
            questionnaireScore = 15,
            investableAmount = 100000.0,
            matrix = matrix
        )
        val seniorConservative = AssetAllocationEngine.resolveAllocation(
            age = 66,
            riskProfile = RiskProfile.CONSERVATIVE,
            questionnaireScore = 5,
            investableAmount = 100000.0,
            matrix = matrix
        )
        assertTrue(youngAggressive.isValid && seniorConservative.isValid)
        assertTrue(
            "Younger aggressive profile should allocate more to equity than senior conservative",
            youngAggressive.equityPercent > seniorConservative.equityPercent
        )
        val sumAmounts = youngAggressive.equityAmount + youngAggressive.debtAmount +
            youngAggressive.goldAmount + youngAggressive.cashLiquidAmount
        assertEquals(100000.0, sumAmounts, 0.01)
    }
}

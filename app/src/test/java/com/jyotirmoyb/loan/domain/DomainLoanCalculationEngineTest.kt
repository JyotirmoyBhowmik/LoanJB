package com.jyotirmoyb.loan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLoanCalculationEngineTest {

    @Test
    fun calculateEmi_matchesExactStandardReducingFormula() {
        // Rs 28,00,000 @ 10.5% p.a. for 6 years (72 months) -> Rs 52,581.11
        val emi = LoanCalculationEngine.calculateEmi(
            principal = 2_800_000.0,
            annualRatePercent = 10.5,
            tenureMonths = 72
        )
        assertEquals(52_581.11, emi, 0.02)

        // Zero-interest edge case -> principal / tenureMonths
        val zeroRateEmi = LoanCalculationEngine.calculateEmi(
            principal = 120_000.0,
            annualRatePercent = 0.0,
            tenureMonths = 12
        )
        assertEquals(10_000.0, zeroRateEmi, 0.01)
    }

    @Test
    fun calculateFixedRateHomeLoan_generatesFullScheduleClosingAtZero() {
        val request = FixedRateLoanRequest(
            principal = 5_000_000.0,
            annualInterestRatePercent = 9.0,
            tenureMonths = 240,
            propertyValue = 6_500_000.0,
            processingFeePercent = 0.5,
            startYear = 2026,
            startMonth = 10
        )

        val result = LoanCalculationEngine.calculateFixedRateLoan(
            request = request,
            category = HomeLoanCatalogDefaults.homeLoanCategory
        )

        assertTrue(result.isValid)
        assertEquals(44_986.30, result.monthlyEmi, 0.05)
        assertEquals(240, result.schedule.size)
        assertEquals(20, result.yearlySummaries.size)
        // Month 1 interest = 50,00,000 * 9% / 12 = 37,500
        assertEquals(37_500.0, result.schedule.first().interestComponent, 0.01)
        assertEquals(0.0, result.schedule.last().closingBalance, 0.001)
        assertEquals(5_000_000.0, result.totalPrincipal, 0.01)
        assertEquals(25_000.0, result.processingFeeAmount, 0.01)
        assertEquals(1_500_000.0, result.downPayment, 0.01)
        assertFalse(result.isLtvExceeded)
        assertEquals("YEAR 1 · Oct – Sep", result.yearlySummaries.first().label)
    }

    @Test
    fun calculateFixedRateHomeLoan_enforcesHomeLoanTenureAndLtvRules() {
        val invalidRequest = FixedRateLoanRequest(
            principal = 9_000_000.0,
            annualInterestRatePercent = 9.5,
            tenureMonths = 36, // Below Home Loan 60-month minimum
            propertyValue = 10_000_000.0 // 90% LTV > 80% cap
        )

        val result = LoanCalculationEngine.calculateFixedRateLoan(
            request = invalidRequest,
            category = HomeLoanCatalogDefaults.homeLoanCategory
        )

        assertFalse(result.isValid)
        assertTrue(result.isLtvExceeded)
        assertEquals(2, result.validationErrors.size)
    }
}

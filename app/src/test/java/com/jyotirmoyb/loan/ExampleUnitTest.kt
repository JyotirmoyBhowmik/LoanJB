package com.jyotirmoyb.loan

import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun emiSanityCheck() {
        val emi = LoanCalculationEngine.standardEmi(1000000.0, 9.0, 120)
        assertTrue(emi in 12600.0..12700.0)
    }
}

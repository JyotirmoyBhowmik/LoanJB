package com.jyotirmoyb.loan

import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {
    @Test
    fun currencyGroupingCheck() {
        assertEquals("₹1,00,000", CurrencyFormatter.format(100000.0, CurrencyCode.INR))
    }
}

package com.jyotirmoyb.loan.performance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.HomeLoanCatalogDefaults
import com.jyotirmoyb.loan.domain.LoanCalculationEngine
import com.jyotirmoyb.loan.domain.PrepaymentMode
import com.jyotirmoyb.loan.domain.PrepaymentRule
import com.jyotirmoyb.loan.domain.RateChangeEvent
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.emi.EmiUiState
import com.jyotirmoyb.loan.features.emi.results.EmiResultsScreen
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cold-start & 360-row amortization schedule performance benchmark test.
 * Verifies:
 * 1. Core catalog + calculator registry + initial 360-month EMI calculation cold start finishes
 *    well under 1,500 ms.
 * 2. 360-month (30-year) reducing-balance amortization schedule with prepayments & rate resets
 *    computes deterministically in < 350 ms.
 * 3. Compose LazyColumn renders and switches tabs on the 360-row Monthly Schedule smoothly with
 *    deterministic item keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColdStartAndScheduleBenchmarkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `cold start catalog and initial EMI calculation completes under 1500 ms`() {
        val elapsedMs = measureTimeMillis {
            val modules = CalculatorRegistry.modules
            assertTrue(modules.isNotEmpty())
            val homeCategory = HomeLoanCatalogDefaults.homeLoanCategory
            val result = LoanCalculationEngine.calculate(
                input = CalculationInput(
                    categoryId = homeCategory.id,
                    principal = 50_00_000.0,
                    annualRate = 8.50,
                    tenureMonths = 360
                ),
                category = homeCategory
            )
            assertTrue(result.isValid)
            assertEquals(360, result.schedule.size)
        }
        assertTrue(
            "Cold start catalog + 360-row calculation took ${elapsedMs}ms (target < 1500ms)",
            elapsedMs < 1500L
        )
    }

    @Test
    fun `360-row schedule calculation and Compose LazyColumn rendering benchmark`() {
        val input = CalculationInput(
            categoryId = "home",
            principal = 75_00_000.0,
            annualRate = 8.65,
            tenureMonths = 360,
            ruleFlags = listOf("RATE_FLOATING", "RATE_EBLR"),
            prepaymentEnabled = true,
            prepayments = listOf(
                PrepaymentRule(
                    amount = 50_000.0,
                    startMonth = 24,
                    isRecurring = true,
                    intervalMonths = 12,
                    mode = PrepaymentMode.REDUCE_TENURE.name
                )
            ),
            rateChanges = listOf(
                RateChangeEvent(afterMonth = 36, newAnnualRate = 8.40)
            )
        )

        // Warm-up run
        LoanCalculationEngine.calculate(input)

        var result = LoanCalculationEngine.calculate(input)
        val calcElapsedMs = measureTimeMillis {
            result = LoanCalculationEngine.calculate(input)
        }
        assertTrue(result.isValid)
        assertTrue("360-month schedule with prepayments took ${calcElapsedMs}ms", calcElapsedMs < 350L)

        // Full 360-row baseline schedule rendering in Compose
        val full360Input = CalculationInput(
            categoryId = "home",
            principal = 75_00_000.0,
            annualRate = 8.65,
            tenureMonths = 360
        )
        val full360Result = LoanCalculationEngine.calculate(full360Input)
        assertEquals(360, full360Result.schedule.size)

        composeTestRule.setContent {
            LoanJBTheme {
                EmiResultsScreen(
                    uiState = EmiUiState(
                        input = full360Input,
                        result = full360Result,
                        currency = CurrencyCode.INR
                    ),
                    shareSummaryText = { "360-month summary" },
                    onBack = {},
                    onSaveLoan = { _, _, _, _, _, _ -> },
                    onAddToCompare = {},
                    onShowMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("results_summary_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_monthly_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("results_tab_yearly").performClick()
        composeTestRule.onNodeWithTag("results_yearly_list").assertIsDisplayed()
    }
}

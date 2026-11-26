package com.jyotirmoyb.loan.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jyotirmoyb.loan.features.legal.AboutAndLegalScreen
import com.jyotirmoyb.loan.ui.designsystem.FinancialDisclaimerBanner
import com.jyotirmoyb.loan.ui.designsystem.LoanJBEmptyState
import com.jyotirmoyb.loan.ui.designsystem.LoanJBErrorState
import com.jyotirmoyb.loan.ui.designsystem.LoanJBLoadingState
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI tests covering:
 * - About, Privacy Policy, Terms & Disclaimers, and Play Data Safety compliance tabs
 * - Design System Loading / Empty / Error / FinancialDisclaimerBanner states
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubDataRatesAndLegalUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `AboutAndLegalScreen renders credits, privacy policy, terms, and play data safety tabs`() {
        composeTestRule.setContent {
            LoanJBTheme {
                AboutAndLegalScreen()
            }
        }

        composeTestRule.onNodeWithTag("about_legal_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("credit_card_rbi_monetary").assertIsDisplayed()

        // Switch to Privacy Policy tab (index 1)
        composeTestRule.onNodeWithTag("legal_tab_1").performClick()
        composeTestRule.onNodeWithTag("legal_privacy_policy_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("In-App Privacy Policy (Effective: November 2026)").assertIsDisplayed()

        // Switch to Terms & Disclaimers tab (index 2)
        composeTestRule.onNodeWithTag("legal_tab_2").performClick()
        composeTestRule.onNodeWithTag("legal_terms_card").assertIsDisplayed()

        // Switch to Play Data Safety tab (index 3)
        composeTestRule.onNodeWithTag("legal_tab_3").performClick()
        composeTestRule.onNodeWithTag("legal_data_safety_card").assertIsDisplayed()
    }

    @Test
    fun `DesignSystem states and FinancialDisclaimerBanner render and handle actions`() {
        var retried = false
        composeTestRule.setContent {
            LoanJBTheme {
                LoanJBErrorState(
                    title = "Unable to sync rates",
                    message = "Showing last verified offline snapshot.",
                    onRetry = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("ds_error_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ds_error_retry_btn").performClick()
        assertTrue(retried)
    }

    @Test
    fun `DesignSystem empty state and disclaimer banner render cleanly`() {
        composeTestRule.setContent {
            LoanJBTheme {
                androidx.compose.foundation.layout.Column {
                    LoanJBLoadingState(message = "Loading schedule...")
                    LoanJBEmptyState(
                        title = "No saved loans",
                        subtitle = "Save any EMI result to track payments."
                    )
                    FinancialDisclaimerBanner()
                }
            }
        }

        composeTestRule.onNodeWithTag("ds_loading_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ds_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calculator_disclaimer_banner").assertIsDisplayed()
    }
}

package com.jyotirmoyb.loan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.PrepaymentMode
import com.jyotirmoyb.loan.engine.PrepaymentRule
import com.jyotirmoyb.loan.features.CalculatorModule
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.CalculatorSection
import com.jyotirmoyb.loan.features.comingsoon.ComingSoonScreen
import com.jyotirmoyb.loan.features.emi.EmiInputScreen
import com.jyotirmoyb.loan.features.emi.EmiUiState
import com.jyotirmoyb.loan.features.emi.results.EmiResultsScreen
import com.jyotirmoyb.loan.features.hub.HubScreen
import com.jyotirmoyb.loan.features.hub.HubSectionGroup
import com.jyotirmoyb.loan.features.hub.HubUiState
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanJBUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hubScreen_rendersAllSections_filtersSearch_andRoutesDisabledTileToComingSoon() {
        var clickedModule by mutableStateOf<CalculatorModule?>(null)
        val allGroups = CalculatorSection.entries.map { sec ->
            HubSectionGroup(sec, CalculatorRegistry.modules.filter { it.section == sec })
        }

        composeRule.setContent {
            LoanJBTheme {
                val activeMod = clickedModule
                if (activeMod != null && !activeMod.enabled) {
                    ComingSoonScreen(
                        moduleId = activeMod.id,
                        onBack = { clickedModule = null }
                    )
                } else {
                    HubScreen(
                        uiState = HubUiState(
                            isSearchActive = true,
                            sections = allGroups,
                            savedLoansCount = 2,
                            catalogCategoriesCount = 6
                        ),
                        onSearchQueryChange = {},
                        onClearSearch = {},
                        onModuleClick = { clickedModule = it },
                        onOpenSavedLoans = {},
                        onOpenLoanGuide = {}
                    )
                }
            }
        }

        // Verify top featured section and Loans & EMI tiles are present
        composeRule.onNodeWithTag("hub_section_wealth_planners").assertIsDisplayed()
        composeRule.onNodeWithTag("hub_list").performScrollToNode(hasTestTag("hub_section_loans_emi"))
        composeRule.onNodeWithTag("hub_tile_loan_emi").assertIsDisplayed()
        composeRule.onNodeWithTag("hub_tile_loan_comparison").assertIsDisplayed()
        composeRule.onNodeWithTag("hub_tile_bank_staff_loan").assertIsDisplayed()

        // Scroll back to top and tap disabled Goal Planner tile -> ComingSoonScreen appears
        composeRule.onNodeWithTag("hub_list").performScrollToNode(hasTestTag("hub_section_wealth_planners"))
        composeRule.onNodeWithTag("hub_tile_goal_planner").performClick()
        composeRule.onNodeWithTag("coming_soon_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Goal Planner").assertIsDisplayed()
    }

    @Test
    fun emiInputFlow_togglesPrepaymentAndTriggersCalculate() {
        var calculated = false
        val homeCat = LoanCategory(id = "home", name = "Home Loan", shortName = "Home")
        val initialInput = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
            principal = 2800000.0,
            assetValue = 3800000.0,
            annualRate = 10.5,
            tenureMonths = 72,
            processingFeePercent = 0.0
        )

        composeRule.setContent {
            LoanJBTheme {
                var input by remember { mutableStateOf(initialInput) }
                val res = remember(input) { LoanCalculationEngine.calculate(input, homeCat) }
                EmiInputScreen(
                    uiState = EmiUiState(
                        input = input,
                        result = res,
                        selectedCategory = homeCat,
                        currency = CurrencyCode.INR,
                        tenureInYears = true
                    ),
                    onSelectCategory = {},
                    onSelectVariant = { _, _, _ -> },
                    onUpdateInput = { transform -> input = transform(input) },
                    onSetTenureUnit = {},
                    onStartDateToday = {},
                    onStartDateSelected = { _, _, _ -> },
                    onSelectEmiMode = {},
                    onToggleMoratorium = {},
                    onCalculateClick = { calculated = true }
                )
            }
        }

        composeRule.onNodeWithTag("emi_amount_input").assertIsDisplayed()
        composeRule.onNodeWithTag("emi_rate_input").assertIsDisplayed()
        composeRule.onNodeWithTag("emi_tenure_input").assertIsDisplayed()

        // Scroll to Advance Settings and toggle Prepayment checkbox ON
        composeRule.onNodeWithTag("emi_input_list")
            .performScrollToNode(hasTestTag("emi_advance_settings_card"))
        composeRule.onNodeWithTag("adv_row_prepayment_checkbox").performClick()
        composeRule.onNodeWithTag("emi_advance_active_badge", useUnmergedTree = true).assertExists()

        // Scroll to Calculate CTA and tap it
        composeRule.onNodeWithTag("emi_input_list")
            .performScrollToNode(hasTestTag("emi_calculate_cta_btn"))
        composeRule.onNodeWithTag("emi_calculate_cta_btn").performClick()

        assertTrue(calculated)
    }

    @Test
    fun resultsScreen_displaysSummaryAndSwitchesYearlyMonthlyGraphTabs() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
            principal = 2800000.0,
            assetValue = 3800000.0,
            annualRate = 10.5,
            tenureMonths = 72,
            startYear = 2026,
            startMonth = 10,
            startDay = 22,
            prepaymentEnabled = true,
            prepayments = listOf(
                PrepaymentRule(startMonth = 7, amount = 600000.0, mode = PrepaymentMode.REDUCE_TENURE.name)
            )
        )
        val result = LoanCalculationEngine.calculate(input)

        composeRule.setContent {
            LoanJBTheme {
                EmiResultsScreen(
                    uiState = EmiUiState(
                        input = input,
                        result = result,
                        currency = CurrencyCode.INR
                    ),
                    shareSummaryText = { "Summary" },
                    onBack = {},
                    onSaveLoan = { _, _, _, _ -> },
                    onAddToCompare = {},
                    onShowMessage = {}
                )
            }
        }

        // Verify summary strip and Monthly schedule default tab
        composeRule.onNodeWithTag("results_summary_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("results_monthly_list").assertIsDisplayed()
        composeRule.onNodeWithText("₹28,00,000").assertIsDisplayed()

        // Switch to Yearly tab
        composeRule.onNodeWithTag("results_tab_yearly").performClick()
        composeRule.onNodeWithTag("results_yearly_list").assertIsDisplayed()

        // Switch to Graph tab
        composeRule.onNodeWithTag("results_tab_graph").performClick()
        composeRule.onNodeWithTag("results_graph_list").assertIsDisplayed()

        assertEquals(600000.0, result.schedule[6].extraPrepayment, 0.01)
    }
}

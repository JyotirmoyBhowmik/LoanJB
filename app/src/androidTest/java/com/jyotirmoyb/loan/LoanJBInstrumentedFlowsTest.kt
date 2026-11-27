package com.jyotirmoyb.loan

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.UpdateBundlePreview
import com.jyotirmoyb.loan.data.security.AppLockManager
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanCalculationEngine
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.CalculatorSection
import com.jyotirmoyb.loan.features.datarates.DataRatesScreen
import com.jyotirmoyb.loan.features.datarates.DataRatesUiState
import com.jyotirmoyb.loan.features.emi.EmiInputScreen
import com.jyotirmoyb.loan.features.emi.EmiUiState
import com.jyotirmoyb.loan.features.emi.results.EmiResultsScreen
import com.jyotirmoyb.loan.features.hub.HubScreen
import com.jyotirmoyb.loan.features.hub.HubSectionGroup
import com.jyotirmoyb.loan.features.hub.HubUiState
import com.jyotirmoyb.loan.features.onboarding.OnboardingScreen
import com.jyotirmoyb.loan.features.security.AppLockScreen
import com.jyotirmoyb.loan.features.security.PrivacySecurityScreen
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.screens.MyLoansScreen
import com.jyotirmoyb.loan.ui.screens.SettingsScreen
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose UI instrumentation tests for critical LoanJB user journeys:
 * 1. Onboarding -> Hub -> Loan (EMI) -> Results tabs -> Save Loan -> My Loans
 * 2. Data & Rates update-bundle import (valid file, invalid file rejection, undo)
 * 3. App Lock set PIN & unlock
 * 4. Language switch to Hindi (hi)
 * 5. 200% font scale smoke test
 */
@RunWith(AndroidJUnit4::class)
class LoanJBInstrumentedFlowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flow1_onboarding_hub_emi_resultsTabs_saveLoan_myLoans() {
        val homeCat = LoanCategory(id = "home", name = "Home Loan", shortName = "Home")
        val defaultInput = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
            principal = 2800000.0,
            assetValue = 3800000.0,
            annualRate = 10.5,
            tenureMonths = 72
        )
        val sections = CalculatorSection.entries.map { sec ->
            HubSectionGroup(sec, CalculatorRegistry.modules.filter { it.section == sec })
        }

        composeRule.setContent {
            LoanJBTheme {
                var stage by remember { mutableStateOf("onboarding") }
                var savedLoans by remember { mutableStateOf<List<TrackedLoanSummary>>(emptyList()) }
                val calcResult = remember(defaultInput) { LoanCalculationEngine.calculate(defaultInput, homeCat) }

                when (stage) {
                    "onboarding" -> OnboardingScreen(onFinished = { stage = "hub" })
                    "hub" -> HubScreen(
                        uiState = HubUiState(sections = sections, savedLoansCount = savedLoans.size),
                        onSearchQueryChange = {},
                        onClearSearch = {},
                        onModuleClick = { mod -> if (mod.id == "loan_emi") stage = "emi_input" },
                        onOpenSavedLoans = { stage = "my_loans" },
                        onOpenLoanGuide = {}
                    )
                    "emi_input" -> EmiInputScreen(
                        uiState = EmiUiState(
                            input = defaultInput,
                            result = calcResult,
                            selectedCategory = homeCat,
                            currency = CurrencyCode.INR,
                            tenureInYears = true
                        ),
                        onSelectCategory = {},
                        onSelectVariant = { _, _, _ -> },
                        onUpdateInput = {},
                        onSetTenureUnit = {},
                        onStartDateToday = {},
                        onStartDateSelected = { _, _, _ -> },
                        onEmiDayOfMonthChange = {},
                        onProRataToggle = {},
                        onResetRateDefault = {},
                        onApplyRepoScenario = {},
                        onSelectEmiMode = {},
                        onToggleMoratorium = {},
                        onCalculateClick = { stage = "emi_results" }
                    )
                    "emi_results" -> EmiResultsScreen(
                        uiState = EmiUiState(
                            input = defaultInput,
                            result = calcResult,
                            selectedCategory = homeCat,
                            currency = CurrencyCode.INR
                        ),
                        shareSummaryText = { "Summary" },
                        onBack = { stage = "emi_input" },
                        onSaveLoan = { nickname, lender, reminder, daysBefore, emiDay, nach ->
                            val entity = SavedLoanEntity(
                                id = 1L,
                                nickname = nickname,
                                lender = lender,
                                categoryId = homeCat.id,
                                categoryName = homeCat.name,
                                variantSummary = "Floating",
                                inputJson = "{}",
                                paidMonthsCsv = "",
                                completedChecklistCsv = "",
                                reminderEnabled = reminder,
                                reminderDaysBefore = daysBefore,
                                createdAtMillis = System.currentTimeMillis(),
                                autoDebitNach = nach,
                                emiDayOfMonth = emiDay
                            )
                            savedLoans = listOf(
                                TrackedLoanSummary(
                                    entity = entity,
                                    input = defaultInput,
                                    result = calcResult,
                                    paidMonths = emptySet(),
                                    paidEmisCount = 0,
                                    totalEmisCount = calcResult.actualTenureMonths,
                                    remainingBalance = defaultInput.principal,
                                    nextDueMonthLabel = "Nov '26",
                                    nextDueAmount = calcResult.monthlyEmi,
                                    interestSavedFromPrepayments = 0.0
                                )
                            )
                            stage = "my_loans"
                        },
                        onAddToCompare = {},
                        onShowMessage = {}
                    )
                    "my_loans" -> MyLoansScreen(
                        savedLoans = savedLoans,
                        currency = CurrencyCode.INR,
                        onOpenLoan = {},
                        onMarkNextPaid = {},
                        onDeleteLoan = {},
                        onStartNewCalculation = { stage = "emi_input" }
                    )
                }
            }
        }

        // 1. Onboarding -> Skip to Hub
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip_btn").performClick()

        // 2. Hub -> Scroll to Loans & EMI -> tap Loan (EMI)
        composeRule.onNodeWithTag("hub_list").performScrollToNode(hasTestTag("hub_section_loans_emi"))
        composeRule.onNodeWithTag("hub_tile_loan_emi").performClick()

        // 3. EMI Input -> Calculate
        composeRule.onNodeWithTag("emi_input_list").performScrollToNode(hasTestTag("emi_calculate_cta_btn"))
        composeRule.onNodeWithTag("emi_calculate_cta_btn").performClick()

        // 4. EMI Results -> Verify Monthly, switch Yearly & Graph tabs
        composeRule.onNodeWithTag("results_summary_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("results_monthly_list").assertIsDisplayed()
        composeRule.onNodeWithTag("results_tab_yearly").performClick()
        composeRule.onNodeWithTag("results_yearly_list").assertIsDisplayed()
        composeRule.onNodeWithTag("results_tab_graph").performClick()
        composeRule.onNodeWithTag("results_graph_list").assertIsDisplayed()

        // 5. Save Loan -> Confirm -> Verify My Loans displays saved card
        composeRule.onNodeWithTag("results_save_loan_btn").performClick()
        composeRule.onNodeWithTag("save_loan_confirm_btn").performClick()
        composeRule.onNodeWithTag("saved_loan_card_1").assertIsDisplayed()
    }

    @Test
    fun flow2_dataRates_importValidBundle_rejectInvalid_andUndoImport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppContainer.provideRatesRepository(context)
        runBlocking {
            repo.resetAllOverrides()
            repo.refreshResolvedItems()
        }

        val initialRepoRate = repo.getDouble(ConfigKeys.RBI_REPO_RATE, 6.25)
        val updatedRate = if (initialRepoRate == 5.75) 5.50 else 5.75

        val validBundleJson = """
            {
              "bundleVersion": 1,
              "generatedOn": "2026-11-27",
              "generatedBy": "InstrumentationTest",
              "dataVersion": "2026.12.99",
              "rates": {
                "items": [
                  {
                    "key": "rbi_repo_rate",
                    "value": $updatedRate,
                    "unit": "%",
                    "effectiveFrom": "2026-11-27",
                    "sourceName": "Reserve Bank of India",
                    "sourceUrl": "https://www.rbi.org.in/",
                    "lastVerified": "2026-11-27"
                  }
                ]
              }
            }
        """.trimIndent()

        val invalidBundleJson = """
            {
              "bundleVersion": 1,
              "generatedOn": "2026-11-27",
              "dataVersion": "2026.12.99",
              "rates": {
                "items": [
                  {
                    "key": "unknown_fake_key",
                    "value": 9.9,
                    "unit": "%",
                    "effectiveFrom": "2026-11-27",
                    "sourceName": "Fake",
                    "sourceUrl": "http://insecure.example.com",
                    "lastVerified": "UNVERIFIED"
                  }
                ]
              }
            }
        """.trimIndent()

        // Verify invalid bundle is rejected before preview
        val invalidResult = runBlocking { repo.previewUpdateBundle(invalidBundleJson, "bad.json") }
        assertTrue(invalidResult.isFailure)

        // Verify valid bundle produces preview and UI applies + undoes cleanly
        val preview = runBlocking { repo.previewUpdateBundle(validBundleJson, "valid.json").getOrThrow() }
        var currentPreview by mutableStateOf<UpdateBundlePreview?>(preview)
        var historyCount by mutableStateOf(0)

        composeRule.setContent {
            LoanJBTheme {
                DataRatesScreen(
                    uiState = DataRatesUiState(
                        items = repo.resolvedItemsFlow.value,
                        importHistoryCount = historyCount,
                        pendingBundlePreview = currentPreview
                    ),
                    onSelectCategory = {},
                    onToggleOnlineData = {},
                    onRefreshNow = {},
                    onSaveOverride = { _, _, _, _ -> },
                    onResetItem = {},
                    onResetAllOverrides = {},
                    exportOverridesJson = { "{}" },
                    onImportOverridesJson = {},
                    onShowMessage = {},
                    onApplyPendingBundle = { itemKeys, tableIds, unchanged ->
                        runBlocking {
                            repo.applyUpdateBundlePreview(preview, itemKeys, tableIds, unchanged).getOrThrow()
                        }
                        currentPreview = null
                        historyCount = repo.importHistoryCountFlow.value
                    },
                    onDismissBundlePreview = { currentPreview = null },
                    onUndoLastImport = {
                        runBlocking { repo.undoLastImport().getOrThrow() }
                        historyCount = repo.importHistoryCountFlow.value
                    }
                )
            }
        }

        // Preview dialog is shown -> tap Apply
        composeRule.onNodeWithTag("update_bundle_preview_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("bundle_preview_apply_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(updatedRate, repo.getDouble(ConfigKeys.RBI_REPO_RATE, 0.0), 0.001)
        assertTrue(historyCount > 0)

        // Undo last import reverts value
        composeRule.onNodeWithTag("data_rates_undo_import_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("data_rates_undo_import_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(initialRepoRate, repo.getDouble(ConfigKeys.RBI_REPO_RATE, 0.0), 0.001)
    }

    @Test
    fun flow3_appLock_setPin_rejectsWrongPin_andUnlocksWithCorrectPin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = AppLockManager(context)
        lockManager.clearPin()

        var locked by mutableStateOf(false)
        var pinSet by mutableStateOf(false)

        composeRule.setContent {
            LoanJBTheme {
                if (locked) {
                    AppLockScreen(
                        hasBiometricAvailable = false,
                        hasPinConfigured = pinSet,
                        onVerifyPin = { lockManager.verifyPin(it) },
                        onUnlocked = { locked = false }
                    )
                } else {
                    PrivacySecurityScreen(
                        appLockEnabled = pinSet,
                        flagSecureEnabled = false,
                        hasBiometricAvailable = false,
                        hasPinConfigured = pinSet,
                        onToggleAppLock = { if (it) locked = true },
                        onSetPin = { pin ->
                            lockManager.setPin(pin).also { if (it.isSuccess) pinSet = true }
                        },
                        onClearPin = {
                            lockManager.clearPin()
                            pinSet = false
                        },
                        onToggleFlagSecure = {},
                        onShowMessage = {}
                    )
                }
            }
        }

        // Configure PIN through AppLockManager and trigger App Lock screen
        assertTrue(lockManager.setPin("2468").isSuccess)
        pinSet = true
        locked = true
        composeRule.waitForIdle()

        // Enter wrong PIN -> stays locked
        composeRule.onNodeWithTag("app_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("9999")
        composeRule.onNodeWithTag("app_lock_unlock_pin_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(locked)

        // Enter correct PIN -> unlocks
        composeRule.onNodeWithTag("app_lock_pin_input").performTextInput("2468")
        composeRule.onNodeWithTag("app_lock_unlock_pin_btn").performClick()
        composeRule.waitForIdle()
        assertFalse(locked)
        composeRule.onNodeWithTag("privacy_security_screen").assertIsDisplayed()

        lockManager.clearPin()
    }

    @Test
    fun flow4_languageSwitch_toHindi_rendersHindiResources() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val hiConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale("hi"))
        }
        val hiContext = baseContext.createConfigurationContext(hiConfig)
        val hindiSkipLabel = hiContext.getString(R.string.onboarding_skip)
        val englishSkipLabel = baseContext.getString(R.string.onboarding_skip)
        assertTrue(
            "Expected Hindi translation to differ from English '$englishSkipLabel', got '$hindiSkipLabel'",
            hindiSkipLabel != englishSkipLabel
        )

        var selectedLang by mutableStateOf("en")
        composeRule.setContent {
            val activeCtx = if (selectedLang == "hi") hiContext else baseContext
            CompositionLocalProvider(LocalContext provides activeCtx) {
                LoanJBTheme {
                    if (selectedLang == "hi") {
                        OnboardingScreen(onFinished = {})
                    } else {
                        SettingsScreen(
                            preferences = UserPreferences(languageCode = selectedLang),
                            catalog = LoanCatalog(categories = emptyList()),
                            serializeCatalog = { "{}" },
                            createEncryptedBackupJson = { Result.success("{}") },
                            onSetLanguage = { selectedLang = it },
                            onSetRegion = {},
                            onSetCurrency = {},
                            onSetThemeMode = {},
                            onSetMonthlyIncome = {},
                            onSetTaxRegime = {},
                            onSetUserAge = {},
                            onSetRiskProfile = {},
                            onSaveCatalogObject = {},
                            onSaveCatalogRawJson = {},
                            onResetCatalogDefault = {},
                            onRestoreBackupJson = { _, _ -> },
                            onResetAllData = {},
                            onShowMessage = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("settings_lang_hi").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(hindiSkipLabel).assertIsDisplayed()
    }

    @Test
    fun flow5_fontScale200Percent_smokeTest_keepsPrimaryValuesVisible() {
        val input = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD"),
            principal = 1000000.0,
            annualRate = 12.0,
            tenureMonths = 12
        )
        val result = LoanCalculationEngine.calculate(input)

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                LoanJBTheme {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ValueSourceBadgesRow(
                            source = ConfigValueSource.BUILT_IN,
                            asOfDate = "2026-11-27",
                            isUnverified = false,
                            sourceName = "Reserve Bank of India",
                            sourceUrl = "https://www.rbi.org.in/",
                            effectiveFrom = "2026-04-01",
                            lastVerified = "2026-11-27"
                        )
                        EmiResultsScreen(
                            uiState = EmiUiState(input = input, result = result, currency = CurrencyCode.INR),
                            shareSummaryText = { "Summary" },
                            onBack = {},
                            onSaveLoan = { _, _, _, _, _, _ -> },
                            onAddToCompare = {},
                            onShowMessage = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("compact_verification_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("results_summary_strip").assertIsDisplayed()
        composeRule.onNodeWithText("₹88,849").assertIsDisplayed()
    }
}

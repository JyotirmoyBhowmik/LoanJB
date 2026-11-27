package com.jyotirmoyb.loan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies compact single-line verification badges at 200% font scale:
 * - No overlap between main label/value and the verification badge line.
 * - Main value does not overflow/truncate at 200% font scale.
 * - Replaces red "UNVERIFIED" pill with compact "⚠ Not yet checked" / "✓ Checked 27 Nov 26".
 * - Tapping opens the bottom sheet with source name, official link, dates, and plain-English legend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompactVerificationBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val verifiedItem = ResolvedConfigItem(
        key = "market.rbi_repo_rate",
        label = "RBI Policy Repo Rate",
        category = ConfigCategory.MARKET_DATA,
        fileName = "rates.json",
        valueText = "5.5",
        numericValue = 5.5,
        unit = "% p.a.",
        effectiveFrom = "2026-10-07",
        asOfDate = "27 Nov 2026",
        sourceName = "Reserve Bank of India",
        sourceUrl = "https://www.rbi.org.in",
        lastVerified = "2026-11-27",
        source = ConfigValueSource.ONLINE,
        defaultValueText = "5.5",
        defaultNumericValue = 5.5
    )

    private val unverifiedItem = verifiedItem.copy(
        key = "loan.home.rate_min",
        label = "Home Loan Min Rate",
        valueText = "8.35",
        numericValue = 8.35,
        asOfDate = "25 Nov 2026",
        lastVerified = UNVERIFIED_MARKER,
        source = ConfigValueSource.BUILT_IN
    )

    @Test
    fun compactBadge_at200PercentFontScale_doesNotOverlapOrTruncateMainValue() {
        var valueOverflowed by mutableStateOf(false)
        var labelLines by mutableStateOf(0)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2.0f)) {
                LoanJBTheme(themeMode = AppThemeMode.LIGHT) {
                    Column(modifier = Modifier.width(320.dp)) {
                        FintechCard {
                            Text(
                                text = verifiedItem.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                onTextLayout = { labelLines = it.lineCount },
                                modifier = Modifier.testTag("metric_label")
                            )
                            Text(
                                text = "${verifiedItem.valueText} ${verifiedItem.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                onTextLayout = { valueOverflowed = it.hasVisualOverflow },
                                modifier = Modifier.testTag("metric_value")
                            )
                            ValueSourceBadgesRow(item = verifiedItem)
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("metric_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metric_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_verification_badge").assertIsDisplayed()

        assertFalse("Main value must not truncate at 200% font scale", valueOverflowed)
        assertTrue("Label should lay out cleanly", labelLines >= 1)

        val labelBounds = composeTestRule.onNodeWithTag("metric_label").getBoundsInRoot()
        val valueBounds = composeTestRule.onNodeWithTag("metric_value").getBoundsInRoot()
        val badgeBounds = composeTestRule.onNodeWithTag("compact_verification_badge").getBoundsInRoot()

        assertTrue(
            "Label (${labelBounds.bottom}) must not overlap value (${valueBounds.top})",
            valueBounds.top >= labelBounds.bottom - 1.dp
        )
        assertTrue(
            "Value (${valueBounds.bottom}) must not overlap badge (${badgeBounds.top})",
            badgeBounds.top >= valueBounds.bottom - 1.dp
        )
        assertTrue("Badge height should remain compact single-line", badgeBounds.bottom - badgeBounds.top < 42.dp)
        composeTestRule.onNodeWithText("Online · 27 Nov 26 · ✓ Checked 27 Nov 26").assertIsDisplayed()
    }

    @Test
    fun unverifiedBadge_rendersAmberNotYetCheckedAndOpensDetailsSheetWithLegend() {
        composeTestRule.setContent {
            LoanJBTheme(themeMode = AppThemeMode.DARK) {
                ValueSourceBadgesRow(item = unverifiedItem)
            }
        }

        // Never shows raw "UNVERIFIED" pill text
        composeTestRule.onNodeWithText("UNVERIFIED", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Built-in · 25 Nov 26 · ⚠ Not yet checked").assertIsDisplayed()

        // Tap opens bottom sheet with source name, official link, dates, and plain-English legend
        composeTestRule.onNodeWithTag("compact_verification_badge").performClick()
        composeTestRule.onNodeWithTag("source_verification_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reserve Bank of India").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("7 Oct 26").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_sheet_url_link").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Checked = confirmed on an official source on that date. Not yet checked = built-in estimate, please verify."
        ).performScrollTo().assertIsDisplayed()
        assertEquals(ConfigValueSource.BUILT_IN, unverifiedItem.source)
    }
}

package com.jyotirmoyb.loan.features.mutualfund.lumpsum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.mutualfund.MfWealthGrowthChartCard
import com.jyotirmoyb.loan.features.mutualfund.MfYearWiseTableCard
import com.jyotirmoyb.loan.features.mutualfund.MutualFundRiskNoticeBanner
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val LUMPSUM_YEAR_PRESETS = listOf(3, 5, 10, 15, 20, 25)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LumpsumScreen(
    uiState: LumpsumUiState,
    onUpdatePrincipal: (Double) -> Unit,
    onUpdateReturnRate: (String) -> Unit,
    onResetReturn: () -> Unit,
    onUpdateInflation: (String) -> Unit,
    onUpdateTenureYears: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    var showTable by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_lumpsum_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "lumpsum_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_lumpsum_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.mf_projected_value),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.format(result.maturityAmount, currency),
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.mf_total_invested),
                            value = CurrencyFormatter.formatCompact(result.investedAmount, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.mf_est_returns),
                            value = CurrencyFormatter.formatCompact(result.estimatedReturns, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.mf_inflation_adjusted),
                            value = CurrencyFormatter.formatCompact(result.inflationAdjustedValue, currency),
                            valueColor = InterestAmber
                        )
                    }
                }
            }
        }

        item(key = "lumpsum_risk") { MutualFundRiskNoticeBanner() }

        item(key = "lumpsum_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_lumpsum_title),
                    badge = "${CurrencyFormatter.formatPercent(input.expectedReturnPercent)} p.a.",
                    icon = Icons.Outlined.Savings
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.principal,
                    currency = currency,
                    onAmountChange = onUpdatePrincipal,
                    label = stringResource(R.string.mf_lumpsum_amount)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumericInputField(
                        label = stringResource(R.string.mf_expected_return),
                        valueText = uiState.returnRateText,
                        onValueChange = onUpdateReturnRate,
                        trailingBadge = "% p.a.",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_lumpsum_rate_input")
                    )
                    ResetToDefaultAction(
                        visible = uiState.isReturnCustom,
                        onClick = onResetReturn,
                        testTag = "mf_lumpsum_rate_reset_btn"
                    )
                }

                uiState.returnRateItem?.let { item ->
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = item)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.mf_horizon_years),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LUMPSUM_YEAR_PRESETS.forEach { yr ->
                        FilterChip(
                            selected = input.tenureYears == yr,
                            onClick = { onUpdateTenureYears(yr) },
                            label = { Text("${yr}Y") },
                            modifier = Modifier.testTag("mf_lumpsum_yr_$yr")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.mf_horizon_years),
                        valueText = input.tenureYears.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onUpdateTenureYears) },
                        trailingBadge = "yrs",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_lumpsum_years_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.mf_inflation_rate),
                        valueText = uiState.inflationText,
                        onValueChange = onUpdateInflation,
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_lumpsum_inflation_input")
                    )
                }
            }
        }

        item(key = "lumpsum_chart") {
            MfWealthGrowthChartCard(rows = result.yearlyRows, currency = currency)
        }

        item(key = "lumpsum_table") {
            MfYearWiseTableCard(
                rows = result.yearlyRows,
                currency = currency,
                expanded = showTable,
                onToggleExpanded = { showTable = !showTable }
            )
        }

        item(key = "lumpsum_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

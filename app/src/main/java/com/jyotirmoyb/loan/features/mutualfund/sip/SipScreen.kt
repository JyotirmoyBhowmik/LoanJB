package com.jyotirmoyb.loan.features.mutualfund.sip

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
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
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val SIP_YEAR_PRESETS = listOf(5, 10, 15, 20, 25, 30)
private val SIP_STEPUP_PRESETS = listOf(0, 5, 10, 15)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SipScreen(
    uiState: SipUiState,
    onUpdateMonthlySip: (Double) -> Unit,
    onUpdateReturnRate: (String) -> Unit,
    onResetReturn: () -> Unit,
    onUpdateStepUp: (String) -> Unit,
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
            .testTag("mf_sip_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Optional selected fund banner when launched from Live NAV
        uiState.selectedFundLabel?.let { fundLabel ->
            item(key = "sip_selected_fund") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = InfoBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.ui_txt_fund_reference_user_cagr),
                            style = MaterialTheme.typography.labelSmall,
                            color = InfoBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = fundLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 1. Hero Card
        item(key = "sip_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_sip_hero_card")
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
                    if (input.annualStepUpPercent > 0.0) {
                        Text(
                            text = stringResource(
                                R.string.ui_txt_with_annual_step_up_final_yr_sip_m,
                                input.annualStepUpPercent.toInt(),
                                CurrencyFormatter.format(result.finalMonthlySip, currency)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = PrincipalEmerald
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.mf_total_invested),
                            value = CurrencyFormatter.formatCompact(result.totalInvested, currency),
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

        // 2. Market Risk Note (Mandatory)
        item(key = "sip_risk_note") {
            MutualFundRiskNoticeBanner()
        }

        // 3. Inputs Card
        item(key = "sip_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_sip_title),
                    badge = "${CurrencyFormatter.formatPercent(input.expectedReturnPercent)} p.a.",
                    icon = Icons.AutoMirrored.Outlined.TrendingUp
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.monthlySip,
                    currency = currency,
                    onAmountChange = onUpdateMonthlySip,
                    label = stringResource(R.string.mf_monthly_sip)
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
                            .testTag("mf_sip_rate_input")
                    )
                    ResetToDefaultAction(
                        visible = uiState.isReturnCustom,
                        onClick = onResetReturn,
                        testTag = "mf_sip_rate_reset_btn"
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
                    SIP_YEAR_PRESETS.forEach { yr ->
                        FilterChip(
                            selected = input.tenureYears == yr,
                            onClick = { onUpdateTenureYears(yr) },
                            label = { Text(stringResource(R.string.ui_txt_y_3e961f, yr)) },
                            modifier = Modifier.testTag("mf_sip_yr_$yr")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.mf_horizon_years),
                    valueText = input.tenureYears.toString(),
                    onValueChange = { txt -> txt.toIntOrNull()?.let(onUpdateTenureYears) },
                    trailingBadge = "yrs",
                    modifier = Modifier.testTag("mf_sip_years_input")
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.mf_annual_step_up),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SIP_STEPUP_PRESETS.forEach { pct ->
                        FilterChip(
                            selected = input.annualStepUpPercent.toInt() == pct,
                            onClick = { onUpdateStepUp(pct.toString()) },
                            label = { Text(if (pct == 0) "Flat" else "+$pct%/yr") },
                            modifier = Modifier.testTag("mf_sip_stepup_$pct")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.mf_annual_step_up),
                        valueText = uiState.stepUpText,
                        onValueChange = onUpdateStepUp,
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_sip_stepup_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.mf_inflation_rate),
                        valueText = uiState.inflationText,
                        onValueChange = onUpdateInflation,
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mf_sip_inflation_input")
                    )
                }
            }
        }

        // 4. Growth Chart
        item(key = "sip_chart") {
            MfWealthGrowthChartCard(rows = result.yearlyRows, currency = currency)
        }

        // 5. Year-wise Table
        item(key = "sip_table") {
            MfYearWiseTableCard(
                rows = result.yearlyRows,
                currency = currency,
                expanded = showTable,
                onToggleExpanded = { showTable = !showTable }
            )
        }

        item(key = "sip_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

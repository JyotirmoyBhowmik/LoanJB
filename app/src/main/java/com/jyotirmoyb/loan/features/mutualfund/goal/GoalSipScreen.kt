package com.jyotirmoyb.loan.features.mutualfund.goal

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
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val GOAL_YEAR_PRESETS = listOf(5, 10, 15, 20, 25)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSipScreen(
    uiState: GoalSipUiState,
    onUpdateTarget: (Double) -> Unit,
    onUpdateYears: (Int) -> Unit,
    onToggleInflationAdjust: (Boolean) -> Unit,
    onUpdateReturnRate: (String) -> Unit,
    onUpdateInflation: (String) -> Unit,
    onUpdateStepUp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    var showTable by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_goal_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "goal_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_goal_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.mf_goal_required_sip),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.ui_txt_mo_d337f7, CurrencyFormatter.format(result.requiredMonthlySip, currency)),
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_txt_fmt_6c03a4,
                            stringResource(R.string.mf_goal_future_target),
                            CurrencyFormatter.format(result.nominalTargetCorpus, currency)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = InterestAmber,
                        fontWeight = FontWeight.SemiBold
                    )
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
                            label = "Horizon",
                            value = "${input.years} Yrs",
                            valueColor = Color.White
                        )
                    }
                }
            }
        }

        item(key = "goal_risk") { MutualFundRiskNoticeBanner() }

        item(key = "goal_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_goal_title),
                    badge = "${input.years} Yrs",
                    icon = Icons.Outlined.TrackChanges
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.targetCorpusToday,
                    currency = currency,
                    onAmountChange = onUpdateTarget,
                    label = stringResource(R.string.mf_goal_target)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.mf_goal_adjust_inflation),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = input.adjustTargetForInflation,
                        onCheckedChange = onToggleInflationAdjust,
                        modifier = Modifier.testTag("mf_goal_inflation_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.mf_horizon_years),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOAL_YEAR_PRESETS.forEach { yr ->
                        FilterChip(
                            selected = input.years == yr,
                            onClick = { onUpdateYears(yr) },
                            label = { Text(stringResource(R.string.ui_txt_y_3e961f, yr)) },
                            modifier = Modifier.testTag("mf_goal_yr_$yr")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.mf_expected_return),
                        valueText = uiState.returnText,
                        onValueChange = onUpdateReturnRate,
                        trailingBadge = "%",
                        modifier = Modifier.weight(1f).testTag("mf_goal_rate_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.mf_inflation_rate),
                        valueText = uiState.inflationText,
                        onValueChange = onUpdateInflation,
                        trailingBadge = "%",
                        modifier = Modifier.weight(1f).testTag("mf_goal_inflation_input")
                    )
                }
                Spacer(Modifier.height(10.dp))
                NumericInputField(
                    label = stringResource(R.string.mf_annual_step_up),
                    valueText = uiState.stepUpText,
                    onValueChange = onUpdateStepUp,
                    trailingBadge = "% / yr",
                    modifier = Modifier.testTag("mf_goal_stepup_input")
                )
            }
        }

        item(key = "goal_chart") {
            MfWealthGrowthChartCard(rows = result.yearlyRows, currency = currency)
        }

        item(key = "goal_table") {
            MfYearWiseTableCard(
                rows = result.yearlyRows,
                currency = currency,
                expanded = showTable,
                onToggleExpanded = { showTable = !showTable }
            )
        }

        item(key = "goal_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

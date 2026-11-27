package com.jyotirmoyb.loan.features.mutualfund.swp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.mutualfund.MutualFundRiskNoticeBanner
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val SWP_YEARS = listOf(10, 15, 20, 25, 30)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwpScreen(
    uiState: SwpUiState,
    onUpdateCorpus: (Double) -> Unit,
    onUpdateWithdrawal: (Double) -> Unit,
    onUpdateReturnRate: (String) -> Unit,
    onUpdateStepUp: (String) -> Unit,
    onUpdateTenureYears: (Int) -> Unit,
    onApplySustainable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    var showTable by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_swp_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "swp_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_swp_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (result.corpusDepleted) DangerRed.copy(alpha = 0.25f) else PrincipalEmerald.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = result.longevityLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result.corpusDepleted) Color(0xFFFFB4AB) else PrincipalEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.mf_swp_final_balance).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Text(
                        text = CurrencyFormatter.format(result.finalBalance, currency),
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.mf_swp_total_withdrawn),
                            value = CurrencyFormatter.formatCompact(result.totalWithdrawn, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "Safe ${input.tenureYears}Y Payout",
                            value = "${CurrencyFormatter.formatCompact(result.sustainableMonthlyWithdrawal, currency)}/mo",
                            valueColor = InterestAmber
                        )
                    }
                }
            }
        }

        item(key = "swp_risk") { MutualFundRiskNoticeBanner() }

        // Sustainable payout helper card
        item(key = "swp_sustainable_card") {
            FintechCard {
                KeyValueRow(
                    label = stringResource(R.string.mf_swp_sustainable, input.tenureYears),
                    value = "${CurrencyFormatter.format(result.sustainableMonthlyWithdrawal, currency)}/mo",
                    valueColor = SavingsGreen,
                    bold = true
                )
                KeyValueRow(
                    label = stringResource(R.string.mf_swp_perpetuity),
                    value = "${CurrencyFormatter.format(result.perpetuityMonthlyWithdrawal, currency)}/mo"
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onApplySustainable,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mf_swp_apply_sustainable_btn")
                ) {
                    Text(stringResource(R.string.ui_txt_set_monthly_withdrawal_to_sustaina), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Inputs Card
        item(key = "swp_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_swp_title),
                    badge = "${CurrencyFormatter.formatPercent(input.expectedReturnPercent)} p.a.",
                    icon = Icons.Outlined.AccountBalanceWallet
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.initialCorpus,
                    currency = currency,
                    onAmountChange = onUpdateCorpus,
                    label = stringResource(R.string.mf_swp_corpus)
                )
                Spacer(Modifier.height(12.dp))

                AmountField(
                    amount = input.monthlyWithdrawal,
                    currency = currency,
                    onAmountChange = onUpdateWithdrawal,
                    label = stringResource(R.string.mf_swp_withdrawal)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.mf_expected_return),
                        valueText = uiState.returnText,
                        onValueChange = onUpdateReturnRate,
                        trailingBadge = "%",
                        modifier = Modifier.weight(1f).testTag("mf_swp_rate_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.mf_swp_stepup),
                        valueText = uiState.stepUpText,
                        onValueChange = onUpdateStepUp,
                        trailingBadge = "%",
                        modifier = Modifier.weight(1f).testTag("mf_swp_stepup_input")
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.mf_horizon_years),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SWP_YEARS.forEach { yr ->
                        FilterChip(
                            selected = input.tenureYears == yr,
                            onClick = { onUpdateTenureYears(yr) },
                            label = { Text(stringResource(R.string.ui_txt_y_3e961f, yr)) },
                            modifier = Modifier.testTag("mf_swp_yr_$yr")
                        )
                    }
                }
            }
        }

        // Year-wise SWP table
        if (result.yearlyRows.isNotEmpty()) {
            item(key = "swp_table") {
                FintechCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTable = !showTable },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionAccentHeader(
                            title = "Year-Wise Withdrawal Schedule",
                            badge = "${result.yearlyRows.size} yrs",
                            icon = Icons.Outlined.CalendarToday,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showTable) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null
                        )
                    }
                    AnimatedVisibility(visible = showTable) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.ui_txt_yr_173ac6),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.7f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_withdrawn),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_returns),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_balance),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            result.yearlyRows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.ui_txt_y_be9cd4, row.yearIndex),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                    Text(
                                        CurrencyFormatter.formatCompact(row.withdrawnInYear, currency),
                                        style = MonoSmallStyle,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        CurrencyFormatter.formatCompact(row.returnsEarned, currency),
                                        style = MonoSmallStyle,
                                        color = SavingsGreen,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        CurrencyFormatter.formatCompact(row.closingBalance, currency),
                                        style = MonoSmallStyle,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.closingBalance <= 0.0) DangerRed else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                                if (idx < result.yearlyRows.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "swp_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

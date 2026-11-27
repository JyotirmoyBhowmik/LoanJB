package com.jyotirmoyb.loan.features.deposits.fd

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
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.jyotirmoyb.loan.domain.CompoundingFrequency
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.FdMode
import com.jyotirmoyb.loan.domain.PayoutFrequency
import com.jyotirmoyb.loan.features.emi.AmountField
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

private val FD_TENURE_PRESETS = listOf(6 to "6M", 12 to "1Y", 24 to "2Y", 36 to "3Y", 60 to "5Y", 120 to "10Y")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixedDepositScreen(
    uiState: FixedDepositUiState,
    onSetMode: (FdMode) -> Unit,
    onUpdatePrincipal: (Double) -> Unit,
    onUpdateRateText: (String) -> Unit,
    onResetRate: () -> Unit,
    onSetSeniorCitizen: (Boolean) -> Unit,
    onSetCompounding: (CompoundingFrequency) -> Unit,
    onSetPayoutFrequency: (PayoutFrequency) -> Unit,
    onUpdateTenureMonths: (Int) -> Unit,
    onSetHasPan: (Boolean) -> Unit,
    onSetTaxSlab: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    var showYearlyTable by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("fd_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Mode Segmented Toggle (Cumulative vs Interest Payout)
        item(key = "fd_mode_toggle") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fd_mode_segmented")
            ) {
                SegmentedButton(
                    selected = input.mode == FdMode.CUMULATIVE,
                    onClick = { onSetMode(FdMode.CUMULATIVE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("fd_mode_cumulative_btn")
                ) {
                    Text(stringResource(R.string.fd_mode_cumulative), fontWeight = FontWeight.SemiBold)
                }
                SegmentedButton(
                    selected = input.mode == FdMode.PAYOUT,
                    onClick = { onSetMode(FdMode.PAYOUT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("fd_mode_payout_btn")
                ) {
                    Text(stringResource(R.string.fd_mode_payout), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Hero Result Card
        item(key = "fd_hero_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fd_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (input.mode == FdMode.CUMULATIVE) {
                        Text(
                            text = stringResource(R.string.fd_maturity_amount),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(result.maturityAmount, currency),
                            style = MonoLargeStyle,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt,
                                stringResource(R.string.fd_periodic_payout),
                                input.payoutFrequency.label.uppercase()
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(result.periodicPayoutAmount, currency),
                            style = MonoLargeStyle,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt_6c03a4,
                                stringResource(R.string.fd_total_payouts, result.totalPayoutCount),
                                CurrencyFormatter.format(result.totalPayoutReceived, currency)
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
                            label = stringResource(R.string.fd_total_interest),
                            value = CurrencyFormatter.formatCompact(result.totalInterest, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.fd_post_tax_maturity),
                            value = CurrencyFormatter.formatCompact(result.postTaxMaturityAmount, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.fd_maturity_date),
                            value = result.maturityDateLabel,
                            valueColor = InterestAmber
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt_6c03a4,
                                stringResource(R.string.fd_pre_tax_yield),
                                CurrencyFormatter.formatPercent(result.effectivePreTaxYieldPercent)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt_6c03a4,
                                stringResource(R.string.fd_post_tax_yield),
                                CurrencyFormatter.formatPercent(result.effectivePostTaxYieldPercent)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrincipalEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Deposit Inputs Card
        item(key = "fd_inputs_card") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.fd_title),
                    badge = "${CurrencyFormatter.formatPercent(result.effectiveNominalRatePercent)} p.a.",
                    icon = Icons.Outlined.Savings
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.principal,
                    currency = currency,
                    onAmountChange = onUpdatePrincipal,
                    label = stringResource(R.string.fd_deposit_amount)
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NumericInputField(
                        label = stringResource(R.string.fd_interest_rate),
                        valueText = uiState.rateText,
                        onValueChange = onUpdateRateText,
                        trailingBadge = "% p.a.",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fd_rate_input")
                    )
                    ResetToDefaultAction(
                        visible = uiState.isRateOverridden,
                        onClick = onResetRate,
                        testTag = "fd_rate_reset_btn"
                    )
                }

                uiState.fdRateItem?.let { item ->
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = item)
                }

                Spacer(Modifier.height(12.dp))

                // Senior Citizen Toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.fd_senior_citizen,
                                    "%.2f".format(input.seniorBonusPercent).trimEnd('0').trimEnd('.')
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.fd_senior_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = input.isSeniorCitizen,
                            onCheckedChange = onSetSeniorCitizen,
                            modifier = Modifier.testTag("fd_senior_switch")
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Compounding or Payout Frequency Chips
                if (input.mode == FdMode.CUMULATIVE) {
                    Text(
                        text = stringResource(R.string.fd_compounding_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompoundingFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = input.compounding == freq,
                                onClick = { onSetCompounding(freq) },
                                label = { Text(freq.label) },
                                modifier = Modifier.testTag("fd_comp_${freq.name.lowercase()}")
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.fd_payout_freq_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PayoutFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = input.payoutFrequency == freq,
                                onClick = { onSetPayoutFrequency(freq) },
                                label = { Text(freq.label) },
                                modifier = Modifier.testTag("fd_payout_${freq.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Tenure quick chips + numeric field
                Text(
                    text = stringResource(R.string.fd_tenure_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FD_TENURE_PRESETS.forEach { (months, label) ->
                        FilterChip(
                            selected = input.tenureMonths == months,
                            onClick = { onUpdateTenureMonths(months) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("fd_tenure_chip_$months")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.fd_tenure_months),
                    valueText = input.tenureMonths.toString(),
                    onValueChange = { txt -> txt.toIntOrNull()?.let(onUpdateTenureMonths) },
                    trailingBadge = "${"%.1f".format(input.tenureMonths / 12.0)} yrs",
                    modifier = Modifier.testTag("fd_tenure_months_input")
                )
            }
        }

        // 4. TDS & Post-Tax Section
        item(key = "fd_tds_card") {
            FdTdsAndPostTaxCard(
                uiState = uiState,
                onSetHasPan = onSetHasPan,
                onSetTaxSlab = onSetTaxSlab
            )
        }

        // 5. Year-wise Table Card
        item(key = "fd_yearly_table") {
            DepositYearWiseCard(
                rows = result.yearlyRows,
                currency = currency,
                showDepositColumn = false,
                expanded = showYearlyTable,
                onToggleExpanded = { showYearlyTable = !showYearlyTable }
            )
        }

        item(key = "fd_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

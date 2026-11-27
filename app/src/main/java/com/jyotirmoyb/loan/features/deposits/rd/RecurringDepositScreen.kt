package com.jyotirmoyb.loan.features.deposits.rd

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
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
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
import com.jyotirmoyb.loan.features.deposits.fd.DepositYearWiseCard
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
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
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val RD_TENURE_PRESETS = listOf(12 to "1Y", 24 to "2Y", 36 to "3Y", 60 to "5Y", 84 to "7Y", 120 to "10Y")
private val RD_TAX_SLABS = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringDepositScreen(
    uiState: RecurringDepositUiState,
    onUpdateMonthlyDeposit: (Double) -> Unit,
    onUpdateRateText: (String) -> Unit,
    onResetRate: () -> Unit,
    onSetSeniorCitizen: (Boolean) -> Unit,
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
            .testTag("rd_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Card
        item(key = "rd_hero_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rd_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                    Text(
                        text = stringResource(R.string.rd_compounding_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = PrincipalEmerald
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.rd_total_deposited),
                            value = CurrencyFormatter.formatCompact(result.totalDeposited, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.fd_total_interest),
                            value = CurrencyFormatter.formatCompact(result.totalInterest, currency),
                            valueColor = PrincipalEmerald
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
                                stringResource(R.string.rd_effective_yield),
                                CurrencyFormatter.formatPercent(result.effectiveYieldPercent)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt_6c03a4,
                                stringResource(R.string.fd_post_tax_maturity),
                                CurrencyFormatter.formatCompact(result.postTaxMaturityAmount, currency)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrincipalEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. RD Inputs Card
        item(key = "rd_inputs_card") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.rd_title),
                    badge = "${CurrencyFormatter.formatPercent(result.effectiveNominalRatePercent)} p.a.",
                    icon = Icons.Outlined.Savings
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.monthlyDeposit,
                    currency = currency,
                    onAmountChange = onUpdateMonthlyDeposit,
                    label = stringResource(R.string.rd_monthly_deposit)
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumericInputField(
                        label = stringResource(R.string.fd_interest_rate),
                        valueText = uiState.rateText,
                        onValueChange = onUpdateRateText,
                        trailingBadge = "% p.a.",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rd_rate_input")
                    )
                    ResetToDefaultAction(
                        visible = uiState.isRateOverridden,
                        onClick = onResetRate,
                        testTag = "rd_rate_reset_btn"
                    )
                }

                uiState.rdRateItem?.let { item ->
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = item)
                }

                Spacer(Modifier.height(12.dp))

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
                            modifier = Modifier.testTag("rd_senior_switch")
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

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
                    RD_TENURE_PRESETS.forEach { (months, label) ->
                        FilterChip(
                            selected = input.tenureMonths == months,
                            onClick = { onUpdateTenureMonths(months) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("rd_tenure_chip_$months")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.fd_tenure_months),
                    valueText = input.tenureMonths.toString(),
                    onValueChange = { txt -> txt.toIntOrNull()?.let(onUpdateTenureMonths) },
                    trailingBadge = "${"%.1f".format(input.tenureMonths / 12.0)} yrs",
                    modifier = Modifier.testTag("rd_tenure_input")
                )
            }
        }

        // 3. TDS & Tax Card
        item(key = "rd_tax_card") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.fd_tds_section),
                    accentColor = InterestAmber,
                    icon = Icons.Outlined.ReceiptLong
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.fd_pan_toggle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = input.hasPan,
                        onCheckedChange = onSetHasPan,
                        modifier = Modifier.testTag("rd_pan_switch")
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.fd_tax_slab_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RD_TAX_SLABS.forEach { slab ->
                        FilterChip(
                            selected = input.taxSlabPercent == slab,
                            onClick = { onSetTaxSlab(slab) },
                            label = { Text(stringResource(R.string.ui_txt_fmt_361cec, slab.toInt())) },
                            modifier = Modifier.testTag("rd_slab_${slab.toInt()}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                KeyValueRow(
                    label = stringResource(R.string.fd_estimated_tds),
                    value = CurrencyFormatter.format(result.totalEstimatedTds, currency),
                    valueColor = if (result.totalEstimatedTds > 0) InterestAmber else SavingsGreen
                )
                KeyValueRow(
                    label = stringResource(R.string.fd_total_slab_tax),
                    value = CurrencyFormatter.format(result.totalSlabTax, currency)
                )
                KeyValueRow(
                    label = stringResource(R.string.fd_post_tax_maturity),
                    value = CurrencyFormatter.format(result.postTaxMaturityAmount, currency),
                    valueColor = SageGreen,
                    bold = true
                )

                uiState.tdsThresholdItem?.let { item ->
                    Spacer(Modifier.height(8.dp))
                    ValueSourceBadgesRow(item = item)
                }
            }
        }

        // 4. Year-wise breakdown
        item(key = "rd_yearly_table") {
            DepositYearWiseCard(
                rows = result.yearlyRows,
                currency = currency,
                showDepositColumn = true,
                expanded = showYearlyTable,
                onToggleExpanded = { showYearlyTable = !showYearlyTable }
            )
        }

        item(key = "rd_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

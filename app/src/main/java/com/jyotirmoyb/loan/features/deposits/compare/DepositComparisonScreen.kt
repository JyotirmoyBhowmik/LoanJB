package com.jyotirmoyb.loan.features.deposits.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.DepositInstrumentResult
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val HORIZON_PRESETS = listOf(3, 5, 10, 15, 20)
private val CMP_TAX_SLABS = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepositComparisonScreen(
    uiState: DepositComparisonUiState,
    onUpdateMonthly: (Double) -> Unit,
    onUpdateHorizonYears: (Int) -> Unit,
    onUpdateTaxSlab: (Double) -> Unit,
    onUpdateFdRate: (String) -> Unit,
    onUpdateRdRate: (String) -> Unit,
    onUpdatePpfRate: (String) -> Unit,
    onUpdateDebtRate: (String) -> Unit,
    onResetRates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val report = uiState.report
    val currency = uiState.currency
    val maxPostTax = report.instruments.maxOfOrNull { it.postTaxMaturity }?.coerceAtLeast(1.0) ?: 1.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("deposit_compare_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Winner Hero Banner
        if (report.isValid && report.instruments.isNotEmpty()) {
            val winner = report.instruments.firstOrNull { it.isWinner } ?: report.instruments.first()
            item(key = "dep_cmp_hero") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SlateNavy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dep_cmp_hero_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = PrincipalEmerald.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dep_cmp_winner_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = PrincipalEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = winner.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.dep_cmp_post_tax),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                                Text(
                                    text = CurrencyFormatter.format(winner.postTaxMaturity, currency),
                                    style = MonoLargeStyle,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.dep_cmp_post_tax_yield),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                                Text(
                                    text = CurrencyFormatter.formatPercent(winner.postTaxCagrPercent),
                                    style = MonoMediumStyle,
                                    color = PrincipalEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (report.taxAdvantageVsFd > 0.0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                            Text(
                                text = stringResource(
                                    R.string.ui_txt_fmt_2e870c,
                                    stringResource(R.string.dep_cmp_extra_vs_fd),
                                    CurrencyFormatter.format(report.taxAdvantageVsFd, currency)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = PrincipalEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. Inputs Card (Monthly investment, Horizon, Tax Slab)
        item(key = "dep_cmp_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.dep_cmp_title),
                    badge = "${input.taxSlabPercent.toInt()}% Slab",
                    icon = Icons.Outlined.CompareArrows
                )
                Spacer(Modifier.height(14.dp))

                AmountField(
                    amount = input.monthlyContribution,
                    currency = currency,
                    onAmountChange = onUpdateMonthly,
                    label = stringResource(R.string.dep_cmp_monthly_label)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.dep_cmp_horizon_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HORIZON_PRESETS.forEach { yr ->
                        FilterChip(
                            selected = input.horizonYears == yr,
                            onClick = { onUpdateHorizonYears(yr) },
                            label = { Text(stringResource(R.string.ui_txt_yrs, yr)) },
                            modifier = Modifier.testTag("dep_cmp_yr_$yr")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.fd_tax_slab_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CMP_TAX_SLABS.forEach { slab ->
                        FilterChip(
                            selected = input.taxSlabPercent == slab,
                            onClick = { onUpdateTaxSlab(slab) },
                            label = { Text(stringResource(R.string.ui_txt_fmt_361cec, slab.toInt())) },
                            modifier = Modifier.testTag("dep_cmp_slab_${slab.toInt()}")
                        )
                    }
                }
            }
        }

        // 3. Ranked Comparison Cards (FD, RD, PPF, Debt Fund)
        items(report.instruments, key = { it.id }) { instrument ->
            InstrumentComparisonCard(
                instrument = instrument,
                maxPostTax = maxPostTax,
                currency = currency
            )
        }

        // 4. Editable Rates Card
        item(key = "dep_cmp_rates") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionAccentHeader(
                        title = stringResource(R.string.dep_cmp_rates_section),
                        icon = Icons.Outlined.Tune,
                        modifier = Modifier.weight(1f)
                    )
                    ResetToDefaultAction(
                        visible = uiState.hasCustomRates,
                        onClick = onResetRates,
                        testTag = "dep_cmp_reset_rates_btn"
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = "FD (%)",
                        valueText = uiState.fdRateText,
                        onValueChange = onUpdateFdRate,
                        modifier = Modifier.weight(1f).testTag("dep_cmp_fd_rate")
                    )
                    NumericInputField(
                        label = "RD (%)",
                        valueText = uiState.rdRateText,
                        onValueChange = onUpdateRdRate,
                        modifier = Modifier.weight(1f).testTag("dep_cmp_rd_rate")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        label = "PPF (%)",
                        valueText = uiState.ppfRateText,
                        onValueChange = onUpdatePpfRate,
                        modifier = Modifier.weight(1f).testTag("dep_cmp_ppf_rate")
                    )
                    NumericInputField(
                        label = "Debt MF (%)",
                        valueText = uiState.debtRateText,
                        onValueChange = onUpdateDebtRate,
                        modifier = Modifier.weight(1f).testTag("dep_cmp_debt_rate")
                    )
                }

                uiState.ppfRateItem?.let { item ->
                    Spacer(Modifier.height(8.dp))
                    ValueSourceBadgesRow(item = item)
                }
            }
        }

        item(key = "dep_cmp_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InstrumentComparisonCard(instrument: DepositInstrumentResult, maxPostTax: Double, currency: CurrencyCode) {
    val accent = when (instrument.id) {
        "ppf" -> SavingsGreen
        "debt_fund" -> InfoBlue
        "fd" -> SageGreen
        else -> InterestAmber
    }
    val barFraction = (instrument.postTaxMaturity / maxPostTax).toFloat().coerceIn(0.08f, 1f)

    FintechCard(
        borderColor = if (instrument.isWinner) SageGreen else MaterialTheme.colorScheme.outline,
        modifier = Modifier.testTag("dep_cmp_card_${instrument.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instrument.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ui_txt_fmt_4ed101, instrument.rateLabel, instrument.taxRuleSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (instrument.isWinner) {
                Surface(
                    color = SageGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = stringResource(R.string.ui_txt_best),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SageGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Visual comparison progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricColumn(
                label = stringResource(R.string.dep_cmp_pre_tax),
                value = CurrencyFormatter.formatCompact(instrument.preTaxMaturity, currency),
                valueColor = MaterialTheme.colorScheme.onSurface
            )
            MetricColumn(
                label = stringResource(R.string.dep_cmp_tax_paid),
                value = if (instrument.estimatedTax <=
                    0.0
                ) {
                    "₹0 (Exempt)"
                } else {
                    "-${CurrencyFormatter.formatCompact(instrument.estimatedTax, currency)}"
                },
                valueColor = if (instrument.estimatedTax <= 0.0) SavingsGreen else InterestAmber
            )
            MetricColumn(
                label = stringResource(R.string.dep_cmp_post_tax),
                value = CurrencyFormatter.formatCompact(instrument.postTaxMaturity, currency),
                valueColor = accent,
                bold = true
            )
            MetricColumn(
                label = "Yield",
                value = CurrencyFormatter.formatPercent(instrument.postTaxCagrPercent),
                valueColor = accent,
                bold = true
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoSmallStyle,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

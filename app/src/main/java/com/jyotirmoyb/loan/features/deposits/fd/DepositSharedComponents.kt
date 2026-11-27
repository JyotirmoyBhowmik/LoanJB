package com.jyotirmoyb.loan.features.deposits.fd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.DepositYearRow
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

private val FD_TAX_SLABS = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FdTdsAndPostTaxCard(uiState: FixedDepositUiState, onSetHasPan: (Boolean) -> Unit, onSetTaxSlab: (Double) -> Unit) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    FintechCard {
        SectionAccentHeader(
            title = stringResource(R.string.fd_tds_section),
            badge = "${CurrencyFormatter.formatPercent(result.appliedTdsRatePercent)} TDS",
            accentColor = InterestAmber,
            icon = Icons.Outlined.ReceiptLong
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fd_pan_toggle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (input.hasPan) {
                        stringResource(R.string.fd_pan_yes, result.appliedTdsRatePercent.toInt().toString())
                    } else {
                        stringResource(R.string.fd_pan_no, result.appliedTdsRatePercent.toInt().toString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = input.hasPan,
                onCheckedChange = onSetHasPan,
                modifier = Modifier.testTag("fd_pan_switch")
            )
        }

        Spacer(Modifier.height(10.dp))

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
            FD_TAX_SLABS.forEach { slab ->
                FilterChip(
                    selected = input.taxSlabPercent == slab,
                    onClick = { onSetTaxSlab(slab) },
                    label = { Text(stringResource(R.string.ui_txt_fmt_361cec, slab.toInt())) },
                    modifier = Modifier.testTag("fd_slab_${slab.toInt()}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        KeyValueRow(
            label = stringResource(R.string.fd_tds_threshold_label),
            value = CurrencyFormatter.format(result.appliedTdsThreshold, currency)
        )
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

@Composable
internal fun HeroMetricItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.70f)
        )
        Text(
            text = value,
            style = MonoSmallStyle,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun KeyValueRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (bold) MonoMediumStyle else MonoSmallStyle,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
internal fun DepositYearWiseCard(
    rows: List<DepositYearRow>,
    currency: CurrencyCode,
    showDepositColumn: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    if (rows.isEmpty()) return
    FintechCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .testTag("deposit_yearwise_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionAccentHeader(
                title = stringResource(R.string.fd_yearwise_title),
                badge = "${rows.size} yrs",
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fd_col_fy),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.1f)
                    )
                    if (showDepositColumn) {
                        Text(
                            text = stringResource(R.string.fd_col_deposit),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.fd_col_opening),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.fd_col_interest),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.fd_col_closing),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = row.fyLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (row.estimatedTds > 0.0) {
                                Text(
                                    text = stringResource(R.string.ui_txt_tds, CurrencyFormatter.formatCompact(row.estimatedTds, currency)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InterestAmber
                                )
                            }
                        }
                        Text(
                            text = CurrencyFormatter.formatCompact(
                                if (showDepositColumn) row.depositAdded else row.openingBalance,
                                currency
                            ),
                            style = MonoSmallStyle,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(row.interestEarned, currency),
                            style = MonoSmallStyle,
                            color = SavingsGreen,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(row.closingBalance, currency),
                            style = MonoSmallStyle,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

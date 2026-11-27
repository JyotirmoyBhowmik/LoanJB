package com.jyotirmoyb.loan.features.tax.gst

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
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.GstCalculationMode
import com.jyotirmoyb.loan.domain.GstSupplyType
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.tax.TaxRulesMetadataFooter
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GstScreen(
    uiState: GstUiState,
    onUpdateAmount: (Double) -> Unit,
    onUpdateRate: (Double) -> Unit,
    onSelectMode: (GstCalculationMode) -> Unit,
    onSelectSupplyType: (GstSupplyType) -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    val halfRateText = formatCleanRate(result.gstRatePercent / 2.0)
    val fullRateText = formatCleanRate(result.gstRatePercent)
    var rateText by remember { mutableStateOf(formatCleanRate(input.gstRatePercent)) }
    LaunchedEffect(input.gstRatePercent) {
        val parsed = rateText.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - input.gstRatePercent) > 0.001) {
            rateText = formatCleanRate(input.gstRatePercent)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("gst_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero GST Summary Card
        item(key = "gst_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gst_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (input.mode == GstCalculationMode.ADD_GST) {
                            stringResource(R.string.gst_gross_amount).uppercase()
                        } else {
                            stringResource(R.string.gst_base_amount).uppercase()
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.format(
                            if (input.mode == GstCalculationMode.ADD_GST) result.grossAmount else result.baseAmount,
                            currency
                        ),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "Base",
                            value = CurrencyFormatter.formatCompact(result.baseAmount, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = "GST ($fullRateText%)",
                            value = CurrencyFormatter.formatCompact(result.totalGstAmount, currency),
                            valueColor = InterestAmber
                        )
                        HeroMetricItem(
                            label = "Total",
                            value = CurrencyFormatter.formatCompact(result.grossAmount, currency),
                            valueColor = PrincipalEmerald
                        )
                    }
                }
            }
        }

        // 2. Inputs & Slabs Card
        item(key = "gst_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.tax_suite_gst_title),
                    badge = "$fullRateText%",
                    icon = Icons.Outlined.Percent
                )
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GstCalculationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = input.mode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("gst_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.gst_amount_label),
                    amount = input.amount,
                    currency = currency,
                    onAmountChange = onUpdateAmount,
                    modifier = Modifier.testTag("gst_amount_input")
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.gst_slab_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.catalog.gstSlabs.forEach { slab ->
                        FilterChip(
                            selected = input.gstRatePercent == slab,
                            onClick = { onUpdateRate(slab) },
                            label = {
                                Text(stringResource(R.string.ui_txt_fmt_361cec, formatCleanRate(slab)), fontWeight = FontWeight.SemiBold)
                            },
                            modifier = Modifier.testTag("gst_slab_${formatCleanRate(slab)}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                NumericInputField(
                    label = stringResource(R.string.gst_custom_rate),
                    valueText = rateText,
                    onValueChange = { text ->
                        rateText = text
                        text.toDoubleOrNull()?.let(onUpdateRate)
                    },
                    trailingBadge = "%",
                    modifier = Modifier.testTag("gst_custom_rate_input")
                )

                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GstSupplyType.entries.forEach { supply ->
                        FilterChip(
                            selected = input.supplyType == supply,
                            onClick = { onSelectSupplyType(supply) },
                            label = { Text(supply.label) },
                            modifier = Modifier.testTag("gst_supply_${supply.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 3. CGST / SGST / IGST Split Card
        item(key = "gst_breakdown") {
            FintechCard(modifier = Modifier.testTag("gst_breakdown_card")) {
                SectionAccentHeader(
                    title = "GST Invoice Split",
                    badge = if (input.supplyType == GstSupplyType.INTRA_STATE) "CGST + SGST" else "IGST"
                )
                Spacer(Modifier.height(10.dp))

                KeyValueRow(
                    label = stringResource(R.string.gst_base_amount),
                    value = CurrencyFormatter.format(result.baseAmount, currency)
                )
                if (input.supplyType == GstSupplyType.INTRA_STATE) {
                    KeyValueRow(
                        label = stringResource(R.string.gst_cgst_label, halfRateText),
                        value = CurrencyFormatter.format(result.cgstAmount, currency),
                        valueColor = InfoBlue
                    )
                    KeyValueRow(
                        label = stringResource(R.string.gst_sgst_label, halfRateText),
                        value = CurrencyFormatter.format(result.sgstAmount, currency),
                        valueColor = InfoBlue
                    )
                } else {
                    KeyValueRow(
                        label = stringResource(R.string.gst_igst_label, fullRateText),
                        value = CurrencyFormatter.format(result.igstAmount, currency),
                        valueColor = InterestAmber
                    )
                }
                KeyValueRow(
                    label = stringResource(R.string.gst_total_gst),
                    value = CurrencyFormatter.format(result.totalGstAmount, currency),
                    valueColor = InterestAmber
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                KeyValueRow(
                    label = stringResource(R.string.gst_gross_amount),
                    value = CurrencyFormatter.format(result.grossAmount, currency),
                    valueColor = PrincipalEmerald
                )
            }
        }

        // 4. Tax Rules Metadata Footer
        item(key = "gst_footer") {
            TaxRulesMetadataFooter(
                catalog = uiState.catalog,
                activeFyCode = uiState.catalog.taxYears.firstOrNull()?.fyCode,
                lawReference = "CBIC GST Slabs: ${uiState.catalog.gstSlabs.joinToString("%, ") { formatCleanRate(it) }}%",
                isCheckingUpdates = uiState.isCheckingUpdates,
                syncFeedback = uiState.syncFeedback,
                onCheckForUpdatedTaxRules = onCheckUpdates
            )
        }

        item(key = "gst_disclaimer") {
            DisclaimerBanner()
        }
    }
}

private fun formatCleanRate(rate: Double): String =
    if (rate % 1.0 == 0.0) rate.toInt().toString() else String.format("%.2f", rate).trimEnd('0').trimEnd('.')

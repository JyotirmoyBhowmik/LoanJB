package com.jyotirmoyb.loan.features.mutualfund.tax

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.MfFundTaxCategory
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.mutualfund.MutualFundRiskNoticeBanner
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val HOLDING_PRESETS = listOf(6 to "6M", 12 to "12M", 18 to "18M", 24 to "24M", 36 to "3Y", 60 to "5Y")
private val MF_TAX_SLABS = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MfTaxGainsScreen(
    uiState: MfTaxGainsUiState,
    onSelectCategory: (MfFundTaxCategory) -> Unit,
    onUpdateInvested: (Double) -> Unit,
    onUpdateRedemption: (Double) -> Unit,
    onUpdateHoldingMonths: (Int) -> Unit,
    onSelectSlab: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mf_tax_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Tax Estimate Card
        item(key = "mf_tax_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mf_tax_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (result.isLongTerm) PrincipalEmerald.copy(alpha = 0.22f) else InterestAmber.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (result.isLongTerm) "LONG-TERM CAPITAL GAIN (LTCG)" else "SHORT-TERM / SLAB TAX",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result.isLongTerm) PrincipalEmerald else InterestAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.mf_tax_estimated_tax),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.format(result.totalTax, currency),
                        style = MonoLargeStyle,
                        color = Color.White
                    )
                    Text(
                        text = result.appliedRuleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrincipalEmerald
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.mf_tax_total_gain),
                            value = CurrencyFormatter.formatCompact(result.totalGain, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.mf_tax_post_tax_proceeds),
                            value = CurrencyFormatter.formatCompact(result.netPostTaxProceeds, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "Eff. Tax",
                            value = CurrencyFormatter.formatPercent(result.effectiveTaxRateOnGainPercent),
                            valueColor = InterestAmber
                        )
                    }
                }
            }
        }

        // 2. Fund Category & Inputs
        item(key = "mf_tax_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.mf_tax_title),
                    badge = "${input.holdingMonths} mos",
                    icon = Icons.Outlined.ReceiptLong
                )
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MfFundTaxCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = input.category == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("mf_tax_cat_${cat.name.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                AmountField(
                    amount = input.investedAmount,
                    currency = currency,
                    onAmountChange = onUpdateInvested,
                    label = stringResource(R.string.mf_tax_invested)
                )
                Spacer(Modifier.height(12.dp))

                AmountField(
                    amount = input.redemptionValue,
                    currency = currency,
                    onAmountChange = onUpdateRedemption,
                    label = stringResource(R.string.mf_tax_redemption)
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.mf_tax_holding_months),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HOLDING_PRESETS.forEach { (mos, lbl) ->
                        FilterChip(
                            selected = input.holdingMonths == mos,
                            onClick = { onUpdateHoldingMonths(mos) },
                            label = { Text(lbl) },
                            modifier = Modifier.testTag("mf_tax_hold_$mos")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.mf_tax_holding_months),
                    valueText = input.holdingMonths.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onUpdateHoldingMonths) },
                    trailingBadge = "mos",
                    modifier = Modifier.testTag("mf_tax_holding_input")
                )

                if (input.category == MfFundTaxCategory.DEBT ||
                    (input.category == MfFundTaxCategory.HYBRID_GOLD_INTL && !result.isLongTerm)
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.fd_tax_slab_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MF_TAX_SLABS.forEach { slab ->
                            FilterChip(
                                selected = input.userTaxSlabPercent == slab,
                                onClick = { onSelectSlab(slab) },
                                label = { Text("${slab.toInt()}%") },
                                modifier = Modifier.testTag("mf_tax_slab_${slab.toInt()}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Detailed Tax Computation Card + Source Chips
        item(key = "mf_tax_breakdown") {
            FintechCard {
                SectionAccentHeader(
                    title = "Capital Gains Breakdown",
                    accentColor = InterestAmber
                )
                Spacer(Modifier.height(10.dp))

                KeyValueRow(
                    label = stringResource(R.string.mf_tax_total_gain),
                    value = CurrencyFormatter.format(result.totalGain, currency)
                )
                KeyValueRow(
                    label = stringResource(R.string.mf_tax_exemption),
                    value = "-${CurrencyFormatter.format(result.exemptionApplied, currency)}",
                    valueColor = SavingsGreen
                )
                KeyValueRow(
                    label = stringResource(R.string.mf_tax_taxable_gain),
                    value = CurrencyFormatter.format(result.taxableGain, currency)
                )
                KeyValueRow(
                    label = "Base Tax (${CurrencyFormatter.formatPercent(result.appliedBaseRatePercent)})",
                    value = CurrencyFormatter.format(result.baseTax, currency)
                )
                KeyValueRow(
                    label = "Health & Education Cess (${uiState.config.cessPercent.toInt()}%)",
                    value = CurrencyFormatter.format(result.cessAmount, currency)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                KeyValueRow(
                    label = stringResource(R.string.mf_tax_post_tax_proceeds),
                    value = CurrencyFormatter.format(result.netPostTaxProceeds, currency),
                    valueColor = SageGreen,
                    bold = true
                )

                val badgeItem = when (input.category) {
                    MfFundTaxCategory.EQUITY -> if (result.isLongTerm) uiState.equityLtcgRateItem else uiState.equityStcgRateItem
                    MfFundTaxCategory.HYBRID_GOLD_INTL -> uiState.hybridLtcgRateItem
                    MfFundTaxCategory.DEBT -> uiState.equityLtcgExemptionItem
                }
                badgeItem?.let { item ->
                    Spacer(Modifier.height(8.dp))
                    ValueSourceBadgesRow(item = item)
                }
            }
        }

        item(key = "mf_tax_risk") { MutualFundRiskNoticeBanner() }
        item(key = "mf_tax_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

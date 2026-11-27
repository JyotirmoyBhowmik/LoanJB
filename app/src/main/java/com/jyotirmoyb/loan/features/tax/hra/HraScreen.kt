package com.jyotirmoyb.loan.features.tax.hra

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jyotirmoyb.loan.features.tax.TaxRulesMetadataFooter
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val OLD_SLABS = listOf(5.0, 20.0, 30.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HraScreen(
    uiState: HraUiState,
    onSetMonthlyMode: (Boolean) -> Unit,
    onUpdateBasicDa: (Double) -> Unit,
    onUpdateHraReceived: (Double) -> Unit,
    onUpdateRentPaid: (Double) -> Unit,
    onSetMetroCity: (Boolean) -> Unit,
    onSelectSlab: (Double) -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.result
    val currency = uiState.currency
    val divisor = if (uiState.isMonthlyInputMode) 12.0 else 1.0
    val metroPct = uiState.catalog.hraMetroPercent.toInt()
    val nonMetroPct = uiState.catalog.hraNonMetroPercent.toInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("hra_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Exempt HRA Card
        item(key = "hra_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hra_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hra_exempt_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.format(result.exemptHraAnnual, currency),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    Text(
                        text = stringResource(R.string.ui_txt_month_exempt, CurrencyFormatter.format(result.exemptHraMonthly, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.hra_taxable_label),
                            value = CurrencyFormatter.formatCompact(result.taxableHraAnnual, currency),
                            valueColor = InterestAmber
                        )
                        HeroMetricItem(
                            label = "Est. Tax Saved",
                            value = CurrencyFormatter.formatCompact(result.estimatedTaxSavedAnnual, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "City Rule",
                            value = "${result.appliedCityPercent.toInt()}%",
                            valueColor = Color.White
                        )
                    }
                }
            }
        }

        // 2. Inputs Card
        item(key = "hra_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.tax_suite_hra_title),
                    badge = "Sec 10(13A)",
                    icon = Icons.Outlined.HomeWork
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !uiState.isMonthlyInputMode,
                        onClick = { onSetMonthlyMode(false) },
                        label = { Text(stringResource(R.string.hra_input_annual)) },
                        modifier = Modifier.testTag("hra_mode_annual")
                    )
                    FilterChip(
                        selected = uiState.isMonthlyInputMode,
                        onClick = { onSetMonthlyMode(true) },
                        label = { Text(stringResource(R.string.hra_input_monthly)) },
                        modifier = Modifier.testTag("hra_mode_monthly")
                    )
                }

                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.hra_basic_da),
                    amount = uiState.input.basicPlusDaAnnual / divisor,
                    currency = currency,
                    onAmountChange = onUpdateBasicDa,
                    modifier = Modifier.testTag("hra_basic_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.hra_received),
                    amount = uiState.input.hraReceivedAnnual / divisor,
                    currency = currency,
                    onAmountChange = onUpdateHraReceived,
                    modifier = Modifier.testTag("hra_received_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.hra_rent_paid),
                    amount = uiState.input.actualRentPaidAnnual / divisor,
                    currency = currency,
                    onAmountChange = onUpdateRentPaid,
                    modifier = Modifier.testTag("hra_rent_input")
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.input.isMetroCity,
                        onClick = { onSetMetroCity(true) },
                        label = { Text(stringResource(R.string.hra_city_metro, metroPct)) },
                        modifier = Modifier.testTag("hra_metro_chip")
                    )
                    FilterChip(
                        selected = !uiState.input.isMetroCity,
                        onClick = { onSetMetroCity(false) },
                        label = { Text(stringResource(R.string.hra_city_non_metro, nonMetroPct)) },
                        modifier = Modifier.testTag("hra_non_metro_chip")
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ui_txt_old_regime_tax_slab_for_tax_saved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OLD_SLABS.forEach { slab ->
                        FilterChip(
                            selected = uiState.input.marginalTaxSlabPercent == slab,
                            onClick = { onSelectSlab(slab) },
                            label = { Text(stringResource(R.string.ui_txt_fmt_361cec, slab.toInt())) },
                            modifier = Modifier.testTag("hra_slab_${slab.toInt()}")
                        )
                    }
                }
            }
        }

        // 3. Least-of-Three Rules Breakdown Card
        item(key = "hra_three_rules") {
            FintechCard(modifier = Modifier.testTag("hra_three_rules_card")) {
                SectionAccentHeader(
                    title = stringResource(R.string.hra_three_rules_title),
                    badge = "Rule 2A"
                )
                Spacer(Modifier.height(10.dp))

                result.ruleRows.forEach { row ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (row.isWinningLeast) SavingsGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (row.isWinningLeast) SavingsGreen.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = CurrencyFormatter.format(row.amountAnnual, currency),
                                    style = MonoSmallStyle,
                                    color = if (row.isWinningLeast) SavingsGreen else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.formulaHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (row.isWinningLeast) {
                                    Surface(
                                        color = SavingsGreen,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.hra_least_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Rent Receipt & Landlord PAN Compliance Note
        item(key = "hra_rent_receipt_note") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hra_rent_receipt_card"),
                shape = RoundedCornerShape(16.dp),
                color = InterestAmber.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, InterestAmber.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = InterestAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.hra_rent_receipt_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (result.requiresLandlordPan) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.hra_landlord_pan_required),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.hra_rent_receipt_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. Tax Rules Metadata Footer
        item(key = "hra_footer") {
            TaxRulesMetadataFooter(
                catalog = uiState.catalog,
                activeFyCode = uiState.catalog.taxYears.firstOrNull()?.fyCode,
                lawReference = "Income-tax Rules, Rule 2A • Metro $metroPct% / Non-Metro $nonMetroPct%",
                isCheckingUpdates = uiState.isCheckingUpdates,
                syncFeedback = uiState.syncFeedback,
                onCheckForUpdatedTaxRules = onCheckUpdates
            )
        }

        item(key = "hra_disclaimer") {
            DisclaimerBanner()
        }
    }
}

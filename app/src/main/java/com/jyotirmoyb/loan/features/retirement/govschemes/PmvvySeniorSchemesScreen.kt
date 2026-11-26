package com.jyotirmoyb.loan.features.retirement.govschemes

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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Elderly
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
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PmvvySeniorSchemesScreen(
    uiState: GovPensionUiState,
    onSelectScheme: (String) -> Unit,
    onUpdateInvestment: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val catalog = uiState.catalog
    val currency = uiState.currency
    val payoutResult = uiState.seniorPayoutResult
    val activeScheme = payoutResult?.scheme

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pmvvy_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clear Open / Closed Status Banner driven by schemes.json
        if (activeScheme != null) {
            item(key = "senior_status_banner") {
                val isClosed = activeScheme.isClosed
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isClosed) MaterialTheme.colorScheme.errorContainer else SavingsGreen.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isClosed) MaterialTheme.colorScheme.error else SavingsGreen
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("senior_scheme_status_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isClosed) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (isClosed) MaterialTheme.colorScheme.error else SavingsGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${activeScheme.shortName}: ${activeScheme.statusLabel.uppercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isClosed) MaterialTheme.colorScheme.error else SavingsGreen
                            )
                        }
                        Text(
                            text = activeScheme.statusNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isClosed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                        if (activeScheme.existingHoldersNote.isNotBlank()) {
                            Text(
                                text = activeScheme.existingHoldersNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Hero Payout Summary Card
            item(key = "senior_hero") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SlateNavy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("senior_scheme_hero_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${activeScheme.shortName.uppercase()} • ${payoutResult.payoutPeriodLabel.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(payoutResult.periodicPayout, currency),
                            style = MonoLargeStyle,
                            color = PrincipalEmerald
                        )
                        Text(
                            text = "Indicative Rate: ${activeScheme.ratePercent}% p.a. • Tenure: ${activeScheme.tenureYears} yrs • Issuer: ${activeScheme.sovereignIssuer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeroMetricItem(
                                label = "Annual Interest",
                                value = CurrencyFormatter.formatCompact(payoutResult.annualInterest, currency),
                                valueColor = InterestAmber
                            )
                            HeroMetricItem(
                                label = "Total Interest",
                                value = CurrencyFormatter.formatCompact(payoutResult.totalInterestOverTenure, currency),
                                valueColor = PrincipalEmerald
                            )
                            HeroMetricItem(
                                label = "Max Cap",
                                value = activeScheme.maxInvestmentCap?.let { CurrencyFormatter.formatCompact(it, currency) } ?: "No Cap",
                                valueColor = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 3. Scheme Selector & Investment Amount Input
        item(key = "senior_controls") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.pmvvy_scheme_selector),
                    badge = "${catalog.seniorPensionSchemes.size} schemes",
                    icon = Icons.Outlined.Elderly
                )
                Spacer(Modifier.height(6.dp))
                ValueSourceBadgesRow(
                    source = catalog.source,
                    asOfDate = catalog.asOfDateFormatted,
                    isUnverified = catalog.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true)
                )
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    catalog.seniorPensionSchemes.forEach { sch ->
                        val statusTag = if (sch.isClosed) " (Closed)" else " (Open)"
                        FilterChip(
                            selected = uiState.selectedSeniorSchemeId == sch.id,
                            onClick = { onSelectScheme(sch.id) },
                            label = { Text("${sch.shortName}$statusTag", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("senior_scheme_chip_${sch.id}")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                AmountField(
                    label = stringResource(R.string.pmvvy_investment_amount),
                    amount = uiState.seniorInvestmentAmount,
                    currency = currency,
                    onAmountChange = onUpdateInvestment,
                    modifier = Modifier.testTag("senior_investment_input")
                )

                if (payoutResult != null && payoutResult.exceedsCap && activeScheme?.maxInvestmentCap != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Note: Investment exceeds the ${CurrencyFormatter.format(activeScheme.maxInvestmentCap, currency)} statutory limit; payout above is calculated on the capped maximum.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. All Senior Schemes Status Overview Table (Open vs Closed comparison)
        item(key = "senior_all_overview") {
            FintechCard(modifier = Modifier.testTag("senior_overview_card")) {
                SectionAccentHeader(
                    title = "Senior Schemes Availability Status",
                    badge = "schemes.json"
                )
                Spacer(Modifier.height(8.dp))
                catalog.seniorPensionSchemes.forEachIndexed { index, sch ->
                    KeyValueRow(
                        label = "${sch.shortName} (${sch.ratePercent}% • ${sch.tenureYears}y)",
                        value = if (sch.isClosed) "CLOSED" else "OPEN",
                        valueColor = if (sch.isClosed) MaterialTheme.colorScheme.error else SavingsGreen
                    )
                    if (index < catalog.seniorPensionSchemes.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item(key = "pmvvy_disclaimer") {
            DisclaimerBanner()
        }
    }
}

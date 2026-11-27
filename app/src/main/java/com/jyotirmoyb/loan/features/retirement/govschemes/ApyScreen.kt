package com.jyotirmoyb.loan.features.retirement.govschemes

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.retirement.planner.AgeSliderRow
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val APY_SLABS = listOf(1000, 2000, 3000, 4000, 5000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApyScreen(uiState: GovPensionUiState, onUpdateAge: (Int) -> Unit, onSelectSlab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val result = uiState.apyResult
    val currency = uiState.currency
    val catalog = uiState.catalog

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("apy_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Monthly Contribution Card
        item(key = "apy_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apy_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.apy_monthly_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.ui_txt_mo, CurrencyFormatter.format(result.monthlyContribution, currency)),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_txt_guaranteed_pension_of_month_from_a,
                            CurrencyFormatter.format(result.desiredMonthlyPension.toDouble(), currency),
                            result.contributionYears
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "Total Paid",
                            value = CurrencyFormatter.formatCompact(result.totalPersonalContribution, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = "Nominee Corpus",
                            value = CurrencyFormatter.formatCompact(result.indicativeNomineeCorpus, currency),
                            valueColor = InterestAmber
                        )
                        HeroMetricItem(
                            label = "Quarterly",
                            value = CurrencyFormatter.format(result.quarterlyContribution, currency),
                            valueColor = PrincipalEmerald
                        )
                    }
                }
            }
        }

        // 2. Entry Age & Pension Slab Selector
        item(key = "apy_controls") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.ret_apy_title),
                    badge = "PFRDA",
                    icon = Icons.Outlined.Elderly
                )
                Spacer(Modifier.height(6.dp))
                ValueSourceBadgesRow(
                    source = catalog.source,
                    asOfDate = catalog.asOfDateFormatted,
                    isUnverified = catalog.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true),
                    sourceName = catalog.sourceName,
                    effectiveFrom = catalog.publishedOn,
                    lastVerified = catalog.lastVerified
                )
                Spacer(Modifier.height(12.dp))

                AgeSliderRow(
                    label = stringResource(R.string.apy_entry_age),
                    value = uiState.apyEntryAge,
                    range = 18f..40f,
                    onValueChange = onUpdateAge,
                    testTag = "apy_age_slider"
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.apy_desired_pension),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    APY_SLABS.forEach { slab ->
                        FilterChip(
                            selected = uiState.apyPensionSlab == slab,
                            onClick = { onSelectSlab(slab) },
                            label = { Text(stringResource(R.string.ui_txt_mo_94b864, slab), fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("apy_slab_$slab")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ui_txt_eligibility_indian_citizens_aged_1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Full Data-Driven APY Contribution Table (from schemes.json)
        item(key = "apy_table") {
            FintechCard(modifier = Modifier.testTag("apy_table_card")) {
                SectionAccentHeader(
                    title = stringResource(R.string.apy_table_title),
                    badge = "schemes.json",
                    icon = Icons.Outlined.TableChart
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_txt_age),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.7f)
                    )
                    Text(
                        stringResource(R.string.ui_txt_1k),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.ui_txt_2k),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.ui_txt_3k),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.ui_txt_4k),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.ui_txt_5k),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))

                catalog.apyTable.forEach { row ->
                    val isSelected = row.entryAge == uiState.apyEntryAge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) SavingsGreen.copy(alpha = 0.14f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ui_txt_fmt_986d4f, row.entryAge),
                            style = MonoSmallStyle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) SavingsGreen else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.7f)
                        )
                        listOf(row.pension1000, row.pension2000, row.pension3000, row.pension4000, row.pension5000).forEach { amt ->
                            Text(
                                text = stringResource(R.string.ui_txt_fmt_82d0a2, amt.toInt()),
                                style = MonoSmallStyle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item(key = "apy_disclaimer") {
            DisclaimerBanner()
        }
    }
}

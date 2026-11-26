package com.jyotirmoyb.loan.features.retirement.govschemes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.emi.AmountField
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

@Composable
fun PmsymScreen(
    uiState: GovPensionUiState,
    onUpdateAge: (Int) -> Unit,
    onUpdateIncome: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.pmsymResult
    val currency = uiState.currency
    val catalog = uiState.catalog

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pmsym_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Card
        item(key = "pmsym_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pmsym_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pmsym_monthly_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${CurrencyFormatter.format(result.subscriberMonthlyContribution, currency)}/mo",
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    Text(
                        text = "+ Equal Govt. of India Match: ${CurrencyFormatter.format(result.govtMatchingMonthlyContribution, currency)}/mo → Assured ${CurrencyFormatter.format(result.assuredMonthlyPension, currency)}/mo pension at 60",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.90f),
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "Your Total",
                            value = CurrencyFormatter.formatCompact(result.totalSubscriberContribution, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = "Govt. Match Total",
                            value = CurrencyFormatter.formatCompact(result.totalGovtContribution, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "Duration",
                            value = "${result.contributionYears} yrs",
                            valueColor = InterestAmber
                        )
                    }
                }
            }
        }

        // 2. Eligibility Warning Banner if monthly income > ceiling
        if (!result.isIncomeEligible) {
            item(key = "pmsym_ineligible_banner") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pmsym_ineligible_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Entered income exceeds the ${CurrencyFormatter.format(result.incomeCeilingApplied, currency)}/month eligibility ceiling for PM-SYM. Consider Atal Pension Yojana (APY) or NPS instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Inputs Card
        item(key = "pmsym_inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.ret_pmsym_title),
                    badge = "1:1 Match",
                    icon = Icons.Outlined.Handshake
                )
                Spacer(Modifier.height(6.dp))
                ValueSourceBadgesRow(
                    source = catalog.source,
                    asOfDate = catalog.asOfDateFormatted,
                    isUnverified = catalog.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true)
                )
                Spacer(Modifier.height(12.dp))

                AgeSliderRow(
                    label = stringResource(R.string.apy_entry_age),
                    value = uiState.pmsymEntryAge,
                    range = 18f..40f,
                    onValueChange = onUpdateAge,
                    testTag = "pmsym_age_slider"
                )
                Spacer(Modifier.height(8.dp))
                AmountField(
                    label = stringResource(R.string.pmsym_monthly_income),
                    amount = uiState.pmsymMonthlyIncome,
                    currency = currency,
                    onAmountChange = onUpdateIncome,
                    modifier = Modifier.testTag("pmsym_income_input")
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.pmsym_govt_match_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Data-Driven PM-SYM Contribution Schedule
        item(key = "pmsym_table") {
            FintechCard(modifier = Modifier.testTag("pmsym_table_card")) {
                SectionAccentHeader(
                    title = stringResource(R.string.pmsym_table_title),
                    badge = "schemes.json",
                    icon = Icons.Outlined.TableChart
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Age", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                    Text("You / mo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("Govt / mo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("Total / mo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))

                catalog.pmsymTable.forEach { row ->
                    val isSelected = row.entryAge == uiState.pmsymEntryAge
                    val sub = row.monthlyContribution.toInt()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) SavingsGreen.copy(alpha = 0.14f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${row.entryAge}", style = MonoSmallStyle, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) SavingsGreen else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.8f))
                        Text("₹$sub", style = MonoSmallStyle, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("₹$sub", style = MonoSmallStyle, color = SavingsGreen, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("₹${sub * 2}", style = MonoSmallStyle, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item(key = "pmsym_disclaimer") {
            DisclaimerBanner()
        }
    }
}

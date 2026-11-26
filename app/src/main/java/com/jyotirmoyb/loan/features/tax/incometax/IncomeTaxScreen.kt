package com.jyotirmoyb.loan.features.tax.incometax

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jyotirmoyb.loan.domain.RegimeTaxCalculation
import com.jyotirmoyb.loan.domain.TaxpayerAgeGroup
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.tax.TaxRulesMetadataFooter
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeTaxScreen(
    uiState: IncomeTaxUiState,
    onSelectFy: (String) -> Unit,
    onUpdateGrossSalary: (Double) -> Unit,
    onUpdateOtherIncome: (Double) -> Unit,
    onToggleSalaried: (Boolean) -> Unit,
    onSelectAgeGroup: (TaxpayerAgeGroup) -> Unit,
    onToggleParentsSenior: (Boolean) -> Unit,
    onUpdateDeduction: (String, Double) -> Unit,
    onSetDeductionFilter: (DeductionRegimeFilter) -> Unit,
    onResetDeductions: () -> Unit,
    onToggleSlabBreakdown: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.result
    val currency = uiState.currency
    val fyRules = uiState.activeFyRules
    val newRegime = result.newRegime
    val oldRegime = result.oldRegime

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("income_tax_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Winner Hero Banner + Side-by-Side Comparison
        if (result.isValid && newRegime != null && oldRegime != null) {
            item(key = "tax_winner_hero") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SlateNavy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("income_tax_hero_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tax_better_regime_label, result.fyCode),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.recommendedRegimeName,
                            style = MonoLargeStyle,
                            color = PrincipalEmerald
                        )
                        if (result.annualTaxSavings > 0.0) {
                            Text(
                                text = stringResource(
                                    R.string.tax_saves_per_year,
                                    CurrencyFormatter.format(result.annualTaxSavings, currency),
                                    CurrencyFormatter.format(result.monthlyTaxSavings, currency)
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RegimeHeroPill(
                                title = stringResource(R.string.tax_new_regime),
                                taxPayable = CurrencyFormatter.format(newRegime.totalTaxPayable, currency),
                                monthlyTds = "${CurrencyFormatter.formatCompact(newRegime.monthlyTdsEstimate, currency)}/mo",
                                isWinner = result.recommendedRegimeId == "NEW",
                                modifier = Modifier.weight(1f)
                            )
                            RegimeHeroPill(
                                title = stringResource(R.string.tax_old_regime),
                                taxPayable = CurrencyFormatter.format(oldRegime.totalTaxPayable, currency),
                                monthlyTds = "${CurrencyFormatter.formatCompact(oldRegime.monthlyTdsEstimate, currency)}/mo",
                                isWinner = result.recommendedRegimeId == "OLD",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. FY Selector & Income Profile Card
        item(key = "tax_fy_and_income") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.tax_select_fy),
                    badge = fyRules?.ayCode ?: "",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong
                )
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.catalog.taxYears.forEach { fy ->
                        FilterChip(
                            selected = uiState.selectedFyCode == fy.fyCode,
                            onClick = { onSelectFy(fy.fyCode) },
                            label = { Text(fy.fyCode, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("tax_fy_chip_${fy.fyCode.replace(" ", "_")}")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tax_age_group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaxpayerAgeGroup.entries.forEach { age ->
                        FilterChip(
                            selected = uiState.input.ageGroup == age,
                            onClick = { onSelectAgeGroup(age) },
                            label = { Text(age.label) },
                            modifier = Modifier.testTag("tax_age_${age.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tax_salaried_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.tax_salaried_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.input.isSalaried,
                        onCheckedChange = onToggleSalaried,
                        modifier = Modifier.testTag("tax_salaried_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))
                AmountField(
                    label = stringResource(R.string.tax_gross_salary),
                    amount = uiState.input.grossSalary,
                    currency = currency,
                    onAmountChange = onUpdateGrossSalary,
                    modifier = Modifier.testTag("tax_gross_salary_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.tax_other_income),
                    amount = uiState.input.otherIncome,
                    currency = currency,
                    onAmountChange = onUpdateOtherIncome,
                    modifier = Modifier.testTag("tax_other_income_input")
                )
            }
        }

        // 3. Data-Driven Deductions Card (Only shows applicable fields per selected filter & FY schema)
        if (fyRules != null) {
            item(key = "tax_deductions_card") {
                val visibleDeductions = fyRules.deductions.filter { rule ->
                    when (uiState.deductionFilter) {
                        DeductionRegimeFilter.ALL -> rule.allowedInOld || rule.allowedInNew
                        DeductionRegimeFilter.NEW_ONLY -> rule.allowedInNew
                        DeductionRegimeFilter.OLD_ONLY -> rule.allowedInOld
                    }
                }

                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionAccentHeader(
                            title = stringResource(R.string.tax_deductions_title),
                            badge = "${visibleDeductions.size} fields",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onResetDeductions,
                            modifier = Modifier.testTag("tax_reset_deductions_btn")
                        ) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.deductionFilter == DeductionRegimeFilter.ALL,
                            onClick = { onSetDeductionFilter(DeductionRegimeFilter.ALL) },
                            label = { Text(stringResource(R.string.tax_filter_all)) },
                            modifier = Modifier.testTag("tax_ded_filter_all")
                        )
                        FilterChip(
                            selected = uiState.deductionFilter == DeductionRegimeFilter.NEW_ONLY,
                            onClick = { onSetDeductionFilter(DeductionRegimeFilter.NEW_ONLY) },
                            label = { Text(stringResource(R.string.tax_filter_new)) },
                            modifier = Modifier.testTag("tax_ded_filter_new")
                        )
                        FilterChip(
                            selected = uiState.deductionFilter == DeductionRegimeFilter.OLD_ONLY,
                            onClick = { onSetDeductionFilter(DeductionRegimeFilter.OLD_ONLY) },
                            label = { Text(stringResource(R.string.tax_filter_old)) },
                            modifier = Modifier.testTag("tax_ded_filter_old")
                        )
                    }

                    if (uiState.deductionFilter != DeductionRegimeFilter.NEW_ONLY) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.tax_parents_senior),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = uiState.input.parentsAreSenior,
                                onCheckedChange = onToggleParentsSenior,
                                modifier = Modifier.testTag("tax_parents_senior_switch")
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    visibleDeductions.forEach { rule ->
                        val isSeniorTaxpayer = uiState.input.ageGroup != TaxpayerAgeGroup.GENERAL
                        val cap = if (rule.id == "sec80d_parents" && uiState.input.parentsAreSenior && rule.capSenior != null) {
                            rule.capSenior
                        } else if (rule.id != "sec80d_parents" && isSeniorTaxpayer && rule.capSenior != null) {
                            rule.capSenior
                        } else {
                            rule.capGeneral
                        }
                        val regimeTag = when {
                            rule.allowedInNew && rule.allowedInOld -> "Old & New"
                            rule.allowedInNew -> "New Only"
                            else -> "Old Only"
                        }
                        val capTag = cap?.let { "Cap ${CurrencyFormatter.formatCompact(it, currency)}" } ?: "No fixed cap"

                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rule.section} • $regimeTag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rule.allowedInNew) SavingsGreen else InfoBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = capTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            AmountField(
                                label = rule.label,
                                amount = uiState.input.deductionAmounts[rule.id] ?: 0.0,
                                currency = currency,
                                onAmountChange = { onUpdateDeduction(rule.id, it) },
                                modifier = Modifier.testTag("tax_ded_input_${rule.id}")
                            )
                        }
                    }
                }
            }
        }

        // 4. Side-by-Side Detailed Tax Table
        if (result.isValid && newRegime != null && oldRegime != null) {
            item(key = "tax_side_by_side_table") {
                FintechCard(modifier = Modifier.testTag("income_tax_comparison_card")) {
                    SectionAccentHeader(
                        title = "New vs Old Regime (${result.fyCode})",
                        badge = "Side-by-Side"
                    )
                    Spacer(Modifier.height(10.dp))

                    ComparisonHeaderRow()
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow("Gross Income", newRegime.grossTotalIncome, oldRegime.grossTotalIncome, currency)
                    ComparisonRow("Standard Ded.", -newRegime.standardDeduction, -oldRegime.standardDeduction, currency)
                    ComparisonRow("Other Deductions", -newRegime.otherDeductionsTotal, -oldRegime.otherDeductionsTotal, currency)
                    ComparisonRow(stringResource(R.string.tax_taxable_income), newRegime.taxableIncome, oldRegime.taxableIncome, currency, bold = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow(stringResource(R.string.tax_slab_tax), newRegime.slabTax, oldRegime.slabTax, currency)
                    if (newRegime.rebate87A > 0.0 || oldRegime.rebate87A > 0.0) {
                        ComparisonRow(stringResource(R.string.tax_rebate_87a), -newRegime.rebate87A, -oldRegime.rebate87A, currency, highlightGreen = true)
                    }
                    if (newRegime.rebateMarginalRelief > 0.0 || oldRegime.rebateMarginalRelief > 0.0) {
                        ComparisonRow(stringResource(R.string.tax_marginal_relief), -newRegime.rebateMarginalRelief, -oldRegime.rebateMarginalRelief, currency, highlightGreen = true)
                    }
                    if (newRegime.surchargePayable > 0.0 || oldRegime.surchargePayable > 0.0) {
                        ComparisonRow(stringResource(R.string.tax_surcharge), newRegime.surchargePayable, oldRegime.surchargePayable, currency)
                    }
                    ComparisonRow("${stringResource(R.string.tax_cess)} (${newRegime.cessPercent.toInt()}%)", newRegime.cessAmount, oldRegime.cessAmount, currency)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow(stringResource(R.string.tax_payable), newRegime.totalTaxPayable, oldRegime.totalTaxPayable, currency, bold = true)
                    ComparisonRow(stringResource(R.string.tax_monthly_tds), newRegime.monthlyTdsEstimate, oldRegime.monthlyTdsEstimate, currency)
                }
            }

            // 5. "Why" Breakdown Card
            item(key = "tax_why_card") {
                FintechCard(modifier = Modifier.testTag("income_tax_why_card")) {
                    SectionAccentHeader(
                        title = stringResource(R.string.tax_why_title),
                        badge = result.recommendedRegimeId,
                        icon = Icons.Outlined.Lightbulb
                    )
                    Spacer(Modifier.height(10.dp))
                    result.whyBullets.forEach { bullet ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = SavingsGreen,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 6. Expandable Slab-by-Slab Breakdown
            item(key = "tax_slab_breakdown") {
                FintechCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleSlabBreakdown)
                            .testTag("tax_slab_breakdown_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionAccentHeader(
                            title = stringResource(R.string.tax_slab_breakdown_title),
                            badge = result.fyCode,
                            icon = Icons.Outlined.TableChart,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (uiState.slabBreakdownExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = uiState.slabBreakdownExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SlabTableBlock(title = newRegime.displayName, calc = newRegime, currency = currency)
                            HorizontalDivider()
                            SlabTableBlock(title = oldRegime.displayName, calc = oldRegime, currency = currency)
                        }
                    }
                }
            }
        }

        // 7. Tax Rules Metadata Footer + Check for updates button
        item(key = "tax_rules_footer") {
            TaxRulesMetadataFooter(
                catalog = uiState.catalog,
                activeFyCode = fyRules?.fyCode,
                lawReference = fyRules?.let { "${it.fyCode} (${it.ayCode}) • ${it.lawReference}" },
                isCheckingUpdates = uiState.isCheckingUpdates,
                syncFeedback = uiState.syncFeedback,
                onCheckForUpdatedTaxRules = onCheckUpdates
            )
        }

        item(key = "tax_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun RegimeHeroPill(
    title: String,
    taxPayable: String,
    monthlyTds: String,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isWinner) PrincipalEmerald.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (isWinner) PrincipalEmerald.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWinner) PrincipalEmerald else Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = taxPayable,
                style = MonoSmallStyle,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TDS $monthlyTds",
                style = MaterialTheme.typography.labelSmall,
                color = InterestAmber
            )
        }
    }
}

@Composable
private fun ComparisonHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("Component", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
        Text("New", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Old", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    newAmount: Double,
    oldAmount: Double,
    currency: CurrencyCode,
    bold: Boolean = false,
    highlightGreen: Boolean = false
) {
    val formatSigned: (Double) -> String = { v ->
        if (v < 0.0) "-${CurrencyFormatter.formatCompact(-v, currency)}"
        else CurrencyFormatter.formatCompact(v, currency)
    }
    val valueColor = if (highlightGreen) SavingsGreen else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = formatSigned(newAmount),
            style = MonoSmallStyle,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatSigned(oldAmount),
            style = MonoSmallStyle,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SlabTableBlock(
    title: String,
    calc: RegimeTaxCalculation,
    currency: CurrencyCode
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        calc.slabBreakdown.forEach { line ->
            KeyValueRow(
                label = "${line.rangeLabel} @ ${line.ratePercent.toInt()}%",
                value = CurrencyFormatter.format(line.taxAmount, currency)
            )
        }
    }
}

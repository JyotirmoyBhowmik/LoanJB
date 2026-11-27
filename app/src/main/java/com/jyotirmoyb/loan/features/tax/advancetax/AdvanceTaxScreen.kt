package com.jyotirmoyb.loan.features.tax.advancetax

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.jyotirmoyb.loan.domain.CurrencyFormatter
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

private val REMAINING_MONTH_PRESETS = listOf(1, 3, 6, 9, 12)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvanceTaxScreen(
    uiState: AdvanceTaxUiState,
    onSelectTab: (AdvanceTaxHelperTab) -> Unit,
    onSelectFy: (String) -> Unit,
    onUpdateEstimatedAnnualTax: (Double) -> Unit,
    onUpdateTdsDeducted: (Double) -> Unit,
    onTogglePresumptive: (Boolean) -> Unit,
    onToggleSeniorExempt: (Boolean) -> Unit,
    onUpdateFdInterest: (Double) -> Unit,
    onToggleFdSenior: (Boolean) -> Unit,
    onToggleFdHasPan: (Boolean) -> Unit,
    onUpdateSalaryAnnualTax: (Double) -> Unit,
    onUpdateSalaryTdsDeducted: (Double) -> Unit,
    onUpdateSalaryRemainingMonths: (Int) -> Unit,
    onOpenIncomeTax: () -> Unit,
    onOpenMfCapitalGains: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = uiState.currency

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("advance_tax_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sub-mode Tabs (Advance Tax | TDS Helper | Capital Gains)
        item(key = "adv_mode_tabs") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedTab == AdvanceTaxHelperTab.ADVANCE_TAX,
                    onClick = { onSelectTab(AdvanceTaxHelperTab.ADVANCE_TAX) },
                    label = { Text(stringResource(R.string.adv_tab_advance), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("adv_tab_advance")
                )
                FilterChip(
                    selected = uiState.selectedTab == AdvanceTaxHelperTab.TDS_HELPER,
                    onClick = { onSelectTab(AdvanceTaxHelperTab.TDS_HELPER) },
                    label = { Text(stringResource(R.string.adv_tab_tds), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("adv_tab_tds")
                )
                FilterChip(
                    selected = uiState.selectedTab == AdvanceTaxHelperTab.CAPITAL_GAINS,
                    onClick = { onSelectTab(AdvanceTaxHelperTab.CAPITAL_GAINS) },
                    label = { Text(stringResource(R.string.adv_tab_cg), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("adv_tab_cg")
                )
            }
        }

        // 2. Active Tab Content
        when (uiState.selectedTab) {
            AdvanceTaxHelperTab.ADVANCE_TAX -> {
                val advResult = uiState.advanceResult
                item(key = "adv_hero") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = SlateNavy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adv_tax_hero_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.adv_net_payable),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.72f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = CurrencyFormatter.format(advResult.netAdvanceTaxPayable, currency),
                                style = MonoLargeStyle,
                                color = if (advResult.isExempt) PrincipalEmerald else InterestAmber
                            )
                            Text(
                                text = if (advResult.isExempt) {
                                    advResult.exemptionReason
                                } else {
                                    val thresholdText = CurrencyFormatter.format(advResult.thresholdApplied, currency)
                                    "${uiState.advanceInput.fyCode} • ${advResult.installments.size} " +
                                        "scheduled instalment(s) (Sec 208 threshold $thresholdText)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                item(key = "adv_inputs") {
                    FintechCard {
                        SectionAccentHeader(
                            title = stringResource(R.string.adv_tab_advance),
                            badge = uiState.advanceInput.fyCode,
                            icon = Icons.Outlined.CalendarMonth
                        )
                        Spacer(Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.catalog.taxYears.forEach { fy ->
                                FilterChip(
                                    selected = uiState.advanceInput.fyCode == fy.fyCode,
                                    onClick = { onSelectFy(fy.fyCode) },
                                    label = { Text(fy.fyCode) },
                                    modifier = Modifier.testTag("adv_fy_${fy.fyCode.replace(" ", "_")}")
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        AmountField(
                            label = stringResource(R.string.adv_est_annual_tax),
                            amount = uiState.advanceInput.estimatedAnnualTax,
                            currency = currency,
                            onAmountChange = onUpdateEstimatedAnnualTax,
                            modifier = Modifier.testTag("adv_annual_tax_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        AmountField(
                            label = stringResource(R.string.adv_tds_deducted),
                            amount = uiState.advanceInput.tdsTcsDeducted,
                            currency = currency,
                            onAmountChange = onUpdateTdsDeducted,
                            modifier = Modifier.testTag("adv_tds_input")
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.adv_presumptive_toggle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.adv_presumptive_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.advanceInput.isPresumptive44AdAda,
                                onCheckedChange = onTogglePresumptive,
                                modifier = Modifier.testTag("adv_presumptive_switch")
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.adv_senior_exempt_toggle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.adv_senior_exempt_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.advanceInput.isSeniorWithoutBusiness,
                                onCheckedChange = onToggleSeniorExempt,
                                modifier = Modifier.testTag("adv_senior_exempt_switch")
                            )
                        }
                    }
                }

                if (!advResult.isExempt && advResult.installments.isNotEmpty()) {
                    item(key = "adv_schedule_table") {
                        FintechCard(modifier = Modifier.testTag("adv_schedule_card")) {
                            SectionAccentHeader(
                                title = stringResource(R.string.adv_schedule_title),
                                badge = "${advResult.installments.size} due dates",
                                icon = Icons.Outlined.EventAvailable
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.ui_txt_due_date),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.1f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_cum),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.7f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_instalment),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_cumulative),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))

                            advResult.installments.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        row.dueDateFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        stringResource(R.string.ui_txt_fmt_361cec, row.cumulativePercent.toInt()),
                                        style = MonoSmallStyle,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                    Text(
                                        CurrencyFormatter.formatCompact(row.installmentAmount, currency),
                                        style = MonoSmallStyle,
                                        color = InterestAmber,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        CurrencyFormatter.formatCompact(row.cumulativeAmount, currency),
                                        style = MonoSmallStyle,
                                        color = SavingsGreen,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                                if (idx < advResult.installments.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            AdvanceTaxHelperTab.TDS_HELPER -> {
                val fdTds = uiState.fdTdsResult
                val salTds = uiState.salaryTdsResult

                // FD TDS Card
                item(key = "tds_fd_card") {
                    FintechCard(modifier = Modifier.testTag("tds_fd_card")) {
                        SectionAccentHeader(
                            title = stringResource(R.string.tds_fd_section_title),
                            badge = if (fdTds?.isTdsApplicable == true) "${fdTds.appliedRatePercent.toInt()}% TDS" else "No TDS",
                            icon = Icons.Outlined.Savings
                        )
                        Spacer(Modifier.height(10.dp))

                        AmountField(
                            label = stringResource(R.string.tds_fd_annual_interest),
                            amount = uiState.fdAnnualInterest,
                            currency = currency,
                            onAmountChange = onUpdateFdInterest,
                            modifier = Modifier.testTag("tds_fd_interest_input")
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.fdIsSenior,
                                onClick = { onToggleFdSenior(!uiState.fdIsSenior) },
                                label = { Text(stringResource(R.string.ui_txt_senior_citizen_60)) },
                                modifier = Modifier.testTag("tds_fd_senior_chip")
                            )
                            FilterChip(
                                selected = uiState.fdHasPan,
                                onClick = { onToggleFdHasPan(!uiState.fdHasPan) },
                                label = { Text(if (uiState.fdHasPan) "PAN Furnished" else "No PAN (20%)") },
                                modifier = Modifier.testTag("tds_fd_pan_chip")
                            )
                        }

                        if (fdTds != null) {
                            Spacer(Modifier.height(10.dp))
                            KeyValueRow("Exemption Threshold", CurrencyFormatter.format(fdTds.threshold, currency))
                            KeyValueRow(
                                "Estimated Bank TDS",
                                CurrencyFormatter.format(fdTds.estimatedTds, currency),
                                valueColor = InterestAmber
                            )
                            KeyValueRow(
                                "Net Interest Credited",
                                CurrencyFormatter.format(fdTds.netInterestAfterTds, currency),
                                valueColor = SavingsGreen
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = fdTds.guidanceNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Salary TDS Card
                item(key = "tds_salary_card") {
                    FintechCard(modifier = Modifier.testTag("tds_salary_card")) {
                        SectionAccentHeader(
                            title = stringResource(R.string.tds_salary_section_title),
                            badge = "${salTds.remainingMonths} mos left",
                            icon = Icons.Outlined.AccountBalance
                        )
                        Spacer(Modifier.height(10.dp))

                        AmountField(
                            label = stringResource(R.string.adv_est_annual_tax),
                            amount = uiState.salaryAnnualTax,
                            currency = currency,
                            onAmountChange = onUpdateSalaryAnnualTax,
                            modifier = Modifier.testTag("tds_salary_annual_tax_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        AmountField(
                            label = stringResource(R.string.adv_tds_deducted),
                            amount = uiState.salaryTdsDeducted,
                            currency = currency,
                            onAmountChange = onUpdateSalaryTdsDeducted,
                            modifier = Modifier.testTag("tds_salary_paid_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.tds_remaining_months),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            REMAINING_MONTH_PRESETS.forEach { m ->
                                FilterChip(
                                    selected = uiState.salaryRemainingMonths == m,
                                    onClick = { onUpdateSalaryRemainingMonths(m) },
                                    label = { Text(stringResource(R.string.ui_txt_m_79c614, m)) },
                                    modifier = Modifier.testTag("tds_salary_months_$m")
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        KeyValueRow("Remaining Tax Liability", CurrencyFormatter.format(salTds.remainingTaxPayable, currency))
                        KeyValueRow(
                            "Est. Monthly Salary TDS",
                            CurrencyFormatter.format(salTds.estimatedMonthlyTds, currency),
                            valueColor = InfoBlue
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = salTds.guidanceNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenIncomeTax,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tds_open_income_tax_btn")
                        ) {
                            Text(stringResource(R.string.tds_open_income_tax))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            AdvanceTaxHelperTab.CAPITAL_GAINS -> {
                val cfg = uiState.mfTaxConfig
                item(key = "cg_helper_card") {
                    FintechCard(modifier = Modifier.testTag("cg_helper_card")) {
                        SectionAccentHeader(
                            title = stringResource(R.string.cg_helper_title),
                            badge = "taxslabs.json",
                            icon = Icons.Outlined.TrendingUp
                        )
                        Spacer(Modifier.height(10.dp))

                        val ltcgExemption = CurrencyFormatter.format(cfg.equityLtcgExemption, currency)
                        CapitalGainsRuleTile(
                            title = "Equity & Equity Mutual Funds",
                            detail = "STCG (<${cfg.equityLtcgHoldingMonths}m): ${cfg.equityStcgRatePercent}% (Sec 111A)\n" +
                                "LTCG (≥${cfg.equityLtcgHoldingMonths}m): ${cfg.equityLtcgRatePercent}% above " +
                                "$ltcgExemption exemption (Sec 112A)"
                        )
                        Spacer(Modifier.height(8.dp))
                        CapitalGainsRuleTile(
                            title = "Debt Mutual Funds (≤35% Equity)",
                            detail = "Sec 50AA — Always taxed at your applicable income tax slab rate regardless of holding period."
                        )
                        Spacer(Modifier.height(8.dp))
                        CapitalGainsRuleTile(
                            title = "Gold ETFs, FoFs, International & Hybrid Funds",
                            detail = "STCG (<${cfg.hybridLtcgHoldingMonths}m): Slab rate • " +
                                "LTCG (≥${cfg.hybridLtcgHoldingMonths}m): ${cfg.hybridLtcgRatePercent}% without indexation (Sec 112)."
                        )

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onOpenMfCapitalGains,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cg_open_mf_tax_btn")
                        ) {
                            Text(
                                text = stringResource(R.string.cg_open_mf_tax_btn),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 3. Tax Rules Metadata Footer
        item(key = "adv_footer") {
            TaxRulesMetadataFooter(
                catalog = uiState.catalog,
                activeFyCode = uiState.advanceInput.fyCode,
                lawReference = "Advance Tax (Sec 207/208/211) • TDS (Sec 192/194A/206AA) • Capital Gains (Sec 111A/112A/50AA)",
                isCheckingUpdates = uiState.isCheckingUpdates,
                syncFeedback = uiState.syncFeedback,
                onCheckForUpdatedTaxRules = onCheckUpdates
            )
        }

        item(key = "adv_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun CapitalGainsRuleTile(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

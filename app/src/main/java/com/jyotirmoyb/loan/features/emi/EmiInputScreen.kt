package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimary

@Composable
fun EmiInputScreen(
    uiState: EmiUiState,
    onSelectCategory: (LoanCategory) -> Unit,
    onSelectVariant: (LoanCategory, String, LoanVariant) -> Unit,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit,
    onSetTenureUnit: (Boolean) -> Unit,
    onStartDateToday: () -> Unit,
    onStartDateSelected: (Int, Int, Int) -> Unit,
    onEmiDayOfMonthChange: (Int) -> Unit,
    onProRataToggle: (Boolean) -> Unit,
    onResetRateDefault: () -> Unit,
    onApplyRepoScenario: (Double) -> Unit,
    onSelectEmiMode: (String) -> Unit,
    onToggleMoratorium: (Boolean) -> Unit,
    onCalculateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    val vehicleChecklistState = remember(input.categoryId) { mutableStateMapOf<Int, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("emi_input_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Loan Type Dropdown + Variant Quick Chips
        item(key = "loan_type") {
            LoanTypeDropdown(
                categories = uiState.catalog.categories,
                selectedCategory = uiState.selectedCategory,
                selectedVariantsByGroup = input.selectedVariantsByGroup,
                onSelectCategory = onSelectCategory,
                onSelectVariant = onSelectVariant
            )
        }

        // 2. Loan Amount Field (with Indian Lakh / Cr short label)
        item(key = "loan_amount") {
            AmountField(
                amount = input.principal,
                currency = currency,
                onAmountChange = { amt ->
                    onUpdateInput {
                        it.copy(
                            principal = amt,
                            billInvoiceAmount = amt
                        )
                    }
                }
            )
        }

        // 3. Interest Rate Field + Slider + Source Chip + Reset to Default
        item(key = "interest_rate") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RateField(
                    annualRate = input.annualRate,
                    onRateChange = { rate ->
                        onUpdateInput { it.copy(annualRate = rate) }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    uiState.rateSourceItem?.let { rateItem ->
                        ValueSourceBadgesRow(
                            item = rateItem,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ResetToDefaultAction(
                        visible = uiState.isRateCustomized,
                        onClick = onResetRateDefault,
                        testTag = "emi_rate_reset_btn"
                    )
                }
            }
        }

        // 4. Tenure Field + Year | Month Toggle
        item(key = "tenure") {
            TenureField(
                tenureMonths = input.tenureMonths,
                inYears = uiState.tenureInYears,
                onTenureMonthsChange = { months ->
                    onUpdateInput { it.copy(tenureMonths = months) }
                },
                onUnitChange = onSetTenureUnit
            )
        }

        // 5. Loan Start Date + EMI Day of Month + Pro-rata First-Month Interest
        item(key = "start_date") {
            StartDateRow(
                year = input.startYear,
                monthOneBased = input.startMonth,
                day = input.startDay,
                emiDayOfMonth = input.emiDayOfMonth,
                proRataFirstMonthEnabled = input.proRataFirstMonthEnabled,
                onTodayClick = onStartDateToday,
                onDateSelected = onStartDateSelected,
                onEmiDayOfMonthChange = onEmiDayOfMonthChange,
                onProRataToggle = onProRataToggle
            )
        }

        // 6. Gold Loan Auction-Risk Alert Banner (when LTV exceeds configurable threshold)
        result.insights.goldSummary?.takeIf { it.isAuctionRiskAlert }?.let { gold ->
            item(key = "gold_auction_alert") {
                FintechCard(
                    containerColor = DangerRed.copy(alpha = 0.1f),
                    borderColor = DangerRed,
                    modifier = Modifier.testTag("gold_auction_risk_banner")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = "Gold auction risk alert",
                            tint = DangerRed
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.emi_gold_auction_alert),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Peak LTV reaches ${String.format("%.1f", gold.peakLtvPercent)}% (threshold: ${String.format("%.0f", gold.auctionRiskThresholdLtv)}%). Reduce principal, choose Monthly EMI/Interest-Only, or pledge more gold to avoid auction risk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 7. Advance Settings Expandable Card
        item(key = "advance_settings") {
            AdvanceSettingsCard(
                input = input,
                result = result,
                category = uiState.selectedCategory,
                currency = currency,
                resolvedConfig = uiState.resolvedConfig,
                gstSourceItem = uiState.gstSourceItem,
                onUpdateInput = onUpdateInput,
                onSelectEmiMode = onSelectEmiMode,
                onToggleMoratorium = onToggleMoratorium,
                onApplyRepoScenario = onApplyRepoScenario
            )
        }

        // 8. Vehicle Loan Hypothecation Release Checklist (Form 35, RC update, Bank NOC)
        if (input.categoryId == "auto" && !uiState.selectedCategory?.checklistItems.isNullOrEmpty()) {
            item(key = "vehicle_hypothecation_checklist") {
                FintechCard(modifier = Modifier.testTag("vehicle_hypothecation_checklist")) {
                    SectionAccentHeader(
                        title = stringResource(R.string.emi_vehicle_checklist_title).uppercase(),
                        badge = "FORM 35 • RC",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.selectedCategory?.checklistItems?.forEachIndexed { idx, step ->
                        val checked = vehicleChecklistState[idx] == true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { vehicleChecklistState[idx] = it }
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 9. India Tax Benefit Estimate Panel (Home Loan Sec 24(b)+80C / Education Loan Sec 80E)
        result.insights.taxBenefitSummary?.let { tax ->
            if (tax.estimatedTotalTaxSaved > 0.0) {
                item(key = "india_tax_benefit_panel") {
                    FintechCard(
                        containerColor = SavingsGreen.copy(alpha = 0.07f),
                        borderColor = SavingsGreen.copy(alpha = 0.35f),
                        modifier = Modifier.testTag("emi_tax_benefit_panel")
                    ) {
                        SectionAccentHeader(
                            title = stringResource(R.string.emi_tax_benefit_title).uppercase(),
                            badge = "OLD REGIME",
                            accentColor = SavingsGreen
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Est. Year-1 Tax Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyFormatter.format(tax.estimatedAnnualTaxSaved, currency),
                                    style = MonoSmallStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Est. Lifetime Tax Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyFormatter.format(tax.estimatedTotalTaxSaved, currency),
                                    style = MonoSmallStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsGreen
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${tax.sectionTitle} at ${String.format("%.0f", input.taxBracketPercent)}% slab. ${tax.notes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 10. Validation Errors OR Live EMI Preview Strip + Primary Calculate CTA
        item(key = "calculate_cta") {
            if (!result.isValid) {
                FintechCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    borderColor = MaterialTheme.colorScheme.error
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = "Input validation warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Please adjust inputs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    result.validationErrors.forEach { err ->
                        Text(
                            text = "• $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Instant Live Preview Strip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SlateNavy,
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emi_live_preview_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ESTIMATED MONTHLY EMI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color(0xFFA7F3E8)
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(result.monthlyEmi, currency, false),
                                        style = MonoLargeStyle,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Interest: ${CurrencyFormatter.formatShortLabel(result.totalInterest, currency)} (${String.format("%.1f", result.interestToPrincipalPercent)}%)",
                                        style = MonoSmallStyle,
                                        color = InterestAmber
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Loan-Free: ${result.loanFreeDateLabel}",
                                        style = MonoSmallStyle,
                                        color = PrincipalEmerald
                                    )
                                }
                            }
                            if (result.gstOnFeeAmount > 0.0 || result.gstOnInterestAmount > 0.0 || result.brokenPeriodInterest > 0.0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(0xFFA7F3E8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    val extras = buildList {
                                        if (result.gstOnFeeAmount + result.gstOnInterestAmount > 0.0) {
                                            add("GST ${CurrencyFormatter.format(result.gstOnFeeAmount + result.gstOnInterestAmount, currency)}")
                                        }
                                        if (result.brokenPeriodInterest > 0.0) {
                                            add("Pro-rata ${CurrencyFormatter.format(result.brokenPeriodInterest, currency)}")
                                        }
                                    }.joinToString(" • ")
                                    Text(
                                        text = "Includes $extras",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onCalculateClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("emi_calculate_cta_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.emi_calculate_cta),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item(key = "emi_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

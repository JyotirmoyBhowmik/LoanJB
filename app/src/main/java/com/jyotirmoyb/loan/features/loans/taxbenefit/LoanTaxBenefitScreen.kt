package com.jyotirmoyb.loan.features.loans.taxbenefit

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanTaxBenefitInput
import com.jyotirmoyb.loan.domain.PropertyOccupancy
import com.jyotirmoyb.loan.domain.RegimeTaxBreakdown
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@Composable
fun LoanTaxBenefitScreen(
    uiState: LoanTaxBenefitUiState,
    onUpdateInput: ((LoanTaxBenefitInput) -> LoanTaxBenefitInput) -> Unit,
    onResetCap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = uiState.currency
    val inp = uiState.input
    val res = uiState.result

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("loan_tax_benefit_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Recommended Regime Banner
        if (res.isValid) {
            item(key = "tax_hero") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateNavy,
                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tax_recommendation_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RECOMMENDED: ${res.recommendedRegime.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF86EFAC),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = res.recommendationSummary,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Old vs New Regime Side-by-Side Comparison
            item(key = "regime_compare") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RegimeSummaryCard(
                        title = stringResource(R.string.tax_old_regime),
                        breakdown = res.oldRegime,
                        isRecommended = res.recommendedRegime == "Old Regime",
                        currency = cur,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tax_old_regime_card")
                    )
                    RegimeSummaryCard(
                        title = stringResource(R.string.tax_new_regime),
                        breakdown = res.newRegime,
                        isRecommended = res.recommendedRegime == "New Regime",
                        currency = cur,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tax_new_regime_card")
                    )
                }
            }
        }

        // Inputs Card
        item(key = "tax_inputs") {
            FintechCard {
                SectionAccentHeader(title = stringResource(R.string.tax_income_section))
                Spacer(Modifier.height(12.dp))

                AmountField(
                    amount = inp.grossAnnualIncome,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(grossAnnualIncome = v) } },
                    label = stringResource(R.string.tax_gross_income)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.tax_salaried_toggle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = inp.isSalaried,
                        onCheckedChange = { c -> onUpdateInput { it.copy(isSalaried = c) } },
                        modifier = Modifier.testTag("tax_salaried_switch")
                    )
                }

                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = inp.propertyOccupancy == PropertyOccupancy.SELF_OCCUPIED,
                        onClick = { onUpdateInput { it.copy(propertyOccupancy = PropertyOccupancy.SELF_OCCUPIED) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("tax_self_occupied_btn")
                    ) {
                        Text(stringResource(R.string.tax_occupancy_self))
                    }
                    SegmentedButton(
                        selected = inp.propertyOccupancy == PropertyOccupancy.LET_OUT,
                        onClick = { onUpdateInput { it.copy(propertyOccupancy = PropertyOccupancy.LET_OUT) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("tax_let_out_btn")
                    ) {
                        Text(stringResource(R.string.tax_occupancy_letout))
                    }
                }

                Spacer(Modifier.height(10.dp))
                AmountField(
                    amount = inp.homeLoanInterestPaid,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(homeLoanInterestPaid = v) } },
                    label = stringResource(R.string.tax_home_interest)
                )

                if (inp.propertyOccupancy == PropertyOccupancy.LET_OUT) {
                    Spacer(Modifier.height(10.dp))
                    AmountField(
                        amount = inp.annualRentalIncome,
                        currency = cur,
                        onAmountChange = { v -> onUpdateInput { it.copy(annualRentalIncome = v) } },
                        label = stringResource(R.string.tax_rental_income)
                    )
                }

                Spacer(Modifier.height(10.dp))
                AmountField(
                    amount = inp.homeLoanPrincipalPaid,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(homeLoanPrincipalPaid = v) } },
                    label = stringResource(R.string.tax_home_principal)
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    amount = inp.other80cInvestments,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(other80cInvestments = v) } },
                    label = stringResource(R.string.tax_other_80c)
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    amount = inp.educationLoanInterestPaid,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(educationLoanInterestPaid = v) } },
                    label = stringResource(R.string.tax_edu_interest)
                )
            }
        }

        // Data-Driven Caps & Source Chips Card
        item(key = "tax_caps") {
            FintechCard {
                SectionAccentHeader(title = stringResource(R.string.tax_caps_section), badge = "DATA")
                Spacer(Modifier.height(10.dp))
                listOfNotNull(
                    uiState.sec24bItem,
                    uiState.sec80cItem,
                    uiState.sec80eItem,
                    uiState.newStdDedItem,
                    uiState.oldStdDedItem
                ).forEachIndexed { idx, item ->
                    TaxCapSourceRow(item = item, currency = cur, onReset = { onResetCap(item.key) })
                    if (idx < 4) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }

        item(key = "disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RegimeSummaryCard(
    title: String,
    breakdown: RegimeTaxBreakdown,
    isRecommended: Boolean,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isRecommended) SavingsGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isRecommended) 2.dp else 1.dp,
            color = if (isRecommended) SavingsGreen else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isRecommended) SavingsGreen else MaterialTheme.colorScheme.onSurface
            )
            Column {
                Text(stringResource(R.string.tax_saved_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = CurrencyFormatter.format(breakdown.totalTaxSavedByLoans, currency),
                    style = MonoMediumStyle,
                    color = if (breakdown.totalTaxSavedByLoans > 0.0) SavingsGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Column {
                Text(stringResource(R.string.tax_before_loans), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(breakdown.taxWithoutLoanDeductions, currency), style = MonoSmallStyle)
            }
            Column {
                Text(stringResource(R.string.tax_after_loans), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.format(breakdown.taxWithLoanDeductions, currency), style = MonoSmallStyle, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Text(
                text = "24(b): ${CurrencyFormatter.formatCompact(breakdown.sec24bDeductionUsed, currency)}\n" +
                    "80C Loan: ${CurrencyFormatter.formatCompact(breakdown.sec80cLoanDeductionUsed, currency)}\n" +
                    "80E: ${CurrencyFormatter.formatCompact(breakdown.sec80eDeductionUsed, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaxCapSourceRow(
    item: ResolvedConfigItem,
    currency: CurrencyCode,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            val displayVal = when {
                item.unit.equals("INR", ignoreCase = true) && item.numericValue != null ->
                    CurrencyFormatter.format(item.numericValue, currency)
                else -> "${item.valueText} ${item.unit}"
            }
            Text(text = displayVal, style = MonoSmallStyle, fontWeight = FontWeight.Bold, color = InterestAmber)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ValueSourceBadgesRow(item = item, modifier = Modifier.weight(1f))
            ResetToDefaultAction(
                visible = item.isOverridden,
                onClick = onReset,
                testTag = "tax_reset_${item.key}"
            )
        }
    }
}

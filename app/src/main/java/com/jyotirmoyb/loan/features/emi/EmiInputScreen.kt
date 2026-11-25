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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.LoanVariant
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
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
    onSelectEmiMode: (String) -> Unit,
    onToggleMoratorium: (Boolean) -> Unit,
    onCalculateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency

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

        // 2. Loan Amount Field (with "28.0 Lakh" badge)
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

        // 3. Interest Rate Field + Slider
        item(key = "interest_rate") {
            RateField(
                annualRate = input.annualRate,
                onRateChange = { rate ->
                    onUpdateInput { it.copy(annualRate = rate) }
                }
            )
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

        // 5. Loan Start Date Row (Today / Change)
        item(key = "start_date") {
            StartDateRow(
                year = input.startYear,
                monthOneBased = input.startMonth,
                day = input.startDay,
                onTodayClick = onStartDateToday,
                onDateSelected = onStartDateSelected
            )
        }

        // 6. Advance Settings Expandable Card
        item(key = "advance_settings") {
            AdvanceSettingsCard(
                input = input,
                category = uiState.selectedCategory,
                currency = currency,
                onUpdateInput = onUpdateInput,
                onSelectEmiMode = onSelectEmiMode,
                onToggleMoratorium = onToggleMoratorium
            )
        }

        // 7. Validation Errors OR Live EMI Preview Strip + Primary Calculate CTA
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    text = "Interest: ${CurrencyFormatter.formatCompact(result.totalInterest, currency)}",
                                    style = MonoSmallStyle,
                                    color = InterestAmber
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Total: ${CurrencyFormatter.formatCompact(result.totalPayment, currency)} (${result.actualTenureMonths}m)",
                                    style = MonoSmallStyle,
                                    color = PrincipalEmerald
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onCalculateClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
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

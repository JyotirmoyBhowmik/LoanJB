package com.jyotirmoyb.loan.features.bankstaff

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.emi.TenureField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimary

@Composable
fun BankStaffLoanScreen(
    uiState: BankStaffUiState,
    onBack: () -> Unit,
    onSelectPreset: (StaffLoanPreset) -> Unit,
    onUpdateAmount: (Double) -> Unit,
    onUpdateStaffRate: (Double) -> Unit,
    onUpdateMarketRate: (Double) -> Unit,
    onUpdateTenureMonths: (Int) -> Unit,
    onViewFullSchedule: () -> Unit,
    onSaveStaffLoan: () -> Unit,
    onCompareWithMarket: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tenureInYears by remember { mutableStateOf(true) }
    val activePreset = uiState.presets.firstOrNull { it.id == uiState.selectedPresetId }
    val cur = uiState.currency

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("bank_staff_screen")
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("bank_staff_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bank_staff_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bank_staff_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preset selector chips
            item(key = "presets") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.presets, key = { it.id }) { preset ->
                        FilterChip(
                            selected = preset.id == uiState.selectedPresetId,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.title) },
                            modifier = Modifier.testTag("staff_preset_${preset.id}")
                        )
                    }
                }
                activePreset?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editable Inputs Card
            item(key = "inputs") {
                FintechCard {
                    SectionAccentHeader("Staff Loan Parameters", badge = "EDITABLE")
                    Spacer(Modifier.height(12.dp))
                    AmountField(
                        amount = uiState.amount,
                        currency = cur,
                        onAmountChange = onUpdateAmount
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericInputField(
                            label = "Staff Rate (%)",
                            valueText = uiState.staffRate.toString(),
                            onValueChange = { txt -> txt.toDoubleOrNull()?.let(onUpdateStaffRate) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("staff_rate_input")
                        )
                        NumericInputField(
                            label = "Market Rate (%)",
                            valueText = uiState.marketRate.toString(),
                            onValueChange = { txt -> txt.toDoubleOrNull()?.let(onUpdateMarketRate) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("staff_market_rate_input")
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TenureField(
                        tenureMonths = uiState.tenureMonths,
                        inYears = tenureInYears,
                        onTenureMonthsChange = onUpdateTenureMonths,
                        onUnitChange = { tenureInYears = it }
                    )
                }
            }

            // Staff vs Market Savings Hero Card
            item(key = "result_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateNavy,
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_staff_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "STAFF MONTHLY EMI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA7F3E8)
                                )
                                Text(
                                    text = CurrencyFormatter.format(uiState.staffResult.monthlyEmi, cur, true),
                                    style = MonoLargeStyle,
                                    color = Color.White
                                )
                            }
                            Surface(
                                color = SavingsGreen.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Save ${CurrencyFormatter.format(uiState.monthlyEmiSaved, cur)}/mo",
                                    style = MonoSmallStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF86EFAC),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("STAFF INTEREST", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    CurrencyFormatter.format(uiState.staffResult.totalInterest, cur),
                                    style = MonoMediumStyle,
                                    color = PrincipalEmerald
                                )
                            }
                            Column {
                                Text("MARKET INTEREST", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    CurrencyFormatter.format(uiState.marketResult.totalInterest, cur),
                                    style = MonoMediumStyle,
                                    color = InterestAmber
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOTAL SAVED", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    CurrencyFormatter.formatCompact(uiState.totalInterestSaved, cur),
                                    style = MonoMediumStyle,
                                    color = Color(0xFF86EFAC)
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onViewFullSchedule,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("staff_view_schedule_btn")
                    ) {
                        Text("Open Full Amortization Schedule", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSaveStaffLoan,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("staff_save_btn")
                        ) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Loan")
                        }
                        OutlinedButton(
                            onClick = onCompareWithMarket,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("staff_compare_btn")
                        ) {
                            Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compare vs Market")
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
}

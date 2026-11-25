package com.jyotirmoyb.loan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.FlexiEvent
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.LoanVariant
import com.jyotirmoyb.loan.engine.PrepaymentMode
import com.jyotirmoyb.loan.engine.PrepaymentRule
import com.jyotirmoyb.loan.engine.RateChangeEvent
import com.jyotirmoyb.loan.engine.TrancheEntry
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.categoryIcon
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.util.ScheduleExporter
import java.util.Calendar
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorScreen(
    catalog: LoanCatalog,
    input: CalculationInput,
    result: CalculationResult,
    currency: CurrencyCode,
    variantSummary: String,
    onSelectCategory: (LoanCategory) -> Unit,
    onSelectVariant: (LoanCategory, String, LoanVariant) -> Unit,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit,
    onSaveLoan: (String, String, Boolean, Int) -> Unit,
    onAddToCompare: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val category = remember(catalog, input.categoryId) {
        catalog.categories.firstOrNull { it.id == input.categoryId } ?: catalog.categories.firstOrNull()
    }
    val flags = remember(input.ruleFlags) { input.ruleFlags.toSet() }

    var tenureInYears by remember(input.categoryId) {
        mutableStateOf(input.tenureMonths >= 24 && input.tenureMonths % 12 == 0)
    }
    var advanceExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }

    val activeAdvanceCount = remember(input) {
        var c = 0
        if (input.processingFeePercent > 0.0) c++
        if (input.insurancePremium > 0.0) c++
        if (input.prepaymentEnabled && input.prepayments.any { it.amount > 0.0 }) c++
        if (input.balanceTransfer.enabled) c++
        if (input.topUp.enabled) c++
        c
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Category Selector Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                catalog.categories.forEach { cat ->
                    val selected = cat.id == category?.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.shortName, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = {
                            Icon(categoryIcon(cat.icon), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("calc_category_chip_${cat.id}")
                    )
                }
            }
        }

        // 2. Variant Group Selectors for the active category
        if (category != null) {
            item {
                FintechCard {
                    SectionAccentHeader(
                        title = "${category.name} Variants",
                        badge = "${category.variantGroups.size} groups"
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        category.variantGroups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    group.variants.forEach { variant ->
                                        val isSelected = input.selectedVariantsByGroup[group.id] == variant.id ||
                                            (input.selectedVariantsByGroup[group.id] == null && flags.contains(variant.ruleFlag))
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSelectVariant(category, group.id, variant) },
                                            label = { Text(variant.name, style = MaterialTheme.typography.labelMedium) },
                                            modifier = Modifier.testTag("calc_variant_chip_${variant.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Core Loan Inputs Card (adapted to selected category & variants)
        item {
            FintechCard {
                SectionAccentHeader("Loan Parameters", badge = currency.code)
                Spacer(Modifier.height(12.dp))

                if (flags.contains("BIZ_BILL_DISCOUNT")) {
                    BillDiscountInputs(input = input, currency = currency, onUpdateInput = onUpdateInput)
                } else {
                    // Principal Amount
                    NumericInputField(
                        label = if (flags.contains("FAC_FLEXI_LOC") || flags.contains("BIZ_CASH_CREDIT")) "Credit Limit (${currency.symbol})" else "Loan Amount (${currency.symbol})",
                        valueText = formatEditableNumber(input.principal),
                        onValueChange = { txt ->
                            val v = txt.toDoubleOrNull() ?: 0.0
                            onUpdateInput { it.copy(principal = v) }
                        },
                        prefix = "${currency.symbol} ",
                        trailingBadge = CurrencyFormatter.formatCompact(input.principal, currency),
                        modifier = Modifier.testTag("calc_principal_input")
                    )

                    // Asset / Property / Vehicle / FD / Share Value (for secured loans)
                    if (category?.secured == true || flags.contains("ASSET_MARGIN_SHARE") || flags.contains("ASSET_FD")) {
                        Spacer(Modifier.height(10.dp))
                        val assetLabel = when {
                            flags.contains("ASSET_FD") -> "Fixed Deposit Value (${currency.symbol})"
                            flags.contains("ASSET_MARGIN_SHARE") -> "Pledged Share Portfolio Value (${currency.symbol})"
                            category?.id == "auto" -> "Vehicle On-Road Price (${currency.symbol})"
                            category?.id == "home" -> "Property Value (${currency.symbol})"
                            else -> "Collateral / Asset Value (${currency.symbol})"
                        }
                        NumericInputField(
                            label = assetLabel,
                            valueText = formatEditableNumber(input.assetValue),
                            onValueChange = { txt ->
                                val v = txt.toDoubleOrNull() ?: 0.0
                                onUpdateInput { it.copy(assetValue = v, shareValue = v) }
                            },
                            prefix = "${currency.symbol} ",
                            trailingBadge = "LTV ${String.format("%.0f", result.ltvPercent)}%",
                            supportingText = "Max LTV cap: ${String.format("%.0f", result.ltvCapPercent)}% • Down payment: ${CurrencyFormatter.format(result.downPayment, currency)}",
                            isError = result.isLtvExceeded,
                            modifier = Modifier.testTag("calc_asset_value_input")
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Interest Rate (Slider + Text)
                    NumericInputField(
                        label = if (flags.contains("BASIS_FLAT_COMPARE")) "Quoted Flat Rate (% p.a.)" else "Annual Interest Rate (%)",
                        valueText = formatEditableDecimal(input.annualRate),
                        onValueChange = { txt ->
                            val v = txt.toDoubleOrNull() ?: 0.0
                            onUpdateInput { it.copy(annualRate = v) }
                        },
                        trailingBadge = "%",
                        isError = input.annualRate < 0.0 || input.annualRate > 40.0,
                        modifier = Modifier.testTag("calc_rate_input")
                    )
                    Slider(
                        value = input.annualRate.toFloat().coerceIn(0f, 30f),
                        onValueChange = { r ->
                            val snapped = (r * 20f).roundToInt() / 20.0
                            onUpdateInput { it.copy(annualRate = snapped) }
                        },
                        valueRange = 1f..28f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tenure (with Year | Month toggle, except LC which uses days)
                    if (flags.contains("BIZ_LC_TR")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Repayment Window (Days)",
                                valueText = input.lcDays.toString(),
                                onValueChange = { t -> onUpdateInput { it.copy(lcDays = t.toIntOrNull() ?: 90) } },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "LC Commission (%)",
                                valueText = formatEditableDecimal(input.lcCommissionPercent),
                                onValueChange = { t -> onUpdateInput { it.copy(lcCommissionPercent = t.toDoubleOrNull() ?: 0.0) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        val displayTenure = if (tenureInYears) (input.tenureMonths / 12.0).roundToInt().toString() else input.tenureMonths.toString()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NumericInputField(
                                label = "Tenure",
                                valueText = displayTenure,
                                onValueChange = { txt ->
                                    val n = txt.toIntOrNull() ?: 0
                                    val months = if (tenureInYears) n * 12 else n
                                    onUpdateInput { it.copy(tenureMonths = months) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calc_tenure_input")
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (tenureInYears) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (tenureInYears) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { tenureInYears = true }
                                    .testTag("calc_tenure_years_btn")
                            ) {
                                Text(
                                    "Year",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (tenureInYears) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (!tenureInYears) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (!tenureInYears) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { tenureInYears = false }
                                    .testTag("calc_tenure_months_btn")
                            ) {
                                Text(
                                    "Month",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (!tenureInYears) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Loan Start Date Row (matching screenshot 1!)
                    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val dateStr = "${input.startDay} ${monthNames[(input.startMonth - 1).coerceIn(0, 11)]} ${input.startYear}"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Loan Start Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(dateStr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = {
                                    val cal = Calendar.getInstance()
                                    onUpdateInput {
                                        it.copy(
                                            startYear = cal.get(Calendar.YEAR),
                                            startMonth = cal.get(Calendar.MONTH) + 1,
                                            startDay = cal.get(Calendar.DAY_OF_MONTH)
                                        )
                                    }
                                }) { Text("Today") }
                                OutlinedButton(
                                    onClick = { showDateDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Change") }
                            }
                        }
                    }
                }
            }
        }

        // 4. Variant-Specific Dynamic Inputs (only shown when relevant variant flag is active)
        val hasVariantInputs = flags.any {
            it in setOf(
                "RATE_FLOATING", "RATE_HYBRID", "EMI_STEP_UP", "EMI_STEP_DOWN",
                "EMI_OFFSET", "DISB_TRANCHE_PRE_EMI", "TERM_MORATORIUM", "MOR_SIMPLE",
                "MOR_COMPOUND", "EMI_BALLOON", "FAC_FLEXI_LOC", "FAC_OVERDRAFT",
                "BIZ_CASH_CREDIT", "ASSET_FD", "ASSET_MARGIN_SHARE"
            )
        }
        if (hasVariantInputs) {
            item {
                VariantSpecificInputsCard(
                    flags = flags,
                    input = input,
                    currency = currency,
                    onUpdateInput = onUpdateInput
                )
            }
        }

        // 5. Expandable Advance Settings (Fees, Insurance, Prepayment, Balance Transfer, Top-Up)
        item {
            FintechCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advanceExpanded = !advanceExpanded }
                        .testTag("calc_advance_settings_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Advance Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (activeAdvanceCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp)) {
                                        Text(
                                            "$activeAdvanceCount Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "Fees, Insurance, Prepayment, Balance Transfer & Top-Up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (advanceExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Toggle advance settings"
                    )
                }

                AnimatedVisibility(visible = advanceExpanded) {
                    AdvanceSettingsContent(
                        input = input,
                        flags = flags,
                        currency = currency,
                        onUpdateInput = onUpdateInput
                    )
                }
            }
        }

        // 6. Live Results, Insights, Charts & Styled Schedule
        item {
            CalculatorResultSection(
                input = input,
                category = category,
                result = result,
                currency = currency,
                onSaveLoanClick = { showSaveDialog = true },
                onAddToCompareClick = { onAddToCompare(null) },
                onExportCsvClick = {
                    val res = ScheduleExporter.exportCsvAndShare(context, category?.name ?: "Loan", result, currency)
                    if (res.isFailure) onShowMessage("Export failed: ${res.exceptionOrNull()?.message}")
                },
                onExportPdfClick = {
                    val res = ScheduleExporter.exportPdfAndShare(context, category?.name ?: "Loan", variantSummary, result, currency)
                    if (res.isFailure) onShowMessage("Export failed: ${res.exceptionOrNull()?.message}")
                }
            )
        }

        item {
            DisclaimerBanner()
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSaveDialog) {
        SaveLoanDialog(
            defaultNickname = "${category?.name ?: "Loan"} Plan",
            onDismiss = { showSaveDialog = false },
            onSave = { nick, lender, reminder, days ->
                showSaveDialog = false
                onSaveLoan(nick, lender, reminder, days)
            }
        )
    }

    if (showDateDialog) {
        StartDatePickerDialog(
            year = input.startYear,
            month = input.startMonth,
            day = input.startDay,
            onDismiss = { showDateDialog = false },
            onConfirm = { y, m, d ->
                showDateDialog = false
                onUpdateInput { it.copy(startYear = y, startMonth = m, startDay = d) }
            }
        )
    }
}

@Composable
private fun BillDiscountInputs(
    input: CalculationInput,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumericInputField(
            label = "Invoice Value (${currency.symbol})",
            valueText = formatEditableNumber(input.billInvoiceAmount),
            onValueChange = { t -> onUpdateInput { it.copy(billInvoiceAmount = t.toDoubleOrNull() ?: 0.0) } },
            trailingBadge = CurrencyFormatter.formatCompact(input.billInvoiceAmount, currency)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericInputField(
                label = "Advance % (70-85)",
                valueText = formatEditableDecimal(input.billAdvancePercent),
                onValueChange = { t -> onUpdateInput { it.copy(billAdvancePercent = t.toDoubleOrNull() ?: 80.0) } },
                modifier = Modifier.weight(1f)
            )
            NumericInputField(
                label = "Discount Rate %",
                valueText = formatEditableDecimal(input.billDiscountRatePercent),
                onValueChange = { t -> onUpdateInput { it.copy(billDiscountRatePercent = t.toDoubleOrNull() ?: 12.0) } },
                modifier = Modifier.weight(1f)
            )
        }
        NumericInputField(
            label = "Invoice Maturity Window (Days: 30-90)",
            valueText = input.billDays.toString(),
            onValueChange = { t -> onUpdateInput { it.copy(billDays = t.toIntOrNull() ?: 60) } }
        )
    }
}

@Composable
private fun VariantSpecificInputsCard(
    flags: Set<String>,
    input: CalculationInput,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    FintechCard {
        SectionAccentHeader("Variant Rules & Parameters", badge = "ADAPTIVE")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Hybrid fixed period + floating rate
            if (flags.contains("RATE_HYBRID")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInputField(
                        label = "Fixed Period (Years)",
                        valueText = (input.hybridFixedMonths / 12).toString(),
                        onValueChange = { t -> onUpdateInput { it.copy(hybridFixedMonths = (t.toIntOrNull() ?: 3) * 12) } },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = "Post-Fixed Rate (%)",
                        valueText = formatEditableDecimal(input.hybridFloatingRate),
                        onValueChange = { t -> onUpdateInput { it.copy(hybridFloatingRate = t.toDoubleOrNull() ?: 10.0) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Floating rate change timeline
            if (flags.contains("RATE_FLOATING") || flags.contains("RATE_HYBRID")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rate Change Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val nextMonth = (input.rateChanges.maxOfOrNull { it.afterMonth } ?: 0) + 24
                            val nextRate = (input.annualRate - 0.5).coerceAtLeast(1.0)
                            onUpdateInput { it.copy(rateChanges = it.rateChanges + RateChangeEvent(nextMonth, nextRate)) }
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Change")
                        }
                    }
                    if (input.rateChanges.isEmpty()) {
                        Text(
                            "No future rate changes scheduled. Tap \"Add Change\" (e.g., -0.5% after Year 3).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        input.rateChanges.forEachIndexed { idx, ev ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NumericInputField(
                                    label = "After Month",
                                    valueText = ev.afterMonth.toString(),
                                    onValueChange = { t ->
                                        val m = t.toIntOrNull() ?: 12
                                        onUpdateInput { st ->
                                            st.copy(rateChanges = st.rateChanges.mapIndexed { i, e -> if (i == idx) e.copy(afterMonth = m) else e })
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                NumericInputField(
                                    label = "New Rate %",
                                    valueText = formatEditableDecimal(ev.newAnnualRate),
                                    onValueChange = { t ->
                                        val r = t.toDoubleOrNull() ?: ev.newAnnualRate
                                        onUpdateInput { st ->
                                            st.copy(rateChanges = st.rateChanges.mapIndexed { i, e -> if (i == idx) e.copy(newAnnualRate = r) else e })
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    onUpdateInput { st -> st.copy(rateChanges = st.rateChanges.filterIndexed { i, _ -> i != idx }) }
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove rate change")
                                }
                            }
                        }
                    }
                }
            }

            // Step-Up / Step-Down
            if (flags.contains("EMI_STEP_UP") || flags.contains("EMI_STEP_DOWN")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (input.isStepDown) "Mode: Step-Down EMI" else "Mode: Step-Up EMI", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = input.isStepDown,
                        onCheckedChange = { down -> onUpdateInput { it.copy(isStepDown = down) } }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInputField(
                        label = "Step Change (%)",
                        valueText = formatEditableDecimal(input.stepPercent),
                        onValueChange = { t -> onUpdateInput { it.copy(stepPercent = t.toDoubleOrNull() ?: 5.0) } },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = "Every N Months",
                        valueText = input.stepIntervalMonths.toString(),
                        onValueChange = { t -> onUpdateInput { it.copy(stepIntervalMonths = t.toIntOrNull() ?: 12) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Offset / Overdraft parked cash
            if (flags.contains("EMI_OFFSET")) {
                NumericInputField(
                    label = "Parked Offset Balance (${currency.symbol})",
                    valueText = formatEditableNumber(input.offsetBalance),
                    onValueChange = { t -> onUpdateInput { it.copy(offsetBalance = t.toDoubleOrNull() ?: 0.0) } },
                    trailingBadge = CurrencyFormatter.formatCompact(input.offsetBalance, currency),
                    supportingText = "Interest is calculated only on (Principal − Offset Balance)"
                )
            }

            // Balloon Payment
            if (flags.contains("EMI_BALLOON")) {
                NumericInputField(
                    label = "Final Balloon Lump Sum (% of Loan)",
                    valueText = formatEditableDecimal(input.balloonPercent),
                    onValueChange = { t -> onUpdateInput { it.copy(balloonPercent = t.toDoubleOrNull() ?: 25.0) } },
                    supportingText = "Deferred amount due at end: ${CurrencyFormatter.format(input.principal * input.balloonPercent / 100.0, currency)}"
                )
            }

            // Moratorium
            if (flags.contains("TERM_MORATORIUM") || flags.contains("MOR_SIMPLE") || flags.contains("MOR_COMPOUND")) {
                NumericInputField(
                    label = "Moratorium / Grace Period (Months)",
                    valueText = input.moratoriumMonths.toString(),
                    onValueChange = { t -> onUpdateInput { it.copy(moratoriumMonths = t.toIntOrNull() ?: 12) } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capitalize Unpaid Interest (Compound)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (input.moratoriumCompound) "Zero payments during moratorium; interest added to principal"
                            else "Simple interest paid monthly during moratorium",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = input.moratoriumCompound,
                        onCheckedChange = { c -> onUpdateInput { it.copy(moratoriumCompound = c) } }
                    )
                }
            }

            // Tranches + Pre-EMI
            if (flags.contains("DISB_TRANCHE_PRE_EMI")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Disbursement Tranches (Pre-EMI)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val nextM = (input.tranches.maxOfOrNull { it.monthNumber } ?: 1) + 6
                            onUpdateInput { it.copy(tranches = it.tranches + TrancheEntry(nextM, it.principal * 0.25)) }
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Tranche")
                        }
                    }
                    input.tranches.forEachIndexed { idx, tr ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Month #",
                                valueText = tr.monthNumber.toString(),
                                onValueChange = { t ->
                                    val m = t.toIntOrNull() ?: 1
                                    onUpdateInput { st -> st.copy(tranches = st.tranches.mapIndexed { i, x -> if (i == idx) x.copy(monthNumber = m) else x }) }
                                },
                                modifier = Modifier.weight(0.75f)
                            )
                            NumericInputField(
                                label = "Amount (${currency.symbol})",
                                valueText = formatEditableNumber(tr.amount),
                                onValueChange = { t ->
                                    val a = t.toDoubleOrNull() ?: 0.0
                                    onUpdateInput { st -> st.copy(tranches = st.tranches.mapIndexed { i, x -> if (i == idx) x.copy(amount = a) else x }) }
                                },
                                modifier = Modifier.weight(1.3f)
                            )
                            if (input.tranches.size > 1) {
                                IconButton(onClick = {
                                    onUpdateInput { st -> st.copy(tranches = st.tranches.filterIndexed { i, _ -> i != idx }) }
                                }) { Icon(Icons.Outlined.Close, contentDescription = "Remove tranche") }
                            }
                        }
                    }
                }
            }

            // Flexi / Cash Credit / Overdraft draw/repay simulator
            if (flags.contains("FAC_FLEXI_LOC") || flags.contains("FAC_OVERDRAFT") || flags.contains("BIZ_CASH_CREDIT")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Draw / Repay Simulator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val nextM = (input.flexiEvents.maxOfOrNull { it.monthNumber } ?: 1) + 3
                            onUpdateInput { it.copy(flexiEvents = it.flexiEvents + FlexiEvent(nextM, drawAmount = 0.0, repayAmount = it.principal * 0.2)) }
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Event")
                        }
                    }
                    input.flexiEvents.forEachIndexed { idx, ev ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NumericInputField(
                                label = "M#",
                                valueText = ev.monthNumber.toString(),
                                onValueChange = { t ->
                                    val m = t.toIntOrNull() ?: 1
                                    onUpdateInput { st -> st.copy(flexiEvents = st.flexiEvents.mapIndexed { i, x -> if (i == idx) x.copy(monthNumber = m) else x }) }
                                },
                                modifier = Modifier.weight(0.6f)
                            )
                            NumericInputField(
                                label = "Draw",
                                valueText = formatEditableNumber(ev.drawAmount),
                                onValueChange = { t ->
                                    val a = t.toDoubleOrNull() ?: 0.0
                                    onUpdateInput { st -> st.copy(flexiEvents = st.flexiEvents.mapIndexed { i, x -> if (i == idx) x.copy(drawAmount = a) else x }) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Repay",
                                valueText = formatEditableNumber(ev.repayAmount),
                                onValueChange = { t ->
                                    val a = t.toDoubleOrNull() ?: 0.0
                                    onUpdateInput { st -> st.copy(flexiEvents = st.flexiEvents.mapIndexed { i, x -> if (i == idx) x.copy(repayAmount = a) else x }) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (input.flexiEvents.size > 1) {
                                IconButton(onClick = {
                                    onUpdateInput { st -> st.copy(flexiEvents = st.flexiEvents.filterIndexed { i, _ -> i != idx }) }
                                }) { Icon(Icons.Outlined.Close, contentDescription = "Remove event") }
                            }
                        }
                    }
                }
            }

            // Loan Against FD
            if (flags.contains("ASSET_FD")) {
                NumericInputField(
                    label = "Existing FD Interest Rate (% p.a.)",
                    valueText = formatEditableDecimal(input.fdRatePercent),
                    onValueChange = { t ->
                        val fdR = t.toDoubleOrNull() ?: 7.0
                        onUpdateInput { it.copy(fdRatePercent = fdR, annualRate = fdR + it.fdSpreadPercent) }
                    },
                    supportingText = "Loan rate automatically tracks FD Rate + ${String.format("%.1f", input.fdSpreadPercent)}% spread"
                )
            }

            // Margin / Share Loan
            if (flags.contains("ASSET_MARGIN_SHARE")) {
                NumericInputField(
                    label = "Minimum Maintenance Margin (%)",
                    valueText = formatEditableDecimal(input.minMarginPercent),
                    onValueChange = { t -> onUpdateInput { it.copy(minMarginPercent = t.toDoubleOrNull() ?: 30.0) } },
                    supportingText = "Margin call triggers when equity cushion drops below this %"
                )
            }
        }
    }
}

@Composable
private fun AdvanceSettingsContent(
    input: CalculationInput,
    flags: Set<String>,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fees & Insurance
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericInputField(
                label = "Processing Fee (%)",
                valueText = formatEditableDecimal(input.processingFeePercent),
                onValueChange = { t -> onUpdateInput { it.copy(processingFeePercent = t.toDoubleOrNull() ?: 0.0) } },
                modifier = Modifier.weight(1f)
            )
            NumericInputField(
                label = "Insurance (${currency.symbol})",
                valueText = formatEditableNumber(input.insurancePremium),
                onValueChange = { t -> onUpdateInput { it.copy(insurancePremium = t.toDoubleOrNull() ?: 0.0) } },
                modifier = Modifier.weight(1f)
            )
        }

        // Prepayment Simulator
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = input.prepaymentEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateInput { st ->
                                val rules = if (enabled && st.prepayments.isEmpty()) {
                                    listOf(PrepaymentRule(startMonth = 12, amount = st.principal * 0.1))
                                } else st.prepayments
                                st.copy(prepaymentEnabled = enabled, prepayments = rules)
                            }
                        },
                        modifier = Modifier.testTag("calc_prepayment_checkbox")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prepayment / Part-Payment Simulator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (flags.contains("RATE_FLOATING")) "Floating rate: 0% default fee (editable)" else "Fixed rate: ${String.format("%.1f", input.prepaymentFeePercent)}% default fee",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (input.prepaymentEnabled) {
                    val rule = input.prepayments.firstOrNull() ?: PrepaymentRule(12, input.principal * 0.1)
                    fun updateRule(transform: (PrepaymentRule) -> PrepaymentRule) {
                        val updated = transform(rule)
                        onUpdateInput { it.copy(prepayments = listOf(updated)) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Amount (${currency.symbol})",
                            valueText = formatEditableNumber(rule.amount),
                            onValueChange = { t -> updateRule { it.copy(amount = t.toDoubleOrNull() ?: 0.0) } },
                            modifier = Modifier.weight(1.2f)
                        )
                        NumericInputField(
                            label = "Start Month",
                            valueText = rule.startMonth.toString(),
                            onValueChange = { t -> updateRule { it.copy(startMonth = t.toIntOrNull() ?: 12) } },
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !rule.isRecurring,
                            onClick = { updateRule { it.copy(isRecurring = false) } },
                            label = { Text("One-Time") }
                        )
                        FilterChip(
                            selected = rule.isRecurring,
                            onClick = { updateRule { it.copy(isRecurring = true, intervalMonths = 12) } },
                            label = { Text("Yearly Recurring") }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = rule.mode == PrepaymentMode.REDUCE_TENURE.name,
                            onClick = { updateRule { it.copy(mode = PrepaymentMode.REDUCE_TENURE.name) } },
                            label = { Text("Reduce Tenure") }
                        )
                        FilterChip(
                            selected = rule.mode == PrepaymentMode.REDUCE_EMI.name,
                            onClick = { updateRule { it.copy(mode = PrepaymentMode.REDUCE_EMI.name) } },
                            label = { Text("Reduce EMI") }
                        )
                    }

                    NumericInputField(
                        label = "Prepayment Fee (%)",
                        valueText = formatEditableDecimal(input.prepaymentFeePercent),
                        onValueChange = { t -> onUpdateInput { it.copy(prepaymentFeePercent = t.toDoubleOrNull() ?: 0.0) } }
                    )
                }
            }
        }

        // Balance Transfer Evaluator
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = input.balanceTransfer.enabled,
                        onCheckedChange = { en -> onUpdateInput { it.copy(balanceTransfer = it.balanceTransfer.copy(enabled = en)) } }
                    )
                    Text("Balance Transfer Break-Even Tool", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (input.balanceTransfer.enabled) {
                    val bt = input.balanceTransfer
                    NumericInputField(
                        label = "Current Outstanding (${currency.symbol})",
                        valueText = formatEditableNumber(bt.currentOutstanding),
                        onValueChange = { t -> onUpdateInput { it.copy(balanceTransfer = bt.copy(currentOutstanding = t.toDoubleOrNull() ?: 0.0)) } }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Current Rate %",
                            valueText = formatEditableDecimal(bt.currentAnnualRate),
                            onValueChange = { t -> onUpdateInput { it.copy(balanceTransfer = bt.copy(currentAnnualRate = t.toDoubleOrNull() ?: 0.0)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "New Bank Rate %",
                            valueText = formatEditableDecimal(bt.newAnnualRate),
                            onValueChange = { t -> onUpdateInput { it.copy(balanceTransfer = bt.copy(newAnnualRate = t.toDoubleOrNull() ?: 0.0)) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Rem. Months",
                            valueText = bt.remainingMonths.toString(),
                            onValueChange = { t -> onUpdateInput { it.copy(balanceTransfer = bt.copy(remainingMonths = t.toIntOrNull() ?: 120)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "Transfer Fees (${currency.symbol})",
                            valueText = formatEditableNumber(bt.transferFees),
                            onValueChange = { t -> onUpdateInput { it.copy(balanceTransfer = bt.copy(transferFees = t.toDoubleOrNull() ?: 0.0)) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Top-Up Blended EMI Tool
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = input.topUp.enabled,
                        onCheckedChange = { en -> onUpdateInput { it.copy(topUp = it.topUp.copy(enabled = en)) } }
                    )
                    Text("Top-Up Blended EMI Tool", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (input.topUp.enabled) {
                    val tu = input.topUp
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Existing Bal (${currency.symbol})",
                            valueText = formatEditableNumber(tu.existingOutstanding),
                            onValueChange = { t -> onUpdateInput { it.copy(topUp = tu.copy(existingOutstanding = t.toDoubleOrNull() ?: 0.0)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "Top-Up (${currency.symbol})",
                            valueText = formatEditableNumber(tu.topUpAmount),
                            onValueChange = { t -> onUpdateInput { it.copy(topUp = tu.copy(topUpAmount = t.toDoubleOrNull() ?: 0.0)) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Existing Rate %",
                            valueText = formatEditableDecimal(tu.existingAnnualRate),
                            onValueChange = { t -> onUpdateInput { it.copy(topUp = tu.copy(existingAnnualRate = t.toDoubleOrNull() ?: 8.5)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "Top-Up Rate %",
                            valueText = formatEditableDecimal(tu.topUpAnnualRate),
                            onValueChange = { t -> onUpdateInput { it.copy(topUp = tu.copy(topUpAnnualRate = t.toDoubleOrNull() ?: 9.5)) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveLoanDialog(
    defaultNickname: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Int) -> Unit
) {
    var nickname by remember { mutableStateOf(defaultNickname) }
    var lender by remember { mutableStateOf("") }
    var enableReminder by remember { mutableStateOf(true) }
    var daysBefore by remember { mutableStateOf("3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to My Loans") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Loan Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lender,
                    onValueChange = { lender = it },
                    label = { Text("Bank / Lender Name") },
                    placeholder = { Text("e.g., SBI, HDFC, Nabil Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EMI Due Reminder", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enableReminder, onCheckedChange = { enableReminder = it })
                }
                if (enableReminder) {
                    NumericInputField(
                        label = "Remind N days before due date",
                        valueText = daysBefore,
                        onValueChange = { daysBefore = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(nickname, lender, enableReminder, daysBefore.toIntOrNull() ?: 3) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StartDatePickerDialog(
    year: Int,
    month: Int,
    day: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    var yText by remember { mutableStateOf(year.toString()) }
    var mText by remember { mutableStateOf(month.toString()) }
    var dText by remember { mutableStateOf(day.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loan Start Date") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField("Day", dText, { dText = it }, modifier = Modifier.weight(1f))
                NumericInputField("Month", mText, { mText = it }, modifier = Modifier.weight(1f))
                NumericInputField("Year", yText, { yText = it }, modifier = Modifier.weight(1.3f))
            }
        },
        confirmButton = {
            Button(onClick = {
                val y = (yText.toIntOrNull() ?: year).coerceIn(2000, 2060)
                val m = (mText.toIntOrNull() ?: month).coerceIn(1, 12)
                val d = (dText.toIntOrNull() ?: day).coerceIn(1, 28)
                onConfirm(y, m, d)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatEditableNumber(value: Double): String {
    if (value == 0.0) return ""
    return if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)
}

private fun formatEditableDecimal(value: Double): String {
    if (value == 0.0) return "0"
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

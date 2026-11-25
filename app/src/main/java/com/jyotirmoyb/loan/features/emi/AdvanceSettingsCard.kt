package com.jyotirmoyb.loan.features.emi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.PrepaymentMode
import com.jyotirmoyb.loan.engine.PrepaymentRule
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.TealPrimary
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvanceSettingsCard(
    input: CalculationInput,
    category: LoanCategory?,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit,
    onSelectEmiMode: (String) -> Unit,
    onToggleMoratorium: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var cardExpanded by remember { mutableStateOf(true) }
    var expandedRowKey by remember { mutableStateOf<String?>(null) }
    var infoDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    val flags = input.ruleFlags.toSet()
    val currentEmiModeLabel = when {
        flags.contains("RATE_FLAT") -> "Flat"
        flags.contains("EMI_STEP_UP") -> "Step-Up"
        flags.contains("EMI_STEP_DOWN") -> "Step-Down"
        flags.contains("EMI_BALLOON") -> "Balloon"
        flags.contains("RATE_HYBRID") -> "Hybrid"
        flags.contains("EMI_OFFSET") -> "Offset"
        else -> "Reducing"
    }
    val isCustomEmiMode = currentEmiModeLabel != "Reducing"
    val isFeeActive = input.processingFeePercent > 0.0 || input.insurancePremium > 0.0
    val isPrepayActive = input.prepaymentEnabled && input.prepayments.isNotEmpty()
    val isMoratoriumActive = flags.contains("MOR_SIMPLE") || flags.contains("MOR_COMPOUND") || flags.contains("MORATORIUM")

    val activeLabels = buildList {
        if (isCustomEmiMode) add(currentEmiModeLabel)
        if (isFeeActive) add("Processing Fee")
        if (isPrepayActive) add("Prepayment")
        if (isMoratoriumActive) add("Moratorium")
        if (input.balanceTransfer.enabled) add("Balance Transfer")
        if (input.topUp.enabled) add("Top-Up")
    }

    val prepayChipText = if (isPrepayActive) {
        val first = input.prepayments.first()
        val amtStr = CurrencyFormatter.format(first.amount, currency)
        val suffix = if (first.isRecurring) "/${first.intervalMonths}m" else "Once"
        if (input.prepayments.size == 1) "$amtStr $suffix" else "$amtStr +${input.prepayments.size - 1}"
    } else null

    FintechCard(
        borderColor = if (cardExpanded) TealPrimary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
        modifier = modifier.testTag("emi_advance_settings_card")
    ) {
        // Card Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { cardExpanded = !cardExpanded }
                .padding(vertical = 2.dp)
                .testTag("emi_advance_settings_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = TealPrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.emi_advance_settings),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeLabels.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = TealPrimary,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("emi_advance_active_badge")
                            ) {
                                Text(
                                    text = "${activeLabels.size} Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (activeLabels.isEmpty()) {
                            "Optional fees, prepayments & EMI rules"
                        } else {
                            activeLabels.joinToString(", ")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (cardExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (cardExpanded) "Collapse advance settings" else "Expand advance settings",
                    tint = TealPrimaryDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = cardExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. EMI Type Row
                AdvanceSettingRow(
                    title = stringResource(R.string.emi_type_row),
                    checked = isCustomEmiMode,
                    chipText = currentEmiModeLabel,
                    chipHighlighted = isCustomEmiMode,
                    expanded = expandedRowKey == "emi_type",
                    testTag = "adv_row_emi_type",
                    onCheckedChange = { checked ->
                        if (!checked) {
                            onSelectEmiMode("EMI_STANDARD")
                        } else {
                            expandedRowKey = "emi_type"
                        }
                    },
                    onInfoClick = {
                        infoDialogContent = "EMI Structure Type" to
                            "Reducing balance charges interest only on the remaining principal each month (RBI standard). Flat rate charges interest on the full original principal for the entire tenure. Step-Up/Step-Down change your EMI periodically, Balloon keeps a lump sum for the final month, and Hybrid locks the rate initially before floating."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "emi_type") null else "emi_type"
                    }
                ) {
                    val emiModes = listOf(
                        "EMI_STANDARD" to "Reducing",
                        "RATE_FLAT" to "Flat",
                        "EMI_STEP_UP" to "Step-Up",
                        "EMI_STEP_DOWN" to "Step-Down",
                        "EMI_BALLOON" to "Balloon",
                        "RATE_HYBRID" to "Hybrid",
                        "EMI_OFFSET" to "Offset"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        emiModes.forEach { (flag, label) ->
                            val isSelected = currentEmiModeLabel == label
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectEmiMode(flag) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("emi_mode_chip_${label.lowercase()}")
                            )
                        }
                    }

                    if (flags.contains("EMI_STEP_UP") || flags.contains("EMI_STEP_DOWN")) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Step %",
                                valueText = input.stepPercent.toString(),
                                onValueChange = { txt ->
                                    txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(stepPercent = v.coerceIn(1.0, 40.0)) } }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Every (months)",
                                valueText = input.stepIntervalMonths.toString(),
                                onValueChange = { txt ->
                                    txt.toIntOrNull()?.let { m -> onUpdateInput { it.copy(stepIntervalMonths = m.coerceIn(1, 60)) } }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (flags.contains("EMI_BALLOON")) {
                        Spacer(Modifier.height(8.dp))
                        NumericInputField(
                            label = "Balloon % of Principal",
                            valueText = input.balloonPercent.toString(),
                            onValueChange = { txt ->
                                txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(balloonPercent = v.coerceIn(5.0, 60.0)) } }
                            },
                            supportingText = "Lump sum due at the final EMI"
                        )
                    }

                    if (flags.contains("RATE_HYBRID")) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Fixed Months",
                                valueText = input.hybridFixedMonths.toString(),
                                onValueChange = { txt ->
                                    txt.toIntOrNull()?.let { m -> onUpdateInput { it.copy(hybridFixedMonths = m.coerceIn(6, 120)) } }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Post-Fixed Rate %",
                                valueText = input.hybridFloatingRate.toString(),
                                onValueChange = { txt ->
                                    txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(hybridFloatingRate = r.coerceIn(1.0, 35.0)) } }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Processing Fee & Insurance Row
                AdvanceSettingRow(
                    title = stringResource(R.string.emi_processing_fee_row),
                    checked = isFeeActive,
                    chipText = if (isFeeActive) "${String.format("%.2f", input.processingFeePercent).trimEnd('0').trimEnd('.')}%" else null,
                    chipHighlighted = isFeeActive,
                    expanded = expandedRowKey == "processing_fee",
                    testTag = "adv_row_processing_fee",
                    onCheckedChange = { checked ->
                        if (checked) {
                            onUpdateInput { it.copy(processingFeePercent = if (it.processingFeePercent <= 0.0) 0.5 else it.processingFeePercent) }
                            expandedRowKey = "processing_fee"
                        } else {
                            onUpdateInput { it.copy(processingFeePercent = 0.0, insurancePremium = 0.0) }
                        }
                    },
                    onInfoClick = {
                        infoDialogContent = "Processing Fee & Insurance" to
                            "One-time bank processing fee (typically 0.25%–2% of loan amount, plus applicable GST) and optional loan protection insurance. Included in your Effective Total Cost."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "processing_fee") null else "processing_fee"
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Processing Fee (%)",
                            valueText = if (input.processingFeePercent == 0.0) "" else input.processingFeePercent.toString(),
                            onValueChange = { txt ->
                                onUpdateInput { it.copy(processingFeePercent = (txt.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 10.0)) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "Insurance (${currency.symbol})",
                            valueText = if (input.insurancePremium == 0.0) "" else input.insurancePremium.toLong().toString(),
                            onValueChange = { txt ->
                                onUpdateInput { it.copy(insurancePremium = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Prepayment / Part Payment Row
                AdvanceSettingRow(
                    title = stringResource(R.string.emi_prepayment_row),
                    checked = isPrepayActive,
                    chipText = prepayChipText,
                    chipHighlighted = isPrepayActive,
                    expanded = expandedRowKey == "prepayment",
                    testTag = "adv_row_prepayment",
                    onCheckedChange = { checked ->
                        if (checked) {
                            val defaultAmt = if (input.principal >= 10_00_000.0) 600000.0 else (input.principal * 0.2).coerceAtLeast(10000.0)
                            val rules = input.prepayments.ifEmpty {
                                listOf(
                                    PrepaymentRule(
                                        startMonth = 7.coerceAtMost(input.tenureMonths),
                                        amount = defaultAmt,
                                        isRecurring = false,
                                        mode = PrepaymentMode.REDUCE_TENURE.name
                                    )
                                )
                            }
                            onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = rules) }
                            expandedRowKey = "prepayment"
                        } else {
                            onUpdateInput { it.copy(prepaymentEnabled = false) }
                        }
                    },
                    onInfoClick = {
                        infoDialogContent = "Prepayment / Part Payment" to
                            "Pay extra principal either once or periodically. Under RBI rules, individual floating-rate loans have zero foreclosure/prepayment penalty. Reducing Tenure usually saves significantly more interest than Reducing EMI."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "prepayment") null else "prepayment"
                    }
                ) {
                    PrepaymentEditorContent(
                        input = input,
                        currency = currency,
                        onUpdateInput = onUpdateInput
                    )
                }

                // 4. Moratorium Period Row
                AdvanceSettingRow(
                    title = stringResource(R.string.emi_moratorium_row),
                    checked = isMoratoriumActive,
                    chipText = if (isMoratoriumActive) {
                        "${input.moratoriumMonths}m ${if (input.moratoriumCompound) "Compound" else "Simple"}"
                    } else null,
                    chipHighlighted = isMoratoriumActive,
                    expanded = expandedRowKey == "moratorium",
                    testTag = "adv_row_moratorium",
                    onCheckedChange = { checked ->
                        onToggleMoratorium(checked)
                        if (checked) expandedRowKey = "moratorium"
                    },
                    onInfoClick = {
                        infoDialogContent = "Moratorium / Grace Period" to
                            "Pause principal repayment for initial months (common in Education or Home construction loans). Choose Simple Interest to pay interest monthly during moratorium, or Compound to capitalize unpaid interest into principal."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "moratorium") null else "moratorium"
                    }
                ) {
                    NumericInputField(
                        label = "Moratorium Duration (Months)",
                        valueText = input.moratoriumMonths.toString(),
                        onValueChange = { txt ->
                            txt.toIntOrNull()?.let { m ->
                                onUpdateInput { it.copy(moratoriumMonths = m.coerceIn(1, 60)) }
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !input.moratoriumCompound,
                            onClick = {
                                onUpdateInput { curr ->
                                    val clean = curr.ruleFlags.filterNot { it == "MOR_COMPOUND" || it == "MOR_SIMPLE" } + "MOR_SIMPLE"
                                    curr.copy(moratoriumCompound = false, ruleFlags = clean)
                                }
                            },
                            label = { Text("Simple (Pay Interest)") }
                        )
                        FilterChip(
                            selected = input.moratoriumCompound,
                            onClick = {
                                onUpdateInput { curr ->
                                    val clean = curr.ruleFlags.filterNot { it == "MOR_COMPOUND" || it == "MOR_SIMPLE" } + "MOR_COMPOUND"
                                    curr.copy(moratoriumCompound = true, ruleFlags = clean)
                                }
                            },
                            label = { Text("Compound (Capitalize)") }
                        )
                    }
                }

                // 5. Variant-Adaptive Advance Rows (only rendered when selected catalog variant needs them)
                VariantAdaptiveAdvanceRows(
                    input = input,
                    category = category,
                    currency = currency,
                    expandedRowKey = expandedRowKey,
                    onToggleExpand = { key ->
                        expandedRowKey = if (expandedRowKey == key) null else key
                    },
                    onShowInfo = { title, body -> infoDialogContent = title to body },
                    onUpdateInput = onUpdateInput
                )
            }
        }
    }

    infoDialogContent?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { infoDialogContent = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { infoDialogContent = null }) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
internal fun AdvanceSettingRow(
    title: String,
    checked: Boolean,
    chipText: String?,
    chipHighlighted: Boolean,
    expanded: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) TealPrimary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = TealPrimary),
                    modifier = Modifier.testTag("${testTag}_checkbox")
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!chipText.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = if (chipHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (chipHighlighted) TealPrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.cd_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PrepaymentEditorContent(
    input: CalculationInput,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    val rules = input.prepayments
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rules.forEachIndexed { idx, rule ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prepayment #${idx + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimaryDark
                        )
                        IconButton(
                            onClick = {
                                val next = rules.toMutableList().apply { removeAt(idx) }
                                onUpdateInput {
                                    it.copy(
                                        prepayments = next,
                                        prepaymentEnabled = next.isNotEmpty()
                                    )
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove prepayment",
                                tint = DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Amount (${currency.symbol})",
                            valueText = if (rule.amount == 0.0) "" else rule.amount.toLong().toString(),
                            onValueChange = { txt ->
                                val amt = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(amount = amt)) }
                                onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = updated) }
                            },
                            modifier = Modifier.weight(1.2f)
                        )
                        NumericInputField(
                            label = "At Month #",
                            valueText = rule.startMonth.toString(),
                            onValueChange = { txt ->
                                val m = (txt.toIntOrNull() ?: 1).coerceIn(1, input.tenureMonths)
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(startMonth = m)) }
                                onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = updated) }
                            },
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !rule.isRecurring,
                            onClick = {
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(isRecurring = false)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("One-Time") }
                        )
                        FilterChip(
                            selected = rule.isRecurring,
                            onClick = {
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(isRecurring = true)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Recurring") }
                        )
                    }

                    if (rule.isRecurring) {
                        NumericInputField(
                            label = "Repeat Every (Months)",
                            valueText = rule.intervalMonths.toString(),
                            onValueChange = { txt ->
                                val iv = (txt.toIntOrNull() ?: 12).coerceIn(1, 60)
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(intervalMonths = iv)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            }
                        )
                    }

                    val reduceTenure = rule.mode != PrepaymentMode.REDUCE_EMI.name
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = reduceTenure,
                            onClick = {
                                val updated = rules.toMutableList().apply {
                                    set(idx, rule.copy(mode = PrepaymentMode.REDUCE_TENURE.name))
                                }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Reduce Tenure") },
                            modifier = Modifier.testTag("prepay_mode_tenure_$idx")
                        )
                        FilterChip(
                            selected = !reduceTenure,
                            onClick = {
                                val updated = rules.toMutableList().apply {
                                    set(idx, rule.copy(mode = PrepaymentMode.REDUCE_EMI.name))
                                }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Reduce EMI") },
                            modifier = Modifier.testTag("prepay_mode_emi_$idx")
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumericInputField(
                label = "Prepayment Fee (%)",
                valueText = if (input.prepaymentFeePercent == 0.0) "0" else input.prepaymentFeePercent.toString(),
                onValueChange = { txt ->
                    onUpdateInput { it.copy(prepaymentFeePercent = (txt.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 10.0)) }
                },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val nextMonth = ((rules.lastOrNull()?.startMonth ?: 6) + 12).coerceAtMost(input.tenureMonths)
                    val nextRule = PrepaymentRule(
                        startMonth = nextMonth,
                        amount = 200000.0,
                        isRecurring = false,
                        mode = PrepaymentMode.REDUCE_TENURE.name
                    )
                    onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = it.prepayments + nextRule) }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_prepayment_rule_btn")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

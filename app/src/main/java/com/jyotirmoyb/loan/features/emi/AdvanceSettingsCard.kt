package com.jyotirmoyb.loan.features.emi

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CalculationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.PrepaymentMode
import com.jyotirmoyb.loan.domain.PrepaymentRule
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvanceSettingsCard(
    input: CalculationInput,
    result: CalculationResult,
    category: LoanCategory?,
    currency: CurrencyCode,
    resolvedConfig: Map<String, ResolvedConfigItem>,
    gstSourceItem: ResolvedConfigItem?,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit,
    onSelectEmiMode: (String) -> Unit,
    onToggleMoratorium: (Boolean) -> Unit,
    onApplyRepoScenario: (Double) -> Unit,
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
    val isFeeActive = input.processingFeePercent > 0.0 || input.insurancePremium > 0.0 || input.gstOnFeesEnabled
    val isPrepayActive = input.prepaymentEnabled && input.prepayments.isNotEmpty()
    val isMoratoriumActive = flags.contains("MOR_SIMPLE") || flags.contains("MOR_COMPOUND") || flags.contains("MORATORIUM")

    val activeLabels = buildList {
        if (isCustomEmiMode) add(currentEmiModeLabel)
        if (isFeeActive) add(if (input.gstOnFeesEnabled) "Fee + GST" else "Processing Fee")
        if (isPrepayActive) add("Prepayment")
        if (isMoratoriumActive) add("Moratorium")
        if (input.pmaySubsidyEnabled) add("PMAY Subsidy")
        if (input.balanceTransfer.enabled) add("Balance Transfer")
        if (input.topUp.enabled) add("Top-Up")
    }

    val prepayChipText = if (isPrepayActive) {
        val first = input.prepayments.first()
        val amtStr = CurrencyFormatter.formatShortLabel(first.amount, currency)
        val suffix = when {
            !first.isRecurring -> "Once"
            first.intervalMonths == 1 -> "/mo"
            first.intervalMonths == 12 -> "/yr"
            else -> "/${first.intervalMonths}m"
        }
        if (input.prepayments.size == 1) "$amtStr $suffix" else "$amtStr +${input.prepayments.size - 1}"
    } else {
        null
    }

    FintechCard(
        borderColor = if (cardExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
        modifier = modifier.testTag("emi_advance_settings_card")
    ) {
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
                        tint = MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("emi_advance_active_badge")
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_txt_active, activeLabels.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (activeLabels.isEmpty()) {
                            "Fees, GST, prepayments, repo scenarios & subsidies"
                        } else {
                            activeLabels.joinToString(", ")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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
                    tint = MaterialTheme.colorScheme.primary,
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
                            "Reducing balance charges interest only on the remaining principal each month (RBI KFS standard). Flat rate charges interest on the full original principal. Step-Up/Step-Down change your EMI periodically, Balloon keeps a lump sum for the final month, and Offset reduces interest-bearing principal by parked overdraft cash."
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

                // 2. Processing Fee, GST on Fees & Insurance Row
                val feeChip = buildString {
                    if (input.processingFeePercent > 0.0) {
                        append("${String.format("%.2f", input.processingFeePercent).trimEnd('0').trimEnd('.')}%")
                    }
                    if (input.gstOnFeesEnabled) {
                        if (isNotEmpty()) append(" ")
                        append("+GST")
                    }
                }.ifBlank { null }

                AdvanceSettingRow(
                    title = stringResource(R.string.emi_processing_fee_row),
                    checked = isFeeActive,
                    chipText = feeChip,
                    chipHighlighted = isFeeActive,
                    expanded = expandedRowKey == "processing_fee",
                    testTag = "adv_row_processing_fee",
                    onCheckedChange = { checked ->
                        if (checked) {
                            onUpdateInput {
                                it.copy(
                                    processingFeePercent = if (it.processingFeePercent <=
                                        0.0
                                    ) {
                                        0.5
                                    } else {
                                        it.processingFeePercent
                                    }
                                )
                            }
                            expandedRowKey = "processing_fee"
                        } else {
                            onUpdateInput { it.copy(processingFeePercent = 0.0, insurancePremium = 0.0, gstOnFeesEnabled = false) }
                        }
                    },
                    onInfoClick = {
                        infoDialogContent = "Processing Fee, GST & Insurance" to
                            "Indian lenders typically charge 0.25%–2% processing fee plus ${String.format(
                                "%.0f",
                                input.gstRatePercent
                            )}% GST on banking fees (and on monthly Credit Card EMI interest). Edit the GST rate centrally under Profile → Data & Rates."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "processing_fee") null else "processing_fee"
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                trailingBadge = if (input.insurancePremium > 0) {
                                    CurrencyFormatter.formatShortLabel(input.insurancePremium, currency)
                                } else {
                                    null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.ui_txt_fmt_c36d80,
                                        stringResource(R.string.emi_gst_on_fees_label),
                                        String.format("%.0f", input.gstRatePercent)
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                gstSourceItem?.let {
                                    Spacer(Modifier.height(3.dp))
                                    ValueSourceBadgesRow(item = it)
                                }
                            }
                            Switch(
                                checked = input.gstOnFeesEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateInput { it.copy(gstOnFeesEnabled = enabled) }
                                },
                                modifier = Modifier.testTag("emi_gst_on_fees_switch")
                            )
                        }
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
                            val defaultAmt = if (input.principal >=
                                10_00_000.0
                            ) {
                                600000.0
                            } else {
                                (input.principal * 0.2).coerceAtLeast(10000.0)
                            }
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
                            "Pay extra principal once, monthly, or yearly. Under RBI guidelines, individual floating-rate home loans carry 0% prepayment penalty. Reducing Tenure usually saves far more interest than Reducing EMI."
                    },
                    onExpandToggle = {
                        expandedRowKey = if (expandedRowKey == "prepayment") null else "prepayment"
                    }
                ) {
                    PrepaymentEditorContent(
                        input = input,
                        result = result,
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
                    } else {
                        null
                    },
                    chipHighlighted = isMoratoriumActive,
                    expanded = expandedRowKey == "moratorium",
                    testTag = "adv_row_moratorium",
                    onCheckedChange = { checked ->
                        onToggleMoratorium(checked)
                        if (checked) expandedRowKey = "moratorium"
                    },
                    onInfoClick = {
                        infoDialogContent = "Moratorium / Grace Period" to
                            "Pause principal repayment for initial months (Education course + 6–12m grace or Home construction). Part-payments mapped during moratorium reduce principal directly."
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
                            label = { Text(stringResource(R.string.ui_txt_simple_pay_interest)) }
                        )
                        FilterChip(
                            selected = input.moratoriumCompound,
                            onClick = {
                                onUpdateInput { curr ->
                                    val clean = curr.ruleFlags.filterNot { it == "MOR_COMPOUND" || it == "MOR_SIMPLE" } + "MOR_COMPOUND"
                                    curr.copy(moratoriumCompound = true, ruleFlags = clean)
                                }
                            },
                            label = { Text(stringResource(R.string.ui_txt_compound_capitalize)) }
                        )
                    }
                }

                // 5. Variant-Adaptive Advance Rows
                VariantAdaptiveAdvanceRows(
                    input = input,
                    category = category,
                    currency = currency,
                    resolvedConfig = resolvedConfig,
                    expandedRowKey = expandedRowKey,
                    onToggleExpand = { key ->
                        expandedRowKey = if (expandedRowKey == key) null else key
                    },
                    onShowInfo = { title, body -> infoDialogContent = title to body },
                    onUpdateInput = onUpdateInput,
                    onApplyRepoScenario = onApplyRepoScenario
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
                    Text(stringResource(R.string.ui_txt_got_it), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

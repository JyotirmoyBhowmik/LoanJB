package com.jyotirmoyb.loan.features.loans.bankstaff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.features.bankstaff.StaffLoanPreset
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow

@Composable
fun BankStaffTemplateEditorDialog(
    presets: List<StaffLoanPreset>,
    resolvedMap: Map<String, ResolvedConfigItem>,
    onSaveTemplate: (preset: StaffLoanPreset, newRate: Double, newAmount: Double, newTenureMonths: Int) -> Unit,
    onResetTemplate: (preset: StaffLoanPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bank_staff_template_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.bank_staff_template_editor_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bank_staff_template_editor_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presets.forEach { preset ->
                    TemplateEditorCard(
                        preset = preset,
                        rateItem = preset.staffRateKey?.let { resolvedMap[it] },
                        amountItem = preset.defaultAmountKey?.let { resolvedMap[it] },
                        tenureItem = preset.tenureMonthsKey?.let { resolvedMap[it] },
                        onSave = { r, a, t -> onSaveTemplate(preset, r, a, t) },
                        onReset = { onResetTemplate(preset) }
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cd_close))
                }
            }
        }
    }
}

@Composable
private fun TemplateEditorCard(
    preset: StaffLoanPreset,
    rateItem: ResolvedConfigItem?,
    amountItem: ResolvedConfigItem?,
    tenureItem: ResolvedConfigItem?,
    onSave: (Double, Double, Int) -> Unit,
    onReset: () -> Unit
) {
    var rateText by remember(preset.staffRate) { mutableStateOf(preset.staffRate.toString()) }
    var amountText by remember(preset.defaultAmount) { mutableStateOf(preset.defaultAmount.toLong().toString()) }
    var tenureText by remember(preset.tenureMonths) { mutableStateOf(preset.tenureMonths.toString()) }
    val hasAnyOverride = (rateItem?.isOverridden == true) ||
        (amountItem?.isOverridden == true) ||
        (tenureItem?.isOverridden == true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            rateItem?.let { ValueSourceBadgesRow(item = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField(
                    label = "Rate (%)",
                    valueText = rateText,
                    onValueChange = { rateText = it },
                    modifier = Modifier.weight(1f)
                )
                NumericInputField(
                    label = "Tenure (m)",
                    valueText = tenureText,
                    onValueChange = { tenureText = it },
                    modifier = Modifier.weight(1f)
                )
            }
            NumericInputField(
                label = "Default Amount (₹)",
                valueText = amountText,
                onValueChange = { amountText = it }
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ResetToDefaultAction(
                    visible = hasAnyOverride,
                    onClick = onReset,
                    testTag = "staff_tpl_reset_${preset.id}"
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val r = rateText.toDoubleOrNull()?.coerceIn(0.5, 25.0) ?: preset.staffRate
                        val a = amountText.toDoubleOrNull()?.coerceAtLeast(10000.0) ?: preset.defaultAmount
                        val t = tenureText.toIntOrNull()?.coerceIn(6, 360) ?: preset.tenureMonths
                        onSave(r, a, t)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("staff_tpl_save_${preset.id}")
                ) {
                    Text(stringResource(R.string.bank_staff_save_template))
                }
            }
        }
    }
}

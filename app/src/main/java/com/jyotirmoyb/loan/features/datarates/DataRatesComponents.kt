package com.jyotirmoyb.loan.features.datarates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.market.MarketApiProviderSlot
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
internal fun ConfigItemRowCard(item: ResolvedConfigItem, onEdit: () -> Unit, onReset: () -> Unit) {
    FintechCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (item.isEditableNumeric) "${item.valueText} ${item.unit}" else item.valueText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimaryDark
                )
                Spacer(Modifier.height(6.dp))
                ValueSourceBadgesRow(item = item)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ui_txt_key, item.sourceName, item.key),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_config_${item.key}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit_value),
                    tint = TealPrimaryDark
                )
            }
        }

        if (item.isOverridden) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ui_txt_default, item.defaultValueText, item.unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ResetToDefaultAction(
                    visible = true,
                    onClick = onReset,
                    testTag = "reset_config_${item.key}"
                )
            }
        }
    }
}

@Composable
internal fun EditConfigValueDialog(item: ResolvedConfigItem, onDismiss: () -> Unit, onSave: (String, (String) -> Unit) -> Unit) {
    var inputText by remember(item.key) { mutableStateOf(item.valueText) }
    var errorMessage by remember(item.key) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ui_txt_unit_default, item.unit, item.defaultValueText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.edit_value)) },
                    suffix = { if (item.isEditableNumeric) Text(item.unit) },
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (item.isEditableNumeric) KeyboardType.Decimal else KeyboardType.Text
                    ),
                    singleLine = item.isEditableNumeric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_config_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(inputText) { err -> errorMessage = err } },
                modifier = Modifier.testTag("edit_config_dialog_save")
            ) {
                Text(stringResource(R.string.save_override), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun MarketApiProvidersSettingsCard(
    uiState: DataRatesUiState,
    onSetMarketStaleHours: (Int) -> Unit,
    onSetStaleDataThresholdDays: (Int) -> Unit = {},
    onEditSlot: (MarketApiProviderSlot) -> Unit,
    onClearSlot: (MarketApiProviderSlot) -> Unit
) {
    val staleOptions = listOf(6, 12, 24, 72, 168)
    val staleDayOptions = listOf(30, 60, 90, 180)
    FintechCard(modifier = Modifier.testTag("data_rates_api_providers_card")) {
        SectionAccentHeader(
            title = stringResource(R.string.market_api_providers_title),
            badge = if (uiState.apiKeyStatus.isHardwareEncrypted) "AES-256 ENCRYPTED" else "ENCRYPTED STORE",
            accentColor = SavingsGreen,
            icon = Icons.Outlined.Lock
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.market_api_providers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        MarketApiProviderSlot.entries.forEachIndexed { idx, slot ->
            val masked = uiState.apiKeyStatus.configuredSlots[slot]
            if (idx > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = slot.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (masked != null) "Configured ($masked)" else "Not configured — using RemoteConfig / Manual",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (masked != null) SavingsGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (masked != null) {
                        IconButton(
                            onClick = { onClearSlot(slot) },
                            modifier = Modifier.testTag("clear_api_key_${slot.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear key",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onEditSlot(slot) },
                        modifier = Modifier.testTag("edit_api_key_${slot.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "Configure API key",
                            tint = TealPrimaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.market_stale_limit_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.market_stale_limit_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            staleOptions.forEach { hours ->
                val label = if (hours >= 24) "${hours / 24}d" else "${hours}h"
                FilterChip(
                    selected = uiState.marketStaleHours == hours,
                    onClick = { onSetMarketStaleHours(hours) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("market_stale_chip_${hours}h")
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.data_stale_threshold_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.data_stale_threshold_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            staleDayOptions.forEach { days ->
                FilterChip(
                    selected = uiState.staleDataThresholdDays == days,
                    onClick = { onSetStaleDataThresholdDays(days) },
                    label = { Text(stringResource(R.string.stale_days_chip_format, days)) },
                    modifier = Modifier.testTag("stale_days_chip_${days}d")
                )
            }
        }
    }
}

@Composable
internal fun EditApiKeyDialog(
    slot: MarketApiProviderSlot,
    existingMask: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var apiKeyText by remember(slot) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.displayName, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = slot.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.ui_txt_allowed_https_host, slot.endpointHint),
                    style = MaterialTheme.typography.labelSmall,
                    color = TealPrimaryDark
                )
                if (existingMask != null) {
                    Text(
                        text = stringResource(R.string.ui_txt_current_stored_key_aes_256_encrypt, existingMask),
                        style = MaterialTheme.typography.labelSmall,
                        color = SavingsGreen
                    )
                }
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text(stringResource(R.string.ui_txt_enter_new_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKeyText) },
                enabled = apiKeyText.isNotBlank(),
                modifier = Modifier.testTag("api_key_dialog_save")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (existingMask != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.ui_txt_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

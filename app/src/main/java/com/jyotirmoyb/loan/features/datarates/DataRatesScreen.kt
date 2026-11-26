package com.jyotirmoyb.loan.features.datarates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.market.MarketApiProviderSlot
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark
import com.jyotirmoyb.loan.util.ScheduleExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRatesScreen(
    uiState: DataRatesUiState,
    onSelectCategory: (ConfigCategory?) -> Unit,
    onToggleOnlineData: (Boolean) -> Unit,
    onRefreshNow: () -> Unit,
    onSaveOverride: (ResolvedConfigItem, String, (String) -> Unit, () -> Unit) -> Unit,
    onResetItem: (ResolvedConfigItem) -> Unit,
    onResetAllOverrides: () -> Unit,
    exportOverridesJson: suspend () -> String,
    onImportOverridesJson: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onSetMarketStaleHours: (Int) -> Unit = {},
    onSaveApiKey: (MarketApiProviderSlot, String) -> Unit = { _, _ -> },
    onClearApiKey: (MarketApiProviderSlot) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingItem by remember { mutableStateOf<ResolvedConfigItem?>(null) }
    var editingApiSlot by remember { mutableStateOf<MarketApiProviderSlot?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) {
                    onImportOverridesJson(text)
                } else {
                    onShowMessage("Selected file was empty.")
                }
            }.onFailure {
                onShowMessage("Could not read overrides JSON file.")
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.syncStatus.isSyncing,
        onRefresh = onRefreshNow,
        modifier = modifier
            .fillMaxSize()
            .testTag("data_rates_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Controls Card: Use online data, Last sync, Refresh now, Export/Import overrides
            item {
                FintechCard {
                    SectionAccentHeader(
                        title = stringResource(R.string.data_rates_title).uppercase(Locale.US),
                        badge = "${uiState.items.size} ITEMS",
                        accentColor = TealPrimaryDark
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.data_rates_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.use_online_data),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.use_online_data_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.useOnlineData,
                            onCheckedChange = onToggleOnlineData,
                            modifier = Modifier.testTag("data_rates_online_toggle")
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = TealPrimaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val syncTimeLabel = if (uiState.syncStatus.lastSyncAttemptMillis > 0L) {
                                formatSyncTimestamp(uiState.syncStatus.lastSyncAttemptMillis)
                            } else {
                                stringResource(R.string.last_sync_never)
                            }
                            Text(
                                text = "${stringResource(R.string.last_sync_label)}: $syncTimeLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = uiState.syncStatus.lastResultMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRefreshNow,
                        enabled = !uiState.syncStatus.isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data_rates_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.syncStatus.isSyncing) {
                                stringResource(R.string.syncing_now)
                            } else {
                                stringResource(R.string.refresh_now)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = exportOverridesJson()
                                    val res = ScheduleExporter.exportBackupJsonAndShare(context, json)
                                    if (res.isFailure) onShowMessage("Could not export overrides JSON")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("data_rates_export_overrides_btn")
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_overrides), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("data_rates_import_overrides_btn")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import_overrides), maxLines = 1)
                        }
                    }

                    if (uiState.overrideCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onResetAllOverrides,
                                modifier = Modifier.testTag("data_rates_reset_all_btn")
                            ) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${stringResource(R.string.reset_all_overrides)} (${uiState.overrideCount})")
                            }
                        }
                    }
                }
            }

            // Optional Live Market API Providers (EncryptedSharedPreferences) & Staleness Limit
            item {
                MarketApiProvidersSettingsCard(
                    uiState = uiState,
                    onSetMarketStaleHours = onSetMarketStaleHours,
                    onEditSlot = { editingApiSlot = it },
                    onClearSlot = onClearApiKey
                )
            }

            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All (${uiState.items.size})") },
                        modifier = Modifier.testTag("data_rates_cat_all")
                    )
                    ConfigCategory.entries.forEach { cat ->
                        val count = uiState.items.count { it.category == cat }
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text("${cat.displayTitle} ($count)") },
                            modifier = Modifier.testTag("data_rates_cat_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            // Grouped Config Items
            uiState.groupedItems.forEach { (category, catItems) ->
                item(key = "header_${category.name}") {
                    SectionAccentHeader(
                        title = category.displayTitle.uppercase(Locale.US),
                        badge = "${catItems.size}",
                        accentColor = TealPrimaryDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(catItems, key = { it.key }) { item ->
                    ConfigItemRowCard(
                        item = item,
                        onEdit = { editingItem = item },
                        onReset = { onResetItem(item) }
                    )
                }
            }

            item {
                DisclaimerBanner()
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    editingItem?.let { target ->
        EditConfigValueDialog(
            item = target,
            onDismiss = { editingItem = null },
            onSave = { newText, onError ->
                onSaveOverride(target, newText, onError) {
                    editingItem = null
                }
            }
        )
    }

    editingApiSlot?.let { slot ->
        EditApiKeyDialog(
            slot = slot,
            existingMask = uiState.apiKeyStatus.configuredSlots[slot],
            onDismiss = { editingApiSlot = null },
            onSave = { keyText ->
                onSaveApiKey(slot, keyText)
                editingApiSlot = null
            },
            onClear = {
                onClearApiKey(slot)
                editingApiSlot = null
            }
        )
    }
}

@Composable
private fun ConfigItemRowCard(
    item: ResolvedConfigItem,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
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
                    text = "${item.sourceName} • Key: ${item.key}",
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
                    text = "Default: ${item.defaultValueText} ${item.unit}",
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
private fun EditConfigValueDialog(
    item: ResolvedConfigItem,
    onDismiss: () -> Unit,
    onSave: (String, (String) -> Unit) -> Unit
) {
    var inputText by remember(item.key) { mutableStateOf(item.valueText) }
    var errorMessage by remember(item.key) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Unit: ${item.unit} • Default: ${item.defaultValueText}",
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
private fun MarketApiProvidersSettingsCard(
    uiState: DataRatesUiState,
    onSetMarketStaleHours: (Int) -> Unit,
    onEditSlot: (MarketApiProviderSlot) -> Unit,
    onClearSlot: (MarketApiProviderSlot) -> Unit
) {
    val staleOptions = listOf(6, 12, 24, 72, 168)
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
    }
}

@Composable
private fun EditApiKeyDialog(
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
                    text = "Allowed HTTPS host: ${slot.endpointHint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealPrimaryDark
                )
                if (existingMask != null) {
                    Text(
                        text = "Current stored key: $existingMask (AES-256 encrypted on device)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SavingsGreen
                    )
                }
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("Enter new API key") },
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
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun formatSyncTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    return formatter.format(Date(millis))
}

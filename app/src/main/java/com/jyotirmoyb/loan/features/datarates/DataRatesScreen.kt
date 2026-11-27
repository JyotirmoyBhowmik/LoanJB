package com.jyotirmoyb.loan.features.datarates

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.market.MarketApiProviderSlot
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark
import com.jyotirmoyb.loan.util.ScheduleExporter
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    onSetStaleDataThresholdDays: (Int) -> Unit = {},
    onSaveApiKey: (MarketApiProviderSlot, String) -> Unit = { _, _ -> },
    onClearApiKey: (MarketApiProviderSlot) -> Unit = {},
    onImportUpdateFile: (String, String?) -> Unit = { json, _ -> onImportOverridesJson(json) },
    onApplyPendingBundle: (Set<String>, Set<String>, Boolean) -> Unit = { _, _, _ -> },
    onDismissBundlePreview: () -> Unit = {},
    onUndoLastImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingItem by remember { mutableStateOf<ResolvedConfigItem?>(null) }
    var editingApiSlot by remember { mutableStateOf<MarketApiProviderSlot?>(null) }

    val tooLargeMsg = stringResource(R.string.bundle_file_too_large)
    val emptyFileMsg = stringResource(R.string.bundle_file_empty)
    val readErrorMsg = stringResource(R.string.bundle_file_read_error)
    val exportErrorMsg = stringResource(R.string.bundle_export_error)

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = resolveDisplayFileName(context, uri)
            when (val read = readUtf8FileCapped(context, uri, RemoteConfigValidator.MAX_UPDATE_BUNDLE_BYTES)) {
                is FileReadOutcome.Success -> {
                    if (read.content.isNotBlank()) {
                        onImportUpdateFile(read.content, fileName)
                    } else {
                        onShowMessage(emptyFileMsg)
                    }
                }
                FileReadOutcome.TooLarge -> onShowMessage(tooLargeMsg)
                FileReadOutcome.Error -> onShowMessage(readErrorMsg)
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
            item {
                FintechCard {
                    SectionAccentHeader(
                        title = stringResource(R.string.data_rates_title).uppercase(Locale.US),
                        badge = "${uiState.items.size}",
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
                                text = stringResource(R.string.ui_txt_fmt_6c03a4, stringResource(R.string.last_sync_label), syncTimeLabel),
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
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("data_rates_import_overrides_btn")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import_update_file), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val json = exportOverridesJson()
                                    val res = ScheduleExporter.exportBackupJsonAndShare(context, json)
                                    if (res.isFailure) onShowMessage(exportErrorMsg)
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
                    }

                    if (uiState.importHistoryCount > 0 || uiState.overrideCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.importHistoryCount > 0) {
                                TextButton(
                                    onClick = onUndoLastImport,
                                    modifier = Modifier.testTag("data_rates_undo_import_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.undo_last_import_count,
                                            uiState.importHistoryCount
                                        )
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            if (uiState.overrideCount > 0) {
                                TextButton(
                                    onClick = onResetAllOverrides,
                                    modifier = Modifier.testTag("data_rates_reset_all_btn")
                                ) {
                                    Icon(
                                        Icons.Outlined.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(
                                            R.string.ui_txt_fmt,
                                            stringResource(R.string.reset_all_overrides),
                                            uiState.overrideCount
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                MarketApiProvidersSettingsCard(
                    uiState = uiState,
                    onSetMarketStaleHours = onSetMarketStaleHours,
                    onSetStaleDataThresholdDays = onSetStaleDataThresholdDays,
                    onEditSlot = { editingApiSlot = it },
                    onClearSlot = onClearApiKey
                )
            }

            item {
                val allLabel = stringResource(R.string.all_items_count, uiState.items.size)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text(allLabel) },
                        modifier = Modifier.testTag("data_rates_cat_all")
                    )
                    ConfigCategory.entries.forEach { cat ->
                        val count = uiState.items.count { it.category == cat }
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text(stringResource(R.string.ui_txt_fmt, cat.displayTitle, count)) },
                            modifier = Modifier.testTag("data_rates_cat_${cat.name.lowercase()}")
                        )
                    }
                }
            }

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

    uiState.pendingBundlePreview?.let { preview ->
        UpdateBundlePreviewDialog(
            preview = preview,
            onDismiss = onDismissBundlePreview,
            onApply = onApplyPendingBundle
        )
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

private sealed class FileReadOutcome {
    data class Success(val content: String) : FileReadOutcome()
    data object TooLarge : FileReadOutcome()
    data object Error : FileReadOutcome()
}

private fun readUtf8FileCapped(context: Context, uri: Uri, maxBytes: Int): FileReadOutcome {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return FileReadOutcome.TooLarge
                output.write(buffer, 0, read)
            }
            FileReadOutcome.Success(output.toString(Charsets.UTF_8.name()))
        } ?: FileReadOutcome.Error
    }.getOrElse { FileReadOutcome.Error }
}

private fun resolveDisplayFileName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

private fun formatSyncTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    return formatter.format(Date(millis))
}

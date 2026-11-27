package com.jyotirmoyb.loan.features.datarates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.UpdateBundlePreview
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
internal fun UpdateBundlePreviewDialog(
    preview: UpdateBundlePreview,
    onDismiss: () -> Unit,
    onApply: (selectedItemKeys: Set<String>, selectedTableIds: Set<String>, applyConfirmedUnchanged: Boolean) -> Unit
) {
    var selectedItemKeys by remember(preview) {
        mutableStateOf(preview.itemChanges.map { it.key }.toSet())
    }
    var selectedTableIds by remember(preview) {
        mutableStateOf(preview.tableChanges.map { it.id }.toSet())
    }
    var applyConfirmedUnchanged by remember(preview) {
        mutableStateOf(preview.confirmedUnchangedCount > 0)
    }

    val totalSelectable = preview.itemChanges.size + preview.tableChanges.size
    val totalSelected = selectedItemKeys.size + selectedTableIds.size
    val allSelected = totalSelected == totalSelectable
    val canApply = totalSelected > 0 || (applyConfirmedUnchanged && preview.confirmedUnchangedCount > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_bundle_preview_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.bundle_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    preview.partInfo?.let { part ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = part.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.bundle_preview_meta,
                        preview.dataVersion,
                        DataSourceResolver.formatToCompactDate(preview.generatedOn)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (preview.generatedBy.isNotBlank()) {
                    Text(
                        text = preview.generatedBy,
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimaryDark
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (preview.itemChanges.isNotEmpty()) {
                    item(key = "header_items") {
                        Text(
                            text = stringResource(R.string.bundle_preview_changes_header, selectedItemKeys.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(preview.itemChanges, key = { it.key }) { change ->
                        val checked = selectedItemKeys.contains(change.key)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedItemKeys = if (isChecked) {
                                            selectedItemKeys + change.key
                                        } else {
                                            selectedItemKeys - change.key
                                        }
                                    },
                                    modifier = Modifier.testTag("bundle_item_checkbox_${change.key}")
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.ui_txt_fmt_8eee4e,
                                            change.label,
                                            change.oldValueDisplay,
                                            change.newValueDisplay
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.bundle_preview_effective_source,
                                            DataSourceResolver.formatToCompactDate(change.effectiveFrom),
                                            change.sourceName
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (preview.tableChanges.isEmpty()) {
                    item(key = "empty_changes") {
                        Text(
                            text = stringResource(R.string.bundle_preview_empty_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (preview.tableChanges.isNotEmpty()) {
                    item(key = "header_tables") {
                        Text(
                            text = stringResource(R.string.bundle_preview_tables_header, selectedTableIds.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(preview.tableChanges, key = { it.id }) { table ->
                        val checked = selectedTableIds.contains(table.id)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedTableIds = if (isChecked) {
                                            selectedTableIds + table.id
                                        } else {
                                            selectedTableIds - table.id
                                        }
                                    },
                                    modifier = Modifier.testTag("bundle_table_checkbox_${table.id}")
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_txt_fmt_6c03a4, table.label, table.summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.bundle_preview_effective_source,
                                            DataSourceResolver.formatToCompactDate(table.effectiveFrom),
                                            table.sourceName
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (preview.confirmedUnchangedCount > 0) {
                    item(key = "confirmed_unchanged") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = applyConfirmedUnchanged,
                                onCheckedChange = { applyConfirmedUnchanged = it }
                            )
                            Text(
                                text = stringResource(
                                    R.string.bundle_preview_confirmed_unchanged,
                                    preview.confirmedUnchangedCount,
                                    DataSourceResolver.formatToCompactDate(preview.generatedOn)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (preview.unresolved.isNotEmpty()) {
                    item(key = "unresolved_block") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.bundle_preview_unresolved_header, preview.unresolved.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(preview.unresolved, key = { "unres_${it.key}" }) { item ->
                        Text(
                            text = stringResource(R.string.ui_txt_fmt_9d2bc0, item.key, item.reason),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (preview.suggestedNewKeys.isNotEmpty()) {
                    item(key = "suggested_block") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.bundle_preview_suggested_header,
                                preview.suggestedNewKeys.size
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(preview.suggestedNewKeys, key = { "sugg_${it.key}" }) { item ->
                        Text(
                            text = stringResource(R.string.ui_txt_fmt_cfbc6c, item.key, item.value.formattedNumber(), item.unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (preview.notes.isNotEmpty()) {
                    item(key = "notes_block") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.bundle_preview_notes_header),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(preview.notes.indices.toList(), key = { "note_$it" }) { idx ->
                        Text(
                            text = stringResource(R.string.ui_txt_fmt_bac541, preview.notes[idx]),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(selectedItemKeys, selectedTableIds, applyConfirmedUnchanged) },
                enabled = canApply,
                modifier = Modifier.testTag("bundle_preview_apply_btn")
            ) {
                Text(
                    text = if (allSelected || totalSelectable == 0) {
                        stringResource(R.string.bundle_preview_apply_all)
                    } else {
                        stringResource(R.string.bundle_preview_apply_selected, totalSelected)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("bundle_preview_cancel_btn")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

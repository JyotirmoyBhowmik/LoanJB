package com.jyotirmoyb.loan.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.security.BackupCrypto
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.RegionCode
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.util.ScheduleExporter
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    catalog: LoanCatalog,
    serializeCatalog: (LoanCatalog) -> String,
    createEncryptedBackupJson: suspend (String) -> Result<String>,
    onSetLanguage: (String) -> Unit,
    onSetRegion: (RegionCode) -> Unit,
    onSetCurrency: (CurrencyCode) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetMonthlyIncome: (Double) -> Unit,
    onSetTaxRegime: (String) -> Unit,
    onSetUserAge: (Int) -> Unit,
    onSetRiskProfile: (String) -> Unit,
    onSaveCatalogObject: (LoanCatalog) -> Unit,
    onSaveCatalogRawJson: (String) -> Unit,
    onResetCatalogDefault: () -> Unit,
    onRestoreBackupJson: (String, String?) -> Unit,
    onResetAllData: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCatalogEditor by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showEncryptExportDialog by remember { mutableStateOf(false) }
    var pendingEncryptedRestoreJson by remember { mutableStateOf<String?>(null) }

    var incomeText by remember(preferences.monthlyIncome) {
        mutableStateOf(if (preferences.monthlyIncome == 0.0) "" else preferences.monthlyIncome.toLong().toString())
    }
    var ageText by remember(preferences.userAge) {
        mutableStateOf(preferences.userAge.toString())
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { json ->
                if (json.isNullOrBlank()) {
                    onShowMessage("Selected file was empty.")
                } else if (BackupCrypto.isEncryptedEnvelope(json)) {
                    pendingEncryptedRestoreJson = json
                } else {
                    onRestoreBackupJson(json, null)
                }
            }.onFailure {
                onShowMessage("Could not read backup file.")
            }
        }
    }

    if (showCatalogEditor) {
        CatalogEditorScreen(
            catalog = catalog,
            catalogJson = remember(catalog) { serializeCatalog(catalog) },
            onBack = { showCatalogEditor = false },
            onSaveCatalogObject = { updated ->
                onSaveCatalogObject(updated)
                showCatalogEditor = false
            },
            onSaveCatalogRawJson = { raw ->
                onSaveCatalogRawJson(raw)
                showCatalogEditor = false
            },
            onResetDefault = onResetCatalogDefault,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Language & Appearance
        item {
            FintechCard {
                SectionAccentHeader(
                    title = "Language & Theme",
                    badge = "${preferences.languageCode.uppercase()} • ${preferences.themeMode.name}"
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.languageCode == "en",
                        onClick = { onSetLanguage("en") },
                        label = { Text(stringResource(R.string.ui_txt_english)) },
                        modifier = Modifier.testTag("settings_lang_en")
                    )
                    FilterChip(
                        selected = preferences.languageCode == "hi",
                        onClick = { onSetLanguage("hi") },
                        label = { Text(stringResource(R.string.ui_txt_hindi)) },
                        modifier = Modifier.testTag("settings_lang_hi")
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Region & Currency
        item {
            FintechCard {
                SectionAccentHeader("Region & Currency", badge = "${preferences.region.code} • ${preferences.currency.code}")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RegionCode.entries.forEach { region ->
                        FilterChip(
                            selected = preferences.region == region,
                            onClick = { onSetRegion(region) },
                            label = { Text(region.label) },
                            modifier = Modifier.testTag("settings_region_${region.name.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CurrencyCode.entries.forEach { code ->
                        val chipLabel = when (code) {
                            CurrencyCode.INR -> "INR ₹ (12,34,567)"
                            CurrencyCode.NPR -> "NPR Rs. (12,34,567)"
                            CurrencyCode.USD -> "USD $ (1,234,567)"
                        }
                        FilterChip(
                            selected = preferences.currency == code,
                            onClick = { onSetCurrency(code) },
                            label = { Text(chipLabel) },
                            modifier = Modifier.testTag("settings_currency_${code.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Financial Profile Defaults (feeds DTI, Eligibility, Income Tax, Retirement, Rules & Where to Invest)
        item {
            FintechCard {
                SectionAccentHeader("Financial Profile Defaults", badge = "PLANNER DEFAULTS")
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui_txt_used_as_defaults_across_my_loans_d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                NumericInputField(
                    label = "Monthly Take-Home Income (${preferences.currency.symbol})",
                    valueText = incomeText,
                    onValueChange = { txt ->
                        incomeText = txt
                        txt.toDoubleOrNull()?.let { onSetMonthlyIncome(it) }
                    },
                    prefix = "${preferences.currency.symbol} ",
                    trailingBadge = CurrencyFormatter.formatShortLabel(preferences.monthlyIncome, preferences.currency),
                    modifier = Modifier.testTag("settings_income_input")
                )
                Spacer(Modifier.height(10.dp))
                NumericInputField(
                    label = "Your Age (Years)",
                    valueText = ageText,
                    onValueChange = { txt ->
                        val filtered = txt.filter { it.isDigit() }.take(2)
                        ageText = filtered
                        filtered.toIntOrNull()?.takeIf { it in 18..85 }?.let { onSetUserAge(it) }
                    },
                    supportingText = "Feeds Retirement Planner, Eligibility & Asset Allocation age bracket",
                    modifier = Modifier.testTag("settings_age_input")
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.ui_txt_preferred_tax_regime),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.preferredTaxRegime == "NEW",
                        onClick = { onSetTaxRegime("NEW") },
                        label = { Text(stringResource(R.string.ui_txt_new_regime_default)) },
                        modifier = Modifier.testTag("settings_regime_new")
                    )
                    FilterChip(
                        selected = preferences.preferredTaxRegime == "OLD",
                        onClick = { onSetTaxRegime("OLD") },
                        label = { Text(stringResource(R.string.ui_txt_old_regime)) },
                        modifier = Modifier.testTag("settings_regime_old")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.ui_txt_default_risk_profile),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "CONSERVATIVE" to "Conservative",
                        "MODERATE" to "Moderate",
                        "AGGRESSIVE" to "Aggressive"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = preferences.riskProfile == key,
                            onClick = { onSetRiskProfile(key) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("settings_risk_${key.lowercase()}")
                        )
                    }
                }
            }
        }

        // Editable Loan Catalog
        item {
            FintechCard {
                SectionAccentHeader("Loan Catalog Config", badge = "${catalog.categories.size} CATS")
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui_txt_customize_indian_loan_categories_v),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showCatalogEditor = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_edit_catalog_btn")
                ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_txt_edit_loan_catalog))
                }
            }
        }

        // Encrypted Offline Backup & Restore (AES-256-GCM)
        item {
            FintechCard {
                SectionAccentHeader("Encrypted Backup & Restore", badge = "AES-256-GCM")
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui_txt_sensitive_tables_and_security_keys),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showEncryptExportDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_backup_json_btn")
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_txt_encrypted_export))
                    }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_restore_json_btn")
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_txt_restore))
                    }
                }
            }
        }

        // Danger Zone: Reset All Data
        item {
            FintechCard(borderColor = DangerRed.copy(alpha = 0.35f)) {
                SectionAccentHeader("Reset Local Data", accentColor = DangerRed)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui_txt_clears_saved_loans_comparison_scen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_all_btn")
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_txt_reset_all_data))
                }
            }
        }

        item {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showEncryptExportDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEncryptExportDialog = false },
            title = { Text(stringResource(R.string.ui_txt_encrypt_backup_with_password), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.ui_txt_your_backup_will_be_encrypted_usin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorText = null
                        },
                        label = { Text(stringResource(R.string.ui_txt_backup_password_min_6_chars)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorText = null
                        },
                        label = { Text(stringResource(R.string.ui_txt_confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.length < BackupCrypto.MIN_PASSWORD_LENGTH) {
                            errorText = "Password must be at least ${BackupCrypto.MIN_PASSWORD_LENGTH} characters."
                        } else if (password != confirmPassword) {
                            errorText = "Passwords do not match."
                        } else {
                            showEncryptExportDialog = false
                            scope.launch {
                                val encrypted = createEncryptedBackupJson(password)
                                encrypted.fold(
                                    onSuccess = { cipherJson ->
                                        val res = ScheduleExporter.exportBackupJsonAndShare(
                                            context = context,
                                            jsonContent = cipherJson,
                                            fileName = "loanjb_encrypted_backup.json"
                                        )
                                        if (res.isFailure) onShowMessage("Backup export failed")
                                    },
                                    onFailure = { onShowMessage(it.message ?: "Encryption failed") }
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_txt_encrypt_share), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptExportDialog = false }) { Text(stringResource(R.string.ui_txt_cancel)) }
            }
        )
    }

    pendingEncryptedRestoreJson?.let { rawEncrypted ->
        var restorePwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingEncryptedRestoreJson = null },
            title = { Text(stringResource(R.string.ui_txt_decrypt_backup_file), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.ui_txt_enter_the_password_used_when_this),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = restorePwd,
                        onValueChange = { restorePwd = it },
                        label = { Text(stringResource(R.string.ui_txt_backup_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pwd = restorePwd
                        pendingEncryptedRestoreJson = null
                        onRestoreBackupJson(rawEncrypted, pwd)
                    },
                    enabled = restorePwd.isNotEmpty()
                ) {
                    Text(stringResource(R.string.ui_txt_decrypt_restore), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEncryptedRestoreJson = null }) { Text(stringResource(R.string.ui_txt_cancel)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.ui_txt_reset_all_data_9ec1c2)) },
            text = { Text(stringResource(R.string.ui_txt_this_will_delete_all_saved_loans_c)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text(stringResource(R.string.ui_txt_reset_everything), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.ui_txt_cancel)) } }
        )
    }
}

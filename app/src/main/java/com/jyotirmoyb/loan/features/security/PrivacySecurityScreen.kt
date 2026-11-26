package com.jyotirmoyb.loan.features.security

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
fun PrivacySecurityScreen(
    appLockEnabled: Boolean,
    flagSecureEnabled: Boolean,
    hasBiometricAvailable: Boolean,
    hasPinConfigured: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onSetPin: (String) -> Result<Unit>,
    onClearPin: () -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPinDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("privacy_security_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Security Controls Card
        item {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.security_controls_title),
                    badge = if (appLockEnabled) "LOCKED" else "LOCAL",
                    accentColor = TealPrimaryDark
                )
                Spacer(Modifier.height(10.dp))

                // Biometric / PIN App Lock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = TealPrimaryDark)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.security_applock_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (hasBiometricAvailable) {
                                    stringResource(R.string.security_applock_bio_available)
                                } else if (hasPinConfigured) {
                                    stringResource(R.string.security_applock_pin_only)
                                } else {
                                    stringResource(R.string.security_applock_setup_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable && !hasBiometricAvailable && !hasPinConfigured) {
                                showPinDialog = true
                            } else {
                                onToggleAppLock(wantEnable)
                            }
                        },
                        modifier = Modifier.testTag("security_applock_switch")
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("security_set_pin_btn")
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasPinConfigured) "Change App PIN" else "Set App PIN")
                    }
                    if (hasPinConfigured) {
                        OutlinedButton(
                            onClick = {
                                onClearPin()
                                if (!hasBiometricAvailable && appLockEnabled) {
                                    onToggleAppLock(false)
                                }
                                onShowMessage("App PIN removed")
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("security_clear_pin_btn")
                        ) {
                            Text("Remove PIN", color = DangerRed)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                // FLAG_SECURE Screenshot & App Switcher Protection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Screenshot, contentDescription = null, tint = TealPrimaryDark)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.security_flag_secure_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.security_flag_secure_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = flagSecureEnabled,
                        onCheckedChange = onToggleFlagSecure,
                        modifier = Modifier.testTag("security_flag_secure_switch")
                    )
                }
            }
        }

        // Privacy Guarantees Card
        item {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.privacy_architecture_title),
                    badge = "ZERO TELEMETRY",
                    accentColor = SavingsGreen
                )
                Spacer(Modifier.height(10.dp))
                PrivacyBulletRow(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Zero Analytics, Ads, or Tracking SDKs",
                    body = "LoanJB bundles no Firebase Analytics, Crashlytics, ad networks, or third-party telemetry SDKs. Your loans, income, tax inputs, and goals are never tracked or profiled."
                )
                Spacer(Modifier.height(10.dp))
                PrivacyBulletRow(
                    icon = Icons.Outlined.CloudOff,
                    title = "100% On-Device Financial Storage & Auto Backup Exclusions",
                    body = "Saved loans, EMI ledgers, income, and preferences live strictly on this device. Android Auto Backup & cloud extraction rules explicitly exclude the Room database (loanjb_offline.db) and Android Keystore-backed EncryptedSharedPreferences so your financial data is never silently uploaded to cloud backup."
                )
                Spacer(Modifier.height(10.dp))
                PrivacyBulletRow(
                    icon = Icons.Outlined.EnhancedEncryption,
                    title = "AES-256-GCM Encrypted Backups & Hardware-Backed Keystore",
                    body = "Manual JSON backups are encrypted with your password using PBKDF2-HMAC-SHA256 (120,000 iterations, unique random salt) and AES-256-GCM authenticated encryption. Optional PIN hashes and optional Market API keys are stored strictly in EncryptedSharedPreferences."
                )
            }
        }

        // Exact Network Transparency Card
        item {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.privacy_network_title),
                    badge = "GET ONLY",
                    accentColor = PrincipalEmerald
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No user financial data ever leaves your device. When you opt into online refresh, LoanJB only performs read-only HTTPS GET requests to fetch public reference data:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                NetworkEndpointRow(
                    title = "1. Public RemoteConfig JSONs (GitHub Raw / HTTPS GET)",
                    detail = "Fetches rates.json, taxslabs.json, schemes.json, and articles.json to update RBI repo rate, tax slabs, small-savings rates, and financial guides."
                )
                Spacer(Modifier.height(8.dp))
                NetworkEndpointRow(
                    title = "2. AMFI India Mutual Fund NAV Feed (HTTPS GET)",
                    detail = "Streams portal.amfiindia.com/spages/NAVAll.txt only when you tap Sync AMFI NAV inside Live NAV."
                )
                Spacer(Modifier.height(8.dp))
                NetworkEndpointRow(
                    title = "3. Optional User-Keyed Market APIs (HTTPS GET)",
                    detail = "Called only if you explicitly enter your own API key in Data & Rates for FX, Bullion, or Indices. Turn off 'Use Online Data' anytime for 100% airplane-mode operation."
                )
            }
        }

        item {
            FintechCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = TealPrimaryDark)
                    Text(
                        stringResource(R.string.disclaimer_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showPinDialog) {
        SetAppPinDialog(
            onDismiss = { showPinDialog = false },
            onSavePin = { pin ->
                val result = onSetPin(pin)
                result.onSuccess {
                    showPinDialog = false
                    onToggleAppLock(true)
                    onShowMessage("App PIN saved & App Lock enabled")
                }
                result
            }
        )
    }
}

@Composable
private fun PrivacyBulletRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TealPrimaryDark,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NetworkEndpointRow(
    title: String,
    detail: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SetAppPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Result<Unit>
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set App Lock PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter a 4 to 6 digit numeric PIN. Your PIN is hashed with salted PBKDF2-SHA256 in Android EncryptedSharedPreferences and never stored in plain text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6); error = null },
                    label = { Text("New PIN (4–6 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(6); error = null },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length !in 4..6) {
                        error = "PIN must be 4 to 6 digits."
                    } else if (pin != confirmPin) {
                        error = "PINs do not match."
                    } else {
                        onSavePin(pin).onFailure { error = it.message ?: "Could not save PIN" }
                    }
                }
            ) {
                Text("Save PIN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

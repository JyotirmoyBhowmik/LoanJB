package com.jyotirmoyb.loan.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ConfigCategory
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import com.jyotirmoyb.loan.ui.theme.LocalIsDarkTheme

private val AmberLightText = Color(0xFFB45309)
private val AmberDarkText = Color(0xFFFBBF24)

@Composable
fun ValueSourceBadgesRow(item: ResolvedConfigItem, modifier: Modifier = Modifier) {
    ValueSourceBadgesRow(
        source = item.source,
        asOfDate = item.asOfDate,
        isUnverified = item.isUnverified,
        modifier = modifier,
        sourceName = item.sourceName,
        sourceUrl = item.sourceUrl,
        effectiveFrom = item.effectiveFrom,
        lastVerified = item.lastVerified
    )
}

/**
 * Compact, single-line source & verification caption rendered beneath values across all screens.
 *
 * - Strictly 1 line (`maxLines = 1`, `softWrap = false`, ellipsis as last resort), 11sp `labelSmall`.
 * - Verified: `"Online · 27 Nov 26 · ✓ Checked 27 Nov 26"` in muted grey.
 * - Unverified: `"Built-in · 25 Nov 26 · ⚠ Not yet checked"` with amber text (no red pill).
 * - Tapping opens a bottom sheet with source details, tappable HTTPS link, dates, and legend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueSourceBadgesRow(
    source: ConfigValueSource,
    asOfDate: String,
    isUnverified: Boolean,
    modifier: Modifier = Modifier,
    sourceName: String = "",
    sourceUrl: String = "",
    effectiveFrom: String = "",
    lastVerified: String = "",
    customSourceLabel: String? = null
) {
    var showSheet by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current
    val amberColor = if (isDark) AmberDarkText else AmberLightText
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val sourceLabel = customSourceLabel?.takeIf { it.isNotBlank() } ?: when (source) {
        ConfigValueSource.MANUAL -> stringResource(R.string.source_manual)
        ConfigValueSource.IMPORTED -> stringResource(R.string.source_imported)
        ConfigValueSource.ONLINE -> stringResource(R.string.source_online)
        ConfigValueSource.BUILT_IN -> stringResource(R.string.source_built_in)
    }

    val compactAsOf = remember(asOfDate) { DataSourceResolver.formatToCompactDate(asOfDate) }
    val compactVerified = remember(lastVerified, asOfDate) {
        val cleaned = lastVerified.trim()
        when {
            cleaned.isEmpty() || cleaned.equals(UNVERIFIED_MARKER, ignoreCase = true) -> ""
            cleaned.equals("Manual", ignoreCase = true) -> compactAsOf
            else -> DataSourceResolver.formatToCompactDate(cleaned)
        }
    }

    val sourceDatePart = if (compactAsOf.isNotBlank()) {
        "$sourceLabel · $compactAsOf"
    } else {
        sourceLabel
    }

    val verificationLabel = if (isUnverified) {
        stringResource(R.string.badge_not_yet_checked)
    } else {
        val checkedDate = compactVerified.ifBlank { compactAsOf }
        if (checkedDate.isNotBlank()) {
            stringResource(R.string.badge_checked_date, checkedDate)
        } else {
            ""
        }
    }

    val lineText = buildAnnotatedString {
        withStyle(SpanStyle(color = mutedColor, fontWeight = FontWeight.Medium)) {
            append(sourceDatePart)
        }
        if (verificationLabel.isNotBlank()) {
            withStyle(SpanStyle(color = mutedColor)) {
                append(" · ")
            }
            withStyle(
                SpanStyle(
                    color = if (isUnverified) amberColor else mutedColor,
                    fontWeight = if (isUnverified) FontWeight.SemiBold else FontWeight.Normal
                )
            ) {
                append(verificationLabel)
            }
        }
    }

    Text(
        text = lineText,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable(role = Role.Button) { showSheet = true }
            .padding(vertical = 2.dp)
            .testTag("compact_verification_badge")
    )

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DataSourceDetailsSheetContent(
                sourceLabel = sourceLabel,
                sourceName = sourceName.ifBlank { sourceLabel },
                sourceUrl = sourceUrl,
                effectiveFrom = DataSourceResolver.formatToCompactDate(effectiveFrom),
                verifiedLabel = verificationLabel.ifBlank { compactAsOf },
                isUnverified = isUnverified,
                amberColor = amberColor,
                onDismiss = { showSheet = false }
            )
        }
    }
}

@Composable
private fun DataSourceDetailsSheetContent(
    sourceLabel: String,
    sourceName: String,
    sourceUrl: String,
    effectiveFrom: String,
    verifiedLabel: String,
    isUnverified: Boolean,
    amberColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasValidHttpsUrl = RemoteConfigValidator.isValidHttpsUrl(sourceUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("source_verification_bottom_sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.source_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SheetMetaRow(
            label = stringResource(R.string.source_sheet_origin),
            value = sourceLabel
        )
        SheetMetaRow(
            label = stringResource(R.string.source_sheet_source_label),
            value = sourceName
        )
        if (effectiveFrom.isNotBlank()) {
            SheetMetaRow(
                label = stringResource(R.string.source_sheet_effective_from),
                value = effectiveFrom
            )
        }
        if (verifiedLabel.isNotBlank()) {
            SheetMetaRow(
                label = stringResource(R.string.source_sheet_last_verified),
                value = verifiedLabel,
                valueColor = if (isUnverified) amberColor else MaterialTheme.colorScheme.onSurface
            )
        }

        if (hasValidHttpsUrl) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.source_sheet_official_link),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl.trim()))
                                context.startActivity(intent)
                            }
                        }
                        .padding(vertical = 4.dp)
                        .testTag("source_sheet_url_link")
                ) {
                    Text(
                        text = sourceUrl.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.source_sheet_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.source_sheet_close), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetMetaRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun ResetToDefaultAction(visible: Boolean, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    if (!visible) return
    TextButton(
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Rounded.RestartAlt,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.reset_to_default),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Preview(name = "Compact Badges Light", showBackground = true)
@Composable
private fun CompactVerificationBadgesLightPreview() {
    LoanJBTheme(themeMode = AppThemeMode.LIGHT) {
        CompactVerificationBadgesPreviewBody()
    }
}

@Preview(name = "Compact Badges Dark", showBackground = true)
@Composable
private fun CompactVerificationBadgesDarkPreview() {
    LoanJBTheme(themeMode = AppThemeMode.DARK) {
        CompactVerificationBadgesPreviewBody()
    }
}

@Composable
private fun CompactVerificationBadgesPreviewBody() {
    val verifiedItem = ResolvedConfigItem(
        key = "market.rbi_repo_rate",
        label = "RBI Policy Repo Rate",
        category = ConfigCategory.MARKET_DATA,
        fileName = "rates.json",
        valueText = "5.5",
        numericValue = 5.5,
        unit = "% p.a.",
        effectiveFrom = "2026-10-07",
        asOfDate = "27 Nov 2026",
        sourceName = "Reserve Bank of India",
        sourceUrl = "https://www.rbi.org.in",
        lastVerified = "2026-11-27",
        source = ConfigValueSource.ONLINE,
        defaultValueText = "5.5",
        defaultNumericValue = 5.5
    )
    val unverifiedItem = verifiedItem.copy(
        key = "loan.home.rate_min",
        label = "Home Loan Min Rate",
        valueText = "8.35",
        numericValue = 8.35,
        asOfDate = "25 Nov 2026",
        lastVerified = UNVERIFIED_MARKER,
        source = ConfigValueSource.BUILT_IN
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FintechCard {
            Text(verifiedItem.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ui_txt_fmt_dfac19, verifiedItem.valueText, verifiedItem.unit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ValueSourceBadgesRow(item = verifiedItem)
        }
        FintechCard {
            Text(unverifiedItem.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.ui_txt_fmt_dfac19, unverifiedItem.valueText, unverifiedItem.unit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ValueSourceBadgesRow(item = unverifiedItem)
        }
    }
}

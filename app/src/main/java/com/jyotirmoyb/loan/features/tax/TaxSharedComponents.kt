package com.jyotirmoyb.loan.features.tax

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.TaxRulesCatalog
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow

/**
 * Visible footer shown on every Taxation screen displaying the active Financial Year / date,
 * source, last verified status, and a one-tap "Check for updated tax rules" action.
 */
@Composable
fun TaxRulesMetadataFooter(
    catalog: TaxRulesCatalog,
    activeFyCode: String? = null,
    lawReference: String? = null,
    isCheckingUpdates: Boolean,
    syncFeedback: String?,
    onCheckForUpdatedTaxRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fyDisplay = activeFyCode?.takeIf { it.isNotBlank() }
        ?: catalog.taxYears.firstOrNull()?.fyCode
        ?: catalog.asOfDateFormatted

    val isUnverified = catalog.source != ConfigValueSource.MANUAL &&
        catalog.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tax_rules_metadata_footer"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.tax_rules_footer_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ValueSourceBadgesRow(
                source = catalog.source,
                asOfDate = catalog.asOfDateFormatted,
                isUnverified = isUnverified
            )

            Text(
                text = stringResource(
                    R.string.tax_rules_as_of,
                    fyDisplay,
                    catalog.asOfDateFormatted,
                    catalog.sourceName,
                    catalog.lastVerified
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!lawReference.isNullOrBlank()) {
                Text(
                    text = lawReference,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!syncFeedback.isNullOrBlank()) {
                Text(
                    text = syncFeedback,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("tax_sync_feedback_text")
                )
            }

            Spacer(Modifier.height(2.dp))

            FilledTonalButton(
                onClick = onCheckForUpdatedTaxRules,
                enabled = !isCheckingUpdates,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_check_updates_btn")
            ) {
                if (isCheckingUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tax_checking_updates))
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.tax_check_updates_btn),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

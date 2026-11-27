package com.jyotirmoyb.loan.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme

/**
 * Centralized spacing tokens following an 8dp baseline grid and WCAG 48dp minimum touch targets.
 */
object LoanJBSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val screenHorizontal: Dp = 16.dp
    val cardInnerPadding: Dp = 16.dp
    val minTouchTarget: Dp = 48.dp
}

/**
 * Centralized elevation tokens for Material 3 surfaces and cards.
 */
object LoanJBElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
}

/**
 * Consistent accessible loading state used across data-fetching or background calculation views.
 */
@Composable
fun LoanJBLoadingState(message: String = "Loading financial data…", modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = LoanJBElevation.level1,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            }
            .testTag("ds_loading_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanJBSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LoanJBSpacing.md)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Consistent accessible empty state with optional primary CTA button meeting 48dp touch target.
 */
@Composable
fun LoanJBEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = LoanJBElevation.level1,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ds_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanJBSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(LoanJBSpacing.xs))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = LoanJBSpacing.minTouchTarget)
                        .testTag("ds_empty_state_action_btn")
                ) {
                    Text(
                        text = actionLabel,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Consistent accessible error state with retry affordance.
 */
@Composable
fun LoanJBErrorState(
    title: String = "Unable to complete action",
    message: String,
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .testTag("ds_error_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanJBSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = LoanJBSpacing.minTouchTarget)
                        .testTag("ds_error_retry_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(LoanJBSpacing.xs))
                    Text(retryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Mandatory regulatory & compliance banner displayed on every calculator screen.
 * Scales cleanly up to 200% font size and is RTL-safe.
 */
@Composable
fun FinancialDisclaimerBanner(
    text: String = stringResource(R.string.disclaimer_text),
    onLearnMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("calculator_disclaimer_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LoanJBSpacing.md, vertical = LoanJBSpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(LoanJBSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.Gavel,
                contentDescription = "Financial disclaimer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onLearnMoreClick != null) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        OutlinedButton(
                            onClick = onLearnMoreClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("disclaimer_learn_more_btn")
                        ) {
                            Text(
                                text = stringResource(R.string.ui_txt_about_terms_sources),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Design System — Light", showBackground = true)
@Composable
private fun LoanJBDesignSystemLightPreview() {
    LoanJBTheme(themeMode = AppThemeMode.LIGHT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoanJBLoadingState(message = "Refreshing RBI & India Post rates…")
            LoanJBEmptyState(
                title = "No Saved Loans Yet",
                subtitle = "Calculate any EMI and tap Save to track prepayments and closure steps.",
                actionLabel = "Open EMI Calculator",
                onAction = {}
            )
            LoanJBErrorState(
                title = "Live Rate Sync Paused",
                message = "Using cached offline rates as of Q3 FY 2025-26.",
                onRetry = {}
            )
            FinancialDisclaimerBanner()
        }
    }
}

@Preview(name = "Design System — Dark & 200% Font Scale", showBackground = true, fontScale = 2.0f)
@Composable
private fun LoanJBDesignSystemDarkLargeFontPreview() {
    LoanJBTheme(themeMode = AppThemeMode.DARK) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoanJBEmptyState(
                title = "No Saved Loans",
                subtitle = "Supports dynamic font scaling up to 200% without clipping.",
                actionLabel = "Calculate EMI",
                onAction = {}
            )
            FinancialDisclaimerBanner()
        }
    }
}

package com.jyotirmoyb.loan.features.articles

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.ArticleCategory
import com.jyotirmoyb.loan.domain.ArticleItem
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.FeesIndigo
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    uiState: ArticlesUiState,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (ArticleCategory?) -> Unit,
    onToggleBookmarkedOnly: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onCloseArticle: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onRefreshRemote: () -> Unit,
    buildShareText: (ArticleItem) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val openArticle = uiState.selectedArticle

    fun shareArticle(article: ArticleItem) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, article.title)
            putExtra(Intent.EXTRA_TEXT, buildShareText(article))
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share article"))
    }

    if (openArticle != null) {
        BackHandler { onCloseArticle() }
        ArticleReaderView(
            article = openArticle,
            onBack = onCloseArticle,
            onToggleBookmark = { onToggleBookmark(openArticle.id) },
            onShare = { shareArticle(openArticle) },
            modifier = modifier
        )
        return
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefreshRemote,
        modifier = modifier
            .fillMaxSize()
            .testTag("articles_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Search
            item {
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionAccentHeader(
                            title = stringResource(R.string.articles_title),
                            badge = "${uiState.allArticles.size} GUIDES",
                            accentColor = TealPrimaryDark,
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRefreshRemote,
                            modifier = Modifier.testTag("articles_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh_now),
                                tint = TealPrimaryDark
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.articles_library_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(stringResource(R.string.articles_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("articles_search_input")
                    )
                }
            }

            // Filter chips: Bookmarked + Categories
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.bookmarkedOnly,
                        onClick = onToggleBookmarkedOnly,
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.bookmarkedOnly) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("${stringResource(R.string.articles_saved)} (${uiState.bookmarkedCount})") },
                        modifier = Modifier.testTag("articles_filter_bookmarked")
                    )
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All (${uiState.allArticles.size})") },
                        modifier = Modifier.testTag("articles_cat_all")
                    )
                    ArticleCategory.entries.forEach { cat ->
                        val count = uiState.allArticles.count { it.category == cat }
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text("${cat.displayTitle} ($count)") },
                            modifier = Modifier.testTag("articles_cat_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            if (uiState.filteredArticles.isEmpty()) {
                item {
                    FintechCard {
                        Text(
                            text = stringResource(R.string.articles_no_results),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.articles_no_results_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.filteredArticles, key = { it.id }) { article ->
                    ArticleSummaryCard(
                        article = article,
                        onOpen = { onOpenArticle(article.id) },
                        onToggleBookmark = { onToggleBookmark(article.id) },
                        onShare = { shareArticle(article) }
                    )
                }
            }

            item {
                DisclaimerBanner()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ArticleSummaryCard(
    article: ArticleItem,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit
) {
    val accent = categoryAccentColor(article.category)
    FintechCard(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .testTag("article_card_${article.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = article.category.displayTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("article_bookmark_${article.id}")
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (article.isBookmarked) "Remove bookmark" else "Bookmark article",
                        tint = if (article.isBookmarked) InterestAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("article_share_${article.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share article",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = article.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${article.readingTimeMinutes} ${stringResource(R.string.articles_min_read)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = TealPrimaryDark,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${stringResource(R.string.articles_last_reviewed)}: ${article.lastReviewedFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArticleReaderView(
    article: ArticleItem,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryAccentColor(article.category)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("article_reader_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Reader Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("article_reader_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.articles_all_guides))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.testTag("article_reader_bookmark_btn")
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (article.isBookmarked) "Remove bookmark" else "Bookmark article",
                        tint = if (article.isBookmarked) InterestAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("article_reader_share_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share article",
                        tint = TealPrimaryDark
                    )
                }
            }
        }

        // Metadata Header Card
        FintechCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = article.category.displayTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "${article.readingTimeMinutes} ${stringResource(R.string.articles_min_read)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.articles_last_reviewed)}: ${article.lastReviewedFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TealPrimaryDark
                )
                Text(
                    text = article.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Native Markdown Article Body
        FintechCard {
            NativeMarkdownView(markdown = article.markdownBody)
        }

        DisclaimerBanner()
        Spacer(Modifier.height(16.dp))
    }
}

private fun categoryAccentColor(category: ArticleCategory) = when (category) {
    ArticleCategory.LOANS_EMI -> TealPrimaryDark
    ArticleCategory.PREPAYMENT -> SavingsGreen
    ArticleCategory.CREDIT_SCORE -> FeesIndigo
    ArticleCategory.TAX_SAVING -> InterestAmber
    ArticleCategory.MUTUAL_FUNDS_SIP -> PurpleAccent
    ArticleCategory.DEPOSITS_SCHEMES -> SageGreenDark
    ArticleCategory.INSURANCE_PLANNING -> TealPrimaryDark
}

package com.jyotirmoyb.loan.features.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.articles.ArticlesRepository
import com.jyotirmoyb.loan.domain.ArticleCategory
import com.jyotirmoyb.loan.domain.ArticleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArticlesUiState(
    val allArticles: List<ArticleItem> = emptyList(),
    val filteredArticles: List<ArticleItem> = emptyList(),
    val selectedCategory: ArticleCategory? = null,
    val bookmarkedOnly: Boolean = false,
    val searchQuery: String = "",
    val selectedArticleId: String? = null,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null
) {
    val selectedArticle: ArticleItem?
        get() = selectedArticleId?.let { id -> allArticles.firstOrNull { it.id == id } }

    val bookmarkedCount: Int
        get() = allArticles.count { it.isBookmarked }
}

class ArticlesViewModel(
    private val articlesRepository: ArticlesRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ArticleCategory?>(null)
    private val _bookmarkedOnly = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedArticleId = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ArticlesUiState> = combine(
        articlesRepository.articlesFlow,
        _selectedCategory,
        _bookmarkedOnly,
        _searchQuery,
        _selectedArticleId,
        _isRefreshing,
        _snackbar
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val all = args[0] as List<ArticleItem>
        val category = args[1] as ArticleCategory?
        val bookmarkedOnly = args[2] as Boolean
        val query = args[3] as String
        val openId = args[4] as String?
        val refreshing = args[5] as Boolean
        val snackbar = args[6] as String?

        val q = query.trim().lowercase()
        val filtered = all.filter { article ->
            val catOk = category == null || article.category == category
            val bmOk = !bookmarkedOnly || article.isBookmarked
            val searchOk = q.isEmpty() ||
                article.title.lowercase().contains(q) ||
                article.summary.lowercase().contains(q) ||
                article.category.displayTitle.lowercase().contains(q) ||
                article.tags.any { it.lowercase().contains(q) }
            catOk && bmOk && searchOk
        }

        ArticlesUiState(
            allArticles = all,
            filteredArticles = filtered,
            selectedCategory = category,
            bookmarkedOnly = bookmarkedOnly,
            searchQuery = query,
            selectedArticleId = openId,
            isRefreshing = refreshing,
            snackbarMessage = snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArticlesUiState())

    fun selectCategory(category: ArticleCategory?) {
        _selectedCategory.value = category
    }

    fun toggleBookmarkedOnly() {
        _bookmarkedOnly.value = !_bookmarkedOnly.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openArticle(articleId: String) {
        _selectedArticleId.value = articleId
    }

    fun closeArticle() {
        _selectedArticleId.value = null
    }

    fun toggleBookmark(articleId: String) {
        viewModelScope.launch {
            val saved = articlesRepository.toggleBookmark(articleId)
            _snackbar.value = if (saved) "Article bookmarked" else "Removed from bookmarks"
        }
    }

    fun refreshRemoteArticles() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _snackbar.value = articlesRepository.refreshFromRemote()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun buildShareText(article: ArticleItem): String {
        return buildString {
            appendLine(article.title)
            appendLine("Category: ${article.category.displayTitle} • ${article.readingTimeMinutes} min read")
            appendLine("Last reviewed: ${article.lastReviewedFormatted}")
            appendLine()
            appendLine(article.summary)
            appendLine()
            appendLine(article.markdownBody)
            appendLine()
            append("Shared via LoanJB — Offline Indian Finance & Loan Companion")
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    class Factory(private val articlesRepository: ArticlesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ArticlesViewModel(articlesRepository) as T
        }
    }
}

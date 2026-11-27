package com.jyotirmoyb.loan.features.mutualfund.nav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AmfiNavEntity
import com.jyotirmoyb.loan.data.AmfiNavSyncEntity
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.nav.AmfiNavSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveNavUiState(
    val searchQuery: String = "",
    val showFavouritesOnly: Boolean = false,
    val schemes: List<AmfiNavEntity> = emptyList(),
    val favouritesCount: Int = 0,
    val totalCachedCount: Int = 0,
    val syncMeta: AmfiNavSyncEntity? = null,
    val isSyncing: Boolean = false,
    val snackbarMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LiveNavViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val navRepository = AppContainer.provideAmfiNavRepository(appContext)

    private val _searchQuery = MutableStateFlow("")
    private val _showFavouritesOnly = MutableStateFlow(false)
    private val _snackbar = MutableStateFlow<String?>(null)

    private val searchResultsFlow = _searchQuery.flatMapLatest { query ->
        navRepository.searchSchemes(query)
    }

    val uiState: StateFlow<LiveNavUiState> = combine(
        _searchQuery,
        _showFavouritesOnly,
        searchResultsFlow,
        navRepository.favouritesFlow,
        navRepository.totalCountFlow,
        navRepository.syncMetaFlow,
        navRepository.isSyncing,
        _snackbar
    ) { arr ->
        val query = arr[0] as String
        val favOnly = arr[1] as Boolean

        @Suppress("UNCHECKED_CAST")
        val allMatches = arr[2] as List<AmfiNavEntity>

        @Suppress("UNCHECKED_CAST")
        val favs = arr[3] as List<AmfiNavEntity>
        val totalCount = arr[4] as Int
        val meta = arr[5] as AmfiNavSyncEntity?
        val syncing = arr[6] as Boolean
        val snack = arr[7] as String?

        val filteredList = if (favOnly) {
            if (query.isBlank()) {
                favs
            } else {
                favs.filter {
                    it.schemeName.contains(query, ignoreCase = true) ||
                        it.schemeCode.contains(query, ignoreCase = true) ||
                        it.isinGrowth.contains(query, ignoreCase = true)
                }
            }
        } else {
            allMatches
        }

        LiveNavUiState(
            searchQuery = query,
            showFavouritesOnly = favOnly,
            schemes = filteredList,
            favouritesCount = favs.size,
            totalCachedCount = totalCount,
            syncMeta = meta,
            isSyncing = syncing,
            snackbarMessage = snack
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveNavUiState())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowFavouritesOnly(favOnly: Boolean) {
        _showFavouritesOnly.value = favOnly
    }

    fun toggleFavourite(item: AmfiNavEntity) {
        viewModelScope.launch {
            navRepository.toggleFavourite(item.schemeCode, item.isFavourite)
        }
    }

    fun syncNow() {
        // Schedule daily background sync via WorkManager + trigger immediate foreground-visible streaming sync
        AmfiNavSyncWorker.scheduleDailySync(appContext)
        viewModelScope.launch {
            val outcome = navRepository.syncLiveNav()
            _snackbar.value = outcome.message
        }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }
}

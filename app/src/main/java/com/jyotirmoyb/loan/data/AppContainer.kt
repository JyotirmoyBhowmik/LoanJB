package com.jyotirmoyb.loan.data

import android.content.Context
import com.jyotirmoyb.loan.data.articles.ArticlesRepository
import com.jyotirmoyb.loan.data.market.MarketRepository
import com.jyotirmoyb.loan.data.market.SecureApiKeyStore
import com.jyotirmoyb.loan.data.nav.AmfiNavRepository
import com.jyotirmoyb.loan.data.remote.AssetSeedConfigProvider
import com.jyotirmoyb.loan.data.remote.RatesRepository

object AppContainer {
    @Volatile
    private var prefsRepository: UserPreferencesRepository? = null

    @Volatile
    private var repository: LoanRepository? = null

    @Volatile
    private var ratesRepository: RatesRepository? = null

    @Volatile
    private var amfiNavRepository: AmfiNavRepository? = null

    @Volatile
    private var secureApiKeyStore: SecureApiKeyStore? = null

    @Volatile
    private var marketRepository: MarketRepository? = null

    @Volatile
    private var articlesRepository: ArticlesRepository? = null

    @Volatile
    private var appLockManager: com.jyotirmoyb.loan.data.security.AppLockManager? = null

    fun provideAppLockManager(context: Context): com.jyotirmoyb.loan.data.security.AppLockManager {
        return appLockManager ?: synchronized(this) {
            appLockManager ?: com.jyotirmoyb.loan.data.security.AppLockManager(context.applicationContext).also {
                appLockManager = it
            }
        }
    }

    fun providePreferencesRepository(context: Context): UserPreferencesRepository {
        return prefsRepository ?: synchronized(this) {
            prefsRepository ?: UserPreferencesRepository(context.applicationContext).also {
                prefsRepository = it
            }
        }
    }

    fun provideRepository(context: Context): LoanRepository {
        return repository ?: synchronized(this) {
            repository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                val prefs = providePreferencesRepository(appContext)
                LoanRepository(appContext, db.loanDao(), prefs)
            }.also { repository = it }
        }
    }

    fun provideRatesRepository(context: Context): RatesRepository {
        return ratesRepository ?: synchronized(this) {
            ratesRepository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                val prefs = providePreferencesRepository(appContext)
                RatesRepository(
                    remoteConfigDao = db.remoteConfigDao(),
                    prefsRepo = prefs,
                    seedProvider = AssetSeedConfigProvider(appContext)
                )
            }.also { ratesRepository = it }
        }
    }

    fun provideAmfiNavRepository(context: Context): AmfiNavRepository {
        return amfiNavRepository ?: synchronized(this) {
            amfiNavRepository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                AmfiNavRepository(db.amfiNavDao())
            }.also { amfiNavRepository = it }
        }
    }

    fun provideSecureApiKeyStore(context: Context): SecureApiKeyStore {
        return secureApiKeyStore ?: synchronized(this) {
            secureApiKeyStore ?: SecureApiKeyStore(context.applicationContext).also {
                secureApiKeyStore = it
            }
        }
    }

    fun provideMarketRepository(context: Context): MarketRepository {
        return marketRepository ?: synchronized(this) {
            marketRepository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                MarketRepository(
                    marketDao = db.marketDao(),
                    ratesRepository = provideRatesRepository(appContext),
                    prefsRepo = providePreferencesRepository(appContext),
                    apiKeyStore = provideSecureApiKeyStore(appContext)
                )
            }.also { marketRepository = it }
        }
    }

    fun provideArticlesRepository(context: Context): ArticlesRepository {
        return articlesRepository ?: synchronized(this) {
            articlesRepository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                ArticlesRepository(
                    context = appContext,
                    articlesDao = db.articlesDao(),
                    ratesRepository = provideRatesRepository(appContext)
                )
            }.also { articlesRepository = it }
        }
    }
}


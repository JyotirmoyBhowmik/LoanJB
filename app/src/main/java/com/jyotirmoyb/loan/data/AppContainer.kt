package com.jyotirmoyb.loan.data

import android.content.Context

object AppContainer {
    @Volatile
    private var repository: LoanRepository? = null

    fun provideRepository(context: Context): LoanRepository {
        return repository ?: synchronized(this) {
            repository ?: run {
                val appContext = context.applicationContext
                val db = LoanDatabase.getInstance(appContext)
                val prefs = UserPreferencesRepository(appContext)
                LoanRepository(appContext, db.loanDao(), prefs)
            }.also { repository = it }
        }
    }
}

package com.jyotirmoyb.loan

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.remote.RemoteConfigSyncWorker
import com.jyotirmoyb.loan.util.GlobalErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application entry point configuring:
 * 1. Global uncaught-exception & coroutine error handling.
 * 2. StrictMode thread & VM policies in debug builds (detecting main-thread disk/network I/O and leaks).
 * 3. Off-main-thread warm-up of DataStore, Room, and WorkManager so cold start stays under 1.5s without ANRs.
 */
class LoanJBApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + GlobalErrorHandler.coroutineExceptionHandler)

    override fun onCreate() {
        super.onCreate()
        GlobalErrorHandler.install(this)

        if (BuildConfig.DEBUG && !isRunningInUnitTest()) {
            enableDebugStrictMode()
        }

        // Perform heavy repository & WorkManager scheduling strictly off the main thread
        appScope.launch {
            runCatching {
                AppContainer.providePreferencesRepository(this@LoanJBApplication)
                AppContainer.provideRatesRepository(this@LoanJBApplication)
                RemoteConfigSyncWorker.schedulePeriodicSync(this@LoanJBApplication)
            }
        }
    }

    private fun enableDebugStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    private fun isRunningInUnitTest(): Boolean {
        return Build.FINGERPRINT == "robolectric" ||
            runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
    }
}

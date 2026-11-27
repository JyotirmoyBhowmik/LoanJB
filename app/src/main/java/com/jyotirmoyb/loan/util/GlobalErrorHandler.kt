package com.jyotirmoyb.loan.util

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Global crash and coroutine exception handler that records a PII-redacted diagnostic summary
 * locally on the device (never transmitted over the network) and prevents unhandled background
 * coroutine exceptions from crashing the UI process.
 */
object GlobalErrorHandler {

    private const val TAG = "LoanJBErrorHandler"
    private const val PREFS_NAME = "loanjb_crash_diagnostics"
    private const val KEY_LAST_CRASH = "last_crash_summary"

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Caught background coroutine exception: ${throwable::class.java.simpleName}", throwable)
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (previousHandler is LoanJBUncaughtExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            LoanJBUncaughtExceptionHandler(appContext, previousHandler)
        )
    }

    fun getLastCrashSummary(context: Context): String? = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }.getOrNull()

    fun clearLastCrashSummary(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_CRASH)
                .apply()
        }
    }

    internal fun sanitizeCrashSummary(
        threadName: String,
        throwable: Throwable,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMillis))
        val exName = throwable::class.java.name
        val topFrame = throwable.stackTrace.firstOrNull {
            it.className.startsWith("com.jyotirmoyb.loan")
        } ?: throwable.stackTrace.firstOrNull()
        val location = topFrame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "unknown"
        // Redact any sequence of 3+ digits in message to ensure zero financial amount or PII is ever logged
        val safeMsg = (throwable.message ?: "no-message")
            .replace(Regex("\\d{3,}"), "[REDACTED]")
            .take(120)
        return "[$dateStr] Thread=$threadName Exception=$exName at $location ($safeMsg)"
    }

    private class LoanJBUncaughtExceptionHandler(private val appContext: Context, private val delegate: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching {
                val summary = sanitizeCrashSummary(thread.name, throwable)
                appContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, summary)
                    .commit()
            }
            delegate?.uncaughtException(thread, throwable)
        }
    }
}

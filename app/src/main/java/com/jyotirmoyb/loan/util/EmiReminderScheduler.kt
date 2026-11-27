package com.jyotirmoyb.loan.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jyotirmoyb.loan.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class ReminderScheduleMode {
    EXACT_ALARM,
    INEXACT_FALLBACK
}

class EmiReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val loanId = inputData.getLong(EmiReminderReceiver.EXTRA_LOAN_ID, 0L)
        val title = inputData.getString(EmiReminderReceiver.EXTRA_TITLE)
            ?: applicationContext.getString(R.string.notif_emi_default_title)
        val body = inputData.getString(EmiReminderReceiver.EXTRA_BODY)
            ?: applicationContext.getString(R.string.notif_emi_default_body)
        EmiReminderReceiver.showNotification(applicationContext, loanId, title, body)
        return Result.success()
    }
}

/**
 * Schedules local EMI due-date notifications with Google Play-compliant exact-alarm fallback:
 * - On Android < 12 or when exact alarms are permitted, uses setExactAndAllowWhileIdle().
 * - On Android 12+ when exact alarm access is not granted, falls back cleanly to an inexact
 *   setAndAllowWhileIdle() alarm paired with a WorkManager OneTimeWorkRequest (no restricted
 *   USE_EXACT_ALARM permission required).
 */
object EmiReminderScheduler {

    fun scheduleNextReminder(
        context: Context,
        loanId: Long,
        nickname: String,
        emiFormatted: String,
        dueYear: Int,
        dueMonthOneBased: Int,
        dueDay: Int,
        daysBefore: Int,
        autoDebitNach: Boolean = false
    ): ReminderScheduleMode {
        EmiReminderReceiver.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, (dueMonthOneBased - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysBefore.coerceIn(0, 14))
        }
        val now = System.currentTimeMillis()
        val triggerMillis = maxOf(now + 5_000L, cal.timeInMillis)
        val delayMillis = maxOf(5_000L, triggerMillis - now)

        val title = if (autoDebitNach) {
            context.getString(R.string.notif_nach_title, nickname)
        } else {
            context.getString(R.string.notif_emi_due_title, nickname)
        }
        val body = if (autoDebitNach) {
            context.getString(R.string.notif_nach_body, emiFormatted, daysBefore)
        } else {
            context.getString(R.string.notif_emi_due_body, emiFormatted, daysBefore)
        }

        val pendingIntent = buildPendingIntent(
            context = context,
            loanId = loanId,
            title = title,
            body = body
        )

        val canScheduleExact = alarmManager != null &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                )

        return if (canScheduleExact && alarmManager != null) {
            cancelFallbackWork(context, loanId)
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                ReminderScheduleMode.EXACT_ALARM
            }.getOrElse {
                scheduleInexactFallback(context, alarmManager, loanId, triggerMillis, delayMillis, title, body, pendingIntent)
                ReminderScheduleMode.INEXACT_FALLBACK
            }
        } else {
            scheduleInexactFallback(context, alarmManager, loanId, triggerMillis, delayMillis, title, body, pendingIntent)
            ReminderScheduleMode.INEXACT_FALLBACK
        }
    }

    fun cancelReminder(context: Context, loanId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pendingIntent = buildPendingIntent(context, loanId, "", "")
        alarmManager?.cancel(pendingIntent)
        cancelFallbackWork(context, loanId)
    }

    private fun scheduleInexactFallback(
        context: Context,
        alarmManager: AlarmManager?,
        loanId: Long,
        triggerMillis: Long,
        delayMillis: Long,
        title: String,
        body: String,
        pendingIntent: PendingIntent
    ) {
        // Use inexact alarm if available, plus WorkManager one-time fallback for Doze reliability
        runCatching {
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
        runCatching {
            val data = Data.Builder()
                .putLong(EmiReminderReceiver.EXTRA_LOAN_ID, loanId)
                .putString(EmiReminderReceiver.EXTRA_TITLE, title)
                .putString(EmiReminderReceiver.EXTRA_BODY, body)
                .build()
            val request = OneTimeWorkRequestBuilder<EmiReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(loanId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun cancelFallbackWork(context: Context, loanId: Long) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(loanId))
        }
    }

    private fun uniqueWorkName(loanId: Long): String = "loanjb_emi_reminder_$loanId"

    private fun buildPendingIntent(context: Context, loanId: Long, title: String, body: String): PendingIntent {
        val intent = Intent(context, EmiReminderReceiver::class.java).apply {
            putExtra(EmiReminderReceiver.EXTRA_LOAN_ID, loanId)
            putExtra(EmiReminderReceiver.EXTRA_TITLE, title)
            putExtra(EmiReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            (loanId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

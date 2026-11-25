package com.jyotirmoyb.loan.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object EmiReminderScheduler {

    fun scheduleNextReminder(
        context: Context,
        loanId: Long,
        nickname: String,
        emiFormatted: String,
        dueYear: Int,
        dueMonthOneBased: Int,
        dueDay: Int,
        daysBefore: Int
    ) {
        EmiReminderReceiver.ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, (dueMonthOneBased - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysBefore.coerceIn(0, 14))
        }
        val triggerMillis = maxOf(System.currentTimeMillis() + 5_000L, cal.timeInMillis)

        val pendingIntent = buildPendingIntent(
            context = context,
            loanId = loanId,
            title = "EMI Due Soon: $nickname",
            body = "Upcoming EMI of $emiFormatted is due in $daysBefore day(s). Open LoanJB to mark it paid."
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun cancelReminder(context: Context, loanId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, loanId, "", "")
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(
        context: Context,
        loanId: Long,
        title: String,
        body: String
    ): PendingIntent {
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

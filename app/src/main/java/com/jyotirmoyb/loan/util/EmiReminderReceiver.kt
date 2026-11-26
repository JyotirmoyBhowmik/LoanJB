package com.jyotirmoyb.loan.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jyotirmoyb.loan.MainActivity

class EmiReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val loanId = intent.getLongExtra(EXTRA_LOAN_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "LoanJB EMI Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Your upcoming loan EMI is due soon."
        showNotification(context, loanId, title, body)
    }

    companion object {
        const val CHANNEL_ID = "loanjb_emi_reminders"
        const val EXTRA_LOAN_ID = "extra_loan_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"

        fun showNotification(context: Context, loanId: Long, title: String, body: String) {
            ensureChannel(context)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                (loanId % Int.MAX_VALUE).toInt(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            runCatching {
                NotificationManagerCompat.from(context).notify((loanId % Int.MAX_VALUE).toInt(), notification)
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "EMI Due Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Local reminders before your scheduled loan EMI due dates"
                }
                mgr?.createNotificationChannel(channel)
            }
        }
    }
}

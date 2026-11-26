package com.jyotirmoyb.loan.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jyotirmoyb.loan.MainActivity
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.LoanDatabase
import com.jyotirmoyb.loan.domain.LoanTrackerEngine
import com.jyotirmoyb.loan.domain.NextEmiWidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NextEmiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = NextEmiWidgetUpdater.loadWidgetData(context)
                appWidgetIds.forEach { widgetId ->
                    NextEmiWidgetUpdater.applyToWidget(context, appWidgetManager, widgetId, data)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object NextEmiWidgetUpdater {

    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, NextEmiWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val data = loadWidgetData(appContext)
                ids.forEach { id ->
                    applyToWidget(appContext, manager, id, data)
                }
            }
        }
    }

    internal suspend fun loadWidgetData(context: Context): NextEmiWidgetData {
        val appContext = context.applicationContext
        val db = LoanDatabase.getInstance(appContext)
        val repo = AppContainer.provideRepository(appContext)
        val prefs = AppContainer.providePreferencesRepository(appContext).preferencesFlow.first()
        val catalog = repo.catalogFlow.first()
        val entities = db.loanDao().getAllSavedLoansOnce()
        val summaries = entities.map { entity ->
            val input = repo.parseInput(entity.inputJson)
            val category = catalog.categories.firstOrNull { it.id == entity.categoryId }
            LoanTrackerEngine.evaluateTrackedLoan(entity, input, category)
        }
        return LoanTrackerEngine.selectNextEmiWidgetData(summaries, prefs.currency)
    }

    internal fun applyToWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        data: NextEmiWidgetData
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_emi)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        if (data.hasActiveLoan) {
            views.setTextViewText(R.id.widget_loan_title, "${data.nickname} • ${data.lender}")
            views.setTextViewText(R.id.widget_emi_amount, data.emiAmountFormatted)
            views.setTextViewText(R.id.widget_due_date, "Due ${data.dueDateLabel}")
            val badgeText = if (data.autoDebitNach) "NACH AUTO" else "MANUAL PAY"
            views.setTextViewText(R.id.widget_badge, badgeText)
            val footerText = if (data.activeLoanCount > 1) {
                "${data.activeLoanCount} loans • ${data.totalMonthlyBurdenFormatted}/mo"
            } else {
                "Day ${data.emiDayOfMonth} • Open LoanJB"
            }
            views.setTextViewText(R.id.widget_footer, footerText)
        } else {
            views.setTextViewText(R.id.widget_loan_title, context.getString(R.string.widget_no_loans_title))
            views.setTextViewText(R.id.widget_emi_amount, "—")
            views.setTextViewText(R.id.widget_due_date, context.getString(R.string.widget_no_loans_sub))
            views.setTextViewText(R.id.widget_badge, "OFFLINE")
            views.setTextViewText(R.id.widget_footer, "Tap to add")
        }

        manager.updateAppWidget(appWidgetId, views)
    }
}

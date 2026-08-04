package com.taskpluss.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskPlussWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Show cache immediately, then try network in background
        val cache = Prefs.run { context.loadCache() }
        WidgetRenderer.applyData(context, cache, appWidgetIds)

        CoroutineScope(Dispatchers.IO).launch {
            WidgetRenderer.fetchAndApply(context, appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        AlarmHelper.schedule(context)
    }

    override fun onDisabled(context: Context) {
        AlarmHelper.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            CoroutineScope(Dispatchers.IO).launch {
                WidgetRenderer.fetchAndApply(context)
            }
        }
    }
}

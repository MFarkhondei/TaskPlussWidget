package com.taskpluss.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class TaskPlussWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val cache = Prefs.loadCache(context)
        WidgetRenderer.applyData(context, cache, appWidgetIds)
        WidgetUpdateService.start(context)
    }

    override fun onEnabled(context: Context) {
        AlarmHelper.schedule(context)
    }

    override fun onDisabled(context: Context) {
        AlarmHelper.cancel(context)
    }
}

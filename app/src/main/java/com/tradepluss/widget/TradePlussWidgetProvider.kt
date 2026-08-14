package com.tradepluss.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class TradePlussWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.tradepluss.widget.ACTION_REFRESH"
        const val ACTION_AUTO_UPDATE = "com.tradepluss.widget.ACTION_AUTO_UPDATE"

        fun updateAll(context: Context) {
            // Use Service so network runs with same privileges as Config
            WidgetUpdateService.start(context.applicationContext)
        }

        fun applyCachedToAll(context: Context) {
            WidgetRenderer.applyCache(context, offline = false)
        }
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancel(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        UpdateScheduler.schedule(context)
        // Show cache immediately, then refresh via Service (Config-like path)
        for (id in appWidgetIds) {
            if (!Prefs.isConfigured(context)) {
                WidgetRenderer.showNotConfigured(context, id)
            } else {
                WidgetRenderer.applyCache(context, offline = true, widgetIds = intArrayOf(id))
            }
        }
        if (Prefs.isConfigured(context)) {
            WidgetUpdateService.start(context.applicationContext)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appCtx = context.applicationContext

        when (action) {
            ACTION_REFRESH -> {
                // Manual refresh from old PendingIntent (broadcast) → Service
                // New widgets use SilentRefreshActivity via PendingIntent.getActivity
                if (Prefs.isConfigured(appCtx)) {
                    WidgetRenderer.showLoading(appCtx)
                    WidgetUpdateService.start(appCtx)
                }
            }
            ACTION_AUTO_UPDATE -> {
                if (Prefs.isConfigured(appCtx)) {
                    WidgetUpdateService.start(appCtx)
                } else {
                    UpdateScheduler.reschedule(appCtx)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                if (Prefs.isConfigured(appCtx)) {
                    WidgetUpdateService.start(appCtx)
                }
            }
        }
    }
}

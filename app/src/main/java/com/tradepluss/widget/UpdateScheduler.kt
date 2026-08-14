package com.tradepluss.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object UpdateScheduler {

    private const val REQUEST_CODE = 9001

    fun schedule(context: Context) {
        val appCtx = context.applicationContext
        val minutes = Prefs.getIntervalMin(appCtx)
        cancel(appCtx)
        if (minutes <= 0) return
        if (!Prefs.isConfigured(appCtx)) return

        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(appCtx)
        val intervalMs = minutes * 60_000L
        val triggerAt = SystemClock.elapsedRealtime() + intervalMs

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (_: Exception) {
            try {
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    intervalMs,
                    pi
                )
            } catch (_: Exception) {
            }
        }
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(appCtx))
    }

    fun reschedule(context: Context) {
        schedule(context)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TradePlussWidgetProvider::class.java).apply {
            action = TradePlussWidgetProvider.ACTION_AUTO_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

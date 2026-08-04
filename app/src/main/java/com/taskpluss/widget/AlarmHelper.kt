package com.taskpluss.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object AlarmHelper {

    private const val REQ = 9001

    fun schedule(context: Context) {
        val minutes = Prefs.run { context.intervalMin }
        cancel(context)
        if (minutes <= 0) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val intervalMs = minutes * 60_000L
        val trigger = SystemClock.elapsedRealtime() + intervalMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    fun rescheduleAfterSuccess(context: Context) {
        schedule(context)
    }
}

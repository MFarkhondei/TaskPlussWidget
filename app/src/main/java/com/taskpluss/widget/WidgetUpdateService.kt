package com.taskpluss.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground Service برای به‌روزرسانی خودکار ویجت
 * همان WidgetRenderer.fetchAndApply مسیر تنظیمات
 */
class WidgetUpdateService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تسک پلاس")
            .setContentText("در حال به‌روزرسانی…")
            .setSmallIcon(R.drawable.ic_refresh)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(appCtx)
                AlarmHelper.rescheduleAfterSuccess(appCtx)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "به‌روزرسانی ویجت",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "به‌روزرسانی خودکار تسک‌ها"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "taskpluss_widget_update"
        private const val NOTIF_ID = 4201

        fun start(context: Context) {
            val i = Intent(context, WidgetUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}

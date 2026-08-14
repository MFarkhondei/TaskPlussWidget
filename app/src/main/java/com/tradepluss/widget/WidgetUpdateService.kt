package com.tradepluss.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Background/auto updates — same ApiClient + CoroutineScope path as ConfigActivity.
 */
class WidgetUpdateService : Service() {

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        WidgetRenderer.showLoading(this)

        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(this@WidgetUpdateService)
            } finally {
                UpdateScheduler.reschedule(this@WidgetUpdateService)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "widget_update"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "به‌روزرسانی ویجت",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("ترید پلاس")
                .setContentText("در حال بروزرسانی ویجت...")
                .setSmallIcon(R.drawable.ic_logo)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(42, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(42, notification)
            }
        } else {
            @Suppress("DEPRECATION")
            val notification = Notification.Builder(this)
                .setContentTitle("ترید پلاس")
                .setContentText("در حال بروزرسانی ویجت...")
                .setSmallIcon(R.drawable.ic_logo)
                .build()
            startForeground(42, notification)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

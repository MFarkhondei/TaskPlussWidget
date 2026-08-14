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
 * Foreground Service برای رفرش دستی و خودکار.
 * روی سامسونگ Activity شفاف به‌تنهایی DNS را resolve نمی‌کند.
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
            .setSilent(true)
            .build()
        startForeground(NOTIF_ID, notif)

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!NetworkUtils.waitUntilOnline(appCtx, 15_000L)) {
                    val prev = Prefs.loadCache(appCtx)
                    WidgetRenderer.applyData(
                        appCtx,
                        prev.copy(offline = true, updatedAt = "شبکه در دسترس نیست")
                    )
                    return@launch
                }
                try { Thread.sleep(300) } catch (_: InterruptedException) { }

                WidgetRenderer.fetchAndApply(appCtx)
                AlarmHelper.rescheduleAfterSuccess(appCtx)
            } catch (e: Exception) {
                val prev = Prefs.loadCache(appCtx)
                val msg = (e.message ?: "خطای شبکه").take(40)
                WidgetRenderer.applyData(
                    appCtx,
                    prev.copy(offline = true, updatedAt = msg)
                )
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
                description = "به‌روزرسانی تسک‌ها"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "taskpluss_widget_update"
        private const val NOTIF_ID = 4201

        fun start(context: Context) {
            startInternal(context)
        }

        fun startForceRefresh(context: Context) {
            startInternal(context)
        }

        private fun startInternal(context: Context) {
            val i = Intent(context, WidgetUpdateService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NetworkUtils.waitUntilOnline(context.applicationContext, 10_000L)
                        WidgetRenderer.fetchAndApply(context.applicationContext)
                    } catch (_: Exception) { }
                }
            }
        }
    }
}

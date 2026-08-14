package com.taskpluss.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * نوتیفیکیشن ثابت (persistent) در نوار اعلان‌ها که همیشه یک دکمهٔ
 * «افزودن تسک جدید» در خودش داره تا بدون باز کردن ویجت/اپ بشه تسک اضافه کرد،
 * و تعداد تسک‌های انجام‌نشده و در حال انجام رو نشون می‌ده.
 */
object QuickAddNotificationHelper {
    private const val CHANNEL_ID = "taskpluss_quick_add"
    private const val NOTIF_ID = 4210
    private const val REQ_ADD_TASK = 4211

    /** با توجه به تنظیم کاربر، نوتیف رو نشون میده یا مخفی می‌کنه. */
    fun refresh(context: Context) {
        if (Prefs.persistentNotifEnabled(context)) show(context) else hide(context)
    }

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)

        val cache = Prefs.loadCache(context)
        val todoCount = cache.tasks.count { it.status == "todo" }
        val doingCount = cache.tasks.count { it.status == "doing" }
        val contentText = "انجام‌نشده: $todoCount   •   در حال انجام: $doingCount"

        // با کلیک روی خودِ اعلان یا دکمه، مستقیماً فرم افزودن تسک باز می‌شود (نه تنظیمات).
        val addTaskIntent = Intent(context, AddTaskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addTaskPendingIntent = PendingIntent.getActivity(
            context, REQ_ADD_TASK, addTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("تسک پلاس")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_add)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(addTaskPendingIntent)
            .addAction(R.drawable.ic_add, "افزودن تسک جدید", addTaskPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "دسترسی سریع تسک پلاس",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نوتیفیکیشن ثابت برای افزودن سریع تسک جدید"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }
}

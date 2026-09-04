package com.taskpluss.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taskpluss.widget.model.TaskItem

/**
 * نوتیفیکیشن ثابت دسترسی سریع که با باز کردن آن، تسک‌های تاریخ امروز
 * در بخش بازشدهٔ اعلان دیده می‌شوند.
 */
object QuickAddNotificationHelper {
    private const val CHANNEL_ID = "taskpluss_quick_add"
    private const val NOTIF_ID = 4210
    private const val REQ_ADD_TASK = 4211

    fun refresh(context: Context) {
        if (Prefs.persistentNotifEnabled(context)) show(context) else hide(context)
    }

    fun show(context: Context) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val cache = Prefs.loadCache(context)
        val todayTasks = cache.tasks
            .filter { JalaliUtils.isToday(it.date) }
            .sortedWith(
                compareBy<TaskItem> {
                    val time = JalaliUtils.parseTime(it.date)
                    if (time == null) 24 * 60 else time.hour * 60 + time.minute
                }.thenBy { it.created }
            )
        val todoCount = cache.tasks.count { it.status == "todo" }
        val doingCount = cache.tasks.count { it.status == "doing" }
        val contentText = if (todayTasks.isEmpty()) {
            "امروز تسکی ثبت نشده است"
        } else {
            "امروز: ${todayTasks.size} تسک  •  انجام‌نشده: $todoCount"
        }

        val addTaskIntent = Intent(context, AddTaskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addTaskPendingIntent = PendingIntent.getActivity(
            context, REQ_ADD_TASK, addTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("تسک پلاس")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_add)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setNumber(todayTasks.size)
            .setContentIntent(addTaskPendingIntent)
            .addAction(R.drawable.ic_add, "افزودن تسک جدید", addTaskPendingIntent)

        if (todayTasks.isEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("برای امروز تسکی با تاریخ تعیین‌شده وجود ندارد.")
            )
        } else {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("تسک‌های امروز")
                .setSummaryText("تسک پلاس")
            todayTasks.forEach { task ->
                val marker = when (task.status) {
                    "done" -> "✓"
                    "doing" -> "◐"
                    else -> "○"
                }
                val time = JalaliUtils.formatTime(task.date)
                val title = task.title.replace(Regex("\\s+"), " ").trim()
                    .ifBlank { "بدون عنوان" }
                inbox.addLine(
                    listOfNotNull(time, "$marker $title")
                        .joinToString(" — ")
                        .take(120)
                )
            }
            builder.setStyle(inbox)
        }

        NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "دسترسی سریع تسک پلاس",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نوتیفیکیشن ثابت و فهرست تسک‌های امروز"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }
}

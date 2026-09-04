package com.taskpluss.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taskpluss.widget.model.TaskItem
import kotlin.math.abs

/**
 * نوتیفیکیشن ثابت دسترسی سریع که با باز کردن آن، تسک‌های تاریخ امروز
 * در بخش بازشدهٔ اعلان دیده می‌شوند.
 */
object QuickAddNotificationHelper {
    private const val CHANNEL_ID = "taskpluss_quick_add"
    private const val NOTIF_ID = 4210
    private const val REQ_ADD_TASK = 4211
    private const val REQ_EDIT_TASK_BASE = 50_000
    private const val MAX_VISIBLE_TASKS = 6

    private val taskRowIds = intArrayOf(
        R.id.notification_task_1,
        R.id.notification_task_2,
        R.id.notification_task_3,
        R.id.notification_task_4,
        R.id.notification_task_5,
        R.id.notification_task_6
    )

    fun refresh(context: Context) {
        if (Prefs.persistentNotifEnabled(context)) show(context) else hide(context)
    }

    fun show(context: Context) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val cache = Prefs.loadCache(context)
        val currentMinute = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("Asia/Tehran")
        ).let {
            it.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                it.get(java.util.Calendar.MINUTE)
        }
        val todayTasks = cache.tasks
            .filter {
                JalaliUtils.isToday(it.date) &&
                    (it.status == "todo" || it.status == "doing")
            }
            .sortedWith(
                compareBy<TaskItem> {
                    if (JalaliUtils.parseTime(it.date) == null) 1 else 0
                }.thenByDescending {
                    val time = JalaliUtils.parseTime(it.date)
                    if (time == null) Int.MIN_VALUE else abs(
                        time.hour * 60 + time.minute - currentMinute
                    )
                }.thenByDescending {
                    val time = JalaliUtils.parseTime(it.date)
                    if (time == null) Int.MIN_VALUE else time.hour * 60 + time.minute
                }
            )
        val unfinishedCount = cache.tasks.count {
            it.status == "todo" || it.status == "doing"
        }
        val countText = "انجام نشده: $unfinishedCount - امروز: ${todayTasks.size}"

        val addTaskPendingIntent = addTaskPendingIntent(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("تسک پلاس")
            .setContentText(countText)
            .setSmallIcon(R.drawable.ic_add)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setNumber(0)
            .setContentIntent(addTaskPendingIntent)
            .addAction(R.drawable.ic_add, "افزودن تسک جدید", addTaskPendingIntent)

        if (todayTasks.isEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("تسک پلاس")
                    .bigText("برای امروز تسکی وجود ندارد.")
            )
        } else {
            val expandedView = RemoteViews(
                context.packageName,
                R.layout.notification_today_expanded
            )
            taskRowIds.forEachIndexed { index, rowId ->
                val task = todayTasks.getOrNull(index)
                if (task == null) {
                    expandedView.setViewVisibility(rowId, View.GONE)
                } else {
                    expandedView.setViewVisibility(rowId, View.VISIBLE)
                    expandedView.setTextViewText(rowId, formatTaskLine(task, cache.groups))
                    expandedView.setOnClickPendingIntent(
                        rowId,
                        editTaskPendingIntent(context, task.id)
                    )
                }
            }
            expandedView.setViewVisibility(R.id.notification_task_count, View.GONE)
            expandedView.setOnClickPendingIntent(
                R.id.notification_today_root,
                addTaskPendingIntent
            )
            builder
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIF_ID)
        notificationManager.notify(NOTIF_ID, builder.build())
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun addTaskPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AddTaskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQ_ADD_TASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun editTaskPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, TaskFormActivity::class.java).apply {
            putExtra(TaskFormActivity.EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = REQ_EDIT_TASK_BASE + (taskId.hashCode() and 0x3fffffff)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTaskLine(
        task: TaskItem,
        groups: Map<String, com.taskpluss.widget.model.GroupItem>
    ): String {
        val time = JalaliUtils.formatTime(task.date)
        val title = task.title.replace(Regex("\\s+"), " ").trim()
            .ifBlank { "بدون عنوان" }
        val group = when {
            task.group.isBlank() || task.group == "none" -> "بدون گروه"
            else -> groups[task.group]?.name ?: task.group
        }
        return listOfNotNull(time, title, group)
            .joinToString(" - ")
            .take(120)
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

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
    private const val REQ_PREVIOUS_PAGE = 4212
    private const val REQ_NEXT_PAGE = 4213
    private const val REQ_EDIT_TASK_BASE = 50_000
    private const val TASKS_PER_PAGE = 6
    private const val PAGE_PREFS = "quick_add_notification"
    private const val PAGE_KEY = "today_page"

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

    fun movePage(context: Context, delta: Int) {
        val pagePrefs = context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE)
        val currentPage = pagePrefs.getInt(PAGE_KEY, 0)
        pagePrefs.edit().putInt(PAGE_KEY, currentPage + delta).apply()
        show(context)
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

        val totalPages = maxOf(1, (todayTasks.size + TASKS_PER_PAGE - 1) / TASKS_PER_PAGE)
        val pagePrefs = context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE)
        val storedPage = pagePrefs.getInt(PAGE_KEY, 0)
        val currentPage = storedPage.coerceIn(0, totalPages - 1)
        if (currentPage != storedPage) {
            pagePrefs.edit().putInt(PAGE_KEY, currentPage).apply()
        }
        val pageTasks = todayTasks
            .drop(currentPage * TASKS_PER_PAGE)
            .take(TASKS_PER_PAGE)

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
                val task = pageTasks.getOrNull(index)
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

            if (totalPages > 1) {
                expandedView.setViewVisibility(R.id.notification_paging, View.VISIBLE)
                expandedView.setTextViewText(
                    R.id.notification_page,
                    "صفحه ${currentPage + 1} از $totalPages"
                )

                val hasPrevious = currentPage > 0
                expandedView.setViewVisibility(
                    R.id.notification_previous,
                    if (hasPrevious) View.VISIBLE else View.INVISIBLE
                )
                if (hasPrevious) {
                    expandedView.setOnClickPendingIntent(
                        R.id.notification_previous,
                        pagePendingIntent(
                            context,
                            NotificationPageReceiver.ACTION_PREVIOUS_PAGE,
                            REQ_PREVIOUS_PAGE
                        )
                    )
                }

                val hasNext = currentPage < totalPages - 1
                expandedView.setViewVisibility(
                    R.id.notification_next,
                    if (hasNext) View.VISIBLE else View.INVISIBLE
                )
                if (hasNext) {
                    expandedView.setOnClickPendingIntent(
                        R.id.notification_next,
                        pagePendingIntent(
                            context,
                            NotificationPageReceiver.ACTION_NEXT_PAGE,
                            REQ_NEXT_PAGE
                        )
                    )
                }
            } else {
                expandedView.setViewVisibility(R.id.notification_paging, View.GONE)
            }

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

    private fun pagePendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationPageReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
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

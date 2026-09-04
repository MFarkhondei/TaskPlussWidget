package com.taskpluss.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.taskpluss.widget.model.TaskItem

object AlarmHelper {

    private const val WIDGET_REQ = 9001
    private const val TASK_REMINDER_REQ_BASE = 100_000
    private const val SCHEDULED_REMINDER_IDS = "scheduled_task_reminder_ids"

    fun schedule(context: Context) {
        val minutes = Prefs.intervalMin(context)
        cancel(context)
        if (minutes <= 0) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, WIDGET_REQ, intent,
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
            context, WIDGET_REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /**
     * زمان‌بندی اعلان هر تسک دارای تاریخ و ساعت.
     * با هر رفرش، اعلان‌های قبلی حذف و فقط تسک‌های جاریِ آینده دوباره ثبت می‌شوند.
     */
    fun syncTaskReminders(context: Context, tasks: List<TaskItem>) {
        val prefs = context.applicationContext.getSharedPreferences(
            "taskpluss_widget_prefs", Context.MODE_PRIVATE
        )
        val previousIds = prefs.getStringSet(SCHEDULED_REMINDER_IDS, emptySet()).orEmpty().toSet()
        previousIds.forEach { cancelTaskReminder(context, it) }

        val now = System.currentTimeMillis()
        val scheduledIds = mutableSetOf<String>()
        tasks.asSequence()
            .filter { it.id.isNotBlank() && it.status != "done" && it.status != "deleted" }
            .mapNotNull { task ->
                val triggerAt = JalaliUtils.toTehranMillis(task.date)
                if (triggerAt != null && triggerAt > now + 1_000L) task to triggerAt else null
            }
            .forEach { (task, triggerAt) ->
                scheduleTaskReminder(context, task.id, triggerAt)
                scheduledIds.add(task.id)
            }

        prefs.edit().putStringSet(SCHEDULED_REMINDER_IDS, scheduledIds).apply()
    }

    private fun scheduleTaskReminder(context: Context, taskId: String, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = taskReminderPendingIntent(context, taskId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                am.canScheduleExactAlarms()
            ) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pi
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // در صورت نداشتن مجوز Exact Alarm، نزدیک‌ترین زمان ممکن ثبت می‌شود.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (_: Exception) {
                // نبودن مجوز زمان‌بندی نباید باعث توقف رفرش ویجت شود.
            }
        }
    }

    private fun cancelTaskReminder(context: Context, taskId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(taskReminderPendingIntent(context, taskId))
    }

    private fun taskReminderPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskReminderRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun taskReminderRequestCode(taskId: String): Int {
        val hash = taskId.hashCode() and Int.MAX_VALUE
        return TASK_REMINDER_REQ_BASE + (hash % 900_000_000)
    }

    fun rescheduleAfterSuccess(context: Context) {
        schedule(context)
    }
}

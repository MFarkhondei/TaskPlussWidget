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

object TaskNotificationHelper {
    private const val CHANNEL_ID = "taskpluss_task_reminders"

    fun showReminder(context: Context, taskId: String) {
        if (taskId.isBlank() || !hasNotificationPermission(context)) return

        val task = Prefs.loadCache(context).tasks.find { it.id == taskId }
            ?: return
        if (task.status == "done" || task.status == "deleted") return

        ensureChannel(context)

        val editIntent = Intent(context, TaskFormActivity::class.java).apply {
            putExtra(TaskFormActivity.EXTRA_TASK_ID, task.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            notificationId(task.id),
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val time = JalaliUtils.formatTime(task.date)
        val taskTitle = task.title.replace(Regex("\\s+"), " ").trim()
            .ifBlank { "بدون عنوان" }
        val body = if (time == null) taskTitle else "$time — $taskTitle"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_add)
            .setContentTitle("یادآوری تسک")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(editPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(task.id), notification)
    }

    private fun notificationId(taskId: String): Int {
        val id = taskId.hashCode() and Int.MAX_VALUE
        return if (id == 0) 1 else id
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "یادآوری تسک‌ها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان در زمان تعیین‌شده برای تسک"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

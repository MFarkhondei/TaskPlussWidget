package com.taskpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TaskNotificationHelper.showReminder(
            context,
            intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}

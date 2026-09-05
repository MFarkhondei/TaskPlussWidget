package com.taskpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationPageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delta = when (intent.action) {
            ACTION_PREVIOUS_PAGE -> -1
            ACTION_NEXT_PAGE -> 1
            else -> return
        }
        QuickAddNotificationHelper.movePage(context, delta)
    }

    companion object {
        const val ACTION_PREVIOUS_PAGE =
            "com.taskpluss.widget.action.NOTIFICATION_PREVIOUS_PAGE"
        const val ACTION_NEXT_PAGE =
            "com.taskpluss.widget.action.NOTIFICATION_NEXT_PAGE"
    }
}

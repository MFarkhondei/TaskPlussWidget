package com.taskpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(context)
                AlarmHelper.rescheduleAfterSuccess(context)
            } finally {
                pending.finish()
            }
        }
    }
}

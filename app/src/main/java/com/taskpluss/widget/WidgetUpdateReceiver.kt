package com.taskpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** آلارم خودکار → Foreground Service با همان fetchAndApply */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdateService.start(context)
    }
}

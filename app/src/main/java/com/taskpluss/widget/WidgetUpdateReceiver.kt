package com.taskpluss.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** فقط Service را شروع می‌کند — شبکه داخل FGS */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdateService.start(context)
    }
}

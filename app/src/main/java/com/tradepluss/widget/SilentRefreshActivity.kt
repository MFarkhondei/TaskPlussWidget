package com.tradepluss.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent Activity launched when user taps refresh on the widget.
 * Uses the exact same CoroutineScope(Dispatchers.IO) + ApiClient path as ConfigActivity,
 * so network works the same way as "تست اتصال".
 */
class SilentRefreshActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val ids = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intArrayOf(widgetId)
        } else null

        WidgetRenderer.showLoading(this, ids)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(this@SilentRefreshActivity, ids)
            } finally {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}

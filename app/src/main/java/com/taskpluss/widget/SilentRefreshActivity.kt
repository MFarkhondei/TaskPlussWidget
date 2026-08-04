package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Transparent activity used for manual refresh from the widget.
 * Runs the same network path as ConfigActivity (no background network limits).
 */
class SilentRefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            WidgetRenderer.fetchAndApply(this@SilentRefreshActivity)
            finish()
        }
    }
}

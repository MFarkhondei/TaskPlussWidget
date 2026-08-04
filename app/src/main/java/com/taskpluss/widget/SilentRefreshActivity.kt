package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** رفرش دستی — همان IO + ApiClient مسیر تنظیمات */
class SilentRefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cache = Prefs.run { loadCache() }.copy(updatedAt = "در حال به‌روزرسانی…", offline = false)
        WidgetRenderer.applyData(this, cache)

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(appCtx)
                AlarmHelper.rescheduleAfterSuccess(appCtx)
            } finally {
                runOnUiThread {
                    finish()
                    moveTaskToBack(true)
                }
            }
        }
    }
}

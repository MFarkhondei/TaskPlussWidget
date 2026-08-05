package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle

/**
 * رفرش دستی از ویجت:
 * وضعیت «در حال به‌روزرسانی» را نشان می‌دهد و کار شبکه را
 * به Foreground Service می‌سپارد تا روی سامسونگ DNS قطع نشود.
 */
class SilentRefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val cache = Prefs.loadCache(this).copy(
                updatedAt = "در حال به‌روزرسانی…",
                offline = false
            )
            WidgetRenderer.applyData(this, cache)
        } catch (_: Exception) { }

        WidgetUpdateService.startForceRefresh(applicationContext)
        finish()
    }
}

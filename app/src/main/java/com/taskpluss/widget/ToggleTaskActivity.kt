package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToggleTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra("task_id") ?: run {
            finish(); return
        }
        val appCtx = applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val cache = Prefs.loadCache(appCtx)
            val task = cache.tasks.find { it.id == taskId }

            // به‌روزرسانی خوش‌بینانه و فوری — هیچ صبری برای شبکه نداره
            if (task != null) {
                val newStatus = if (task.status == "done") "todo" else "done"
                val optimistic = cache.copy(
                    tasks = cache.tasks.map { if (it.id == taskId) it.copy(status = newStatus) else it }
                )
                Prefs.saveCache(appCtx, optimistic)
                WidgetRenderer.applyData(appCtx, optimistic)
            }

            withContext(Dispatchers.Main) {
                finish()
                moveTaskToBack(true)
            }

            // اعمال واقعی روی سرور در پس‌زمینه — دیگر چیزی رو بلاک نمی‌کنه
            if (task != null) {
                try {
                    val baseUrl = Prefs.webappUrl(appCtx)
                    val token = Prefs.token(appCtx)
                    if (baseUrl.isNotBlank() && token.isNotBlank()) {
                        ApiClient.toggleTaskDone(baseUrl, token, task)
                    }
                } catch (_: Exception) { }
            }
            WidgetUpdateService.start(appCtx)
        }
    }
}

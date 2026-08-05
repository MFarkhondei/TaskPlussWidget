package com.taskpluss.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * کلیک روی آیتم لیست ویجت:
 * action=toggle → انجام‌شده
 * action=edit   → فرم ویرایش
 */
class ToggleTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra("task_id").orEmpty()
        val action = intent.getStringExtra("action") ?: "toggle"
        val appCtx = applicationContext

        if (taskId.isBlank()) {
            finish()
            return
        }

        if (action == "edit") {
            startActivity(
                Intent(this, TaskFormActivity::class.java)
                    .putExtra(TaskFormActivity.EXTRA_TASK_ID, taskId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        val cache = Prefs.loadCache(appCtx)
        val task = cache.tasks.find { it.id == taskId }
        if (task == null) {
            finish()
            return
        }
        val newStatus = if (task.status == "done") "todo" else "done"
        val updatedList = cache.tasks.map {
            if (it.id == taskId) it.copy(status = newStatus) else it
        }
        val updated = cache.copy(tasks = updatedList)
        Prefs.saveCache(appCtx, updated)
        WidgetRenderer.applyData(appCtx, updated)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = Prefs.webappUrl(appCtx)
                val token = Prefs.token(appCtx)
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    ApiClient.toggleTaskDone(baseUrl, token, task)
                }
            } catch (_: Exception) { }
            WidgetUpdateService.start(appCtx)
        }
        finish()
    }
}

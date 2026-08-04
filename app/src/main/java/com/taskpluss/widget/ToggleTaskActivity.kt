package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ToggleTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra("task_id") ?: run {
            finish(); return
        }
        val appCtx = applicationContext
        val cacheHint = Prefs.loadCache(this).copy(updatedAt = "…")
        WidgetRenderer.applyData(this, cacheHint)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = Prefs.webappUrl(appCtx)
                val token = Prefs.token(appCtx)
                val cache = Prefs.loadCache(appCtx)
                val task = cache.tasks.find { it.id == taskId }
                if (task != null && baseUrl.isNotBlank() && token.isNotBlank()) {
                    val result = ApiClient.toggleTaskDone(baseUrl, token, task)
                    if (result.success) {
                        val newStatus = if (task.status == "done") "todo" else "done"
                        val updated = cache.tasks.map {
                            if (it.id == taskId) it.copy(status = newStatus) else it
                        }
                        val newCache = cache.copy(
                            tasks = updated,
                            offline = false,
                            updatedAt = JalaliUtils.nowTehranJalaliString()
                        )
                        Prefs.saveCache(appCtx, newCache)
                        WidgetRenderer.applyData(appCtx, newCache)
                    }
                }
                WidgetRenderer.fetchAndApply(appCtx)
            } finally {
                runOnUiThread {
                    finish()
                    moveTaskToBack(true)
                }
            }
        }
    }
}

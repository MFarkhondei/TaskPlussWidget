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
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val baseUrl = Prefs.run { webappUrl }
            val token = Prefs.run { token }
            val cache = Prefs.run { loadCache() }
            val task = cache.tasks.find { it.id == taskId }

            if (task != null && baseUrl.isNotBlank() && token.isNotBlank()) {
                val result = ApiClient.toggleTaskDone(baseUrl, token, task)
                if (result.success) {
                    // Optimistic local update
                    val newStatus = if (task.status == "done") "todo" else "done"
                    val updated = cache.tasks.map {
                        if (it.id == taskId) it.copy(status = newStatus) else it
                    }
                    val newCache = cache.copy(tasks = updated, offline = false)
                    Prefs.run { saveCache(newCache) }
                    WidgetRenderer.applyData(this@ToggleTaskActivity, newCache)
                }
            }
            // Full refresh
            WidgetRenderer.fetchAndApply(this@ToggleTaskActivity)
            finish()
        }
    }
}

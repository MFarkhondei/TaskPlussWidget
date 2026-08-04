package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupSelectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("group_key") ?: "all"
        Prefs.run { selectedGroupKey = key }

        val cache = Prefs.run { loadCache() }.copy(selectedGroupKey = key)
        Prefs.run { saveCache(cache) }
        WidgetRenderer.applyData(this, cache)

        val appCtx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.fetchAndApply(appCtx)
            } finally {
                runOnUiThread { finish() }
            }
        }
    }
}

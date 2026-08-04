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

        // Re-apply from cache with new selection, then optional network refresh
        val cache = Prefs.run { loadCache() }.copy(selectedGroupKey = key)
        Prefs.run { saveCache(cache) }
        WidgetRenderer.applyData(this, cache)

        CoroutineScope(Dispatchers.IO).launch {
            WidgetRenderer.fetchAndApply(this@GroupSelectActivity)
            finish()
        }
    }
}

package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle

class GroupSelectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("group_key") ?: "all"
        Prefs.setSelectedGroupKey(this, key)
        val cache = Prefs.loadCache(this).copy(selectedGroupKey = key)
        Prefs.saveCache(this, cache)
        WidgetRenderer.applyData(this, cache)
        finish()
    }
}

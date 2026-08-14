package com.taskpluss.widget

import android.app.Activity
import android.os.Bundle

class GroupPageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delta = intent.getIntExtra("delta", 0)
        val cur = Prefs.groupPage(this)
        Prefs.setGroupPage(this, (cur + delta).coerceAtLeast(0))
        WidgetRenderer.applyData(this, Prefs.loadCache(this))
        finish()
    }
}

package com.taskpluss.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.taskpluss.widget.model.TaskItem

class TaskRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val cache = Prefs.loadCache(context)
        val key = cache.selectedGroupKey
        val list = if (key == "all") cache.tasks else cache.tasks.filter { it.group == key }
        val active = list.filter { it.status != "done" }
        tasks = if (key == "all") {
            active.sortedWith(compareByDescending<TaskItem> { it.created }.thenByDescending { it.id })
        } else {
            active.sortedWith(
                compareByDescending<TaskItem> { it.priority }.thenByDescending { it.created }
            )
        }
    }

    override fun onDestroy() { tasks = emptyList() }
    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.item_task)
        }
        val t = tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.item_task)
        rv.setTextViewText(R.id.tv_task_title, t.title)
        rv.setImageViewResource(R.id.iv_check, R.drawable.ic_check_empty)
        rv.setTextColor(R.id.tv_task_title, 0xFFF8FAFC.toInt())
        val pText = when {
            t.priority >= 3 -> "!!!"
            t.priority == 2 -> "!!"
            t.priority == 1 -> "!"
            else -> ""
        }
        rv.setTextViewText(R.id.tv_task_priority, pText)
        rv.setTextColor(
            R.id.tv_task_priority,
            when {
                t.priority >= 3 -> 0xFFF87171.toInt()
                t.priority == 2 -> 0xFFF5C542.toInt()
                else -> 0xFF34D399.toInt()
            }
        )
        val fill = Intent().apply { putExtra("task_id", t.id) }
        rv.setOnClickFillInIntent(R.id.item_task_root, fill)
        rv.setOnClickFillInIntent(R.id.iv_check, fill)
        rv.setOnClickFillInIntent(R.id.tv_task_title, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}

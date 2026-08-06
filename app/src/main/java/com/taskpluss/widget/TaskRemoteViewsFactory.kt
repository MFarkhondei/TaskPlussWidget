package com.taskpluss.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.taskpluss.widget.model.TaskItem

class TaskRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskItem> = emptyList()
    private var showGroupChip: Boolean = false
    private var groupNames: Map<String, String> = emptyMap()
    private var groupOrder: List<String> = emptyList()
    private var titleFontSp: Float = 11f

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val cache = Prefs.loadCache(context)
        val key = cache.selectedGroupKey
        titleFontSp = Prefs.taskFontSp(context).coerceIn(9f, 20f)
        val active = cache.tasks.filter {
            it.status != "done" && it.status != "deleted"
        }

        groupOrder = cache.groups.keys.filter { it != "none" }.toList()
        groupNames = cache.groups.mapValues { it.value.name }
        showGroupChip = (key == "by_priority")

        val byPriorityAsc = compareBy<TaskItem> {
            if (it.priority in 1..5) it.priority else 99
        }.thenByDescending { it.created }

        tasks = when (key) {
            "all" -> active.sortedWith(
                compareByDescending<TaskItem> { it.created }.thenByDescending { it.id }
            )
            "by_priority" -> active.sortedWith(
                compareBy<TaskItem> { groupRank(it.group) }
                    .thenBy { if (it.priority in 1..5) it.priority else 99 }
                    .thenByDescending { it.created }
            )
            else -> active.filter { it.group == key }.sortedWith(byPriorityAsc)
        }
    }

    private fun groupRank(groupKey: String): Int {
        val g = if (groupKey.isBlank()) "none" else groupKey
        if (g == "none") return 10_000
        val idx = groupOrder.indexOf(g)
        return if (idx >= 0) idx else 5_000
    }

    private fun groupLabel(groupKey: String): String {
        val g = if (groupKey.isBlank()) "none" else groupKey
        if (g == "none") return "بدون گروه"
        return groupNames[g] ?: g
    }

    override fun onDestroy() { tasks = emptyList() }
    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= tasks.size) {
            return RemoteViews(context.packageName, R.layout.item_task)
        }
        val t = tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.item_task)
        rv.setImageViewResource(R.id.iv_check, R.drawable.ic_check_empty)

        WidgetText.setTitle(
            context, rv, R.id.iv_task_title, t.title,
            textSizeSp = titleFontSp,
            maxWidthDp = 200,
            maxLines = 2
        )

        val label = if (t.priority in 1..5) t.priority.toString() else ""
        WidgetText.setPriority(context, rv, R.id.iv_task_priority, label, priorityColor(t.priority))

        if (showGroupChip) {
            rv.setViewVisibility(R.id.iv_task_group, View.VISIBLE)
            WidgetText.setLabel(
                context, rv, R.id.iv_task_group, groupLabel(t.group),
                textSizeSp = (titleFontSp - 1.5f).coerceAtLeast(9f),
                color = 0xFF94A3B8.toInt(),
                bold = false,
                maxWidthDp = 64,
                align = WidgetText.Align.CENTER
            )
        } else {
            rv.setViewVisibility(R.id.iv_task_group, View.GONE)
        }

        val toggleFill = Intent().apply {
            putExtra("task_id", t.id)
            putExtra("action", "toggle")
        }
        rv.setOnClickFillInIntent(R.id.iv_check, toggleFill)

        val editFill = Intent().apply {
            putExtra("task_id", t.id)
            putExtra("action", "edit")
        }
        rv.setOnClickFillInIntent(R.id.iv_task_title, editFill)
        rv.setOnClickFillInIntent(R.id.item_task_root, editFill)

        return rv
    }

    private fun priorityColor(p: Int): Int = when (p) {
        1 -> 0xFFF87171.toInt()
        2 -> 0xFFFB923C.toInt()
        3 -> 0xFFF5C542.toInt()
        4 -> 0xFFA3E635.toInt()
        5 -> 0xFF34D399.toInt()
        else -> 0xFF64748B.toInt()
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}

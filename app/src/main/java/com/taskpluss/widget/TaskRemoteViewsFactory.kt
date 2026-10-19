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
    private var showDateChip: Boolean = false
    private var currentKey: String = ""
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
        currentKey = key
        showGroupChip = (key == "by_priority" || key == "schedule" || key == "today")
        showDateChip = false  // دیگر تاریخ در چیپ نشان داده نمی‌شود

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
            "schedule" -> active
                .filter { it.date.isNotBlank() && JalaliUtils.parseJalaliDate(it.date) != null }
                .sortedWith(
                    compareBy<TaskItem> { dateSortKey(it.date) }
                        .thenByDescending { it.created }
                )
            "today" -> active
                .filter { JalaliUtils.isToday(it.date) }
                .sortedWith(
                    compareBy<TaskItem> { timeSortKey(it.date) }
                        .thenBy { if (it.priority in 1..5) it.priority else 99 }
                        .thenByDescending { it.created }
                )
            else -> active.filter { it.group == key }.sortedWith(byPriorityAsc)
        }
    }

    private fun dateSortKey(dateStr: String): Long {
        val parts = JalaliUtils.parseJalaliDate(dateStr) ?: return Long.MAX_VALUE
        var key = parts.year.toLong() * 1_000_000L + parts.month * 10_000L + parts.day * 100L
        val time = JalaliUtils.parseTime(dateStr)
        if (time != null) key += time.hour * 60L + time.minute
        return key
    }

    private fun timeSortKey(dateStr: String): Int {
        val time = JalaliUtils.parseTime(dateStr)
        return if (time == null) 24 * 60 else time.hour * 60 + time.minute
    }

    private fun formatDateLabel(dateStr: String, forToday: Boolean): String {
        if (forToday) {
            JalaliUtils.formatTime(dateStr)?.let { return it }
        }
        val parts = JalaliUtils.parseJalaliDate(dateStr) ?: return ""
        return "%02d/%02d".format(parts.month, parts.day)
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

        // اولویت‌بندی / زمانبندی / امروز: فونت کمی بزرگ‌تر و عرض عنوان کمتر به‌خاطر چیپ گروه
        val listFont: Float
        val titleWidth: Int
        if (showGroupChip) {
            listFont = (titleFontSp + 0.5f).coerceAtMost(22f)
            titleWidth = if (currentKey == "schedule" || currentKey == "today") 185 else 170
        } else {
            listFont = titleFontSp
            titleWidth = 200
        }
        WidgetText.setTitle(
            context, rv, R.id.iv_task_title, t.title,
            textSizeSp = listFont,
            maxWidthDp = titleWidth,
            maxLines = 2
        )

        // در زمانبندی و امروز اولویت نشان داده نمی‌شود
        val hidePriority = (currentKey == "schedule" || currentKey == "today")
        if (hidePriority) {
            rv.setViewVisibility(R.id.iv_task_priority, View.GONE)
        } else {
            rv.setViewVisibility(R.id.iv_task_priority, View.VISIBLE)
            val label = if (t.priority in 1..5) t.priority.toString() else ""
            WidgetText.setPriority(context, rv, R.id.iv_task_priority, label, priorityColor(t.priority))
        }

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

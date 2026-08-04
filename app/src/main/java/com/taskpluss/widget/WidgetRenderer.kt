package com.taskpluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.taskpluss.widget.model.TaskItem
import com.taskpluss.widget.model.WidgetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetRenderer {

    private val TASK_ROW_IDS = intArrayOf(
        R.id.row_task_1, R.id.row_task_2, R.id.row_task_3,
        R.id.row_task_4, R.id.row_task_5, R.id.row_task_6
    )
    private val TASK_TV_IDS = intArrayOf(
        R.id.tv_task_1, R.id.tv_task_2, R.id.tv_task_3,
        R.id.tv_task_4, R.id.tv_task_5, R.id.tv_task_6
    )
    private val CHECK_IDS = intArrayOf(
        R.id.iv_check_1, R.id.iv_check_2, R.id.iv_check_3,
        R.id.iv_check_4, R.id.iv_check_5, R.id.iv_check_6
    )
    private val PRIORITY_IDS = intArrayOf(
        R.id.tv_priority_1, R.id.tv_priority_2, R.id.tv_priority_3,
        R.id.tv_priority_4, R.id.tv_priority_5, R.id.tv_priority_6
    )
    private val CHIP_IDS = intArrayOf(
        R.id.chip_group_0, R.id.chip_group_1, R.id.chip_group_2,
        R.id.chip_group_3, R.id.chip_group_4
    )

    suspend fun fetchAndApply(context: Context, appWidgetIds: IntArray? = null) {
        withContext(Dispatchers.IO) {
            val baseUrl = Prefs.run { context.webappUrl }
            val token = Prefs.run { context.token }
            val selected = Prefs.run { context.selectedGroupKey }

            if (baseUrl.isBlank() || token.isBlank()) {
                val cache = Prefs.run { context.loadCache() }
                applyData(context, cache.copy(offline = true), appWidgetIds)
                return@withContext
            }

            val result = ApiClient.fetchAll(baseUrl, token, selected)
            if (result.success && result.cache != null) {
                Prefs.run { context.saveCache(result.cache) }
                applyData(context, result.cache, appWidgetIds)
            } else {
                val cache = Prefs.run { context.loadCache() }.copy(offline = true)
                applyData(context, cache, appWidgetIds)
            }
        }
    }

    fun applyData(context: Context, cache: WidgetCache, appWidgetIds: IntArray? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = appWidgetIds ?: mgr.getAppWidgetIds(
            android.content.ComponentName(context, TaskPlussWidgetProvider::class.java)
        )
        for (id in ids) {
            val rv = buildRemoteViews(context, cache)
            mgr.updateAppWidget(id, rv)
        }
    }

    private fun buildRemoteViews(context: Context, cache: WidgetCache): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_layout)

        val statusText = when {
            cache.offline -> "آفلاین"
            cache.updatedAt.isNotBlank() -> cache.updatedAt
            else -> "—"
        }
        rv.setTextViewText(R.id.tv_updated, statusText)
        rv.setTextColor(
            R.id.tv_updated,
            if (cache.offline) 0xFFF87171.toInt() else 0xFF64748B.toInt()
        )

        val refreshIntent = Intent(context, SilentRefreshActivity::class.java)
        val refreshPi = PendingIntent.getActivity(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)

        val addIntent = Intent(context, AddTaskActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val addPi = PendingIntent.getActivity(
            context, 1, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btn_add_task, addPi)

        val groupKeys = mutableListOf("all")
        val groupNames = mutableListOf("همه")
        cache.groups.entries
            .filter { it.key != "none" }
            .sortedBy { it.value.name }
            .take(4)
            .forEach {
                groupKeys.add(it.key)
                groupNames.add(it.value.name)
            }

        for (i in CHIP_IDS.indices) {
            if (i < groupKeys.size) {
                rv.setViewVisibility(CHIP_IDS[i], View.VISIBLE)
                rv.setTextViewText(CHIP_IDS[i], groupNames[i])
                val selected = groupKeys[i] == cache.selectedGroupKey
                rv.setInt(
                    CHIP_IDS[i], "setBackgroundResource",
                    if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip
                )
                rv.setTextColor(
                    CHIP_IDS[i],
                    if (selected) 0xFFF5C542.toInt() else 0xFF94A3B8.toInt()
                )
                val gIntent = Intent(context, GroupSelectActivity::class.java).apply {
                    putExtra("group_key", groupKeys[i])
                }
                val gPi = PendingIntent.getActivity(
                    context, 100 + i, gIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                rv.setOnClickPendingIntent(CHIP_IDS[i], gPi)
            } else {
                rv.setViewVisibility(CHIP_IDS[i], View.GONE)
            }
        }

        val filtered = filterAndSort(cache)
        val show = filtered.take(6)

        if (show.isEmpty()) {
            rv.setViewVisibility(R.id.tv_empty, View.VISIBLE)
            TASK_ROW_IDS.forEach { rv.setViewVisibility(it, View.GONE) }
        } else {
            rv.setViewVisibility(R.id.tv_empty, View.GONE)
            for (i in TASK_ROW_IDS.indices) {
                if (i < show.size) {
                    val t = show[i]
                    rv.setViewVisibility(TASK_ROW_IDS[i], View.VISIBLE)
                    rv.setTextViewText(TASK_TV_IDS[i], t.title)
                    rv.setImageViewResource(CHECK_IDS[i], R.drawable.ic_check_empty)
                    rv.setTextColor(TASK_TV_IDS[i], 0xFFF8FAFC.toInt())
                    val pText = when {
                        t.priority >= 3 -> "!!!"
                        t.priority == 2 -> "!!"
                        t.priority == 1 -> "!"
                        else -> ""
                    }
                    rv.setTextViewText(PRIORITY_IDS[i], pText)
                    rv.setTextColor(
                        PRIORITY_IDS[i],
                        when {
                            t.priority >= 3 -> 0xFFF87171.toInt()
                            t.priority == 2 -> 0xFFF5C542.toInt()
                            else -> 0xFF34D399.toInt()
                        }
                    )

                    val toggleIntent = Intent(context, ToggleTaskActivity::class.java).apply {
                        putExtra("task_id", t.id)
                    }
                    val togglePi = PendingIntent.getActivity(
                        context, 200 + i, toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    rv.setOnClickPendingIntent(CHECK_IDS[i], togglePi)
                    rv.setOnClickPendingIntent(TASK_ROW_IDS[i], togglePi)
                } else {
                    rv.setViewVisibility(TASK_ROW_IDS[i], View.GONE)
                }
            }
        }

        return rv
    }

    private fun filterAndSort(cache: WidgetCache): List<TaskItem> {
        val key = cache.selectedGroupKey
        // تسک‌های انجام‌شده در ویجت نمایش داده نمی‌شوند
        val active = cache.tasks.filter { it.status != "done" }
        val list = if (key == "all") {
            active
        } else {
            active.filter { it.group == key }
        }
        return if (key == "all") {
            list.sortedWith(compareByDescending<TaskItem> { it.created }.thenByDescending { it.id })
        } else {
            list.sortedWith(
                compareByDescending<TaskItem> { it.priority }
                    .thenByDescending { it.created }
            )
        }
    }
}

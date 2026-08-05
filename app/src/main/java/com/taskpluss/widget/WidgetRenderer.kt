package com.taskpluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.taskpluss.widget.model.WidgetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetRenderer {

    private const val PAGE_SIZE = 4
    private val CHIP_IDS = intArrayOf(
        R.id.chip_group_0, R.id.chip_group_1, R.id.chip_group_2, R.id.chip_group_3
    )

    suspend fun fetchAndApply(context: Context, appWidgetIds: IntArray? = null) {
        withContext(Dispatchers.IO) {
            val baseUrl = Prefs.webappUrl(context)
            val token = Prefs.token(context)
            val selected = Prefs.selectedGroupKey(context)

            if (baseUrl.isBlank() || token.isBlank()) {
                val cache = Prefs.loadCache(context)
                applyData(context, cache.copy(offline = true), appWidgetIds)
                return@withContext
            }

            val result = ApiClient.fetchAll(baseUrl, token, selected)
            if (result.success && result.cache != null) {
                Prefs.saveCache(context, result.cache)
                applyData(context, result.cache, appWidgetIds)
            } else {
                val cache = Prefs.loadCache(context).copy(offline = true)
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
            val rv = buildRemoteViews(context, cache, id)
            mgr.updateAppWidget(id, rv)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.list_tasks)
        }
    }

    private fun buildRemoteViews(context: Context, cache: WidgetCache, appWidgetId: Int): RemoteViews {
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

        val refreshPi = PendingIntent.getActivity(
            context, 0,
            Intent(context, SilentRefreshActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)

        val addPi = PendingIntent.getActivity(
            context, 1,
            Intent(context, AddTaskActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btn_add_task, addPi)

        val allKeys = mutableListOf("all")
        val allNames = mutableListOf("همه")
        cache.groups.entries
            .filter { it.key != "none" }
            .sortedBy { it.value.name }
            .forEach {
                allKeys.add(it.key)
                allNames.add(it.value.name)
            }

        val page = Prefs.groupPage(context).coerceAtLeast(0)
        val maxPage = ((allKeys.size - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceAtMost(maxPage)
        if (safePage != page) Prefs.setGroupPage(context, safePage)

        val start = safePage * PAGE_SIZE
        for (i in CHIP_IDS.indices) {
            val idx = start + i
            if (idx < allKeys.size) {
                rv.setViewVisibility(CHIP_IDS[i], View.VISIBLE)
                rv.setTextViewText(CHIP_IDS[i], allNames[idx])
                val selected = allKeys[idx] == cache.selectedGroupKey
                rv.setInt(
                    CHIP_IDS[i], "setBackgroundResource",
                    if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip
                )
                rv.setTextColor(
                    CHIP_IDS[i],
                    if (selected) 0xFFF5C542.toInt() else 0xFF94A3B8.toInt()
                )
                val gPi = PendingIntent.getActivity(
                    context, 100 + i,
                    Intent(context, GroupSelectActivity::class.java).apply {
                        putExtra("group_key", allKeys[idx])
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                rv.setOnClickPendingIntent(CHIP_IDS[i], gPi)
            } else {
                rv.setViewVisibility(CHIP_IDS[i], View.GONE)
            }
        }

        val prevPi = PendingIntent.getActivity(
            context, 50,
            Intent(context, GroupPageActivity::class.java).apply { putExtra("delta", -1) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPi = PendingIntent.getActivity(
            context, 51,
            Intent(context, GroupPageActivity::class.java).apply { putExtra("delta", 1) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btn_group_prev, prevPi)
        rv.setOnClickPendingIntent(R.id.btn_group_next, nextPi)
        rv.setTextColor(R.id.btn_group_prev, if (safePage > 0) 0xFFF5C542.toInt() else 0xFF64748B.toInt())
        rv.setTextColor(R.id.btn_group_next, if (safePage < maxPage) 0xFFF5C542.toInt() else 0xFF64748B.toInt())

        val serviceIntent = Intent(context, TaskListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "/$appWidgetId")
        }
        rv.setRemoteAdapter(R.id.list_tasks, serviceIntent)

        val template = Intent(context, ToggleTaskActivity::class.java)
        val templatePi = PendingIntent.getActivity(
            context, 200, template,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.list_tasks, templatePi)

        val activeCount = cache.tasks.count { t ->
            (cache.selectedGroupKey == "all" || t.group == cache.selectedGroupKey) && t.status != "done"
        }
        if (activeCount == 0) {
            rv.setViewVisibility(R.id.tv_empty, View.VISIBLE)
            rv.setViewVisibility(R.id.list_tasks, View.GONE)
        } else {
            rv.setViewVisibility(R.id.tv_empty, View.GONE)
            rv.setViewVisibility(R.id.list_tasks, View.VISIBLE)
        }

        return rv
    }
}

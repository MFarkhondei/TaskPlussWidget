package com.taskpluss.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.taskpluss.widget.model.GroupItem
import com.taskpluss.widget.model.TaskItem
import com.taskpluss.widget.model.WidgetCache

object Prefs {
    private const val NAME = "taskpluss_widget_prefs"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var Context.webappUrl: String
        get() = sp(this).getString("webapp_url", "") ?: ""
        @JvmName("setWebappUrlExt")
        set(v) { sp(this).edit().putString("webapp_url", v).apply() }

    var Context.username: String
        get() = sp(this).getString("username", "") ?: ""
        @JvmName("setUsernameExt")
        set(v) { sp(this).edit().putString("username", v).apply() }

    var Context.token: String
        get() = sp(this).getString("token", "") ?: ""
        @JvmName("setTokenExt")
        set(v) { sp(this).edit().putString("token", v).apply() }

    var Context.intervalMin: Int
        get() = sp(this).getInt("interval_min", 30)
        @JvmName("setIntervalMinExt")
        set(v) { sp(this).edit().putInt("interval_min", v).apply() }

    var Context.selectedGroupKey: String
        get() = sp(this).getString("selected_group", "all") ?: "all"
        @JvmName("setSelectedGroupKeyExt")
        set(v) { sp(this).edit().putString("selected_group", v).apply() }

    var Context.groupPage: Int
        get() = sp(this).getInt("group_page", 0)
        @JvmName("setGroupPageExt")
        set(v) { sp(this).edit().putInt("group_page", v).apply() }

    /** اندازه فونت عنوان تسک در ویجت (sp) — پیش‌فرض ۱۱ */
    var Context.taskFontSp: Float
        get() = sp(this).getFloat("task_font_sp", 11f)
        @JvmName("setTaskFontSpExt")
        set(v) { sp(this).edit().putFloat("task_font_sp", v).apply() }

    var Context.cacheJson: String
        get() = sp(this).getString("cache_json", "") ?: ""
        set(v) { sp(this).edit().putString("cache_json", v).apply() }

    var Context.cacheAt: Long
        get() = sp(this).getLong("cache_at", 0L)
        set(v) { sp(this).edit().putLong("cache_at", v).apply() }

    fun webappUrl(ctx: Context) = ctx.webappUrl
    fun token(ctx: Context) = ctx.token
    fun username(ctx: Context) = ctx.username
    fun intervalMin(ctx: Context) = ctx.intervalMin
    fun selectedGroupKey(ctx: Context) = ctx.selectedGroupKey
    fun groupPage(ctx: Context) = ctx.groupPage
    fun taskFontSp(ctx: Context) = ctx.taskFontSp

    fun setWebappUrl(ctx: Context, v: String) { ctx.webappUrl = v }
    fun setToken(ctx: Context, v: String) { ctx.token = v }
    fun setUsername(ctx: Context, v: String) { ctx.username = v }
    fun setIntervalMin(ctx: Context, v: Int) { ctx.intervalMin = v }
    fun setSelectedGroupKey(ctx: Context, v: String) { ctx.selectedGroupKey = v }
    fun setGroupPage(ctx: Context, v: Int) { ctx.groupPage = v }
    fun setTaskFontSp(ctx: Context, v: Float) { ctx.taskFontSp = v }

    fun saveCache(ctx: Context, cache: WidgetCache) {
        val root = JSONObject()
        root.put("updatedAt", cache.updatedAt)
        root.put("offline", cache.offline)
        root.put("selectedGroupKey", cache.selectedGroupKey)
        val tasksArr = JSONArray()
        cache.tasks.forEach { t ->
            tasksArr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("status", t.status)
                put("priority", t.priority); put("date", t.date); put("created", t.created)
                put("group", t.group); put("notes", t.notes)
            })
        }
        root.put("tasks", tasksArr)
        val groupsObj = JSONObject()
        val orderArr = JSONArray()
        cache.groups.forEach { (k, g) ->
            groupsObj.put(k, JSONObject().apply {
                put("key", g.key); put("name", g.name); put("color", g.color)
            })
            orderArr.put(JSONObject().apply {
                put("key", g.key); put("name", g.name); put("color", g.color)
            })
        }
        root.put("groups", groupsObj)
        root.put("groupsOrder", orderArr)
        ctx.cacheJson = root.toString()
        ctx.cacheAt = System.currentTimeMillis()
    }

    fun loadCache(ctx: Context): WidgetCache {
        val raw = ctx.cacheJson
        if (raw.isBlank()) return WidgetCache(selectedGroupKey = ctx.selectedGroupKey)
        return try {
            val root = JSONObject(raw)
            val tasks = mutableListOf<TaskItem>()
            val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.getJSONObject(i)
                tasks.add(
                    TaskItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        status = o.optString("status", "todo"),
                        priority = o.optInt("priority", 0),
                        date = o.optString("date"),
                        created = o.optString("created"),
                        group = o.optString("group", "none"),
                        notes = o.optString("notes")
                    )
                )
            }
            val groups = linkedMapOf<String, GroupItem>()
            val orderArr = root.optJSONArray("groupsOrder")
            val groupsObj = root.optJSONObject("groups")
            if (orderArr != null && orderArr.length() > 0) {
                for (i in 0 until orderArr.length()) {
                    val g = orderArr.optJSONObject(i) ?: continue
                    val k = g.optString("key")
                    if (k.isBlank()) continue
                    groups[k] = GroupItem(
                        key = k,
                        name = g.optString("name", k),
                        color = g.optString("color", "#5B6B7A")
                    )
                }
            } else if (groupsObj != null) {
                val keys = groupsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val g = groupsObj.getJSONObject(k)
                    groups[k] = GroupItem(
                        key = g.optString("key", k),
                        name = g.optString("name", k),
                        color = g.optString("color", "#5B6B7A")
                    )
                }
            }
            WidgetCache(
                tasks = tasks,
                groups = groups,
                selectedGroupKey = root.optString("selectedGroupKey", ctx.selectedGroupKey),
                updatedAt = root.optString("updatedAt"),
                offline = root.optBoolean("offline", false)
            )
        } catch (_: Exception) {
            WidgetCache(selectedGroupKey = ctx.selectedGroupKey)
        }
    }
}

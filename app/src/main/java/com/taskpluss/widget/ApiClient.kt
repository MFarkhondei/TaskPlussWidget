package com.taskpluss.widget

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.taskpluss.widget.model.GroupItem
import com.taskpluss.widget.model.TaskItem
import com.taskpluss.widget.model.WidgetCache

/**
 * کلاینت API مطابق بک‌اند تسک‌پلاس (doGet + action)
 * همان مسیر فرانت‌اند: action=login|getGroups|getTasks|addTask|updateTask
 */
object ApiClient {

    private const val TIMEOUT = 60000  // افزایش تایم‌اوت به ۱ دقیقه (۶۰۰۰۰ میلی‌ثانیه)

    const val DEFAULT_WEBAPP_URL =
        "https://script.google.com/macros/s/AKfycbx0W1jYG8-N4le384oJFYIwXD1OAgYb5lc6E6vOe9CDO3ov7fmkNRXJNdOvw_GSzGalkw/exec"

    data class Result(
        val success: Boolean,
        val message: String = "",
        val cache: WidgetCache? = null,
        val token: String? = null,
        val task: TaskItem? = null
    )

    fun normalizeUrl(raw: String): String {
        var u = raw.trim()
            .replace("\u200f", "").replace("\u200e", "")
            .replace("\u202a", "").replace("\u202c", "")
        if (u.isBlank()) return DEFAULT_WEBAPP_URL
        if (u.endsWith("/exed")) u = u.dropLast(4) + "exec"
        if (u.endsWith("/exe")) u = u + "c"
        if (!u.startsWith("http")) {
            u = "https://script.google.com/macros/s/$u"
        }
        u = u.substringBefore("?")
        if (!u.endsWith("/exec") && !u.endsWith("/exec/")) {
            if (!u.endsWith("/")) u += "/"
            if (!u.endsWith("exec/")) u = u.trimEnd('/') + "/exec"
        }
        return u.trimEnd('/')
    }

    private fun isHtml(text: String): Boolean {
        val t = text.trimStart().take(120).lowercase()
        return t.startsWith("<!doctype") || t.startsWith("<html") || t.contains("<head>")
    }

    private fun parseJson(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw Exception("پاسخ خالی از سرور")
        if (isHtml(trimmed)) {
            throw Exception(
                "سرور HTML برگرداند نه JSON.\n" +
                "آدرس باید کامل و به /exec ختم شود و Deploy روی Anyone باشد."
            )
        }
        var candidate = trimmed
        if (candidate.startsWith("\"") && candidate.endsWith("\"")) {
            try {
                candidate = JSONArray("[$candidate]").getString(0)
            } catch (_: Exception) { }
        }
        return JSONObject(candidate)
    }

    private fun getText(fullUrl: String): String {
        var url = fullUrl
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "TaskPlussWidget/1.1")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                url = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                redirects++
                conn.disconnect()
                continue
            }
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
        throw Exception("Redirect زیاد یا خطای شبکه")
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val b = normalizeUrl(base)
        val q = params.entries.joinToString("&") { (k, v) ->
            val ek = URLEncoder.encode(k, "UTF-8")
            val ev = URLEncoder.encode(v, "UTF-8")
            "$ek=$ev"
        }
        return "$b?$q"
    }

    fun login(baseUrl: String, username: String, password: String): Result {
        return try {
            val data = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "login",
                "data" to data.toString()
            ))
            val obj = parseJson(getText(url))
            if (obj.optBoolean("success", false) && obj.has("token")) {
                Result(true, "ورود موفق", token = obj.optString("token"))
            } else {
                Result(false, obj.optString("message", "ورود ناموفق"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun fetchAll(baseUrl: String, token: String, selectedGroup: String): Result {
        return try {
            val groupsUrl = buildUrl(baseUrl, mapOf(
                "action" to "getGroups",
                "token" to token
            ))
            
            // دریافت همه تسک‌ها با pagination - شروع با صفحه 0 و limit بالا
            val allTasks = mutableListOf<TaskItem>()
            var page = 0
            val limit = 500
            var hasMore = true
            var groupsText = ""
            
            while (hasMore) {
                val tasksData = JSONObject().apply {
                    put("page", page)
                    put("limit", limit)
                }
                val tasksUrl = buildUrl(baseUrl, mapOf(
                    "action" to "getTasks",
                    "token" to token,
                    "data" to tasksData.toString()
                ))

                // درخواست گروه‌ها فقط در دور اول
                if (page == 0) {
                    try { 
                        groupsText = getText(groupsUrl) 
                    } catch (_: Exception) { }
                }

                val tasksText = getText(tasksUrl)
                val tasksObj = parseJson(tasksText)
                
                if (tasksObj.has("message") && !tasksObj.optBoolean("success", true)) {
                    return Result(false, tasksObj.optString("message", "خطا در تسک‌ها"))
                }

                val arr = tasksObj.optJSONArray("tasks") ?: JSONArray()
                if (arr.length() == 0) {
                    // هیچ تسکی در این صفحه نیست، پس پایان داده‌ها
                    hasMore = false
                } else {
                    // افزودن تسک‌های دریافتی به لیست
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        allTasks.add(
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
                    // بررسی hasMore از سرور
                    val serverHasMore = tasksObj.optBoolean("hasMore", false)
                    // اگر تعداد تسک‌های دریافتی کمتر از limit باشد یا سرور بگوید بیشتر نیست، پایان
                    if (arr.length() < limit || !serverHasMore) {
                        hasMore = false
                    } else {
                        // صفحه بعدی را بگیر
                        page++
                    }
                }
            }

            // پردازش گروه‌ها از پاسخ اول
            val groupsMap = mutableMapOf<String, GroupItem>()
            if (groupsText.isNotBlank()) {
                val groupsObj = parseJson(groupsText)
                if (groupsObj.has("message") && !groupsObj.optBoolean("success", true)) {
                    // خطا در گروه‌ها حیاتی نیست، ادامه می‌دهیم
                } else {
                    val gObj = groupsObj.optJSONObject("groups")
                    if (gObj != null) {
                        val keys = gObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val g = gObj.getJSONObject(k)
                            groupsMap[k] = GroupItem(
                                key = k,
                                name = g.optString("name", k),
                                color = g.optString("color", "#5B6B7A")
                            )
                        }
                    }
                }
            }
            if (!groupsMap.containsKey("none")) {
                groupsMap["none"] = GroupItem("none", "بدون گروه")
            }

            Result(
                true, "موفق",
                cache = WidgetCache(
                    tasks = allTasks,
                    groups = groupsMap,
                    selectedGroupKey = selectedGroup,
                    updatedAt = nowTime(),
                    offline = false
                )
            )
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun addTask(baseUrl: String, token: String, title: String): Result {
        return try {
            // دقیقاً مطابق quickAdd فرانت‌اند تسک‌پلاس
            val created = JalaliUtils.nowTehranJalaliString()
            val id = JalaliUtils.newTaskId()
            val cleanTitle = title.trim()
            val task = JSONObject().apply {
                put("id", id)
                put("title", cleanTitle)
                put("status", "todo")
                put("priority", 0)
                put("date", "")
                put("created", created)
                put("group", "none")
                put("tags", JSONArray())
                put("notes", "")
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
                put("doingAt", "")
                put("doneAt", "")
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "addTask",
                "token" to token,
                "data" to task.toString()
            ))
            val obj = parseJson(getText(url))
            if (obj.optBoolean("success", false)) {
                val newTask = TaskItem(
                    id = id,
                    title = cleanTitle,
                    status = "todo",
                    priority = 0,
                    date = "",
                    created = created,
                    group = "none",
                    notes = ""
                )
                Result(true, "تسک اضافه شد ($created)", task = newTask)
            } else {
                Result(false, obj.optString("message", "خطا در افزودن"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun toggleTaskDone(baseUrl: String, token: String, task: TaskItem): Result {
        return try {
            val newStatus = if (task.status == "done") "todo" else "done"
            val stamp = JalaliUtils.nowTehranJalaliString()
            val taskJson = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("status", newStatus)
                put("priority", task.priority)
                put("date", task.date)
                put("created", task.created.ifBlank { stamp })
                put("group", if (task.group.isBlank()) "none" else task.group)
                put("tags", JSONArray())
                put("notes", task.notes)
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
                if (newStatus == "done") {
                    put("doneAt", stamp)
                } else {
                    put("doneAt", "")
                }
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "updateTask",
                "token" to token,
                "data" to taskJson.toString()
            ))
            val obj = parseJson(getText(url))
            if (obj.optBoolean("success", false)) {
                Result(true, "وضعیت به‌روز شد")
            } else {
                Result(false, obj.optString("message", "خطا"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    private fun nowTime(): String = JalaliUtils.nowTehranJalaliString()
}

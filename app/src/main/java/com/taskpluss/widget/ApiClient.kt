package com.taskpluss.widget

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.taskpluss.widget.model.GroupItem
import com.taskpluss.widget.model.TaskItem
import com.taskpluss.widget.model.WidgetCache

object ApiClient {

    private const val TIMEOUT = 15000

    data class Result(
        val success: Boolean,
        val message: String = "",
        val cache: WidgetCache? = null,
        val token: String? = null
    )

    /** Login via doGet action=login or RPC POST */
    fun login(baseUrl: String, username: String, password: String): Result {
        return try {
            val payload = JSONObject().apply {
                put("fn", "loginUser")
                put("args", JSONArray().put(username).put(password))
            }
            val resp = postJson(baseUrl, payload.toString())
            val obj = JSONObject(resp)
            if (obj.optBoolean("success", false) || obj.has("token")) {
                val token = obj.optString("token", "")
                Result(true, "ورود موفق", token = token)
            } else {
                Result(false, obj.optString("message", "ورود ناموفق"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    /** Fetch groups + tasks page (first page, limit 80) */
    fun fetchAll(baseUrl: String, token: String, selectedGroup: String): Result {
        return try {
            // Groups
            val groupsPayload = JSONObject().apply {
                put("fn", "getGroupsForUser")
                put("args", JSONArray().put(token))
            }
            val groupsResp = postJson(baseUrl, groupsPayload.toString())
            val groupsObj = JSONObject(groupsResp)
            if (!groupsObj.optBoolean("success", true) && groupsObj.has("message")) {
                return Result(false, groupsObj.optString("message", "خطا در دریافت گروه‌ها"))
            }

            val groupsMap = mutableMapOf<String, GroupItem>()
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
            if (!groupsMap.containsKey("none")) {
                groupsMap["none"] = GroupItem("none", "بدون گروه")
            }

            // Tasks page
            val tasksPayload = JSONObject().apply {
                put("fn", "getTasksPage")
                put("args", JSONArray().put(token).put(0).put(80))
            }
            val tasksResp = postJson(baseUrl, tasksPayload.toString())
            val tasksObj = JSONObject(tasksResp)
            if (!tasksObj.optBoolean("success", true) && tasksObj.has("message")) {
                return Result(false, tasksObj.optString("message", "خطا در دریافت تسک‌ها"))
            }

            val tasks = mutableListOf<TaskItem>()
            val arr = tasksObj.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
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

            val cache = WidgetCache(
                tasks = tasks,
                groups = groupsMap,
                selectedGroupKey = selectedGroup,
                updatedAt = nowJalaliApprox(),
                offline = false
            )
            Result(true, "موفق", cache = cache)
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun addTask(baseUrl: String, token: String, title: String): Result {
        return try {
            val task = JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("title", title)
                put("status", "todo")
                put("priority", 0)
                put("date", "")
                put("created", nowJalaliApprox())
                put("group", "none")
                put("tags", JSONArray())
                put("notes", "")
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
            }
            val payload = JSONObject().apply {
                put("fn", "upsertTask")
                put("args", JSONArray().put(token).put(task.toString()))
            }
            val resp = postJson(baseUrl, payload.toString())
            val obj = JSONObject(resp)
            if (obj.optBoolean("success", false)) {
                Result(true, "تسک اضافه شد")
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
            val taskJson = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("status", newStatus)
                put("priority", task.priority)
                put("date", task.date)
                put("created", task.created)
                put("group", task.group)
                put("tags", JSONArray())
                put("notes", task.notes)
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
                if (newStatus == "done") put("doneAt", nowJalaliApprox())
            }
            val payload = JSONObject().apply {
                put("fn", "upsertTask")
                put("args", JSONArray().put(token).put(taskJson.toString()))
            }
            val resp = postJson(baseUrl, payload.toString())
            val obj = JSONObject(resp)
            if (obj.optBoolean("success", false)) {
                Result(true, "وضعیت به‌روز شد")
            } else {
                Result(false, obj.optString("message", "خطا"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    private fun postJson(baseUrl: String, body: String): String {
        var url = baseUrl.trim()
        if (!url.contains("?")) {
            // ok
        }
        var conn: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 6) {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
                doInput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                url = loc
                redirects++
                conn.disconnect()
                continue
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
        throw Exception("Redirect loop or network error")
    }

    private fun nowJalaliApprox(): String {
        // Approximate display only; server uses proper Jalali
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran"))
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }
}

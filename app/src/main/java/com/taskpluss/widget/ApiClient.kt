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

object ApiClient {

    private const val TIMEOUT = 60000
    private const val PAGE_SIZE = 40

    const val DEFAULT_WEBAPP_URL =
        "https://script.google.com/macros/s/AKfycbx0W1jYG8-N4le384oJFYIwXD1OAgYb5lc6E6vOe9CDO3ov7fmkNRXJNdOvw_GSzGalkw/exec"

    // خواندن تسک‌ها و گروه‌ها مستقیم از Supabase (بدون واسطه Apps Script).
    // نوشتن (login/add/update/delete) هم‌چنان از طریق Apps Script انجام می‌شود.
    private const val SUPABASE_URL = "https://uzjaafbreuclhrmalukm.supabase.co"
    private const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV6amFhZmJyZXVjbGhybWFsdWttIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYwMjkzMTIsImV4cCI6MjEwMTYwNTMxMn0.Byx58LVLhYqbslZ33-W52ROzv7zwovoNG4uI6rAP5HU"

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
        var candidate = text.trim()
        if (candidate.isEmpty()) throw Exception("پاسخ خالی از سرور")
        if (isHtml(candidate)) {
            throw Exception("پاسخ HTML به‌جای JSON — آدرس /exec یا دسترسی Anyone را چک کنید")
        }
        repeat(3) {
            if (candidate.length >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
                try {
                    candidate = JSONArray("[$candidate]").getString(0).trim()
                } catch (_: Exception) {
                    return@repeat
                }
            } else return@repeat
        }
        return try {
            JSONObject(candidate)
        } catch (e: Exception) {
            throw Exception("JSON نامعتبر: ${candidate.take(80)}")
        }
    }

    private fun getText(fullUrl: String): String {
        return try {
            getTextOnce(fullUrl, followRedirects = true)
        } catch (_: Exception) {
            getTextOnce(fullUrl, followRedirects = false)
        }
    }

    private fun getTextOnce(fullUrl: String, followRedirects: Boolean): String {
        if (followRedirects) {
            val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "TaskPlussWidget/1.3 (Android)")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code")
                if (body.isBlank()) throw Exception("پاسخ خالی")
                return body
            } finally {
                conn.disconnect()
            }
        }

        var url = fullUrl
        var redirects = 0
        while (redirects < 10) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "TaskPlussWidget/1.3 (Android)")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw Exception("Redirect بدون Location")
                    url = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                    redirects++
                    continue
                }
                val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code")
                if (body.isBlank()) throw Exception("پاسخ خالی")
                return body
            } finally {
                conn.disconnect()
            }
        }
        throw Exception("Redirect بیش از حد")
    }

    private fun getTextWithRetry(fullUrl: String, retries: Int = 5): String {
        var last: Exception? = null
        for (attempt in 1..retries) {
            try {
                return getText(fullUrl)
            } catch (e: Exception) {
                last = e
                val msg = (e.message ?: "").lowercase()
                val isDns = e is java.net.UnknownHostException ||
                    msg.contains("unable to resolve host") ||
                    msg.contains("no address associated") ||
                    msg.contains("unknownhost")
                if (attempt < retries) {
                    val delay = if (isDns) (1500L * attempt).coerceAtMost(8000L) else (700L * attempt)
                    try { Thread.sleep(delay) } catch (_: InterruptedException) { }
                }
            }
        }
        val m = last?.message ?: "خطای شبکه"
        if (m.lowercase().contains("unable to resolve host") || last is java.net.UnknownHostException) {
            throw Exception("DNS: اینترنت یا محدودیت باتری را چک کنید")
        }
        throw last ?: Exception("خطای شبکه")
    }

    /** فراخوانی مستقیم توابع RPC در Supabase (rest/v1/rpc/...) با کلید anon. */
    private fun postSupabaseRpc(fn: String, body: JSONObject): JSONObject {
        val url = "$SUPABASE_URL/rest/v1/rpc/$fn"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TaskPlussWidget/1.3 (Android)")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw Exception("Supabase HTTP $code: ${text.take(150)}")
            if (text.isBlank()) throw Exception("پاسخ خالی از Supabase")
            return parseJson(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun postSupabaseRpcWithRetry(fn: String, body: JSONObject, retries: Int = 5): JSONObject {
        var last: Exception? = null
        for (attempt in 1..retries) {
            try {
                return postSupabaseRpc(fn, body)
            } catch (e: Exception) {
                last = e
                val msg = (e.message ?: "").lowercase()
                val isDns = e is java.net.UnknownHostException ||
                    msg.contains("unable to resolve host") || msg.contains("unknownhost")
                if (attempt < retries) {
                    val delay = if (isDns) (1500L * attempt).coerceAtMost(8000L) else (700L * attempt)
                    try { Thread.sleep(delay) } catch (_: InterruptedException) { }
                }
            }
        }
        val m = last?.message ?: "خطای شبکه"
        if (m.lowercase().contains("unable to resolve host")) {
            throw Exception("DNS: اینترنت یا محدودیت باتری را چک کنید")
        }
        throw last ?: Exception("خطای شبکه")
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val b = normalizeUrl(base)
        val q = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
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
            val obj = parseJson(getTextWithRetry(url))
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
        if (baseUrl.isBlank()) return Result(false, "آدرس Web App خالی است")
        if (token.isBlank()) return Result(false, "توکن خالی است — دوباره وارد شوید")

        var groupsMap = linkedMapOf<String, GroupItem>()
        var groupsError: String? = null
        var tasksError: String? = null
        var tasks = mutableListOf<TaskItem>()

        val groupsThread = Thread {
            try {
                val groupsBody = JSONObject().apply { put("p_token", token) }
                val groupsObj = postSupabaseRpcWithRetry("tp_get_groups", groupsBody, retries = 5)
                if (!groupsObj.optBoolean("success", true)) {
                    groupsError = groupsObj.optString("message", "خطا در گروه‌ها")
                    return@Thread
                }
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
            } catch (e: Exception) {
                groupsError = e.message ?: "خطا در گروه‌ها"
            }
        }

        val tasksThread = Thread {
            try {
                tasks = fetchAllTasks(baseUrl, token)
            } catch (e: Exception) {
                tasksError = e.message ?: "خطا در تسک‌ها"
            }
        }

        groupsThread.start()
        tasksThread.start()
        groupsThread.join()
        tasksThread.join()

        if (!groupsMap.containsKey("none")) {
            groupsMap["none"] = GroupItem("none", "بدون گروه")
        }

        if (tasksError != null && tasks.isEmpty()) {
            return Result(false, tasksError ?: groupsError ?: "خطای شبکه")
        }

        val warn = listOfNotNull(groupsError, tasksError).joinToString(" | ")
        return Result(
            true,
            if (warn.isBlank()) "موفق (${tasks.size})" else "ناقص: $warn",
            cache = WidgetCache(
                tasks = tasks,
                groups = groupsMap,
                selectedGroupKey = selectedGroup,
                updatedAt = nowTime(),
                offline = false
            )
        )
    }

    private fun fetchAllTasks(baseUrl: String, token: String): MutableList<TaskItem> {
        val tasks = mutableListOf<TaskItem>()
        val seenIds = mutableSetOf<String>()
        var page = 0
        var totalHint = -1
        while (page < 200) {
            val resp = requestTasksPage(baseUrl, token, page = page, limit = PAGE_SIZE)
            if (totalHint < 0 && resp.total > 0) totalHint = resp.total
            appendTasks(tasks, seenIds, resp.tasks)
            if (!resp.hasMore || resp.tasks.isEmpty()) break
            if (totalHint > 0 && tasks.size >= totalHint) break
            page++
        }
        return tasks
    }

    private data class TasksPage(val tasks: List<TaskItem>, val hasMore: Boolean, val total: Int)

    private fun requestTasksPage(baseUrl: String, token: String, page: Int, limit: Int): TasksPage {
        val body = JSONObject().apply {
            put("p_token", token)
            put("p_limit", limit)
            put("p_offset", page * limit)
        }
        val obj = postSupabaseRpcWithRetry("tp_get_tasks", body, retries = 5)
        if (!obj.optBoolean("success", true)) {
            throw Exception(obj.optString("message", "خطا در دریافت تسک‌ها"))
        }
        val arr = obj.optJSONArray("tasks") ?: JSONArray()
        val list = ArrayList<TaskItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
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
        val total = obj.optInt("total", -1)
        val hasMore = when {
            obj.has("hasMore") -> obj.optBoolean("hasMore")
            total >= 0 -> (page * limit + list.size) < total
            else -> list.size >= limit
        }
        return TasksPage(list, hasMore, total)
    }

    private fun appendTasks(out: MutableList<TaskItem>, seen: MutableSet<String>, incoming: List<TaskItem>) {
        for (t in incoming) {
            if (t.id.isNotBlank() && seen.add(t.id)) out.add(t)
        }
    }

    fun addTask(
        baseUrl: String,
        token: String,
        title: String,
        status: String = "todo",
        priority: Int = 0,
        group: String = "none",
        date: String = "",
        notes: String = ""
    ): Result {
        return try {
            val created = JalaliUtils.nowTehranJalaliString()
            val id = JalaliUtils.newTaskId()
            val cleanTitle = title.trim()
            val cleanStatus = status.ifBlank { "todo" }
            val g = if (group.isBlank()) "none" else group
            val task = JSONObject().apply {
                put("id", id)
                put("title", cleanTitle)
                put("status", cleanStatus)
                put("priority", priority)
                put("date", date)
                put("created", created)
                put("group", g)
                put("tags", JSONArray())
                put("notes", notes)
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
                put("doingAt", if (cleanStatus == "doing") created else "")
                put("doneAt", if (cleanStatus == "done") created else "")
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "addTask",
                "token" to token,
                "data" to task.toString()
            ))
            val obj = parseJson(getTextWithRetry(url))
            if (obj.optBoolean("success", false)) {
                Result(
                    true, "تسک اضافه شد ($created)",
                    task = TaskItem(
                        id = id, title = cleanTitle, status = cleanStatus,
                        priority = priority, date = date, created = created,
                        group = g, notes = notes
                    )
                )
            } else {
                Result(false, obj.optString("message", "خطا در افزودن"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun updateTaskFull(baseUrl: String, token: String, task: TaskItem): Result {
        return try {
            val taskJson = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("status", task.status.ifBlank { "todo" })
                put("priority", task.priority)
                put("date", task.date)
                put("created", task.created.ifBlank { JalaliUtils.nowTehranJalaliString() })
                put("group", if (task.group.isBlank()) "none" else task.group)
                put("tags", JSONArray())
                put("notes", task.notes)
                put("mainTask", JSONObject.NULL)
                put("subtasks", JSONArray())
                put("doingAt", "")
                put("doneAt", if (task.status == "done") JalaliUtils.nowTehranJalaliString() else "")
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "updateTask",
                "token" to token,
                "data" to taskJson.toString()
            ))
            val obj = parseJson(getTextWithRetry(url))
            if (obj.optBoolean("success", false)) Result(true, "ذخیره شد", task = task)
            else Result(false, obj.optString("message", "خطا در ذخیره"))
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
                if (newStatus == "done") put("doneAt", stamp) else put("doneAt", "")
            }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "updateTask",
                "token" to token,
                "data" to taskJson.toString()
            ))
            val obj = parseJson(getTextWithRetry(url))
            if (obj.optBoolean("success", false)) Result(true, "وضعیت به‌روز شد")
            else Result(false, obj.optString("message", "خطا"))
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    /** حذف واقعی مطابق بک‌اند: action=deleteTask و data.id */
    fun deleteTask(baseUrl: String, token: String, taskId: String): Result {
        return try {
            val data = JSONObject().apply { put("id", taskId) }
            val url = buildUrl(baseUrl, mapOf(
                "action" to "deleteTask",
                "token" to token,
                "data" to data.toString()
            ))
            val obj = parseJson(getTextWithRetry(url))
            if (obj.optBoolean("success", false)) Result(true, "حذف شد")
            else Result(false, obj.optString("message", "خطا در حذف"))
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    private fun nowTime(): String = JalaliUtils.nowTehranJalaliString()
}

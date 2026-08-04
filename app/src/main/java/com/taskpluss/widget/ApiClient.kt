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

    private const val TIMEOUT = 20000

    data class Result(
        val success: Boolean,
        val message: String = "",
        val cache: WidgetCache? = null,
        val token: String? = null
    )

    /** نرمال‌سازی آدرس Web App */
    fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        // حذف فاصله و کاراکترهای مخفی
        u = u.replace("\u200f", "").replace("\u200e", "").replace("\u202a", "").replace("\u202c", "")
        if (u.isBlank()) return u
        // اگر فقط شناسه deployment داده شده
        if (!u.startsWith("http")) {
            // حالت: AKfycb.../exec یا فقط شناسه
            if (u.contains("/exec") || u.length > 20) {
                u = "https://script.google.com/macros/s/$u"
            }
        }
        // اصلاح typo رایج exed → exec
        if (u.endsWith("/exed")) u = u.dropLast(4) + "exec"
        if (u.endsWith("/exe")) u = u + "c"
        // اطمینان از /exec در انتها (بدون query)
        val noQuery = u.substringBefore("?")
        if (!noQuery.endsWith("/exec") && !noQuery.endsWith("/exec/")) {
            // اگر /dev یا چیزی دیگر است دست نزن؛ فقط هشدار در پیام
        }
        return u
    }

    private fun isHtml(text: String): Boolean {
        val t = text.trimStart().take(200).lowercase()
        return t.startsWith("<!doctype") || t.startsWith("<html") || t.contains("<head>")
    }

    private fun parseJsonSafe(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw Exception("پاسخ خالی از سرور")
        if (isHtml(trimmed)) {
            throw Exception(
                "سرور صفحه HTML برگرداند نه JSON.\n" +
                "۱) آدرس باید کامل باشد و به /exec ختم شود\n" +
                "مثال: https://script.google.com/macros/s/XXXX/exec\n" +
                "۲) Deploy → Anyone (حتی بدون ورود گوگل)\n" +
                "۳) نسخه جدید Deploy را Publish کرده باشید"
            )
        }
        // گاهی پاسخ double-encoded است (رشته JSON داخل رشته)
        var candidate = trimmed
        if (candidate.startsWith("\"") && candidate.endsWith("\"")) {
            candidate = JSONObject.quote(candidate).let {
                // decode once
                try {
                    JSONArray("[$candidate]").getString(0)
                } catch (_: Exception) {
                    candidate
                }
            }
        }
        return try {
            JSONObject(candidate)
        } catch (e: Exception) {
            // اگر خود رشته JSON باشد که با { شروع نمی‌شود ولی parse می‌شود
            throw Exception("پاسخ JSON معتبر نیست: ${trimmed.take(80)}…")
        }
    }

    /** Login: ارسال با GET و پارامترهای query */
    fun login(baseUrl: String, username: String, password: String): Result {
        val url = normalizeUrl(baseUrl)
        if (url.isBlank()) return Result(false, "آدرس Web App خالی است")

        return try {
            val args = JSONArray().put(username).put(password)
            val fullUrl = "$url?fn=${URLEncoder.encode("loginUser", "UTF-8")}&args=${URLEncoder.encode(args.toString(), "UTF-8")}"
            val resp = getText(fullUrl)
            val obj = parseJsonSafe(resp)
            val root = unwrap(obj)
            if (root.optBoolean("success", false) || root.has("token")) {
                Result(true, "ورود موفق", token = root.optString("token", ""))
            } else {
                Result(false, root.optString("message", "ورود ناموفق"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    /** اگر سرور یک JSON به‌صورت رشته داخل فیلد برگردانده، بازش کن */
    private fun unwrap(obj: JSONObject): JSONObject {
        // بعضی مسیرها کل پاسخ را داخل success/message نمی‌گذارند
        return obj
    }

    fun fetchAll(baseUrl: String, token: String, selectedGroup: String): Result {
        val url = normalizeUrl(baseUrl)
        return try {
            // درخواست گروه‌ها با GET
            val groupsArgs = JSONArray().put(token)
            val groupsUrl = "$url?fn=${URLEncoder.encode("getGroupsForUser", "UTF-8")}&args=${URLEncoder.encode(groupsArgs.toString(), "UTF-8")}"
            val groupsResp = getText(groupsUrl)
            val groupsObj = parseJsonSafe(groupsResp)
            if (!groupsObj.optBoolean("success", true) && groupsObj.has("message")) {
                return Result(false, groupsObj.optString("message", "خطا در دریافت گروه‌ها"))
            }

            val groupsMap = mutableMapOf<String, GroupItem>()
            // پشتیبانی از فرمت‌های مختلف پاسخ
            val gArray = groupsObj.optJSONArray("groups")
            if (gArray != null) {
                for (i in 0 until gArray.length()) {
                    val g = gArray.getJSONObject(i)
                    val key = g.optString("groupId", g.optString("id", "group_$i"))
                    groupsMap[key] = GroupItem(
                        key = key,
                        name = g.optString("groupName", g.optString("name", key)),
                        color = g.optString("color", "#5B6B7A")
                    )
                }
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
            if (!groupsMap.containsKey("none")) {
                groupsMap["none"] = GroupItem("none", "بدون گروه")
            }
            // اضافه کردن گروه «همه» اگر وجود ندارد
            if (!groupsMap.containsKey("all")) {
                groupsMap["all"] = GroupItem("all", "همه")
            }

            // درخواست تسک‌ها با GET
            val tasksArgs = JSONArray().put(token).put(selectedGroup).put(0)
            val tasksUrl = "$url?fn=${URLEncoder.encode("getTasksPage", "UTF-8")}&args=${URLEncoder.encode(tasksArgs.toString(), "UTF-8")}"
            val tasksResp = getText(tasksUrl)
            val tasksObj = parseJsonSafe(tasksResp)
            if (!tasksObj.optBoolean("success", true) && tasksObj.has("message")) {
                return Result(false, tasksObj.optString("message", "خطا در دریافت تسک‌ها"))
            }
            val arr = tasksObj.optJSONArray("tasks") ?: JSONArray()
            val list = mutableListOf<TaskItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TaskItem(
                        id = o.optString("taskId", o.optString("id")),
                        title = o.optString("title"),
                        status = if (o.optBoolean("isDone", false)) "done" else "todo",
                        priority = o.optInt("priority", 0),
                        date = "",
                        created = o.optString("createdAt", o.optString("created")),
                        group = o.optString("groupId", o.optString("group", "none")),
                        notes = o.optString("description", o.optString("notes"))
                    )
                )
            }

            val cache = WidgetCache(
                tasks = list,
                groups = groupsMap,
                selectedGroupKey = selectedGroup,
                updatedAt = nowTime(),
                offline = false
            )
            Result(true, "موفق", cache = cache)
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
    }

    fun addTask(baseUrl: String, token: String, title: String): Result {
        val url = normalizeUrl(baseUrl)
        return try {
            // ساخت آبجکت تسک کامل مطابق با فرمت code.gs
            val taskObj = JSONObject()
                .put("id", java.util.UUID.randomUUID().toString())
                .put("title", title)
                .put("status", "todo")
                .put("priority", 0)
                .put("date", "")
                .put("created", "")
                .put("group", "none")
                .put("tags", JSONArray())
                .put("notes", "")
                .put("mainTask", JSONObject.NULL)
                .put("subtasks", JSONArray())
            
            val args = JSONArray().put(taskObj)
            
            val fullUrl = "$url?fn=${URLEncoder.encode("upsertTask", "UTF-8")}&args=${URLEncoder.encode(args.toString(), "UTF-8")}"
            val resp = getText(fullUrl)
            val obj = parseJsonSafe(resp)
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
        val url = normalizeUrl(baseUrl)
        return try {
            val isDone = task.status != "done"  // اگر انجام نشده باشد، انجام شده قرار بده
            
            // ساخت آبجکت تسک کامل با وضعیت جدید
            val taskObj = JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("status", if (isDone) "done" else "todo")
                .put("priority", task.priority)
                .put("date", task.date)
                .put("created", task.created)
                .put("group", task.group)
                .put("tags", JSONArray().apply {
                    task.tags?.forEach { put(it) }
                })
                .put("notes", task.notes)
                .put("mainTask", JSONObject.NULL)
                .put("subtasks", JSONArray().apply {
                    task.subtasks?.forEach { put(it) }
                })
            
            val args = JSONArray().put(taskObj)
            
            val fullUrl = "$url?fn=${URLEncoder.encode("upsertTask", "UTF-8")}&args=${URLEncoder.encode(args.toString(), "UTF-8")}"
            val resp = getText(fullUrl)
            val obj = parseJsonSafe(resp)
            if (obj.optBoolean("success", false)) {
                Result(true, "وضعیت به‌روز شد")
            } else {
                Result(false, obj.optString("message", "خطا"))
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "خطای شبکه")
        }
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
                setRequestProperty("User-Agent", "TaskPlussWidget/1.0")
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

    private fun postJson(baseUrl: String, body: String): String {
        // این تابع برای سازگاری با کدهای قدیمی نگه داشته شده است
        // اما اکنون تمام درخواست‌ها از طریق getText با متد GET ارسال می‌شوند
        throw UnsupportedOperationException("استفاده از POST پشتیبانی نمی‌شود. از GET استفاده کنید.")
    }

    private fun nowTime(): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran"))
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }
}

package com.tradepluss.widget

import com.google.gson.Gson
import com.tradepluss.widget.model.WidgetResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ApiClient {
    private val gson = Gson()

    /**
     * Login with username + password (no widget token required).
     * Returns pair of (response, rawJson) so caller can cache raw JSON.
     */
    fun fetchWidgetData(baseUrl: String, username: String, password: String): Pair<WidgetResponse, String> {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val qUser = URLEncoder.encode(username, "UTF-8")
        val qPass = URLEncoder.encode(password, "UTF-8")
        val fullUrl = "$cleanUrl?action=widget&user=$qUser&password=$qPass"

        // Warm up DNS (helps intermittent Iran DNS)
        try {
            val host = URL(cleanUrl).host
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            // continue; actual request may still work
        }

        var lastError: Exception? = null
        // Retry up to 3 times — DNS on mobile networks is flaky
        for (attempt in 0 until 3) {
            try {
                val body = httpGet(fullUrl)
                val parsed = gson.fromJson(body, WidgetResponse::class.java)
                    ?: throw Exception("پاسخ نامعتبر")
                return Pair(parsed, body)
            } catch (e: Exception) {
                lastError = e
                try {
                    Thread.sleep(400L * (attempt + 1))
                } catch (_: InterruptedException) {
                }
            }
        }
        throw lastError ?: Exception("خطای شبکه")
    }

    private fun httpGet(urlStr: String): String {
        var current = urlStr
        var redirects = 0
        while (redirects < 6) {
            val url = URL(current)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 35000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TradePlussWidget/1.1")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                useCaches = false
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw Exception("ریدایرکت بدون Location")
                    current = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                    redirects++
                    continue
                }
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: throw Exception("خطای HTTP $code")
                }
                val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) {
                    throw Exception("خطای HTTP $code")
                }
                if (body.isBlank()) throw Exception("پاسخ خالی از سرور")
                return body
            } finally {
                conn.disconnect()
            }
        }
        throw Exception("ریدایرکت بیش از حد")
    }
}

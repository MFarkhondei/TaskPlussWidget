package com.tradepluss.widget

import android.content.Context

object Prefs {
    private const val NAME = "tradepluss_widget_prefs"
    private const val KEY_URL = "webapp_url"
    private const val KEY_USER = "username"
    private const val KEY_PASSWORD = "password"
    /** @deprecated kept only to migrate old installs that stored widget token */
    private const val KEY_TOKEN_LEGACY = "token"
    private const val KEY_CACHE_JSON = "cache_json"
    private const val KEY_CACHE_AT = "cache_at"
    private const val KEY_INTERVAL_MIN = "interval_min"

    // Minutes: 15, 30, 60, 120, 0=manual only
    val INTERVAL_OPTIONS = intArrayOf(15, 30, 60, 120, 0)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun save(context: Context, url: String, user: String, password: String, intervalMin: Int) {
        prefs(context).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_USER, user.trim())
            .putString(KEY_PASSWORD, password)
            .remove(KEY_TOKEN_LEGACY)
            .putInt(KEY_INTERVAL_MIN, intervalMin)
            .commit()
    }

    fun getUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "") ?: ""

    fun getUser(context: Context): String =
        prefs(context).getString(KEY_USER, "") ?: ""

    fun getPassword(context: Context): String {
        val p = prefs(context).getString(KEY_PASSWORD, "") ?: ""
        if (p.isNotBlank()) return p
        // fallback: old token installs — user must re-login with password
        return ""
    }

    fun getIntervalMin(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_MIN, 30)

    fun isConfigured(context: Context): Boolean {
        return getUrl(context).isNotBlank() &&
                getUser(context).isNotBlank() &&
                getPassword(context).isNotBlank()
    }

    fun saveCache(context: Context, json: String) {
        prefs(context).edit()
            .putString(KEY_CACHE_JSON, json)
            .putLong(KEY_CACHE_AT, System.currentTimeMillis())
            .commit()
    }

    fun getCacheJson(context: Context): String? =
        prefs(context).getString(KEY_CACHE_JSON, null)

    fun getCacheAt(context: Context): Long =
        prefs(context).getLong(KEY_CACHE_AT, 0L)
}

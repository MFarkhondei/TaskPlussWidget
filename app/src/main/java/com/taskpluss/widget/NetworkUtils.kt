package com.taskpluss.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    /** صبر تا شبکه آماده شود (مخصوص سامسونگ بعد از Doze) */
    fun waitUntilOnline(context: Context, timeoutMs: Long = 12_000L): Boolean {
        val step = 400L
        var waited = 0L
        while (waited < timeoutMs) {
            if (isOnline(context)) return true
            try {
                Thread.sleep(step)
            } catch (_: InterruptedException) {
                break
            }
            waited += step
        }
        return isOnline(context)
    }
}

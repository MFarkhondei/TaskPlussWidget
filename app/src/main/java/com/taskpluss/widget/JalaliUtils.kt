package com.taskpluss.widget

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

object JalaliUtils {

    private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")
    private val PERSIAN_LOCALE = ULocale("fa_IR@calendar=persian")

    fun nowTehranJalaliString(): String {
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        return formatPersianDateTime(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    fun formatPersianDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        return "%d/%02d/%02d %02d:%02d".format(year, month, day, hour, minute)
    }

    /** فقط تاریخ شمسی YYYY/MM/DD از میلی‌ثانیه */
    fun jalaliDateFromMillis(millis: Long): String {
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        cal.timeInMillis = millis
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%d/%02d/%02d".format(y, m, d)
    }

    fun newTaskId(): String {
        val rand = java.util.UUID.randomUUID().toString().replace("-", "").take(9)
        return "task_${System.currentTimeMillis()}_$rand"
    }
}

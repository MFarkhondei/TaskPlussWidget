package com.taskpluss.widget

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

/**
 * تاریخ/ساعت شمسی مطابق فرانت‌اند تسک‌پلاس:
 * formatPersianDateTime + getPersianDate (Intl fa-IR)
 * خروجی نمونه: 1405/05/13 21:05
 */
object JalaliUtils {

    private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")
    private val PERSIAN_LOCALE = ULocale("fa_IR@calendar=persian")

    /** همین لحظه به وقت تهران، فرمت YYYY/MM/DD HH:mm */
    fun nowTehranJalaliString(): String {
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1 // ICU ماه از ۰
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return formatPersianDateTime(year, month, day, hour, minute)
    }

    /** همان formatPersianDateTime فرانت‌اند */
    fun formatPersianDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        return "%d/%02d/%02d %02d:%02d".format(year, month, day, hour, minute)
    }

    /** شناسه تسک مثل فرانت: task_{timestamp}_{random} */
    fun newTaskId(): String {
        val rand = java.util.UUID.randomUUID().toString().replace("-", "").take(9)
        return "task_${System.currentTimeMillis()}_$rand"
    }
}

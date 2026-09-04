package com.taskpluss.widget

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

object JalaliUtils {

    private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")
    private val PERSIAN_LOCALE = ULocale("fa_IR@calendar=persian")

    data class JalaliParts(val year: Int, val month: Int, val day: Int)
    data class TimeParts(val hour: Int, val minute: Int)

    fun nowTehranJalaliString(): String {
        val p = nowParts()
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        return formatPersianDateTime(
            p.year, p.month, p.day,
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    fun nowParts(): JalaliParts {
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        return JalaliParts(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatPersianDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        return "%d/%02d/%02d %02d:%02d".format(year, month, day, hour, minute)
    }

    fun formatJalaliDate(year: Int, month: Int, day: Int): String {
        return "%d/%02d/%02d".format(year, month, day)
    }

    fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun parseJalaliDate(s: String): JalaliParts? {
        val t = toLatinDigits(s.trim())
        if (t.isBlank()) return null
        val parts = t.split(" ", "T").first().split("/", "-")
        if (parts.size < 3) return null
        return try {
            JalaliParts(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: Exception) {
            null
        }
    }

    fun newTaskId(): String {
        val rand = java.util.UUID.randomUUID().toString().replace("-", "").take(9)
        return "task_${System.currentTimeMillis()}_$rand"
    }

    fun parseTime(s: String): TimeParts? {
        val normalized = toLatinDigits(s.trim())
        val match = Regex("(?:^|\\s)(\\d{1,2}):(\\d{2})$").find(normalized) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) TimeParts(hour, minute) else null
    }

    fun isToday(value: String): Boolean {
        val date = parseJalaliDate(value) ?: return false
        val today = nowParts()
        return date.year == today.year && date.month == today.month && date.day == today.day
    }

    fun formatTime(value: String): String? {
        return parseTime(value)?.let { "%02d:%02d".format(it.hour, it.minute) }
    }

    /**
     * تبدیل تاریخ و ساعت شمسی ذخیره‌شده به epoch میلی‌ثانیه برای AlarmManager.
     */
    fun toTehranMillis(value: String): Long? {
        val date = parseJalaliDate(value) ?: return null
        val time = parseTime(value) ?: return null
        return try {
            val cal = Calendar.getInstance(TEHRAN, PERSIAN_LOCALE)
            cal.clear()
            cal.isLenient = false
            cal.set(Calendar.YEAR, date.year)
            cal.set(Calendar.MONTH, date.month - 1)
            cal.set(Calendar.DAY_OF_MONTH, date.day)
            cal.set(Calendar.HOUR_OF_DAY, time.hour)
            cal.set(Calendar.MINUTE, time.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun toLatinDigits(value: String): String {
        return value.map { ch ->
            when (ch) {
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                else -> ch
            }
        }.joinToString("")
    }
}

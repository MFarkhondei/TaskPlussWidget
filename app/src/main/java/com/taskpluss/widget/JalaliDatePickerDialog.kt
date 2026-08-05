package com.taskpluss.widget

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView

/** انتخاب تاریخ شمسی با NumberPicker */
object JalaliDatePickerDialog {

    fun show(
        context: Context,
        initial: JalaliUtils.JalaliParts?,
        onPicked: (year: Int, month: Int, day: Int) -> Unit
    ) {
        val now = JalaliUtils.nowParts()
        val year0 = initial?.year ?: now.year
        val month0 = (initial?.month ?: now.month).coerceIn(1, 12)
        val day0 = (initial?.day ?: now.day).coerceIn(1, 31)

        val pad = (12 * context.resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad, pad, pad)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }

        fun label(t: String) = TextView(context).apply {
            text = t
            setTextColor(0xFF94A3B8.toInt())
            textSize = 12f
            setPadding(pad / 2, 0, pad / 2, 0)
        }

        val yearPicker = NumberPicker(context).apply {
            minValue = now.year - 20
            maxValue = now.year + 15
            value = year0.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        val monthPicker = NumberPicker(context).apply {
            minValue = 1
            maxValue = 12
            value = month0
            displayedValues = arrayOf(
                "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۱۰", "۱۱", "۱۲"
            )
            wrapSelectorWheel = false
        }
        val dayPicker = NumberPicker(context).apply {
            minValue = 1
            maxValue = JalaliUtils.daysInMonth(year0, month0)
            value = day0.coerceIn(1, maxValue)
            wrapSelectorWheel = false
        }

        fun refreshDays() {
            val y = yearPicker.value
            val m = monthPicker.value
            val max = JalaliUtils.daysInMonth(y, m)
            val old = dayPicker.value
            dayPicker.maxValue = max
            if (old > max) dayPicker.value = max
        }

        yearPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }
        monthPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }

        fun col(picker: NumberPicker, title: String) =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(label(title))
                addView(picker)
            }

        root.addView(col(yearPicker, "سال"))
        root.addView(col(monthPicker, "ماه"))
        root.addView(col(dayPicker, "روز"))

        AlertDialog.Builder(context)
            .setTitle("تاریخ شمسی")
            .setView(root)
            .setPositiveButton("تأیید") { _, _ ->
                onPicked(yearPicker.value, monthPicker.value, dayPicker.value)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}

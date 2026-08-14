package com.tradepluss.widget

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberUtils {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
    }
    private val formatter = DecimalFormat("#,###", symbols)

    fun format(value: Long): String = formatter.format(value)

    fun formatSigned(value: Long): String {
        val abs = format(kotlin.math.abs(value))
        return if (value >= 0) "+$abs" else "-$abs"
    }

    fun formatPercent(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return String.format(Locale.US, "%s%.2f%%", sign, value)
    }
}

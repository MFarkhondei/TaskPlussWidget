package com.tradepluss.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

object ChartHelper {

    fun drawChart(values: List<Long>, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(100), height.coerceAtLeast(60), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (values.isEmpty() || values.all { it == 0L }) {
            return bmp
        }

        val min = values.minOrNull() ?: 0L
        val max = values.maxOrNull() ?: 1L
        val range = (max - min).coerceAtLeast(1L).toFloat()

        val padding = 8f
        val chartW = width - padding * 2
        val chartH = height - padding * 2

        val points = values.mapIndexed { i, v ->
            val x = padding + (i.toFloat() / (values.size - 1).coerceAtLeast(1)) * chartW
            val y = padding + chartH - ((v - min).toFloat() / range) * chartH
            x to y
        }

        // Fill under line
        val fillPath = Path()
        fillPath.moveTo(points.first().first, height - padding)
        points.forEach { (x, y) -> fillPath.lineTo(x, y) }
        fillPath.lineTo(points.last().first, height - padding)
        fillPath.close()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, padding, 0f, height.toFloat(),
                Color.parseColor("#55F5C542"),
                Color.parseColor("#00F5C542"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        // Line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.parseColor("#F5C542")
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val linePath = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        canvas.drawPath(linePath, linePaint)

        // Dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F5C542")
        }
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B1220")
        }
        points.forEach { (x, y) ->
            canvas.drawCircle(x, y, 6f, outerPaint)
            canvas.drawCircle(x, y, 4f, dotPaint)
        }

        return bmp
    }
}

package com.taskpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat

/** رسم متن با فونت وزیر برای RemoteViews */
object WidgetText {

    private fun typeface(context: Context, bold: Boolean): Typeface {
        val id = if (bold) R.font.vazirmatn_bold else R.font.vazirmatn_regular
        return try {
            ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    fun setTitle(
        context: Context,
        rv: RemoteViews,
        viewId: Int,
        text: String,
        textSizeSp: Float = 11f,
        color: Int = 0xFFF8FAFC.toInt(),
        maxWidthDp: Int = 240,
        maxLines: Int = 3
    ) {
        val bmp = render(
            context, text, textSizeSp, color, bold = false,
            maxWidthDp = maxWidthDp, maxLines = maxLines, align = Align.RTL_START
        )
        rv.setImageViewBitmap(viewId, bmp)
    }

    fun setPriority(
        context: Context,
        rv: RemoteViews,
        viewId: Int,
        text: String,
        color: Int
    ) {
        if (text.isBlank()) {
            rv.setImageViewBitmap(viewId, emptyBitmap())
            return
        }
        val bmp = render(
            context, text, 11f, color, bold = false,
            maxWidthDp = 24, maxLines = 1, align = Align.CENTER
        )
        rv.setImageViewBitmap(viewId, bmp)
    }

    fun setLabel(
        context: Context,
        rv: RemoteViews,
        viewId: Int,
        text: String,
        textSizeSp: Float = 13f,
        color: Int = 0xFF94A3B8.toInt(),
        bold: Boolean = false,
        maxWidthDp: Int = 120,
        align: Align = Align.CENTER,
        tightFit: Boolean = false
    ) {
        val bmp = render(
            context, text, textSizeSp, color, bold = bold,
            maxWidthDp = maxWidthDp, maxLines = 1, align = align, tightFit = tightFit
        )
        rv.setImageViewBitmap(viewId, bmp)
    }

    enum class Align { RTL_START, CENTER, LTR_START }

    private fun emptyBitmap(): Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private fun render(
        context: Context,
        text: String,
        textSizeSp: Float,
        color: Int,
        bold: Boolean,
        maxWidthDp: Int,
        maxLines: Int,
        align: Align,
        tightFit: Boolean = false
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val maxWidthPx = (maxWidthDp * density).toInt().coerceAtLeast(32)
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(context, bold)
            textSize = textSizeSp * density
            this.color = color
            isSubpixelText = true
        }
        val alignment = when (align) {
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.RTL_START, Align.LTR_START -> Layout.Alignment.ALIGN_NORMAL
        }
        val dir = when (align) {
            Align.LTR_START -> TextDirectionHeuristics.LTR
            else -> TextDirectionHeuristics.RTL
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidthPx)
            .setAlignment(alignment)
            .setTextDirection(dir)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.0f)
            .build()
        val h = layout.height.coerceAtLeast((textSizeSp * density).toInt() + 2)
        val contentWidthPx = if (tightFit) {
            val lineW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            kotlin.math.ceil(lineW).toInt().coerceIn(1, maxWidthPx)
        } else {
            maxWidthPx
        }
        val bmp = Bitmap.createBitmap(contentWidthPx, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).also { layout.draw(it) }
        return bmp
    }
}

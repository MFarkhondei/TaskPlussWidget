package com.taskpluss.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
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
        textSizeSp: Float = 15f,
        color: Int = 0xFFF8FAFC.toInt(),
        maxWidthDp: Int = 220,
        maxLines: Int = 3
    ) {
        val bmp = render(
            context, text, textSizeSp, color, bold = true,
            maxWidthDp = maxWidthDp, maxLines = maxLines, alignEnd = true
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
            context, text, 14f, color, bold = true,
            maxWidthDp = 28, maxLines = 1, alignEnd = true
        )
        rv.setImageViewBitmap(viewId, bmp)
    }

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
        alignEnd: Boolean
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val maxWidthPx = (maxWidthDp * density).toInt().coerceAtLeast(40)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(context, bold)
            textSize = textSizeSp * density
            this.color = color
            isSubpixelText = true
        }
        val alignment = if (alignEnd) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidthPx)
            .setAlignment(alignment)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .build()
        val h = layout.height.coerceAtLeast((textSizeSp * density).toInt() + 4)
        val bmp = Bitmap.createBitmap(maxWidthPx, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        layout.draw(canvas)
        return bmp
    }
}

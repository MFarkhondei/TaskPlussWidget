package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.tradepluss.widget.model.AssetItem
import com.tradepluss.widget.model.WidgetResponse

object WidgetRenderer {

    private val gson = Gson()

    private val GOLD = Color.parseColor("#F5C542")
    private val WHITE = Color.parseColor("#F8FAFC")
    private val MUTED = Color.parseColor("#A8B2C1")
    private val SECONDARY = Color.parseColor("#94A3B8")
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")

    /** اندازه یکسان هدر جدول + نام/ارزش/درصد دارایی */
    private const val TABLE_SP = 13f

    private data class AssetSlot(
        val row: Int,
        val icon: Int,
        val name: Int,
        val value: Int,
        val toman: Int,
        val pct: Int
    )

    private val slots = arrayOf(
        AssetSlot(R.id.row_asset_1, R.id.iv_asset_1, R.id.iv_asset_1_name, R.id.iv_asset_1_value, R.id.iv_asset_1_toman, R.id.iv_asset_1_pct),
        AssetSlot(R.id.row_asset_2, R.id.iv_asset_2, R.id.iv_asset_2_name, R.id.iv_asset_2_value, R.id.iv_asset_2_toman, R.id.iv_asset_2_pct),
        AssetSlot(R.id.row_asset_3, R.id.iv_asset_3, R.id.iv_asset_3_name, R.id.iv_asset_3_value, R.id.iv_asset_3_toman, R.id.iv_asset_3_pct),
        AssetSlot(R.id.row_asset_4, R.id.iv_asset_4, R.id.iv_asset_4_name, R.id.iv_asset_4_value, R.id.iv_asset_4_toman, R.id.iv_asset_4_pct),
        AssetSlot(R.id.row_asset_5, R.id.iv_asset_5, R.id.iv_asset_5_name, R.id.iv_asset_5_value, R.id.iv_asset_5_toman, R.id.iv_asset_5_pct),
        AssetSlot(R.id.row_asset_6, R.id.iv_asset_6, R.id.iv_asset_6_name, R.id.iv_asset_6_value, R.id.iv_asset_6_toman, R.id.iv_asset_6_pct)
    )

    fun allIds(context: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetIds(ComponentName(context, TradePlussWidgetProvider::class.java))
    }

    fun showLoading(context: Context, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        for (id in ids) {
            val views = baseViews(appCtx, id)
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, "در حال بروزرسانی...", 11f, MUTED)
            mgr.updateAppWidget(id, views)
        }
    }

    fun applyResponse(context: Context, data: WidgetResponse, offline: Boolean = false, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        for (id in ids) {
            val views = baseViews(appCtx, id)
            applyData(appCtx, views, data, offline)
            mgr.updateAppWidget(id, views)
        }
    }

    fun applyCache(context: Context, offline: Boolean = true, widgetIds: IntArray? = null): Boolean {
        val json = Prefs.getCacheJson(context) ?: return false
        val data = try {
            gson.fromJson(json, WidgetResponse::class.java)
        } catch (_: Exception) {
            null
        } ?: return false
        if (!data.success) return false
        applyResponse(context, data, offline, widgetIds)
        return true
    }

    fun showError(context: Context, message: String, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        if (applyCache(appCtx, offline = true, widgetIds = ids)) return
        for (id in ids) {
            val views = baseViews(appCtx, id)
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_total_assets, "خطا", 26f, GOLD, bold = true)
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, message.replace("\n", " ").take(40), 11f, MUTED)
            mgr.updateAppWidget(id, views)
        }
    }

    fun showNotConfigured(context: Context, widgetId: Int) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val views = baseViews(appCtx, widgetId)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_total_assets, "تنظیم نشده", 24f, GOLD, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, "اپ را باز کنید", 11f, MUTED)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_pnl, "-", 15f, WHITE, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_pnl_pct, "", TABLE_SP, MUTED)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_buy, "-", 15f, WHITE, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_asset_1_name, "برای تنظیم روی ویجت بزنید", TABLE_SP, WHITE, bold = true, maxWidthDp = 140f)
        for (i in 1 until slots.size) {
            views.setViewVisibility(slots[i].row, View.GONE)
        }
        mgr.updateAppWidget(widgetId, views)
    }

    fun fetchAndApply(context: Context, widgetIds: IntArray? = null): Boolean {
        val appCtx = context.applicationContext
        if (!Prefs.isConfigured(appCtx)) return false
        return try {
            val (data, raw) = ApiClient.fetchWidgetData(
                Prefs.getUrl(appCtx),
                Prefs.getUser(appCtx),
                Prefs.getPassword(appCtx)
            )
            if (data.success) {
                Prefs.saveCache(appCtx, raw)
                applyResponse(appCtx, data, offline = false, widgetIds = widgetIds)
                true
            } else {
                showError(appCtx, data.message ?: "ناموفق", widgetIds)
                false
            }
        } catch (e: Exception) {
            showError(appCtx, e.message ?: "شبکه", widgetIds)
            false
        }
    }

    private fun baseViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        FontHelper.setTextBitmap(views, context, R.id.iv_title, context.getString(R.string.widget_name), 18f, GOLD, bold = true)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_total, context.getString(R.string.total_assets), 13f, SECONDARY)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_toman_total, context.getString(R.string.toman), 12f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_pnl, context.getString(R.string.daily_pnl), 12f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_buy, context.getString(R.string.daily_buy), 12f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_toman_buy, context.getString(R.string.toman), 12f, MUTED)

        FontHelper.setTextBitmap(views, context, R.id.iv_header_assets, context.getString(R.string.assets), TABLE_SP, MUTED, bold = true, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, R.id.iv_header_value, context.getString(R.string.current_value), TABLE_SP, MUTED, bold = true, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, R.id.iv_header_pct, context.getString(R.string.change_percent), TABLE_SP, MUTED, bold = true, align = FontHelper.Align.CENTER)

        val refreshIntent = Intent(context, SilentRefreshActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getActivity(
                context, 1000 + widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 2000 + widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    private fun applyData(context: Context, views: RemoteViews, data: WidgetResponse, offline: Boolean) {
        if (!data.success) {
            FontHelper.setTextBitmap(views, context, R.id.iv_total_assets, "خطا", 26f, GOLD, bold = true)
            FontHelper.setTextBitmap(views, context, R.id.iv_updated_at, data.message ?: "ناموفق", 11f, MUTED)
            return
        }

        FontHelper.setTextBitmap(
            views, context, R.id.iv_total_assets,
            NumberUtils.format(data.totalAssetsToman), 26f, GOLD, bold = true
        )

        val pnl = data.dailyProfitToman
        val pnlColor = if (pnl >= 0) POS else NEG
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_pnl, NumberUtils.formatSigned(pnl), 15f, pnlColor, bold = true)
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_pnl_pct, NumberUtils.formatPercent(data.dailyProfitPercent), 13f, pnlColor)
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_buy, NumberUtils.format(data.dailyBuyToman), 15f, WHITE, bold = true)

        val stamp = data.updatedAt ?: ""
        FontHelper.setTextBitmap(
            views, context, R.id.iv_updated_at,
            if (offline) "آفلاین · $stamp" else stamp, 11f, MUTED
        )

        for (i in slots.indices) {
            val slot = slots[i]
            if (i < data.items.size) {
                views.setViewVisibility(slot.row, View.VISIBLE)
                bindAsset(context, views, slot, data.items[i])
            } else {
                views.setViewVisibility(slot.row, View.GONE)
            }
        }
    }

    private fun bindAsset(context: Context, views: RemoteViews, slot: AssetSlot, item: AssetItem) {
        val name = item.coinName.ifBlank { item.symbol }.ifBlank { "-" }
        val positive = item.profitPercent >= 0
        val pctColor = if (positive) POS else NEG
        val arrow = if (positive) " ↑" else " ↓"

        FontHelper.setTextBitmap(views, context, slot.name, name, TABLE_SP, WHITE, bold = true, maxWidthDp = 120f)
        FontHelper.setTextBitmap(views, context, slot.value, NumberUtils.format(item.currentValue), TABLE_SP, WHITE, bold = true, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, slot.toman, context.getString(R.string.toman), TABLE_SP, MUTED, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(
            views, context, slot.pct,
            NumberUtils.formatPercent(item.profitPercent) + arrow,
            TABLE_SP, pctColor, bold = true, align = FontHelper.Align.CENTER
        )

        views.setImageViewResource(slot.icon, iconForSymbol(item.symbol, item.coinName))
    }

    private fun iconForSymbol(symbol: String, coinName: String): Int {
        val s = symbol.uppercase()
        val n = coinName
        return when {
            s.contains("BTC") || n.contains("بیت") -> R.drawable.ic_asset_btc
            s.contains("ETH") || n.contains("اتری") -> R.drawable.ic_asset_eth
            s.contains("USDC") || s.contains("USDT") || n.contains("یو اس") || n.contains("تتر") -> R.drawable.ic_asset_usdc
            s.contains("GOLD") || n.contains("طلا") -> R.drawable.ic_asset_gold
            s.contains("SILVER") || n.contains("نقره") -> R.drawable.ic_asset_silver
            s.contains("COPPER") || s.contains("CU") || n.contains("مس") -> R.drawable.ic_asset_copper
            else -> R.drawable.ic_coin_default
        }
    }
}

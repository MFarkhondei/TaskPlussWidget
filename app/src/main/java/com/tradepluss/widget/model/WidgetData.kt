package com.tradepluss.widget.model

data class WidgetResponse(
    val success: Boolean = false,
    val message: String? = null,
    val username: String? = null,
    val totalAssetsToman: Long = 0,
    val dailyBuyToman: Long = 0,
    val dailyProfitToman: Long = 0,
    val dailyProfitPercent: Double = 0.0,
    val items: List<AssetItem> = emptyList(),
    val weeklyDates: List<String> = emptyList(),
    val weeklyValues: List<Long> = emptyList(),
    val updatedAt: String? = null
)

data class AssetItem(
    val coinName: String = "",
    val symbol: String = "",
    val currentPrice: Long = 0,
    val currentValue: Long = 0,
    val profitPercent: Double = 0.0
)

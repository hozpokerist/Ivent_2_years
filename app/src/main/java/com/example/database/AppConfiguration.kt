package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configuration")
data class AppConfiguration(
    @PrimaryKey val id: Int = 1,
    val autoBuyEnabled: Boolean = false,
    val scanMode: String = "grid_16", // "grid_16" (Сканирование блока из 16 элементов), "market_ocr" (Обычный OCR), "view_tree" (Accessibility Tree)
    val useViewScanning: Boolean = false,
    val useSearchCycles: Boolean = true,
    val cycle1DurationMin: Int = 5,
    val cycle1RandomRangeSec: Int = 30,
    val cycle2DurationMin: Int = 7,
    val cycle2RandomRangeSec: Int = 45,
    val cycle3DurationMin: Int = 10,
    val cycle3RandomRangeSec: Int = 60,
    val targetItemName: String = "",
    val priceThreshold: Double = 0.0,
    val usePriceThreshold: Boolean = false, // По умолчанию выключено, чтобы покупать любой лот указанного ресурса
    val isLessThanOperator: Boolean = true,
    val enableActualBuying: Boolean = true, // По умолчанию покупка включена
    val scanIntervalMs: Long = 200L,
    val tabSwitchIntervalMs: Long = 15L,
    val tabSwitchRandomizationMs: Long = 5L,
    val clickRandomizationRadiusPx: Float = 6f,
    val gridCloseDelayMs: Long = 1000L, // Задержка перед повторным нажатием на '2' после закрытия крестиком 'X' (мс)
    val gridOpenDelayMs: Long = 2000L, // Задержка на открытие/прогрузку сетки после нажатия на '2' (мс)
    val verboseOcrLogging: Boolean = false,
    val calibratedOreX: Float? = null,
    val calibratedOreY: Float? = null,
    val calibratedCopperX: Float? = null,
    val calibratedCopperY: Float? = null,
    val calibratedSilverX: Float? = null,
    val calibratedSilverY: Float? = null,
    val calibratedGoldX: Float? = null,
    val calibratedGoldY: Float? = null,
    val calibratedSapX: Float? = null,
    val calibratedSapY: Float? = null,
    val buyButtonX: Float? = null,
    val buyButtonY: Float? = null,
    val refreshButtonX: Float? = null,
    val refreshButtonY: Float? = null,
    val calibratedConfirmX: Float? = null,
    val calibratedConfirmY: Float? = null,
    val checkAreaX: Float? = null,
    val checkAreaY: Float? = null
)

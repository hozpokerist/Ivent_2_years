package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppConfiguration
import com.example.database.AppDatabase
import com.example.database.PurchaseRecord
import com.example.service.AutoBuyerLogs
import com.example.service.LootBuyerAccessibilityService
import com.example.service.MediaProjectionHelper
import com.example.service.OpenCvVisionScanner
import com.example.service.OverlayControlService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val defaultConfig = AppConfiguration(
        targetItemName = "Медь",
        priceThreshold = 0.0,
        usePriceThreshold = false,
        autoBuyEnabled = false,
        scanMode = "grid_16",
        enableActualBuying = true,
        useSearchCycles = false
    )

    val config: StateFlow<AppConfiguration> = db.configurationDao().getConfigurationFlow()
        .map { it ?: defaultConfig }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = defaultConfig
        )

    val logs: StateFlow<List<String>> = AutoBuyerLogs.logs
    val purchases: StateFlow<List<PurchaseRecord>> = db.purchaseDao().getAllPurchases()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _cooldownMs = MutableStateFlow(0L)
    val cooldownMs: StateFlow<Long> = _cooldownMs.asStateFlow()

    init {
        // Ensure default config exists in DB
        viewModelScope.launch {
            val existing = db.configurationDao().getConfiguration()
            if (existing == null) {
                db.configurationDao().saveConfiguration(
                    AppConfiguration(
                        targetItemName = "Медь",
                        priceThreshold = 50.0,
                        autoBuyEnabled = false,
                        enableActualBuying = false,
                        useSearchCycles = true
                    )
                )
            }
        }

        // Monitor service status & cooldown
        viewModelScope.launch {
            while (true) {
                _isServiceActive.value = LootBuyerAccessibilityService.isServiceRunning
                _cooldownMs.value = LootBuyerAccessibilityService.getCooldownRemainingMs()
                delay(1000)
            }
        }
    }

    fun updateConfig(updated: AppConfiguration) {
        viewModelScope.launch {
            db.configurationDao().saveConfiguration(updated)
        }
    }

    fun toggleAutoBuy() {
        viewModelScope.launch {
            val current = db.configurationDao().getConfiguration() ?: config.value
            val newStatus = !current.autoBuyEnabled
            val updated = current.copy(autoBuyEnabled = newStatus)
            db.configurationDao().saveConfiguration(updated)

            val context = getApplication<Application>()

            if (newStatus) {
                LootBuyerAccessibilityService.instance?.startAutomation()
                val mode = if (updated.enableActualBuying) "⚡ РЕАЛЬНАЯ ПОКУПКА (ВЫКУП ВКЛЮЧЕН)" else "👁 ТОЛЬКО МОНИТОРИНГ (Покупка ВЫКЛЮЧЕНА)"
                AutoBuyerLogs.addLog("▶ [UI] Бот ЗАПУЩЕН пользователем. Режим: $mode")

                // Automatically launch floating circle overlay if overlay permission is granted
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                    val overlayIntent = Intent(context, OverlayControlService::class.java).apply {
                        action = OverlayControlService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(overlayIntent)
                    } else {
                        context.startService(overlayIntent)
                    }
                }
            } else {
                LootBuyerAccessibilityService.instance?.stopAutomation()
                AutoBuyerLogs.addLog("⏹ [UI] Автопокупка остановлена пользователем")
            }
        }
    }

    fun resetCooldown() {
        LootBuyerAccessibilityService.resetCooldown()
    }

    fun clearLogs() {
        AutoBuyerLogs.clearLogs()
    }

    fun clearPurchases() {
        viewModelScope.launch {
            db.purchaseDao().clearAll()
            AutoBuyerLogs.addLog("🗑️ История покупок очищена")
        }
    }

    fun triggerOpenCvBoardScan() {
        viewModelScope.launch {
            val screenshot = MediaProjectionHelper.getLatestScreenshot()
            if (screenshot != null) {
                AutoBuyerLogs.addLog("🔍 [ТЕСТ] Запуск прямого анализа 16 элементов через OpenCV...")
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(screenshot, 0)
                val ocrLines = try {
                    val res = com.google.android.gms.tasks.Tasks.await(recognizer.process(inputImage))
                    res.textBlocks.flatMap { it.lines }
                } catch (e: Exception) {
                    emptyList()
                }

                val report = OpenCvVisionScanner.scanResourceHuntBoard(screenshot, ocrLines, logToUi = true)
                screenshot.recycle()
            } else {
                AutoBuyerLogs.addLog("⚠️ [ТЕСТ OPENCV] Нет активного скриншота экрана. Нажмите 'Разрешить OCR' для захвата экрана.")
            }
        }
    }

    fun generateSampleGridTemplate(itemName: String) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val templatesDir = File(context.filesDir, "element_templates")
                if (!templatesDir.exists()) templatesDir.mkdirs()

                val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // High fidelity color palettes for all 10 items
                val colors = mapOf(
                    "медь" to Color.rgb(205, 127, 50),
                    "серебро" to Color.rgb(192, 192, 192),
                    "золото" to Color.rgb(255, 215, 0),
                    "руда" to Color.rgb(105, 95, 120),
                    "эль" to Color.rgb(180, 100, 30),
                    "ммт" to Color.rgb(240, 160, 20),
                    "смт" to Color.rgb(200, 200, 205),
                    "рубин" to Color.rgb(220, 20, 60),
                    "сапфир" to Color.rgb(0, 100, 255),
                    "свиток" to Color.rgb(218, 165, 32)
                )

                val bgColor = colors[itemName.lowercase().trim()] ?: Color.rgb(100, 149, 237)
                paint.color = bgColor
                canvas.drawCircle(60f, 60f, 52f, paint)

                paint.color = Color.WHITE
                paint.textSize = 18f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(itemName.take(6), 60f, 66f, paint)

                val targetFile = File(templatesDir, "${itemName.lowercase().trim()}.png")
                FileOutputStream(targetFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                AutoBuyerLogs.addLog("✨ Создан шаблон ресурса '$itemName' (${targetFile.name})")
            } catch (e: Exception) {
                AutoBuyerLogs.addLog("⚠️ Ошибка создания шаблона: ${e.message}")
            }
        }
    }
}

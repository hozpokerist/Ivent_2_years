package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayControlService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var windowManager: WindowManager? = null
    private var rootOverlayView: View? = null
    private var updateJob: Job? = null

    // UI references
    private var bubbleCircle: FrameLayout? = null
    private var bubbleIcon: TextView? = null
    private var bubbleStatusText: TextView? = null
    private var menuPanel: LinearLayout? = null
    private var statusDetailText: TextView? = null
    private var btnToggleBot: Button? = null

    private var isExpanded = false

    companion object {
        const val CHANNEL_ID = "overlay_control_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        @Volatile
        var instance: OverlayControlService? = null
            private set
    }

    fun isControlPanelVisible(): Boolean = rootOverlayView != null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_START -> showOverlay()
            ACTION_STOP -> stopSelf()
            else -> showOverlay()
        }

        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (rootOverlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val metrics = resources.displayMetrics
        val density = metrics.density
        val bubbleSizePx = (64 * density).toInt()
        val screenWidthPx = metrics.widthPixels
        val screenHeightPx = metrics.heightPixels
        val panelWidthPx = (250 * density).toInt()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidthPx - bubbleSizePx - (16 * density).toInt()).coerceAtLeast(0)
            y = (200 * density).toInt()
        }

        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        // Floating Circle Button (Кружок бота)
        val circleLayout = FrameLayout(this).apply {
            this.layoutParams = LinearLayout.LayoutParams(bubbleSizePx, bubbleSizePx)
            val circleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(244, 67, 54)) // Red by default (inactive)
                setStroke((3 * density).toInt(), Color.WHITE)
            }
            background = circleBg
            elevation = 20f
        }

        val iconView = TextView(this).apply {
            text = "⏹"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val subText = TextView(this).apply {
            text = "OFF"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setPadding(0, 0, 0, (6 * density).toInt())
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        circleLayout.addView(iconView)
        circleLayout.addView(subText)
        bubbleCircle = circleLayout
        bubbleIcon = iconView
        bubbleStatusText = subText

        // Expandable Quick Panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val panelBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(Color.argb(240, 18, 22, 34))
                setStroke((1.5f * density).toInt(), Color.argb(180, 255, 255, 255))
            }
            background = panelBg
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            elevation = 24f
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(panelWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (8 * density).toInt()
            this.layoutParams = lp
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "⚡ Меню бота 4x4"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            this.layoutParams = lp
        }

        val btnClosePanel = TextView(this).apply {
            text = "✕"
            setTextColor(Color.LTGRAY)
            textSize = 16f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                toggleMenuPanel(wmParams, rootContainer, screenWidthPx, panelWidthPx, density)
            }
        }

        titleRow.addView(titleView)
        titleRow.addView(btnClosePanel)

        val statusView = TextView(this).apply {
            text = "Статус: Ожидание..."
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
        }

        val btnToggle = Button(this).apply {
            text = "▶ Включить бота"
            setBackgroundColor(Color.rgb(76, 175, 80))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                toggleBot()
            }
        }

        val btnToggleBuying = Button(this).apply {
            text = "🛒 Реальная покупка: ..."
            setBackgroundColor(Color.rgb(255, 152, 0))
            setTextColor(Color.WHITE)
            textSize = 12f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * density).toInt()
            this.layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val config = db.configurationDao().getConfiguration()
                    if (config != null) {
                        val newMode = !config.enableActualBuying
                        db.configurationDao().saveConfiguration(config.copy(enableActualBuying = newMode))
                        val modeStr = if (newMode) "⚡ ВКЛЮЧЕНА (Бот кликает и покупает!)" else "👁 ВЫКЛЮЧЕНА (Только мониторинг)"
                        AutoBuyerLogs.addLog("🛒 [РЕЖИМ ПОКУПКИ] Реальная покупка $modeStr")
                    }
                }
            }
        }

        val btnResetCooldown = Button(this).apply {
            text = "🔄 Сброс Кулдауна"
            setBackgroundColor(Color.rgb(33, 150, 243))
            setTextColor(Color.WHITE)
            textSize = 12f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * density).toInt()
            this.layoutParams = lp
            setOnClickListener {
                LootBuyerAccessibilityService.resetCooldown()
            }
        }

        val btnScanViewNow = Button(this).apply {
            text = "🔍 Скан 16 слотов (OpenCV)"
            setBackgroundColor(Color.rgb(156, 39, 176))
            setTextColor(Color.WHITE)
            textSize = 12f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * density).toInt()
            this.layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val config = db.configurationDao().getConfiguration()
                    if (config != null) {
                        AutoBuyerLogs.addLog("⚡ [МЕНЮ ОВЕРЛЕЯ] Запуск сканирования 16 элементов...")
                        LootBuyerAccessibilityService.instance?.let { service ->
                            if (MediaProjectionHelper.hasProjection()) {
                                service.performGrid16Scan(config, isManualScan = true)
                            } else {
                                service.performNativeViewScan(config)
                            }
                        }
                    }
                }
            }
        }

        val btnClose = Button(this).apply {
            text = "✕ Закрыть кружок"
            setBackgroundColor(Color.rgb(100, 116, 139))
            setTextColor(Color.WHITE)
            textSize = 11f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (4 * density).toInt()
            this.layoutParams = lp
            setOnClickListener {
                stopSelf()
            }
        }

        panel.addView(titleRow)
        panel.addView(statusView)
        panel.addView(btnToggle)
        panel.addView(btnToggleBuying)
        panel.addView(btnResetCooldown)
        panel.addView(btnScanViewNow)
        panel.addView(btnClose)

        rootContainer.addView(circleLayout)
        rootContainer.addView(panel)

        menuPanel = panel
        statusDetailText = statusView
        btnToggleBot = btnToggle

        // Drag and Click handling on the circle
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        circleLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = wmParams.x
                    initialY = wmParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val curWidth = if (isExpanded) panelWidthPx else bubbleSizePx
                    val maxX = (screenWidthPx - curWidth).coerceAtLeast(0)
                    val maxY = (screenHeightPx - (80 * density).toInt()).coerceAtLeast(0)
                    wmParams.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, maxX)
                    wmParams.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn((20 * density).toInt(), maxY)
                    windowManager?.updateViewLayout(rootContainer, wmParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - touchStartTime
                    val moveDist = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                    
                    if (clickDuration < 300 && moveDist < 20) {
                        // Short Tap -> Toggle Bot directly!
                        toggleBot()
                    } else if (clickDuration >= 400 && moveDist < 20) {
                        // Long Press -> Open/Close mini settings menu with safe alignment
                        toggleMenuPanel(wmParams, rootContainer, screenWidthPx, panelWidthPx, density)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(rootContainer, wmParams)
            rootOverlayView = rootContainer

            updateJob = serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                while (true) {
                    val config = db.configurationDao().getConfiguration()
                    val isAccConnected = LootBuyerAccessibilityService.isServiceRunning
                    val cooldownMs = LootBuyerAccessibilityService.getCooldownRemainingMs()

                    val botActive = config?.autoBuyEnabled == true

                    // Update Circle UI
                    val circleBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (botActive) Color.rgb(76, 175, 80) else Color.rgb(244, 67, 54))
                        setStroke((3 * density).toInt(), Color.WHITE)
                    }
                    circleLayout.background = circleBg
                    iconView.text = if (botActive) "▶" else "⏹"
                    subText.text = if (botActive) "ON" else "OFF"

                    // Update Panel UI
                    btnToggle.text = if (botActive) "⏹ Остановить бота" else "▶ Запустить бота"
                    btnToggle.setBackgroundColor(if (botActive) Color.rgb(244, 67, 54) else Color.rgb(76, 175, 80))

                    val isRealBuying = config?.enableActualBuying == true
                    btnToggleBuying.text = if (isRealBuying) "🛒 Покупка: ВКЛ (Кликает)" else "👁 Покупка: ВЫКЛ (Мониторинг)"
                    btnToggleBuying.setBackgroundColor(if (isRealBuying) Color.rgb(255, 152, 0) else Color.rgb(96, 125, 139))

                    statusView.text = when {
                        !isAccConnected -> "⚠️ Спец. возможности выключены"
                        cooldownMs > 0 -> "⏳ Кулдаун: ${cooldownMs / 1000} сек"
                        botActive -> "🟢 Работает (Поиск: ${config?.targetItemName ?: "—"})\n${if (isRealBuying) "⚡ Режим: РЕАЛЬНАЯ ПОКУПКА" else "👁 Режим: ТОЛЬКО МОНИТОРИНГ"}"
                        else -> "⚪ Остановлен (Нажмите для старта)"
                    }

                    delay(1000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleBot() {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val config = db.configurationDao().getConfiguration()
            val newEnabled = !(config?.autoBuyEnabled ?: false)
            val updated = config?.copy(autoBuyEnabled = newEnabled)
            if (updated != null) {
                db.configurationDao().saveConfiguration(updated)
                if (newEnabled) {
                    LootBuyerAccessibilityService.instance?.startAutomation()
                    val mode = if (updated.enableActualBuying) "⚡ РЕАЛЬНАЯ ПОКУПКА (ВЫКУП ВКЛЮЧЕН)" else "👁 ТОЛЬКО МОНИТОРИНГ (Покупка ВЫКЛЮЧЕНА)"
                    AutoBuyerLogs.addLog("▶ [КРУЖОК ОВЕРЛЕЯ] Бот ЗАПУЩЕН. Режим: $mode")
                } else {
                    LootBuyerAccessibilityService.instance?.stopAutomation()
                    AutoBuyerLogs.addLog("⏹ [КРУЖОК ОВЕРЛЕЯ] Бот ОСТАНОВЛЕН")
                }
            }
        }
    }

    private fun toggleMenuPanel(
        wmParams: WindowManager.LayoutParams,
        rootContainer: View,
        screenWidthPx: Int,
        panelWidthPx: Int,
        density: Float
    ) {
        isExpanded = !isExpanded
        if (isExpanded) {
            // Shift left if the panel would overflow off the right edge of screen
            if (wmParams.x + panelWidthPx > screenWidthPx) {
                wmParams.x = (screenWidthPx - panelWidthPx - (16 * density).toInt()).coerceAtLeast(0)
            }
            menuPanel?.visibility = View.VISIBLE
        } else {
            menuPanel?.visibility = View.GONE
        }
        windowManager?.updateViewLayout(rootContainer, wmParams)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ivent 2 Years Bot Control",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ivent 2 Years Bot")
            .setContentText("Плавающая кнопка-кружок активна")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        updateJob?.cancel()
        rootOverlayView?.let {
            windowManager?.removeView(it)
            rootOverlayView = null
        }
        super.onDestroy()
    }
}


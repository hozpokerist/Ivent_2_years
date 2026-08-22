package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AppConfiguration
import com.example.service.AutoBuyerLogs
import com.example.service.LootBuyerAccessibilityService
import com.example.service.MediaProjectionHelper
import com.example.service.OverlayControlService
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val cooldownMs by viewModel.cooldownMs.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Бот", "4x4 Сетка", "Циклы", "Логи & История", "Калибровка")

    // Launcher for MediaProjection (Screen Capture)
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            MediaProjectionHelper.initProjection(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!,
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                density = metrics.densityDpi
            )
            AutoBuyerLogs.addLog("✅ [OCR] Захват экрана успешно авторизован (${metrics.widthPixels}x${metrics.heightPixels})")
            Toast.makeText(context, "Захват экрана авторизован!", Toast.LENGTH_SHORT).show()
        } else {
            AutoBuyerLogs.addLog("❌ [OCR] Авторизация захвата экрана отклонена")
            Toast.makeText(context, "Захват экрана отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ Ivent 2 Years",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("app_title")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isServiceActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = if (isServiceActive) "Сервис активен" else "Сервис выключен",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("btn_accessibility_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = "Настройки спец. возможностей"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    val icon = when (index) {
                        0 -> Icons.Default.PlayArrow
                        1 -> Icons.Default.GridOn
                        2 -> Icons.Default.Autorenew
                        3 -> Icons.Default.History
                        else -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title, maxLines = 1) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        modifier = Modifier.testTag("tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> DashboardTab(
                    config = config,
                    isServiceActive = isServiceActive,
                    cooldownMs = cooldownMs,
                    onToggleAutoBuy = { viewModel.toggleAutoBuy() },
                    onResetCooldown = { viewModel.resetCooldown() },
                    onUpdateConfig = { viewModel.updateConfig(it) },
                    onRequestMediaProjection = {
                        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onToggleOverlay = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, OverlayControlService::class.java).apply {
                                action = OverlayControlService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            Toast.makeText(context, "Плавающий оверлей запущен", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestOpenCvScan = { viewModel.triggerOpenCvBoardScan() }
                )
                1 -> GridRecognitionTab(
                    config = config,
                    onUpdateConfig = { viewModel.updateConfig(it) },
                    onGenerateTemplate = { viewModel.generateSampleGridTemplate(it) },
                    onTestOpenCvScan = { viewModel.triggerOpenCvBoardScan() }
                )
                2 -> SearchCyclesTab(
                    config = config,
                    onUpdateConfig = { viewModel.updateConfig(it) }
                )
                3 -> LogsAndHistoryTab(
                    logs = logs,
                    purchases = purchases,
                    onClearLogs = { viewModel.clearLogs() },
                    onClearPurchases = { viewModel.clearPurchases() }
                )
                4 -> CalibrationTab(
                    config = config,
                    onUpdateConfig = { viewModel.updateConfig(it) }
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    config: AppConfiguration,
    isServiceActive: Boolean,
    cooldownMs: Long,
    onToggleAutoBuy: () -> Unit,
    onResetCooldown: () -> Unit,
    onUpdateConfig: (AppConfiguration) -> Unit,
    onRequestMediaProjection: () -> Unit,
    onToggleOverlay: () -> Unit,
    onTestOpenCvScan: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Control Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (config.autoBuyEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (config.autoBuyEnabled) "🟢 Бот активен" else "⚪ Бот в режиме ожидания",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (config.autoBuyEnabled) 
                                "Выполняется автопоиск и сканирование сетки" 
                                else "Нажмите кнопку ниже для появления плавающего кружка. Запуск производится кликом по кружку на экране игры!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (!config.autoBuyEnabled) {
                    Button(
                        onClick = onToggleAutoBuy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("btn_start_bot_overlay")
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🚀 Запустить бота (Показать кружок)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onToggleAutoBuy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("btn_stop_bot")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⏹ Остановить бота", fontWeight = FontWeight.Bold)
                    }
                }

                if (cooldownMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "⏳ Пауза после покупки",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Осталось: ${cooldownMs / 1000 / 60} мин ${(cooldownMs / 1000) % 60} сек",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Button(
                                onClick = onResetCooldown,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("btn_reset_cooldown")
                            ) {
                                Text("Сброс")
                            }
                        }
                    }
                }
            }
        }

        // Speed & Timing Settings Card for Button 2 and Close X
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⏱️ Скорость клика на '2' и закрытия на крестик 'X'", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Настройте время задержки между закрытием окна по крестику 'X' и повторным открытием кнопкой '2':",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = config.gridCloseDelayMs.toString(),
                        onValueChange = { onUpdateConfig(config.copy(gridCloseDelayMs = it.toLongOrNull() ?: 1000L)) },
                        label = { Text("Пауза после 'X' (мс)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = { Text("Перед нажатием на '2'") }
                    )
                    OutlinedTextField(
                        value = config.gridOpenDelayMs.toString(),
                        onValueChange = { onUpdateConfig(config.copy(gridOpenDelayMs = it.toLongOrNull() ?: 2000L)) },
                        label = { Text("Пауза после '2' (мс)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = { Text("Загрузка 16 ячеек") }
                    )
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Пресеты:", style = MaterialTheme.typography.labelSmall)
                    FilledTonalButton(
                        onClick = { onUpdateConfig(config.copy(gridCloseDelayMs = 500L, gridOpenDelayMs = 1200L)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Быстро (0.5с/1.2с)", fontSize = 10.sp, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { onUpdateConfig(config.copy(gridCloseDelayMs = 1000L, gridOpenDelayMs = 2000L)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚖️ Норм (1.0с/2.0с)", fontSize = 10.sp, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { onUpdateConfig(config.copy(gridCloseDelayMs = 1500L, gridOpenDelayMs = 2500L)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛡️ Плавный (1.5с/2.5с)", fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }

        // Quick System Setup & Permissions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚙️ Системные разрешения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Спец. возможности")
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = if (isServiceActive) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (isServiceActive) "✓ Включено" else "Открыть настройки")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. Захват экрана (OCR)")
                    Button(
                        onClick = onRequestMediaProjection,
                        colors = if (MediaProjectionHelper.hasProjection()) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (MediaProjectionHelper.hasProjection()) "✓ Авторизован" else "Разрешить OCR")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("3. Плавающий кружок-кнопка", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Кружок поверх всех окон (тап — старт/стоп, зажатие — меню)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onToggleOverlay) {
                        Text("Запустить кружок")
                    }
                }
            }
        }

        // Target Settings
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Настройка целей для покупки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Super Priority Banner (Gnome, White Shroud, License)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⭐", fontSize = 18.sp)
                        Text(
                            text = "Супер-приоритет: «Гном», «Белое покрывало», «Лицензия» выкупаются ВСЕГДА при появлении!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Helper to parse selected targets set
                val selectedTargets = remember(config.targetItemName) {
                    config.targetItemName
                        .split(",", ";", "/")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }

                fun toggleTarget(item: String) {
                    val currentList = config.targetItemName
                        .split(",", ";", "/")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toMutableList()

                    val existingIndex = currentList.indexOfFirst { it.equals(item, ignoreCase = true) }
                    if (existingIndex >= 0) {
                        currentList.removeAt(existingIndex)
                    } else {
                        currentList.add(item)
                    }
                    onUpdateConfig(config.copy(targetItemName = currentList.joinToString(", ")))
                }

                // Quick Action Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onUpdateConfig(config.copy(targetItemName = "")) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("⭐ Только раритеты", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { 
                            val currentList = config.targetItemName.split(",", ";", "/").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                            if (!currentList.any { it.equals("ММТ", ignoreCase = true) }) {
                                currentList.add("ММТ")
                            }
                            onUpdateConfig(config.copy(targetItemName = currentList.joinToString(", "), minMmtQuantity = 100.0))
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("🟡 + ММТ (от 100 шт)", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onUpdateConfig(config.copy(targetItemName = "")) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Очистить", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = config.targetItemName,
                    onValueChange = { onUpdateConfig(config.copy(targetItemName = it)) },
                    label = { Text("Выбранные дополнительные ресурсы (через запятую)") },
                    placeholder = { Text("Оставьте пустым, чтобы покупать ТОЛЬКО Гнома/Покрывало/Лицензию") },
                    modifier = Modifier.fillMaxWidth().testTag("input_target_name"),
                    singleLine = false,
                    maxLines = 2,
                    supportingText = {
                        Text(
                            text = if (selectedTargets.isEmpty()) 
                                "⚡ Сейчас покупаются ТОЛЬКО: Гном, Белое покрывало, Лицензия (все обычные ресурсы пропускаются)"
                                else "⚡ Выбрано для покупки: ${selectedTargets.joinToString(", ")} + (Гном, Покрывало, Лицензия)",
                            color = if (selectedTargets.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // MMT Minimum Quantity Condition
                if (selectedTargets.any { it.contains("ММТ", ignoreCase = true) || it.contains("MMT", ignoreCase = true) } || true) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🟡 Правило для монеты ММТ (жёлтый MM Token):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = config.minMmtQuantity.toInt().toString(),
                                onValueChange = { onUpdateConfig(config.copy(minMmtQuantity = it.toDoubleOrNull() ?: 100.0)) },
                                label = { Text("Минимальный размер ММТ для покупки (шт)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                supportingText = {
                                    Text("Покупать ММТ только если его размер >= ${config.minMmtQuantity.toInt()} шт. (Если 1.0 или 10.0 — покупка НЕ совершается)")
                                }
                            )
                        }
                    }
                }

                // Quick item presets with MULTI-SELECT support
                val primaryResources = listOf("Медь", "Серебро", "Золото", "Руда")
                val gemResources = listOf("ММТ (≥100)", "СМТ", "Изумруд", "Рубин", "Сапфир", "Свиток", "Эль")

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Нажмите на чип для включения/выключения:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                    // Row 1: MMT & SMT & Gems
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val mmtSelected = selectedTargets.any { it.equals("ММТ", ignoreCase = true) || it.equals("ММТ (≥100)", ignoreCase = true) }
                        FilterChip(
                            selected = mmtSelected,
                            onClick = { toggleTarget("ММТ") },
                            leadingIcon = if (mmtSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else {
                                { Text("🟡", fontSize = 11.sp) }
                            },
                            label = { Text("ММТ (≥${config.minMmtQuantity.toInt()})", fontSize = 11.sp, fontWeight = if (mmtSelected) FontWeight.Bold else FontWeight.Normal) }
                        )

                        val smtSelected = selectedTargets.any { it.equals("СМТ", ignoreCase = true) }
                        FilterChip(
                            selected = smtSelected,
                            onClick = { toggleTarget("СМТ") },
                            leadingIcon = if (smtSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            label = { Text("СМТ", fontSize = 11.sp, fontWeight = if (smtSelected) FontWeight.Bold else FontWeight.Normal) }
                        )

                        val goldSelected = selectedTargets.any { it.equals("Золото", ignoreCase = true) }
                        FilterChip(
                            selected = goldSelected,
                            onClick = { toggleTarget("Золото") },
                            leadingIcon = if (goldSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            label = { Text("Золото", fontSize = 11.sp, fontWeight = if (goldSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    // Row 2: Secondary items
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Медь", "Серебро", "Руда", "Свиток").forEach { item ->
                            val isSelected = selectedTargets.any { it.equals(item, ignoreCase = true) }
                            FilterChip(
                                selected = isSelected,
                                onClick = { toggleTarget(item) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                label = { Text(item, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = if (config.priceThreshold == 0.0) "" else config.priceThreshold.toString(),
                    onValueChange = {
                        val num = it.toDoubleOrNull() ?: 0.0
                        onUpdateConfig(config.copy(priceThreshold = num))
                    },
                    label = { Text("Порог цены (макс. цена покупки)") },
                    placeholder = { Text("50.0") },
                    modifier = Modifier.fillMaxWidth().testTag("input_price_threshold"),
                    singleLine = true
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (config.enableActualBuying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (config.enableActualBuying) "⚡ Реальная покупка (ВЫКУП ВКЛЮЧЕН)" else "👁 Только мониторинг (Покупка ВЫКЛ)",
                                fontWeight = FontWeight.Bold,
                                color = if (config.enableActualBuying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (config.enableActualBuying) 
                                    "При нахождении цели бот моментально кликает и выкупает лот" 
                                    else "Бот только распознает и пишет в лог (клики покупки не производятся)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (config.enableActualBuying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = config.enableActualBuying,
                            onCheckedChange = { onUpdateConfig(config.copy(enableActualBuying = it)) },
                            modifier = Modifier.testTag("switch_enable_buying")
                        )
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = onTestOpenCvScan,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().testTag("btn_quick_scan_16")
                ) {
                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ Сканировать 16 элементов сейчас (OpenCV)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GridRecognitionTab(
    config: AppConfiguration,
    onUpdateConfig: (AppConfiguration) -> Unit,
    onGenerateTemplate: (String) -> Unit,
    onTestOpenCvScan: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // OpenCV Scanner Test Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔬 OpenCV Сканер доски (16 слотов)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Сканирует всю сетку Resource Hunt, определяет все 10 типов ресурсов, выявляет выкупленные слоты (🚫), парсит количество и логирует сводную таблицу в консоль и приложение.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onTestOpenCvScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Просканировать текущий экран (OpenCV)")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🧩 4x4 Grid Распознавание (16 слотов)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Ивент Unity использует окно с сеткой 4x4 (16 элементов). Детектор сканирует все 16 ячеек, находит целевой ресурс и автоматически кликает по центру ячейки, либо закрывает окно по крестику сверху слева.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 4x4 Grid Visualizer
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val index = row * 4 + col
                                    val isSampleTarget = index == 5
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSampleTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = if (isSampleTarget) "🎯 #${index + 1}" else "#${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSampleTarget) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎨 Генератор эталонных шаблонов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Создайте базовый графический шаблон для сопоставления пикселей и цветовой гаммы:",
                    style = MaterialTheme.typography.bodySmall
                )

                val allResources = listOf("Медь", "Серебро", "Золото", "ММТ", "СМТ", "Руда", "Эль", "Рубин", "Сапфир", "Свиток")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allResources.take(5).forEach { item ->
                            Button(
                                onClick = { onGenerateTemplate(item) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item, maxLines = 1, fontSize = 10.sp)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allResources.drop(5).forEach { item ->
                            Button(
                                onClick = { onGenerateTemplate(item) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item, maxLines = 1, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchCyclesTab(
    config: AppConfiguration,
    onUpdateConfig: (AppConfiguration) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("3-Фазовые циклы поиска", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Имитация реального поведения с перерывами и автоперезапуском игры при отсутствии лотов",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = config.useSearchCycles,
                        onCheckedChange = { onUpdateConfig(config.copy(useSearchCycles = it)) }
                    )
                }

                Divider()

                Text("⏱️ Цикл 1 (Быстрый скан)", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = config.cycle1DurationMin.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle1DurationMin = it.toIntOrNull() ?: 5)) },
                        label = { Text("Длительность (мин)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.cycle1RandomRangeSec.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle1RandomRangeSec = it.toIntOrNull() ?: 30)) },
                        label = { Text("Разброс ± (сек)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Text("⏱️ Цикл 2 (Умеренный скан)", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = config.cycle2DurationMin.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle2DurationMin = it.toIntOrNull() ?: 7)) },
                        label = { Text("Длительность (мин)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.cycle2RandomRangeSec.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle2RandomRangeSec = it.toIntOrNull() ?: 45)) },
                        label = { Text("Разброс ± (сек)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Text("⏱️ Цикл 3 (Глубокий скан)", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = config.cycle3DurationMin.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle3DurationMin = it.toIntOrNull() ?: 10)) },
                        label = { Text("Длительность (мин)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.cycle3RandomRangeSec.toString(),
                        onValueChange = { onUpdateConfig(config.copy(cycle3RandomRangeSec = it.toIntOrNull() ?: 60)) },
                        label = { Text("Разброс ± (сек)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun LogsAndHistoryTab(
    logs: List<String>,
    purchases: List<com.example.database.PurchaseRecord>,
    onClearLogs: () -> Unit,
    onClearPurchases: () -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("📜 Логи (${logs.size})") }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("🛒 Покупки (${purchases.size})") }
            )
        }

        if (subTab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Журнал действий", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("AutoBuyer Logs", logs.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Все логи скопированы в буфер!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Логи пусты", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Скопировать всё", fontSize = 12.sp)
                    }
                    TextButton(onClick = onClearLogs) {
                        Text("Очистить", fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Логи пока пусты. Запустите бота для начала работы.")
                        }
                    }
                } else {
                    items(logs) { log ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Log Entry", log)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Строка скопирована", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSpent = purchases.sumOf { it.price * it.quantity }
                Text("Всего потрачено: ${"%.2f".format(totalSpent)}", fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearPurchases) {
                    Text("Очистить")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (purchases.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("История покупок пуста.")
                        }
                    }
                } else {
                    items(purchases) { item ->
                        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.itemName, fontWeight = FontWeight.Bold)
                                    Text("${item.quantity} шт. × ${item.price} = ${"%.2f".format(item.price * item.quantity)}", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.details.isNotEmpty()) {
                                    Text(item.details, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrationTab(
    config: AppConfiguration,
    onUpdateConfig: (AppConfiguration) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Калибровка вкладок (X, Y)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Если в игре нестандартное разрешение или смещены вкладки, укажите точные экранные координаты кликов (в пикселях):",
                    style = MaterialTheme.typography.bodySmall
                )

                CoordinateRow("Руда", config.calibratedOreX, config.calibratedOreY) { x, y ->
                    onUpdateConfig(config.copy(calibratedOreX = x, calibratedOreY = y))
                }
                CoordinateRow("Медь", config.calibratedCopperX, config.calibratedCopperY) { x, y ->
                    onUpdateConfig(config.copy(calibratedCopperX = x, calibratedCopperY = y))
                }
                CoordinateRow("Серебро", config.calibratedSilverX, config.calibratedSilverY) { x, y ->
                    onUpdateConfig(config.copy(calibratedSilverX = x, calibratedSilverY = y))
                }
                CoordinateRow("Золото", config.calibratedGoldX, config.calibratedGoldY) { x, y ->
                    onUpdateConfig(config.copy(calibratedGoldX = x, calibratedGoldY = y))
                }
                CoordinateRow("Сок", config.calibratedSapX, config.calibratedSapY) { x, y ->
                    onUpdateConfig(config.copy(calibratedSapX = x, calibratedSapY = y))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⏱️ Скорость сетки 4x4 (Кнопка '2' ↔ Крестик 'X')", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Задержки между нажатиями при поиске и обновлении 16 ячеек Resource Hunt:",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = config.gridCloseDelayMs.toString(),
                        onValueChange = { onUpdateConfig(config.copy(gridCloseDelayMs = it.toLongOrNull() ?: 1000L)) },
                        label = { Text("Пауза после 'X' (мс)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.gridOpenDelayMs.toString(),
                        onValueChange = { onUpdateConfig(config.copy(gridOpenDelayMs = it.toLongOrNull() ?: 2000L)) },
                        label = { Text("Пауза после '2' (мс)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚡ Скорость и анти-бан рандомизация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = config.tabSwitchIntervalMs.toString(),
                        onValueChange = { onUpdateConfig(config.copy(tabSwitchIntervalMs = it.toLongOrNull() ?: 15L)) },
                        label = { Text("Интервал (мс)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config.clickRandomizationRadiusPx.toString(),
                        onValueChange = { onUpdateConfig(config.copy(clickRandomizationRadiusPx = it.toFloatOrNull() ?: 6f)) },
                        label = { Text("Радиус клика (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun CoordinateRow(
    label: String,
    x: Float?,
    y: Float?,
    onCoordChanged: (Float?, Float?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(70.dp), fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = x?.takeIf { it != -1f }?.toString() ?: "",
            onValueChange = { onCoordChanged(it.toFloatOrNull(), y) },
            label = { Text("X") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = y?.takeIf { it != -1f }?.toString() ?: "",
            onValueChange = { onCoordChanged(x, it.toFloatOrNull()) },
            label = { Text("Y") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
}

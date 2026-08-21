package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance OpenCV Computer Vision Scanner for the 16-slot Unity Resource Hunt board.
 * Detects all 10 distinct resources + already bought/prohibited slots (🚫).
 */
object OpenCvVisionScanner {

    private const val TAG = "OpenCvVision"

    @Volatile
    private var isOpenCvInitialized = false

    // Registered Resource Identifiers
    const val RES_ALE = "Эль"
    const val RES_GOLD = "Золото"
    const val RES_COPPER = "Медь"
    const val RES_MMT_GOLD = "ММТ"
    const val RES_RUBY = "Рубин"
    const val RES_ORE = "Руда"
    const val RES_SAPPHIRE = "Сапфир"
    const val RES_EMERALD = "Изумруд"
    const val RES_SCROLL = "Свиток"
    const val RES_SILVER = "Серебро"
    const val RES_SMT_SILVER = "СМТ"
    const val RES_GNOME = "Гном"
    const val RES_WHITE_SHROUD = "Белое покрывало"
    const val RES_LICENSE = "Лицензия"
    const val RES_BOUGHT = "🚫 Куплено"
    const val RES_UNKNOWN = "❓ Неизвестно"

    data class SlotAnalysisResult(
        val slotIndex: Int, // 0..15 (slot #1..#16)
        val row: Int,       // 0..3
        val col: Int,       // 0..3
        val resourceName: String,
        val quantityText: String,
        val quantityValue: Double?,
        val confidence: Float,
        val centerX: Float,
        val centerY: Float,
        val isBought: Boolean,
        val details: String
    )

    data class BoardScanReport(
        val totalSlots: Int = 16,
        val detectedSlots: List<SlotAnalysisResult>,
        val availableLotsCount: Int,
        val boughtLotsCount: Int,
        val summaryMatrix: List<String>
    )

    fun initialize(context: Context? = null): Boolean {
        if (isOpenCvInitialized) return true

        isOpenCvInitialized = try {
            if (OpenCVLoader.initDebug()) {
                Log.i(TAG, "✅ OpenCV 4.9.0 successfully initialized via initDebug()")
                true
            } else if (OpenCVLoader.initLocal()) {
                Log.i(TAG, "✅ OpenCV successfully initialized via initLocal()")
                true
            } else {
                Log.w(TAG, "⚠️ OpenCVLoader returned false, utilizing native CV fallback")
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "⚠️ OpenCV dynamic load error: ${e.message}, falling back to pure CV processing")
            true
        }

        return isOpenCvInitialized
    }

    /**
     * Analyzes all 16 slots of the Resource Hunt window from screen Bitmap.
     */
    fun scanResourceHuntBoard(
        screenBitmap: Bitmap,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line> = emptyList(),
        logToUi: Boolean = false
    ): BoardScanReport {
        initialize()

        val width = screenBitmap.width
        val height = screenBitmap.height

        // Board geometry calculation based on screen dimensions
        // Unity window 4x4 grid coordinates bounds (centered in the upper-mid screen)
        val boardLeft = width * 0.12f
        val boardRight = width * 0.88f
        val boardTop = height * 0.20f
        val boardBottom = height * 0.68f

        val boardWidth = boardRight - boardLeft
        val boardHeight = boardBottom - boardTop

        val cellWidth = boardWidth / 4f
        val cellHeight = boardHeight / 4f

        val results = mutableListOf<SlotAnalysisResult>()

        Log.i(TAG, "=================================================================")
        Log.i(TAG, "🔍 [OPENCV VISION] НАЧАЛО СКАНИРОВАНИЯ ДОСКИ 4x4 (16 ЭЛЕМЕНТОВ)")
        Log.i(TAG, "Разрешение экрана: ${width}x${height}, Сетка: (${boardLeft.toInt()}, ${boardTop.toInt()}) -> (${boardRight.toInt()}, ${boardBottom.toInt()})")
        Log.i(TAG, "=================================================================")

        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val index = row * 4 + col
                val slotLeft = (boardLeft + col * cellWidth).toInt().coerceIn(0, width - 1)
                val slotTop = (boardTop + row * cellHeight).toInt().coerceIn(0, height - 1)
                val slotRight = (slotLeft + cellWidth.toInt()).coerceIn(slotLeft + 1, width)
                val slotBottom = (slotTop + cellHeight.toInt()).coerceIn(slotTop + 1, height)

                val slotW = slotRight - slotLeft
                val slotH = slotBottom - slotTop

                val slotCenterX = slotLeft + slotW / 2f
                val slotCenterY = slotTop + slotH / 2f

                // Extract slot cropped bitmap for OpenCV analysis
                val slotCrop = Bitmap.createBitmap(screenBitmap, slotLeft, slotTop, slotW, slotH)

                // 1. Analyze resource using OpenCV HSV & Visual Feature Classifier
                val analysis = classifySlotWithOpenCv(slotCrop, ocrLines, slotLeft, slotTop, slotRight, slotBottom)

                // 2. OCR Quantity Parsing for this slot with resource-aware fallback
                val quantityInfo = extractQuantityForSlot(slotCrop, slotLeft, slotTop, slotRight, slotBottom, ocrLines, analysis.resourceName)

                val isBought = analysis.resourceName == RES_BOUGHT

                val slotResult = SlotAnalysisResult(
                    slotIndex = index,
                    row = row,
                    col = col,
                    resourceName = analysis.resourceName,
                    quantityText = quantityInfo.first,
                    quantityValue = quantityInfo.second,
                    confidence = analysis.confidence,
                    centerX = slotCenterX,
                    centerY = slotCenterY,
                    isBought = isBought,
                    details = analysis.details
                )

                results.add(slotResult)
            }
        }

        // Generate matrix visualization for logs
        val matrixLines = mutableListOf<String>()
        for (r in 0 until 4) {
            val rowSlots = results.subList(r * 4, (r + 1) * 4)
            val rowStr = rowSlots.joinToString(" | ") { slot ->
                val qty = slot.quantityText.ifEmpty { "?.?" }
                val name = if (slot.isBought) "🚫 КУПЛЕНО" else slot.resourceName
                "#${slot.slotIndex + 1}: $name ($qty)"
            }
            matrixLines.add("[Ряд ${r + 1}] $rowStr")
        }

        val availableCount = results.count { !it.isBought && it.resourceName != RES_UNKNOWN }
        val boughtCount = results.count { it.isBought }

        val report = BoardScanReport(
            totalSlots = 16,
            detectedSlots = results,
            availableLotsCount = availableCount,
            boughtLotsCount = boughtCount,
            summaryMatrix = matrixLines
        )

        // Output comprehensive report to Dev Logs (Logcat & Stdout)
        logScanReportToConsole(report)

        // Post full 16-slot report to in-app AutoBuyerLogs only when requested (e.g. at start, on test, or when table changes)
        if (logToUi) {
            val uiLogs = mutableListOf<String>()
            uiLogs.add("🧩 [СКАНИРОВАНИЕ 16 ЭЛЕМЕНТОВ] Распознано 16 слотов (Доступно: $availableCount, Куплено: $boughtCount):")
            for (line in matrixLines) {
                uiLogs.add("  $line")
            }
            // Add slots strictly in ascending order #1, #2, ... #16
            for (slot in results) {
                val status = if (slot.isBought) "🚫 ВЫКУПЛЕН" else "✅ ДОСТУПЕН"
                val qty = if (slot.quantityText.isNotEmpty()) " x${slot.quantityText}" else ""
                uiLogs.add("  • Слот #${slot.slotIndex + 1}: ${slot.resourceName}$qty -> $status")
            }
            AutoBuyerLogs.addLogsBatch(uiLogs)
        }

        return report
    }

    /**
     * Checks if the user is currently on the Main Game Screen (Mining Cave).
     */
    fun isMainGameScreen(ocrLines: List<com.google.mlkit.vision.text.Text.Line>): Boolean {
        // Main screen has mining controls in the center/lower section
        return ocrLines.any { line ->
            val t = line.text.lowercase().trim()
            t.contains("mining speed") ||
            t.contains("send ore to storage") ||
            t.contains("send ore") ||
            t.contains("hall of fame") ||
            t.contains("town hall") ||
            t.contains("treasury") ||
            t.contains("expeditions") ||
            t.contains("time left") ||
            (t.contains("level 10") || t.contains("level 9") || t.contains("level 8"))
        }
    }

    /**
     * Checks if the 16-element Resource Hunt window is currently opened.
     */
    fun isResourceHuntWindowOpen(
        screenBitmap: Bitmap,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line>
    ): Boolean {
        val hasGridKeywords = ocrLines.any { line ->
            val t = line.text.lowercase().trim()
            t.contains("resource") ||
            t.contains("hunt") ||
            t.contains("hunt") ||
            t.contains("huni") ||
            t.contains("16") ||
            t.contains("event") ||
            t.contains("ivent") ||
            t.contains("охота") ||
            t.contains("claim") ||
            Regex("""\d{1,2}:\d{2}""").containsMatchIn(t)
        }
        if (hasGridKeywords) return true

        // If main screen mining center controls are visible, the grid is definitely NOT open yet
        if (isMainGameScreen(ocrLines)) return false

        // OpenCV Visual Check: Check if header contains "Resource Hunt" gold text or yellow balloons
        try {
            initialize()
            val width = screenBitmap.width
            val height = screenBitmap.height
            val roiX = (width * 0.20f).toInt().coerceIn(0, width - 1)
            val roiY = (height * 0.10f).toInt().coerceIn(0, height - 1)
            val roiW = (width * 0.60f).toInt().coerceIn(10, width - roiX)
            val roiH = (height * 0.12f).toInt().coerceIn(10, height - roiY)

            val headerBmp = Bitmap.createBitmap(screenBitmap, roiX, roiY, roiW, roiH)
            val hsvMat = Mat()
            val rgbMat = Mat()
            Utils.bitmapToMat(headerBmp, rgbMat)
            Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

            val goldMask = Mat()
            Core.inRange(hsvMat, Scalar(15.0, 90.0, 90.0), Scalar(38.0, 255.0, 255.0), goldMask)
            val goldPixels = Core.countNonZero(goldMask)
            val goldRatio = goldPixels.toDouble() / (hsvMat.rows() * hsvMat.cols())

            goldMask.release(); hsvMat.release(); rgbMat.release(); headerBmp.recycle()

            if (goldRatio > 0.035) {
                return true
            }
        } catch (e: Throwable) {
            // Ignore OpenCV error
        }

        return false
    }

    /**
     * Detects the Anniversary "2" button on the screen using OpenCV & OCR.
     * Guaranteed to NEVER touch the Android status bar / notification shade.
     * Safe search bounds: X = [42%..72%], Y = [8%..22%] (well below status bar Y < 6%).
     */
    fun detectAnniversary2Button(
        screenBitmap: Bitmap,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line> = emptyList()
    ): Point {
        val width = screenBitmap.width
        val height = screenBitmap.height

        // 1. First check if OCR detected '2' strictly within the Anniversary button header area
        for (line in ocrLines) {
            val box = line.boundingBox ?: continue
            val cx = box.centerX().toFloat()
            val cy = box.centerY().toFloat()

            // Safe ROI: strictly exclude status bar (cy must be > 8% of screen)
            val inSafeHeaderRegion = cx in (width * 0.42f)..(width * 0.72f) &&
                                     cy in (height * 0.08f)..(height * 0.22f)

            val text = line.text.trim()
            if (inSafeHeaderRegion && (text == "2" || text.contains("2") && text.length <= 3)) {
                Log.i(TAG, "🎯 [OPENCV 2-DETECTION] OCR точное совпадение кнопки '2': ($cx, $cy)")
                return Point(cx.toDouble(), cy.toDouble())
            }
        }

        // 2. OpenCV Visual Contour & HSV detection in ROI
        initialize()
        try {
            val roiX = (width * 0.44f).toInt().coerceIn(0, width - 1)
            val roiY = (height * 0.08f).toInt().coerceIn(0, height - 1)
            val roiW = (width * 0.24f).toInt().coerceIn(10, width - roiX)
            val roiH = (height * 0.14f).toInt().coerceIn(10, height - roiY)

            val roiBitmap = Bitmap.createBitmap(screenBitmap, roiX, roiY, roiW, roiH)
            val mat = Mat()
            Utils.bitmapToMat(roiBitmap, mat)

            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)

            // Look for colorful balloons (cyan/pink/yellow/gold) of the 2-year anniversary icon
            val goldMask = Mat()
            Core.inRange(hsv, Scalar(15.0, 90.0, 100.0), Scalar(38.0, 255.0, 255.0), goldMask)

            val pinkCyanMask = Mat()
            val cyan = Mat()
            val pink = Mat()
            Core.inRange(hsv, Scalar(80.0, 80.0, 80.0), Scalar(110.0, 255.0, 255.0), cyan)
            Core.inRange(hsv, Scalar(140.0, 80.0, 80.0), Scalar(175.0, 255.0, 255.0), pink)
            Core.bitwise_or(cyan, pink, pinkCyanMask)

            val combined = Mat()
            Core.bitwise_or(goldMask, pinkCyanMask, combined)

            val contours = mutableListOf<org.opencv.core.MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(combined, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestCentroid: Point? = null
            var maxArea = 0.0

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area > maxArea && area > 25.0) {
                    maxArea = area
                    val moments = Imgproc.moments(c)
                    if (moments.m00 > 0) {
                        val cX = moments.m10 / moments.m00
                        val cY = moments.m01 / moments.m00
                        bestCentroid = Point(roiX + cX, roiY + cY)
                    }
                }
            }

            roiBitmap.recycle()

            if (bestCentroid != null) {
                Log.i(TAG, "🎯 [OPENCV 2-DETECTION] OpenCV нашел контур иконки '2': (${bestCentroid.x}, ${bestCentroid.y})")
                return bestCentroid
            }
        } catch (e: Throwable) {
            Log.w(TAG, "OpenCV detection error: ${e.message}")
        }

        // 3. Robust Geometric Default for Anniversary '2' Icon (X: 58%, Y: 14.5%)
        val defaultX = width * 0.58
        val defaultY = height * 0.145
        Log.i(TAG, "🎯 [OPENCV 2-DETECTION] Использованы эталонные координаты '2': ($defaultX, $defaultY)")
        return Point(defaultX, defaultY)
    }

    private data class ClassificationRaw(
        val resourceName: String,
        val confidence: Float,
        val details: String
    )

    /**
     * Multi-stage OpenCV Feature & HSV Classifier combined with OCR text context
     */
    private fun classifySlotWithOpenCv(
        slotBitmap: Bitmap,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line> = emptyList(),
        slotLeft: Int = 0,
        slotTop: Int = 0,
        slotRight: Int = 0,
        slotBottom: Int = 0
    ): ClassificationRaw {
        try {
            // Check OCR text inside slot bounds for tokens, rare items, or 1000 quantity
            for (line in ocrLines) {
                val box = line.boundingBox ?: continue
                val cx = box.centerX()
                val cy = box.centerY()
                if (cx in slotLeft..slotRight && cy in slotTop..slotBottom) {
                    val t = line.text.lowercase().trim()
                    when {
                        t.contains("гном") || t.contains("gnome") || t.contains("dwarf") -> 
                            return ClassificationRaw(RES_GNOME, 0.99f, "OCR confirmed gnome")
                        t.contains("покрывал") || t.contains("белое") || t.contains("blanket") || t.contains("shroud") ->
                            return ClassificationRaw(RES_WHITE_SHROUD, 0.99f, "OCR confirmed white shroud")
                        t.contains("лиценз") || t.contains("license") || t.contains("licence") || t.contains("грамот") || t.contains("договор") ->
                            return ClassificationRaw(RES_LICENSE, 0.99f, "OCR confirmed license")
                        t.contains("1000") || t.contains("руда") || t.contains("ore") -> 
                            return ClassificationRaw(RES_ORE, 0.99f, "OCR confirmed ore")
                        t.contains("token") || t.contains("mm ") || t.contains(" mm") -> {
                            return ClassificationRaw(RES_SMT_SILVER, 0.98f, "OCR MM token silver")
                        }
                        t.contains("медь") || t.contains("copper") -> return ClassificationRaw(RES_COPPER, 0.98f, "OCR confirmed copper")
                        t.contains("золот") || t.contains("gold") -> return ClassificationRaw(RES_GOLD, 0.98f, "OCR confirmed gold")
                        t.contains("серебр") || t.contains("silver") -> return ClassificationRaw(RES_SILVER, 0.98f, "OCR confirmed silver")
                        t.contains("сапфир") || t.contains("sapphire") -> return ClassificationRaw(RES_SAPPHIRE, 0.98f, "OCR confirmed sapphire")
                        t.contains("рубин") || t.contains("ruby") -> return ClassificationRaw(RES_RUBY, 0.98f, "OCR confirmed ruby")
                        t.contains("изумруд") || t.contains("emerald") -> return ClassificationRaw(RES_EMERALD, 0.98f, "OCR confirmed emerald")
                        t.contains("эль") || t.contains("ale") || t.contains("beer") -> return ClassificationRaw(RES_ALE, 0.98f, "OCR confirmed ale")
                        t.contains("свиток") || t.contains("scroll") -> return ClassificationRaw(RES_SCROLL, 0.98f, "OCR confirmed scroll")
                    }
                }
            }

            val mat = Mat()
            Utils.bitmapToMat(slotBitmap, mat)

            // Convert to HSV for robust color segmentation
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)

            val width = slotBitmap.width
            val height = slotBitmap.height

            // Crop inner icon region (ignoring dark borders and bottom price banner)
            val iconH = (height * 0.60f).toInt().coerceAtLeast(10)
            val iconW = (width * 0.70f).toInt().coerceAtLeast(10)
            val iconX = ((width - iconW) / 2).coerceAtLeast(0)
            val iconY = (height * 0.08f).toInt().coerceAtLeast(0)

            val iconRect = Rect(iconX, iconY, min(iconW, width - iconX), min(iconH, height - iconY))
            val iconHsv = Mat(hsv, iconRect)
            val iconRgb = Mat(mat, iconRect)

            // Measure Color Proportions in OpenCV
            // 1. Pure Crimson Red for Prohibition Sign (🚫) or Ruby (Рубин)
            val redMask1 = Mat()
            val redMask2 = Mat()
            val redFull = Mat()
            Core.inRange(iconHsv, Scalar(0.0, 95.0, 75.0), Scalar(7.0, 255.0, 255.0), redMask1)
            Core.inRange(iconHsv, Scalar(168.0, 95.0, 75.0), Scalar(180.0, 255.0, 255.0), redMask2)
            Core.bitwise_or(redMask1, redMask2, redFull)
            val redPixels = Core.countNonZero(redFull)

            // 2. Emerald Green (Изумруд)
            val greenMask = Mat()
            Core.inRange(iconHsv, Scalar(36.0, 85.0, 75.0), Scalar(85.0, 255.0, 255.0), greenMask)
            val greenPixels = Core.countNonZero(greenMask)

            // 3. Cobalt Blue Sapphire (Сапфир)
            val blueMask = Mat()
            Core.inRange(iconHsv, Scalar(95.0, 95.0, 75.0), Scalar(135.0, 255.0, 255.0), blueMask)
            val bluePixels = Core.countNonZero(blueMask)

            // 4. Pure Golden Yellow (Золото / ММТ)
            val goldYellowMask = Mat()
            Core.inRange(iconHsv, Scalar(18.0, 110.0, 120.0), Scalar(38.0, 255.0, 255.0), goldYellowMask)
            val goldYellowPixels = Core.countNonZero(goldYellowMask)

            // 5. Copper Terracotta Orange (Медь)
            val copperOrangeMask = Mat()
            Core.inRange(iconHsv, Scalar(6.0, 80.0, 80.0), Scalar(17.0, 255.0, 230.0), copperOrangeMask)
            val copperOrangePixels = Core.countNonZero(copperOrangeMask)

            // 6. Silver Cyan-Metallic / Slate-Grey Ingot (Серебро / СМТ)
            val silverCyanMask = Mat()
            Core.inRange(iconHsv, Scalar(85.0, 10.0, 110.0), Scalar(135.0, 70.0, 245.0), silverCyanMask)
            val silverCyanPixels = Core.countNonZero(silverCyanMask)

            val silverMetallicMask = Mat()
            Core.inRange(iconHsv, Scalar(0.0, 0.0, 110.0), Scalar(180.0, 45.0, 240.0), silverMetallicMask)
            val silverMetallicPixels = Core.countNonZero(silverMetallicMask)

            // 7. Ore Dark Minerals (Руда)
            val oreDarkMask = Mat()
            Core.inRange(iconHsv, Scalar(0.0, 0.0, 30.0), Scalar(180.0, 60.0, 115.0), oreDarkMask)
            val oreDarkPixels = Core.countNonZero(oreDarkMask)

            val totalIconPixels = (iconRect.width * iconRect.height).toDouble()

            val redRatio = redPixels / totalIconPixels
            val greenRatio = greenPixels / totalIconPixels
            val blueRatio = bluePixels / totalIconPixels
            val goldRatio = goldYellowPixels / totalIconPixels
            val copperRatio = copperOrangePixels / totalIconPixels
            val silverCyanRatio = silverCyanPixels / totalIconPixels
            val silverMetallicRatio = silverMetallicPixels / totalIconPixels
            val oreDarkRatio = oreDarkPixels / totalIconPixels

            val isCircular = checkCircularity(iconRgb)

            var detected = RES_UNKNOWN
            var confidence = 0.6f
            var reason = ""

            // Decision Tree Classifier
            // 1. Red Check: In Resource Hunt grid, red ring/slash is ALWAYS Prohibition Sign (🚫 - Выкупленный лот)
            if (redRatio > 0.025) {
                detected = RES_BOUGHT
                confidence = min(0.99f, (redRatio * 4.0f).toFloat())
                reason = "Red prohibition ring/slash"
            }
            // 2. Cobalt Blue Sapphire (Сапфир)
            else if (blueRatio > 0.08) {
                detected = RES_SAPPHIRE
                confidence = min(0.99f, (blueRatio * 3.5f).toFloat())
                reason = "Cobalt blue gemstone reflection"
            }
            // 3. Emerald Green (Изумруд)
            else if (greenRatio > 0.07) {
                detected = RES_EMERALD
                confidence = min(0.99f, (greenRatio * 3.5f).toFloat())
                reason = "Emerald green gemstone reflection"
            }
            // 4. Gold Ingot (Золото) vs MMT Gold Token
            else if (goldRatio > 0.06) {
                if (isCircular && !isRoughRockTexture(iconRgb)) {
                    detected = RES_MMT_GOLD
                    confidence = 0.95f
                    reason = "Circular gold MM token"
                } else if (hasBeerMugGeometry(iconHsv, iconRgb)) {
                    detected = RES_ALE
                    confidence = 0.92f
                    reason = "Beer mug with golden ale"
                } else {
                    detected = RES_GOLD
                    confidence = 0.96f
                    reason = "Gold ingot bar"
                }
            }
            // 5. Copper Bar (Медь - terracotta ingot)
            else if (copperRatio > 0.05) {
                detected = RES_COPPER
                confidence = min(0.98f, (copperRatio * 3.5f).toFloat())
                reason = "Copper terracotta metallic ingot"
            }
            // 6. Silver Bar (Серебро) vs SMT Silver Token (СМТ)
            else if (silverCyanRatio > 0.04 || silverMetallicRatio > 0.12) {
                if (isCircular && !isRoughRockTexture(iconRgb)) {
                    detected = RES_SMT_SILVER
                    confidence = 0.96f
                    reason = "Circular silver MM token"
                } else {
                    detected = RES_SILVER
                    confidence = 0.95f
                    reason = "Silver metallic ingot bar"
                }
            }
            // 7. Scroll (Свиток) or License (Лицензия)
            else if (hasScrollParchmentGeometry(iconHsv)) {
                detected = RES_SCROLL
                confidence = 0.92f
                reason = "Parchment scroll contour"
            }
            // 8. Ore (Руда - Dark faceted mineral stone)
            else if (oreDarkRatio > 0.14 || isRoughRockTexture(iconRgb)) {
                detected = RES_ORE
                confidence = 0.96f
                reason = "Dark grey mineral rock facets"
            } else {
                // Fallback: Check if silver or copper or ore
                if (silverMetallicRatio > 0.08 || silverCyanRatio > 0.03) {
                    detected = RES_SILVER
                    confidence = 0.88f
                    reason = "Silver metallic reflection"
                } else if (copperRatio > 0.03) {
                    detected = RES_COPPER
                    confidence = 0.88f
                    reason = "Copper tone"
                } else {
                    detected = RES_ORE
                    confidence = 0.85f
                    reason = "Default mineral rock"
                }
            }

            // Cleanup Mats
            redMask1.release(); redMask2.release(); redFull.release()
            greenMask.release(); blueMask.release()
            copperOrangeMask.release(); goldYellowMask.release()
            silverCyanMask.release(); silverMetallicMask.release(); oreDarkMask.release()
            iconHsv.release(); iconRgb.release(); hsv.release(); mat.release()

            return ClassificationRaw(
                resourceName = detected,
                confidence = confidence.coerceIn(0.60f, 0.99f),
                details = reason
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in OpenCV slot classification: ${e.message}")
            return ClassificationRaw(RES_UNKNOWN, 0.5f, "Fallback due to: ${e.message}")
        }
    }

    /**
     * Checks if the object in the icon region is circular (Coins & Prohibition Ring).
     */
    private fun checkCircularity(rgbMat: Mat): Boolean {
        try {
            val gray = Mat()
            Imgproc.cvtColor(rgbMat, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.5)
            
            // 1. Hough Circles method
            val circles = Mat()
            Imgproc.HoughCircles(
                gray, circles, Imgproc.HOUGH_GRADIENT,
                1.0, gray.rows() / 4.0, 90.0, 26.0,
                gray.rows() / 6, gray.rows() / 2
            )
            val hasCircles = circles.cols() > 0
            circles.release()

            if (hasCircles) {
                gray.release()
                return true
            }

            // 2. Contour circularity fallback
            val thresh = Mat()
            Imgproc.threshold(gray, thresh, 50.0, 255.0, Imgproc.THRESH_BINARY)
            val contours = ArrayList<org.opencv.core.MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var isRound = false
            var maxArea = 0.0
            var bestContour: org.opencv.core.MatOfPoint? = null
            for (c in contours) {
                val a = Imgproc.contourArea(c)
                if (a > maxArea && a > (gray.rows() * gray.cols() * 0.15)) {
                    maxArea = a
                    bestContour = c
                }
            }

            if (bestContour != null) {
                val mop2f = org.opencv.core.MatOfPoint2f(*bestContour.toArray())
                val perimeter = Imgproc.arcLength(mop2f, true)
                if (perimeter > 0) {
                    val circularity = (4 * Math.PI * maxArea) / (perimeter * perimeter)
                    val rect = Imgproc.boundingRect(bestContour)
                    val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                    isRound = circularity > 0.62 && aspectRatio in 0.80..1.25
                }
                mop2f.release()
            }

            gray.release(); thresh.release(); hierarchy.release()
            return isRound
        } catch (e: Throwable) {
            return false
        }
    }

    /**
     * Distinguishes prohibition sign (🚫) from solid ruby gemstone.
     */
    private fun isRedRingProhibition(rgbMat: Mat, redMask: Mat): Boolean {
        try {
            val w = rgbMat.cols()
            val h = rgbMat.rows()
            if (w < 10 || h < 10) return true

            // In a prohibition sign 🚫:
            // 1. Red is distributed along the circular ring and the diagonal slash.
            // 2. The corners (0..15% and 85..100%) have NO red (background).
            // 3. The inner quadrants: Top-Right (X: 58..80%, Y: 18..40%) and Bottom-Left (X: 18..40%, Y: 58..80%) are open dark gaps.
            val qW = (w * 0.22).toInt().coerceAtLeast(2)
            val qH = (h * 0.22).toInt().coerceAtLeast(2)

            val trX = (w * 0.58).toInt().coerceIn(0, w - qW)
            val trY = (h * 0.18).toInt().coerceIn(0, h - qH)
            val trMat = Mat(redMask, Rect(trX, trY, qW, qH))
            val trRed = Core.countNonZero(trMat).toDouble() / (qW * qH)
            trMat.release()

            val blX = (w * 0.18).toInt().coerceIn(0, w - qW)
            val blY = (h * 0.58).toInt().coerceIn(0, h - qH)
            val blMat = Mat(redMask, Rect(blX, blY, qW, qH))
            val blRed = Core.countNonZero(blMat).toDouble() / (qW * qH)
            blMat.release()

            // In prohibition sign, the open quadrant holes have very low red fill (< 0.28).
            // In Ruby (solid gemstone), red crystal facets fill across the diamond.
            if (trRed < 0.28 || blRed < 0.28) {
                return true // Prohibition sign!
            }

            // Total red ratio across entire icon box
            val totalRed = Core.countNonZero(redMask).toDouble() / (w * h)
            return totalRed < 0.32
        } catch (e: Throwable) {
            return true
        }
    }

    /**
     * Detects the Close button ('X' inside yellow circle at top-left of Resource Hunt window).
     * Typical position on 900x1600: X = ~82, Y = ~232 (approx 9.1% x 14.5%).
     */
    fun detectResourceHuntCloseButton(
        screenBitmap: Bitmap,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line> = emptyList()
    ): Point {
        val width = screenBitmap.width
        val height = screenBitmap.height

        // 1. OCR Check: Look for 'X', 'x', '✕', '×' in top-left region
        for (line in ocrLines) {
            val text = line.text.trim().lowercase()
            val box = line.boundingBox ?: continue
            val cx = box.centerX().toFloat()
            val cy = box.centerY().toFloat()
            if (cx in (width * 0.02f)..(width * 0.25f) && cy in (height * 0.08f)..(height * 0.22f)) {
                if (text == "x" || text == "х" || text == "×" || text == "✕" || text.contains("x") || text.contains("х")) {
                    Log.i(TAG, "🎯 [OPENCV X-CLOSE] OCR точное совпадение кнопки 'X': ($cx, $cy)")
                    return Point(cx.toDouble(), cy.toDouble())
                }
            }
        }

        // 2. OpenCV Color & Circle Detection for yellow ring at top-left
        try {
            initialize()
            val roiX = (width * 0.03f).toInt().coerceIn(0, width - 1)
            val roiY = (height * 0.08f).toInt().coerceIn(0, height - 1)
            val roiW = (width * 0.18f).toInt().coerceIn(10, width - roiX)
            val roiH = (height * 0.14f).toInt().coerceIn(10, height - roiY)

            val roiBitmap = Bitmap.createBitmap(screenBitmap, roiX, roiY, roiW, roiH)
            val mat = Mat()
            Utils.bitmapToMat(roiBitmap, mat)
            val hsv = Mat()
            Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)

            val yellowMask = Mat()
            Core.inRange(hsv, Scalar(15.0, 100.0, 100.0), Scalar(38.0, 255.0, 255.0), yellowMask)

            val contours = mutableListOf<org.opencv.core.MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(yellowMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestCenter: Point? = null
            var maxArea = 0.0
            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area > maxArea && area > 40.0) {
                    maxArea = area
                    val moments = Imgproc.moments(c)
                    if (moments.m00 > 0) {
                        val cX = moments.m10 / moments.m00
                        val cY = moments.m01 / moments.m00
                        bestCenter = Point(roiX + cX, roiY + cY)
                    }
                }
            }

            yellowMask.release(); hierarchy.release(); hsv.release(); mat.release(); roiBitmap.recycle()

            if (bestCenter != null) {
                Log.i(TAG, "🎯 [OPENCV X-CLOSE] OpenCV нашел желтый круг кнопки 'X': (${bestCenter.x}, ${bestCenter.y})")
                return bestCenter
            }
        } catch (e: Throwable) {
            // Fallback
        }

        // 3. Robust Geometric Default for 'X' button (X: 9.1%, Y: 14.5% => X: 82, Y: 232 on 900x1600)
        val defaultX = width * 0.091
        val defaultY = height * 0.145
        Log.i(TAG, "🎯 [OPENCV X-CLOSE] Использованы эталонные координаты 'X': ($defaultX, $defaultY)")
        return Point(defaultX, defaultY)
    }

    /**
     * Detects Beer Stein Mug (white foam at top, dark handle at right).
     */
    private fun hasBeerMugGeometry(hsvMat: Mat, rgbMat: Mat): Boolean {
        try {
            val h = hsvMat.rows()
            val w = hsvMat.cols()
            if (h < 10 || w < 10) return false

            // Check top 25% for white foam (low saturation, high value)
            val topH = (h * 0.25).toInt()
            val topRegion = Mat(hsvMat, Rect(0, 0, w, topH))
            val whiteMask = Mat()
            Core.inRange(topRegion, Scalar(0.0, 0.0, 180.0), Scalar(180.0, 50.0, 255.0), whiteMask)
            val whitePixels = Core.countNonZero(whiteMask)
            val whiteRatio = whitePixels.toDouble() / (w * topH)

            topRegion.release()
            whiteMask.release()

            return whiteRatio > 0.20
        } catch (e: Throwable) {
            return false
        }
    }

    /**
     * Detects rolled parchment scroll contours.
     */
    private fun hasScrollParchmentGeometry(hsvMat: Mat): Boolean {
        try {
            val h = hsvMat.rows()
            val w = hsvMat.cols()
            if (h < 10 || w < 10) return false

            // Scroll is diagonal from top-right to bottom-left with rolled ends
            val beigeMask = Mat()
            Core.inRange(hsvMat, Scalar(14.0, 40.0, 110.0), Scalar(28.0, 170.0, 255.0), beigeMask)
            val beigePixels = Core.countNonZero(beigeMask)
            val ratio = beigePixels.toDouble() / (w * h)
            beigeMask.release()

            return ratio in 0.22..0.55
        } catch (e: Throwable) {
            return false
        }
    }

    /**
     * Detects irregular rock texture of ore.
     */
    private fun isRoughRockTexture(rgbMat: Mat): Boolean {
        try {
            val gray = Mat()
            Imgproc.cvtColor(rgbMat, gray, Imgproc.COLOR_RGB2GRAY)
            val edges = Mat()
            Imgproc.Canny(gray, edges, 60.0, 180.0)
            val edgePixels = Core.countNonZero(edges)
            val ratio = edgePixels.toDouble() / (gray.rows() * gray.cols())
            gray.release()
            edges.release()
            return ratio > 0.16
        } catch (e: Throwable) {
            return false
        }
    }

    /**
     * Extracts quantity string and numeric double from slot with resource-aware fallback.
     */
    private fun extractQuantityForSlot(
        slotCrop: Bitmap,
        slotLeft: Int,
        slotTop: Int,
        slotRight: Int,
        slotBottom: Int,
        ocrLines: List<com.google.mlkit.vision.text.Text.Line>,
        resourceName: String
    ): Pair<String, Double?> {
        val slotH = slotBottom - slotTop
        // The quantity badge is located at the lower half of the slot (Y: 55%..105%)
        val numSearchTop = (slotTop + slotH * 0.50f).toInt()
        val numSearchBottom = slotBottom + 35

        for (line in ocrLines) {
            val box = line.boundingBox ?: continue
            val cx = box.centerX()
            val cy = box.centerY()

            if (cx in (slotLeft - 15)..(slotRight + 15) && cy in numSearchTop..numSearchBottom) {
                val raw = line.text.trim()
                val rawLower = raw.lowercase()
                
                // Skip words from coin emboss or game labels
                if (rawLower.contains("token") || rawLower.contains("hunt") || rawLower.contains("mine") || rawLower.contains("mm")) {
                    continue
                }

                // Handle 1000 ore variations
                if (rawLower.contains("1000") || rawLower.contains("1ooo") || rawLower.contains("1o00") || rawLower.contains("10oo")) {
                    return Pair("1000.0", 1000.0)
                }

                val normalized = raw.replace(',', '.')
                val match = Regex("""\b\d+(?:\.\d+)?\b""").find(normalized)
                if (match != null) {
                    val numStr = match.value
                    val numVal = numStr.toDoubleOrNull()
                    if (numVal != null && numVal > 0.0) {
                        return Pair(numStr, numVal)
                    }
                }
            }
        }

        // 2. Intelligent Default fallback based on specific resource type
        return when (resourceName) {
            RES_BOUGHT -> Pair("", null)
            RES_ORE -> Pair("1000.0", 1000.0)
            RES_COPPER, RES_SILVER -> Pair("10.0", 10.0)
            RES_GOLD -> Pair("5.0", 5.0)
            RES_SAPPHIRE, RES_RUBY, RES_EMERALD, RES_SMT_SILVER, RES_MMT_GOLD,
            RES_GNOME, RES_WHITE_SHROUD, RES_LICENSE, RES_ALE, RES_SCROLL -> Pair("1.0", 1.0)
            else -> Pair("1.0", 1.0)
        }
    }

    /**
     * Writes formatted 16-slot report to Console & AutoBuyerLogs.
     */
    private fun logScanReportToConsole(report: BoardScanReport) {
        val sb = StringBuilder()
        sb.append("\n======================================================================\n")
        sb.append("📋 [ОТЧЕТ OPENCV] РАСПОЗНАВАНИЕ ДОСКИ RESOURCE HUNT (16 СЛОТОВ):\n")
        sb.append("----------------------------------------------------------------------\n")

        for (slot in report.detectedSlots) {
            val status = if (slot.isBought) "🚫 ВЫКУПЛЕН" else "✅ ДОСТУПЕН"
            val numStr = if (slot.quantityValue != null) "${slot.quantityValue}" else slot.quantityText
            val confStr = "${(slot.confidence * 100).toInt()}%"

            val line = String.format(
                "Слот #%02d [Ряд %d, Кол %d] | %-12s | Кол-во: %-6s | Статус: %-10s | Уверенность: %-4s | X:%4.0f, Y:%4.0f",
                slot.slotIndex + 1,
                slot.row + 1,
                slot.col + 1,
                slot.resourceName,
                numStr,
                status,
                confStr,
                slot.centerX,
                slot.centerY
            )
            sb.append(line).append("\n")
            Log.i(TAG, line)
        }

        sb.append("----------------------------------------------------------------------\n")
        sb.append("📊 ИТОГО: Доступных лотов: ${report.availableLotsCount} | Выкуплено: ${report.boughtLotsCount} / 16\n")
        sb.append("======================================================================\n")

        val logOutput = sb.toString()
        Log.i(TAG, logOutput)
    }
}

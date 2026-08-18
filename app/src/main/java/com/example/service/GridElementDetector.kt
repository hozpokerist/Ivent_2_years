package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Data class representing a recognized slot in the 4x4 grid (16 elements).
 */
data class RecognizedSlot(
    val index: Int,            // 0 to 15
    val row: Int,              // 0 to 3
    val col: Int,              // 0 to 3
    val screenX: Float,        // Center X coordinate on screen
    val screenY: Float,        // Center Y coordinate on screen
    val similarity: Float,     // Match score (0.0 to 1.0)
    val elementName: String    // Name of matched target element
)

/**
 * Target element reference representation.
 */
data class ElementReference(
    val name: String,
    val referenceBitmap: Bitmap,
    val averageColor: IntArray = computeAverageColor(referenceBitmap),
    val normalizedPixels: FloatArray = extractNormalizedGrayscale(referenceBitmap)
) {
    companion object {
        fun computeAverageColor(bitmap: Bitmap): IntArray {
            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            val width = bitmap.width
            val height = bitmap.height
            val totalPixels = width * height
            if (totalPixels == 0) return intArrayOf(0, 0, 0)

            val pixels = IntArray(totalPixels)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            for (pixel in pixels) {
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
            }

            return intArrayOf(
                (totalR / totalPixels).toInt(),
                (totalG / totalPixels).toInt(),
                (totalB / totalPixels).toInt()
            )
        }

        fun extractNormalizedGrayscale(bitmap: Bitmap, size: Int = 16): FloatArray {
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val pixels = IntArray(size * size)
            scaled.getPixels(pixels, 0, size, 0, 0, size, size)
            val floats = FloatArray(size * size)

            for (i in pixels.indices) {
                val r = Color.red(pixels[i])
                val g = Color.green(pixels[i])
                val b = Color.blue(pixels[i])
                val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                floats[i] = gray
            }
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            return floats
        }
    }
}

/**
 * Advanced Graphical Element Detector for Unity 4x4 Grid UI (16 elements).
 */
class GridElementDetector {

    private val referenceRegistry = mutableMapOf<String, ElementReference>()
    private var lastTemplatesLoadTime: Long = 0L

    /**
     * Register a named element reference with its sample screenshot/template bitmap.
     */
    fun registerElementReference(name: String, referenceBitmap: Bitmap) {
        val key = name.lowercase().trim()
        val oldRef = referenceRegistry[key]
        if (oldRef != null && !oldRef.referenceBitmap.isRecycled && oldRef.referenceBitmap != referenceBitmap) {
            oldRef.referenceBitmap.recycle()
        }
        val elementRef = ElementReference(
            name = name,
            referenceBitmap = referenceBitmap
        )
        referenceRegistry[key] = elementRef
    }

    /**
     * Save an element reference template image to disk and register it.
     */
    fun saveAndRegisterElementTemplate(context: Context, elementName: String, bitmap: Bitmap) {
        try {
            val templatesDir = File(context.filesDir, "element_templates")
            if (!templatesDir.exists()) templatesDir.mkdirs()

            val file = File(templatesDir, "${elementName.lowercase().trim()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            registerElementReference(elementName, bitmap)
            lastTemplatesLoadTime = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Load all saved element template images from disk storage into the registry
     * only if they haven't been loaded yet or if template files have been updated.
     */
    @Synchronized
    fun loadSavedElementTemplates(context: Context, forceReload: Boolean = false) {
        try {
            val templatesDir = File(context.filesDir, "element_templates")
            if (!templatesDir.exists()) return

            val lastModified = templatesDir.lastModified()
            if (!forceReload && lastTemplatesLoadTime >= lastModified && referenceRegistry.isNotEmpty()) {
                return // Already up-to-date, avoid re-decoding and memory churn
            }

            templatesDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.extension.equals("png", ignoreCase = true) || file.extension.equals("jpg", ignoreCase = true))) {
                    val name = file.nameWithoutExtension.lowercase()
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        registerElementReference(name, bmp)
                    }
                }
            }
            lastTemplatesLoadTime = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clear all element references and recycle memory.
     */
    @Synchronized
    fun clearElementReferences() {
        referenceRegistry.values.forEach { ref ->
            if (!ref.referenceBitmap.isRecycled) {
                ref.referenceBitmap.recycle()
            }
        }
        referenceRegistry.clear()
        lastTemplatesLoadTime = 0L
    }

    /**
     * Check if a specific target element reference template is currently loaded.
     */
    fun hasRegisteredReference(targetElementName: String): Boolean {
        return referenceRegistry.containsKey(targetElementName.lowercase().trim())
    }

    /**
     * Calculates the default bounding region for the 4x4 grid.
     */
    fun getDefaultGridRegion(screenWidth: Float, screenHeight: Float): RectF {
        val left = screenWidth * 0.15f
        val top = screenHeight * 0.25f
        val right = screenWidth * 0.85f
        val bottom = screenHeight * 0.75f
        return RectF(left, top, right, bottom)
    }

    /**
     * Returns screen center coordinates (X, Y) for a grid slot index (0 to 15) in a 4x4 grid.
     */
    fun getSlotCoordinates(
        slotIndex: Int,
        screenWidth: Float,
        screenHeight: Float,
        gridBounds: RectF = getDefaultGridRegion(screenWidth, screenHeight)
    ): Pair<Float, Float> {
        val safeIndex = slotIndex.coerceIn(0, 15)
        val row = safeIndex / 4
        val col = safeIndex % 4

        val cellWidth = gridBounds.width() / 4f
        val cellHeight = gridBounds.height() / 4f

        val centerX = gridBounds.left + (col + 0.5f) * cellWidth
        val centerY = gridBounds.top + (row + 0.5f) * cellHeight

        return Pair(centerX, centerY)
    }

    /**
     * Returns screen coordinates for the top-left 'X' close button.
     * Top-left corner of window: X ~ 12% of screen width, Y ~ 15% of screen height.
     */
    fun getCloseButtonCoordinates(
        screenWidth: Float,
        screenHeight: Float,
        customX: Float = -1f,
        customY: Float = -1f
    ): Pair<Float, Float> {
        if (customX > 0f && customY > 0f) {
            return Pair(customX, customY)
        }
        val closeX = screenWidth * 0.12f
        val closeY = screenHeight * 0.15f
        return Pair(closeX, closeY)
    }

    /**
     * Crop 16 individual slot Bitmaps from the 4x4 grid in the screenshot.
     */
    fun extractGridSlots(
        screenBitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        gridBounds: RectF = getDefaultGridRegion(screenWidth, screenHeight)
    ): List<Pair<Int, Bitmap>> {
        val slots = mutableListOf<Pair<Int, Bitmap>>()
        val scaleX = screenBitmap.width / screenWidth
        val scaleY = screenBitmap.height / screenHeight

        val bitmapGridLeft = (gridBounds.left * scaleX).toInt().coerceIn(0, screenBitmap.width - 1)
        val bitmapGridTop = (gridBounds.top * scaleY).toInt().coerceIn(0, screenBitmap.height - 1)
        val bitmapGridRight = (gridBounds.right * scaleX).toInt().coerceIn(bitmapGridLeft + 1, screenBitmap.width)
        val bitmapGridBottom = (gridBounds.bottom * scaleY).toInt().coerceIn(bitmapGridTop + 1, screenBitmap.height)

        val gridWidth = bitmapGridRight - bitmapGridLeft
        val gridHeight = bitmapGridBottom - bitmapGridTop

        val cellWidth = gridWidth / 4
        val cellHeight = gridHeight / 4

        for (index in 0 until 16) {
            val row = index / 4
            val col = index % 4

            val x = bitmapGridLeft + col * cellWidth
            val y = bitmapGridTop + row * cellHeight

            val w = cellWidth.coerceAtMost(screenBitmap.width - x)
            val h = cellHeight.coerceAtMost(screenBitmap.height - y)

            if (w > 0 && h > 0) {
                try {
                    val croppedSlot = Bitmap.createBitmap(screenBitmap, x, y, w, h)
                    slots.add(Pair(index, croppedSlot))
                } catch (e: Exception) {
                    // Fallback on cropping failure
                }
            }
        }
        return slots
    }

    /**
     * Analyzes the 16 slots in the 4x4 grid and returns the best matching RecognizedSlot
     * for the target element name, or null if no slot meets the similarity threshold or if no reference template is set.
     */
    fun findMatchingSlotInGrid(
        screenBitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        targetElementName: String,
        minThreshold: Float = 0.70f,
        gridBounds: RectF = getDefaultGridRegion(screenWidth, screenHeight)
    ): RecognizedSlot? {
        val targetRef = referenceRegistry[targetElementName.lowercase().trim()]
        
        // SAFEGUARD: If no reference template exists for targetElementName, do NOT perform false positive random variance matching.
        if (targetRef == null) {
            return null
        }

        val slots = extractGridSlots(screenBitmap, screenWidth, screenHeight, gridBounds)

        var bestMatchSlot: RecognizedSlot? = null
        var maxScore = 0f

        for ((index, slotBitmap) in slots) {
            val row = index / 4
            val col = index % 4
            val (screenX, screenY) = getSlotCoordinates(index, screenWidth, screenHeight, gridBounds)

            val score = calculateBitmapSimilarity(slotBitmap, targetRef)

            if (score > maxScore && score >= minThreshold) {
                maxScore = score
                bestMatchSlot = RecognizedSlot(
                    index = index,
                    row = row,
                    col = col,
                    screenX = screenX,
                    screenY = screenY,
                    similarity = score,
                    elementName = targetElementName
                )
            }
            if (!slotBitmap.isRecycled) {
                slotBitmap.recycle()
            }
        }

        return bestMatchSlot
    }

    /**
     * Compute visual similarity between a slot bitmap and an element reference (0.0 to 1.0).
     */
    fun calculateBitmapSimilarity(slotBitmap: Bitmap, reference: ElementReference): Float {
        // 1. Normalized Grayscale Pixel Correlation
        val slotGrayscale = ElementReference.extractNormalizedGrayscale(slotBitmap, 16)
        val refGrayscale = reference.normalizedPixels

        var sumSqDiff = 0.0f
        for (i in slotGrayscale.indices) {
            val diff = slotGrayscale[i] - refGrayscale[i]
            sumSqDiff += diff * diff
        }
        val mse = sumSqDiff / slotGrayscale.size
        val pixelScore = (1.0f - sqrt(mse.toDouble()).toFloat()).coerceIn(0.0f, 1.0f)

        // 2. Average Color Similarity (RGB Distance)
        val slotColor = ElementReference.computeAverageColor(slotBitmap)
        val refColor = reference.averageColor

        val dr = (slotColor[0] - refColor[0]).toDouble()
        val dg = (slotColor[1] - refColor[1]).toDouble()
        val db = (slotColor[2] - refColor[2]).toDouble()

        val colorDist = sqrt(dr * dr + dg * dg + db * db) / sqrt(3.0 * 255.0 * 255.0)
        val colorScore = (1.0 - colorDist).toFloat().coerceIn(0.0f, 1.0f)

        // Weighted combination: 60% pixel correlation + 40% color similarity
        return (0.6f * pixelScore + 0.4f * colorScore)
    }
}

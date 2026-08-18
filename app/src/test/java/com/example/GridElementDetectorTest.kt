package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.service.ElementReference
import com.example.service.GridElementDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GridElementDetectorTest {

    private lateinit var detector: GridElementDetector

    @Before
    fun setUp() {
        detector = GridElementDetector()
    }

    @Test
    fun testGetSlotCoordinates_computesValidCenter() {
        val (x, y) = detector.getSlotCoordinates(slotIndex = 0, screenWidth = 1000f, screenHeight = 2000f)
        assertTrue(x > 0f)
        assertTrue(y > 0f)

        val (lastX, lastY) = detector.getSlotCoordinates(slotIndex = 15, screenWidth = 1000f, screenHeight = 2000f)
        assertTrue(lastX > x)
        assertTrue(lastY > y)
    }

    @Test
    fun testCloseButtonCoordinates_returnsTopLeftArea() {
        val (closeX, closeY) = detector.getCloseButtonCoordinates(screenWidth = 1000f, screenHeight = 2000f)
        assertEquals(120f, closeX, 0.1f)
        assertEquals(300f, closeY, 0.1f)
    }

    @Test
    fun testBitmapSimilarity_identicalBitmapsMatchWithHighScore() {
        val bitmap1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap1.eraseColor(Color.RED)

        val ref = ElementReference("red_item", bitmap1)
        detector.registerElementReference("red_item", bitmap1)

        val similarity = detector.calculateBitmapSimilarity(bitmap1, ref)
        assertTrue("Similarity should be very high for identical bitmap", similarity > 0.95f)
    }

    @Test
    fun testFindMatchingSlotInGrid_findsSlot() {
        val screen = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        screen.eraseColor(Color.BLUE)

        val targetSample = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        targetSample.eraseColor(Color.BLUE)

        detector.registerElementReference("target_item", targetSample)

        val result = detector.findMatchingSlotInGrid(
            screenBitmap = screen,
            screenWidth = 400f,
            screenHeight = 800f,
            targetElementName = "target_item",
            minThreshold = 0.50f
        )

        assertNotNull(result)
        assertEquals(0, result?.index)
    }
}

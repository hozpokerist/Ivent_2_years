package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager

object MediaProjectionHelper {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    @Volatile
    private var latestBitmap: Bitmap? = null

    fun hasProjection(): Boolean = mediaProjection != null

    fun initProjection(
        context: Context,
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        density: Int
    ) {
        stopProjection()

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                ?: return

        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        screenWidth = width
        screenHeight = height
        screenDensity = density

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                try {
                    val image = reader?.acquireLatestImage()
                    if (image != null) {
                        processImage(image)
                    }
                } catch (e: Exception) {
                    // Ignore transient acquisition frames
                }
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoBuyerScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun processImage(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            synchronized(this) {
                val old = latestBitmap
                latestBitmap = croppedBitmap
                if (old != null && old != croppedBitmap && !old.isRecycled) {
                    old.recycle()
                }
            }
        } catch (e: Exception) {
            // Processing error handling
        } finally {
            image.close()
        }
    }

    @Synchronized
    fun getLatestScreenshot(): Bitmap? {
        val current = latestBitmap ?: return null
        if (current.isRecycled) return null
        return try {
            current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            null
        }
    }

    fun stopProjection() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            synchronized(this) {
                latestBitmap?.recycle()
                latestBitmap = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

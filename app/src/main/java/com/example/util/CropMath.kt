package com.example.util

import kotlin.math.max
import kotlin.math.roundToInt

data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

object CropMath {

    /**
     * Calculates the minimum scale required so that the source image completely covers
     * the target crop frame (ensuring no empty / transparent gaps inside the frame).
     */
    fun calculateMinScale(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float
    ): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || frameWidth <= 0f || frameHeight <= 0f) return 1f
        val scaleX = frameWidth / imageWidth.toFloat()
        val scaleY = frameHeight / imageHeight.toFloat()
        return max(scaleX, scaleY)
    }

    /**
     * Calculates the maximum horizontal and vertical pan offsets allowed relative to center,
     * ensuring the frame remains entirely covered by the image.
     */
    fun calculateMaxOffsets(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
        currentScale: Float
    ): Pair<Float, Float> {
        val displayWidth = imageWidth * currentScale
        val displayHeight = imageHeight * currentScale
        val maxOffsetX = max(0f, (displayWidth - frameWidth) / 2f)
        val maxOffsetY = max(0f, (displayHeight - frameHeight) / 2f)
        return Pair(maxOffsetX, maxOffsetY)
    }

    /**
     * Pure function converting viewport dimensions, current scale, pan offsets,
     * and image dimensions into an exact integer CropRect within the source bitmap.
     */
    fun calculateCropRect(
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): CropRect {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return CropRect(0, 0, 0, 0)
        }
        val safeScale = if (scale <= 0f) 1f else scale
        val displayW = imageWidth * safeScale
        val displayH = imageHeight * safeScale

        // Clamp offsets so the frame never samples outside the source image bounds
        val maxOffsets = calculateMaxOffsets(imageWidth, imageHeight, frameWidth, frameHeight, safeScale)
        val clampedOffsetX = offsetX.coerceIn(-maxOffsets.first, maxOffsets.first)
        val clampedOffsetY = offsetY.coerceIn(-maxOffsets.second, maxOffsets.second)

        // Distance from image top-left to frame top-left in viewport pixels
        val screenLeft = (displayW - frameWidth) / 2f - clampedOffsetX
        val screenTop = (displayH - frameHeight) / 2f - clampedOffsetY

        // Map viewport pixels to bitmap pixels
        val bitmapLeft = (screenLeft / safeScale).roundToInt()
        val bitmapTop = (screenTop / safeScale).roundToInt()
        val bitmapW = (frameWidth / safeScale).roundToInt()
        val bitmapH = (frameHeight / safeScale).roundToInt()

        val safeLeft = bitmapLeft.coerceIn(0, (imageWidth - 1).coerceAtLeast(0))
        val safeTop = bitmapTop.coerceIn(0, (imageHeight - 1).coerceAtLeast(0))
        val safeWidth = bitmapW.coerceIn(1, imageWidth - safeLeft)
        val safeHeight = bitmapH.coerceIn(1, imageHeight - safeTop)

        return CropRect(
            left = safeLeft,
            top = safeTop,
            width = safeWidth,
            height = safeHeight
        )
    }
}

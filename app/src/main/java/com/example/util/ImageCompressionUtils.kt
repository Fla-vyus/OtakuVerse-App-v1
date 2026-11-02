package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageCompressionUtils {

    private const val TAG = "ImageCompression"
    const val MAX_DECODE_DIMENSION = 2048

    /**
     * Calculates optimal inSampleSize so that neither width nor height exceeds maxDimension.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int = MAX_DECODE_DIMENSION): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        while ((height / inSampleSize) > maxDimension || (width / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Decodes a downsampled Bitmap from Uri respecting maxDimension (<= 2048px) and EXIF rotation.
     */
    fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            rotateBitmapAccordingToExif(context, uri, decoded)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao decodificar imagem: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao decodificar bitmap do Uri: ${e.message}")
            null
        }
    }

    /**
     * Rotates bitmap according to EXIF orientation metadata using android.media.ExifInterface.
     */
    fun rotateBitmapAccordingToExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao rotacionar bitmap: ${e.message}")
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler EXIF: ${e.message}")
            bitmap
        }
    }

    /**
     * Crops source bitmap using user-defined CropRect and scales to target 256x256 WebP for avatar.
     */
    suspend fun cropAndSaveAvatar(
        context: Context,
        sourceBitmap: Bitmap,
        cropRect: CropRect
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeX = cropRect.left.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
            val safeY = cropRect.top.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
            val safeW = cropRect.width.coerceIn(1, sourceBitmap.width - safeX)
            val safeH = cropRect.height.coerceIn(1, sourceBitmap.height - safeY)

            val cropped = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeW, safeH)
            val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
            if (scaled != cropped) {
                cropped.recycle()
            }

            val outputFile = File(context.filesDir, "avatar.webp")
            FileOutputStream(outputFile).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            scaled.recycle()

            Log.i(TAG, "Avatar recortado e salvo com sucesso: ${outputFile.length() / 1024} KB")
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao recortar avatar: ${e.message}")
            Result.failure(Exception("Memória insuficiente ao recortar o avatar."))
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao recortar avatar: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Crops source bitmap using user-defined CropRect and scales to target 1280x720 JPEG q80 for banner.
     */
    suspend fun cropAndSaveBanner(
        context: Context,
        sourceBitmap: Bitmap,
        cropRect: CropRect
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeX = cropRect.left.coerceIn(0, (sourceBitmap.width - 1).coerceAtLeast(0))
            val safeY = cropRect.top.coerceIn(0, (sourceBitmap.height - 1).coerceAtLeast(0))
            val safeW = cropRect.width.coerceIn(1, sourceBitmap.width - safeX)
            val safeH = cropRect.height.coerceIn(1, sourceBitmap.height - safeY)

            val cropped = Bitmap.createBitmap(sourceBitmap, safeX, safeY, safeW, safeH)
            val scaled = Bitmap.createScaledBitmap(cropped, 1280, 720, true)
            if (scaled != cropped) {
                cropped.recycle()
            }

            val outputFile = File(context.filesDir, "banner.jpg")
            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            scaled.recycle()

            Log.i(TAG, "Capa recortada e salva com sucesso: ${outputFile.length() / 1024} KB")
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError ao recortar capa: ${e.message}")
            Result.failure(Exception("Memória insuficiente ao recortar a capa."))
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao recortar capa: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fallback automatic 1:1 avatar processor using downsampled bitmap.
     */
    suspend fun processAndSaveAvatar(context: Context, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val original = decodeSampledBitmap(context, sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Não foi possível carregar imagem do avatar."))

            val minSide = minOf(original.width, original.height)
            val cropX = (original.width - minSide) / 2
            val cropY = (original.height - minSide) / 2
            val square = Bitmap.createBitmap(original, cropX, cropY, minSide, minSide)
            original.recycle()

            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
            if (scaled != square) {
                square.recycle()
            }

            val outputFile = File(context.filesDir, "avatar.webp")
            FileOutputStream(outputFile).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            scaled.recycle()

            Log.i(TAG, "Avatar salvo com sucesso: ${outputFile.length() / 1024} KB")
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Memória insuficiente para processar avatar."))
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao processar avatar: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fallback automatic 16:9 banner processor using downsampled bitmap.
     */
    suspend fun processAndSaveBanner(context: Context, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val original = decodeSampledBitmap(context, sourceUri)
                ?: return@withContext Result.failure(IllegalStateException("Não foi possível carregar imagem da capa."))

            val targetRatio = 16f / 9f
            val currentRatio = original.width.toFloat() / original.height.toFloat()

            val cropWidth: Int
            val cropHeight: Int
            val cropX: Int
            val cropY: Int

            if (currentRatio > targetRatio) {
                cropHeight = original.height
                cropWidth = (cropHeight * targetRatio).toInt()
                cropX = (original.width - cropWidth) / 2
                cropY = 0
            } else {
                cropWidth = original.width
                cropHeight = (cropWidth / targetRatio).toInt()
                cropX = 0
                cropY = (original.height - cropHeight) / 2
            }

            val cropped = Bitmap.createBitmap(original, cropX, cropY, cropWidth, cropHeight)
            original.recycle()

            val scaled = Bitmap.createScaledBitmap(cropped, 1280, 720, true)
            if (scaled != cropped) {
                cropped.recycle()
            }

            val outputFile = File(context.filesDir, "banner.jpg")
            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            scaled.recycle()

            Log.i(TAG, "Capa salva com sucesso: ${outputFile.length() / 1024} KB")
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Memória insuficiente para processar capa."))
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao processar capa: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Saves direct work cover bitmap as 16:9 banner.
     */
    suspend fun saveBitmapAsBanner(context: Context, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetRatio = 16f / 9f
            val currentRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            val cropWidth: Int
            val cropHeight: Int
            val cropX: Int
            val cropY: Int

            if (currentRatio > targetRatio) {
                cropHeight = bitmap.height
                cropWidth = (cropHeight * targetRatio).toInt()
                cropX = (bitmap.width - cropWidth) / 2
                cropY = 0
            } else {
                cropWidth = bitmap.width
                cropHeight = (cropWidth / targetRatio).toInt()
                cropX = 0
                cropY = (bitmap.height - cropHeight) / 2
            }

            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
            val scaled = Bitmap.createScaledBitmap(cropped, 1280, 720, true)
            if (scaled != cropped) {
                cropped.recycle()
            }

            val outputFile = File(context.filesDir, "banner.jpg")
            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            scaled.recycle()
            Result.success(outputFile.absolutePath)
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Memória insuficiente ao salvar capa."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

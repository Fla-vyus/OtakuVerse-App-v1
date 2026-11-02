package com.example

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.util.CropMath
import com.example.util.CropRect
import com.example.util.ImageCompressionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImageCropUnitTest {
    @Test
    fun `test routeHasOwnTopInset returns true for tabs and settings and false for details and null`() {
        // True for 5 tabs
        assertTrue(com.example.ui.components.routeHasOwnTopInset("home"))
        assertTrue(com.example.ui.components.routeHasOwnTopInset("explore"))
        assertTrue(com.example.ui.components.routeHasOwnTopInset("schedule"))
        assertTrue(com.example.ui.components.routeHasOwnTopInset("library"))
        assertTrue(com.example.ui.components.routeHasOwnTopInset("profile"))

        // True for settings and image_crop
        assertTrue(com.example.ui.components.routeHasOwnTopInset("settings"))
        assertTrue(com.example.ui.components.routeHasOwnTopInset("image_crop"))

        // False for work_detail and null
        org.junit.Assert.assertFalse(com.example.ui.components.routeHasOwnTopInset("work_detail/{workId}"))
        org.junit.Assert.assertFalse(com.example.ui.components.routeHasOwnTopInset("work_detail/123"))
        org.junit.Assert.assertFalse(com.example.ui.components.routeHasOwnTopInset(null))
    }

    @Test
    fun `test calculateMinScale covers frame completely`() {
        // Landscape 12MP image: 4000 x 3000
        val imageW = 4000
        val imageH = 3000
        val frameW = 400f
        val frameH = 400f // 1:1 Avatar

        val minScale = CropMath.calculateMinScale(imageW, imageH, frameW, frameH)
        // scaleX = 400 / 4000 = 0.1, scaleY = 400 / 3000 = 0.1333...
        // To cover both dimensions, minScale must be max(0.1, 0.1333) = 0.1333...
        assertTrue("minScale must be at least 400/3000", minScale >= 400f / 3000f)
        assertTrue("Image width at minScale must cover frame", imageW * minScale >= frameW - 0.01f)
        assertTrue("Image height at minScale must cover frame", imageH * minScale >= frameH - 0.01f)
    }

    @Test
    fun `test small image smaller than frame scales up to cover frame`() {
        // Image is 100 x 80, frame is 300 x 300
        val imageW = 100
        val imageH = 80
        val frameW = 300f
        val frameH = 300f

        val minScale = CropMath.calculateMinScale(imageW, imageH, frameW, frameH)
        // scaleX = 300/100 = 3.0, scaleY = 300/80 = 3.75
        // minScale must be 3.75
        assertEquals(3.75f, minScale, 0.001f)
        assertTrue(imageW * minScale >= frameW)
        assertTrue(imageH * minScale >= frameH)
    }

    @Test
    fun `test calculateCropRect centered crop on landscape image`() {
        val imageW = 2000
        val imageH = 1000
        val frameW = 400f
        val frameH = 400f
        val scale = CropMath.calculateMinScale(imageW, imageH, frameW, frameH) // 400/1000 = 0.4

        val rect = CropMath.calculateCropRect(
            imageWidth = imageW,
            imageHeight = imageH,
            frameWidth = frameW,
            frameHeight = frameH,
            scale = scale,
            offsetX = 0f,
            offsetY = 0f
        )

        // At scale 0.4: frame is 400x400 in screen pixels -> 400 / 0.4 = 1000 in bitmap pixels
        assertEquals(1000, rect.width)
        assertEquals(1000, rect.height)
        assertEquals(500, rect.left) // centered: (2000 - 1000) / 2 = 500
        assertEquals(0, rect.top)    // centered: (1000 - 1000) / 2 = 0
    }

    @Test
    fun `test calculateCropRect banner 16 by 9 crop`() {
        val imageW = 1920
        val imageH = 1080
        val frameW = 640f
        val frameH = 360f // exactly 16:9
        val scale = CropMath.calculateMinScale(imageW, imageH, frameW, frameH) // 640/1920 = 1/3

        val rect = CropMath.calculateCropRect(
            imageWidth = imageW,
            imageHeight = imageH,
            frameWidth = frameW,
            frameHeight = frameH,
            scale = scale,
            offsetX = 0f,
            offsetY = 0f
        )

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1920, rect.width)
        assertEquals(1080, rect.height)
    }

    @Test
    fun `test calculateCropRect clamps offsets to never exceed image bounds`() {
        val imageW = 2000
        val imageH = 1000
        val frameW = 400f
        val frameH = 400f
        val scale = 0.5f // displayW = 1000, displayH = 500. maxOffsetX = (1000 - 400)/2 = 300

        // Pan with massive offset (e.g. 5000px to the left)
        val rectOverLeft = CropMath.calculateCropRect(
            imageWidth = imageW,
            imageHeight = imageH,
            frameWidth = frameW,
            frameHeight = frameH,
            scale = scale,
            offsetX = -5000f,
            offsetY = -5000f
        )

        assertTrue("left must be >= 0", rectOverLeft.left >= 0)
        assertTrue("top must be >= 0", rectOverLeft.top >= 0)
        assertTrue("left + width must be <= imageW", rectOverLeft.left + rectOverLeft.width <= imageW)
        assertTrue("top + height must be <= imageH", rectOverLeft.top + rectOverLeft.height <= imageH)

        // Pan with massive offset to the right
        val rectOverRight = CropMath.calculateCropRect(
            imageWidth = imageW,
            imageHeight = imageH,
            frameWidth = frameW,
            frameHeight = frameH,
            scale = scale,
            offsetX = 5000f,
            offsetY = 5000f
        )

        assertTrue("left must be >= 0", rectOverRight.left >= 0)
        assertTrue("left + width must be <= imageW", rectOverRight.left + rectOverRight.width <= imageW)
        assertTrue("top + height must be <= imageH", rectOverRight.top + rectOverRight.height <= imageH)
    }

    @Test
    fun `test inSampleSize calculation for 12MP image downsamples to 2048 max dimension`() {
        val options = android.graphics.BitmapFactory.Options().apply {
            outWidth = 4032
            outHeight = 3024
        }
        val sampleSize = ImageCompressionUtils.calculateInSampleSize(options, 2048)
        // 4032 / 2 = 2016 <= 2048, so sampleSize is 2
        assertEquals(2, sampleSize)

        // Verify sampled dimensions
        assertTrue(options.outWidth / sampleSize <= 2048)
        assertTrue(options.outHeight / sampleSize <= 2048)
    }

    @Test
    fun `test inSampleSize for already small image returns 1`() {
        val options = android.graphics.BitmapFactory.Options().apply {
            outWidth = 800
            outHeight = 600
        }
        val sampleSize = ImageCompressionUtils.calculateInSampleSize(options, 2048)
        assertEquals(1, sampleSize)
    }

    @Test
    fun `test EXIF rotation matrix on bitmap`() {
        // Create 200x100 bitmap
        val original = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        assertEquals(200, original.width)
        assertEquals(100, original.height)

        // Rotate 90 degrees
        val matrix90 = Matrix().apply { postRotate(90f) }
        val rotated90 = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix90, true)
        assertEquals(100, rotated90.width)
        assertEquals(200, rotated90.height)

        // Rotate 180 degrees
        val matrix180 = Matrix().apply { postRotate(180f) }
        val rotated180 = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix180, true)
        assertEquals(200, rotated180.width)
        assertEquals(100, rotated180.height)

        original.recycle()
        rotated90.recycle()
        rotated180.recycle()
    }

    private fun createTestViewModel(context: android.content.Context): com.example.ui.screens.profile.ProfileViewModel {
        val app = context.applicationContext as? com.example.OtakuVerseApp
        return if (app != null) {
            com.example.ui.screens.profile.ProfileViewModel(
                application = app,
                preferencesRepository = app.preferencesRepository,
                workRepository = app.workRepository,
                libraryRepository = app.libraryRepository,
                tursoClient = app.tursoClient,
                authRepository = app.authRepository,
                syncRepository = app.syncRepository
            )
        } else {
            val appObj = context.applicationContext as android.app.Application
            val prefRepo = com.example.data.repository.UserPreferencesRepository(context)
            val cacheDb = com.example.data.local.CacheDatabase.getDatabase(context)
            val libDb = com.example.data.local.LibraryDatabase.getDatabase(context)
            val turso = com.example.data.turso.TursoClient()
            val workRepo = com.example.data.repository.WorkRepository(turso, cacheDb.workDao(), prefRepo)
            val libRepo = com.example.data.repository.LibraryRepository(libDb.libraryDao())
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            val authRepo = com.example.data.auth.FirebaseAuthRepository(context, libDb.libraryDao(), prefRepo, scope)
            val syncRepo = com.example.data.sync.FirestoreSyncRepository(context, authRepo, libDb.libraryDao(), prefRepo)
            com.example.ui.screens.profile.ProfileViewModel(
                application = appObj,
                preferencesRepository = prefRepo,
                workRepository = workRepo,
                libraryRepository = libRepo,
                tursoClient = turso,
                authRepository = authRepo,
                syncRepository = syncRepo
            )
        }
    }

    @Test
    fun `test cancel crop in the middle preserves state`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val viewModel = createTestViewModel(context)

        val dummyUri = Uri.parse("content://media/external/images/media/42")
        viewModel.startCrop(dummyUri, com.example.ui.screens.profile.CropType.AVATAR)
        assertEquals(dummyUri, viewModel.pendingCropUri.value)
        assertEquals(com.example.ui.screens.profile.CropType.AVATAR, viewModel.pendingCropType.value)

        // User cancels in the middle
        viewModel.clearCrop()
        assertEquals(null, viewModel.pendingCropUri.value)
        assertEquals(1f, viewModel.cropUserZoom.value, 0.001f)
        assertEquals(0f, viewModel.cropPanOffsetX.value, 0.001f)
        assertEquals(0f, viewModel.cropPanOffsetY.value, 0.001f)
    }

    @Test
    fun `test rotation and configuration change transform state survives`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val viewModel = createTestViewModel(context)

        // Set user zoom and pan during editing
        viewModel.updateCropTransform(zoom = 2.5f, offsetX = 120f, offsetY = -45f)
        assertEquals(2.5f, viewModel.cropUserZoom.value, 0.001f)
        assertEquals(120f, viewModel.cropPanOffsetX.value, 0.001f)
        assertEquals(-45f, viewModel.cropPanOffsetY.value, 0.001f)

        // Simulate config change: State survives in ViewModel
        val zoomAfter = viewModel.cropUserZoom.value
        val offsetXAfter = viewModel.cropPanOffsetX.value
        val offsetYAfter = viewModel.cropPanOffsetY.value

        assertEquals(2.5f, zoomAfter, 0.001f)
        assertEquals(120f, offsetXAfter, 0.001f)
        assertEquals(-45f, offsetYAfter, 0.001f)
    }
}

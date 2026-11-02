package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.CacheDatabase
import com.example.data.local.LibraryDatabase
import com.example.data.repository.LibraryRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WorkRepository
import com.example.data.turso.TursoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OtakuVerseApp : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val cacheDatabase by lazy { CacheDatabase.getDatabase(this) }
    val libraryDatabase by lazy { LibraryDatabase.getDatabase(this) }
    val preferencesRepository by lazy { UserPreferencesRepository(this) }
    val networkMonitor by lazy { com.example.data.network.NetworkMonitor(this) }

    @Volatile
    private var cachedCustomToken: String = ""

    val tursoClient by lazy {
        TursoClient(
            customTokenProvider = { cachedCustomToken.takeIf { it.isNotBlank() } },
            suspendTokenProvider = { preferencesRepository.customToken.first().takeIf { it.isNotBlank() } }
        )
    }

    val workRepository by lazy {
        WorkRepository(
            tursoClient = tursoClient,
            workDao = cacheDatabase.workDao(),
            userPreferencesRepository = preferencesRepository
        )
    }

    val libraryRepository by lazy {
        LibraryRepository(
            libraryDao = libraryDatabase.libraryDao()
        )
    }

    val authRepository by lazy {
        com.example.data.auth.FirebaseAuthRepository(
            context = this,
            libraryDao = libraryDatabase.libraryDao(),
            preferencesRepository = preferencesRepository,
            applicationScope = applicationScope
        )
    }

    val syncRepository by lazy {
        com.example.data.sync.FirestoreSyncRepository(
            context = this,
            authRepository = authRepository,
            libraryDao = libraryDatabase.libraryDao(),
            preferencesRepository = preferencesRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize notification channel
        com.example.notification.NotificationHelper.createNotificationChannel(this)

        // Collect custom token reactively on background IO scope without runBlocking
        applicationScope.launch {
            preferencesRepository.customToken.collect { token ->
                cachedCustomToken = token.trim()
            }
        }

        // Trigger periodic sync or initial check on launch
        applicationScope.launch {
            preferencesRepository.notificationsEnabled.collect { enabled ->
                if (enabled) {
                    com.example.notification.NotificationHelper.schedulePeriodicCheck(this@OtakuVerseApp)
                } else {
                    com.example.notification.NotificationHelper.cancelPeriodicCheck(this@OtakuVerseApp)
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}

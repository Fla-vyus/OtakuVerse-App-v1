package com.example.ui.screens.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthState
import com.example.data.auth.AuthUser
import com.example.data.local.LibraryEntity
import com.example.data.model.AdultMode
import com.example.data.model.WorkCard
import com.example.data.repository.LibraryRepository
import com.example.data.repository.LibraryStats
import com.example.data.repository.ThemeOption
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WorkRepository
import com.example.data.sync.SyncRepository
import com.example.data.sync.SyncState
import com.example.data.turso.TursoClient
import com.example.util.ImageCompressionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

enum class CropType {
    AVATAR,
    BANNER
}

data class ConnectionTestState(
    val isTesting: Boolean = false,
    val isSuccess: Boolean? = null,
    val message: String? = null,
    val latencyMs: Long? = null,
    val count: Long? = null
)

data class OtakuAchievement(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val isUnlocked: Boolean
)

class ProfileViewModel(
    application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val workRepository: WorkRepository,
    private val libraryRepository: LibraryRepository,
    private val tursoClient: TursoClient,
    val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : AndroidViewModel(application) {

    // Auth & Sync
    val authState: StateFlow<AuthState> = authRepository.authState
    val syncState: StateFlow<SyncState> = syncRepository.syncState
    val lastSyncTime: StateFlow<Long> = syncRepository.lastSyncTime
    val syncError: StateFlow<String?> = syncRepository.syncError

    // Appearance & Display
    val selectedTheme: StateFlow<ThemeOption> = preferencesRepository.selectedTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeOption.NOITE_AZUL)

    val fontSizeScale: StateFlow<String> = preferencesRepository.fontSizeScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "normal")

    val gridColumns: StateFlow<Int> = preferencesRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    // User Profile
    val userNickname: StateFlow<String> = preferencesRepository.userNickname
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Otaku")

    val userBio: StateFlow<String> = preferencesRepository.userBio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val avatarColorHex: StateFlow<String> = preferencesRepository.avatarColorHex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#3B82F6")

    val avatarImagePath: StateFlow<String?> = preferencesRepository.avatarImagePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bannerImagePath: StateFlow<String?> = preferencesRepository.bannerImagePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favoriteWorkId: StateFlow<String?> = preferencesRepository.favoriteWorkId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val profileAccentColor: StateFlow<String> = preferencesRepository.profileAccentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#3B82F6")

    // Content Preferences
    val preferredTitleLang: StateFlow<String> = preferencesRepository.preferredTitleLang
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "english")

    val preferSynopsisPt: StateFlow<Boolean> = preferencesRepository.preferSynopsisPt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hideLibraryInExplore: StateFlow<Boolean> = preferencesRepository.hideLibraryInExplore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Adult Content (B2)
    val adultMode: StateFlow<AdultMode> = preferencesRepository.adultMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdultMode.OFF)

    val hasConfirmedAge: StateFlow<Boolean> = preferencesRepository.hasConfirmedAge
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blurAdultCovers: StateFlow<Boolean> = preferencesRepository.blurAdultCovers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isAuxTablesAvailable: Boolean
        get() = workRepository.isAuxTablesAvailable

    // Notifications
    val notificationsEnabled: StateFlow<Boolean> = preferencesRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val quietHours: StateFlow<Pair<Int, Int>> = preferencesRepository.quietHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(22, 8))

    // Library & Stats
    val libraryStats: StateFlow<LibraryStats> = libraryRepository.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    val allLibraryWorks: StateFlow<List<LibraryEntity>> = libraryRepository.getWorksForTab(com.example.data.repository.LibraryTab.ALL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Achievements derived from library statistics
    val achievements: StateFlow<List<OtakuAchievement>> = libraryStats.combine(preferencesRepository.userNickname) { stats, _ ->
        listOf(
            OtakuAchievement(
                id = "ach_1",
                title = "Primeiro Passo",
                description = "Adicionou a 1ª obra à biblioteca",
                iconEmoji = "🌱",
                isUnlocked = stats.totalCount >= 1
            ),
            OtakuAchievement(
                id = "ach_2",
                title = "Maratonista",
                description = "Concluiu 5 obras completas",
                iconEmoji = "🏃",
                isUnlocked = stats.completedCount >= 5
            ),
            OtakuAchievement(
                id = "ach_3",
                title = "Devorador de Obras",
                description = "10 obras concluídas na conta",
                iconEmoji = "📚",
                isUnlocked = stats.completedCount >= 10
            ),
            OtakuAchievement(
                id = "ach_4",
                title = "Colecionador Otaku",
                description = "50 obras salvas na sua coleção",
                iconEmoji = "👑",
                isUnlocked = stats.totalCount >= 50
            ),
            OtakuAchievement(
                id = "ach_5",
                title = "Crítico Exigente",
                description = "Avaliou e deu nota para 10 obras",
                iconEmoji = "⭐",
                isUnlocked = (stats.averagePersonalRating != null && stats.completedCount >= 3)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Diagnostics & Tools
    val customToken: StateFlow<String> = preferencesRepository.customToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val buildConfigTokenLength: Int
        get() = tursoClient.getBuildConfigToken().length

    val diagnosticInfo: StateFlow<Triple<String?, Long, String?>> = preferencesRepository.diagnosticInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(null, 0L, null))

    private val _connectionTest = MutableStateFlow(ConnectionTestState())
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    private val _cacheSizeFormatted = MutableStateFlow("Calculando...")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _firebaseStatus = MutableStateFlow<com.example.data.auth.FirebaseStatus?>(null)
    val firebaseStatus: StateFlow<com.example.data.auth.FirebaseStatus?> = _firebaseStatus.asStateFlow()

    init {
        calculateCacheSize()
        refreshFirebaseStatus()
    }

    // ------------------------------------------------------------------------
    // Sync & Auth
    // ------------------------------------------------------------------------
    fun syncNow() {
        viewModelScope.launch {
            val res = syncRepository.syncNow()
            res.fold(
                onSuccess = {
                    _userMessage.value = "Sincronização concluída com sucesso!"
                },
                onFailure = { err ->
                    _userMessage.value = "Erro ao sincronizar: ${err.localizedMessage}"
                }
            )
        }
    }

    fun signOut(keepLocalData: Boolean) {
        viewModelScope.launch {
            authRepository.signOut(keepLocalData)
            _userMessage.value = if (keepLocalData) "Desconectado com sucesso." else "Desconectado e dados limpos."
        }
    }

    fun deleteAccountAndData() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user != null) {
                syncRepository.deleteRemoteUserData(user.uid)
            }
            authRepository.deleteAccountAndData()
            clearCache()
            _userMessage.value = "Conta e dados excluídos com sucesso."
        }
    }

    // ------------------------------------------------------------------------
    // Profile Customization
    // ------------------------------------------------------------------------
    fun updateProfile(nickname: String, bio: String, accentHex: String, favoriteId: String?) {
        viewModelScope.launch {
            if (nickname.isNotBlank()) preferencesRepository.setNickname(nickname)
            preferencesRepository.setBio(bio)
            preferencesRepository.setProfileAccentColor(accentHex)
            preferencesRepository.setFavoriteWorkId(favoriteId)
            // Trigger upload to Firestore if logged in
            syncRepository.uploadProfile()
            _userMessage.value = "Perfil atualizado com sucesso!"
        }
    }

    // Crop Editor State (Etapa 8B)
    private val _pendingCropUri = MutableStateFlow<Uri?>(null)
    val pendingCropUri: StateFlow<Uri?> = _pendingCropUri.asStateFlow()

    private val _pendingCropType = MutableStateFlow(CropType.AVATAR)
    val pendingCropType: StateFlow<CropType> = _pendingCropType.asStateFlow()

    private val _cropUserZoom = MutableStateFlow(1f)
    val cropUserZoom: StateFlow<Float> = _cropUserZoom.asStateFlow()

    private val _cropPanOffsetX = MutableStateFlow(0f)
    val cropPanOffsetX: StateFlow<Float> = _cropPanOffsetX.asStateFlow()

    private val _cropPanOffsetY = MutableStateFlow(0f)
    val cropPanOffsetY: StateFlow<Float> = _cropPanOffsetY.asStateFlow()

    private val _isSavingCrop = MutableStateFlow(false)
    val isSavingCrop: StateFlow<Boolean> = _isSavingCrop.asStateFlow()

    fun startCrop(uri: Uri, type: CropType) {
        _pendingCropUri.value = uri
        _pendingCropType.value = type
        _cropUserZoom.value = 1f
        _cropPanOffsetX.value = 0f
        _cropPanOffsetY.value = 0f
    }

    fun clearCrop() {
        _pendingCropUri.value = null
        _cropUserZoom.value = 1f
        _cropPanOffsetX.value = 0f
        _cropPanOffsetY.value = 0f
    }

    fun updateCropTransform(zoom: Float, offsetX: Float, offsetY: Float) {
        _cropUserZoom.value = zoom
        _cropPanOffsetX.value = offsetX
        _cropPanOffsetY.value = offsetY
    }

    fun resetCropTransform() {
        _cropUserZoom.value = 1f
        _cropPanOffsetX.value = 0f
        _cropPanOffsetY.value = 0f
    }

    fun applyCrop(sourceBitmap: Bitmap, cropRect: com.example.util.CropRect, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSavingCrop.value = true
            val type = _pendingCropType.value
            val result = withContext(Dispatchers.IO) {
                try {
                    if (type == CropType.AVATAR) {
                        ImageCompressionUtils.cropAndSaveAvatar(getApplication(), sourceBitmap, cropRect)
                    } else {
                        ImageCompressionUtils.cropAndSaveBanner(getApplication(), sourceBitmap, cropRect)
                    }
                } catch (e: OutOfMemoryError) {
                    Result.failure(Exception("Memória insuficiente para recortar imagem."))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            _isSavingCrop.value = false
            result.fold(
                onSuccess = { path ->
                    if (type == CropType.AVATAR) {
                        preferencesRepository.setAvatarImagePath(path)
                        _userMessage.value = "Foto de perfil atualizada!"
                    } else {
                        preferencesRepository.setBannerImagePath(path)
                        _userMessage.value = "Capa do perfil atualizada!"
                    }
                    syncRepository.uploadProfile()
                    clearCrop()
                    onComplete(true)
                },
                onFailure = { err ->
                    _userMessage.value = "Erro ao salvar recorte: ${err.message}"
                    onComplete(false)
                }
            )
        }
    }

    fun updateAvatarFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = ImageCompressionUtils.processAndSaveAvatar(getApplication(), uri)
            result.fold(
                onSuccess = { path ->
                    preferencesRepository.setAvatarImagePath(path)
                    syncRepository.uploadProfile()
                    _userMessage.value = "Foto de perfil atualizada!"
                },
                onFailure = { err ->
                    _userMessage.value = "Erro ao atualizar avatar: ${err.message}"
                }
            )
        }
    }

    fun updateBannerFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = ImageCompressionUtils.processAndSaveBanner(getApplication(), uri)
            result.fold(
                onSuccess = { path ->
                    preferencesRepository.setBannerImagePath(path)
                    syncRepository.uploadProfile()
                    _userMessage.value = "Capa do perfil atualizada!"
                },
                onFailure = { err ->
                    _userMessage.value = "Erro ao atualizar capa: ${err.message}"
                }
            )
        }
    }

    fun useWorkCoverAsBanner(coverUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val stream = URL(coverUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val res = ImageCompressionUtils.saveBitmapAsBanner(getApplication(), bitmap)
                        res.fold(
                            onSuccess = { path ->
                                preferencesRepository.setBannerImagePath(path)
                                syncRepository.uploadProfile()
                                _userMessage.value = "Capa da obra aplicada ao perfil!"
                            },
                            onFailure = { err ->
                                _userMessage.value = "Erro ao salvar capa: ${err.message}"
                            }
                        )
                    }
                } catch (e: Exception) {
                    _userMessage.value = "Não foi possível baixar a capa da obra."
                }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            val file = File(getApplication<Application>().filesDir, "avatar.webp")
            if (file.exists()) file.delete()
            preferencesRepository.setAvatarImagePath(null)
            syncRepository.uploadProfile()
            _userMessage.value = "Foto de perfil removida."
        }
    }

    fun removeBanner() {
        viewModelScope.launch {
            val file = File(getApplication<Application>().filesDir, "banner.jpg")
            if (file.exists()) file.delete()
            preferencesRepository.setBannerImagePath(null)
            syncRepository.uploadProfile()
            _userMessage.value = "Capa removida."
        }
    }

    // ------------------------------------------------------------------------
    // Settings & Display Options
    // ------------------------------------------------------------------------
    fun updateTheme(theme: ThemeOption) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    fun updateFontSizeScale(scale: String) {
        viewModelScope.launch { preferencesRepository.setFontSizeScale(scale) }
    }

    fun updateGridColumns(cols: Int) {
        viewModelScope.launch { preferencesRepository.setGridColumns(cols) }
    }

    fun updatePreferredTitleLang(lang: String) {
        viewModelScope.launch { preferencesRepository.setPreferredTitleLang(lang) }
    }

    fun updatePreferSynopsisPt(prefer: Boolean) {
        viewModelScope.launch { preferencesRepository.setPreferSynopsisPt(prefer) }
    }

    fun updateHideLibraryInExplore(hide: Boolean) {
        viewModelScope.launch { preferencesRepository.setHideLibraryInExplore(hide) }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
            if (enabled) {
                com.example.notification.NotificationHelper.schedulePeriodicCheck(getApplication())
            } else {
                com.example.notification.NotificationHelper.cancelPeriodicCheck(getApplication())
            }
        }
    }

    fun updateQuietHours(startHour: Int, endHour: Int) {
        viewModelScope.launch { preferencesRepository.setQuietHours(startHour, endHour) }
    }

    fun updateAdultMode(mode: AdultMode) {
        viewModelScope.launch {
            preferencesRepository.setAdultMode(mode)
            syncRepository.uploadProfile()
        }
    }

    fun confirmAgeAndSetAdultMode(mode: AdultMode) {
        viewModelScope.launch {
            preferencesRepository.setHasConfirmedAge(true)
            preferencesRepository.setAdultMode(mode)
            syncRepository.uploadProfile()
        }
    }

    fun updateBlurAdultCovers(blur: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBlurAdultCovers(blur)
            syncRepository.uploadProfile()
        }
    }

    fun refreshFirebaseStatus() {
        viewModelScope.launch {
            _firebaseStatus.value = authRepository.getFirebaseStatus()
        }
    }

    fun updateCustomToken(token: String) {
        viewModelScope.launch { preferencesRepository.setCustomToken(token) }
    }

    fun restoreDefaultToken() {
        viewModelScope.launch { preferencesRepository.setCustomToken("") }
    }

    fun clearCache() {
        viewModelScope.launch {
            workRepository.clearCache()
            withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    app.cacheDir.resolve("image_cache").deleteRecursively()
                } catch (_: Exception) {}
            }
            calculateCacheSize()
            _userMessage.value = "Cache limpo com sucesso!"
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            libraryRepository.clearAll()
            _userMessage.value = "Biblioteca limpa com sucesso!"
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            clearCache()
            libraryRepository.clearAll()
            preferencesRepository.resetAllPreferences()
            _userMessage.value = "Aplicativo resetado para as configurações de fábrica."
        }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val sizeBytes = withContext(Dispatchers.IO) {
                var total = 0L
                val app = getApplication<Application>()
                val cacheDb = app.getDatabasePath("cache.db")
                if (cacheDb.exists()) total += cacheDb.length()
                val imageDir = app.cacheDir.resolve("image_cache")
                if (imageDir.exists()) {
                    imageDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
                }
                total
            }
            val formatted = if (sizeBytes > 1024 * 1024) {
                String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            } else {
                "${sizeBytes / 1024} KB"
            }
            _cacheSizeFormatted.value = formatted
        }
    }

    fun testTursoConnection() {
        viewModelScope.launch {
            _connectionTest.update { ConnectionTestState(isTesting = true) }
            val start = System.currentTimeMillis()
            try {
                val rows = tursoClient.query("SELECT COUNT(*) as count FROM \"Work\" WHERE isAdult = 0")
                val latency = System.currentTimeMillis() - start
                val count = rows.firstOrNull()?.getLong("count") ?: 0L
                _connectionTest.update {
                    ConnectionTestState(
                        isTesting = false,
                        isSuccess = true,
                        latencyMs = latency,
                        count = count,
                        message = "Conexão ativa! $count obras no catálogo."
                    )
                }
                preferencesRepository.recordDiagnostic(null, latency)
            } catch (e: Exception) {
                val err = e.message ?: "Falha ao conectar"
                _connectionTest.update {
                    ConnectionTestState(
                        isTesting = false,
                        isSuccess = false,
                        message = "Falha: $err"
                    )
                }
                preferencesRepository.recordDiagnostic(err, 0L)
            }
        }
    }

    suspend fun exportLibrary(): String {
        return libraryRepository.exportToJson()
    }

    fun importLibrary(json: String) {
        viewModelScope.launch {
            val res = libraryRepository.importFromJson(json)
            res.fold(
                onSuccess = { count ->
                    _userMessage.value = "Importadas $count obras com sucesso!"
                },
                onFailure = {
                    _userMessage.value = "Erro ao importar: arquivo JSON inválido."
                }
            )
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}

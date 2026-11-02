package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.SyncState
import com.example.ui.components.AuthDialog
import com.example.ui.components.OtakuHeaderIconButton
import com.example.ui.components.OtakuScreenHeader

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCrop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // States from ViewModel
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val currentTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val nickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val bio by viewModel.userBio.collectAsStateWithLifecycle()
    val avatarPath by viewModel.avatarImagePath.collectAsStateWithLifecycle()
    val bannerPath by viewModel.bannerImagePath.collectAsStateWithLifecycle()
    val favWorkId by viewModel.favoriteWorkId.collectAsStateWithLifecycle()
    val profileAccentHex by viewModel.profileAccentColor.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val libraryWorks by viewModel.allLibraryWorks.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()

    // Dialog & Sheet States
    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }

    // Photo Picker launchers for Avatar and Banner - starts manual crop editor
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            showEditProfileSheet = false
            viewModel.startCrop(uri, CropType.AVATAR)
            onNavigateToCrop()
        }
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            showEditProfileSheet = false
            viewModel.startCrop(uri, CropType.BANNER)
            onNavigateToCrop()
        }
    }

    LaunchedEffect(userMsg) {
        userMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val isLoggedIn = authState.isUserLoggedIn
    val loggedUser = authState.currentUser

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with gear icon to Settings
            OtakuScreenHeader(
                title = "Perfil",
                actions = {
                    OtakuHeaderIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Configurações",
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("profile_settings_button")
                    )
                }
            )

            // Card de login só aparece quando deslogado (gerenciamento de conta fica em Configurações > Conta)
            if (!isLoggedIn) {
                AccountSettingsCard(
                    isLoggedIn = false,
                    currentUser = null,
                    lastSyncTime = 0L,
                    syncState = SyncState.IDLE,
                    onLoginClick = { showAuthDialog = true },
                    onSyncNow = {},
                    onSignOutClick = {},
                    onDeleteAccountClick = {}
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Cabeçalho do Perfil (Capa 16:9 + Avatar 88dp + Nome + Bio + "Editar perfil")
            ProfileHeaderSection(
                bannerPath = bannerPath,
                avatarPath = avatarPath,
                nickname = nickname,
                bio = bio,
                accentHex = profileAccentHex,
                isLoggedIn = isLoggedIn,
                userEmail = loggedUser?.email,
                syncState = syncState,
                onEditProfileClick = { showEditProfileSheet = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Grade de Temas Visuais (com miniatura) permanece no Perfil
            ProfileThemesCard(
                currentTheme = currentTheme,
                onThemeSelect = { viewModel.updateTheme(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Estatísticas Pessoais (com gráfico de gêneros apenas quando houver dados)
            ProfileStatsSection(stats = libraryStats)

            Spacer(modifier = Modifier.height(18.dp))

            // Conquistas Otaku
            ProfileAchievementsSection(achievements = achievements)

            Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    // Modal de Edição de Perfil
    if (showEditProfileSheet) {
        EditProfileDialog(
            currentNickname = nickname,
            currentBio = bio,
            currentAccent = profileAccentHex,
            currentFavWorkId = favWorkId,
            libraryWorks = libraryWorks,
            onPickAvatar = { avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickBanner = { bannerPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveAvatar = { viewModel.removeAvatar() },
            onRemoveBanner = { viewModel.removeBanner() },
            onSelectWorkCoverAsBanner = { coverUrl -> viewModel.useWorkCoverAsBanner(coverUrl) },
            onSave = { newNick, newBio, newAccent, newFavId ->
                viewModel.updateProfile(newNick, newBio, newAccent, newFavId)
                showEditProfileSheet = false
            },
            onDismiss = { showEditProfileSheet = false }
        )
    }

    // Diálogo de Autenticação (quando clica em "Entrar" no card de login de visitante)
    if (showAuthDialog) {
        AuthDialog(
            authRepository = viewModel.authRepository,
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = {
                showAuthDialog = false
                viewModel.syncNow()
            }
        )
    }
}

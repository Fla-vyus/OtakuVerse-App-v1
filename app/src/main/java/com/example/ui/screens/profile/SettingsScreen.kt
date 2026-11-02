package com.example.ui.screens.profile

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdultMode
import com.example.ui.components.AuthDialog
import com.example.ui.components.OtakuScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // States from the shared ViewModel
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val currentTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val preferredTitleLang by viewModel.preferredTitleLang.collectAsStateWithLifecycle()
    val preferSynopsisPt by viewModel.preferSynopsisPt.collectAsStateWithLifecycle()
    val hideLibraryInExplore by viewModel.hideLibraryInExplore.collectAsStateWithLifecycle()
    val adultMode by viewModel.adultMode.collectAsStateWithLifecycle()
    val hasConfirmedAge by viewModel.hasConfirmedAge.collectAsStateWithLifecycle()
    val blurAdultCovers by viewModel.blurAdultCovers.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val quietHours by viewModel.quietHours.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSizeFormatted.collectAsStateWithLifecycle()
    val connState by viewModel.connectionTest.collectAsStateWithLifecycle()
    val firebaseStatus by viewModel.firebaseStatus.collectAsStateWithLifecycle()
    val diagnosticInfo by viewModel.diagnosticInfo.collectAsStateWithLifecycle()
    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()

    // Dialog & Expandable States
    var showAuthDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountStep1 by remember { mutableStateOf(false) }
    var showDeleteAccountStep2 by remember { mutableStateOf(false) }
    var showClearLibraryDialog by remember { mutableStateOf(false) }
    var showResetAppDialog by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var pendingAdultMode by remember { mutableStateOf<AdultMode?>(null) }

    // Launchers moved to SettingsScreen
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateNotificationsEnabled(isGranted)
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportLibrary()
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray())
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    } catch (_: Exception) { null }
                }
                if (!json.isNullOrBlank()) {
                    viewModel.importLibrary(json)
                }
            }
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
            .testTag("settings_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with back navigation button
            OtakuScreenHeader(
                title = "Configurações",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 1. Conta
            AccountSettingsCard(
                isLoggedIn = isLoggedIn,
                currentUser = loggedUser,
                lastSyncTime = lastSyncTime,
                syncState = syncState,
                onLoginClick = { showAuthDialog = true },
                onSyncNow = { viewModel.syncNow() },
                onSignOutClick = { showSignOutDialog = true },
                onDeleteAccountClick = { showDeleteAccountStep1 = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Aparência (seguir o sistema, tamanho da fonte segmentado, colunas)
            AppearanceSettingsCard(
                currentTheme = currentTheme,
                fontSizeScale = fontSizeScale,
                gridColumns = gridColumns,
                onThemeSelect = { viewModel.updateTheme(it) },
                onFontSizeSelect = { viewModel.updateFontSizeScale(it) },
                onGridColsSelect = { viewModel.updateGridColumns(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Conteúdo e exibição (com Adult Mode)
            ContentDisplaySettingsCard(
                preferredTitleLang = preferredTitleLang,
                preferSynopsisPt = preferSynopsisPt,
                hideLibraryInExplore = hideLibraryInExplore,
                adultMode = adultMode,
                hasConfirmedAge = hasConfirmedAge,
                blurAdultCovers = blurAdultCovers,
                onTitleLangSelect = { viewModel.updatePreferredTitleLang(it) },
                onPreferSynopsisPtToggle = { viewModel.updatePreferSynopsisPt(it) },
                onHideLibraryToggle = { viewModel.updateHideLibraryInExplore(it) },
                onAdultModeChange = { viewModel.updateAdultMode(it) },
                onAgeConfirmationRequired = { pendingAdultMode = it },
                onBlurAdultCoversToggle = { viewModel.updateBlurAdultCovers(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Notificações
            NotificationsSettingsCard(
                notificationsEnabled = notificationsEnabled,
                quietHours = quietHours,
                onToggleNotifications = { check ->
                    if (check && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.updateNotificationsEnabled(check)
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Dados & Backup
            DataBackupSettingsCard(
                cacheSize = cacheSize,
                onClearCache = { viewModel.clearCache() },
                onExportJson = { exportFileLauncher.launch("otakuverse_backup.json") },
                onImportJson = { importFileLauncher.launch(arrayOf("application/json")) },
                onClearLibraryClick = { showClearLibraryDialog = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Avançado (Turso, Firebase & Diagnóstico)
            AdvancedSettingsCard(
                isExpanded = isAdvancedExpanded,
                customToken = viewModel.customToken.collectAsStateWithLifecycle().value,
                buildTokenLength = viewModel.buildConfigTokenLength,
                connState = connState,
                firebaseStatus = firebaseStatus,
                isAuxTablesAvailable = viewModel.isAuxTablesAvailable,
                diagnosticInfo = diagnosticInfo,
                onToggleExpand = { isAdvancedExpanded = !isAdvancedExpanded },
                onUpdateCustomToken = { viewModel.updateCustomToken(it) },
                onRestoreDefaultToken = { viewModel.restoreDefaultToken() },
                onTestTursoConnection = { viewModel.testTursoConnection() },
                onRefreshFirebaseStatus = { viewModel.refreshFirebaseStatus() },
                onResetAppClick = { showResetAppDialog = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 7. Sobre
            AboutSettingsCard(context = context)

            // Espaço final com navigationBarsPadding para gestos do sistema
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }

    // -------------------------------------------------------------------------
    // DIÁLOGOS DE CONFIGURAÇÕES
    // -------------------------------------------------------------------------

    // Diálogo de Confirmação de Idade (18+)
    pendingAdultMode?.let { targetMode ->
        AlertDialog(
            onDismissRequest = { pendingAdultMode = null },
            title = {
                Text(
                    text = "Confirmação de Idade",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Confirmo que tenho 18 anos ou mais e desejo acessar conteúdos com classificação adulta.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmAgeAndSetAdultMode(targetMode)
                        pendingAdultMode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdultMode = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Auth Dialog
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

    // Sign Out Dialog
    if (showSignOutDialog) {
        var keepData by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sair da conta?") },
            text = {
                Column {
                    Text(
                        text = "Ao sair, você pode escolher se deseja manter ou remover os dados baixados neste aparelho.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keepData = !keepData }
                    ) {
                        Switch(checked = keepData, onCheckedChange = { keepData = it })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (keepData) "Manter dados neste aparelho (Recomendado)" else "Remover dados locais ao sair",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.signOut(keepLocalData = keepData)
                    showSignOutDialog = false
                }) {
                    Text("Confirmar saída")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete Account Dialogs (2 steps)
    if (showDeleteAccountStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountStep1 = false },
            title = { Text("Excluir conta e dados?") },
            text = {
                Text(
                    text = "Atenção: esta ação é irreversível e excluirá sua conta, sua biblioteca sincronizada na nuvem e todos os dados salvos.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountStep1 = false
                        showDeleteAccountStep2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountStep1 = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteAccountStep2) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountStep2 = false },
            title = { Text("Confirmação Definitiva") },
            text = {
                Text(
                    text = "Tem certeza absoluta? Todos os seus favoritos, notas e progresso serão apagados permanentemente.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccountAndData()
                        showDeleteAccountStep2 = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sim, excluir tudo agora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountStep2 = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Clear Library Dialog
    if (showClearLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showClearLibraryDialog = false },
            title = { Text("Limpar biblioteca deste aparelho?") },
            text = {
                Text(
                    text = "Todas as obras salvas localmente serão removidas deste dispositivo. Se estiver logado, seus dados na nuvem permanecerão salvos.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLibrary()
                        showClearLibraryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Apagar localmente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Reset App Dialog
    if (showResetAppDialog) {
        AlertDialog(
            onDismissRequest = { showResetAppDialog = false },
            title = { Text("Resetar aplicativo?") },
            text = {
                Text(
                    text = "Isso apagará o cache, preferências locais e desconectará qualquer usuário ativo.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetApp()
                        showResetAppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Resetar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAppDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

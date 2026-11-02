package com.example.ui.screens.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.auth.AuthUser
import com.example.data.auth.FirebaseStatus
import com.example.data.model.AdultMode
import com.example.data.repository.ThemeOption
import com.example.data.sync.SyncState
import com.example.ui.theme.getSchemeForTheme
import com.example.util.FeatureFlags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -----------------------------------------------------------------------------
// 1. CONTA
// -----------------------------------------------------------------------------
@Composable
fun AccountSettingsCard(
    isLoggedIn: Boolean,
    currentUser: AuthUser?,
    lastSyncTime: Long,
    syncState: SyncState,
    onLoginClick: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    SectionTitle(title = "CONTA")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronização na Nuvem",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Entre com Google ou e-mail para sincronizar sua biblioteca e perfil.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onLoginClick,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Entrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Conectado como:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentUser?.email ?: currentUser?.displayName ?: "Conta OtakuVerse",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val syncTimeStr = if (lastSyncTime > 0) {
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(lastSyncTime))
                        } else "Ainda não sincronizado"
                        Text(
                            text = "Última sincronização: $syncTimeStr",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = onSyncNow,
                        enabled = syncState != SyncState.SYNCING,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (syncState == SyncState.SYNCING) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizar", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onSignOutClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sair da conta", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = onDeleteAccountClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excluir conta", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileThemesCard(
    currentTheme: ThemeOption,
    onThemeSelect: (ThemeOption) -> Unit
) {
    SectionTitle(title = "TEMAS VISUAIS")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeOption.entries.forEach { option ->
                    val scheme = getSchemeForTheme(option).first
                    val isSelected = option == currentTheme

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .clickable { onThemeSelect(option) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. APARÊNCIA (CONFIGURAÇÕES)
// -----------------------------------------------------------------------------
@Composable
fun AppearanceSettingsCard(
    currentTheme: ThemeOption,
    fontSizeScale: String,
    gridColumns: Int,
    onThemeSelect: (ThemeOption) -> Unit,
    onFontSizeSelect: (String) -> Unit,
    onGridColsSelect: (Int) -> Unit
) {
    SectionTitle(title = "APARÊNCIA")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Seguir o sistema
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Seguir o sistema", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Ajusta o tema de acordo com as configurações do dispositivo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = currentTheme == ThemeOption.FOLLOW_SYSTEM,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onThemeSelect(ThemeOption.FOLLOW_SYSTEM)
                        } else {
                            onThemeSelect(ThemeOption.NOITE_AZUL)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tamanho da fonte: seletor segmentado de largura total, maxLines = 1
            Text(text = "Tamanho da fonte:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("small" to "Pequeno", "normal" to "Normal", "large" to "Grande").forEach { (scale, label) ->
                    val isSel = fontSizeScale == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onFontSizeSelect(scale) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Colunas na grade do Explorar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Colunas na grade do Explorar:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4).forEach { cols ->
                        val isSel = gridColumns == cols
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onGridColsSelect(cols) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = cols.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. CONTEÚDO E EXIBIÇÃO (COM ADULT MODE B1/B2)
// -----------------------------------------------------------------------------
@Composable
fun ContentDisplaySettingsCard(
    preferredTitleLang: String,
    preferSynopsisPt: Boolean,
    hideLibraryInExplore: Boolean,
    adultMode: AdultMode,
    hasConfirmedAge: Boolean,
    blurAdultCovers: Boolean,
    onTitleLangSelect: (String) -> Unit,
    onPreferSynopsisPtToggle: (Boolean) -> Unit,
    onHideLibraryToggle: (Boolean) -> Unit,
    onAdultModeChange: (AdultMode) -> Unit,
    onAgeConfirmationRequired: (AdultMode) -> Unit,
    onBlurAdultCoversToggle: (Boolean) -> Unit
) {
    SectionTitle(title = "CONTEÚDO E EXIBIÇÃO")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Idioma preferido do título:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("english" to "Inglês", "romaji" to "Romaji", "native" to "Nativo").forEach { (key, label) ->
                    val isSelected = preferredTitleLang == key
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onTitleLangSelect(key) }
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(text = label, fontSize = 13.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Priorizar sinopse em português", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Exibe sinopse traduzida se existir no catálogo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = preferSynopsisPt, onCheckedChange = onPreferSynopsisPtToggle)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Ocultar no Explorar o que já está salvo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Filtra obras que já estão na sua biblioteca", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hideLibraryInExplore, onCheckedChange = onHideLibraryToggle)
            }

            // B1 & B2: Modo Conteúdo Adulto (18+)
            if (FeatureFlags.ALLOW_ADULT_CONTENT) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Conteúdo Adulto (18+)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Controla a exibição de mangás e animes adultos no Explorar e Agenda.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdultMode.entries.forEach { mode ->
                        val isSel = adultMode == mode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable {
                                    if (mode != AdultMode.OFF && !hasConfirmedAge) {
                                        onAgeConfirmationRequired(mode)
                                    } else {
                                        onAdultModeChange(mode)
                                    }
                                }
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    AdultMode.OFF -> "Desativado"
                                    AdultMode.INCLUDE -> "Incluir"
                                    AdultMode.ONLY -> "Apenas 18+"
                                },
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (adultMode != AdultMode.OFF) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Desfocar capas 18+", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Capas adultas ficam borradas até o toque", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = blurAdultCovers, onCheckedChange = onBlurAdultCoversToggle)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. NOTIFICAÇÕES
// -----------------------------------------------------------------------------
@Composable
fun NotificationsSettingsCard(
    notificationsEnabled: Boolean,
    quietHours: Pair<Int, Int>,
    onToggleNotifications: (Boolean) -> Unit
) {
    SectionTitle(title = "NOTIFICAÇÕES")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Lembrete de novos episódios", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Avisa quando obras em 'Em andamento' lançam episódios", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
            }

            if (notificationsEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "Horário de silêncio: ${quietHours.first}:00 às 0${quietHours.second}:00", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Durante a noite, notificações são silenciadas para não incomodar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. DADOS & BACKUP
// -----------------------------------------------------------------------------
@Composable
fun DataBackupSettingsCard(
    cacheSize: String,
    onClearCache: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onClearLibraryClick: () -> Unit
) {
    SectionTitle(title = "DADOS & BACKUP")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Armazenamento em cache", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Imagens e catálogo: $cacheSize", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportJson,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar JSON", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar JSON", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClearLibraryClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apagar biblioteca deste aparelho", fontSize = 12.sp)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 6. AVANÇADO (TURSO, STATUS DO FIREBASE, AVISO DE TABELAS AUXILIARES)
// -----------------------------------------------------------------------------
@Composable
fun AdvancedSettingsCard(
    isExpanded: Boolean,
    customToken: String,
    buildTokenLength: Int = 0,
    connState: ConnectionTestState,
    firebaseStatus: FirebaseStatus?,
    isAuxTablesAvailable: Boolean,
    diagnosticInfo: Triple<String?, Long, String?>,
    onToggleExpand: () -> Unit,
    onUpdateCustomToken: (String) -> Unit,
    onRestoreDefaultToken: () -> Unit,
    onTestTursoConnection: () -> Unit,
    onRefreshFirebaseStatus: () -> Unit,
    onResetAppClick: () -> Unit
) {
    var inputTokenState by remember(customToken) { mutableStateOf(customToken) }
    var isTokenMasked by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Avançado (Turso, Firebase & Diagnóstico)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // A3: Status do Firebase
                    Text(text = "Status do Firebase:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Column {
                            if (firebaseStatus != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (firebaseStatus.isAppInitialized) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (firebaseStatus.isAppInitialized) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (firebaseStatus.isAppInitialized) "Firebase Inicializado" else "Firebase NÃO inicializado",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (firebaseStatus.isAppInitialized) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = firebaseStatus.details,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            } else {
                                Text("Verificando status do Firebase...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = onRefreshFirebaseStatus,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Atualizar status do Firebase", fontSize = 12.sp)
                            }
                        }
                    }

                    // C1: Aviso de tabelas auxiliares
                    if (!isAuxTablesAvailable) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Filtros lentos: tabelas auxiliares indisponíveis (usando json_each como fallback com limite de 12s)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status dos Tokens (Requisito: sem exibir o valor)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val buildText = if (buildTokenLength > 0) {
                                "Token do build: presente ($buildTokenLength caracteres)"
                            } else {
                                "Token do build: ausente"
                            }
                            val manualText = if (customToken.isNotBlank()) {
                                "Token manual: presente"
                            } else {
                                "Token manual: ausente"
                            }
                            Text(
                                text = buildText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (buildTokenLength > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = manualText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (customToken.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Turso Token Manual
                    Text(text = "Token de autenticação Turso (somente leitura):", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = inputTokenState,
                        onValueChange = { inputTokenState = it },
                        placeholder = { Text("Configurado via BuildConfig / .env") },
                        visualTransformation = if (isTokenMasked) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { isTokenMasked = !isTokenMasked }) {
                                Icon(
                                    imageVector = if (isTokenMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Mostrar/Ocultar"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onUpdateCustomToken(inputTokenState) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Salvar Token", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onRestoreDefaultToken()
                                inputTokenState = ""
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restaurar padrão", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Teste de conexão Turso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Teste de Conexão Turso:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onTestTursoConnection,
                            enabled = !connState.isTesting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (connState.isTesting) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Testar conexão", fontSize = 12.sp)
                            }
                        }
                    }

                    if (connState.message != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (connState.isSuccess == true) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(text = connState.message ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                connState.latencyMs?.let {
                                    Text(text = "Latência: ${it}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Painel Diagnóstico
                    Text(text = "Painel de Diagnóstico:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Último erro: ${diagnosticInfo.first ?: "Nenhum erro registrado"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "• Duração da consulta: ${diagnosticInfo.second}ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "• nextEpisodeAt: ${diagnosticInfo.third ?: "Não amostrado"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onResetAppClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Resetar App para Padrão de Fábrica")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 7. SOBRE (COM LINKS C4)
// -----------------------------------------------------------------------------
@Composable
fun AboutSettingsCard(context: Context) {
    SectionTitle(title = "SOBRE")
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "OtakuVerse v${BuildConfig.VERSION_NAME}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Catálogo Otaku independente com conexão direta ao Turso (libSQL) e sincronização Firebase. Dados agregados de fontes abertas da comunidade como AniList e Kitsu.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:lavymikhail@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Feedback OtakuVerse v${BuildConfig.VERSION_NAME}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Enviar Feedback"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Feedback", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Avaliar", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // C4: Links para Política de Privacidade e Página de Exclusão de Conta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "Política de Privacidade",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://otakuverse.app/privacy"))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                        .padding(6.dp)
                )

                Text(
                    text = "Exclusão de Conta",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://otakuverse.app/delete-account"))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                        .padding(6.dp)
                )
            }
        }
    }
}
